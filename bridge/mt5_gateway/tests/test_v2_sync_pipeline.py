"""v2 同步层测试。"""

import asyncio
import json
import sys
import types
import unittest
from pathlib import Path
from unittest import mock


def _install_test_stubs():
    """为 v2 同步测试注入最小依赖桩，避免本地缺依赖时无法导入模块。"""
    if "uvicorn" not in sys.modules:
        uvicorn_stub = types.ModuleType("uvicorn")
        uvicorn_stub.run = lambda *args, **kwargs: None
        sys.modules["uvicorn"] = uvicorn_stub

    if "dotenv" not in sys.modules:
        dotenv_stub = types.ModuleType("dotenv")
        dotenv_stub.load_dotenv = lambda *args, **kwargs: None
        sys.modules["dotenv"] = dotenv_stub

    if "fastapi" not in sys.modules:
        fastapi_stub = types.ModuleType("fastapi")

        class _FastAPI:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

            def add_middleware(self, *args, **kwargs):
                return None

            def get(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator

            def post(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator

            def websocket(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator

        class _HTTPException(Exception):
            def __init__(self, status_code=500, detail=""):
                super().__init__(detail)
                self.status_code = status_code
                self.detail = detail

        fastapi_stub.FastAPI = _FastAPI
        fastapi_stub.HTTPException = _HTTPException
        fastapi_stub.Header = lambda default=None, **kwargs: default
        fastapi_stub.Query = lambda default=None, **kwargs: default
        fastapi_stub.Request = type("Request", (), {})
        fastapi_stub.Response = type("Response", (), {})
        fastapi_stub.WebSocket = type("WebSocket", (), {})
        fastapi_stub.WebSocketDisconnect = Exception
        sys.modules["fastapi"] = fastapi_stub

    if "fastapi.middleware.gzip" not in sys.modules:
        gzip_stub = types.ModuleType("fastapi.middleware.gzip")

        class _GZipMiddleware:
            pass

        gzip_stub.GZipMiddleware = _GZipMiddleware
        sys.modules["fastapi.middleware.gzip"] = gzip_stub

    if "websockets" not in sys.modules:
        websockets_stub = types.ModuleType("websockets")
        websockets_stub.connect = lambda *args, **kwargs: None
        sys.modules["websockets"] = websockets_stub


_install_test_stubs()

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import server_v2  # noqa: E402


class V2SyncPipelineTests(unittest.TestCase):
    """验证 v2 增量和 WS 消息结构。"""

    def setUp(self):
        if hasattr(server_v2, "account_publish_state") and hasattr(server_v2, "_reset_account_publish_state_locked"):
            with server_v2.snapshot_cache_lock:
                server_v2._reset_account_publish_state_locked()
                if hasattr(server_v2, "_reset_market_realtime_publish_state_locked"):
                    server_v2._reset_market_realtime_publish_state_locked()
        if hasattr(server_v2, "v2_sync_state"):
            server_v2.v2_sync_state.clear()
        if hasattr(server_v2, "v2_stream_subscribers"):
            with server_v2.v2_stream_subscribers_lock:
                server_v2.v2_stream_subscribers.clear()
                server_v2.v2_stream_subscriber_seq = 0

    @staticmethod
    def _build_stub_snapshot(position_count: int = 0, order_count: int = 0):
        positions = []
        for index in range(position_count):
            positions.append(
                {
                    "positionTicket": 1000 + index,
                    "code": "BTCUSD",
                    "side": "BUY",
                    "quantity": 0.1 + index * 0.01,
                    "costPrice": 60000.0 + index,
                }
            )

        pending_orders = []
        for index in range(order_count):
            pending_orders.append(
                {
                    "orderId": 2000 + index,
                    "code": "XAUUSD",
                    "side": "SELL",
                    "pendingLots": 0.2 + index * 0.01,
                    "pendingPrice": 2300.0 + index,
                }
            )

        return {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "historyRevision": f"stub-{position_count}-{order_count}",
            },
            "overviewMetrics": [],
            "curvePoints": [],
            "curveIndicators": [],
            "positions": positions,
            "pendingOrders": pending_orders,
            "trades": [],
            "statsMetrics": [],
        }

    def test_stream_event_should_read_published_bus_state_without_runtime_rebuild(self):
        calls = {"runtime": 0}

        def fake_runtime_snapshot(server_time):
            calls["runtime"] += 1
            return {
                "digest": "runtime-digest",
                "market": {"symbolStates": []},
                "account": {"positions": []},
            }

        original_runtime_builder = getattr(server_v2, "_build_v2_sync_runtime_snapshot")
        server_v2._build_v2_sync_runtime_snapshot = fake_runtime_snapshot
        try:
            with server_v2.snapshot_cache_lock:
                server_v2.account_publish_state.update({
                    "busSeq": 3,
                    "publishedAt": 1000,
                    "revisions": {
                        "marketRevision": "market-3",
                        "accountRuntimeRevision": "account-7",
                        "accountHistoryRevision": "history-9",
                        "abnormalRevision": "abnormal-2",
                    },
                    "event": {
                        "type": "syncEvent",
                        "busSeq": 3,
                        "publishedAt": 1000,
                        "revisions": {
                            "marketRevision": "market-3",
                            "accountRuntimeRevision": "account-7",
                            "accountHistoryRevision": "history-9",
                            "abnormalRevision": "abnormal-2",
                        },
                        "changes": {
                            "market": {"snapshot": {"symbolStates": []}},
                        },
                    },
                })

            payload = server_v2._build_v2_stream_event_for_client(last_bus_seq=2)
        finally:
            server_v2._build_v2_sync_runtime_snapshot = original_runtime_builder

        self.assertEqual(3, payload["busSeq"])
        self.assertEqual("syncEvent", payload["type"])
        self.assertEqual("market-3", payload["revisions"]["marketRevision"])
        self.assertEqual(0, calls["runtime"])

    def test_stream_event_should_emit_heartbeat_when_bus_seq_unchanged(self):
        with server_v2.snapshot_cache_lock:
            server_v2.account_publish_state.update({
                "busSeq": 5,
                "publishedAt": 2000,
                "revisions": {
                    "marketRevision": "market-5",
                    "accountRuntimeRevision": "account-5",
                    "accountHistoryRevision": "history-5",
                    "abnormalRevision": "abnormal-5",
                },
                "event": {
                    "type": "syncEvent",
                    "busSeq": 5,
                    "publishedAt": 2000,
                    "revisions": {
                        "marketRevision": "market-5",
                        "accountRuntimeRevision": "account-5",
                        "accountHistoryRevision": "history-5",
                        "abnormalRevision": "abnormal-5",
                    },
                    "changes": {
                        "accountRuntime": {"snapshot": {"positions": []}},
                    },
                },
            })

        payload = server_v2._build_v2_stream_event_for_client(last_bus_seq=5)

        self.assertEqual("heartbeat", payload["type"])
        self.assertEqual(5, payload["busSeq"])
        self.assertEqual({}, payload["changes"])

    def test_build_market_stream_upstream_url_should_subscribe_trade_streams(self):
        url = server_v2._build_market_stream_upstream_url()

        self.assertIn("btcusdt@trade", url)
        self.assertIn("xauusdt@trade", url)
        self.assertNotIn("@kline_1m", url)

    def test_market_stream_runtime_message_should_wake_market_publish_chain(self):
        payload = {
            "stream": "btcusdt@trade",
            "data": {
                "e": "trade",
                "E": 1_710_000_000_123,
                "s": "BTCUSDT",
                "p": "100.5",
                "q": "12.0",
            },
        }

        server_v2._consume_market_stream_runtime_message(json.dumps(payload))

        market_state = server_v2._read_market_realtime_publish_state()
        self.assertTrue(market_state["dirty"])
        self.assertEqual(1, market_state["dirtyVersion"])
        state = server_v2._build_market_runtime_symbol_state("BTCUSDT")
        self.assertEqual(100.5, state["latestPatch"]["close"])
        self.assertEqual(12.0, state["latestPatch"]["volume"])
        self.assertEqual(1_710_000_000_123, state["lastEventTime"])

    def test_v2_bus_producer_loop_should_publish_on_fixed_batch_deadline_under_continuous_wake(self):
        published = []

        class _FakeStopEvent:
            def __init__(self):
                self.should_stop = False

            def is_set(self):
                return self.should_stop

        class _FakeWakeEvent:
            def __init__(self, wait_results):
                self.wait_results = list(wait_results)
                self.clear_calls = 0

            def wait(self, timeout):
                return self.wait_results.pop(0) if self.wait_results else False

            def clear(self):
                self.clear_calls += 1

        fake_stop_event = _FakeStopEvent()
        fake_wake_event = _FakeWakeEvent([True, True])

        def fake_publish():
            published.append("published")
            fake_stop_event.should_stop = True

        with mock.patch.object(server_v2, "v2_bus_stop_event", fake_stop_event, create=True), mock.patch.object(
            server_v2,
            "v2_bus_wake_event",
            fake_wake_event,
            create=True,
        ), mock.patch.object(
            server_v2,
            "V2_STREAM_PUSH_INTERVAL_MS",
            500,
        ), mock.patch.object(
            server_v2.time,
            "monotonic",
            side_effect=[100.0, 100.2, 100.4, 100.6],
        ), mock.patch.object(
            server_v2,
            "_v2_bus_publish_current_state",
            side_effect=fake_publish,
        ):
            server_v2._v2_bus_producer_loop()

        self.assertEqual(["published"], published)
        self.assertEqual(2, fake_wake_event.clear_calls)

    def test_market_realtime_publisher_loop_should_publish_on_fixed_interval_when_dirty(self):
        published = []

        class _FakeStopEvent:
            def __init__(self):
                self.should_stop = False
                self.wait_calls = 0

            def wait(self, timeout):
                self.wait_calls += 1
                return self.should_stop

        fake_stop_event = _FakeStopEvent()

        def fake_publish():
            published.append("marketTick")
            fake_stop_event.should_stop = True

        with mock.patch.object(server_v2, "market_realtime_stop_event", fake_stop_event, create=True), mock.patch.object(
            server_v2,
            "V2_STREAM_PUSH_INTERVAL_MS",
            500,
        ), mock.patch.object(
            server_v2,
            "_publish_market_realtime_current_state",
            side_effect=fake_publish,
        ):
            server_v2._market_realtime_publisher_loop()

        self.assertEqual(["marketTick"], published)
        self.assertEqual(2, fake_stop_event.wait_calls)

    def test_market_stream_symbol_state_should_only_use_sanitized_runtime_without_rest_hot_path(self):
        runtime_state = {
            "productId": "BTC",
            "marketSymbol": "BTCUSDT",
            "tradeSymbol": "BTCUSD",
            "interval": "1m",
            "latestPrice": 0.0,
            "latestOpenTime": 0,
            "latestCloseTime": 0,
            "latestClosedCandle": None,
            "latestPatch": None,
            "updatedAt": 0,
            "lastEventTime": 0,
        }

        with mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value=runtime_state,
        ), mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            side_effect=AssertionError("hot path should not hit rest"),
        ):
            payload = server_v2._build_market_stream_symbol_state("BTCUSDT", 1_710_000_200_000)

        self.assertIsNone(payload["latestPatch"])
        self.assertIsNone(payload["latestClosedCandle"])
        self.assertEqual(0.0, payload["latestPrice"])
        self.assertEqual(0, payload["lastEventTime"])

    def test_market_stream_symbol_state_should_drop_stale_runtime_patch_without_rest_closed_fallback(self):
        stale_runtime_state = {
            "productId": "BTC",
            "marketSymbol": "BTCUSDT",
            "tradeSymbol": "BTCUSD",
            "interval": "1m",
            "latestPrice": 101.8,
            "latestOpenTime": 1_710_000_000_000,
            "latestCloseTime": 1_710_000_059_999,
            "latestClosedCandle": {
                "productId": "BTC",
                "marketSymbol": "BTCUSDT",
                "tradeSymbol": "BTCUSD",
                "symbol": "BTCUSDT",
                "interval": "1m",
                "openTime": 1_709_999_940_000,
                "closeTime": 1_709_999_999_999,
                "open": 100.0,
                "high": 101.0,
                "low": 99.0,
                "close": 100.5,
                "volume": 5.0,
                "quoteVolume": 500.0,
                "tradeCount": 2,
                "source": "binance-ws",
                "isClosed": True,
                "version": 1_709_999_940_000,
            },
            "latestPatch": {
                "productId": "BTC",
                "marketSymbol": "BTCUSDT",
                "tradeSymbol": "BTCUSD",
                "symbol": "BTCUSDT",
                "interval": "1m",
                "openTime": 1_710_000_000_000,
                "closeTime": 1_710_000_059_999,
                "open": 100.5,
                "high": 102.0,
                "low": 100.0,
                "close": 101.8,
                "volume": 8.0,
                "quoteVolume": 800.0,
                "tradeCount": 4,
                "source": "binance-ws",
                "isClosed": False,
                "version": 1_710_000_000_000,
            },
            "updatedAt": 1_710_000_000_123,
            "lastEventTime": 1_710_000_000_123,
        }

        with mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value=stale_runtime_state,
        ):
            payload = server_v2._build_market_stream_symbol_state("BTCUSDT", 1_710_000_200_000)

        self.assertIsNone(payload["latestPatch"])
        self.assertEqual("binance-ws", payload["latestClosedCandle"]["source"])
        self.assertEqual(1_709_999_940_000, payload["latestClosedCandle"]["openTime"])

    def test_publish_v2_bus_event_should_notify_registered_stream_subscribers(self):
        queue = asyncio.Queue(maxsize=1)
        scheduled_calls = []

        class _FakeLoop:
            def call_soon_threadsafe(self, func, *args):
                scheduled_calls.append((func, args))

        subscriber_id = server_v2._register_v2_stream_subscriber(_FakeLoop(), queue)
        try:
            event = server_v2._publish_v2_bus_event(
                event_type="syncEvent",
                changes={"market": {"snapshot": {"symbolStates": []}}},
                revisions={
                    "marketRevision": "market-1",
                    "accountRuntimeRevision": "",
                    "accountHistoryRevision": "",
                    "abnormalRevision": "",
                },
                published_at=1234,
            )
        finally:
            server_v2._unregister_v2_stream_subscriber(subscriber_id)

        self.assertEqual(1, len(scheduled_calls))
        scheduled_calls[0][0](*scheduled_calls[0][1])
        self.assertEqual({
            "kind": "syncEvent",
            "seq": event["busSeq"],
        }, queue.get_nowait())

    def test_publish_market_tick_should_notify_registered_stream_subscribers(self):
        queue = asyncio.Queue(maxsize=1)
        scheduled_calls = []

        class _FakeLoop:
            def call_soon_threadsafe(self, func, *args):
                scheduled_calls.append((func, args))

        subscriber_id = server_v2._register_v2_stream_subscriber(_FakeLoop(), queue)
        try:
            event = server_v2._publish_market_tick(
                snapshot={"symbolStates": [{"marketSymbol": "BTCUSDT"}]},
                published_at=1234,
                published_version=1,
            )
        finally:
            server_v2._unregister_v2_stream_subscriber(subscriber_id)

        self.assertEqual(1, len(scheduled_calls))
        scheduled_calls[0][0](*scheduled_calls[0][1])
        self.assertEqual({
            "kind": "marketTick",
            "seq": event["marketSeq"],
        }, queue.get_nowait())

    def test_legacy_v2_bus_state_alias_should_not_exist(self):
        self.assertFalse(hasattr(server_v2, "v2_bus_state"))

    def test_runtime_snapshot_should_not_touch_mt5_when_session_logged_out(self):
        with mock.patch.object(
            server_v2.session_manager,
            "build_status_payload",
            return_value={
                "state": "logged_out",
                "activeAccount": None,
                "savedAccounts": [],
                "savedAccountCount": 0,
            },
        ), mock.patch.object(
            server_v2,
            "_build_account_light_snapshot_with_cache",
            side_effect=AssertionError("logged_out stream should not build MT5 snapshot"),
        ), mock.patch.object(
            server_v2,
            "_build_v2_market_section",
            return_value={
                "source": "binance",
                "symbols": ["BTCUSDT", "XAUUSDT"],
                "restUpstream": "https://fapi.binance.com",
                "wsUpstream": "wss://fstream.binance.com",
            },
        ):
            snapshot = server_v2._build_v2_sync_runtime_snapshot(server_time=606)

        self.assertEqual("", snapshot["account"]["accountMeta"]["login"])
        self.assertEqual("", snapshot["account"]["accountMeta"]["server"])
        self.assertEqual("remote_logged_out", snapshot["account"]["accountMeta"]["source"])
        self.assertEqual([], snapshot["account"]["positions"])
        self.assertEqual([], snapshot["account"]["orders"])
        self.assertEqual(0, snapshot["tradeCount"])
        self.assertEqual(0, snapshot["curvePointCount"])

    def test_v2_stream_sends_bootstrap_then_heartbeat_after_idle_timeout(self):
        snapshot = self._build_stub_snapshot(position_count=1, order_count=1)
        market = {
            "source": "binance",
            "symbols": ["BTCUSDT", "XAUUSDT"],
            "symbolStates": [],
            "restUpstream": "https://fapi.binance.com",
            "wsUpstream": "wss://fstream.binance.com",
        }

        class _FakeClient:
            def __init__(self):
                self.accepted = False
                self.messages = []

            async def accept(self):
                self.accepted = True

            async def send_json(self, payload):
                self.messages.append(payload)
                if len(self.messages) >= 2:
                    raise server_v2.WebSocketDisconnect()

            async def close(self, code=1000, reason=""):
                return None

        async def _timeout_wait_for(awaitable, timeout):
            if hasattr(awaitable, "close"):
                awaitable.close()
            raise asyncio.TimeoutError()

        fake_client = _FakeClient()
        with mock.patch.object(
            server_v2.session_manager,
            "build_status_payload",
            return_value={
                "state": "activated",
                "activeAccount": {"login": "7400048", "server": "ICMarketsSC-MT5-6"},
                "savedAccounts": [],
                "savedAccountCount": 0,
            },
        ), mock.patch.object(
            server_v2, "_build_account_light_snapshot_with_cache", return_value=snapshot
        ), mock.patch.object(
            server_v2, "_build_v2_market_section", return_value=market
        ), mock.patch.object(
            server_v2.asyncio, "wait_for", new=_timeout_wait_for
        ):
            server_v2._v2_bus_publish_current_state()
            asyncio.run(server_v2.v2_stream(fake_client))

        self.assertTrue(fake_client.accepted)
        self.assertGreaterEqual(len(fake_client.messages), 2)
        self.assertEqual("syncBootstrap", fake_client.messages[0]["type"])
        self.assertIn("changes", fake_client.messages[0])
        self.assertIn("market", fake_client.messages[0]["changes"])
        self.assertEqual("heartbeat", fake_client.messages[1]["type"])

    def test_v2_stream_should_consume_published_bus_without_to_thread_snapshot_build(self):
        snapshot = self._build_stub_snapshot(position_count=1, order_count=1)
        market = {
            "source": "binance",
            "symbols": ["BTCUSDT", "XAUUSDT"],
            "symbolStates": [],
            "restUpstream": "https://fapi.binance.com",
            "wsUpstream": "wss://fstream.binance.com",
        }
        to_thread_calls = []

        class _FakeClient:
            async def accept(self):
                return None

            async def send_json(self, _payload):
                raise server_v2.WebSocketDisconnect()

            async def close(self, code=1000, reason=""):
                return None

        async def _fake_to_thread(func, *args, **kwargs):
            to_thread_calls.append((func, args, kwargs))
            return func(*args, **kwargs)

        with mock.patch.object(
            server_v2.session_manager,
            "build_status_payload",
            return_value={
                "state": "activated",
                "activeAccount": {"login": "7400048", "server": "ICMarketsSC-MT5-6"},
                "savedAccounts": [],
                "savedAccountCount": 0,
            },
        ), mock.patch.object(
            server_v2, "_build_account_light_snapshot_with_cache", return_value=snapshot
        ), mock.patch.object(
            server_v2, "_build_v2_market_section", return_value=market
        ), mock.patch.object(
            server_v2.asyncio, "to_thread", new=_fake_to_thread
        ):
            server_v2._v2_bus_publish_current_state()
            asyncio.run(server_v2.v2_stream(_FakeClient()))

        self.assertEqual(0, len(to_thread_calls))

    def test_v2_stream_bootstrap_should_keep_account_runtime_identity(self):
        payload = server_v2._build_v2_stream_bootstrap_message(
            {
                "busSeq": 1,
                "publishedAt": 1000,
                "runtimeSnapshot": {
                    "accountRevision": "acct-1",
                    "historyRevision": "history-9",
                    "account": {
                        "accountMeta": {
                            "login": "7400048",
                            "server": "ICMarketsSC-MT5-6",
                            "historyRevision": "history-9",
                        },
                        "positions": [],
                        "orders": [],
                    },
                    "market": {},
                },
                "abnormalSnapshot": {},
                "revisions": {
                    "marketRevision": "",
                    "accountRuntimeRevision": "acct-1",
                    "accountHistoryRevision": "history-9",
                    "abnormalRevision": "",
                },
            }
        )

        self.assertEqual(
            "7400048",
            payload["changes"]["accountRuntime"]["snapshot"]["accountMeta"]["login"],
        )
        self.assertEqual(
            "ICMarketsSC-MT5-6",
            payload["changes"]["accountRuntime"]["snapshot"]["accountMeta"]["server"],
        )

    def test_market_stream_runtime_should_be_marked_stale_after_event_timeout(self):
        with mock.patch.object(
            server_v2,
            "_build_market_runtime_source_status",
            return_value={
                "marketRuntimeConnected": True,
                "marketRuntimeLastEventTime": 1_710_000_000_000,
                "marketRuntimeConnectedAt": 1_710_000_000_000,
            },
        ):
            self.assertTrue(server_v2._is_market_stream_runtime_stale(1_710_000_006_000))
            self.assertFalse(server_v2._is_market_stream_runtime_stale(1_710_000_004_000))

    def test_configure_windows_event_loop_policy_should_switch_to_selector_on_windows(self):
        class _FakeSelectorPolicy:
            pass

        with mock.patch.object(server_v2.os, "name", "nt"), mock.patch.object(
            server_v2.asyncio, "WindowsSelectorEventLoopPolicy", _FakeSelectorPolicy, create=True
        ), mock.patch.object(server_v2.asyncio, "get_event_loop_policy", return_value=object()), mock.patch.object(
            server_v2.asyncio, "set_event_loop_policy"
        ) as set_mock:
            server_v2._configure_windows_event_loop_policy()

        selected_policy = set_mock.call_args[0][0]
        self.assertIsInstance(selected_policy, _FakeSelectorPolicy)


if __name__ == "__main__":
    unittest.main()

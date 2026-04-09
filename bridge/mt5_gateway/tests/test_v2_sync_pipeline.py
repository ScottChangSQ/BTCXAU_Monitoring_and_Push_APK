"""v2 同步层测试。"""

import asyncio
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
        if hasattr(server_v2, "v2_sync_state"):
            server_v2.v2_sync_state.clear()
        if hasattr(server_v2, "v2_bus_state"):
            server_v2.v2_bus_state.clear()
            server_v2.v2_bus_state.update({
                "busSeq": 0,
                "publishedAt": 0,
                "revisions": {
                    "marketRevision": "",
                    "accountRuntimeRevision": "",
                    "accountHistoryRevision": "",
                    "abnormalRevision": "",
                },
                "event": None,
                "runtimeSnapshot": None,
                "abnormalSnapshot": None,
            })

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
            server_v2.v2_bus_state = {
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
            }

            payload = server_v2._build_v2_stream_event_for_client(last_bus_seq=2)
        finally:
            server_v2._build_v2_sync_runtime_snapshot = original_runtime_builder

        self.assertEqual(3, payload["busSeq"])
        self.assertEqual("syncEvent", payload["type"])
        self.assertEqual("market-3", payload["revisions"]["marketRevision"])
        self.assertEqual(0, calls["runtime"])

    def test_stream_event_should_emit_heartbeat_when_bus_seq_unchanged(self):
        server_v2.v2_bus_state = {
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
        }

        payload = server_v2._build_v2_stream_event_for_client(last_bus_seq=5)

        self.assertEqual("heartbeat", payload["type"])
        self.assertEqual(5, payload["busSeq"])
        self.assertEqual({}, payload["changes"])

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

    def test_v2_stream_sends_bootstrap_then_non_heartbeat_message(self):
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

        async def _fast_sleep(_seconds: float):
            return None

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
            server_v2.asyncio, "sleep", new=_fast_sleep
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

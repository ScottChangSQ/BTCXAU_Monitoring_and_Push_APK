"""v2 网关契约测试。"""

from contextlib import contextmanager
import sys
import types
import unittest
from unittest import mock
from pathlib import Path


def _install_test_stubs():
    """为 v2 契约测试注入最小依赖桩，避免本地缺依赖时无法导入模块。"""
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


@contextmanager
def _configured_mt5_server_timezone(timezone_name: str = "UTC"):
    """在测试里显式声明 MT5 服务器时区，避免依赖空配置。"""
    original_offset = getattr(server_v2, "MT5_TIME_OFFSET_MINUTES", 0)
    original_timezone = getattr(server_v2, "MT5_SERVER_TIMEZONE", "")
    try:
        server_v2.MT5_TIME_OFFSET_MINUTES = 0
        server_v2.MT5_SERVER_TIMEZONE = timezone_name
        yield
    finally:
        server_v2.MT5_TIME_OFFSET_MINUTES = original_offset
        server_v2.MT5_SERVER_TIMEZONE = original_timezone


class V2ContractTests(unittest.TestCase):
    """验证 v2 契约最小结构。"""

    def setUp(self):
        # 每个用例前清理共享缓存状态，避免跨用例污染导致偶发失败。
        if hasattr(server_v2, "snapshot_build_cache"):
            server_v2.snapshot_build_cache.clear()
        if hasattr(server_v2, "snapshot_sync_cache"):
            server_v2.snapshot_sync_cache.clear()
        if hasattr(server_v2, "market_candles_cache"):
            server_v2.market_candles_cache.clear()
        if hasattr(server_v2, "v2_sync_state"):
            server_v2.v2_sync_state.clear()
        if hasattr(server_v2, "_reset_account_publish_state_locked") and hasattr(server_v2, "snapshot_cache_lock"):
            with server_v2.snapshot_cache_lock:
                server_v2._reset_account_publish_state_locked()
                if hasattr(server_v2, "_reset_market_realtime_publish_state_locked"):
                    server_v2._reset_market_realtime_publish_state_locked()

    def test_account_light_snapshot_cache_should_not_be_slower_than_v2_stream_push_interval(self):
        self.assertLessEqual(
            server_v2.SNAPSHOT_BUILD_CACHE_MS,
            server_v2.V2_STREAM_PUSH_INTERVAL_MS,
            "账户轻快照缓存寿命不能长于 v2 stream 推送间隔，否则客户端看不到 0.5s 运行态更新",
        )

    def test_market_snapshot_has_sync_token_and_market_section(self):
        payload = server_v2._build_v2_market_snapshot_payload({}, {}, 101)

        self.assertEqual(101, payload["serverTime"])
        self.assertIn("syncToken", payload)
        self.assertIn("market", payload)
        self.assertIn("account", payload)

    def test_symbol_descriptor_should_not_accept_legacy_aliases(self):
        btc = server_v2._resolve_symbol_descriptor("XBT")
        xau = server_v2._resolve_symbol_descriptor("GOLD")

        self.assertEqual("XBT", btc["productId"])
        self.assertEqual("XBT", btc["marketSymbol"])
        self.assertEqual("XBT", btc["tradeSymbol"])
        self.assertEqual("GOLD", xau["productId"])
        self.assertEqual("GOLD", xau["marketSymbol"])
        self.assertEqual("GOLD", xau["tradeSymbol"])

    def test_market_candles_response_separates_closed_and_patch(self):
        payload = server_v2._build_v2_market_candles_payload(
            symbol="BTCUSDT",
            interval="1h",
            server_time=202,
            candles=[{"openTime": 1000}],
            latest_patch={"openTime": 2000},
        )

        self.assertEqual("BTCUSDT", payload["symbol"])
        self.assertEqual("1h", payload["interval"])
        self.assertEqual([{"openTime": 1000}], payload["candles"])
        self.assertEqual({"openTime": 2000}, payload["latestPatch"])
        self.assertIn("nextSyncToken", payload)

    def test_market_candles_supports_start_and_end_time_passthrough(self):
        with mock.patch.object(server_v2, "_fetch_binance_kline_rows", return_value=[]) as fetch_mock:
            payload = server_v2.v2_market_candles(
                symbol="BTCUSDT",
                interval="1m",
                limit=20,
                startTime=111,
                endTime=222,
            )

        fetch_mock.assert_called_once_with(
            "BTCUSDT",
            "1m",
            20,
            start_time_ms=111,
            end_time_ms=222,
        )
        self.assertEqual("BTCUSDT", payload["symbol"])
        self.assertEqual("1m", payload["interval"])

    def test_market_candles_should_not_expose_rest_current_minute_as_live_patch(self):
        rest_rows = [
            [1000, "1.0", "2.0", "0.5", "1.5", "10.0", 1999, "20.0", 3],
            [2000, "2.0", "2.5", "1.8", "2.1", "5.0", 4999, "12.0", 2],
        ]
        with mock.patch.object(server_v2, "_now_ms", return_value=3000), mock.patch.object(
            server_v2, "_fetch_binance_kline_rows", return_value=rest_rows
        ):
            payload = server_v2.v2_market_candles(
                symbol="BTCUSDT",
                interval="1m",
                limit=20,
                startTime=0,
                endTime=0,
            )

        self.assertEqual([1000], [item["openTime"] for item in payload["candles"]])
        self.assertIsNone(payload["latestPatch"])

    def test_market_stream_symbol_state_should_not_build_rest_realtime_patch_when_runtime_missing(self):
        with mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value={
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
            },
        ), mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            side_effect=AssertionError("hot path should not hit rest"),
        ):
            payload = server_v2._build_market_stream_symbol_state("BTCUSDT", 3000)

        self.assertIsNone(payload["latestPatch"])
        self.assertEqual(0.0, payload["latestPrice"])

    def test_market_candles_should_fetch_large_window_in_multiple_chunks(self):
        first_chunk = [
            [2000, "2.0", "2.1", "1.9", "2.0", "5.0", 2999, "10.0", 2],
            [3000, "3.0", "3.1", "2.9", "3.0", "5.0", 3999, "10.0", 2],
        ]
        second_chunk = [
            [1, "0.0", "0.1", "-0.1", "0.0", "5.0", 999, "10.0", 2],
            [1000, "1.0", "1.1", "0.9", "1.0", "5.0", 1999, "10.0", 2],
        ]
        with mock.patch.object(server_v2, "MARKET_CANDLES_UPSTREAM_CHUNK_LIMIT", 2), mock.patch.object(
            server_v2, "_now_ms", return_value=10_000
        ), mock.patch.object(server_v2, "_fetch_binance_kline_rows", side_effect=[first_chunk, second_chunk]) as fetch_mock:
            payload = server_v2.v2_market_candles(
                symbol="BTCUSDT",
                interval="30m",
                limit=4,
                startTime=0,
                endTime=0,
            )

        self.assertEqual([1, 1000, 2000, 3000], [item["openTime"] for item in payload["candles"]])
        self.assertEqual(
            [
                mock.call("BTCUSDT", "30m", 2, start_time_ms=0, end_time_ms=0),
                mock.call("BTCUSDT", "30m", 2, start_time_ms=0, end_time_ms=1999),
            ],
            fetch_mock.call_args_list,
        )

    def test_market_candles_should_reuse_short_lived_query_cache(self):
        rows = [
            [1000, "1.0", "2.0", "0.5", "1.5", "10.0", 1999, "20.0", 3],
        ]
        with mock.patch.object(server_v2, "_now_ms", side_effect=[5_000] * 8 + [5_100] * 8), mock.patch.object(
            server_v2,
            "_fetch_binance_kline_rows",
            return_value=rows,
        ) as fetch_mock:
            first = server_v2.v2_market_candles(
                symbol="BTCUSDT",
                interval="1h",
                limit=300,
                startTime=0,
                endTime=0,
            )
            second = server_v2.v2_market_candles(
                symbol="BTCUSDT",
                interval="1h",
                limit=300,
                startTime=0,
                endTime=0,
            )

        self.assertEqual(first["candles"], second["candles"])
        fetch_mock.assert_called_once_with(
            "BTCUSDT",
            "1h",
            300,
            start_time_ms=0,
            end_time_ms=0,
        )

    def test_account_snapshot_has_positions_and_orders(self):
        payload = server_v2._build_v2_account_snapshot_payload(
            account={"balance": 1000.0},
            positions=[{"symbol": "BTCUSD"}],
            orders=[{"symbol": "XAUUSD"}],
            server_time=303,
        )

        self.assertEqual(303, payload["serverTime"])
        self.assertEqual({"balance": 1000.0}, payload["account"])
        self.assertEqual([{"symbol": "BTCUSD"}], payload["positions"])
        self.assertEqual([{"symbol": "XAUUSD"}], payload["orders"])
        self.assertIn("syncToken", payload)

    def test_v2_account_snapshot_uses_light_snapshot_builder(self):
        light_snapshot = {
            "accountMeta": {
                "login": "7400048",
                "balance": 1000.0,
                "historyRevision": "stub-history-rev",
            },
            "overviewMetrics": [{"name": "总资产", "value": "$1000.00"}],
            "curveIndicators": [{"name": "当日收益", "value": "+1.2%"}],
            "statsMetrics": [{"name": "交易笔数", "value": "2"}],
            "positions": [{"symbol": "BTCUSD"}],
            "pendingOrders": [{"symbol": "XAUUSD"}],
        }
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
            server_v2, "_build_account_light_snapshot_with_cache", return_value=light_snapshot
        ) as light_mock, mock.patch.object(
            server_v2, "_build_snapshot_with_cache", side_effect=AssertionError("不应走重快照")
        ):
            payload = server_v2.v2_account_snapshot()

        light_mock.assert_called_once_with()
        self.assertEqual("BTCUSD", payload["positions"][0]["code"])
        self.assertEqual("XAUUSD", payload["orders"][0]["code"])
        self.assertEqual([{"name": "总资产", "value": "$1000.00"}], payload["overviewMetrics"])
        self.assertEqual([{"name": "当日收益", "value": "+1.2%"}], payload["curveIndicators"])
        self.assertEqual([{"name": "交易笔数", "value": "2"}], payload["statsMetrics"])
        self.assertEqual(1000.0, payload["account"]["balance"])

    def test_v2_account_snapshot_should_use_logged_out_snapshot_when_no_active_session(self):
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
            side_effect=AssertionError("logged_out 不应触发 MT5 轻快照构建"),
        ):
            payload = server_v2.v2_account_snapshot()

        self.assertEqual("", payload["accountMeta"]["login"])
        self.assertEqual("", payload["accountMeta"]["server"])
        self.assertEqual("remote_logged_out", payload["accountMeta"]["source"])
        self.assertEqual([], payload["positions"])
        self.assertEqual([], payload["orders"])

    def test_v2_account_snapshot_should_keep_canonical_identity_for_remote_active(self):
        light_snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "historyRevision": "history-7",
            },
            "positions": [],
            "pendingOrders": [],
            "overviewMetrics": [],
            "curveIndicators": [],
            "statsMetrics": [],
        }
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
            server_v2, "_build_account_light_snapshot_with_cache", return_value=light_snapshot
        ):
            payload = server_v2.v2_account_snapshot()

        self.assertEqual("7400048", payload["accountMeta"]["login"])
        self.assertEqual("ICMarketsSC-MT5-6", payload["accountMeta"]["server"])
        self.assertEqual("history-7", payload["accountMeta"]["historyRevision"])

    def test_v2_market_snapshot_uses_light_snapshot_builder(self):
        light_snapshot = {
            "accountMeta": {"login": "7400048", "historyRevision": "stub-history-rev"},
            "positions": [{"symbol": "BTCUSD"}],
            "pendingOrders": [{"symbol": "XAUUSD"}],
        }
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
            server_v2, "_build_account_light_snapshot_with_cache", return_value=light_snapshot
        ) as light_mock, mock.patch.object(
            server_v2, "_build_snapshot_with_cache", side_effect=AssertionError("不应走重快照")
        ), mock.patch.object(
            server_v2,
            "_build_v2_market_section",
            return_value={
                "source": "binance",
                "symbols": ["BTCUSDT", "XAUUSDT"],
                "symbolStates": [],
                "restUpstream": "https://fapi.binance.com",
                "wsUpstream": "wss://fstream.binance.com",
            },
        ):
            payload = server_v2.v2_market_snapshot()

        light_mock.assert_called_once_with()
        self.assertEqual("BTCUSD", payload["account"]["positions"][0]["code"])
        self.assertEqual("BTCUSDT", payload["account"]["positions"][0]["marketSymbol"])
        self.assertEqual("XAUUSD", payload["account"]["orders"][0]["code"])
        self.assertEqual("XAUUSDT", payload["account"]["orders"][0]["marketSymbol"])

    def test_build_market_stream_symbol_state_should_prefer_market_runtime_snapshot(self):
        runtime_state = {
            "productId": "BTC",
            "marketSymbol": "BTCUSDT",
            "tradeSymbol": "BTCUSD",
            "interval": "1m",
            "latestPrice": 102.5,
            "latestOpenTime": 1_710_000_000_000,
            "latestCloseTime": 1_710_000_059_999,
            "latestClosedCandle": {
                "symbol": "BTCUSDT",
                "interval": "1m",
                "openTime": 1_709_999_940_000,
                "closeTime": 1_709_999_999_999,
                "close": 101.0,
                "isClosed": True,
                "source": "binance-ws",
            },
            "latestPatch": {
                "symbol": "BTCUSDT",
                "interval": "1m",
                "openTime": 1_710_000_000_000,
                "closeTime": 1_710_000_059_999,
                "close": 102.5,
                "isClosed": False,
                "source": "binance-ws",
            },
        }
        runtime_stub = mock.Mock()
        runtime_stub.build_symbol_state.return_value = runtime_state

        with mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            side_effect=AssertionError("市场热路径不应再主动拉 REST K 线"),
        ):
            payload = server_v2._build_market_stream_symbol_state("BTCUSDT", 1_710_000_030_000)

        runtime_stub.build_symbol_state.assert_called_once()
        self.assertEqual(runtime_state, payload)

    def test_build_market_tick_message_should_carry_full_market_snapshot(self):
        payload = server_v2._build_market_tick_message(
            snapshot={
                "source": "binance",
                "symbolStates": [{"marketSymbol": "BTCUSDT", "latestPrice": 102.5}],
                "marketRuntimeConnected": True,
            },
            seq=7,
            published_at=1_710_000_060_000,
        )

        self.assertEqual("marketTick", payload["type"])
        self.assertEqual(7, payload["marketSeq"])
        self.assertEqual(1_710_000_060_000, payload["publishedAt"])
        self.assertEqual("binance", payload["market"]["source"])
        self.assertEqual("BTCUSDT", payload["market"]["symbolStates"][0]["marketSymbol"])

    def test_stream_event_should_prioritize_market_tick_when_new_market_seq_pending(self):
        with server_v2.snapshot_cache_lock:
            server_v2.market_realtime_publish_state.update({
                "seq": 5,
                "publishedAt": 2222,
                "latestSnapshot": {
                    "source": "binance",
                    "symbolStates": [{"marketSymbol": "BTCUSDT"}],
                },
            })
            server_v2.account_publish_state.update({
                "busSeq": 9,
                "publishedAt": 3333,
                "revisions": {
                    "marketRevision": "market-9",
                    "accountRuntimeRevision": "account-9",
                    "accountHistoryRevision": "history-9",
                    "abnormalRevision": "abnormal-9",
                },
                "event": {
                    "type": "syncEvent",
                    "busSeq": 9,
                    "publishedAt": 3333,
                    "revisions": {
                        "marketRevision": "market-9",
                        "accountRuntimeRevision": "account-9",
                        "accountHistoryRevision": "history-9",
                        "abnormalRevision": "abnormal-9",
                    },
                    "changes": {
                        "accountRuntime": {"snapshot": {"positions": []}},
                    },
                },
            })

        payload = server_v2._build_v2_stream_event_for_client(last_bus_seq=0, last_market_seq=0)

        self.assertEqual("marketTick", payload["type"])
        self.assertEqual(5, payload["marketSeq"])
        self.assertEqual("BTCUSDT", payload["market"]["symbolStates"][0]["marketSymbol"])

    def test_source_status_should_expose_market_runtime_fields(self):
        with mock.patch.object(
            server_v2,
            "_build_market_runtime_source_status",
            return_value={
                "marketRuntimeMode": "binance-ws",
                "marketRuntimeConnected": True,
                "marketRuntimeUpdatedAt": 1_710_000_060_000,
            },
            create=True,
        ):
            payload = server_v2.source_status()

        self.assertEqual("binance-ws", payload["marketRuntimeMode"])
        self.assertTrue(payload["marketRuntimeConnected"])
        self.assertEqual(1_710_000_060_000, payload["marketRuntimeUpdatedAt"])

    def test_v2_market_candles_should_use_runtime_patch_for_one_minute(self):
        closed_row = [1_709_999_940_000, "100.0", "101.0", "99.0", "100.5", "5.0", 1_709_999_999_999, "500.0", 2]
        stale_patch_row = [1_710_000_000_000, "100.5", "101.5", "100.0", "101.0", "6.0", 1_710_000_059_999, "600.0", 3]
        runtime_patch = {
            "k": {
                "t": 1_710_000_000_000,
                "T": 1_710_000_059_999,
                "s": "BTCUSDT",
                "i": "1m",
                "o": "100.5",
                "h": "102.0",
                "l": "100.0",
                "c": "101.8",
                "v": "8.0",
                "q": "800.0",
                "n": 4,
                "x": False,
            }
        }
        runtime_stub = mock.Mock()
        runtime_stub.get_latest_patch_row.return_value = runtime_patch

        with mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            return_value=[closed_row, stale_patch_row],
        ), mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), mock.patch.object(
            server_v2,
            "_now_ms",
            return_value=1_710_000_030_000,
        ), mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value={
                "latestPatch": {
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
                }
            },
        ):
            payload = server_v2.v2_market_candles("BTCUSDT", "1m", 2, 0, 0)

        self.assertEqual("binance-ws", payload["latestPatch"]["source"])
        self.assertEqual(101.8, payload["latestPatch"]["close"])
        self.assertEqual(1_709_999_940_000, payload["candles"][0]["openTime"])

    def test_v2_market_candles_should_use_runtime_aggregated_patch_for_fifteen_minute(self):
        closed_row = [1_709_999_100_000, "95.0", "98.0", "94.0", "97.0", "10.0", 1_709_999_999_999, "970.0", 5]
        runtime_stub = mock.Mock()
        runtime_stub.get_latest_patch_row.return_value = None
        runtime_stub.build_interval_patch.return_value = {
            "symbol": "BTCUSDT",
            "interval": "15m",
            "openTime": 1_710_000_000_000,
            "closeTime": 1_710_000_899_999,
            "open": 100.0,
            "high": 103.0,
            "low": 99.0,
            "close": 102.5,
            "volume": 9.0,
            "quoteVolume": 900.0,
            "tradeCount": 9,
            "isClosed": False,
            "source": "binance-ws",
        }

        with mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            return_value=[closed_row],
        ), mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), mock.patch.object(
            server_v2,
            "_now_ms",
            return_value=1_710_000_150_000,
        ), mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value={
                "latestPatch": {
                    "symbol": "BTCUSDT",
                    "interval": "1m",
                    "openTime": 1_710_000_120_000,
                    "closeTime": 1_710_000_179_999,
                    "open": 101.5,
                    "high": 103.0,
                    "low": 101.0,
                    "close": 102.5,
                    "volume": 4.0,
                    "quoteVolume": 400.0,
                    "tradeCount": 4,
                    "source": "binance-ws",
                    "isClosed": False,
                }
            },
        ):
            payload = server_v2.v2_market_candles("BTCUSDT", "15m", 2, 0, 0)

        runtime_stub.build_interval_patch.assert_called_once_with("BTCUSDT", "15m")
        self.assertEqual("binance-ws", payload["latestPatch"]["source"])
        self.assertEqual("15m", payload["latestPatch"]["interval"])
        self.assertEqual(102.5, payload["latestPatch"]["close"])

    def test_v2_market_candles_should_fallback_to_rest_patch_for_weekly_interval(self):
        closed_row = [1_709_395_200_000, "95.0", "98.0", "94.0", "97.0", "10.0", 1_709_999_999_999, "970.0", 5]
        rest_patch_row = [1_710_000_000_000, "100.0", "106.0", "99.0", "104.5", "12.0", 1_710_604_799_999, "1200.0", 8]
        runtime_stub = mock.Mock()
        runtime_stub.get_latest_patch_row.return_value = None
        runtime_stub.build_interval_patch.return_value = None

        with mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            return_value=[closed_row, rest_patch_row],
        ), mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), mock.patch.object(
            server_v2,
            "_now_ms",
            return_value=1_710_200_000_000,
        ), mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value={"latestPatch": None},
        ):
            payload = server_v2.v2_market_candles("BTCUSDT", "1w", 2, 0, 0)

        self.assertEqual("binance-rest", payload["latestPatch"]["source"])
        self.assertEqual("1w", payload["latestPatch"]["interval"])
        self.assertEqual(1_710_000_000_000, payload["latestPatch"]["openTime"])
        self.assertEqual(104.5, payload["latestPatch"]["close"])

    def test_v2_market_candles_should_fallback_to_rest_patch_for_monthly_interval(self):
        closed_row = [1_706_745_600_000, "95.0", "98.0", "94.0", "97.0", "10.0", 1_709_222_399_999, "970.0", 5]
        rest_patch_row = [1_709_222_400_000, "100.0", "108.0", "99.0", "105.5", "18.0", 1_711_900_799_999, "1800.0", 11]
        runtime_stub = mock.Mock()
        runtime_stub.get_latest_patch_row.return_value = None
        runtime_stub.build_interval_patch.return_value = None

        with mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            return_value=[closed_row, rest_patch_row],
        ), mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), mock.patch.object(
            server_v2,
            "_now_ms",
            return_value=1_710_000_000_000,
        ), mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value={"latestPatch": None},
        ):
            payload = server_v2.v2_market_candles("BTCUSDT", "1M", 2, 0, 0)

        self.assertEqual("binance-rest", payload["latestPatch"]["source"])
        self.assertEqual("1M", payload["latestPatch"]["interval"])
        self.assertEqual(1_709_222_400_000, payload["latestPatch"]["openTime"])
        self.assertEqual(105.5, payload["latestPatch"]["close"])

    def test_v2_market_candles_should_probe_current_week_bucket_when_incremental_rows_are_empty(self):
        current_week_patch_row = [1_776_643_200_000, "84000.0", "87000.0", "83500.0", "86123.5", "25.0", 1_777_247_999_999, "2150000.0", 18]
        runtime_stub = mock.Mock()
        runtime_stub.get_latest_patch_row.return_value = None
        runtime_stub.build_interval_patch.return_value = None

        with mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            return_value=[],
        ), mock.patch.object(
            server_v2,
            "_fetch_binance_kline_rows_resilient",
            return_value=[current_week_patch_row],
        ) as probe_mock, mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), mock.patch.object(
            server_v2,
            "_now_ms",
            return_value=1_777_107_200_000,
        ), mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value={"latestPatch": None},
        ):
            payload = server_v2.v2_market_candles("BTCUSDT", "1w", 3, 1_776_643_200_000, 0)

        probe_mock.assert_called_once_with(
            "BTCUSDT",
            "1w",
            1,
            start_time_ms=1_776_643_200_000,
            end_time_ms=0,
        )
        self.assertEqual("binance-rest", payload["latestPatch"]["source"])
        self.assertEqual("1w", payload["latestPatch"]["interval"])
        self.assertEqual(1_776_643_200_000, payload["latestPatch"]["openTime"])
        self.assertEqual(86123.5, payload["latestPatch"]["close"])

    def test_v2_market_candles_should_probe_current_month_bucket_when_incremental_rows_are_empty(self):
        current_month_patch_row = [1_775_001_600_000, "82000.0", "88000.0", "81000.0", "85321.0", "42.0", 1_777_593_599_999, "3560000.0", 27]
        runtime_stub = mock.Mock()
        runtime_stub.get_latest_patch_row.return_value = None
        runtime_stub.build_interval_patch.return_value = None

        with mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            return_value=[],
        ), mock.patch.object(
            server_v2,
            "_fetch_binance_kline_rows_resilient",
            return_value=[current_month_patch_row],
        ) as probe_mock, mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), mock.patch.object(
            server_v2,
            "_now_ms",
            return_value=1_777_107_200_000,
        ), mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value={"latestPatch": None},
        ):
            payload = server_v2.v2_market_candles("BTCUSDT", "1M", 3, 1_775_001_600_000, 0)

        probe_mock.assert_called_once_with(
            "BTCUSDT",
            "1M",
            1,
            start_time_ms=1_775_001_600_000,
            end_time_ms=0,
        )
        self.assertEqual("binance-rest", payload["latestPatch"]["source"])
        self.assertEqual("1M", payload["latestPatch"]["interval"])
        self.assertEqual(1_775_001_600_000, payload["latestPatch"]["openTime"])
        self.assertEqual(85321.0, payload["latestPatch"]["close"])

    def test_v2_market_candles_should_drop_stale_runtime_patch_for_one_minute(self):
        closed_row = [1_710_000_120_000, "102.0", "103.0", "101.0", "102.5", "9.0", 1_710_000_179_999, "900.0", 6]
        runtime_stub = mock.Mock()
        runtime_stub.get_latest_patch_row.return_value = {
            "k": {
                "t": 1_710_000_000_000,
                "T": 1_710_000_059_999,
                "s": "BTCUSDT",
                "i": "1m",
                "o": "100.5",
                "h": "102.0",
                "l": "100.0",
                "c": "101.8",
                "v": "8.0",
                "q": "800.0",
                "n": 4,
                "x": False,
            }
        }

        with mock.patch.object(
            server_v2,
            "_fetch_market_candle_rows_with_cache",
            return_value=[closed_row],
        ), mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), mock.patch.object(
            server_v2,
            "_now_ms",
            return_value=1_710_000_240_000,
        ), mock.patch.object(
            server_v2,
            "_build_market_runtime_symbol_state",
            return_value={
                "latestPatch": {
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
                }
            },
        ):
            payload = server_v2.v2_market_candles("BTCUSDT", "1m", 2, 0, 0)

        self.assertIsNone(payload["latestPatch"])

    def test_bootstrap_market_stream_runtime_should_seed_enough_minutes_for_daily_patch(self):
        runtime_stub = mock.Mock()
        runtime_stub.bootstrap_from_rest = None

        with mock.patch.object(server_v2, "market_stream_runtime", runtime_stub, create=True), \
                mock.patch.object(server_v2, "ABNORMAL_SYMBOLS", ["BTCUSDT"]), \
                mock.patch.object(
                    server_v2,
                    "_build_market_runtime_symbol_state",
                    return_value={"latestPatch": None, "latestClosedCandle": None},
                ), \
                mock.patch.object(server_v2, "_fetch_market_candle_rows_with_cache", return_value=[] ) as fetch_mock, \
                mock.patch.object(server_v2.v2_market_runtime, "bootstrap_symbol_from_rest_rows") as bootstrap_mock, \
                mock.patch.object(server_v2, "_now_ms", return_value=1_710_000_000_000):
            server_v2._bootstrap_market_stream_runtime_from_rest()

        fetch_mock.assert_called_once_with(
            "BTCUSDT",
            "1m",
            1440,
            start_time_ms=0,
            end_time_ms=0,
        )
        bootstrap_mock.assert_called_once()

    def test_v2_market_snapshot_should_use_logged_out_snapshot_when_no_active_session(self):
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
            side_effect=AssertionError("logged_out 不应触发 MT5 轻快照构建"),
        ), mock.patch.object(
            server_v2,
            "_build_v2_market_section",
            return_value={
                "source": "binance",
                "symbols": ["BTCUSDT", "XAUUSDT"],
                "symbolStates": [],
                "restUpstream": "https://fapi.binance.com",
                "wsUpstream": "wss://fstream.binance.com",
            },
        ):
            payload = server_v2.v2_market_snapshot()

        self.assertEqual("", payload["account"]["accountMeta"]["login"])
        self.assertEqual("", payload["account"]["accountMeta"]["server"])
        self.assertEqual("remote_logged_out", payload["account"]["accountMeta"]["source"])
        self.assertEqual([], payload["account"]["positions"])
        self.assertEqual([], payload["account"]["orders"])

    def test_admin_cache_clear_should_flush_runtime_caches(self):
        server_v2.snapshot_build_cache = {"build": {"snapshot": 1}}
        server_v2.snapshot_sync_cache = {"sync": {"snapshot": 2}}
        server_v2.account_publish_state.update({
            "runtimeSeq": 3,
            "busSeq": 4,
        })
        server_v2.v2_sync_state = {"seq": 3}

        payload = server_v2.admin_cache_clear()

        self.assertEqual(1, payload["cleared"]["snapshotBuildCache"])
        self.assertEqual(1, payload["cleared"]["snapshotSyncCache"])
        self.assertEqual(1, payload["cleared"]["accountPublishState"])
        self.assertEqual(1, payload["cleared"]["v2SyncState"])
        self.assertEqual(1, payload["cleared"]["v2BusState"])
        self.assertEqual({}, server_v2.snapshot_build_cache)
        self.assertEqual({}, server_v2.snapshot_sync_cache)
        self.assertEqual({}, server_v2.v2_sync_state)
        self.assertEqual(0, server_v2.account_publish_state["runtimeSeq"])
        self.assertEqual(0, server_v2.account_publish_state["busSeq"])

    def test_select_snapshot_should_use_mt5_pull_even_when_ea_snapshot_is_fresh(self):
        mt5_snapshot = {
            "accountMeta": {"source": "MT5 Python Pull", "updatedAt": 456},
            "overviewMetrics": [],
            "curvePoints": [{"timestamp": 1, "equity": 100.0, "balance": 100.0}],
            "curveIndicators": [],
            "positions": [],
            "pendingOrders": [],
            "trades": [],
            "statsMetrics": [],
        }

        with mock.patch.object(server_v2, "mt5", object()), mock.patch.object(
            server_v2, "_is_mt5_configured", return_value=True
        ), mock.patch.object(server_v2, "_is_ea_snapshot_fresh", return_value=True), mock.patch.object(
            server_v2, "_snapshot_from_mt5", return_value=mt5_snapshot
        ) as mt5_mock:
            snapshot = server_v2._select_snapshot("all")

        mt5_mock.assert_called_once_with("all")
        self.assertEqual("MT5 Python Pull", snapshot["accountMeta"]["source"])
        self.assertEqual(mt5_snapshot["curvePoints"], snapshot["curvePoints"])

    def test_select_snapshot_should_fail_when_mt5_pull_is_not_configured(self):
        with mock.patch.object(server_v2, "mt5", None), mock.patch.object(
            server_v2, "_is_mt5_configured", return_value=False
        ):
            with self.assertRaisesRegex(RuntimeError, "MT5 Python Pull is not configured"):
                server_v2._select_snapshot("all")

    def test_build_snapshot_with_cache_should_ignore_legacy_ea_snapshot_cache(self):
        server_v2.snapshot_build_cache.clear()
        now_ms = server_v2._now_ms()
        server_v2.snapshot_build_cache["all"] = {
            "builtAt": now_ms,
            "lastAccessAt": now_ms,
            "snapshot": {
                "accountMeta": {"source": "MT5 EA Push", "updatedAt": now_ms},
                "curvePoints": [{"timestamp": 1, "equity": 1.0, "balance": 1.0}],
            },
        }
        mt5_snapshot = {
            "accountMeta": {"source": "MT5 Python Pull", "updatedAt": now_ms + 1},
            "overviewMetrics": [],
            "curvePoints": [{"timestamp": 2, "equity": 2.0, "balance": 2.0}],
            "curveIndicators": [],
            "positions": [],
            "pendingOrders": [],
            "trades": [],
            "statsMetrics": [],
        }

        with mock.patch.object(server_v2, "_select_snapshot", return_value=mt5_snapshot) as select_mock:
            snapshot = server_v2._build_snapshot_with_cache("all")

        select_mock.assert_called_once_with("all")
        self.assertEqual("MT5 Python Pull", snapshot["accountMeta"]["source"])
        self.assertEqual(2, snapshot["curvePoints"][0]["timestamp"])

    def test_build_account_light_snapshot_with_cache_should_ignore_legacy_ea_snapshot_cache(self):
        server_v2.snapshot_build_cache.clear()
        now_ms = server_v2._now_ms()
        server_v2.snapshot_build_cache["account-light"] = {
            "builtAt": now_ms,
            "lastAccessAt": now_ms,
            "snapshot": {
                "accountMeta": {"source": "MT5 EA Push", "updatedAt": now_ms},
                "positions": [{"ticket": 1}],
                "pendingOrders": [],
            },
        }
        mt5_snapshot = {
            "accountMeta": {"source": "MT5 Python Pull", "updatedAt": now_ms + 1},
            "positions": [{"ticket": 2}],
            "pendingOrders": [],
        }

        with mock.patch.object(server_v2, "_build_account_light_snapshot", return_value=mt5_snapshot) as build_mock:
            snapshot = server_v2._build_account_light_snapshot_with_cache()

        build_mock.assert_called_once_with()
        self.assertEqual("MT5 Python Pull", snapshot["accountMeta"]["source"])
        self.assertEqual(2, snapshot["positions"][0]["ticket"])

    def test_build_account_light_snapshot_should_use_mt5_light_builder(self):
        light_snapshot = {
            "accountMeta": {"source": "MT5 Python Pull", "updatedAt": 456},
            "overviewMetrics": [{"name": "总资产", "value": "$1000.00"}],
            "curveIndicators": [],
            "statsMetrics": [],
            "positions": [{"symbol": "BTCUSD"}],
            "pendingOrders": [{"symbol": "XAUUSD"}],
        }

        with mock.patch.object(server_v2, "_snapshot_from_mt5_light", return_value=light_snapshot) as light_mock, mock.patch.object(
            server_v2, "_build_snapshot_with_cache", side_effect=AssertionError("轻快照不应回退到完整 all 快照")
        ):
            snapshot = server_v2._build_account_light_snapshot()

        light_mock.assert_called_once_with()
        self.assertEqual([{"name": "总资产", "value": "$1000.00"}], snapshot["overviewMetrics"])
        self.assertEqual([], snapshot["curveIndicators"])
        self.assertEqual([], snapshot["statsMetrics"])
        self.assertEqual("BTCUSD", snapshot["positions"][0]["symbol"])
        self.assertEqual("XAUUSD", snapshot["pendingOrders"][0]["symbol"])
        self.assertNotIn("trades", snapshot)
        self.assertNotIn("curvePoints", snapshot)

    def test_map_positions_should_include_mt5_open_time(self):
        fake_position = types.SimpleNamespace(
            symbol="BTCUSD",
            type=0,
            volume=0.1,
            price_open=100.0,
            price_current=105.0,
            profit=5.0,
            ticket=101,
            identifier=202,
            tp=120.0,
            sl=90.0,
            swap=-1.5,
            time_msc=1710000000123,
            time=1710000000,
        )
        fake_mt5 = types.SimpleNamespace(
            symbol_info=lambda _symbol: None,
            positions_get=lambda: [fake_position],
            orders_get=lambda: [],
        )

        with _configured_mt5_server_timezone("UTC"):
            with mock.patch.object(server_v2, "mt5", fake_mt5):
                payload = server_v2._map_positions()

        self.assertEqual(1710000000123, payload[0]["openTime"])

    def test_build_trade_history_with_cache_should_prefer_mt5_complete_history(self):
        fallback_snapshot = {
            "trades": [{"dealTicket": 1, "code": "BTCUSD"}],
        }
        mt5_trades = [
            {"dealTicket": 11, "tradeSymbol": "BTCUSD", "productName": "BTCUSD", "code": "BTCUSD"},
            {"dealTicket": 12, "tradeSymbol": "XAUUSD", "productName": "XAUUSD", "code": "XAUUSD"},
        ]
        server_v2.snapshot_build_cache.clear()

        with mock.patch.object(server_v2, "mt5", object()), mock.patch.object(
            server_v2, "_is_mt5_configured", return_value=True
        ), mock.patch.object(
            server_v2, "_snapshot_trades_from_mt5", return_value=mt5_trades
        ) as mt5_mock:
            trades = server_v2._build_trade_history_with_cache("all", fallback_snapshot)

        mt5_mock.assert_called_once_with("all")
        self.assertEqual(mt5_trades, trades)

    def test_build_trade_history_with_cache_should_use_canonical_snapshot_trades_directly(self):
        server_v2.snapshot_build_cache.clear()
        snapshot = {
            "accountMeta": {"source": "MT5 Python Pull", "updatedAt": 456},
            "trades": [
                {"dealTicket": 11, "code": "BTCUSD"},
                {"dealTicket": 12, "code": "XAUUSD"},
            ],
        }

        with mock.patch.object(server_v2, "mt5", object()), mock.patch.object(
            server_v2, "_is_mt5_configured", return_value=True
        ), mock.patch.object(
            server_v2, "_snapshot_trades_from_mt5", side_effect=AssertionError("不应二次拉取成交历史")
        ):
            trades = server_v2._build_trade_history_with_cache("all", snapshot)

        self.assertEqual([11, 12], [item["dealTicket"] for item in trades])

    def test_build_trade_history_with_cache_should_ignore_legacy_cached_history_without_canonical_source(self):
        server_v2.snapshot_build_cache.clear()
        now_ms = server_v2._now_ms()
        server_v2.snapshot_build_cache["all:trade-history"] = {
            "builtAt": now_ms,
            "lastAccessAt": now_ms,
            "trades": [{"dealTicket": 1, "code": "LEGACY"}],
        }
        snapshot = {
            "accountMeta": {"source": "MT5 Python Pull", "updatedAt": now_ms},
            "trades": [{"dealTicket": 11, "code": "BTCUSD"}],
        }

        with mock.patch.object(server_v2, "mt5", object()), mock.patch.object(
            server_v2, "_is_mt5_configured", return_value=True
        ), mock.patch.object(
            server_v2, "_snapshot_trades_from_mt5", side_effect=AssertionError("不应二次拉取成交历史")
        ):
            trades = server_v2._build_trade_history_with_cache("all", snapshot)

        self.assertEqual([11], [item["dealTicket"] for item in trades])

    def test_build_trade_history_with_cache_should_fail_instead_of_falling_back_to_ea_snapshot(self):
        server_v2.snapshot_build_cache.clear()
        fallback_snapshot = {
            "accountMeta": {"source": "MT5 EA Push", "updatedAt": 456},
            "trades": [{"dealTicket": 1, "code": "BTCUSD"}],
        }

        with mock.patch.object(server_v2, "mt5", object()), mock.patch.object(
            server_v2, "_is_mt5_configured", return_value=True
        ), mock.patch.object(
            server_v2, "_snapshot_trades_from_mt5", side_effect=RuntimeError("mt5 failed")
        ):
            with self.assertRaisesRegex(RuntimeError, "mt5 failed"):
                server_v2._build_trade_history_with_cache("all", fallback_snapshot)

    def test_v2_account_history_should_use_complete_trade_history_builder(self):
        snapshot = {
            "accountMeta": {"login": "7400048", "source": "MT5 EA Push"},
            "overviewMetrics": [{"name": "总资产", "value": "$1000.00"}],
            "curveIndicators": [{"name": "当日收益", "value": "+1.2%"}],
            "statsMetrics": [{"name": "交易笔数", "value": "2"}],
            "pendingOrders": [{
                "productId": "XAU",
                "marketSymbol": "XAUUSDT",
                "tradeSymbol": "XAUUSD",
                "productName": "XAUUSD",
                "quantity": 0.0,
                "pendingLots": 0.1,
                "pendingPrice": 2000.0,
                "latestPrice": 2000.0,
                "pendingCount": 1,
            }],
            "curvePoints": [{"timestamp": 1, "equity": 100.0, "balance": 100.0}],
            "trades": [{
                "dealTicket": 1,
                "productId": "BTC",
                "marketSymbol": "BTCUSDT",
                "tradeSymbol": "BTCUSD",
                "productName": "BTCUSD",
            }],
        }
        mt5_trades = [
            {
                "dealTicket": 11,
                "productId": "BTC",
                "marketSymbol": "BTCUSDT",
                "tradeSymbol": "BTCUSD",
                "productName": "BTCUSD",
            },
            {
                "dealTicket": 12,
                "productId": "XAU",
                "marketSymbol": "XAUUSDT",
                "tradeSymbol": "XAUUSD",
                "productName": "XAUUSD",
            },
        ]

        with mock.patch.object(
            server_v2.session_manager,
            "build_status_payload",
            return_value={
                "state": "activated",
                "activeAccount": {"login": "7400048", "server": "ICMarketsSC-MT5-6"},
                "savedAccounts": [],
                "savedAccountCount": 0,
            },
        ), mock.patch.object(server_v2, "_build_snapshot_with_cache", return_value=snapshot), mock.patch.object(
            server_v2, "_build_trade_history_with_cache", return_value=mt5_trades
        ) as history_mock:
            payload = server_v2.v2_account_history(range="all", cursor="")

        history_mock.assert_called_once_with("all", snapshot)
        self.assertEqual([11, 12], [item["dealTicket"] for item in payload["trades"]])
        self.assertEqual(1, payload["curvePoints"][0]["timestamp"])
        self.assertEqual(100.0, payload["curvePoints"][0]["equity"])
        self.assertEqual([{"name": "总资产", "value": "$1000.00"}], payload["overviewMetrics"])
        self.assertEqual([{"name": "当日收益", "value": "+1.2%"}], payload["curveIndicators"])
        self.assertEqual([{"name": "交易笔数", "value": "2"}], payload["statsMetrics"])

    def test_v2_account_history_should_use_logged_out_snapshot_when_no_active_session(self):
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
            "_build_snapshot_with_cache",
            side_effect=AssertionError("logged_out 不应触发完整 MT5 快照构建"),
        ), mock.patch.object(
            server_v2,
            "_build_trade_history_with_cache",
            side_effect=AssertionError("logged_out 不应触发 MT5 历史成交构建"),
        ):
            payload = server_v2.v2_account_history(range="all", cursor="")

        self.assertEqual("", payload["accountMeta"]["login"])
        self.assertEqual("", payload["accountMeta"]["server"])
        self.assertEqual("remote_logged_out", payload["accountMeta"]["source"])
        self.assertEqual([], payload["trades"])
        self.assertEqual([], payload["orders"])
        self.assertEqual([], payload["curvePoints"])

    def test_mt5_bridge_push_ea_should_not_default_missing_contract_size_to_one(self):
        source = (ROOT / "ea" / "MT5BridgePushEA.mq5").read_text(encoding="utf-8")

        self.assertIn("bool TryGetContractSize(string symbol, double &contractSize)", source)
        self.assertIn("missing contractSize for symbol=", source)
        self.assertIn("if(!BuildSnapshotParts(overviewMetrics, curvePoints, curveIndicators, positions, trades, stats))", source)
        self.assertNotIn("contractSize = 1.0;", source)
        self.assertNotIn("return 1.0;", source)


if __name__ == "__main__":
    unittest.main()

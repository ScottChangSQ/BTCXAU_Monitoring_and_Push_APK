"""v2 网关契约测试。"""

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


class V2ContractTests(unittest.TestCase):
    """验证 v2 契约最小结构。"""

    def test_market_snapshot_has_sync_token_and_market_section(self):
        payload = server_v2._build_v2_market_snapshot_payload({}, {}, 101)

        self.assertEqual(101, payload["serverTime"])
        self.assertIn("syncToken", payload)
        self.assertIn("market", payload)
        self.assertIn("account", payload)

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

    def test_market_candles_should_separate_latest_rest_row_as_patch_when_not_closed(self):
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
        self.assertEqual(2000, payload["latestPatch"]["openTime"])
        self.assertEqual("binance-rest", payload["latestPatch"]["source"])
        self.assertFalse(payload["latestPatch"]["isClosed"])

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
        with mock.patch.object(server_v2, "_now_ms", side_effect=[5_000, 5_000, 5_000, 5_100, 5_100, 5_100]), mock.patch.object(
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

    def test_v2_market_snapshot_uses_light_snapshot_builder(self):
        light_snapshot = {
            "accountMeta": {"login": "7400048", "historyRevision": "stub-history-rev"},
            "positions": [{"symbol": "BTCUSD"}],
            "pendingOrders": [{"symbol": "XAUUSD"}],
        }
        with mock.patch.object(
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

    def test_admin_cache_clear_should_flush_runtime_caches(self):
        server_v2.snapshot_build_cache = {"build": {"snapshot": 1}}
        server_v2.snapshot_sync_cache = {"sync": {"snapshot": 2}}
        server_v2.v2_sync_state = {"seq": 3}

        payload = server_v2.admin_cache_clear()

        self.assertEqual(1, payload["cleared"]["snapshotBuildCache"])
        self.assertEqual(1, payload["cleared"]["snapshotSyncCache"])
        self.assertEqual(1, payload["cleared"]["v2SyncState"])
        self.assertEqual({}, server_v2.snapshot_build_cache)
        self.assertEqual({}, server_v2.snapshot_sync_cache)
        self.assertEqual({}, server_v2.v2_sync_state)

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

    def test_build_trade_history_with_cache_should_prefer_mt5_complete_history(self):
        fallback_snapshot = {
            "trades": [{"dealTicket": 1, "code": "BTCUSD"}],
        }
        mt5_trades = [
            {"dealTicket": 11, "code": "BTCUSD"},
            {"dealTicket": 12, "code": "XAUUSD"},
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
            "pendingOrders": [{"symbol": "XAUUSD"}],
            "curvePoints": [{"timestamp": 1, "equity": 100.0, "balance": 100.0}],
            "trades": [{"dealTicket": 1, "code": "BTCUSD"}],
        }
        mt5_trades = [
            {"dealTicket": 11, "code": "BTCUSD"},
            {"dealTicket": 12, "code": "XAUUSD"},
        ]

        with mock.patch.object(server_v2, "_build_snapshot_with_cache", return_value=snapshot), mock.patch.object(
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


if __name__ == "__main__":
    unittest.main()

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
            "accountMeta": {"login": "7400048", "balance": 1000.0},
            "positions": [{"symbol": "BTCUSD"}],
            "pendingOrders": [{"symbol": "XAUUSD"}],
        }
        with mock.patch.object(
            server_v2, "_build_account_light_snapshot_with_cache", return_value=light_snapshot
        ) as light_mock, mock.patch.object(
            server_v2, "_build_snapshot_with_cache", side_effect=AssertionError("不应走重快照")
        ):
            payload = server_v2.v2_account_snapshot()

        light_mock.assert_called_once_with()
        self.assertEqual("BTCUSD", payload["positions"][0]["code"])
        self.assertEqual("XAUUSD", payload["orders"][0]["code"])

    def test_v2_market_snapshot_uses_light_snapshot_builder(self):
        light_snapshot = {
            "accountMeta": {"login": "7400048"},
            "positions": [{"symbol": "BTCUSD"}],
            "pendingOrders": [{"symbol": "XAUUSD"}],
        }
        with mock.patch.object(
            server_v2, "_build_account_light_snapshot_with_cache", return_value=light_snapshot
        ) as light_mock, mock.patch.object(
            server_v2, "_build_snapshot_with_cache", side_effect=AssertionError("不应走重快照")
        ):
            payload = server_v2.v2_market_snapshot()

        light_mock.assert_called_once_with()
        self.assertEqual([{"symbol": "BTCUSD"}], payload["account"]["positions"])
        self.assertEqual([{"symbol": "XAUUSD"}], payload["account"]["orders"])

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


if __name__ == "__main__":
    unittest.main()

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
            },
            "overviewMetrics": [],
            "curvePoints": [],
            "curveIndicators": [],
            "positions": positions,
            "pendingOrders": pending_orders,
            "trades": [],
            "statsMetrics": [],
        }

    def test_build_delta_payload_contains_market_and_account_sections(self):
        payload = server_v2._build_v2_delta_payload(
            market_delta=[{"type": "marketClosedCandle"}],
            account_delta=[{"type": "accountSnapshotChanged"}],
            server_time=404,
        )

        self.assertEqual(404, payload["serverTime"])
        self.assertEqual([{"type": "marketClosedCandle"}], payload["marketDelta"])
        self.assertEqual([{"type": "accountSnapshotChanged"}], payload["accountDelta"])
        self.assertIn("nextSyncToken", payload)

    def test_build_ws_message_has_type_and_sync_token(self):
        message = server_v2._build_v2_ws_message(
            message_type="marketPatch",
            payload={"symbol": "BTCUSDT"},
            sync_token="abc",
            server_time=505,
        )

        self.assertEqual("marketPatch", message["type"])
        self.assertEqual({"symbol": "BTCUSDT"}, message["payload"])
        self.assertEqual("abc", message["syncToken"])
        self.assertEqual(505, message["serverTime"])

    def test_sync_delta_bootstrap_returns_full_refresh_snapshot(self):
        snapshot = self._build_stub_snapshot(position_count=1, order_count=1)
        market = {
            "source": "binance",
            "symbols": ["BTCUSDT", "XAUUSDT"],
            "restUpstream": "https://fapi.binance.com",
            "wsUpstream": "wss://fstream.binance.com",
        }
        with mock.patch.object(server_v2, "_build_snapshot_with_cache", return_value=snapshot), mock.patch.object(
            server_v2, "_build_v2_market_section", return_value=market
        ):
            payload = server_v2.v2_sync_delta(syncToken="")

        self.assertTrue(payload["fullRefresh"]["required"])
        self.assertEqual("bootstrap", payload["fullRefresh"]["reason"])
        self.assertEqual(market, payload["fullRefresh"]["snapshot"]["market"])
        self.assertEqual(1, payload["summary"]["positionCount"])
        self.assertEqual(1, payload["summary"]["orderCount"])
        self.assertEqual([], payload["marketDelta"])
        self.assertEqual([], payload["accountDelta"])
        self.assertTrue(payload["nextSyncToken"])

    def test_sync_delta_with_previous_token_returns_account_delta(self):
        first_snapshot = self._build_stub_snapshot(position_count=0, order_count=0)
        second_snapshot = self._build_stub_snapshot(position_count=1, order_count=0)
        with mock.patch.object(
            server_v2,
            "_build_snapshot_with_cache",
            side_effect=[first_snapshot, second_snapshot],
        ):
            bootstrap = server_v2.v2_sync_delta(syncToken="")
            payload = server_v2.v2_sync_delta(syncToken=bootstrap["nextSyncToken"])

        self.assertFalse(payload["fullRefresh"]["required"])
        self.assertFalse(payload["unchanged"])
        self.assertEqual([], payload["marketDelta"])
        self.assertEqual(1, len(payload["accountDelta"]))
        self.assertEqual("accountSnapshotChanged", payload["accountDelta"][0]["type"])
        self.assertEqual(1, len(payload["accountDelta"][0]["positions"]["upsert"]))

    def test_v2_stream_sends_bootstrap_then_non_heartbeat_message(self):
        snapshot = self._build_stub_snapshot(position_count=1, order_count=1)

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
        with mock.patch.object(server_v2, "_build_snapshot_with_cache", return_value=snapshot), mock.patch.object(
            server_v2.asyncio, "sleep", new=_fast_sleep
        ):
            asyncio.run(server_v2.v2_stream(fake_client))

        self.assertTrue(fake_client.accepted)
        self.assertGreaterEqual(len(fake_client.messages), 2)
        self.assertEqual("syncBootstrap", fake_client.messages[0]["type"])
        self.assertIn("fullRefresh", fake_client.messages[0]["payload"])
        self.assertNotEqual("heartbeat", fake_client.messages[1]["type"])


if __name__ == "__main__":
    unittest.main()

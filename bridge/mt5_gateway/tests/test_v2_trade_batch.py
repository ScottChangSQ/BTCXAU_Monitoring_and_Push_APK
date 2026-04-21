"""v2 批量交易网关契约测试。"""

import sys
import types
import unittest
from pathlib import Path
from unittest import mock


def _install_test_stubs():
    """注入最小依赖桩，保证在精简环境也能导入 server_v2。"""
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


def _fake_mt5_result(retcode: int, *, comment: str = "", order: int = 0, deal: int = 0):
    return types.SimpleNamespace(retcode=retcode, comment=comment, order=order, deal=deal)


class V2TradeBatchTests(unittest.TestCase):
    """验证 v2 批量交易接口契约。"""

    def setUp(self):
        server_v2.trade_request_store.clear()
        if hasattr(server_v2, "batch_request_store"):
            server_v2.batch_request_store.clear()

    def test_batch_submit_should_require_batch_id_and_item_ids(self):
        payload = server_v2.v2_trade_batch_submit(
            {
                "batchId": "",
                "strategy": "BEST_EFFORT",
                "items": [
                    {
                        "itemId": "",
                        "action": "CLOSE_POSITION",
                        "params": {"symbol": "BTCUSD", "positionTicket": 1, "volume": 0.10},
                    }
                ],
            }
        )

        self.assertEqual("FAILED", payload["status"])
        self.assertEqual("TRADE_BATCH_INVALID_ID", payload["error"]["code"])

    def test_batch_submit_should_return_per_item_results(self):
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        send_result = _fake_mt5_result(retcode=0, comment="sent", order=7788, deal=8899)
        position_lookup = mock.Mock(
            side_effect=[
                {"ticket": 11, "positionTicket": 11, "symbol": "BTCUSD", "side": "buy"},
                {"ticket": 12, "positionTicket": 12, "symbol": "BTCUSD", "side": "buy"},
            ]
        )
        with mock.patch.object(server_v2, "_detect_account_mode", return_value="hedging"), mock.patch.object(
            server_v2, "_lookup_trade_position", position_lookup
        ), mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_trade_send_request", return_value=send_result
        ), mock.patch.object(server_v2, "_invalidate_account_runtime_cache_after_trade_commit"), mock.patch.object(
            server_v2, "_publish_account_trade_commit_sync_state"
        ):
            payload = server_v2.v2_trade_batch_submit(
                {
                    "batchId": "batch-close-001",
                    "strategy": "BEST_EFFORT",
                    "items": [
                        {
                            "itemId": "close-1",
                            "action": "CLOSE_POSITION",
                            "params": {"symbol": "BTCUSD", "positionTicket": 11, "volume": 0.10},
                        },
                        {
                            "itemId": "close-2",
                            "action": "CLOSE_POSITION",
                            "params": {"symbol": "BTCUSD", "positionTicket": 12, "volume": 0.20},
                        },
                    ],
                }
            )

        self.assertEqual("ACCEPTED", payload["status"])
        self.assertEqual(2, len(payload["items"]))
        self.assertEqual("close-1", payload["items"][0]["itemId"])
        self.assertEqual("ACCEPTED", payload["items"][0]["status"])

    def test_batch_submit_should_support_all_or_none(self):
        position_lookup = mock.Mock(
            side_effect=[
                {"ticket": 11, "positionTicket": 11, "symbol": "BTCUSD", "side": "buy"},
                None,
            ]
        )
        with mock.patch.object(server_v2, "_detect_account_mode", return_value="hedging"), mock.patch.object(
            server_v2, "_lookup_trade_position", position_lookup
        ), mock.patch.object(server_v2, "_trade_check_request", side_effect=AssertionError("ALL_OR_NONE 不应提前执行单项检查")), mock.patch.object(
            server_v2, "_trade_send_request", side_effect=AssertionError("ALL_OR_NONE 预校验失败时不应发送")
        ):
            payload = server_v2.v2_trade_batch_submit(
                {
                    "batchId": "batch-aon-001",
                    "strategy": "ALL_OR_NONE",
                    "items": [
                        {
                            "itemId": "ok-1",
                            "action": "CLOSE_POSITION",
                            "params": {"symbol": "BTCUSD", "positionTicket": 11, "volume": 0.10},
                        },
                        {
                            "itemId": "bad-2",
                            "action": "CLOSE_POSITION",
                            "params": {"symbol": "BTCUSD", "positionTicket": 0, "volume": 0.20},
                        },
                    ],
                }
            )

        self.assertEqual("FAILED", payload["status"])
        self.assertEqual("REJECTED", payload["items"][0]["status"])
        self.assertEqual("REJECTED", payload["items"][1]["status"])

    def test_batch_submit_should_keep_close_by_pairing(self):
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        send_result = _fake_mt5_result(retcode=0, comment="sent", order=8801, deal=9901)
        with mock.patch.object(server_v2, "_detect_account_mode", return_value="hedging"), mock.patch.object(
            server_v2, "_trade_check_request", return_value=check_result
        ), mock.patch.object(server_v2, "_trade_send_request", return_value=send_result):
            with mock.patch.object(server_v2, "_invalidate_account_runtime_cache_after_trade_commit"), mock.patch.object(
                server_v2, "_publish_account_trade_commit_sync_state"
            ):
                payload = server_v2.v2_trade_batch_submit(
                    {
                        "batchId": "batch-closeby-001",
                        "strategy": "GROUPED",
                        "items": [
                            {
                                "itemId": "pair-1a",
                                "action": "CLOSE_BY",
                                "params": {
                                    "symbol": "BTCUSD",
                                    "positionTicket": 101,
                                    "oppositePositionTicket": 202,
                                    "groupKey": "pair-1",
                                },
                            },
                            {
                                "itemId": "pair-1b",
                                "action": "CLOSE_BY",
                                "params": {
                                    "symbol": "BTCUSD",
                                    "positionTicket": 202,
                                    "oppositePositionTicket": 101,
                                    "groupKey": "pair-1",
                                },
                            },
                        ],
                    }
                )

        self.assertEqual("ACCEPTED", payload["status"])
        self.assertTrue(all(item["groupKey"] == "pair-1" for item in payload["items"]))

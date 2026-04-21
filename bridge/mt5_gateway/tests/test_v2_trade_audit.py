"""v2 交易审计契约测试。"""

import sys
import types
import unittest
from pathlib import Path
from unittest import mock


def _install_test_stubs():
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
                pass

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
        gzip_stub.GZipMiddleware = type("GZipMiddleware", (), {})
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


class V2TradeAuditTests(unittest.TestCase):
    def setUp(self):
        server_v2.trade_request_store.clear()
        server_v2.batch_request_store.clear()
        server_v2.trade_audit_store.clear()

    def test_trade_submit_and_result_should_leave_audit_timeline(self):
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        send_result = _fake_mt5_result(retcode=0, comment="sent", order=7001, deal=7002)
        request_payload = {
            "requestId": "req-audit-001",
            "action": "OPEN_MARKET",
            "params": {"symbol": "BTCUSD", "side": "buy", "volume": 0.1},
        }
        with mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_trade_send_request", return_value=send_result
        ), mock.patch.object(server_v2, "_detect_account_mode", return_value="hedging"):
            submit_payload = server_v2.v2_trade_submit(request_payload)

        self.assertEqual("ACCEPTED", submit_payload["status"])
        lookup_payload = server_v2.v2_trade_audit_lookup(id="req-audit-001")
        self.assertEqual("req-audit-001", lookup_payload["id"])
        self.assertTrue(any(item["stage"] == "submit" for item in lookup_payload["items"]))

        server_v2.v2_trade_result(requestId="req-audit-001")
        lookup_after_result = server_v2.v2_trade_audit_lookup(id="req-audit-001")
        self.assertTrue(any(item["stage"] == "result" for item in lookup_after_result["items"]))

    def test_trade_batch_submit_should_leave_batch_audit_entries(self):
        result = {
            "batchId": "batch-audit-001",
            "strategy": "BEST_EFFORT",
            "accountMode": "hedging",
            "status": "PARTIAL",
            "error": {"code": "TRADE_BATCH_PARTIAL", "message": "partial"},
            "items": [{"itemId": "item-1", "status": "ACCEPTED"}],
            "serverTime": 123,
        }
        with mock.patch.object(server_v2.v2_trade_batch, "submit_trade_batch", return_value=result), mock.patch.object(
            server_v2, "_detect_account_mode", return_value="hedging"
        ):
            payload = server_v2.v2_trade_batch_submit({"batchId": "batch-audit-001", "items": [{"itemId": "item-1"}]})

        self.assertEqual("PARTIAL", payload["status"])
        lookup_payload = server_v2.v2_trade_audit_lookup(id="batch-audit-001")
        self.assertTrue(any(item["traceType"] == "batch" for item in lookup_payload["items"]))
        self.assertTrue(any(item["stage"] == "batch_submit" for item in lookup_payload["items"]))

    def test_trade_audit_recent_should_return_latest_items_first(self):
        server_v2.trade_audit_store.append(
            trace_id="req-old",
            trace_type="single",
            action="OPEN_MARKET",
            symbol="BTCUSD",
            account_mode="hedging",
            stage="check",
            status="EXECUTABLE",
            error_code="",
            message="检查通过",
            server_time=100,
        )
        server_v2.trade_audit_store.append(
            trace_id="req-new",
            trace_type="single",
            action="OPEN_MARKET",
            symbol="BTCUSD",
            account_mode="hedging",
            stage="submit",
            status="ACCEPTED",
            error_code="",
            message="交易已受理",
            server_time=200,
        )

        payload = server_v2.v2_trade_audit_recent(limit=10)

        self.assertEqual(2, len(payload["items"]))
        self.assertEqual("req-new", payload["items"][0]["traceId"])


if __name__ == "__main__":
    unittest.main()

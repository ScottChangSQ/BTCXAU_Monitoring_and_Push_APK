"""v2 交易网关契约测试。"""

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


class V2TradeContractTests(unittest.TestCase):
    """验证 v2 交易接口契约。"""

    def setUp(self):
        server_v2.trade_request_store.clear()

    def test_trade_check_should_return_contract_shape(self):
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        with mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_detect_account_mode", return_value="hedging"
        ):
            payload = server_v2.v2_trade_check(
                {
                    "requestId": "req-check-001",
                    "action": "OPEN_MARKET",
                    "params": {"symbol": "BTCUSD", "side": "buy", "volume": 0.1},
                }
            )

        self.assertEqual("req-check-001", payload["requestId"])
        self.assertEqual("OPEN_MARKET", payload["action"])
        self.assertEqual("hedging", payload["accountMode"])
        self.assertEqual("EXECUTABLE", payload["status"])
        self.assertIn("check", payload)

    def test_trade_submit_should_be_idempotent_by_request_id(self):
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        send_result = _fake_mt5_result(retcode=0, comment="sent", order=7788, deal=8899)
        request_payload = {
            "requestId": "req-submit-001",
            "action": "OPEN_MARKET",
            "params": {"symbol": "BTCUSD", "side": "buy", "volume": 0.1},
        }
        with mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_trade_send_request", return_value=send_result
        ) as send_mock, mock.patch.object(server_v2, "_detect_account_mode", return_value="netting"):
            first = server_v2.v2_trade_submit(request_payload)
            second = server_v2.v2_trade_submit(request_payload)

        self.assertEqual("ACCEPTED", first["status"])
        self.assertEqual("DUPLICATE", second["status"])
        self.assertEqual("TRADE_DUPLICATE_SUBMISSION", second["error"]["code"])
        self.assertEqual(1, send_mock.call_count)

    def test_trade_check_should_distinguish_error_codes(self):
        check_result = _fake_mt5_result(retcode=10019, comment="no money")
        with mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_detect_account_mode", return_value="netting"
        ):
            payload = server_v2.v2_trade_check(
                {
                    "requestId": "req-check-002",
                    "action": "OPEN_MARKET",
                    "params": {"symbol": "BTCUSD", "side": "buy", "volume": 0.1},
                }
            )

        self.assertEqual("NOT_EXECUTABLE", payload["status"])
        self.assertEqual("TRADE_INSUFFICIENT_MARGIN", payload["error"]["code"])

    def test_trade_check_should_reject_invalid_volume_step(self):
        with mock.patch.object(server_v2, "_detect_account_mode", return_value="hedging"):
            payload = server_v2.v2_trade_check(
                {
                    "requestId": "req-check-003",
                    "action": "OPEN_MARKET",
                    "params": {"symbol": "BTCUSD", "side": "buy", "volume": 0.015},
                }
            )

        self.assertEqual("NOT_EXECUTABLE", payload["status"])
        self.assertEqual("TRADE_INVALID_VOLUME_STEP", payload["error"]["code"])

    def test_trade_result_should_return_recorded_submit_result(self):
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        send_result = _fake_mt5_result(retcode=0, comment="sent", order=9001, deal=9002)
        request_payload = {
            "requestId": "req-submit-002",
            "action": "OPEN_MARKET",
            "params": {"symbol": "BTCUSD", "side": "buy", "volume": 0.1},
        }
        with mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_trade_send_request", return_value=send_result
        ), mock.patch.object(server_v2, "_detect_account_mode", return_value="netting"):
            submit_payload = server_v2.v2_trade_submit(request_payload)

        result_payload = server_v2.v2_trade_result(requestId="req-submit-002")
        self.assertEqual(submit_payload["requestId"], result_payload["requestId"])
        self.assertEqual("ACCEPTED", result_payload["status"])
        self.assertEqual(9001, result_payload["result"]["order"])

    def test_close_position_should_reject_when_target_position_not_found(self):
        with mock.patch.object(server_v2, "_detect_account_mode", return_value="netting"), mock.patch.object(
            server_v2, "_lookup_trade_position", return_value=None
        ), mock.patch.object(server_v2, "_trade_check_request", side_effect=AssertionError("不应执行 order_check")):
            payload = server_v2.v2_trade_submit(
                {
                    "requestId": "req-close-001",
                    "action": "CLOSE_POSITION",
                    "params": {"symbol": "BTCUSD", "volume": 0.1},
                }
            )

        self.assertEqual("FAILED", payload["status"])
        self.assertEqual("TRADE_INVALID_POSITION", payload["error"]["code"])

    def test_close_position_should_split_netting_hedging_unknown_modes(self):
        with mock.patch.object(server_v2, "_detect_account_mode", return_value="hedging"), mock.patch.object(
            server_v2, "_lookup_trade_position", return_value=None
        ):
            hedging_payload = server_v2.v2_trade_submit(
                {
                    "requestId": "req-close-002-a",
                    "action": "CLOSE_POSITION",
                    "params": {"symbol": "BTCUSD", "volume": 0.1},
                }
            )
        self.assertEqual("TRADE_INVALID_POSITION", hedging_payload["error"]["code"])

        with mock.patch.object(server_v2, "_detect_account_mode", return_value="unknown"), mock.patch.object(
            server_v2, "_lookup_trade_position", return_value=None
        ):
            unknown_payload = server_v2.v2_trade_submit(
                {
                    "requestId": "req-close-002-b",
                    "action": "CLOSE_POSITION",
                    "params": {"symbol": "BTCUSD", "volume": 0.1, "positionTicket": 1},
                }
            )
        self.assertEqual("TRADE_UNSAFE_ACCOUNT_MODE", unknown_payload["error"]["code"])

        check_result = _fake_mt5_result(retcode=0, comment="ok")
        send_result = _fake_mt5_result(retcode=0, comment="sent", order=7001, deal=7002)
        with mock.patch.object(server_v2, "_detect_account_mode", return_value="netting"), mock.patch.object(
            server_v2, "_lookup_trade_position", return_value={"ticket": 101, "positionTicket": 101, "symbol": "BTCUSD", "side": "buy"}
        ), mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_trade_send_request", return_value=send_result
        ):
            netting_payload = server_v2.v2_trade_submit(
                {
                    "requestId": "req-close-002-c",
                    "action": "CLOSE_POSITION",
                    "params": {"symbol": "BTCUSD", "volume": 0.1},
                }
            )
        self.assertEqual("ACCEPTED", netting_payload["status"])

    def test_trade_submit_should_mark_unknown_when_order_send_raises_after_check_passed(self):
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        with mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_trade_send_request", side_effect=RuntimeError("network timeout")
        ), mock.patch.object(server_v2, "_detect_account_mode", return_value="netting"):
            payload = server_v2.v2_trade_submit(
                {
                    "requestId": "req-submit-unknown-001",
                    "action": "OPEN_MARKET",
                    "params": {"symbol": "BTCUSD", "side": "buy", "volume": 0.1},
                }
            )

        self.assertEqual("ACCEPTED", payload["status"])
        self.assertEqual("TRADE_RESULT_UNKNOWN", payload["error"]["code"])

    def test_trade_submit_should_reject_same_request_id_with_different_payload(self):
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        send_result = _fake_mt5_result(retcode=0, comment="sent", order=8801, deal=8802)
        with mock.patch.object(server_v2, "_trade_check_request", return_value=check_result), mock.patch.object(
            server_v2, "_trade_send_request", return_value=send_result
        ), mock.patch.object(server_v2, "_detect_account_mode", return_value="netting"):
            first = server_v2.v2_trade_submit(
                {
                    "requestId": "req-submit-003",
                    "action": "OPEN_MARKET",
                    "params": {"symbol": "BTCUSD", "side": "buy", "volume": 0.1},
                }
            )
            second = server_v2.v2_trade_submit(
                {
                    "requestId": "req-submit-003",
                    "action": "OPEN_MARKET",
                    "params": {"symbol": "BTCUSD", "side": "sell", "volume": 0.1},
                }
            )

        self.assertEqual("ACCEPTED", first["status"])
        self.assertEqual("FAILED", second["status"])
        self.assertEqual("TRADE_DUPLICATE_PAYLOAD_MISMATCH", second["error"]["code"])

    def test_hedging_wrong_ticket_should_not_fallback_to_symbol_position(self):
        fake_positions = [
            types.SimpleNamespace(ticket=2001, identifier=2001, symbol="BTCUSD", type=0),
            types.SimpleNamespace(ticket=2002, identifier=2002, symbol="BTCUSD", type=1),
        ]
        fake_mt5 = types.SimpleNamespace(positions_get=lambda: fake_positions, symbol_info=lambda _symbol: None)
        check_result = _fake_mt5_result(retcode=0, comment="ok")
        send_result = _fake_mt5_result(retcode=0, comment="sent", order=9901, deal=9902)

        with mock.patch.object(server_v2, "mt5", fake_mt5), mock.patch.object(
            server_v2, "_detect_account_mode", return_value="hedging"
        ), mock.patch.object(server_v2, "_trade_check_request", return_value=check_result) as check_mock, mock.patch.object(
            server_v2, "_trade_send_request", return_value=send_result
        ) as send_mock:
            payload = server_v2.v2_trade_submit(
                {
                    "requestId": "req-close-hedging-ticket-001",
                    "action": "CLOSE_POSITION",
                    "params": {"symbol": "BTCUSD", "volume": 0.1, "positionTicket": 9999},
                }
            )

        self.assertEqual("FAILED", payload["status"])
        self.assertEqual("TRADE_INVALID_POSITION", payload["error"]["code"])
        check_mock.assert_not_called()
        send_mock.assert_not_called()


if __name__ == "__main__":
    unittest.main()

"""异常交易服务端同步测试。"""

import sys
import types
import unittest
from pathlib import Path


def _install_test_stubs():
    """为异常交易网关测试注入最小依赖桩，避免缺少服务端依赖时无法导入模块。"""
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


class AbnormalGatewayTests(unittest.TestCase):
    """验证异常交易服务端配置与增量同步行为。"""

    def setUp(self):
        self._refresh_impl = server_v2._refresh_abnormal_state
        self._reset_state()

    def tearDown(self):
        server_v2._refresh_abnormal_state = self._refresh_impl
        self._reset_state()

    def test_evaluate_abnormal_kline_respects_and_or_mode(self):
        config = {
            "symbol": "BTCUSDT",
            "volumeThreshold": 1000.0,
            "amountThreshold": 70000000.0,
            "priceChangeThreshold": 200.0,
            "volumeEnabled": True,
            "amountEnabled": True,
            "priceChangeEnabled": True,
        }
        kline = {
            "volume": 1200.0,
            "amount": 1000000.0,
            "priceChange": 50.0,
        }

        or_result = server_v2._evaluate_abnormal_kline(kline, config, use_and_mode=False)
        and_result = server_v2._evaluate_abnormal_kline(kline, config, use_and_mode=True)

        self.assertTrue(or_result["abnormal"])
        self.assertFalse(and_result["abnormal"])
        self.assertEqual("成交量", or_result["summary"])

    def test_build_abnormal_response_returns_delta_for_new_records(self):
        server_v2._refresh_abnormal_state = lambda: None
        server_v2._set_abnormal_config({"logicAnd": False, "configs": []})
        with server_v2.abnormal_state_lock:
            server_v2.abnormal_record_store[:] = [self._build_record("r1", "BTCUSDT", 1000, "成交量")]
            server_v2.abnormal_alert_store[:] = [self._build_alert("a1", ["BTCUSDT"], 1000, "BTC 的 成交量 出现异常！")]
            server_v2._commit_abnormal_snapshot_locked()

        first = server_v2._build_abnormal_response(since_seq=0, delta=True)

        with server_v2.abnormal_state_lock:
            server_v2.abnormal_record_store.insert(0, self._build_record("r2", "XAUUSD", 2000, "价格变化"))
            server_v2.abnormal_alert_store.insert(0, self._build_alert("a2", ["XAUUSD"], 2000, "XAU 的 价格变化 出现异常！"))
            server_v2._commit_abnormal_snapshot_locked()

        second = server_v2._build_abnormal_response(
            since_seq=first["abnormalMeta"]["syncSeq"],
            delta=True,
        )

        self.assertFalse(first["isDelta"])
        self.assertTrue(second["isDelta"])
        self.assertFalse(second["unchanged"])
        self.assertEqual(1, len(second["delta"]["records"]))
        self.assertEqual("r2", second["delta"]["records"][0]["id"])
        self.assertEqual(1, len(second["delta"]["alerts"]))
        self.assertEqual("a2", second["delta"]["alerts"][0]["id"])

    def test_build_abnormal_response_should_fallback_to_cached_snapshot_when_refresh_fails(self):
        original_fetch = server_v2._fetch_recent_closed_binance_klines
        server_v2._fetch_recent_closed_binance_klines = lambda symbol, limit: (_ for _ in ()).throw(RuntimeError("upstream failed"))
        server_v2._set_abnormal_config({"logicAnd": False, "configs": []})
        with server_v2.abnormal_state_lock:
            server_v2.abnormal_record_store[:] = [self._build_record("r1", "BTCUSDT", 1000, "成交量")]
            server_v2.abnormal_alert_store[:] = []
            server_v2._commit_abnormal_snapshot_locked()
        try:
            response = server_v2._build_abnormal_response(since_seq=0, delta=True)
        finally:
            server_v2._fetch_recent_closed_binance_klines = original_fetch

        self.assertFalse(response["isDelta"])
        self.assertEqual("r1", response["records"][0]["id"])
        self.assertIn("warning", response["abnormalMeta"])

    def _reset_state(self):
        with server_v2.abnormal_state_lock:
            server_v2.abnormal_config_state.clear()
            server_v2.abnormal_config_state.update({"logicAnd": False, "symbols": {}})
            server_v2.abnormal_record_store.clear()
            server_v2.abnormal_alert_store.clear()
            server_v2.abnormal_last_close_time_by_symbol.clear()
            server_v2.abnormal_last_notify_at.clear()
            server_v2.abnormal_kline_cache.clear()
            server_v2.abnormal_sync_state.clear()

    def _build_record(self, record_id, symbol, close_time, summary):
        return {
            "id": record_id,
            "symbol": symbol,
            "timestamp": close_time + 1,
            "closeTime": close_time,
            "openPrice": 100.0,
            "closePrice": 101.0,
            "volume": 1000.0,
            "amount": 1000000.0,
            "priceChange": 1.0,
            "percentChange": 1.0,
            "triggerSummary": summary,
        }

    def _build_alert(self, alert_id, symbols, close_time, content):
        return {
            "id": alert_id,
            "symbols": list(symbols),
            "title": "异常提醒",
            "content": content,
            "closeTime": close_time,
            "createdAt": close_time + 2,
        }


if __name__ == "__main__":
    unittest.main()

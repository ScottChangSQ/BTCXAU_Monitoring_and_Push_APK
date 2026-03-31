"""MT5 网关摘要响应测试。"""

import sys
import types
import unittest
from pathlib import Path


def _install_test_stubs():
    """为网关测试注入最小依赖桩，避免本地缺少服务端依赖时无法导入模块。"""
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

        class _HTTPException(Exception):
            def __init__(self, status_code=500, detail=""):
                super().__init__(detail)
                self.status_code = status_code
                self.detail = detail

        fastapi_stub.FastAPI = _FastAPI
        fastapi_stub.HTTPException = _HTTPException
        fastapi_stub.Header = lambda default=None, **kwargs: default
        fastapi_stub.Query = lambda default=None, **kwargs: default
        sys.modules["fastapi"] = fastapi_stub

    if "fastapi.middleware.gzip" not in sys.modules:
        gzip_stub = types.ModuleType("fastapi.middleware.gzip")

        class _GZipMiddleware:
            pass

        gzip_stub.GZipMiddleware = _GZipMiddleware
        sys.modules["fastapi.middleware.gzip"] = gzip_stub


_install_test_stubs()

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import server_v2  # noqa: E402


class SummaryResponseTests(unittest.TestCase):
    """验证后台摘要响应会去掉大体积列表字段。"""

    def test_build_summary_response_omits_heavy_collections(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "updatedAt": 1774868116886,
                "tradeCount": 5,
                "positionCount": 6,
            },
            "overviewMetrics": [{"name": "Total Asset", "value": "$17,854.56"}],
            "curvePoints": [{"timestamp": 1, "equity": 100.0, "balance": 100.0}],
            "curveIndicators": [{"name": "1D Return", "value": "-0.58%"}],
            "positions": [{"code": "BTCUSD", "quantity": 0.05}],
            "pendingOrders": [{"code": "XAUUSD", "pendingLots": 0.02}],
            "trades": [{"dealTicket": 1779714434, "profit": -67.17}],
            "statsMetrics": [{"name": "Cumulative Profit", "value": "-$104.25"}],
        }

        builder = getattr(server_v2, "_build_summary_response", None)
        self.assertIsNotNone(builder, "缺少 _build_summary_response，尚未实现摘要接口")

        response = builder(snapshot, 7)

        self.assertEqual("7400048", response["accountMeta"]["login"])
        self.assertEqual(7, response["accountMeta"]["syncSeq"])
        self.assertFalse(response["isDelta"])
        self.assertFalse(response["unchanged"])
        self.assertEqual(snapshot["overviewMetrics"], response["overviewMetrics"])
        self.assertEqual(snapshot["statsMetrics"], response["statsMetrics"])
        self.assertNotIn("curvePoints", response)
        self.assertNotIn("curveIndicators", response)
        self.assertNotIn("positions", response)
        self.assertNotIn("pendingOrders", response)
        self.assertNotIn("trades", response)

    def test_build_live_response_keeps_positions_only(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "updatedAt": 1774868116886,
            },
            "overviewMetrics": [{"name": "Total Asset", "value": "$17,854.56"}],
            "curvePoints": [{"timestamp": 1, "equity": 100.0, "balance": 100.0}],
            "curveIndicators": [{"name": "1D Return", "value": "-0.58%"}],
            "positions": [{"code": "BTCUSD", "quantity": 0.05}],
            "pendingOrders": [{"code": "XAUUSD", "pendingLots": 0.02}],
            "trades": [{"dealTicket": 1779714434, "profit": -67.17}],
            "statsMetrics": [{"name": "Cumulative Profit", "value": "-$104.25"}],
        }

        builder = getattr(server_v2, "_build_live_response", None)
        self.assertIsNotNone(builder, "缺少 _build_live_response，尚未实现轻实时持仓接口")

        response = builder(snapshot, 3)

        self.assertEqual(3, response["accountMeta"]["syncSeq"])
        self.assertEqual(snapshot["overviewMetrics"], response["overviewMetrics"])
        self.assertEqual(snapshot["statsMetrics"], response["statsMetrics"])
        self.assertEqual(snapshot["positions"], response["positions"])
        self.assertNotIn("curvePoints", response)
        self.assertNotIn("curveIndicators", response)
        self.assertNotIn("pendingOrders", response)
        self.assertNotIn("trades", response)

    def test_build_trades_response_keeps_trades_only(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "updatedAt": 1774868116886,
            },
            "overviewMetrics": [{"name": "Total Asset", "value": "$17,854.56"}],
            "positions": [{"code": "BTCUSD", "quantity": 0.05}],
            "pendingOrders": [{"code": "XAUUSD", "pendingLots": 0.02}],
            "trades": [{"dealTicket": 1779714434, "profit": -67.17}],
            "statsMetrics": [{"name": "Cumulative Profit", "value": "-$104.25"}],
        }

        builder = getattr(server_v2, "_build_trades_response", None)
        self.assertIsNotNone(builder, "缺少 _build_trades_response，尚未实现成交增量接口")

        response = builder(snapshot, 5)

        self.assertEqual(5, response["accountMeta"]["syncSeq"])
        self.assertEqual(snapshot["trades"], response["trades"])
        self.assertNotIn("positions", response)
        self.assertNotIn("pendingOrders", response)
        self.assertNotIn("curvePoints", response)
        self.assertNotIn("overviewMetrics", response)


if __name__ == "__main__":
    unittest.main()

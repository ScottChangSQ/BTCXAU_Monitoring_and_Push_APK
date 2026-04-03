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


class SummaryResponseTests(unittest.TestCase):
    """验证后台摘要响应会去掉大体积列表字段。"""

    def test_map_trades_pairs_partial_close_with_fifo_open_batches(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def history_deals_get(from_time, to_time):
                return [
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=0,
                        volume=1.0,
                        ticket=101,
                        order=201,
                        position_id=301,
                        entry=0,
                        time=100,
                        price=100.0,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open-1",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=0,
                        volume=1.0,
                        ticket=102,
                        order=202,
                        position_id=301,
                        entry=0,
                        time=200,
                        price=110.0,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open-2",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=1,
                        volume=1.5,
                        ticket=103,
                        order=203,
                        position_id=301,
                        entry=1,
                        time=300,
                        price=120.0,
                        profit=30.0,
                        commission=0.0,
                        swap=0.0,
                        comment="close",
                    ),
                ]

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        server_v2.mt5 = _FakeMt5()
        try:
            trades = server_v2._map_trades("1d")
        finally:
            server_v2.mt5 = original_mt5

        self.assertEqual(2, len(trades))
        ordered = sorted(trades, key=lambda item: item["openTime"])
        self.assertEqual([100000, 200000], [item["openTime"] for item in ordered])
        self.assertEqual([300000, 300000], [item["closeTime"] for item in ordered])
        self.assertEqual("Buy", ordered[0]["side"])
        self.assertEqual("Buy", ordered[1]["side"])
        self.assertAlmostEqual(100.0, ordered[0]["openPrice"])
        self.assertAlmostEqual(110.0, ordered[1]["openPrice"])
        self.assertAlmostEqual(1.0, ordered[0]["quantity"])
        self.assertAlmostEqual(0.5, ordered[1]["quantity"])
        self.assertAlmostEqual(20.0, ordered[0]["profit"])
        self.assertAlmostEqual(10.0, ordered[1]["profit"])

    def test_trim_cache_entries_locked_keeps_newest_entries_only(self):
        helper = getattr(server_v2, "_trim_cache_entries_locked", None)
        self.assertIsNotNone(helper, "缺少 _trim_cache_entries_locked，无法验证快照缓存裁剪")

        cache = {
            "1d:snapshot": {"seq": 1},
            "7d:snapshot": {"seq": 2},
            "1m:curve": {"seq": 3},
        }

        helper(cache, 2)

        self.assertEqual(["7d:snapshot", "1m:curve"], list(cache.keys()))

    def test_should_slide_snapshot_build_cache_only_for_fresh_ea_snapshot(self):
        helper = getattr(server_v2, "_should_slide_snapshot_build_cache", None)
        self.assertIsNotNone(helper, "缺少 _should_slide_snapshot_build_cache，无法验证缓存平滑命中")

        original_is_fresh = server_v2._is_ea_snapshot_fresh
        try:
            server_v2._is_ea_snapshot_fresh = lambda: True
            self.assertTrue(helper({
                "builtAt": 1000,
                "snapshot": {"accountMeta": {"source": "MT5 EA Push"}},
            }, 9500))

            server_v2._is_ea_snapshot_fresh = lambda: False
            self.assertFalse(helper({
                "builtAt": 1000,
                "snapshot": {"accountMeta": {"source": "MT5 EA Push"}},
            }, 9500))

            server_v2._is_ea_snapshot_fresh = lambda: True
            self.assertFalse(helper({
                "builtAt": 1000,
                "snapshot": {"accountMeta": {"source": "MT5 Python Pull"}},
            }, 9500))
        finally:
            server_v2._is_ea_snapshot_fresh = original_is_fresh

    def test_ingest_ea_snapshot_clears_snapshot_sync_cache_when_payload_changes(self):
        server_v2.snapshot_build_cache.clear()
        server_v2.snapshot_sync_cache.clear()
        server_v2.snapshot_build_cache["7d"] = {"builtAt": 1, "snapshot": {"accountMeta": {"source": "old"}}}
        server_v2.snapshot_sync_cache["7d:snapshot"] = {"seq": 9, "snapshot": {"accountMeta": {"source": "old"}}}

        payload = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 EA Push",
                "updatedAt": 1774868116886,
            },
            "overviewMetrics": [],
            "curveIndicators": [],
            "curvePoints": [],
            "positions": [],
            "pendingOrders": [],
            "trades": [],
            "statsMetrics": [],
        }

        response = server_v2.ingest_ea_snapshot(payload, x_bridge_token=None)

        self.assertTrue(response["changed"])
        self.assertEqual({}, server_v2.snapshot_build_cache)
        self.assertEqual({}, server_v2.snapshot_sync_cache)

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

    def test_rebuild_curve_includes_open_positions(self):
        helper = getattr(server_v2, "_replay_curve_from_history", None)
        self.assertIsNotNone(helper, "缺少 _replay_curve_from_history，无法验证曲线重算")

        deals = [
            {
                "timestamp": 1000,
                "price": 100.0,
                "profit": 0.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 0,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "XAUUSD",
                "position_id": 1,
            },
            {
                "timestamp": 1500,
                "price": 110.0,
                "profit": 10.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 1,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "XAUUSD",
                "position_id": 1,
            },
        ]

        open_positions = [
            {
                "positionTicket": 99,
                "code": "BTCUSD",
                "productName": "BTCUSD",
                "side": "Buy",
                "quantity": 0.5,
                "costPrice": 200.0,
                "latestPrice": 210.0,
            }
        ]

        points = helper(
            deal_history=deals,
            start_balance=1000.0,
            open_positions=open_positions,
            current_balance=1010.0,
            current_equity=1030.0,
            leverage=10.0,
            contract_size_fn=lambda symbol: 1.0,
            now_ms=2000,
        )

        self.assertGreater(len(points), 2)
        self.assertAlmostEqual(points[0]["equity"] - points[0]["balance"], 5.0)
        self.assertTrue(
            any(abs(point["equity"] - point["balance"]) > 0.0 for point in points[:-1]),
            "曲线点应在有未平仓时 equity 与 balance 不一致",
        )
        self.assertIn("positionRatio", points[0])
        self.assertGreater(points[0]["positionRatio"], 0.0)
        self.assertEqual(points[-1]["balance"], 1010.0)
        self.assertEqual(points[-1]["equity"], 1030.0)
        self.assertAlmostEqual(points[-1]["positionRatio"], 10.5 / 1030.0, places=6)

    def test_curve_point_digest_includes_position_ratio(self):
        helper = getattr(server_v2, "_normalize_digest_curve_points", None)
        self.assertIsNotNone(helper, "缺少 _normalize_digest_curve_points，无法验证曲线摘要")

        low_ratio = helper([{"timestamp": 1, "equity": 100.0, "balance": 100.0, "positionRatio": 0.10}])
        high_ratio = helper([{"timestamp": 1, "equity": 100.0, "balance": 100.0, "positionRatio": 0.60}])

        self.assertNotEqual(low_ratio, high_ratio)


if __name__ == "__main__":
    unittest.main()

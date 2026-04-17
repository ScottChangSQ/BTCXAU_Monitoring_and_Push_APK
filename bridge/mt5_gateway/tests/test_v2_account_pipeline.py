"""v2 账户流水线单测。"""

import sys
import types
import unittest
from pathlib import Path
from unittest import mock


def _install_test_stubs():
    """为 server_v2 注入最小依赖桩，避免本地缺少运行时依赖时无法导入。"""
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

from bridge.mt5_gateway import v2_account  # noqa: E402
import server_v2  # noqa: E402


class V2AccountPipelineTests(unittest.TestCase):
    """覆盖 v2 账户 helper 的关键行为。"""

    def setUp(self):
        if hasattr(server_v2, "snapshot_build_cache"):
            server_v2.snapshot_build_cache.clear()
        if hasattr(server_v2, "snapshot_sync_cache"):
            server_v2.snapshot_sync_cache.clear()
        if hasattr(server_v2, "account_publish_state") and hasattr(server_v2, "_reset_account_publish_state_locked"):
            with server_v2.snapshot_cache_lock:
                server_v2._reset_account_publish_state_locked()
        if hasattr(server_v2, "v2_sync_state"):
            server_v2.v2_sync_state.clear()

    def test_build_account_snapshot_model_contains_core_totals(self):
        payload = v2_account.build_account_snapshot_model(
            {
                "balance": 1000.0,
                "equity": 1010.0,
                "margin": 20.0,
                "freeMargin": 990.0,
                "marginLevel": 5050.0,
                "profit": 10.0,
                "positions": [],
                "orders": [],
            }
        )
        self.assertEqual(1000.0, payload["balance"])
        self.assertEqual(1010.0, payload["equity"])
        self.assertEqual(20.0, payload["margin"])
        self.assertEqual(990.0, payload["freeMargin"])
        self.assertEqual(5050.0, payload["marginLevel"])
        self.assertEqual(10.0, payload["profit"])
        self.assertEqual([], payload["positions"])
        self.assertEqual([], payload["orders"])

    def test_build_account_snapshot_model_should_preserve_zero_metric_values(self):
        payload = v2_account.build_account_snapshot_model(
            {
                "balance": 1000.0,
                "equity": 1000.0,
                "margin": 50.0,
                "freeMargin": 950.0,
                "profit": 12.0,
                "accountMetrics": {
                    "balance": 0.0,
                    "equity": 0.0,
                    "margin": 0.0,
                    "freeMargin": 0.0,
                    "marginLevel": 0.0,
                    "profit": 0.0,
                },
                "positions": [],
                "orders": [],
            }
        )

        self.assertEqual(0.0, payload["balance"])
        self.assertEqual(0.0, payload["equity"])
        self.assertEqual(0.0, payload["margin"])
        self.assertEqual(0.0, payload["freeMargin"])
        self.assertEqual(0.0, payload["marginLevel"])
        self.assertEqual(0.0, payload["profit"])

    def test_build_account_history_model_exposes_curve_and_trades(self):
        payload = v2_account.build_account_history_model(
            {
                "trades": [{
                    "timestamp": 1,
                    "productId": "BTC",
                    "marketSymbol": "BTCUSDT",
                    "tradeSymbol": "BTCUSD",
                    "productName": "BTCUSD",
                    "side": "Buy",
                    "price": 10,
                    "quantity": 0.1,
                }],
                "orders": [{
                    "productId": "XAU",
                    "marketSymbol": "XAUUSDT",
                    "tradeSymbol": "XAUUSD",
                    "productName": "XAUUSD",
                    "side": "Sell",
                    "quantity": 0.0,
                    "pendingPrice": 20,
                    "pendingLots": 0.2,
                    "latestPrice": 20.0,
                    "pendingCount": 1,
                }],
                "curvePoints": [{"timestamp": 3, "equity": 1100.0, "balance": 1000.0, "positionRatio": 0.1}],
            }
        )
        self.assertIn("trades", payload)
        self.assertIn("orders", payload)
        self.assertIn("curvePoints", payload)
        self.assertEqual(1, len(payload["trades"]))
        self.assertEqual(1, len(payload["orders"]))
        self.assertEqual(1, len(payload["curvePoints"]))
        self.assertEqual("BTCUSD", payload["trades"][0]["code"])
        self.assertEqual("XAUUSD", payload["orders"][0]["code"])
        self.assertEqual(0.1, payload["curvePoints"][0]["positionRatio"])

    def test_build_account_history_model_keeps_trade_lifecycle_fields(self):
        payload = v2_account.build_account_history_model(
            {
                "trades": [{
                    "timestamp": 200,
                    "productId": "BTC",
                    "marketSymbol": "BTCUSDT",
                    "tradeSymbol": "BTCUSD",
                    "productName": "BTCUSD",
                    "side": "Sell",
                    "price": 120.0,
                    "quantity": 0.1,
                    "profit": 8.0,
                    "fee": 1.5,
                    "storageFee": -3.0,
                    "openTime": 100,
                    "closeTime": 200,
                    "openPrice": 100.0,
                    "closePrice": 120.0,
                    "dealTicket": 12,
                    "orderId": 102,
                    "positionId": 201,
                    "entryType": 1,
                }],
                "orders": [],
                "curvePoints": [],
            }
        )

        trade = payload["trades"][0]
        self.assertEqual(100, trade["openTime"])
        self.assertEqual(200, trade["closeTime"])
        self.assertEqual(100.0, trade["openPrice"])
        self.assertEqual(120.0, trade["closePrice"])
        self.assertEqual(-3.0, trade["storageFee"])
        self.assertEqual(12, trade["dealTicket"])
        self.assertEqual(102, trade["orderId"])
        self.assertEqual(201, trade["positionId"])
        self.assertEqual(1, trade["entryType"])

    def test_build_account_history_model_should_not_backfill_trade_fields(self):
        payload = v2_account.build_account_history_model(
            {
                "trades": [{
                    "timestamp": 200,
                    "productId": "BTC",
                    "marketSymbol": "BTCUSDT",
                    "tradeSymbol": "BTCUSD",
                    "productName": "BTCUSD",
                    "price": 120.0,
                    "quantity": 0.1,
                }],
                "orders": [],
                "curvePoints": [{"timestamp": 3, "equity": 1100.0, "balance": 1000.0}],
            }
        )

        trade = payload["trades"][0]
        curve_point = payload["curvePoints"][0]
        self.assertEqual(0, trade["openTime"])
        self.assertEqual(0, trade["closeTime"])
        self.assertEqual(0.0, trade["openPrice"])
        self.assertEqual(0.0, trade["closePrice"])
        self.assertEqual(0.0, curve_point["positionRatio"])

    def test_build_account_history_model_should_ignore_legacy_history_aliases(self):
        payload = v2_account.build_account_history_model(
            {
                "dealHistory": [{"time": 1, "symbol": "BTCUSD", "price": 10.0, "volume": 0.1}],
                "orderHistory": [{"symbol": "XAUUSD", "price": 20.0, "volume": 0.2}],
                "equityCurve": [{"time": 3, "equity": 1100.0, "balance": 1000.0, "positionRatio": 0.1}],
            }
        )

        self.assertEqual([], payload["trades"])
        self.assertEqual([], payload["orders"])
        self.assertEqual([], payload["curvePoints"])

    def test_build_account_history_model_should_not_backfill_trade_price_from_lifecycle_fields(self):
        payload = v2_account.build_account_history_model(
            {
                "trades": [{
                    "timestamp": 200,
                    "productId": "BTC",
                    "marketSymbol": "BTCUSDT",
                    "tradeSymbol": "BTCUSD",
                    "productName": "BTCUSD",
                    "side": "Sell",
                    "openPrice": 100.0,
                    "closePrice": 120.0,
                    "openTime": 100,
                    "closeTime": 200,
                }],
                "orders": [],
                "curvePoints": [],
            }
        )

        trade = payload["trades"][0]
        self.assertEqual(0.0, trade["price"])
        self.assertEqual(100.0, trade["openPrice"])
        self.assertEqual(120.0, trade["closePrice"])

    def test_build_account_snapshot_model_keeps_position_and_order_identifiers(self):
        payload = v2_account.build_account_snapshot_model(
            {
                "positions": [{
                    "productId": "BTC",
                    "marketSymbol": "BTCUSDT",
                    "tradeSymbol": "BTCUSD",
                    "productName": "BTCUSD",
                    "positionTicket": 101,
                    "orderId": 202,
                    "openTime": 1710000000123,
                    "quantity": 0.1,
                    "costPrice": 100.0,
                    "latestPrice": 105.0,
                    "takeProfit": 120.0,
                    "stopLoss": 90.0,
                    "storageFee": -1.5,
                }],
                "orders": [{
                    "productId": "XAU",
                    "marketSymbol": "XAUUSDT",
                    "tradeSymbol": "XAUUSD",
                    "productName": "XAUUSD",
                    "orderId": 303,
                    "openTime": 1710000000456,
                    "quantity": 0.0,
                    "pendingLots": 0.2,
                    "pendingPrice": 2050.0,
                    "latestPrice": 2051.0,
                    "takeProfit": 2070.0,
                    "stopLoss": 2030.0,
                    "pendingCount": 1,
                }],
            }
        )

        position = payload["positions"][0]
        order = payload["orders"][0]
        self.assertEqual(101, position["positionTicket"])
        self.assertEqual(202, position["orderId"])
        self.assertEqual(1710000000123, position["openTime"])
        self.assertEqual(120.0, position["takeProfit"])
        self.assertEqual(90.0, position["stopLoss"])
        self.assertEqual(-1.5, position["storageFee"])
        self.assertEqual(303, order["orderId"])
        self.assertEqual(1710000000456, order["openTime"])
        self.assertEqual(2051.0, order["latestPrice"])
        self.assertEqual(2070.0, order["takeProfit"])
        self.assertEqual(2030.0, order["stopLoss"])
        self.assertEqual(1, order["pendingCount"])

    def test_build_account_history_model_should_require_canonical_trade_symbol_fields(self):
        with self.assertRaisesRegex(RuntimeError, "trade missing tradeSymbol"):
            v2_account.build_account_history_model(
                {
                    "trades": [{
                        "timestamp": 1,
                        "productName": "BTCUSD",
                        "price": 10.0,
                        "quantity": 0.1,
                    }],
                    "orders": [],
                    "curvePoints": [],
                }
            )

    def test_build_account_snapshot_model_should_require_matching_product_name(self):
        with self.assertRaisesRegex(RuntimeError, "position productName must equal tradeSymbol"):
            v2_account.build_account_snapshot_model(
                {
                    "positions": [{
                        "productId": "BTC",
                        "marketSymbol": "BTCUSDT",
                        "tradeSymbol": "BTCUSD",
                        "productName": "BTC",
                    }],
                    "orders": [],
                }
            )

    def test_build_account_snapshot_model_should_ignore_legacy_snapshot_aliases(self):
        payload = v2_account.build_account_snapshot_model(
            {
                "openPositions": [{"symbol": "BTCUSD", "quantity": 0.1}],
                "accountPositions": [{"symbol": "XAUUSD", "quantity": 0.2}],
                "pendingOrders": [{"symbol": "BTCUSD", "pendingLots": 0.1}],
                "pending": [{"symbol": "XAUUSD", "pendingLots": 0.2}],
            }
        )

        self.assertEqual([], payload["positions"])
        self.assertEqual([], payload["orders"])

    def test_build_account_snapshot_response_keeps_meta(self):
        payload = v2_account.build_account_snapshot_response(
            {
                "balance": 1000.0,
                "positions": [],
                "orders": [],
                "overviewMetrics": [{"name": "总资产", "value": "$1000.00"}],
                "curveIndicators": [{"name": "当日收益", "value": "+1.2%"}],
                "statsMetrics": [{"name": "交易笔数", "value": "2"}],
            },
            {"login": "1"},
            sync_seq=7,
        )
        self.assertEqual("1", payload["accountMeta"]["login"])
        self.assertEqual(7, payload["accountMeta"]["syncSeq"])
        self.assertEqual(1000.0, payload["balance"])
        self.assertEqual([{"name": "总资产", "value": "$1000.00"}], payload["overviewMetrics"])
        self.assertEqual([{"name": "当日收益", "value": "+1.2%"}], payload["curveIndicators"])
        self.assertEqual([{"name": "交易笔数", "value": "2"}], payload["statsMetrics"])

    def test_build_account_full_response_keeps_meta_and_sections(self):
        payload = v2_account.build_account_full_response(
            {
                "account": {"balance": 1000.0},
                "positions": [],
                "orders": [],
                "trades": [],
                "curvePoints": [],
                "overviewMetrics": [{"name": "总资产", "value": "$1000.00"}],
                "curveIndicators": [{"name": "当日收益", "value": "+1.2%"}],
                "statsMetrics": [{"name": "交易笔数", "value": "2"}],
            },
            {"login": "1", "historyRevision": "hist-1"},
            sync_seq=9,
        )

        self.assertEqual("1", payload["accountMeta"]["login"])
        self.assertEqual("hist-1", payload["accountMeta"]["historyRevision"])
        self.assertEqual(9, payload["accountMeta"]["syncSeq"])
        self.assertEqual({"balance": 1000.0}, payload["account"])
        self.assertEqual([], payload["positions"])
        self.assertEqual([], payload["orders"])
        self.assertEqual([], payload["trades"])
        self.assertEqual([], payload["curvePoints"])
        self.assertEqual([{"name": "总资产", "value": "$1000.00"}], payload["overviewMetrics"])
        self.assertEqual([{"name": "当日收益", "value": "+1.2%"}], payload["curveIndicators"])
        self.assertEqual([{"name": "交易笔数", "value": "2"}], payload["statsMetrics"])

    def test_v2_account_full_should_use_complete_snapshot_builder(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "currency": "USD",
                "leverage": 500,
                "historyRevision": "hist-7400048",
            },
            "overviewMetrics": [{"name": "总资产", "value": "$1000.00"}],
            "curveIndicators": [{"name": "当日收益", "value": "+1.2%"}],
            "statsMetrics": [{"name": "交易笔数", "value": "2"}],
            "positions": [{
                "productId": "BTC",
                "marketSymbol": "BTCUSDT",
                "tradeSymbol": "BTCUSD",
                "productName": "BTCUSD",
            }],
            "pendingOrders": [{
                "productId": "XAU",
                "marketSymbol": "XAUUSDT",
                "tradeSymbol": "XAUUSD",
                "productName": "XAUUSD",
                "pendingLots": 0.2,
                "pendingPrice": 2000.0,
                "latestPrice": 2000.0,
                "pendingCount": 1,
            }],
            "trades": [{
                "timestamp": 1,
                "productId": "BTC",
                "marketSymbol": "BTCUSDT",
                "tradeSymbol": "BTCUSD",
                "productName": "BTCUSD",
            }],
            "curvePoints": [{"timestamp": 1, "equity": 1000.0, "balance": 1000.0, "positionRatio": 0.0}],
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
        ), mock.patch.object(server_v2, "_build_snapshot_with_cache", return_value=snapshot) as snapshot_mock, mock.patch.object(
            server_v2, "_build_account_light_snapshot_with_cache", side_effect=AssertionError("full 不应走轻快照")
        ), mock.patch.object(
            server_v2, "_build_trade_history_with_cache", side_effect=AssertionError("full 不应单独拼历史")
        ):
            payload = server_v2.v2_account_full()

        snapshot_mock.assert_called_once_with("all")
        self.assertEqual("7400048", payload["accountMeta"]["login"])
        self.assertEqual("hist-7400048", payload["accountMeta"]["historyRevision"])
        self.assertEqual("BTCUSD", payload["positions"][0]["code"])
        self.assertEqual("XAUUSD", payload["orders"][0]["code"])
        self.assertEqual("BTCUSD", payload["trades"][0]["code"])
        self.assertEqual(1, payload["curvePoints"][0]["timestamp"])

    def test_v2_account_full_should_use_logged_out_snapshot_when_no_active_session(self):
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
            server_v2, "_build_snapshot_with_cache", side_effect=AssertionError("logged_out 不应触发完整快照构建")
        ):
            payload = server_v2.v2_account_full()

        self.assertEqual("", payload["accountMeta"]["login"])
        self.assertEqual("", payload["accountMeta"]["server"])
        self.assertEqual("remote_logged_out", payload["accountMeta"]["source"])
        self.assertIn("historyRevision", payload["accountMeta"])
        self.assertEqual([], payload["positions"])
        self.assertEqual([], payload["orders"])
        self.assertEqual([], payload["trades"])
        self.assertEqual([], payload["curvePoints"])

    def test_v2_account_full_should_fail_when_history_revision_missing(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "currency": "USD",
                "leverage": 500,
            },
            "positions": [],
            "pendingOrders": [],
            "trades": [],
            "curvePoints": [],
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
        ), mock.patch.object(server_v2, "_build_snapshot_with_cache", return_value=snapshot):
            with self.assertRaisesRegex(Exception, "historyRevision"):
                server_v2.v2_account_full()

    def test_v2_bus_publish_should_not_advance_bus_seq_when_runtime_unchanged(self):
        runtime_snapshot = {
            "marketDigest": "market-1",
            "accountRevision": "account-1",
            "historyRevision": "history-1",
            "digest": "digest-1",
            "market": {"symbols": []},
            "account": {"accountMeta": {}, "positions": [], "orders": []},
            "tradeCount": 0,
            "curvePointCount": 0,
        }
        abnormal_state = {"seq": 1, "snapshot": {"records": [], "alerts": []}, "previousSnapshot": None, "lastError": ""}

        with mock.patch.object(server_v2, "_build_v2_sync_runtime_snapshot", return_value=runtime_snapshot), \
             mock.patch.object(server_v2, "_refresh_abnormal_state"), \
             mock.patch.object(server_v2, "_read_abnormal_sync_state_for_bus", return_value=abnormal_state):
            server_v2._v2_bus_publish_current_state()
            first_state = server_v2._read_account_publish_state()
            server_v2._v2_bus_publish_current_state()
            second_state = server_v2._read_account_publish_state()

        self.assertEqual(1, first_state["busSeq"])
        self.assertEqual(1, second_state["busSeq"])
        self.assertEqual("digest-1", second_state["runtimeDigest"])

    def test_v2_bus_publish_should_only_advance_once_when_runtime_changes_once(self):
        abnormal_state = {"seq": 1, "snapshot": {"records": [], "alerts": []}, "previousSnapshot": None, "lastError": ""}
        runtime_snapshots = [
            {
                "marketDigest": "market-1",
                "accountRevision": "account-1",
                "historyRevision": "history-1",
                "digest": "digest-1",
                "market": {"symbols": []},
                "account": {"accountMeta": {}, "positions": [], "orders": []},
                "tradeCount": 0,
                "curvePointCount": 0,
            },
            {
                "marketDigest": "market-2",
                "accountRevision": "account-2",
                "historyRevision": "history-2",
                "digest": "digest-2",
                "market": {"symbols": []},
                "account": {"accountMeta": {}, "positions": [], "orders": []},
                "tradeCount": 1,
                "curvePointCount": 0,
            },
            {
                "marketDigest": "market-2",
                "accountRevision": "account-2",
                "historyRevision": "history-2",
                "digest": "digest-2",
                "market": {"symbols": []},
                "account": {"accountMeta": {}, "positions": [], "orders": []},
                "tradeCount": 1,
                "curvePointCount": 0,
            },
        ]

        with mock.patch.object(server_v2, "_build_v2_sync_runtime_snapshot", side_effect=runtime_snapshots), \
             mock.patch.object(server_v2, "_refresh_abnormal_state"), \
             mock.patch.object(server_v2, "_read_abnormal_sync_state_for_bus", return_value=abnormal_state):
            server_v2._v2_bus_publish_current_state()
            server_v2._v2_bus_publish_current_state()
            server_v2._v2_bus_publish_current_state()

        state = server_v2._read_account_publish_state()
        self.assertEqual(2, state["busSeq"])
        self.assertEqual(2, state["runtimeSeq"])
        self.assertEqual("digest-2", state["runtimeDigest"])

    def test_trade_commit_should_invalidate_runtime_once_and_publish_new_history_revision(self):
        server_v2.snapshot_build_cache["all"] = {"snapshot": {"accountMeta": {"historyRevision": "stale"}}}
        server_v2.snapshot_sync_cache["all:snapshot"] = {"seq": 9}
        first_snapshot = {
            "marketDigest": "market-1",
            "accountRevision": "account-1",
            "historyRevision": "history-1",
            "digest": "digest-1",
            "market": {"symbols": []},
            "account": {"accountMeta": {}, "positions": [], "orders": []},
            "tradeCount": 0,
            "curvePointCount": 0,
        }
        second_snapshot = {
            "marketDigest": "market-1",
            "accountRevision": "account-2",
            "historyRevision": "history-2",
            "digest": "digest-2",
            "market": {"symbols": []},
            "account": {"accountMeta": {}, "positions": [], "orders": []},
            "tradeCount": 1,
            "curvePointCount": 0,
        }
        abnormal_state = {"seq": 1, "snapshot": {"records": [], "alerts": []}, "previousSnapshot": None, "lastError": ""}

        with mock.patch.object(server_v2, "_build_v2_sync_runtime_snapshot", side_effect=[first_snapshot, second_snapshot]), \
             mock.patch.object(server_v2, "_refresh_abnormal_state"), \
             mock.patch.object(server_v2, "_read_abnormal_sync_state_for_bus", return_value=abnormal_state):
            server_v2._v2_bus_publish_current_state()
            server_v2._invalidate_account_runtime_cache_after_trade_commit()
            self.assertEqual({}, server_v2.snapshot_build_cache)
            self.assertEqual({}, server_v2.snapshot_sync_cache)
            server_v2._publish_account_trade_commit_sync_state()

        stream_event = server_v2._build_v2_stream_event_for_client(last_bus_seq=1)
        self.assertEqual("history-2", stream_event["revisions"]["accountHistoryRevision"])
        self.assertEqual("syncEvent", stream_event["type"])

    def test_build_account_history_response_keeps_meta(self):
        payload = v2_account.build_account_history_response(
            {
                "trades": [],
                "orders": [],
                "curvePoints": [],
                "overviewMetrics": [{"name": "总资产", "value": "$1000.00"}],
                "curveIndicators": [{"name": "当日收益", "value": "+1.2%"}],
                "statsMetrics": [{"name": "交易笔数", "value": "2"}],
            },
            {"server": "demo"},
            sync_seq=8,
        )
        self.assertEqual("demo", payload["accountMeta"]["server"])
        self.assertEqual(8, payload["accountMeta"]["syncSeq"])
        self.assertEqual([], payload["curvePoints"])
        self.assertEqual([{"name": "总资产", "value": "$1000.00"}], payload["overviewMetrics"])
        self.assertEqual([{"name": "当日收益", "value": "+1.2%"}], payload["curveIndicators"])
        self.assertEqual([{"name": "交易笔数", "value": "2"}], payload["statsMetrics"])


if __name__ == "__main__":
    unittest.main()

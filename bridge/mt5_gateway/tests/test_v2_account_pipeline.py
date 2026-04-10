"""v2 账户流水线单测。"""

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from bridge.mt5_gateway import v2_account  # noqa: E402


class V2AccountPipelineTests(unittest.TestCase):
    """覆盖 v2 账户 helper 的关键行为。"""

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

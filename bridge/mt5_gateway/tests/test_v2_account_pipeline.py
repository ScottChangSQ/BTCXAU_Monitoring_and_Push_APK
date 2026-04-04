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

    def test_build_account_history_model_exposes_curve_and_trades(self):
        payload = v2_account.build_account_history_model(
            {
                "trades": [{"time": 1, "symbol": "BTCUSD", "price": 10, "volume": 0.1}],
                "orders": [{"symbol": "XAUUSD", "price": 20, "volume": 0.2}],
                "curvePoints": [{"timestamp": 3, "equity": 1100.0, "balance": 1000.0}],
            }
        )
        self.assertIn("trades", payload)
        self.assertIn("orders", payload)
        self.assertIn("curvePoints", payload)
        self.assertEqual(1, len(payload["trades"]))
        self.assertEqual(1, len(payload["orders"]))
        self.assertEqual(1, len(payload["curvePoints"]))

    def test_build_account_history_model_keeps_trade_lifecycle_fields(self):
        payload = v2_account.build_account_history_model(
            {
                "trades": [{
                    "timestamp": 200,
                    "symbol": "BTCUSD",
                    "code": "BTCUSD",
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

    def test_build_account_snapshot_response_keeps_meta(self):
        payload = v2_account.build_account_snapshot_response(
            {"balance": 1000.0, "positions": [], "orders": []},
            {"login": "1"},
            sync_seq=7,
        )
        self.assertEqual("1", payload["accountMeta"]["login"])
        self.assertEqual(7, payload["accountMeta"]["syncSeq"])
        self.assertEqual(1000.0, payload["balance"])

    def test_build_account_history_response_keeps_meta(self):
        payload = v2_account.build_account_history_response(
            {"trades": [], "orders": [], "curvePoints": []},
            {"server": "demo"},
            sync_seq=8,
        )
        self.assertEqual("demo", payload["accountMeta"]["server"])
        self.assertEqual(8, payload["accountMeta"]["syncSeq"])
        self.assertEqual([], payload["curvePoints"])


if __name__ == "__main__":
    unittest.main()

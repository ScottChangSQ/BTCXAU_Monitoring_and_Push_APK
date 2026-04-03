"""v2 市场流水线单测，确保 REST 与 patch 可以拼装成 v2 响应。"""

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from bridge.mt5_gateway import v2_market  # noqa: E402


class V2MarketPipelineTests(unittest.TestCase):
    """覆盖 v2 market helper 的关键行为。"""

    def test_build_market_candle_payload_from_rest_row(self):
        rest_row = [1000, "1.0", "2.0", "0.5", "1.5", "10.0", 1999, "20.0", 3]
        candle = v2_market.build_market_candle_payload(
            symbol="BTCUSDT",
            interval="1m",
            row=rest_row,
            is_closed=True,
            source="binance-rest",
        )
        self.assertEqual("BTCUSDT", candle["symbol"])
        self.assertEqual("1m", candle["interval"])
        self.assertEqual(1000, candle["openTime"])
        self.assertEqual(1999, candle["closeTime"])
        self.assertEqual(1.5, candle["close"])
        self.assertTrue(candle["isClosed"])
        self.assertEqual("binance-rest", candle["source"])

    def test_build_market_candle_payload_from_patch_row(self):
        patch_row = {
            "k": {
                "t": 3000,
                "T": 3999,
                "o": "1.6",
                "h": "2.2",
                "l": "1.5",
                "c": "2.0",
                "v": "4",
                "q": "8.0",
                "n": 1,
                "x": False,
                "s": "BTCUSDT",
                "i": "1m",
            }
        }
        candle = v2_market.build_market_candle_payload(
            symbol="BTCUSDT",
            interval="1m",
            row=patch_row,
            is_closed=False,
            source="binance-ws",
        )
        self.assertEqual(3000, candle["openTime"])
        self.assertEqual(3999, candle["closeTime"])
        self.assertEqual(2.0, candle["close"])
        self.assertFalse(candle["isClosed"])
        self.assertEqual("binance-ws", candle["source"])

    def test_split_market_rows(self):
        rows = [
            {"openTime": 200, "isClosed": True},
            {"openTime": 210, "isClosed": False},
            {"openTime": 100, "isClosed": True},
        ]
        closed, patch = v2_market.split_market_rows(rows)
        self.assertEqual([100, 200], [item["openTime"] for item in closed])
        self.assertEqual(210, patch["openTime"])

    def test_build_market_candles_response_combines_rest_and_patch(self):
        rest_rows = [
            [1000, "1.0", "2.0", "0.5", "1.5", "10.0", 1999, "20.0", 3],
            [2000, "2.0", "2.5", "1.8", "2.1", "5.0", 2999, "12.0", 2],
        ]
        patch_row = {
            "k": {
                "t": 3000,
                "T": 3999,
                "o": "2.1",
                "h": "2.6",
                "l": "2.0",
                "c": "2.3",
                "v": "1.0",
                "q": "2.3",
                "n": 1,
                "x": False,
                "s": "BTCUSDT",
                "i": "1m",
            }
        }
        payload = v2_market.build_market_candles_response(
            symbol="BTCUSDT",
            interval="1m",
            server_time=1234,
            rest_rows=rest_rows,
            latest_patch=patch_row,
        )
        self.assertEqual("BTCUSDT", payload["symbol"])
        self.assertEqual("1m", payload["interval"])
        self.assertEqual(1234, payload["serverTime"])
        self.assertEqual(2, len(payload["candles"]))
        self.assertFalse(payload["latestPatch"]["isClosed"])
        self.assertEqual("binance-rest", payload["candles"][0]["source"])
        self.assertEqual("binance-ws", payload["latestPatch"]["source"])
        self.assertEqual([1000, 2000], [c["openTime"] for c in payload["candles"]])
        self.assertTrue(payload["nextSyncToken"])


if __name__ == "__main__":
    unittest.main()

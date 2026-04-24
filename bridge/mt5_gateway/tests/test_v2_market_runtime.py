"""v2 市场运行时单测，确保 Binance WS 真值能驱动最新 patch 与闭合 K 线。"""

import importlib
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


class V2MarketRuntimeTests(unittest.TestCase):
    """覆盖市场运行时的最小状态推进规则。"""

    def _import_runtime_module(self):
        try:
            return importlib.import_module("bridge.mt5_gateway.v2_market_runtime")
        except ModuleNotFoundError as exc:
            self.fail(f"缺少市场运行时模块: {exc}")

    def test_apply_ws_trade_event_should_update_same_minute_patch(self):
        runtime_module = self._import_runtime_module()
        runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])

        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "stream": "btcusdt@trade",
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_000_100,
                    "p": "100.0",
                    "q": "1.2",
                },
            },
        )
        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "stream": "btcusdt@trade",
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_000_400,
                    "p": "104.0",
                    "q": "0.8",
                },
            },
        )

        snapshot = runtime_module.build_symbol_state(runtime, "BTCUSDT")
        self.assertEqual("BTCUSDT", snapshot["marketSymbol"])
        self.assertEqual(104.0, snapshot["latestPrice"])
        self.assertIsNone(snapshot["latestClosedCandle"])
        self.assertIsNotNone(snapshot["latestPatch"])
        self.assertFalse(snapshot["latestPatch"]["isClosed"])
        self.assertEqual(1_710_000_000_000, snapshot["latestPatch"]["openTime"])
        self.assertEqual(100.0, snapshot["latestPatch"]["open"])
        self.assertEqual(104.0, snapshot["latestPatch"]["high"])
        self.assertEqual(100.0, snapshot["latestPatch"]["low"])
        self.assertEqual(104.0, snapshot["latestPatch"]["close"])
        self.assertEqual(2.0, snapshot["latestPatch"]["volume"])
        self.assertEqual(203.2, snapshot["latestPatch"]["quoteVolume"])
        self.assertEqual(2, snapshot["latestPatch"]["tradeCount"])
        self.assertEqual(1_710_000_000_400, snapshot["updatedAt"])
        self.assertEqual(1_710_000_000_400, snapshot["lastEventTime"])

    def test_apply_ws_trade_event_should_close_previous_minute_when_bucket_advances(self):
        runtime_module = self._import_runtime_module()
        runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])

        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_059_000,
                    "p": "105.0",
                    "q": "1.5",
                },
            },
        )
        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_061_000,
                    "p": "106.0",
                    "q": "0.5",
                },
            },
        )

        snapshot = runtime_module.build_symbol_state(runtime, "BTCUSDT")
        self.assertEqual(106.0, snapshot["latestPrice"])
        self.assertIsNotNone(snapshot["latestClosedCandle"])
        self.assertTrue(snapshot["latestClosedCandle"]["isClosed"])
        self.assertEqual(1_710_000_000_000, snapshot["latestClosedCandle"]["openTime"])
        self.assertEqual(105.0, snapshot["latestClosedCandle"]["open"])
        self.assertEqual(105.0, snapshot["latestClosedCandle"]["close"])
        self.assertEqual(1.5, snapshot["latestClosedCandle"]["volume"])
        self.assertIsNotNone(snapshot["latestPatch"])
        self.assertEqual(1_710_000_060_000, snapshot["latestPatch"]["openTime"])
        self.assertEqual(106.0, snapshot["latestPatch"]["close"])

    def test_build_interval_patch_should_aggregate_recent_minutes(self):
        runtime_module = self._import_runtime_module()
        runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])

        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_000_100,
                    "p": "100.0",
                    "q": "1.0",
                },
            },
        )
        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_059_900,
                    "p": "100.5",
                    "q": "1.0",
                },
            },
        )
        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_060_100,
                    "p": "100.5",
                    "q": "1.0",
                },
            },
        )
        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_119_900,
                    "p": "101.5",
                    "q": "2.0",
                },
            },
        )
        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_120_100,
                    "p": "101.5",
                    "q": "1.0",
                },
            },
        )
        runtime_module.apply_ws_trade_event(
            runtime,
            {
                "data": {
                    "e": "trade",
                    "s": "BTCUSDT",
                    "E": 1_710_000_179_900,
                    "p": "102.5",
                    "q": "3.0",
                },
            },
        )

        patch = runtime_module.build_interval_patch(runtime, "BTCUSDT", "15m")

        self.assertIsNotNone(patch)
        self.assertEqual("15m", patch["interval"])
        self.assertFalse(patch["isClosed"])
        self.assertEqual(1_710_000_000_000, patch["openTime"])
        self.assertEqual(100.0, patch["open"])
        self.assertEqual(102.5, patch["high"])
        self.assertEqual(100.0, patch["low"])
        self.assertEqual(102.5, patch["close"])
        self.assertEqual(9.0, patch["volume"])


if __name__ == "__main__":
    unittest.main()

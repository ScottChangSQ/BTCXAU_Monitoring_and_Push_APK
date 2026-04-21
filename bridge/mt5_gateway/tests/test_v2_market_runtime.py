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

    def test_apply_ws_kline_event_should_store_open_patch(self):
        runtime_module = self._import_runtime_module()
        runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])

        runtime_module.apply_ws_kline_event(
            runtime,
            {
                "stream": "btcusdt@kline_1m",
                "data": {
                    "s": "BTCUSDT",
                    "E": 1_710_000_000_111,
                    "k": {
                        "t": 1_710_000_000_000,
                        "T": 1_710_000_059_999,
                        "s": "BTCUSDT",
                        "i": "1m",
                        "o": "100.0",
                        "h": "105.0",
                        "l": "99.0",
                        "c": "104.0",
                        "v": "12.0",
                        "q": "1200.0",
                        "n": 6,
                        "x": False,
                    },
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

    def test_apply_ws_kline_event_should_promote_closed_candle(self):
        runtime_module = self._import_runtime_module()
        runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])

        runtime_module.apply_ws_kline_event(
            runtime,
            {
                "data": {
                    "s": "BTCUSDT",
                    "E": 1_710_000_060_100,
                    "k": {
                        "t": 1_710_000_000_000,
                        "T": 1_710_000_059_999,
                        "s": "BTCUSDT",
                        "i": "1m",
                        "o": "100.0",
                        "h": "106.0",
                        "l": "99.0",
                        "c": "105.0",
                        "v": "18.0",
                        "q": "1800.0",
                        "n": 9,
                        "x": True,
                    },
                },
            },
        )

        snapshot = runtime_module.build_symbol_state(runtime, "BTCUSDT")
        self.assertEqual(105.0, snapshot["latestPrice"])
        self.assertIsNotNone(snapshot["latestClosedCandle"])
        self.assertTrue(snapshot["latestClosedCandle"]["isClosed"])
        self.assertEqual(1_710_000_000_000, snapshot["latestClosedCandle"]["openTime"])
        self.assertIsNone(snapshot["latestPatch"])

    def test_build_interval_patch_should_aggregate_recent_minutes(self):
        runtime_module = self._import_runtime_module()
        runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])

        runtime_module.apply_ws_kline_event(
            runtime,
            {
                "data": {
                    "s": "BTCUSDT",
                    "E": 1_710_000_000_100,
                    "k": {
                        "t": 1_710_000_000_000,
                        "T": 1_710_000_059_999,
                        "s": "BTCUSDT",
                        "i": "1m",
                        "o": "100.0",
                        "h": "101.0",
                        "l": "99.0",
                        "c": "100.5",
                        "v": "2.0",
                        "q": "200.0",
                        "n": 2,
                        "x": True,
                    },
                },
            },
        )
        runtime_module.apply_ws_kline_event(
            runtime,
            {
                "data": {
                    "s": "BTCUSDT",
                    "E": 1_710_000_060_100,
                    "k": {
                        "t": 1_710_000_060_000,
                        "T": 1_710_000_119_999,
                        "s": "BTCUSDT",
                        "i": "1m",
                        "o": "100.5",
                        "h": "102.0",
                        "l": "100.0",
                        "c": "101.5",
                        "v": "3.0",
                        "q": "300.0",
                        "n": 3,
                        "x": True,
                    },
                },
            },
        )
        runtime_module.apply_ws_kline_event(
            runtime,
            {
                "data": {
                    "s": "BTCUSDT",
                    "E": 1_710_000_120_100,
                    "k": {
                        "t": 1_710_000_120_000,
                        "T": 1_710_000_179_999,
                        "s": "BTCUSDT",
                        "i": "1m",
                        "o": "101.5",
                        "h": "103.0",
                        "l": "101.0",
                        "c": "102.5",
                        "v": "4.0",
                        "q": "400.0",
                        "n": 4,
                        "x": False,
                    },
                },
            },
        )

        patch = runtime_module.build_interval_patch(runtime, "BTCUSDT", "15m")

        self.assertIsNotNone(patch)
        self.assertEqual("15m", patch["interval"])
        self.assertFalse(patch["isClosed"])
        self.assertEqual(1_710_000_000_000, patch["openTime"])
        self.assertEqual(100.0, patch["open"])
        self.assertEqual(103.0, patch["high"])
        self.assertEqual(99.0, patch["low"])
        self.assertEqual(102.5, patch["close"])
        self.assertEqual(9.0, patch["volume"])


if __name__ == "__main__":
    unittest.main()

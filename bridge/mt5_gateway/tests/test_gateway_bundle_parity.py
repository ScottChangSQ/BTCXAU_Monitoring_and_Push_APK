"""验证网关源码与部署包关键默认值保持一致。"""

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
SOURCE_EA = ROOT / "bridge" / "mt5_gateway" / "ea" / "MT5BridgePushEA.mq5"
BUNDLE_EA = ROOT / "deploy" / "tencent" / "windows_server_bundle" / "mt5_gateway" / "ea" / "MT5BridgePushEA.mq5"


class GatewayBundleParityTests(unittest.TestCase):
    """避免源码与 Windows 部署包再次漂移。"""

    @staticmethod
    def _extract_default_lines(path: Path) -> set[str]:
        content = path.read_text(encoding="utf-8")
        return {
            line.strip()
            for line in content.splitlines()
            if line.strip().startswith("input int RequestTimeoutMs")
            or line.strip().startswith("input int TradeHistoryDays")
            or line.strip().startswith("input int MaxTradeItems")
        }

    def test_ea_defaults_should_use_smaller_snapshot_budget(self):
        self.assertEqual(
            {
                "input int RequestTimeoutMs = 15000;",
                "input int TradeHistoryDays = 30;",
                "input int MaxTradeItems = 200;",
            },
            self._extract_default_lines(SOURCE_EA),
        )

    def test_windows_bundle_ea_should_match_source_defaults(self):
        self.assertEqual(self._extract_default_lines(SOURCE_EA), self._extract_default_lines(BUNDLE_EA))


if __name__ == "__main__":
    unittest.main()

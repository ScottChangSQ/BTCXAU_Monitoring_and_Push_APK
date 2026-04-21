"""验证部署包由唯一源码目录生成，避免仓库内再次出现长期漂移副本。"""

import re
import subprocess
import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory


ROOT = Path(__file__).resolve().parents[3]
SOURCE_EA = ROOT / "bridge" / "mt5_gateway" / "ea" / "MT5BridgePushEA.mq5"
SOURCE_SERVER = ROOT / "bridge" / "mt5_gateway" / "server_v2.py"
SOURCE_ENV = ROOT / "bridge" / "mt5_gateway" / ".env.example"
BUILD_SCRIPT = ROOT / "scripts" / "build_windows_server_bundle.py"


class GatewayBundleParityTests(unittest.TestCase):
    """确保部署包只由当前源码生成。"""

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

    def _build_bundle(self) -> Path:
        temp_dir = TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        output_dir = Path(temp_dir.name) / "windows_server_bundle"
        subprocess.run(
            [sys.executable, str(BUILD_SCRIPT), "--output", str(output_dir)],
            check=True,
            cwd=ROOT,
        )
        return output_dir

    def test_ea_defaults_should_use_smaller_snapshot_budget(self):
        self.assertEqual(
            {
                "input int RequestTimeoutMs = 15000;",
                "input int TradeHistoryDays = 30;",
                "input int MaxTradeItems = 200;",
            },
            self._extract_default_lines(SOURCE_EA),
        )

    def test_build_script_should_copy_gateway_defaults_into_bundle(self):
        bundle_dir = self._build_bundle()
        bundle_ea = bundle_dir / "mt5_gateway" / "ea" / "MT5BridgePushEA.mq5"

        self.assertEqual(self._extract_default_lines(SOURCE_EA), self._extract_default_lines(bundle_ea))

    def test_build_script_should_include_trade_gateway_modules(self):
        bundle_dir = self._build_bundle()

        self.assertTrue((bundle_dir / "mt5_gateway" / "v2_trade.py").exists())
        self.assertTrue((bundle_dir / "mt5_gateway" / "v2_trade_models.py").exists())
        self.assertTrue((bundle_dir / "mt5_gateway" / "v2_market_runtime.py").exists())
        self.assertTrue((bundle_dir / "mt5_gateway" / "v2_mt5_account_switch.py").exists())
        self.assertTrue((bundle_dir / "mt5_gateway" / "mt5_direct_login.py").exists())
        self.assertTrue((bundle_dir / "mt5_gateway" / "mt5_login_probe.py").exists())
        self.assertTrue((bundle_dir / "bundle_manifest.json").exists())

    def test_build_script_should_include_all_server_runtime_v2_modules(self):
        bundle_dir = self._build_bundle()
        server_source = SOURCE_SERVER.read_text(encoding="utf-8")
        imported_modules = {
            match.group(1)
            for match in re.finditer(r"^import (v2_[a-z0-9_]+)\s*$", server_source, flags=re.MULTILINE)
        }

        for module_name in sorted(imported_modules):
            with self.subTest(module=module_name):
                self.assertTrue(
                    (bundle_dir / "mt5_gateway" / f"{module_name}.py").exists(),
                    f"bundle missing runtime module: {module_name}.py",
                )

    def test_build_script_should_copy_gateway_env_template_into_bundle(self):
        bundle_dir = self._build_bundle()
        bundle_env = (bundle_dir / "mt5_gateway" / ".env.example").read_text(encoding="utf-8")

        self.assertEqual(SOURCE_ENV.read_text(encoding="utf-8"), bundle_env)

    def test_build_script_should_include_root_deploy_entrypoints(self):
        bundle_dir = self._build_bundle()

        self.assertTrue((bundle_dir / "deploy_bundle.cmd").exists())
        self.assertTrue((bundle_dir / "deploy_bundle.ps1").exists())

    def test_root_deploy_cmd_should_use_crlf_line_endings(self):
        bundle_dir = self._build_bundle()
        deploy_cmd = (bundle_dir / "deploy_bundle.cmd").read_bytes()

        self.assertIn(b"\r\n", deploy_cmd)
        self.assertNotIn(b"off\nsetlocal", deploy_cmd)

    def test_root_deploy_ps1_should_use_utf8_bom_and_crlf(self):
        bundle_dir = self._build_bundle()
        deploy_ps1 = (bundle_dir / "deploy_bundle.ps1").read_bytes()

        self.assertTrue(deploy_ps1.startswith(b"\xef\xbb\xbf"))
        self.assertFalse(deploy_ps1.startswith(b"\xef\xbb\xbf\xef\xbb\xbf"))
        self.assertIn(b"\r\n", deploy_ps1)

    def test_nested_powershell_scripts_should_use_utf8_bom(self):
        bundle_dir = self._build_bundle()
        nested_scripts = [
            bundle_dir / "windows" / "run_gateway.ps1",
            bundle_dir / "windows" / "02_register_startup_task.ps1",
            bundle_dir / "mt5_gateway" / "start_gateway.ps1",
        ]

        for script in nested_scripts:
            with self.subTest(script=script.name):
                data = script.read_bytes()
                self.assertTrue(data.startswith(b"\xef\xbb\xbf"))
                self.assertFalse(data.startswith(b"\xef\xbb\xbf\xef\xbb\xbf"))

    def test_build_script_should_exclude_tests_and_pycache(self):
        bundle_dir = self._build_bundle()

        self.assertFalse((bundle_dir / "mt5_gateway" / "tests").exists())
        self.assertFalse((bundle_dir / "mt5_gateway" / "__pycache__").exists())

    def test_built_bundle_server_should_expose_trade_endpoints(self):
        bundle_dir = self._build_bundle()
        source = SOURCE_SERVER.read_text(encoding="utf-8")
        bundle = (bundle_dir / "mt5_gateway" / "server_v2.py").read_text(encoding="utf-8")

        self.assertIn('@app.post("/v2/trade/check")', source)
        self.assertIn('@app.post("/v2/trade/check")', bundle)
        self.assertIn('import v2_trade', bundle)
        self.assertIn('import v2_trade_models', bundle)

    def test_root_deploy_ps1_should_invoke_child_scripts_by_absolute_path(self):
        bundle_dir = self._build_bundle()
        deploy_script = (bundle_dir / "deploy_bundle.ps1").read_text(encoding="utf-8-sig")

        self.assertIn('$bootstrapScript = Join-Path $windowsDir "01_bootstrap_gateway.ps1"', deploy_script)
        self.assertIn('& $bootstrapScript -BundleRoot $Context.BundleRoot', deploy_script)
        self.assertIn('& $registerGatewayTaskScript -BundleRoot $Context.BundleRoot -TaskName $Context.GatewayTaskName -Force', deploy_script)
        self.assertIn('& $registerAdminTaskScript -BundleRoot $Context.BundleRoot -TaskName $Context.AdminTaskName -Force', deploy_script)

    def test_root_deploy_ps1_should_support_parent_level_caddy_exe(self):
        bundle_dir = self._build_bundle()
        deploy_script = (bundle_dir / "deploy_bundle.ps1").read_text(encoding="utf-8-sig")

        self.assertIn("function Resolve-CaddyExecutablePath", deploy_script)
        self.assertIn('(Join-Path $Context.BundleParent "caddy.exe")', deploy_script)
        self.assertIn('$caddyExe = Resolve-CaddyExecutablePath -Context $Context', deploy_script)


if __name__ == "__main__":
    unittest.main()

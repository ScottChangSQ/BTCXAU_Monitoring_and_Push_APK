"""Windows 部署包与部署脚本的回归测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts import build_windows_server_bundle as builder


class WindowsServerBundleTests(unittest.TestCase):
    """验证部署包内容和一键部署入口不会回退。"""

    def test_build_bundle_should_include_v2_session_chain_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir) / "windows_server_bundle"
            builder.build_bundle(output_dir)

            expected_files = [
                output_dir / "mt5_gateway" / "v2_session_crypto.py",
                output_dir / "mt5_gateway" / "v2_session_manager.py",
                output_dir / "mt5_gateway" / "v2_session_models.py",
                output_dir / "mt5_gateway" / "v2_session_store.py",
                output_dir / "deploy_bundle.ps1",
                output_dir / "deploy_bundle.cmd",
            ]

            for expected_file in expected_files:
                self.assertTrue(expected_file.exists(), f"missing bundle file: {expected_file}")

    def test_deploy_script_should_use_single_gui_window_and_full_port_cleanup(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn("System.Windows.Forms", deploy_script)
        self.assertIn("System.Drawing", deploy_script)
        self.assertIn("LocalPort -in 80, 443, 2019, 8787, 8788", deploy_script)
        self.assertIn("Start-Process", deploy_script)
        self.assertIn("-WindowStyle Hidden", deploy_script)
        self.assertNotIn("foreach ($pid in $listenerPids)", deploy_script)
        self.assertIn('function Wait-HttpsLoopbackOk', deploy_script)
        self.assertIn('Wait-HttpsLoopbackOk -HostName "tradeapp.ltd" -Path "/health"', deploy_script)
        self.assertIn('Wait-HttpOk -Url "https://tradeapp.ltd/health"', deploy_script)

    def test_deploy_cmd_should_launch_hidden_gui_entry(self) -> None:
        deploy_cmd = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.cmd").read_text(encoding="utf-8")

        self.assertIn("-WindowStyle Hidden", deploy_cmd)
        self.assertNotIn("-NoExit", deploy_cmd)

    def test_bootstrap_script_should_write_requirements_stamp_for_first_start(self) -> None:
        bootstrap_script = (builder.SOURCE_WINDOWS_DIR / "01_bootstrap_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn(".requirements.sha256", bootstrap_script)
        self.assertIn("Set-Content -LiteralPath $requirementsStampPath", bootstrap_script)

    def test_deploy_script_should_use_shared_log_io_between_gui_and_worker(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn("function Write-TextFileShared", deploy_script)
        self.assertIn("function Read-TextFileShared", deploy_script)
        self.assertNotIn('Add-Content -LiteralPath $Context.LogFile -Value $line -Encoding UTF8', deploy_script)
        self.assertNotIn('$logBox.Text = Get-Content -LiteralPath $ResolvedLogFile -Raw -Encoding UTF8', deploy_script)

    def test_default_caddyfile_should_expose_tradeapp_https_entry(self) -> None:
        caddyfile = (builder.SOURCE_WINDOWS_DIR / "Caddyfile").read_text(encoding="utf-8")

        self.assertIn("tradeapp.ltd {", caddyfile)
        self.assertIn("http://tradeapp.ltd", caddyfile)
        self.assertIn('redir https://tradeapp.ltd{uri} 308', caddyfile)


if __name__ == "__main__":
    unittest.main()

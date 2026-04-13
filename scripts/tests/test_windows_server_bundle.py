"""Windows 部署包与部署脚本的回归测试。"""

from __future__ import annotations

import json
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
                output_dir / "bundle_manifest.json",
                output_dir / "mt5_gateway" / "v2_session_crypto.py",
                output_dir / "mt5_gateway" / "v2_session_manager.py",
                output_dir / "mt5_gateway" / "v2_session_models.py",
                output_dir / "mt5_gateway" / "v2_session_store.py",
                output_dir / "deploy_bundle.ps1",
                output_dir / "deploy_bundle.cmd",
            ]

            for expected_file in expected_files:
                self.assertTrue(expected_file.exists(), f"missing bundle file: {expected_file}")

    def test_build_bundle_should_write_bundle_manifest_with_runtime_fingerprint(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir) / "windows_server_bundle"
            builder.build_bundle(output_dir)

            manifest = json.loads((output_dir / "bundle_manifest.json").read_text(encoding="utf-8"))

        self.assertEqual("windows_server_bundle", manifest.get("bundleName"))
        self.assertIsInstance(manifest.get("bundleFingerprint"), str)
        self.assertTrue(manifest.get("bundleFingerprint"))
        self.assertIsInstance(manifest.get("generatedAt"), str)
        self.assertTrue(manifest.get("generatedAt"))

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
        self.assertIn('Wait-HttpJsonFieldMatch', deploy_script)
        self.assertIn('https://tradeapp.ltd/health', deploy_script)
        self.assertIn('http://127.0.0.1:8787/v2/account/snapshot', deploy_script)
        self.assertIn('http://127.0.0.1:8787/v2/account/history?range=all', deploy_script)
        self.assertIn('https://tradeapp.ltd/v2/account/snapshot', deploy_script)
        self.assertIn('https://tradeapp.ltd/v2/account/history?range=all', deploy_script)
        self.assertIn('bundle_manifest.json', deploy_script)
        self.assertIn('bundleFingerprint', deploy_script)
        self.assertIn('wss://tradeapp.ltd/v2/stream', deploy_script)

    def test_deploy_cmd_should_launch_hidden_gui_entry(self) -> None:
        deploy_cmd = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.cmd").read_text(encoding="utf-8")

        self.assertIn("-WindowStyle Hidden", deploy_cmd)
        self.assertNotIn("-NoExit", deploy_cmd)

    def test_bootstrap_script_should_write_requirements_stamp_for_first_start(self) -> None:
        bootstrap_script = (builder.SOURCE_WINDOWS_DIR / "01_bootstrap_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn(".requirements.sha256", bootstrap_script)
        self.assertIn("Set-Content -LiteralPath $requirementsStampPath", bootstrap_script)

    def test_bootstrap_script_should_use_dotnet_sha256_instead_of_get_file_hash(self) -> None:
        bootstrap_script = (builder.SOURCE_WINDOWS_DIR / "01_bootstrap_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn("System.Security.Cryptography.SHA256", bootstrap_script)
        self.assertNotIn("Get-FileHash", bootstrap_script)

    def test_deploy_script_should_use_shared_log_io_between_gui_and_worker(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn("function Write-TextFileShared", deploy_script)
        self.assertIn("function Read-TextFileShared", deploy_script)
        self.assertNotIn('Add-Content -LiteralPath $Context.LogFile -Value $line -Encoding UTF8', deploy_script)
        self.assertNotIn('$logBox.Text = Get-Content -LiteralPath $ResolvedLogFile -Raw -Encoding UTF8', deploy_script)

    def test_deploy_script_should_log_each_health_probe_and_preserve_last_failure_reason(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn("function Get-WebRequestFailureMessage", deploy_script)
        self.assertIn("function Start-HealthProbe", deploy_script)
        self.assertIn("最后错误", deploy_script)
        self.assertIn('Start-HealthProbe -Context $Context -Label "8787 /health"', deploy_script)
        self.assertIn('Start-HealthProbe -Context $Context -Label "8788 /"', deploy_script)
        self.assertIn('Start-HealthProbe -Context $Context -Label "80 /health"', deploy_script)
        self.assertIn('Start-HealthProbe -Context $Context -Label "443 tradeapp.ltd/health"', deploy_script)
        self.assertIn('Start-HealthProbe -Context $Context -Label "8787 /v2/account/snapshot"', deploy_script)
        self.assertIn('Start-HealthProbe -Context $Context -Label "443 tradeapp.ltd/v2/account/snapshot"', deploy_script)
        self.assertIn('Start-HealthProbe -Context $Context -Label "wss://tradeapp.ltd/v2/stream"', deploy_script)

    def test_deploy_script_should_retry_websocket_probe_until_timeout(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn('function Wait-WebSocketMessage', deploy_script)
        self.assertIn('$deadline = (Get-Date).AddSeconds($MaxSeconds)', deploy_script)
        self.assertIn('while ((Get-Date) -lt $deadline)', deploy_script)
        self.assertIn('Start-Sleep -Seconds 2', deploy_script)
        self.assertIn('throw "等待 WebSocket 首条消息超时: $Url | 最后错误: $lastFailure"', deploy_script)

    def test_deploy_script_should_only_stop_bundle_managed_python_processes(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn('function Test-BundleManagedProcess', deploy_script)
        self.assertIn('$Context.GatewayDir', deploy_script)
        self.assertIn('$Context.WindowsDir', deploy_script)
        self.assertIn('Test-BundleManagedProcess -Process $_ -Context $Context', deploy_script)

    def test_deploy_script_should_match_relative_python_script_names_inside_bundle_venv(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn('$serverScriptName = [System.IO.Path]::GetFileName($serverScriptPath).ToLowerInvariant()', deploy_script)
        self.assertIn('$adminScriptName = [System.IO.Path]::GetFileName($adminScriptPath).ToLowerInvariant()', deploy_script)
        self.assertIn('$pythonExecutableManagedByBundle = $normalizedExecutablePath.StartsWith($normalizedGatewayDir)', deploy_script)
        self.assertIn('$normalizedCommandLine.Contains($serverScriptName)', deploy_script)
        self.assertIn('$normalizedCommandLine.Contains($adminScriptName)', deploy_script)

    def test_deploy_script_should_validate_gateway_env_before_starting_tasks(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn("function Test-GatewayEnvContract", deploy_script)
        self.assertIn('Join-Path $Context.GatewayDir ".env"', deploy_script)
        self.assertIn('"MT5_LOGIN"', deploy_script)
        self.assertIn('"MT5_PASSWORD"', deploy_script)
        self.assertIn('"MT5_SERVER"', deploy_script)
        self.assertIn('"MT5_SERVER_TIMEZONE"', deploy_script)
        self.assertIn("Test-GatewayEnvContract -Context $Context", deploy_script)

    def test_deploy_script_should_include_latest_gateway_log_when_8787_probe_fails(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn("function Get-ServiceLogTail", deploy_script)
        self.assertIn('Join-Path $Context.GatewayDir "logs"', deploy_script)
        self.assertIn('"gateway-*.log"', deploy_script)
        self.assertIn('最近网关日志', deploy_script)
        self.assertIn('健康检查通过: 8787 /health', deploy_script)

    def test_run_gateway_should_pin_bundle_manifest_path_for_runtime_fingerprint(self) -> None:
        run_gateway_script = (builder.SOURCE_WINDOWS_DIR / "run_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn("MT5_BUNDLE_MANIFEST_PATH", run_gateway_script)
        self.assertIn('Join-Path (Split-Path -Parent $gatewayDir) "bundle_manifest.json"', run_gateway_script)
        self.assertIn('$env:MT5_BUNDLE_MANIFEST_PATH = (Resolve-Path $bundleManifestPath).Path', run_gateway_script)

    def test_default_caddyfile_should_expose_tradeapp_https_entry(self) -> None:
        caddyfile = (builder.SOURCE_WINDOWS_DIR / "Caddyfile").read_text(encoding="utf-8")

        self.assertIn("tradeapp.ltd {", caddyfile)
        self.assertIn("http://tradeapp.ltd", caddyfile)
        self.assertIn('redir https://tradeapp.ltd{uri} 308', caddyfile)


if __name__ == "__main__":
    unittest.main()

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
                output_dir / "mt5_gateway" / "mt5_direct_login.py",
                output_dir / "mt5_gateway" / "v2_mt5_account_switch.py",
                output_dir / "mt5_gateway" / "v2_session_crypto.py",
                output_dir / "mt5_gateway" / "v2_session_manager.py",
                output_dir / "mt5_gateway" / "v2_session_models.py",
                output_dir / "mt5_gateway" / "v2_session_store.py",
                output_dir / "mt5_gateway" / "v2_market_runtime.py",
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

    def test_bootstrap_script_should_fail_fast_when_native_dependency_setup_fails(self) -> None:
        bootstrap_script = (builder.SOURCE_WINDOWS_DIR / "01_bootstrap_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn("function Invoke-NativeCommandSafely", bootstrap_script)
        self.assertIn("function Resolve-CompatiblePythonCommand", bootstrap_script)
        self.assertIn('Python 3.8 or newer (64-bit) is required for the MT5 gateway bundle.', bootstrap_script)
        self.assertIn('$resolvedPythonCommand = Resolve-CompatiblePythonCommand -PreferredPythonExe $PythonExe', bootstrap_script)
        self.assertIn('$venvArguments = @($resolvedPythonCommand.PrefixArguments + @("-m", "venv"))', bootstrap_script)
        self.assertIn('$venvArguments += "--upgrade"', bootstrap_script)
        self.assertIn('Invoke-NativeCommandSafely -FilePath $resolvedPythonCommand.FilePath -Arguments $venvArguments', bootstrap_script)
        self.assertIn('Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @("-m", "pip", "install", "--upgrade", "pip")', bootstrap_script)
        self.assertIn('Invoke-NativeCommandSafely -FilePath $venvPython -Arguments @("-m", "pip", "install", "-r", "requirements.txt")', bootstrap_script)
        self.assertIn('PrefixArguments = @("-3")', bootstrap_script)
        self.assertNotIn('& $PythonExe -m venv .venv', bootstrap_script)
        self.assertNotIn('& $venvPython -m pip install --upgrade pip', bootstrap_script)
        self.assertNotIn('& $venvPython -m pip install -r requirements.txt', bootstrap_script)

    def test_bootstrap_script_should_require_64bit_python_for_windows_mt5_dependencies(self) -> None:
        bootstrap_script = (builder.SOURCE_WINDOWS_DIR / "01_bootstrap_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn("import sys, struct;", bootstrap_script)
        self.assertIn("struct.calcsize('P') * 8", bootstrap_script)
        self.assertIn('Python 3.8 or newer (64-bit) is required for the MT5 gateway bundle.', bootstrap_script)
        self.assertIn("Bits = [int]$parts[2]", bootstrap_script)
        self.assertIn("($versionInfo.Bits -eq 64)", bootstrap_script)
        self.assertIn('Write-Host ("Using Python runtime: " + $resolvedPythonCommand.Label + " (" + $resolvedPythonCommand.Version + ", " + $resolvedPythonCommand.Bits + "-bit)")', bootstrap_script)

    def test_bootstrap_script_should_capture_native_stderr_via_cmd_like_previous_working_version(self) -> None:
        bootstrap_script = (builder.SOURCE_WINDOWS_DIR / "01_bootstrap_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn('$commandLine = ($commandParts -join " ") + " 2>&1"', bootstrap_script)
        self.assertIn('& cmd.exe /d /s /c $commandLine | ForEach-Object { $_ }', bootstrap_script)
        self.assertIn('$exitCode = $LASTEXITCODE', bootstrap_script)
        self.assertNotIn('NativeCommandRunner', bootstrap_script)

    def test_start_gateway_should_validate_dependency_contract_before_reusing_requirements_stamp(self) -> None:
        start_gateway_script = (builder.SOURCE_GATEWAY_DIR / "start_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn("function Test-PythonVersionAtLeast", start_gateway_script)
        self.assertIn('Python 3.8 or newer is required for the MT5 gateway runtime.', start_gateway_script)
        self.assertIn('if (-not (Test-PythonVersionAtLeast -PythonPath $venvPython -MinimumMajor 3 -MinimumMinor 8)) {', start_gateway_script)
        self.assertIn("function Test-GatewayDependencyContract", start_gateway_script)
        self.assertIn('$dependencyModules = @("fastapi", "uvicorn", "MetaTrader5", "dotenv", "cryptography", "tzdata")', start_gateway_script)
        self.assertIn('Test-GatewayDependencyContract -PythonPath $venvPython -ModuleNames $dependencyModules', start_gateway_script)
        self.assertIn('Write-Host "Python dependency contract invalid, reinstalling dependencies..."', start_gateway_script)
        self.assertIn('throw "Gateway dependency contract check failed after reinstall."', start_gateway_script)
        self.assertIn('__import__(sys.argv[1])', start_gateway_script)
        self.assertIn('$moduleCheckScript = "import sys; __import__(sys.argv[1])"', start_gateway_script)
        self.assertIn('Write-Host ("Python dependency import failed: " + ($missingModules -join ", "))', start_gateway_script)
        self.assertNotIn('import importlib', start_gateway_script)
        self.assertNotIn('missing.append(f"{name}: {exc}")', start_gateway_script)

    def test_start_gateway_should_capture_native_stderr_via_cmd_like_previous_working_version(self) -> None:
        start_gateway_script = (builder.SOURCE_GATEWAY_DIR / "start_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn('$commandLine = ($commandParts -join " ") + " 2>&1"', start_gateway_script)
        self.assertIn('& cmd.exe /d /s /c $commandLine | ForEach-Object { $_ }', start_gateway_script)
        self.assertIn('$exitCode = $LASTEXITCODE', start_gateway_script)
        self.assertNotIn('NativeCommandRunner', start_gateway_script)

    def test_deploy_script_should_use_shared_log_io_between_gui_and_worker(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn("function Write-TextFileShared", deploy_script)
        self.assertIn("function Read-TextFileShared", deploy_script)
        self.assertNotIn('Add-Content -LiteralPath $Context.LogFile -Value $line -Encoding UTF8', deploy_script)
        self.assertNotIn('$logBox.Text = Get-Content -LiteralPath $ResolvedLogFile -Raw -Encoding UTF8', deploy_script)

    def test_deploy_script_should_upgrade_gui_into_runtime_status_panel(self) -> None:
        deploy_script = (builder.SOURCE_WINDOWS_DIR / "deploy_bundle.ps1").read_text(encoding="utf-8")

        self.assertIn('$form.Text = "MT5 部署与连接状态"', deploy_script)
        self.assertIn('[ValidateSet("Gui", "Worker", "RuntimePoller")]', deploy_script)
        self.assertIn("$form.TopMost = $false", deploy_script)
        self.assertIn("$form.MinimizeBox = $true", deploy_script)
        self.assertIn("$form.ControlBox = $true", deploy_script)
        self.assertIn("$form.ShowInTaskbar = $true", deploy_script)
        self.assertIn("FormBorderStyle]::Sizable", deploy_script)
        self.assertNotIn("$form.TopMost = $true", deploy_script)
        self.assertIn("执行日志（仅内容变化时刷新）", deploy_script)
        self.assertIn("本地部署状态", deploy_script)
        self.assertIn("行情连接状态", deploy_script)
        self.assertIn("账户连接状态", deploy_script)
        self.assertIn("手机 APP 交互状态", deploy_script)
        self.assertNotIn('$titleLabel.Text = "MT5 部署与连接状态"', deploy_script)
        self.assertNotIn('$bundleLabel.Text = "部署目录：" + $ResolvedBundleRoot', deploy_script)
        self.assertIn("$stepList.Size = New-Object System.Drawing.Size(950, 116)", deploy_script)
        self.assertIn("$panelColumnWidth = 227", deploy_script)
        self.assertIn("$panelColumnGap = 14", deploy_script)
        self.assertIn("$panelBoxHeight = 210", deploy_script)
        self.assertIn("$appLabel.Location = New-Object System.Drawing.Point((16 + (($panelColumnWidth + $panelColumnGap) * 3)), $panelTopY)", deploy_script)
        self.assertIn("$appBox.Location = New-Object System.Drawing.Point((16 + (($panelColumnWidth + $panelColumnGap) * 3)), $panelBoxTopY)", deploy_script)
        self.assertIn("$appBox.Size = New-Object System.Drawing.Size($panelColumnWidth, $panelBoxHeight)", deploy_script)
        self.assertIn("$logBox.Size = New-Object System.Drawing.Size(950, 412)", deploy_script)
        self.assertIn("最近会话动作", deploy_script)
        self.assertIn("最近交易动作", deploy_script)
        self.assertIn("最近客户端来源", deploy_script)
        self.assertIn("http://127.0.0.1:8787/internal/runtime/panel", deploy_script)
        self.assertIn("$timer.Interval = 1000", deploy_script)
        self.assertIn("RuntimeSnapshotFile", deploy_script)
        self.assertIn('"-Mode", "RuntimePoller"', deploy_script)
        self.assertIn('Invoke-RuntimePoller', deploy_script)
        self.assertIn('".runtime.json"', deploy_script)
        self.assertIn("Build-RuntimeSnapshotPayload", deploy_script)
        self.assertIn("$localBox = New-Object System.Windows.Forms.RichTextBox", deploy_script)
        self.assertIn("$marketBox = New-Object System.Windows.Forms.RichTextBox", deploy_script)
        self.assertIn("$accountBox = New-Object System.Windows.Forms.RichTextBox", deploy_script)
        self.assertIn("$appBox = New-Object System.Windows.Forms.RichTextBox", deploy_script)
        self.assertIn("function Update-TextBoxIfChanged", deploy_script)
        self.assertIn("function Test-TextPanelInteractionActive", deploy_script)
        self.assertIn("$TextBox.ContainsFocus", deploy_script)
        self.assertIn("$TextBox.Capture", deploy_script)
        self.assertIn("System.Windows.Forms.RichTextBox", deploy_script)
        self.assertIn("$logBox = New-Object System.Windows.Forms.RichTextBox", deploy_script)
        self.assertIn("$localBox.DetectUrls = $false", deploy_script)
        self.assertIn("$marketBox.DetectUrls = $false", deploy_script)
        self.assertIn("$accountBox.DetectUrls = $false", deploy_script)
        self.assertIn("$appBox.DetectUrls = $false", deploy_script)
        self.assertIn("$logBox.DetectUrls = $false", deploy_script)
        self.assertIn("$localBox.WordWrap = $true", deploy_script)
        self.assertIn("$marketBox.WordWrap = $true", deploy_script)
        self.assertIn("$accountBox.WordWrap = $true", deploy_script)
        self.assertIn("$appBox.WordWrap = $true", deploy_script)
        self.assertIn("$logBox.WordWrap = $false", deploy_script)
        self.assertIn("Test-TextPanelInteractionActive -TextBox $TextBox", deploy_script)
        self.assertIn("Build-StepListSignature", deploy_script)
        self.assertNotIn('运行中（状态每 1 秒刷新）', deploy_script)
        self.assertNotIn("if ($TextBox.Focused)", deploy_script)

    def test_gateway_server_should_use_fastapi_native_request_annotation(self) -> None:
        gateway_server = (builder.SOURCE_GATEWAY_DIR / "server_v2.py").read_text(encoding="utf-8")

        self.assertIn("from fastapi import FastAPI, Header, HTTPException, Query, Request, Response, WebSocket, WebSocketDisconnect", gateway_server)
        self.assertNotIn("Optional[Request]", gateway_server)

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
        self.assertIn('Start-HealthProbe -Context $Context -Label "8787 /v2/account/full (diagnostic)"', deploy_script)
        self.assertIn('http://127.0.0.1:8787/v2/account/full', deploy_script)
        self.assertIn('健康检查通过: 8787 /v2/account/full (diagnostic)', deploy_script)

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

    def test_start_gateway_should_log_current_identity_and_session_for_mt5_gui_diagnostics(self) -> None:
        start_gateway_script = (builder.SOURCE_GATEWAY_DIR / "start_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn("WindowsIdentity", start_gateway_script)
        self.assertIn("GetCurrentProcess().SessionId", start_gateway_script)
        self.assertIn("Gateway process identity:", start_gateway_script)
        self.assertIn("Gateway process session id:", start_gateway_script)

    def test_register_task_scripts_should_prefer_interactive_user_and_fallback_to_system(self) -> None:
        gateway_task_script = (builder.SOURCE_WINDOWS_DIR / "02_register_startup_task.ps1").read_text(encoding="utf-8")
        admin_task_script = (builder.SOURCE_WINDOWS_DIR / "04_register_admin_panel_task.ps1").read_text(encoding="utf-8")

        for script in (gateway_task_script, admin_task_script):
            self.assertIn("Resolve-InteractiveTaskUser", script)
            self.assertIn("Resolve-TaskRegistrationProfile", script)
            self.assertIn("New-ScheduledTaskTrigger -AtLogOn -User $interactiveUser", script)
            self.assertIn("New-ScheduledTaskPrincipal -UserId $interactiveUser -LogonType Interactive -RunLevel Highest", script)
            self.assertIn('New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest', script)
            self.assertIn('LaunchMode = "interactive_user"', script)
            self.assertIn('LaunchMode = "service_account"', script)
            self.assertIn("-WindowStyle Hidden", script)

    def test_default_caddyfile_should_expose_tradeapp_https_entry(self) -> None:
        caddyfile = (builder.SOURCE_WINDOWS_DIR / "Caddyfile").read_text(encoding="utf-8")

        self.assertIn("tradeapp.ltd {", caddyfile)
        self.assertIn("http://tradeapp.ltd", caddyfile)
        self.assertIn('redir https://tradeapp.ltd{uri} 308', caddyfile)


if __name__ == "__main__":
    unittest.main()

"""轻量管理面板单测。"""

import hashlib
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
import subprocess
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from bridge.mt5_gateway import admin_panel  # noqa: E402


class AdminPanelTests(unittest.TestCase):
    """验证管理面板配置解析与本地管理能力。"""

    @staticmethod
    def _requirements_hash(path: Path) -> str:
        """计算与启动脚本一致的 requirements SHA256。"""
        return hashlib.sha256(path.read_bytes()).hexdigest().upper()

    def _build_start_script_fixture(self, script_name: str, target_script_name: str) -> Path:
        """构造最小启动目录，复现真实 PowerShell 启动链。"""
        fixture_dir = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, fixture_dir, True)

        (fixture_dir / script_name).write_text((ROOT / script_name).read_text(encoding="utf-8"), encoding="utf-8")
        requirements_path = fixture_dir / "requirements.txt"
        requirements_path.write_text("", encoding="utf-8")
        (fixture_dir / ".requirements.sha256").write_text(self._requirements_hash(requirements_path), encoding="utf-8")
        (fixture_dir / ".env").write_text("GATEWAY_HOST=127.0.0.1\n", encoding="utf-8")
        (fixture_dir / target_script_name).write_text("print('fixture ok')\n", encoding="utf-8")
        subprocess.run([sys.executable, "-m", "venv", str(fixture_dir / ".venv")], check=True, cwd=fixture_dir)
        return fixture_dir

    def _run_start_script_without_get_file_hash(self, fixture_dir: Path, script_name: str) -> subprocess.CompletedProcess[str]:
        """在禁用 Get-FileHash 的 PowerShell 会话中执行启动脚本。"""
        command = (
            "function Get-FileHash { param($Algorithm, $LiteralPath) throw 'Get-FileHash blocked for regression test' }\n"
            f"Set-Location '{fixture_dir}'\n"
            f"& '{fixture_dir / script_name}' -EnvFile '.env'\n"
        )
        return subprocess.run(
            ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command],
            cwd=fixture_dir,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_resolve_gateway_url_should_prefer_explicit_admin_url(self):
        env_map = {"ADMIN_GATEWAY_URL": "http://10.0.0.8:8787"}

        self.assertEqual("http://10.0.0.8:8787", admin_panel.resolve_gateway_url(env_map))

    def test_build_component_registry_should_include_gateway_mt5_caddy_and_nginx(self):
        registry = admin_panel.build_component_registry(
            env_map={"ADMIN_GATEWAY_TASK_NAME": "MT5GatewayAutoStart"},
            repo_root="C:/repo",
        )

        self.assertEqual({"gateway", "mt5", "caddy", "nginx"}, set(registry.keys()))
        self.assertIn("Start-ScheduledTask", registry["gateway"]["actions"]["start"])

    def test_build_component_registry_should_prefer_service_controls_when_configured(self):
        registry = admin_panel.build_component_registry(
            env_map={"ADMIN_CADDY_SERVICE_NAME": "CaddySvc"},
            repo_root="C:/repo",
        )

        self.assertIn("Restart-Service", registry["caddy"]["actions"]["restart"])
        self.assertIn("CaddySvc", registry["caddy"]["actions"]["restart"])

    def test_build_component_registry_should_prefer_explicit_commands_over_defaults(self):
        registry = admin_panel.build_component_registry(
            env_map={"ADMIN_NGINX_START_CMD": "Write-Host custom-nginx-start"},
            repo_root="C:/repo",
        )

        self.assertEqual("Write-Host custom-nginx-start", registry["nginx"]["actions"]["start"])

    def test_read_recent_logs_should_return_latest_lines_from_newest_files(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            logs_dir = Path(temp_dir)
            older = logs_dir / "gateway-older.log"
            newer = logs_dir / "gateway-newer.log"
            older.write_text("line-a\nline-b\n", encoding="utf-8")
            newer.write_text("line-c\nline-d\nline-e\n", encoding="utf-8")

            entries = admin_panel.read_recent_logs(logs_dir, limit=3)

        self.assertEqual(3, len(entries))
        self.assertEqual("line-c", entries[0]["line"])
        self.assertEqual("line-d", entries[1]["line"])
        self.assertEqual("line-e", entries[2]["line"])

    def test_env_file_helpers_should_roundtrip_utf8_text(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            env_path = Path(temp_dir) / ".env"
            content = "# 注释\nMT5_SERVER=测试服务器\n"
            admin_panel.write_text_file_utf8(env_path, content)

            restored = admin_panel.read_text_file_utf8(env_path)

        self.assertEqual(content, restored)

    def test_read_text_file_utf8_should_fallback_to_gbk_when_needed(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            log_path = Path(temp_dir) / "sample.log"
            log_path.write_bytes("中文日志".encode("gbk"))

            restored = admin_panel.read_text_file_utf8(log_path)

        self.assertEqual("中文日志", restored)

    def test_configure_windows_event_loop_policy_should_switch_to_selector_on_windows(self):
        class _FakeSelectorPolicy:
            pass

        with mock.patch.object(admin_panel.os, "name", "nt"), mock.patch.object(
            admin_panel.asyncio, "WindowsSelectorEventLoopPolicy", _FakeSelectorPolicy, create=True
        ), mock.patch.object(admin_panel.asyncio, "get_event_loop_policy", return_value=object()), mock.patch.object(
            admin_panel.asyncio, "set_event_loop_policy"
        ) as set_mock:
            admin_panel._configure_windows_event_loop_policy()

        selected_policy = set_mock.call_args[0][0]
        self.assertIsInstance(selected_policy, _FakeSelectorPolicy)

    def test_admin_index_should_use_relative_asset_paths_for_subpath_proxy(self):
        html = admin_panel.index()

        self.assertIn('href="./styles.css"', html)
        self.assertIn('src="./app.js"', html)
        self.assertIn('id="toggleLogsBtn"', html)
        self.assertIn('id="logsBox" class="code-box compact"', html)

    def test_admin_app_script_should_use_relative_api_paths_for_subpath_proxy(self):
        response = admin_panel.app_js()
        script = getattr(response, "body", None)
        if isinstance(script, bytes):
            script = script.decode("utf-8")
        if script is None:
            script = getattr(response, "content", "")

        self.assertIn("const API_BASE = './api';", script)
        self.assertIn("requestJson(`${API_BASE}/state`)", script)
        self.assertIn("requestJson(`${API_BASE}/logs?limit=80`)", script)
        self.assertIn("requestJson(`${API_BASE}/env`)", script)

    def test_admin_static_assets_should_return_browser_friendly_media_types(self):
        js_response = admin_panel.app_js()
        css_response = admin_panel.styles_css()

        self.assertEqual("application/javascript", getattr(js_response, "media_type", ""))
        self.assertEqual("text/css", getattr(css_response, "media_type", ""))

    def test_admin_app_script_should_include_log_toggle_behavior(self):
        response = admin_panel.app_js()
        script = getattr(response, "body", None)
        if isinstance(script, bytes):
            script = script.decode("utf-8")
        if script is None:
            script = getattr(response, "content", "")

        self.assertIn("toggleLogsBtn", script)
        self.assertIn("logsBox.classList.toggle('expanded')", script)

    def test_build_admin_state_should_surface_port_online_fallback_and_time_offset_warning(self):
        with mock.patch.object(admin_panel, "load_env_map", return_value={}), mock.patch.object(
            admin_panel, "resolve_gateway_url", return_value="http://127.0.0.1:8787"
        ), mock.patch.object(
            admin_panel,
            "build_component_registry",
            return_value={"gateway": {"label": "MT5 网关", "actions": {}}},
        ), mock.patch.object(
            admin_panel,
            "inspect_component",
            return_value={
                "running": True,
                "statusText": "端口在线",
                "details": {"warning": "健康检查超时或失败：timed out"},
            },
        ), mock.patch.object(
            admin_panel,
            "request_gateway_json",
            side_effect=[RuntimeError("timed out"), {"mt5TimeOffsetMinutes": 180}],
        ):
            state = admin_panel.build_admin_state()

        self.assertTrue(state["gatewayHealth"]["portOnline"])
        self.assertEqual("端口在线", state["gatewayHealth"]["statusText"])
        self.assertIn("timed out", state["gatewayHealth"]["warning"])
        self.assertIn("180", state["gatewaySource"]["mt5TimeOffsetWarning"])

    def test_decorate_gateway_payload_should_surface_session_summary_fields(self):
        payload = admin_panel.decorate_gateway_payload(
            {
                "ok": True,
                "session": {
                    "activeAccount": {"profileId": "acct_1", "loginMasked": "****5678"},
                    "savedAccountCount": 2,
                },
            }
        )

        self.assertIn("session", payload)
        self.assertEqual("acct_1", payload["session"]["activeAccount"]["profileId"])
        self.assertEqual(2, payload["session"]["savedAccountCount"])

    def test_start_admin_panel_script_should_skip_noisy_pip_install_when_requirements_unchanged(self):
        script_path = ROOT / "start_admin_panel.ps1"
        content = script_path.read_text(encoding="utf-8")

        self.assertIn(".requirements.sha256", content)
        self.assertIn("--quiet", content)
        self.assertIn("chcp 65001", content)
        self.assertIn("public static int Run(string filePath, string argumentsLine, string workingDirectory)", content)
        self.assertIn("RedirectStandardError = true", content)

    def test_start_gateway_script_should_skip_reinstall_when_requirements_unchanged(self):
        script_path = ROOT / "start_gateway.ps1"
        content = script_path.read_text(encoding="utf-8")

        self.assertIn(".requirements.sha256", content)
        self.assertIn("--quiet", content)
        self.assertIn("--disable-pip-version-check", content)
        self.assertIn("public static int Run(string filePath, string argumentsLine, string workingDirectory)", content)
        self.assertIn("RedirectStandardError = true", content)

    def test_start_gateway_script_should_not_depend_on_get_file_hash_cmdlet(self):
        fixture_dir = self._build_start_script_fixture("start_gateway.ps1", "server_v2.py")

        result = self._run_start_script_without_get_file_hash(fixture_dir, "start_gateway.ps1")

        self.assertEqual(0, result.returncode, msg=result.stdout + result.stderr)
        self.assertIn("fixture ok", result.stdout + result.stderr)
        self.assertNotIn("Get-FileHash blocked for regression test", result.stdout + result.stderr)

    def test_start_admin_panel_script_should_not_depend_on_get_file_hash_cmdlet(self):
        fixture_dir = self._build_start_script_fixture("start_admin_panel.ps1", "admin_panel.py")

        result = self._run_start_script_without_get_file_hash(fixture_dir, "start_admin_panel.ps1")

        self.assertEqual(0, result.returncode, msg=result.stdout + result.stderr)
        self.assertIn("fixture ok", result.stdout + result.stderr)
        self.assertNotIn("Get-FileHash blocked for regression test", result.stdout + result.stderr)

    def test_health_endpoint_should_not_force_mt5_login_probe(self):
        server_source = (ROOT / "server_v2.py").read_text(encoding="utf-8")
        start = server_source.index('@app.get("/health")')
        end = server_source.index('@app.get("/binance-rest/{path_value:path}")')
        health_block = server_source[start:end]

        self.assertNotIn("_ensure_mt5()", health_block)
        self.assertNotIn("mt5.account_info()", health_block)
        self.assertNotIn("mt5.last_error()", health_block)


if __name__ == "__main__":
    unittest.main()

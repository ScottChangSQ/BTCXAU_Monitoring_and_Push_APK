"""轻量管理面板单测。"""

import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from bridge.mt5_gateway import admin_panel  # noqa: E402


class AdminPanelTests(unittest.TestCase):
    """验证管理面板配置解析与本地管理能力。"""

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

    def test_start_admin_panel_script_should_skip_noisy_pip_install_when_requirements_unchanged(self):
        script_path = ROOT / "start_admin_panel.ps1"
        content = script_path.read_text(encoding="utf-8")

        self.assertIn(".requirements.sha256", content)
        self.assertIn("--quiet", content)
        self.assertIn("chcp 65001", content)
        self.assertIn('$ErrorActionPreference = "Continue"', content)
        self.assertNotIn('2>&1 | ForEach-Object', content)


if __name__ == "__main__":
    unittest.main()

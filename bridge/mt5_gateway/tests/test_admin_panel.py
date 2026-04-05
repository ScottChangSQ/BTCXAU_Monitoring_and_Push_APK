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


if __name__ == "__main__":
    unittest.main()

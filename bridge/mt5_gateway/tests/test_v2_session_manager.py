"""v2 会话管理器的单元测试。"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest import mock

from bridge.mt5_gateway import v2_session_manager
from bridge.mt5_gateway import v2_session_store


class V2SessionManagerTests(unittest.TestCase):
    """验证登录与退出的最小会话流程。"""

    def test_login_should_activate_current_account(self):
        """登录成功后应写入当前激活账号。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        receipt = manager.login_new_account(
            login="12345678",
            password="secret",
            server="ICMarketsSC-MT5-6",
            remember=False,
            request_id="req-login-1",
        )

        self.assertEqual("activated", receipt["state"])
        self.assertEqual("12345678", receipt["account"]["login"])
        fake_store.save_active_session.assert_called_once()

    def test_login_should_trigger_session_changed_hook(self):
        """登录成功后应触发会话变更回调。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_hook = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        manager = v2_session_manager.AccountSessionManager(
            store=fake_store,
            gateway=fake_gateway,
            on_session_changed=fake_hook,
        )

        manager.login_new_account(
            login="12345678",
            password="secret",
            server="ICMarketsSC-MT5-6",
            remember=False,
            request_id="req-login-hook",
        )

        fake_hook.assert_called_once()
        args = fake_hook.call_args[0]
        self.assertEqual("login", args[0])
        self.assertEqual("12345678", args[1]["login"])

    def test_login_with_remember_should_persist_profile(self):
        """remember=true 时应保存账号档案。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        manager.login_new_account(
            login="12345678",
            password="secret",
            server="ICMarketsSC-MT5-6",
            remember=True,
            request_id="req-login-2",
        )

        fake_store.save_profile.assert_called_once()

    def test_login_should_rollback_mt5_when_save_profile_failed(self):
        """登录后保存账号档案失败时应回滚 MT5 会话。"""
        fake_store = mock.Mock()
        fake_store.save_profile.side_effect = RuntimeError("disk full")
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.login_new_account(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                remember=True,
                request_id="req-login-rollback-profile",
            )

        fake_gateway.logout_mt5.assert_called_once_with()
        fake_store.clear_active_session.assert_called_once_with()
        fake_store.save_active_session.assert_not_called()

    def test_login_should_rollback_mt5_when_save_active_session_failed(self):
        """登录后保存激活会话失败时应回滚 MT5 会话。"""
        fake_store = mock.Mock()
        fake_store.save_active_session.side_effect = RuntimeError("disk full")
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.login_new_account(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                remember=False,
                request_id="req-login-rollback-active",
            )

        fake_gateway.logout_mt5.assert_called_once_with()
        fake_store.clear_active_session.assert_called_once_with()

    def test_login_rollback_should_trigger_session_changed_hook(self):
        """登录持久化失败并回滚时应触发会话变更回调。"""
        fake_store = mock.Mock()
        fake_store.save_profile.side_effect = RuntimeError("disk full")
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        fake_hook = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(
            store=fake_store,
            gateway=fake_gateway,
            on_session_changed=fake_hook,
        )

        with self.assertRaises(RuntimeError):
            manager.login_new_account(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                remember=True,
                request_id="req-login-rollback-hook",
            )

        fake_hook.assert_called_once()
        args = fake_hook.call_args[0]
        self.assertEqual("logout", args[0])
        self.assertEqual("12345678", args[1]["login"])

    def test_logout_should_clear_active_session(self):
        """logout 后应清理当前激活账号。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        receipt = manager.logout_current_session(request_id="req-logout-1")

        self.assertEqual("logged_out", receipt["state"])
        fake_gateway.logout_mt5.assert_called_once_with()
        fake_store.clear_active_session.assert_called_once_with()

    def test_logout_should_trigger_session_changed_hook(self):
        """退出后应触发会话变更回调。"""
        fake_store = mock.Mock()
        fake_store.load_active_session.return_value = {"profileId": "acct_1", "login": "12345678"}
        fake_gateway = mock.Mock()
        fake_hook = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(
            store=fake_store,
            gateway=fake_gateway,
            on_session_changed=fake_hook,
        )

        manager.logout_current_session(request_id="req-logout-hook")

        fake_hook.assert_called_once()
        args = fake_hook.call_args[0]
        self.assertEqual("logout", args[0])
        self.assertEqual("acct_1", args[1]["profileId"])

    def test_logout_should_not_logout_mt5_when_clear_active_session_failed(self):
        """清理激活会话失败时不应继续执行 MT5 登出。"""
        fake_store = mock.Mock()
        fake_store.load_active_session.return_value = {"profileId": "acct_1", "login": "12345678"}
        fake_store.clear_active_session.side_effect = RuntimeError("permission denied")
        fake_gateway = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.logout_current_session(request_id="req-logout-clear-failed")

        fake_gateway.logout_mt5.assert_not_called()

    def test_logout_should_restore_active_session_when_logout_mt5_failed(self):
        """MT5 登出失败时应恢复 active_session，避免文件态与运行态分裂。"""
        fake_store = mock.Mock()
        active_profile = {"profileId": "acct_1", "login": "12345678"}
        fake_store.load_active_session.return_value = active_profile
        fake_gateway = mock.Mock()
        fake_gateway.logout_mt5.side_effect = RuntimeError("mt5 busy")
        fake_hook = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(
            store=fake_store,
            gateway=fake_gateway,
            on_session_changed=fake_hook,
        )

        with self.assertRaises(RuntimeError):
            manager.logout_current_session(request_id="req-logout-gateway-failed")

        fake_store.clear_active_session.assert_called_once_with()
        fake_store.save_active_session.assert_called_once_with(active_profile)
        fake_hook.assert_not_called()


class V2SessionStoreTests(unittest.TestCase):
    """验证会话存储层最小读写行为。"""

    def test_load_profile_should_return_full_record(self):
        """按 profileId 应能读回完整档案。"""
        with tempfile.TemporaryDirectory() as temp_dir:
            store = v2_session_store.FileSessionStore(Path(temp_dir) / "session")
            profile = {
                "profileId": "acct_123",
                "login": "12345678",
                "loginMasked": "****5678",
                "server": "ICMarketsSC-MT5-6",
                "displayName": "IC 5678",
            }
            saved = store.save_profile(profile, "secret")

            loaded = store.load_profile("acct_123")

        self.assertIsNotNone(loaded)
        self.assertEqual(saved["encryptedPassword"], loaded["encryptedPassword"])
        self.assertEqual("12345678", loaded["profile"]["login"])


if __name__ == "__main__":
    unittest.main()

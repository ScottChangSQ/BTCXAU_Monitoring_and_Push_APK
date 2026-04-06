"""v2 会话管理器的单元测试。"""

from __future__ import annotations

import base64
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from bridge.mt5_gateway import v2_session_crypto
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

    def test_switch_saved_account_should_clear_gateway_caches_before_refresh(self):
        """切换已保存账号成功后应先清缓存，再触发强制刷新。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        profile = {
            "profileId": "acct_87654321_icmarketssc-mt5-6",
            "login": "87654321",
            "loginMasked": "****4321",
            "server": "ICMarketsSC-MT5-6",
            "displayName": "IC 4321",
        }
        cipher = v2_session_crypto.protect_secret_for_machine(b"secret")
        fake_store.load_profile.return_value = {
            "profile": profile,
            "encryptedPassword": base64.b64encode(cipher).decode("ascii"),
        }
        fake_gateway.switch_mt5_account.return_value = {
            "login": "87654321",
            "server": "ICMarketsSC-MT5-6",
        }
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        receipt = manager.switch_saved_account("acct_87654321_icmarketssc-mt5-6", request_id="req-switch-1")

        self.assertEqual(True, receipt["ok"])
        self.assertEqual("activated", receipt["state"])
        fake_gateway.switch_mt5_account.assert_called_once_with(
            login="87654321",
            password="secret",
            server="ICMarketsSC-MT5-6",
        )
        fake_gateway.clear_account_caches.assert_called_once_with()
        fake_gateway.force_account_resync.assert_called_once_with()

    def test_switch_saved_account_should_restore_previous_account_when_target_switch_failed(self):
        """切换目标账号失败时，应尝试恢复旧账号运行态。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        old_profile = {
            "profileId": "acct_old",
            "login": "12345678",
            "loginMasked": "****5678",
            "server": "ICMarketsSC-MT5-6",
            "displayName": "IC old",
        }
        target_profile = {
            "profileId": "acct_new",
            "login": "87654321",
            "loginMasked": "****4321",
            "server": "Pepperstone-MT5-Live01",
            "displayName": "Pepper new",
        }
        old_cipher = v2_session_crypto.protect_secret_for_machine(b"old-secret")
        new_cipher = v2_session_crypto.protect_secret_for_machine(b"new-secret")
        profile_records = {
            "acct_old": {"profile": old_profile, "encryptedPassword": base64.b64encode(old_cipher).decode("ascii")},
            "acct_new": {"profile": target_profile, "encryptedPassword": base64.b64encode(new_cipher).decode("ascii")},
        }
        fake_store.load_profile.side_effect = lambda profile_id: profile_records.get(profile_id)
        fake_store.load_active_session.return_value = old_profile
        fake_gateway.switch_mt5_account.side_effect = [
            RuntimeError("target login failed"),
            {"login": "12345678", "server": "ICMarketsSC-MT5-6"},
        ]
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.switch_saved_account("acct_new", request_id="req-switch-fail-1")

        self.assertEqual(2, fake_gateway.switch_mt5_account.call_count)
        restore_call = fake_gateway.switch_mt5_account.call_args_list[1]
        self.assertEqual("12345678", restore_call.kwargs["login"])
        self.assertEqual("old-secret", restore_call.kwargs["password"])
        self.assertEqual("ICMarketsSC-MT5-6", restore_call.kwargs["server"])
        fake_store.clear_active_session.assert_not_called()

    def test_switch_saved_account_should_clear_active_session_when_restore_is_unavailable(self):
        """切换失败且无法恢复旧账号时，应清空会话避免伪旧状态。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        old_profile = {
            "profileId": "acct_old_tmp",
            "login": "12345678",
            "loginMasked": "****5678",
            "server": "ICMarketsSC-MT5-6",
            "displayName": "IC old",
        }
        target_profile = {
            "profileId": "acct_new",
            "login": "87654321",
            "loginMasked": "****4321",
            "server": "Pepperstone-MT5-Live01",
            "displayName": "Pepper new",
        }
        new_cipher = v2_session_crypto.protect_secret_for_machine(b"new-secret")
        fake_store.load_profile.side_effect = lambda profile_id: (
            {"profile": target_profile, "encryptedPassword": base64.b64encode(new_cipher).decode("ascii")}
            if profile_id == "acct_new"
            else None
        )
        fake_store.load_active_session.return_value = old_profile
        fake_gateway.switch_mt5_account.side_effect = RuntimeError("target login failed")
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.switch_saved_account("acct_new", request_id="req-switch-fail-2")

        fake_store.clear_active_session.assert_called_once_with()
        fake_gateway.logout_mt5.assert_called_once_with()

    def test_switch_saved_account_should_rollback_when_commit_after_switch_failed(self):
        """切换成功后若持久化失败，应回滚到旧账号。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        old_profile = {
            "profileId": "acct_old",
            "login": "12345678",
            "loginMasked": "****5678",
            "server": "ICMarketsSC-MT5-6",
            "displayName": "IC old",
        }
        target_profile = {
            "profileId": "acct_new",
            "login": "87654321",
            "loginMasked": "****4321",
            "server": "Pepperstone-MT5-Live01",
            "displayName": "Pepper new",
        }
        old_cipher = v2_session_crypto.protect_secret_for_machine(b"old-secret")
        new_cipher = v2_session_crypto.protect_secret_for_machine(b"new-secret")
        profile_records = {
            "acct_old": {"profile": old_profile, "encryptedPassword": base64.b64encode(old_cipher).decode("ascii")},
            "acct_new": {"profile": target_profile, "encryptedPassword": base64.b64encode(new_cipher).decode("ascii")},
        }
        fake_store.load_profile.side_effect = lambda profile_id: profile_records.get(profile_id)
        fake_store.load_active_session.return_value = old_profile
        fake_store.save_active_session.side_effect = [RuntimeError("disk full"), None]
        fake_gateway.switch_mt5_account.side_effect = [
            {"login": "87654321", "server": "Pepperstone-MT5-Live01"},
            {"login": "12345678", "server": "ICMarketsSC-MT5-6"},
        ]
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.switch_saved_account("acct_new", request_id="req-switch-fail-3")

        self.assertEqual(2, fake_gateway.switch_mt5_account.call_count)
        self.assertEqual(2, fake_store.save_active_session.call_count)
        fake_gateway.logout_mt5.assert_not_called()


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

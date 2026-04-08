"""v2 会话管理器的单元测试。"""

from __future__ import annotations

import base64
import json
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
        self.assertEqual("12345678", receipt["activeAccount"]["login"])
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

    def test_login_should_clear_gateway_caches_before_refresh(self):
        """新账号登录成功后也应清缓存并触发强制刷新。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        receipt = manager.login_new_account(
            login="12345678",
            password="secret",
            server="ICMarketsSC-MT5-6",
            remember=False,
            request_id="req-login-refresh",
        )

        self.assertEqual(True, receipt["ok"])
        self.assertEqual("activated", receipt["state"])
        fake_gateway.clear_account_caches.assert_called_once_with()
        fake_gateway.force_account_resync.assert_called_once_with()

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

    def test_login_should_fail_when_gateway_account_meta_missing_server(self):
        """登录成功后若网关未返回完整账号身份，不应回退输入值拼装 activeAccount。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": ""}
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(ValueError) as error_ctx:
            manager.login_new_account(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                remember=False,
                request_id="req-login-missing-server",
            )

        self.assertIn("canonical account identity", str(error_ctx.exception))
        fake_store.save_active_session.assert_not_called()
        fake_gateway.logout_mt5.assert_not_called()

    def test_login_should_clear_previous_active_session_when_gateway_identity_missing(self):
        """新登录在运行态已切空后若拿不到 canonical identity，应把旧文件态收口到 logged_out。"""
        fake_store = mock.Mock()
        fake_store.load_active_session.return_value = {
            "profileId": "acct_old",
            "login": "87654321",
            "loginMasked": "****4321",
            "server": "Pepperstone-MT5-Live01",
            "displayName": "Pepper old",
            "active": True,
            "state": "activated",
        }
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": ""}
        fake_hook = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(
            store=fake_store,
            gateway=fake_gateway,
            on_session_changed=fake_hook,
        )

        with self.assertRaises(ValueError) as error_ctx:
            manager.login_new_account(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                remember=False,
                request_id="req-login-missing-server-clear-old",
            )

        self.assertIn("canonical account identity", str(error_ctx.exception))
        fake_store.clear_active_session.assert_called_once_with()
        fake_hook.assert_called_once_with("logout", fake_store.load_active_session.return_value)
        fake_store.save_active_session.assert_not_called()
        fake_gateway.logout_mt5.assert_not_called()

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

    def test_login_should_rollback_when_refresh_after_commit_failed(self):
        """登录提交后若强制刷新失败，也应回滚，避免留下半成功状态。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        fake_gateway.force_account_resync.side_effect = RuntimeError("refresh failed")
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.login_new_account(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                remember=False,
                request_id="req-login-refresh-failed",
            )

        fake_store.save_active_session.assert_called_once()
        fake_gateway.clear_account_caches.assert_called_once_with()
        fake_store.clear_active_session.assert_called_once_with()
        fake_gateway.logout_mt5.assert_called_once_with()

    def test_login_should_remove_new_saved_profile_when_commit_failed(self):
        """记住账号的新登录若提交失败，不应留下新的已保存账号档案。"""
        fake_store = mock.Mock()
        fake_store.load_profile.return_value = None
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        fake_gateway.force_account_resync.side_effect = RuntimeError("refresh failed")
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.login_new_account(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                remember=True,
                request_id="req-login-rollback-new-profile",
            )

        fake_store.restore_profile_record.assert_called_once_with(
            "acct_12345678_icmarketssc-mt5-6",
            None,
        )
        fake_gateway.logout_mt5.assert_called_once_with()

    def test_login_should_restore_previous_saved_profile_when_commit_failed(self):
        """记住账号覆盖旧档案后若提交失败，应恢复旧档案内容。"""
        fake_store = mock.Mock()
        previous_record = {
            "profile": {
                "profileId": "acct_12345678_icmarketssc-mt5-6",
                "login": "12345678",
                "loginMasked": "****5678",
                "server": "ICMarketsSC-MT5-6",
                "displayName": "ICMarketsSC-MT5-6 ****5678",
                "active": False,
                "state": "",
            },
            "encryptedPassword": "old-password-cipher",
            "updatedAtMs": 1,
        }
        fake_store.load_profile.return_value = previous_record
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}
        fake_gateway.force_account_resync.side_effect = RuntimeError("refresh failed")
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(RuntimeError):
            manager.login_new_account(
                login="12345678",
                password="new-secret",
                server="ICMarketsSC-MT5-6",
                remember=True,
                request_id="req-login-rollback-restore-profile",
            )

        fake_store.restore_profile_record.assert_called_once_with(
            "acct_12345678_icmarketssc-mt5-6",
            previous_record,
        )
        fake_gateway.logout_mt5.assert_called_once_with()

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

    def test_build_status_payload_should_keep_active_flag_when_active_session_only_has_activated_state(self):
        """旧会话文件只剩 state=activated 时，状态接口也不应丢失激活标记。"""
        fake_store = mock.Mock()
        fake_store.load_active_session.return_value = {
            "profileId": "acct_12345678_icmarketssc-mt5-6",
            "login": "12345678",
            "loginMasked": "****5678",
            "server": "ICMarketsSC-MT5-6",
            "displayName": "ICMarketsSC-MT5-6 ****5678",
            "state": "activated",
        }
        fake_store.list_profiles.return_value = []
        fake_gateway = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        payload = manager.build_status_payload()

        self.assertEqual("activated", payload["state"])
        self.assertEqual(True, payload["activeAccount"]["active"])
        self.assertEqual("activated", payload["activeAccount"]["state"])

    def test_build_status_payload_should_drop_incomplete_active_session_identity(self):
        """active_session 缺关键身份时，不应继续对外宣称 activated。"""
        fake_store = mock.Mock()
        fake_store.load_active_session.return_value = {
            "profileId": "acct_12345678_icmarketssc-mt5-6",
            "login": "12345678",
            "loginMasked": "****5678",
            "server": "",
            "displayName": "ICMarketsSC-MT5-6 ****5678",
            "active": True,
            "state": "activated",
        }
        fake_store.list_profiles.return_value = []
        fake_gateway = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        payload = manager.build_status_payload()

        self.assertEqual("logged_out", payload["state"])
        self.assertIsNone(payload["activeAccount"])

    def test_build_status_payload_should_strip_runtime_flags_from_saved_accounts(self):
        """legacy saved profile 就算残留 active/state，也不能在 savedAccounts 里继续冒充激活态。"""
        fake_store = mock.Mock()
        fake_store.load_active_session.return_value = {
            "profileId": "acct_active",
            "login": "12345678",
            "loginMasked": "****5678",
            "server": "ICMarketsSC-MT5-6",
            "displayName": "IC active",
            "active": True,
            "state": "activated",
        }
        fake_store.list_profiles.return_value = [
            {
                "profileId": "acct_saved",
                "login": "87654321",
                "loginMasked": "****4321",
                "server": "Pepperstone-MT5-Live01",
                "displayName": "Pepper saved",
                "active": True,
                "state": "activated",
            }
        ]
        fake_gateway = mock.Mock()
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        payload = manager.build_status_payload()

        self.assertEqual(True, payload["activeAccount"]["active"])
        self.assertEqual("activated", payload["activeAccount"]["state"])
        self.assertEqual(False, payload["savedAccounts"][0]["active"])
        self.assertEqual("", payload["savedAccounts"][0]["state"])

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

    def test_switch_saved_account_should_fail_when_gateway_account_meta_missing_server(self):
        """切换已保存账号后若网关未返回完整身份，不应回退已保存凭据拼装 activeAccount。"""
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
            "server": "",
        }
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(ValueError) as error_ctx:
            manager.switch_saved_account("acct_87654321_icmarketssc-mt5-6", request_id="req-switch-missing-server")

        self.assertIn("canonical account identity", str(error_ctx.exception))
        fake_store.save_active_session.assert_not_called()

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

    def test_save_profile_should_clear_active_flag_before_persist(self):
        """已保存账号档案不应把当前激活态直接落盘。"""
        with tempfile.TemporaryDirectory() as temp_dir:
            store = v2_session_store.FileSessionStore(Path(temp_dir) / "session")
            profile = {
                "profileId": "acct_123",
                "login": "12345678",
                "loginMasked": "****5678",
                "server": "ICMarketsSC-MT5-6",
                "displayName": "IC 5678",
                "active": True,
                "state": "activated",
            }

            saved = store.save_profile(profile, "secret")

        self.assertEqual(False, saved["profile"]["active"])
        self.assertEqual("", saved["profile"]["state"])

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

    def test_restore_profile_record_should_delete_file_when_record_is_none(self):
        """恢复空档案快照时应删除现有账号文件。"""
        with tempfile.TemporaryDirectory() as temp_dir:
            session_root = Path(temp_dir) / "session"
            store = v2_session_store.FileSessionStore(session_root)
            store.save_profile(
                {
                    "profileId": "acct_123",
                    "login": "12345678",
                    "loginMasked": "****5678",
                    "server": "ICMarketsSC-MT5-6",
                    "displayName": "IC 5678",
                },
                "secret",
            )

            store.restore_profile_record("acct_123", None)

            self.assertIsNone(store.load_profile("acct_123"))

    def test_restore_profile_record_should_write_snapshot_back(self):
        """恢复旧档案快照时应完整写回原始记录。"""
        with tempfile.TemporaryDirectory() as temp_dir:
            session_root = Path(temp_dir) / "session"
            store = v2_session_store.FileSessionStore(session_root)
            snapshot = {
                "profile": {
                    "profileId": "acct_123",
                    "login": "12345678",
                    "loginMasked": "****5678",
                    "server": "ICMarketsSC-MT5-6",
                    "displayName": "IC 5678",
                    "active": False,
                    "state": "",
                },
                "encryptedPassword": "cipher-1",
                "updatedAtMs": 123,
            }

            store.restore_profile_record("acct_123", snapshot)
            loaded = store.load_profile("acct_123")

            self.assertEqual(snapshot, loaded)

    def test_load_profile_should_return_none_when_record_json_invalid(self):
        """账号档案 JSON 损坏时应按缺失处理，而不是直接抛异常。"""
        with tempfile.TemporaryDirectory() as temp_dir:
            session_root = Path(temp_dir) / "session"
            accounts_dir = session_root / "accounts"
            accounts_dir.mkdir(parents=True, exist_ok=True)
            (accounts_dir / "acct_bad.json").write_text("{bad json", encoding="utf-8")
            store = v2_session_store.FileSessionStore(session_root)

            loaded = store.load_profile("acct_bad")

        self.assertIsNone(loaded)

    def test_list_profiles_should_skip_invalid_json_record(self):
        """列出账号摘要时应跳过损坏的档案文件。"""
        with tempfile.TemporaryDirectory() as temp_dir:
            session_root = Path(temp_dir) / "session"
            accounts_dir = session_root / "accounts"
            accounts_dir.mkdir(parents=True, exist_ok=True)
            (accounts_dir / "acct_bad.json").write_text("{bad json", encoding="utf-8")
            good_record = {
                "profile": {
                    "profileId": "acct_good",
                    "login": "12345678",
                    "loginMasked": "****5678",
                    "server": "ICMarketsSC-MT5-6",
                    "displayName": "IC 5678",
                }
            }
            (accounts_dir / "acct_good.json").write_text(
                json.dumps(good_record, ensure_ascii=False),
                encoding="utf-8",
            )
            store = v2_session_store.FileSessionStore(session_root)

            profiles = store.list_profiles()

        self.assertEqual(1, len(profiles))
        self.assertEqual("acct_good", profiles[0]["profileId"])

    def test_list_profiles_should_skip_record_with_non_object_profile(self):
        """列出账号摘要时应跳过 profile 结构错误的记录。"""
        with tempfile.TemporaryDirectory() as temp_dir:
            session_root = Path(temp_dir) / "session"
            accounts_dir = session_root / "accounts"
            accounts_dir.mkdir(parents=True, exist_ok=True)
            bad_record = {"profile": ["wrong-shape"]}
            good_record = {
                "profile": {
                    "profileId": "acct_good",
                    "login": "12345678",
                    "loginMasked": "****5678",
                    "server": "ICMarketsSC-MT5-6",
                    "displayName": "IC 5678",
                }
            }
            (accounts_dir / "acct_bad.json").write_text(
                json.dumps(bad_record, ensure_ascii=False),
                encoding="utf-8",
            )
            (accounts_dir / "acct_good.json").write_text(
                json.dumps(good_record, ensure_ascii=False),
                encoding="utf-8",
            )
            store = v2_session_store.FileSessionStore(session_root)

            profiles = store.list_profiles()

        self.assertEqual(1, len(profiles))
        self.assertEqual("acct_good", profiles[0]["profileId"])

    def test_switch_saved_account_should_raise_value_error_when_profile_shape_invalid(self):
        """已保存账号档案 profile 结构错误时，应按不可用账号处理。"""
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_store.load_profile.return_value = {
            "profile": ["wrong-shape"],
            "encryptedPassword": "c2VjcmV0",
        }
        manager = v2_session_manager.AccountSessionManager(store=fake_store, gateway=fake_gateway)

        with self.assertRaises(ValueError) as error_ctx:
            manager.switch_saved_account("acct_bad", request_id="req-switch-bad-shape")
        self.assertIn("saved profile not found", str(error_ctx.exception))


if __name__ == "__main__":
    unittest.main()

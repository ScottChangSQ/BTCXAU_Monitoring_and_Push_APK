"""v2 会话接口契约测试。"""

from __future__ import annotations

import base64
import json
import os
import sys
import time
import threading
import types
import unittest
import uuid
from pathlib import Path
from unittest import mock


def _install_test_stubs():
    """注入最小依赖桩，保证可以导入 server_v2。"""
    if "uvicorn" not in sys.modules:
        uvicorn_stub = types.ModuleType("uvicorn")
        uvicorn_stub.run = lambda *args, **kwargs: None
        sys.modules["uvicorn"] = uvicorn_stub

    if "dotenv" not in sys.modules:
        dotenv_stub = types.ModuleType("dotenv")
        dotenv_stub.load_dotenv = lambda *args, **kwargs: None
        sys.modules["dotenv"] = dotenv_stub

    if "fastapi" not in sys.modules:
        fastapi_stub = types.ModuleType("fastapi")

        class _FastAPI:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

            def add_middleware(self, *args, **kwargs):
                return None

            def get(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator

            def post(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator

            def websocket(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator

        class _HTTPException(Exception):
            def __init__(self, status_code=500, detail=""):
                super().__init__(detail)
                self.status_code = status_code
                self.detail = detail

        fastapi_stub.FastAPI = _FastAPI
        fastapi_stub.HTTPException = _HTTPException
        fastapi_stub.Header = lambda default=None, **kwargs: default
        fastapi_stub.Query = lambda default=None, **kwargs: default
        fastapi_stub.Request = type("Request", (), {})
        fastapi_stub.Response = type("Response", (), {})
        fastapi_stub.WebSocket = type("WebSocket", (), {})
        fastapi_stub.WebSocketDisconnect = Exception
        sys.modules["fastapi"] = fastapi_stub

    if "fastapi.middleware.gzip" not in sys.modules:
        gzip_stub = types.ModuleType("fastapi.middleware.gzip")

        class _GZipMiddleware:
            pass

        gzip_stub.GZipMiddleware = _GZipMiddleware
        sys.modules["fastapi.middleware.gzip"] = gzip_stub

    if "websockets" not in sys.modules:
        websockets_stub = types.ModuleType("websockets")
        websockets_stub.connect = lambda *args, **kwargs: None
        sys.modules["websockets"] = websockets_stub


_install_test_stubs()

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import server_v2  # noqa: E402


class V2SessionContractsTests(unittest.TestCase):
    """验证会话接口响应结构。"""

    def _build_login_envelope(self, key_payload: dict, *, key_id: str, client_time: int, nonce: str = "") -> dict:
        """按网关公钥构造最小可解密登录信封。"""
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM

        public_key = serialization.load_pem_public_key(str(key_payload.get("publicKeyPem") or "").encode("utf-8"))
        aes_key = os.urandom(32)
        iv = os.urandom(12)
        plain_payload = {
            "login": "12345678",
            "password": "secret",
            "server": "ICMarketsSC-MT5-6",
            "remember": True,
            "nonce": str(nonce or f"nonce-{uuid.uuid4().hex}"),
            "clientTime": int(client_time),
        }
        encrypted_payload = AESGCM(aes_key).encrypt(iv, json.dumps(plain_payload).encode("utf-8"), None)
        encrypted_key = public_key.encrypt(
            aes_key,
            padding.OAEP(
                mgf=padding.MGF1(algorithm=hashes.SHA256()),
                algorithm=hashes.SHA256(),
                label=None,
            ),
        )
        return {
            "requestId": "req-login-1",
            "keyId": str(key_id),
            "algorithm": str(key_payload.get("algorithm") or ""),
            "encryptedKey": base64.b64encode(encrypted_key).decode("ascii"),
            "encryptedPayload": base64.b64encode(encrypted_payload).decode("ascii"),
            "iv": base64.b64encode(iv).decode("ascii"),
            "saveAccount": True,
        }

    def test_session_status_should_return_stable_shape(self):
        """状态接口应始终返回固定结构字段。"""
        payload = server_v2.v2_session_status()

        self.assertIn("ok", payload)
        self.assertIn("state", payload)
        self.assertIn("activeAccount", payload)
        self.assertIn("savedAccounts", payload)
        self.assertIn("savedAccountCount", payload)
        self.assertIsInstance(payload["savedAccounts"], list)

    def test_session_logout_should_forward_request_id(self):
        """logout 接口应把 requestId 传给会话管理器。"""
        expected = {"ok": True, "state": "logged_out", "requestId": "req-logout-1"}
        with mock.patch.object(
            server_v2.session_manager,
            "logout_current_session",
            return_value=expected,
        ) as logout_mock:
            payload = server_v2.v2_session_logout({"requestId": "req-logout-1"})

        logout_mock.assert_called_once_with(request_id="req-logout-1")
        self.assertEqual(expected, payload)

    def test_session_credentials_should_prefer_runtime_session_over_env(self):
        """会话凭据解析应优先使用当前会话与运行时密码。"""
        original_runtime = dict(getattr(server_v2, "session_runtime_credentials", {}))
        try:
            with mock.patch.object(server_v2, "LOGIN", 7400048), mock.patch.object(
                server_v2, "PASSWORD", "env-secret"
            ):
                server_v2._set_runtime_session_credentials("12345678", "runtime-secret", "SESSION-SERVER")
                creds = server_v2._current_mt5_credentials()
        finally:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(original_runtime)

        self.assertEqual(12345678, creds["login"])
        self.assertEqual("runtime-secret", creds["password"])
        self.assertEqual("SESSION-SERVER", creds["server"])
        self.assertEqual("remote_active", creds["source"])
        self.assertEqual("remote_active", creds["mode"])

    def test_mt5_configured_should_not_depend_only_on_env_password(self):
        """当 env 密码为空时，只要会话运行时密码存在也应视为可配置。"""
        original_runtime = dict(getattr(server_v2, "session_runtime_credentials", {}))
        try:
            with mock.patch.object(server_v2, "LOGIN", 7400048), mock.patch.object(
                server_v2, "PASSWORD", ""
            ):
                server_v2._set_runtime_session_credentials("12345678", "runtime-secret", "SESSION-SERVER")
                configured = server_v2._is_mt5_configured()
        finally:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(original_runtime)

        self.assertTrue(configured)

    def test_remote_logged_out_should_not_fallback_to_env(self):
        """登出后应进入 remote_logged_out，且不自动回退 env。"""
        original_runtime = dict(getattr(server_v2, "session_runtime_credentials", {}))
        try:
            with mock.patch.object(server_v2, "LOGIN", 7400048), mock.patch.object(
                server_v2, "PASSWORD", "env-secret"
            ), mock.patch.object(server_v2, "SERVER", "ENV-SERVER"):
                server_v2._clear_runtime_session_credentials()
                creds = server_v2._current_mt5_credentials()
                configured = server_v2._is_mt5_configured()
        finally:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(original_runtime)

        self.assertEqual("remote_logged_out", creds["mode"])
        self.assertEqual(0, creds["login"])
        self.assertEqual("", creds["password"])
        self.assertFalse(configured)

    def test_session_changed_hook_should_clear_runtime_caches(self):
        """会话变更 hook 应清理最小必要缓存。"""
        server_v2.snapshot_build_cache = {"build": {"snapshot": 1}}
        server_v2.snapshot_sync_cache = {"sync": {"snapshot": 2}}
        server_v2.health_status_cache = {"health": {"ok": True}}
        server_v2.v2_sync_state = {"seq": 3}
        server_v2.trade_request_store = {"req": {"payloadDigest": "x", "response": {"ok": True}}}

        server_v2._on_session_changed("login", {"profileId": "acct_1"})

        self.assertEqual({}, server_v2.snapshot_build_cache)
        self.assertEqual({}, server_v2.snapshot_sync_cache)
        self.assertEqual({}, server_v2.health_status_cache)
        self.assertEqual({}, server_v2.v2_sync_state)
        self.assertEqual({}, server_v2.trade_request_store)

    def test_build_session_manager_should_wire_server_hook(self):
        """会话管理器构造时应接入服务端缓存失效 hook。"""
        manager = server_v2._build_session_manager()
        self.assertIs(manager._on_session_changed, server_v2._on_session_changed)

    def test_build_session_manager_should_clear_stale_active_session_when_runtime_not_remote_active(self):
        """服务端重启后若只剩磁盘 active_session，不应继续对外宣称 activated。"""
        stale_active = {
            "profileId": "acct_12345678_icmarketssc-mt5-6",
            "login": "12345678",
            "loginMasked": "****5678",
            "server": "ICMarketsSC-MT5-6",
            "displayName": "ICMarketsSC-MT5-6 ****5678",
            "active": True,
            "state": "activated",
        }
        original_runtime = dict(getattr(server_v2, "session_runtime_credentials", {}))
        try:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(
                {
                    "mode": "env_default",
                    "login": 7400048,
                    "password": "env-secret",
                    "server": "ENV-SERVER",
                }
            )
            with mock.patch.object(server_v2.session_store, "load_active_session", side_effect=[stale_active, None]), \
                    mock.patch.object(server_v2.session_store, "clear_active_session") as clear_mock:
                manager = server_v2._build_session_manager()
                payload = manager.build_status_payload()
        finally:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(original_runtime)

        clear_mock.assert_called_once_with()
        self.assertEqual("logged_out", payload["state"])
        self.assertIsNone(payload["activeAccount"])

    def test_session_public_key_should_return_stable_shape(self):
        """public-key 接口应返回稳定结构。"""
        payload = server_v2.v2_session_public_key()

        self.assertEqual(True, payload["ok"])
        self.assertIn("keyId", payload)
        self.assertIn("algorithm", payload)
        self.assertIn("publicKeyPem", payload)
        self.assertIn("expiresAt", payload)
        self.assertIn("activeAccount", payload)
        self.assertIn("savedAccounts", payload)
        self.assertIn("savedAccountCount", payload)

    def test_session_login_should_reject_wrong_key_id(self):
        """login 接口遇到错误 keyId 时应失败。"""
        key_payload = server_v2.v2_session_public_key()
        envelope = self._build_login_envelope(
            key_payload,
            key_id="wrong-key-id",
            client_time=int(time.time() * 1000),
        )

        with self.assertRaises(server_v2.HTTPException) as error_ctx:
            server_v2.v2_session_login(envelope)

        self.assertEqual(400, error_ctx.exception.status_code)
        self.assertIn("keyId", str(error_ctx.exception.detail))

    def test_session_login_should_reject_expired_client_time(self):
        """login 接口遇到过期时间戳时应失败。"""
        key_payload = server_v2.v2_session_public_key()
        expired_client_time = int(time.time() * 1000) - 30 * 60 * 1000
        envelope = self._build_login_envelope(
            key_payload,
            key_id=str(key_payload.get("keyId") or ""),
            client_time=expired_client_time,
        )

        with self.assertRaises(server_v2.HTTPException) as error_ctx:
            server_v2.v2_session_login(envelope)

        self.assertEqual(400, error_ctx.exception.status_code)
        self.assertIn("time", str(error_ctx.exception.detail))

    def test_session_login_should_forward_decrypted_credentials(self):
        """login 接口应把解密后的凭据交给会话管理器。"""
        key_payload = server_v2.v2_session_public_key()
        envelope = self._build_login_envelope(
            key_payload,
            key_id=str(key_payload.get("keyId") or ""),
            client_time=int(time.time() * 1000),
        )
        expected = {"ok": True, "state": "activated", "requestId": "req-login-1"}
        with mock.patch.object(server_v2.session_manager, "login_new_account", return_value=expected) as login_mock:
            payload = server_v2.v2_session_login(envelope)

        login_mock.assert_called_once_with(
            login="12345678",
            password="secret",
            server="ICMarketsSC-MT5-6",
            remember=True,
            request_id="req-login-1",
        )
        self.assertEqual(expected, payload)

    def test_session_login_should_treat_string_false_save_account_as_false(self):
        """login 接口里的 saveAccount='false' 不得被当成 True。"""
        key_payload = server_v2.v2_session_public_key()
        envelope = self._build_login_envelope(
            key_payload,
            key_id=str(key_payload.get("keyId") or ""),
            client_time=int(time.time() * 1000),
        )
        envelope["saveAccount"] = "false"
        expected = {"ok": True, "state": "activated", "requestId": "req-login-1"}
        with mock.patch.object(server_v2.session_manager, "login_new_account", return_value=expected) as login_mock:
            payload = server_v2.v2_session_login(envelope)

        login_mock.assert_called_once_with(
            login="12345678",
            password="secret",
            server="ICMarketsSC-MT5-6",
            remember=False,
            request_id="req-login-1",
        )
        self.assertEqual(expected, payload)

    def test_session_switch_should_forward_profile_id_and_request_id(self):
        """switch 接口应把账号标识和 requestId 传给会话管理器。"""
        expected = {"ok": True, "state": "activated", "requestId": "req-switch-1"}
        with mock.patch.object(server_v2.session_manager, "switch_saved_account", return_value=expected) as switch_mock:
            payload = server_v2.v2_session_switch(
                {"requestId": "req-switch-1", "accountProfileId": "acct_2"}
            )

        switch_mock.assert_called_once_with("acct_2", request_id="req-switch-1")
        self.assertEqual(expected, payload)

    def test_session_login_should_reject_replayed_nonce(self):
        """同一个 nonce 在时间窗内重复提交时应拒绝。"""
        key_payload = server_v2.v2_session_public_key()
        envelope = self._build_login_envelope(
            key_payload,
            key_id=str(key_payload.get("keyId") or ""),
            client_time=int(time.time() * 1000),
            nonce="nonce-replay-1",
        )
        expected = {"ok": True, "state": "activated", "requestId": "req-login-1"}
        with mock.patch.object(server_v2.session_manager, "login_new_account", return_value=expected):
            first = server_v2.v2_session_login(envelope)
            self.assertEqual(True, first["ok"])
            with self.assertRaises(server_v2.HTTPException) as error_ctx:
                server_v2.v2_session_login(envelope)

        self.assertEqual(400, error_ctx.exception.status_code)
        self.assertIn("nonce", str(error_ctx.exception.detail))

    def test_login_envelope_crypto_should_block_concurrent_replay_nonce(self):
        """并发同 nonce 请求下，最多只能有一个通过。"""
        from bridge.mt5_gateway import v2_session_crypto

        class _RaceNonceMap(dict):
            """制造并发 contains 竞争，放大无锁 check-then-set 的穿透概率。"""

            def __init__(self):
                super().__init__()
                self._barrier = threading.Barrier(2)
                self._guard = threading.Lock()
                self._contains_calls = 0
                self._snapshot = False

            def __contains__(self, key):
                with self._guard:
                    if self._contains_calls == 0:
                        self._snapshot = super().__contains__(key)
                    self._contains_calls += 1
                    call_index = self._contains_calls
                try:
                    self._barrier.wait(timeout=0.2)
                    barrier_completed = True
                except threading.BrokenBarrierError:
                    barrier_completed = False
                if barrier_completed and call_index <= 2:
                    return bool(self._snapshot)
                return super().__contains__(key)

        now_ms = int(time.time() * 1000)
        crypto = v2_session_crypto.LoginEnvelopeCrypto(now_ms_provider=lambda: now_ms)
        key_payload = crypto.build_public_key_payload()
        crypto._nonce_seen_at = _RaceNonceMap()
        envelope = self._build_login_envelope(
            key_payload,
            key_id=str(key_payload.get("keyId") or ""),
            client_time=now_ms,
            nonce="nonce-concurrent-1",
        )
        success_count = 0
        errors = []
        counter_lock = threading.Lock()

        def _worker():
            nonlocal success_count
            try:
                crypto.decrypt_login_envelope(envelope)
                with counter_lock:
                    success_count += 1
            except Exception as exc:
                with counter_lock:
                    errors.append(str(exc))

        t1 = threading.Thread(target=_worker)
        t2 = threading.Thread(target=_worker)
        t1.start()
        t2.start()
        t1.join()
        t2.join()

        self.assertEqual(1, success_count)
        self.assertEqual(1, len(errors))
        self.assertIn("nonce", errors[0])


if __name__ == "__main__":
    unittest.main()

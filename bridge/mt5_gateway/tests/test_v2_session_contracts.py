"""v2 会话接口契约测试。"""

from __future__ import annotations

import base64
import io
import json
import os
import subprocess
import sys
import tempfile
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
from bridge.mt5_gateway import mt5_login_probe  # noqa: E402


class V2SessionContractsTests(unittest.TestCase):
    """验证会话接口响应结构。"""

    def setUp(self):
        self._original_runtime_status = dict(getattr(server_v2, "gateway_runtime_status", {}))

    def tearDown(self):
        runtime_status = getattr(server_v2, "gateway_runtime_status", None)
        if isinstance(runtime_status, dict):
            runtime_status.clear()
            runtime_status.update(self._original_runtime_status)

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

    def test_internal_runtime_status_should_return_stable_shape(self):
        """运行时状态接口应始终返回固定结构字段。"""
        payload = server_v2.internal_runtime_status()

        self.assertIn("streamClientsActive", payload)
        self.assertIn("streamLastConnectedAt", payload)
        self.assertIn("streamLastDisconnectedAt", payload)
        self.assertIn("httpLastRequestAt", payload)
        self.assertIn("httpLastRequestPath", payload)
        self.assertIn("sessionLastAction", payload)
        self.assertIn("sessionLastRequestAt", payload)
        self.assertIn("sessionLastResult", payload)
        self.assertIn("tradeLastAction", payload)
        self.assertIn("tradeLastRequestAt", payload)
        self.assertIn("tradeLastResult", payload)
        self.assertIn("lastClientAddress", payload)

    def test_internal_runtime_panel_should_return_aggregated_shape(self):
        """状态面板聚合接口应返回固定的五段结构。"""
        payload = server_v2.internal_runtime_panel()

        self.assertIn("health", payload)
        self.assertIn("source", payload)
        self.assertIn("session", payload)
        self.assertIn("runtime", payload)
        self.assertIn("latestDiagnostic", payload)

    def test_internal_runtime_panel_should_return_partial_payload_when_source_fails(self):
        """状态面板聚合接口局部失败时不能整页 500。"""
        with mock.patch.object(server_v2, "source_status", side_effect=RuntimeError("source exploded")):
            payload = server_v2.internal_runtime_panel()

        self.assertIn("health", payload)
        self.assertIn("source", payload)
        self.assertEqual("source", payload["source"]["section"])
        self.assertIn("source exploded", payload["source"]["__error"])
        self.assertIn("session", payload)
        self.assertIn("runtime", payload)

    def test_internal_runtime_panel_should_not_overwrite_recent_app_request(self):
        """状态面板聚合接口不应把最近 APP 请求覆盖成面板自己的内部读取。"""
        with mock.patch.object(server_v2, "_now_ms", return_value=2000):
            server_v2._record_runtime_http_request("/v2/account/full", ("10.0.0.9", 41234))

        payload = server_v2.internal_runtime_panel()

        self.assertEqual("/v2/account/full", payload["runtime"]["httpLastRequestPath"])
        self.assertEqual("10.0.0.9:41234", payload["runtime"]["lastClientAddress"])

    def test_internal_runtime_panel_should_not_trigger_lightweight_mt5_identity_verification(self):
        """状态面板聚合接口不应每次刷新都触发 MT5 轻量身份校验。"""
        session_status = {
            "state": "activated",
            "activeAccount": {"login": "7400048", "server": "ICMarketsSC-MT5-6"},
            "savedAccounts": [],
            "savedAccountCount": 0,
        }
        with mock.patch.object(
            server_v2.session_manager,
            "build_status_payload",
            return_value=session_status,
        ), mock.patch.object(
            server_v2,
            "_read_lightweight_current_mt5_account_identity",
            side_effect=AssertionError("runtime panel should not verify MT5 identity"),
        ) as verify_mock:
            payload = server_v2.internal_runtime_panel()

        verify_mock.assert_not_called()
        self.assertEqual("activated", payload["session"]["state"])
        self.assertEqual("7400048", payload["session"]["activeAccount"]["login"])

    def test_internal_runtime_status_should_track_recent_app_interactions(self):
        """运行时状态应收口 stream、session、trade 的最近交互事实。"""
        with mock.patch.object(server_v2, "_now_ms", side_effect=[1000, 1100, 1200, 1300, 1400]):
            server_v2._record_runtime_stream_connected("app://device-a")
            server_v2._record_runtime_http_request("/v2/account/snapshot")
            server_v2._record_runtime_session_action("login", "ok")
            server_v2._record_runtime_trade_action("submit", "ACCEPTED")
            server_v2._record_runtime_stream_disconnected("app://device-a")

        payload = server_v2.internal_runtime_status()

        self.assertEqual(0, payload["streamClientsActive"])
        self.assertEqual(1000, payload["streamLastConnectedAt"])
        self.assertEqual(1100, payload["httpLastRequestAt"])
        self.assertEqual("/v2/account/snapshot", payload["httpLastRequestPath"])
        self.assertEqual("login", payload["sessionLastAction"])
        self.assertEqual(1200, payload["sessionLastRequestAt"])
        self.assertEqual("ok", payload["sessionLastResult"])
        self.assertEqual("submit", payload["tradeLastAction"])
        self.assertEqual(1300, payload["tradeLastRequestAt"])
        self.assertEqual("ACCEPTED", payload["tradeLastResult"])
        self.assertEqual("app://device-a", payload["lastClientAddress"])
        self.assertEqual(1400, payload["streamLastDisconnectedAt"])

    def test_account_snapshot_should_update_runtime_http_request_path(self):
        """常用账户接口也应更新最近一次客户端普通请求路径。"""
        light_snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "historyRevision": "history-1",
            },
            "positions": [],
            "pendingOrders": [],
            "overviewMetrics": [],
            "curveIndicators": [],
            "statsMetrics": [],
        }
        with mock.patch.object(
            server_v2.session_manager,
            "build_status_payload",
            return_value={
                "state": "activated",
                "activeAccount": {"login": "7400048", "server": "ICMarketsSC-MT5-6"},
                "savedAccounts": [],
                "savedAccountCount": 0,
            },
        ), mock.patch.object(
            server_v2, "_build_account_light_snapshot_with_cache", return_value=light_snapshot
        ):
            fake_request = types.SimpleNamespace(client=("10.8.0.5", 50123))
            server_v2.v2_account_snapshot(request=fake_request)

        payload = server_v2.internal_runtime_status()
        self.assertEqual("/v2/account/snapshot", payload["httpLastRequestPath"])
        self.assertGreaterEqual(int(payload["httpLastRequestAt"] or 0), 0)
        self.assertEqual("10.8.0.5:50123", payload["lastClientAddress"])

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

    def test_current_mt5_credentials_should_not_deadlock_when_state_lock_already_held(self):
        """账户主链持有 state_lock 时，再读取运行时凭据也不应自锁死。"""
        original_runtime = dict(getattr(server_v2, "session_runtime_credentials", {}))
        completed = threading.Event()
        result = {}

        def _worker():
            try:
                with server_v2.state_lock:
                    result["creds"] = server_v2._current_mt5_credentials()
            finally:
                completed.set()

        try:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(
                {
                    "mode": "remote_active",
                    "login": 12345678,
                    "password": "runtime-secret",
                    "server": "ICMarketsSC-MT5-6",
                }
            )
            thread = threading.Thread(target=_worker, daemon=True)
            thread.start()
            self.assertTrue(
                completed.wait(0.3),
                "state_lock deadlocked while reading runtime MT5 credentials",
            )
        finally:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(original_runtime)

        self.assertEqual(12345678, result["creds"]["login"])
        self.assertEqual("runtime-secret", result["creds"]["password"])
        self.assertEqual("ICMarketsSC-MT5-6", result["creds"]["server"])

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

    def test_build_session_manager_should_restore_active_session_from_saved_profile_on_restart(self):
        """服务端重启后若 active_session 对应的已保存账号仍在，应恢复远程会话而不是直接清空。"""
        active_profile = {
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
            with mock.patch.object(server_v2.session_store, "load_active_session", side_effect=[active_profile, active_profile]), \
                    mock.patch.object(server_v2, "_restore_runtime_session_from_active_session", return_value=True) as restore_mock, \
                    mock.patch.object(server_v2.session_store, "clear_active_session") as clear_mock:
                manager = server_v2._build_session_manager()
                payload = manager.build_status_payload()
        finally:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(original_runtime)

        restore_mock.assert_called_once_with(active_profile)
        clear_mock.assert_not_called()
        self.assertEqual("activated", payload["state"])
        self.assertEqual("12345678", payload["activeAccount"]["login"])

    def test_build_session_manager_should_clear_stale_active_session_when_restore_unavailable(self):
        """服务端重启后若无法恢复 active_session，对外必须回到 logged_out。"""
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
                    mock.patch.object(server_v2, "_restore_runtime_session_from_active_session", return_value=False) as restore_mock, \
                    mock.patch.object(server_v2.session_store, "clear_active_session") as clear_mock:
                manager = server_v2._build_session_manager()
                payload = manager.build_status_payload()
        finally:
            server_v2.session_runtime_credentials.clear()
            server_v2.session_runtime_credentials.update(original_runtime)

        restore_mock.assert_called_once_with(stale_active)
        clear_mock.assert_called_once_with()
        self.assertEqual("logged_out", payload["state"])
        self.assertIsNone(payload["activeAccount"])

    def test_build_mt5_terminal_stop_command_should_scope_to_exact_executable_path(self):
        """MT5 终端停止命令应按精确 exe 路径匹配，不能误伤其他同名进程。"""
        command = server_v2._build_mt5_terminal_stop_command(
            r"C:\Program Files\MetaTrader 5\terminal64.exe",
            wait_timeout_ms=15000,
        )

        self.assertIn("Get-CimInstance Win32_Process", command)
        self.assertIn("$_.ExecutablePath", command)
        self.assertIn("terminal64.exe", command)
        self.assertIn("Stop-Process -Id", command)
        self.assertNotIn("Get-Process -Name", command)

    def test_build_mt5_terminal_start_command_should_scope_to_exact_executable_path_and_wait_for_process(self):
        """MT5 终端启动命令应按精确 exe 路径启动，并等待目标进程真正出现。"""
        command = server_v2._build_mt5_terminal_start_command(
            r"C:\Program Files\MetaTrader 5\terminal64.exe",
            wait_timeout_ms=15000,
        )

        self.assertIn("Start-Process -FilePath", command)
        self.assertIn("terminal64.exe", command)
        self.assertIn("$_.ExecutablePath", command)
        self.assertIn("WorkingDirectory", command)
        self.assertIn("process did not appear after start", command)

    def test_build_mt5_gui_window_detection_command_should_not_depend_on_main_window_handle(self):
        """MT5 进程检测不能继续依赖 MainWindowHandle，否则 SYSTEM 上下文会误判手动启动实例。"""
        command = server_v2._build_mt5_gui_window_detection_command(r"C:\Program Files\MetaTrader 5\terminal64.exe")

        self.assertIn("Get-CimInstance Win32_Process", command)
        self.assertIn("SessionId", command)
        self.assertIn("sameSessionCount", command)
        self.assertIn("exactPathCount", command)
        self.assertNotIn("MainWindowHandle", command)

    def test_build_mt5_terminal_window_probe_command_should_include_window_and_interactive_session_checks(self):
        """直登窗口探测应显式检查 MainWindowHandle 和交互会话。"""
        command = server_v2._build_mt5_terminal_window_probe_command(r"C:\Program Files\MetaTrader 5\terminal64.exe")

        self.assertIn("MainWindowHandle", command)
        self.assertIn("interactiveSessionIds", command)
        self.assertIn("interactiveVisibleWindowCount", command)
        self.assertIn("Get-Process explorer", command)

    def test_resolve_visible_mt5_terminal_path_should_require_interactive_visible_window(self):
        """直登只应接受进入交互会话且已有可见窗口的终端。"""
        payload = {
            "items": [
                {
                    "processId": 1234,
                    "sessionId": 3,
                    "interactiveSession": True,
                    "hasVisibleWindow": True,
                    "executablePath": r"C:\MT5\terminal64.exe",
                    "normalizedExecutablePath": "c:/mt5/terminal64.exe",
                    "exactPath": True,
                },
                {
                    "processId": 5678,
                    "sessionId": 0,
                    "interactiveSession": False,
                    "hasVisibleWindow": False,
                    "executablePath": r"C:\MT5\terminal64.exe",
                    "normalizedExecutablePath": "c:/mt5/terminal64.exe",
                    "exactPath": True,
                },
            ]
        }

        resolved = server_v2._resolve_visible_mt5_terminal_path(payload, preferred_path=r"C:\MT5\terminal64.exe")

        self.assertEqual(r"C:\MT5\terminal64.exe", resolved)

    def test_detect_mt5_gui_window_should_accept_same_session_terminal_process_without_main_window_handle(self):
        """同会话 MT5 终端即便没有窗口句柄，也应被认作可附着实例。"""
        completed = subprocess.CompletedProcess(
            args=["powershell", "-Command", "detect"],
            returncode=0,
            stdout=json.dumps(
                {
                    "processCount": 1,
                    "sameSessionCount": 1,
                    "exactPathCount": 1,
                    "items": [
                        {
                            "processId": 1234,
                            "sessionId": 1,
                            "sameSession": True,
                            "executablePath": r"C:\MT5\terminal64.exe",
                            "normalizedExecutablePath": "c:/mt5/terminal64.exe",
                            "exactPath": True,
                        }
                    ],
                }
            ).encode("utf-8"),
            stderr=b"",
        )
        with mock.patch("bridge.mt5_gateway.server_v2.subprocess.run", return_value=completed):
            detected = server_v2._detect_mt5_gui_window(r"C:\MT5\terminal64.exe")

        self.assertTrue(detected)

    def test_detect_mt5_gui_window_should_ignore_terminal_process_from_other_session(self):
        """别的 Windows 会话里的 MT5 终端不能再被误判成当前网关可附着实例。"""
        completed = subprocess.CompletedProcess(
            args=["powershell", "-Command", "detect"],
            returncode=0,
            stdout=json.dumps(
                {
                    "processCount": 1,
                    "sameSessionCount": 0,
                    "exactPathCount": 0,
                    "items": [
                        {
                            "processId": 1234,
                            "sessionId": 1,
                            "sameSession": False,
                            "executablePath": r"C:\MT5\terminal64.exe",
                            "normalizedExecutablePath": "c:/mt5/terminal64.exe",
                            "exactPath": True,
                        }
                    ],
                }
            ).encode("utf-8"),
            stderr=b"",
        )
        with mock.patch("bridge.mt5_gateway.server_v2.subprocess.run", return_value=completed):
            detected = server_v2._detect_mt5_gui_window(r"C:\MT5\terminal64.exe")

        self.assertFalse(detected)

    def test_attach_current_mt5_gui_terminal_should_initialize_with_resolved_same_session_path(self):
        """附着链应按已解析到的同会话终端路径初始化，而不是再走 initialize(None)。"""
        payload = {
            "processCount": 1,
            "sameSessionCount": 1,
            "exactPathCount": 1,
            "items": [
                {
                    "processId": 1234,
                    "sessionId": 0,
                    "sameSession": True,
                    "executablePath": r"C:\MT5\terminal64.exe",
                    "normalizedExecutablePath": "c:/mt5/terminal64.exe",
                    "exactPath": True,
                }
            ],
        }
        with mock.patch.object(server_v2, "PATH", r"C:\MT5\terminal64.exe"), \
                mock.patch.object(server_v2, "_inspect_mt5_gui_terminals", return_value=payload), \
                mock.patch.object(server_v2, "_shutdown_mt5") as shutdown_mock, \
                mock.patch.object(server_v2, "_mt5_initialize", return_value=(True, "")) as initialize_mock:
            attached = server_v2._attach_current_mt5_gui_terminal(15000)

        self.assertTrue(attached["ok"])
        shutdown_mock.assert_called_once_with()
        initialize_mock.assert_called_once_with(r"C:\MT5\terminal64.exe", timeout_ms=5000)

    def test_attach_current_mt5_gui_terminal_should_recycle_terminal_and_retry_initialize(self):
        """附着失败时，应接管目标终端并在同一条正式主链里重试初始化。"""
        payload = {
            "processCount": 1,
            "sameSessionCount": 1,
            "exactPathCount": 1,
            "items": [
                {
                    "processId": 1234,
                    "sessionId": 0,
                    "sameSession": True,
                    "executablePath": r"C:\MT5\terminal64.exe",
                    "normalizedExecutablePath": "c:/mt5/terminal64.exe",
                    "exactPath": True,
                }
            ],
        }
        with mock.patch.object(server_v2, "PATH", r"C:\MT5\terminal64.exe"), \
                mock.patch.object(server_v2, "_inspect_mt5_gui_terminals", return_value=payload), \
                mock.patch.object(server_v2, "_shutdown_mt5") as shutdown_mock, \
                mock.patch.object(server_v2, "_mt5_initialize", side_effect=[(False, "IPC timeout"), (True, "")]) as initialize_mock, \
                mock.patch.object(server_v2, "_recycle_mt5_terminal_for_attach", return_value={"remainingBudgetMs": 8000}) as recycle_mock:
            attached = server_v2._attach_current_mt5_gui_terminal(15000)

        self.assertTrue(attached["ok"])
        self.assertEqual("recycled_terminal_then_initialize", attached["detail"]["attachMode"])
        self.assertEqual(2, shutdown_mock.call_count)
        recycle_mock.assert_called_once()
        self.assertEqual(2, initialize_mock.call_count)

    def test_attach_current_mt5_gui_terminal_should_poll_initialize_after_recycle_until_ready(self):
        """重启终端后不能只尝试一次附着，应在剩余预算内短轮询直到终端真正可附着。"""
        payload = {
            "processCount": 1,
            "sameSessionCount": 1,
            "exactPathCount": 1,
            "items": [
                {
                    "processId": 1234,
                    "sessionId": 0,
                    "sameSession": True,
                    "executablePath": r"C:\MT5\terminal64.exe",
                    "normalizedExecutablePath": "c:/mt5/terminal64.exe",
                    "exactPath": True,
                }
            ],
        }
        with mock.patch.object(server_v2, "PATH", r"C:\MT5\terminal64.exe"), \
                mock.patch.object(server_v2, "_inspect_mt5_gui_terminals", return_value=payload), \
                mock.patch.object(server_v2, "_shutdown_mt5") as shutdown_mock, \
                mock.patch.object(
                    server_v2,
                    "_mt5_initialize",
                    side_effect=[(False, "IPC timeout"), (False, "IPC timeout"), (True, "")],
                ) as initialize_mock, \
                mock.patch.object(server_v2, "_recycle_mt5_terminal_for_attach", return_value={"remainingBudgetMs": 9000}) as recycle_mock, \
                mock.patch("bridge.mt5_gateway.server_v2.time.monotonic", side_effect=[0.0, 0.0, 0.5, 2.0]), \
                mock.patch("bridge.mt5_gateway.server_v2.time.sleep") as sleep_mock:
            attached = server_v2._attach_current_mt5_gui_terminal(15000)

        self.assertTrue(attached["ok"])
        self.assertEqual("recycled_terminal_then_initialize", attached["detail"]["attachMode"])
        self.assertEqual(2, int(attached["detail"]["retryInitializeAttemptCount"]))
        self.assertEqual(3, initialize_mock.call_count)
        self.assertEqual(3, shutdown_mock.call_count)
        recycle_mock.assert_called_once()
        sleep_mock.assert_called_once()

    def test_resolve_direct_login_terminal_path_should_use_discovered_candidate_when_mt5_path_missing(self):
        """未显式配置 MT5_PATH 时，直登链应使用已发现的终端候选路径。"""
        with mock.patch.object(server_v2, "PATH", None), \
                mock.patch.object(server_v2, "MT5_TERMINAL_CANDIDATES", [r"C:\Discovered\terminal64.exe"]):
            resolved_path, source = server_v2._resolve_direct_login_terminal_path()

        self.assertEqual(r"C:\Discovered\terminal64.exe", resolved_path)
        self.assertEqual("discovered_candidate", source)

    def test_login_mt5_direct_with_input_credentials_should_fail_when_terminal_window_is_not_visible(self):
        """直登在未检测到可见交互窗口时应提前失败，而不是继续盲目 initialize。"""
        with mock.patch.object(server_v2, "mt5", mock.Mock()), \
                mock.patch.object(server_v2, "_shutdown_mt5"), \
                mock.patch.object(server_v2, "_resolve_direct_login_terminal_path", return_value=(r"C:\MT5\terminal64.exe", "configured_path")), \
                mock.patch.object(server_v2, "_restart_mt5_terminal_for_direct_login", return_value={"terminalPath": r"C:\MT5\terminal64.exe", "matchedCount": 1, "remainingCount": 0}), \
                mock.patch.object(server_v2, "_start_mt5_terminal_for_direct_login", return_value={"terminalPath": r"C:\MT5\terminal64.exe", "matchedCount": 1}), \
                mock.patch.object(
                    server_v2,
                    "_resolve_mt5_terminal_window_state",
                    return_value={
                        "ready": False,
                        "resolvedPath": None,
                        "payload": {
                            "currentSessionId": 0,
                            "interactiveSessionIds": [3],
                            "processCount": 1,
                            "sameSessionCount": 1,
                            "exactPathCount": 1,
                            "interactiveSessionCount": 0,
                            "visibleWindowCount": 0,
                            "interactiveVisibleWindowCount": 0,
                            "items": [],
                        },
                    },
                ), \
                mock.patch.object(server_v2, "_login_mt5_in_isolated_process") as direct_login_mock:
            with self.assertRaises(RuntimeError) as error_ctx:
                server_v2._login_mt5_direct_with_input_credentials(
                    login_value=12345678,
                    password_value="secret",
                    server_value="ICMarketsSC-MT5-6",
                    request_id="req-window-missing-001",
                    action="login",
                )

        self.assertIn("no visible interactive window", str(error_ctx.exception))
        direct_login_mock.assert_not_called()

    def test_session_gateway_adapter_should_delegate_to_direct_login_flow(self):
        """登录适配器应改为委托独立 MT5 实例直登主链。"""
        fake_flow = mock.Mock(
            return_value={
                "login": "12345678",
                "server": "New-Server",
            }
        )
        with mock.patch.object(server_v2, "_login_mt5_direct_with_input_credentials", fake_flow), \
                mock.patch.object(server_v2, "_set_runtime_session_credentials") as set_runtime_mock:
            payload = server_v2._SessionGatewayAdapter().login_mt5(
                login="12345678",
                password="secret",
                server="New-Server",
                request_id="req-switch-1",
            )

        fake_flow.assert_called_once_with(
            login_value=12345678,
            password_value="secret",
            server_value="New-Server",
            request_id="req-switch-1",
            action="login",
        )
        set_runtime_mock.assert_called_once_with("12345678", "secret", "New-Server")
        self.assertEqual("direct_identity_confirmed", payload["stage"])
        self.assertEqual("12345678", payload["login"])
        self.assertEqual("acct_12345678_new-server", payload["finalAccount"]["profileId"])
        self.assertEqual("****5678", payload["finalAccount"]["loginMasked"])

    def test_session_gateway_adapter_should_raise_structured_direct_login_error_on_failure(self):
        """独立直登主链失败时，适配器应抛出可透传结构化结果的错误。"""
        server_v2.session_diagnostic_store.clear()
        server_v2.session_diagnostic_store.append(
            request_id="req-switch-failed",
            action="login",
            stage="direct_login_failed",
            status="failed",
            message="MT5 账号登录失败: Authorization failed",
            server_time=123456,
            error_code="SESSION_DIRECT_LOGIN_FAILED",
            detail={},
        )
        with mock.patch.object(server_v2, "_login_mt5_direct_with_input_credentials", side_effect=RuntimeError("MT5 账号登录失败: Authorization failed")):
            with self.assertRaises(server_v2.Mt5AccountSwitchFlowError) as error_ctx:
                server_v2._SessionGatewayAdapter().login_mt5(
                    login="12345678",
                    password="secret",
                    server="New-Server",
                    request_id="req-switch-failed",
                )

        self.assertEqual("direct_login_failed", error_ctx.exception.result["stage"])
        self.assertIn("Authorization failed", error_ctx.exception.result["message"])

    def test_build_failed_direct_session_result_should_recover_expected_and_actual_accounts(self):
        """直登失败回执应尽量从诊断 detail 里恢复 expected/actual 账号摘要。"""
        server_v2.session_diagnostic_store.clear()
        server_v2.session_diagnostic_store.append(
            request_id="req-direct-identity-mismatch",
            action="login",
            stage="direct_identity_failed",
            status="failed",
            message="MT5 当前账号与输入账号不一致",
            server_time=123456,
            error_code="SESSION_DIRECT_IDENTITY_MISMATCH",
            detail={
                "expectedLogin": "12345678",
                "expectedServer": "Input-Server",
                "actualLogin": "87654321",
                "actualServer": "Actual-Server",
            },
        )

        payload = server_v2._build_failed_direct_session_result(
            action="login",
            request_id="req-direct-identity-mismatch",
            error=RuntimeError("MT5 canonical account identity mismatch after direct login"),
        )

        self.assertEqual("12345678", payload["baselineAccount"]["login"])
        self.assertEqual("87654321", payload["finalAccount"]["login"])
        self.assertEqual("87654321", payload["lastObservedAccount"]["login"])
        self.assertEqual("acct_87654321_actual-server", payload["lastObservedAccount"]["profileId"])

    def test_session_gateway_adapter_should_delegate_switch_to_direct_login_flow(self):
        """已保存账号切换也应改为独立 MT5 实例直登主链。"""
        fake_flow = mock.Mock(
            return_value={
                "login": "87654321",
                "server": "Switch-Server",
            }
        )
        with mock.patch.object(server_v2, "_login_mt5_direct_with_input_credentials", fake_flow), \
                mock.patch.object(server_v2, "_set_runtime_session_credentials") as set_runtime_mock:
            payload = server_v2._SessionGatewayAdapter().switch_mt5_account(
                login="87654321",
                password="secret2",
                server="Switch-Server",
                request_id="req-switch-2",
            )

        fake_flow.assert_called_once_with(
            login_value=87654321,
            password_value="secret2",
            server_value="Switch-Server",
            request_id="req-switch-2",
            action="switch",
        )
        set_runtime_mock.assert_called_once_with("87654321", "secret2", "Switch-Server")
        self.assertEqual("direct_identity_confirmed", payload["stage"])
        self.assertEqual("87654321", payload["login"])
        self.assertEqual("acct_87654321_switch-server", payload["finalAccount"]["profileId"])
        self.assertEqual("****4321", payload["lastObservedAccount"]["loginMasked"])

    def test_switch_mt5_account_via_gui_flow_should_append_real_stage_to_diagnostic(self):
        """切号主链应把真实阶段直接写入诊断时间线。"""
        result = {
            "ok": False,
            "stage": "attach_failed",
            "message": "MT5 主窗口已存在，但附着/初始化失败",
            "elapsedMs": 9000,
            "baselineAccount": None,
            "finalAccount": None,
            "loginError": "",
            "lastObservedAccount": None,
        }
        fake_controller = mock.Mock()
        fake_controller.switch_account.return_value = result
        with mock.patch.object(server_v2, "_build_mt5_account_switch_controller", return_value=fake_controller), \
                mock.patch.object(server_v2, "_append_session_diagnostic_entry") as diagnostic_mock:
            payload = server_v2._switch_mt5_account_via_gui_flow(
                login="12345678",
                password="secret",
                server="New-Server",
                request_id="req-stage-1",
                action="login",
            )

        self.assertEqual(result, payload)
        self.assertEqual("attach_failed", diagnostic_mock.call_args.kwargs["stage"])
        self.assertEqual("failed", diagnostic_mock.call_args.kwargs["status"])

    def test_switch_mt5_account_via_gui_flow_should_stream_intermediate_stage_to_diagnostic(self):
        """切号主链运行中应实时写入内部阶段，不能等最终返回后才补诊断。"""
        server_v2.session_diagnostic_store.clear()
        with mock.patch.object(server_v2, "_detect_mt5_gui_window", return_value=True), \
                mock.patch.object(server_v2, "_attach_current_mt5_gui_terminal", return_value=True), \
                mock.patch.object(
                    server_v2,
                    "_read_current_mt5_gui_account",
                    side_effect=[
                        {"login": "11111111", "server": "Old-Server"},
                        {"login": "12345678", "server": "ICMarketsSC-MT5-6"},
                    ],
                ), \
                mock.patch.object(server_v2, "_call_mt5_account_switch_api", return_value={"ok": True, "error": ""}), \
                mock.patch.object(server_v2, "_now_monotonic_ms", side_effect=[0, 0, 1000, 1000, 2000, 2000, 3000] + [4000] * 20):
            payload = server_v2._switch_mt5_account_via_gui_flow(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                request_id="req-stage-stream-001",
                action="login",
            )

        self.assertTrue(payload["ok"])
        timeline = server_v2.session_diagnostic_store.lookup("req-stage-stream-001")
        self.assertTrue(any(item["stage"] == "attach_attempt_start" for item in timeline))
        self.assertTrue(any(item["stage"] == "switch_call_start" for item in timeline))
        self.assertTrue(any(item["stage"] == "final_account_poll_start" for item in timeline))
        self.assertEqual("switch_succeeded", timeline[-1]["stage"])

    def test_mt5_gui_switch_controller_should_cap_total_budget_within_client_contract(self):
        """GUI 切号链总预算必须小于客户端读超时契约，避免手机先超时。"""
        controller = server_v2._build_mt5_account_switch_controller()

        self.assertEqual(120000, controller.total_timeout_ms)
        self.assertLess(controller.total_timeout_ms, server_v2.SESSION_CLIENT_READ_TIMEOUT_MS)

    def test_login_mt5_in_isolated_process_should_fail_when_subprocess_times_out(self):
        """隔离进程直登超时时，应返回明确超时错误。"""
        timeout_error = subprocess.TimeoutExpired(cmd=["python", "direct-login"], timeout=55)
        with mock.patch.object(server_v2, "MT5_INIT_TIMEOUT_MS", 50000), \
                mock.patch("bridge.mt5_gateway.server_v2.subprocess.run", side_effect=timeout_error):
            with self.assertRaises(RuntimeError) as error_ctx:
                server_v2._login_mt5_in_isolated_process(
                    login_value=12345678,
                    password_value="secret",
                    server_value="ICMarketsSC-MT5-6",
                    path_value=r"C:\MT5\terminal64.exe",
                )

        self.assertIn("timed out", str(error_ctx.exception))

    def test_login_mt5_in_isolated_process_should_fail_when_subprocess_returns_error(self):
        """隔离进程直登返回错误时，应把错误消息透传出来。"""
        completed = subprocess.CompletedProcess(
            args=["python", "direct-login"],
            returncode=1,
            stdout=json.dumps({"ok": False, "error": "MT5 initialize failed: (-10005, 'IPC timeout')"}).encode("utf-8"),
            stderr=b"",
        )
        with mock.patch("bridge.mt5_gateway.server_v2.subprocess.run", return_value=completed):
            with self.assertRaises(RuntimeError) as error_ctx:
                server_v2._login_mt5_in_isolated_process(
                    login_value=12345678,
                    password_value="secret",
                    server_value="ICMarketsSC-MT5-6",
                    path_value=r"C:\MT5\terminal64.exe",
                )

        self.assertIn("IPC timeout", str(error_ctx.exception))

    def test_mt5_direct_login_should_initialize_then_login_with_input_credentials_and_return_canonical_identity(self):
        """隔离直登脚本应按官方顺序 initialize -> login -> account_info -> shutdown。"""
        fake_mt5 = mock.Mock()
        fake_mt5.initialize.return_value = True
        fake_mt5.login.return_value = True
        fake_mt5.account_info.return_value = types.SimpleNamespace(login=12345678, server="ICMarketsSC-MT5-6")
        fake_mt5.last_error.return_value = (0, "ok")
        payload = {
            "login": 12345678,
            "password": "secret",
            "server": "ICMarketsSC-MT5-6",
            "path": r"C:\MT5\terminal64.exe",
            "timeoutMs": 50000,
        }
        stdin_buffer = io.BytesIO(json.dumps(payload).encode("utf-8"))
        stdout_buffer = io.StringIO()
        fake_stdin = types.SimpleNamespace(buffer=stdin_buffer)

        from bridge.mt5_gateway import mt5_direct_login

        with mock.patch.dict(sys.modules, {"MetaTrader5": fake_mt5}), \
                mock.patch.object(sys, "stdin", fake_stdin), \
                mock.patch.object(sys, "stdout", stdout_buffer):
            exit_code = mt5_direct_login.main()

        self.assertEqual(0, exit_code)
        fake_mt5.initialize.assert_called_once_with(
            timeout=50000,
            path=r"C:\MT5\terminal64.exe",
        )
        fake_mt5.login.assert_called_once()
        login_call = fake_mt5.login.call_args
        self.assertEqual((12345678,), login_call.args)
        self.assertEqual("secret", login_call.kwargs.get("password"))
        self.assertEqual("ICMarketsSC-MT5-6", login_call.kwargs.get("server"))
        self.assertLessEqual(int(login_call.kwargs.get("timeout") or 0), 50000)
        self.assertGreater(int(login_call.kwargs.get("timeout") or 0), 0)
        fake_mt5.account_info.assert_called_once_with()
        fake_mt5.shutdown.assert_called_once_with()
        result = json.loads(stdout_buffer.getvalue())
        self.assertEqual(True, result["ok"])
        self.assertEqual("12345678", result["login"])
        self.assertEqual("ICMarketsSC-MT5-6", result["server"])

    def test_mt5_direct_login_should_retry_initialize_when_ipc_timeout_then_succeed(self):
        """直登脚本遇到冷启动 IPC timeout 时应等待后重试 initialize。"""
        fake_mt5 = mock.Mock()
        fake_mt5.initialize.side_effect = [False, True]
        fake_mt5.login.return_value = True
        fake_mt5.account_info.return_value = types.SimpleNamespace(login=12345678, server="ICMarketsSC-MT5-6")
        fake_mt5.last_error.return_value = (-10005, "IPC timeout")
        payload = {
            "login": 12345678,
            "password": "secret",
            "server": "ICMarketsSC-MT5-6",
            "path": r"C:\MT5\terminal64.exe",
            "timeoutMs": 50000,
        }
        stdin_buffer = io.BytesIO(json.dumps(payload).encode("utf-8"))
        stdout_buffer = io.StringIO()
        fake_stdin = types.SimpleNamespace(buffer=stdin_buffer)

        from bridge.mt5_gateway import mt5_direct_login

        with mock.patch.dict(sys.modules, {"MetaTrader5": fake_mt5}), \
                mock.patch.object(sys, "stdin", fake_stdin), \
                mock.patch.object(sys, "stdout", stdout_buffer), \
                mock.patch("bridge.mt5_gateway.mt5_direct_login.time.sleep") as sleep_mock:
            exit_code = mt5_direct_login.main()

        self.assertEqual(0, exit_code)
        self.assertEqual(2, fake_mt5.initialize.call_count)
        sleep_mock.assert_called_once_with(2.0)
        self.assertGreaterEqual(fake_mt5.shutdown.call_count, 2)
        result = json.loads(stdout_buffer.getvalue())
        self.assertEqual(True, result["ok"])
        self.assertTrue(any(item["stage"] == "direct_initialize_retry_wait" for item in result["trace"]))

    def test_mt5_direct_login_should_fail_when_login_failed(self):
        """隔离直登脚本若 login 失败，应直接返回 MT5 的真实错误。"""
        fake_mt5 = mock.Mock()
        fake_mt5.initialize.return_value = True
        fake_mt5.login.return_value = False
        fake_mt5.last_error.return_value = (-6, "Authorization failed")
        payload = {
            "login": 12345678,
            "password": "secret",
            "server": "ICMarketsSC-MT5-6",
            "path": r"C:\MT5\terminal64.exe",
            "timeoutMs": 50000,
        }
        stdin_buffer = io.BytesIO(json.dumps(payload).encode("utf-8"))
        stdout_buffer = io.StringIO()
        fake_stdin = types.SimpleNamespace(buffer=stdin_buffer)

        from bridge.mt5_gateway import mt5_direct_login

        with mock.patch.dict(sys.modules, {"MetaTrader5": fake_mt5}), \
                mock.patch.object(sys, "stdin", fake_stdin), \
                mock.patch.object(sys, "stdout", stdout_buffer):
            exit_code = mt5_direct_login.main()

        self.assertEqual(1, exit_code)
        result = json.loads(stdout_buffer.getvalue())
        self.assertEqual(False, result["ok"])
        self.assertIn("Authorization failed", result["error"])

    def test_mt5_direct_login_should_fail_when_canonical_identity_missing(self):
        """隔离直登脚本若拿不到 canonical identity，应直接失败。"""
        fake_mt5 = mock.Mock()
        fake_mt5.initialize.return_value = True
        fake_mt5.account_info.return_value = None
        fake_mt5.last_error.return_value = (-10005, "IPC timeout")
        payload = {
            "login": 12345678,
            "password": "secret",
            "server": "ICMarketsSC-MT5-6",
            "path": r"C:\MT5\terminal64.exe",
            "timeoutMs": 50000,
        }
        stdin_buffer = io.BytesIO(json.dumps(payload).encode("utf-8"))
        stdout_buffer = io.StringIO()
        fake_stdin = types.SimpleNamespace(buffer=stdin_buffer)

        from bridge.mt5_gateway import mt5_direct_login

        with mock.patch.dict(sys.modules, {"MetaTrader5": fake_mt5}), \
                mock.patch.object(sys, "stdin", fake_stdin), \
                mock.patch.object(sys, "stdout", stdout_buffer):
            exit_code = mt5_direct_login.main()

        self.assertEqual(1, exit_code)
        result = json.loads(stdout_buffer.getvalue())
        self.assertEqual(False, result["ok"])
        self.assertIn("canonical account identity", result["error"])

    def test_mt5_login_probe_should_support_legacy_initialize_plus_login_flow(self):
        """旧探针脚本仍保留原有合同，避免其它历史测试口径被破坏。"""
        fake_mt5 = mock.Mock()
        fake_mt5.initialize.side_effect = [
            TypeError("base init timeout unsupported"),
            True,
            TypeError("authenticated init unsupported"),
            True,
        ]
        fake_mt5.login.return_value = True
        fake_mt5.account_info.side_effect = [
            types.SimpleNamespace(login=87654321, server="Other-Server"),
            types.SimpleNamespace(login=12345678, server="ICMarketsSC-MT5-6"),
        ]
        fake_mt5.last_error.return_value = (0, "ok")
        payload = {
            "login": 12345678,
            "password": "secret",
            "server": "ICMarketsSC-MT5-6",
            "path": r"C:\MT5\terminal64.exe",
            "timeoutMs": 50000,
        }
        stdin_buffer = io.BytesIO(json.dumps(payload).encode("utf-8"))
        stdout_buffer = io.StringIO()
        fake_stdin = types.SimpleNamespace(buffer=stdin_buffer)

        with mock.patch.dict(sys.modules, {"MetaTrader5": fake_mt5}), \
                mock.patch.object(sys, "stdin", fake_stdin), \
                mock.patch.object(sys, "stdout", stdout_buffer):
            exit_code = mt5_login_probe.main()

        self.assertEqual(0, exit_code)
        self.assertEqual(4, fake_mt5.initialize.call_count)
        self.assertEqual(1, fake_mt5.login.call_count)
        self.assertEqual(
            mock.call(
                timeout=50000,
                path=r"C:\MT5\terminal64.exe",
            ),
            fake_mt5.initialize.call_args_list[0],
        )
        self.assertEqual(
            mock.call(
                path=r"C:\MT5\terminal64.exe",
            ),
            fake_mt5.initialize.call_args_list[1],
        )
        self.assertEqual(
            mock.call(
                timeout=50000,
                login=12345678,
                password="secret",
                server="ICMarketsSC-MT5-6",
                path=r"C:\MT5\terminal64.exe",
            ),
            fake_mt5.initialize.call_args_list[2],
        )
        self.assertEqual(
            mock.call(
                timeout=50000,
                path=r"C:\MT5\terminal64.exe",
            ),
            fake_mt5.initialize.call_args_list[3],
        )
        self.assertEqual(
            mock.call(
                12345678,
                password="secret",
                server="ICMarketsSC-MT5-6",
                timeout=50000,
            ),
            fake_mt5.login.call_args,
        )
        result = json.loads(stdout_buffer.getvalue())
        self.assertEqual(True, result["ok"])
        self.assertEqual("12345678", result["login"])
        self.assertEqual("ICMarketsSC-MT5-6", result["server"])

    def test_mt5_login_probe_should_reuse_current_terminal_when_target_account_already_active(self):
        """若当前终端已经就是目标账号，探针应直接复用现有会话，不能再卡 95 秒重登录。"""
        fake_mt5 = mock.Mock()
        fake_mt5.initialize.return_value = True
        fake_mt5.account_info.return_value = types.SimpleNamespace(
            login=12345678,
            server="ICMarketsSC-MT5-6",
        )
        payload = {
            "login": 12345678,
            "password": "secret",
            "server": "ICMarketsSC-MT5-6",
            "path": r"C:\MT5\terminal64.exe",
            "timeoutMs": 90000,
        }
        stdin_buffer = io.BytesIO(json.dumps(payload).encode("utf-8"))
        stdout_buffer = io.StringIO()
        fake_stdin = types.SimpleNamespace(buffer=stdin_buffer)

        with mock.patch.dict(sys.modules, {"MetaTrader5": fake_mt5}), \
                mock.patch.object(sys, "stdin", fake_stdin), \
                mock.patch.object(sys, "stdout", stdout_buffer):
            exit_code = mt5_login_probe.main()

        self.assertEqual(0, exit_code)
        fake_mt5.initialize.assert_called_once_with(
            timeout=90000,
            path=r"C:\MT5\terminal64.exe",
        )
        fake_mt5.account_info.assert_called()
        fake_mt5.login.assert_not_called()
        result = json.loads(stdout_buffer.getvalue())
        self.assertEqual(True, result["ok"])
        self.assertEqual("12345678", result["login"])
        self.assertEqual("ICMarketsSC-MT5-6", result["server"])

    def test_authenticated_mt5_initialize_should_fail_when_sdk_cannot_accept_auth_signature(self):
        """带鉴权初始化若签名不支持，应直接失败，不能静默退回成未鉴权初始化。"""
        fake_mt5 = mock.Mock()
        fake_mt5.initialize.side_effect = [
            TypeError("timeout signature unsupported"),
            TypeError("authenticated init unsupported"),
        ]

        with mock.patch.object(server_v2, "mt5", fake_mt5):
            ok, message = server_v2._mt5_initialize(
                path_value=r"C:\MT5\terminal64.exe",
                login=12345678,
                password="secret",
                server_name="ICMarketsSC-MT5-6",
            )

        self.assertFalse(ok)
        self.assertIn("authenticated init", message)
        self.assertEqual(2, fake_mt5.initialize.call_count)

    def test_mt5_init_timeout_should_be_capped_to_client_contract(self):
        """环境变量把 MT5 登录预算调大时，也不能突破客户端等待契约。"""
        with mock.patch.dict(os.environ, {"MT5_INIT_TIMEOUT_MS": "90000"}, clear=False):
            timeout_ms = server_v2._read_bounded_env_int(
                "MT5_INIT_TIMEOUT_MS",
                default=50000,
                minimum=1000,
                maximum=50000,
            )

        self.assertEqual(50000, timeout_ms)

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

    def test_session_login_should_translate_gateway_failure_to_structured_502(self):
        """登录链路若失败，会话接口必须返回结构化 detail，不能再漏成裸 500。"""
        key_payload = server_v2.v2_session_public_key()
        envelope = self._build_login_envelope(
            key_payload,
            key_id=str(key_payload.get("keyId") or ""),
            client_time=int(time.time() * 1000),
        )
        with mock.patch.object(
            server_v2.session_manager,
            "login_new_account",
            side_effect=server_v2.Mt5AccountSwitchFlowError(
                {
                    "ok": False,
                    "stage": "switch_timeout_account_not_changed",
                    "message": "30s 内未切换到目标账号",
                    "elapsedMs": 30000,
                    "baselineAccount": {"login": "11111111", "server": "Old-Server"},
                    "finalAccount": None,
                    "loginError": "(-6, 'Authorization failed')",
                    "lastObservedAccount": {"login": "11111111", "server": "Old-Server"},
                }
            ),
        ):
            with self.assertRaises(server_v2.HTTPException) as error_ctx:
                server_v2.v2_session_login(envelope)

        self.assertEqual(502, error_ctx.exception.status_code)
        self.assertEqual("SESSION_LOGIN_FAILED", error_ctx.exception.detail["code"])
        self.assertEqual("switch_timeout_account_not_changed", error_ctx.exception.detail["stage"])
        self.assertEqual("(-6, 'Authorization failed')", error_ctx.exception.detail["loginError"])

    def test_session_login_should_return_switch_flow_fields(self):
        """登录接口成功回执应保留新的切号主链字段。"""
        key_payload = server_v2.v2_session_public_key()
        envelope = self._build_login_envelope(
            key_payload,
            key_id=str(key_payload.get("keyId") or ""),
            client_time=int(time.time() * 1000),
        )
        expected = {
            "ok": True,
            "state": "activated",
            "requestId": "req-login-1",
            "message": "切换前=11111111 / Old-Server；切换后=12345678 / New-Server；耗时=8000ms",
            "stage": "switch_succeeded",
            "elapsedMs": 8000,
            "baselineAccount": {"login": "11111111", "server": "Old-Server"},
            "finalAccount": {"login": "12345678", "server": "New-Server"},
            "loginError": "",
            "lastObservedAccount": {"login": "12345678", "server": "New-Server"},
        }
        with mock.patch.object(server_v2.session_manager, "login_new_account", return_value=expected):
            payload = server_v2.v2_session_login(envelope)

        self.assertEqual("switch_succeeded", payload["stage"])
        self.assertEqual(8000, payload["elapsedMs"])
        self.assertEqual("11111111", payload["baselineAccount"]["login"])
        self.assertEqual("12345678", payload["finalAccount"]["login"])

    def test_session_switch_should_forward_profile_id_and_request_id(self):
        """switch 接口应把账号标识和 requestId 传给会话管理器。"""
        expected = {"ok": True, "state": "activated", "requestId": "req-switch-1"}
        with mock.patch.object(server_v2.session_manager, "switch_saved_account", return_value=expected) as switch_mock:
            payload = server_v2.v2_session_switch(
                {"requestId": "req-switch-1", "accountProfileId": "acct_2"}
            )

        switch_mock.assert_called_once_with("acct_2", request_id="req-switch-1")
        self.assertEqual(expected, payload)

    def test_session_switch_should_translate_gateway_failure_to_structured_502(self):
        """切换链路若失败，会话接口也必须返回结构化 detail。"""
        with mock.patch.object(
            server_v2.session_manager,
            "switch_saved_account",
            side_effect=RuntimeError("saved profile encryptedPassword is invalid base64"),
        ):
            with self.assertRaises(server_v2.HTTPException) as error_ctx:
                server_v2.v2_session_switch(
                    {"requestId": "req-switch-1", "accountProfileId": "acct_2"}
                )

        self.assertEqual(502, error_ctx.exception.status_code)
        self.assertEqual("SESSION_SWITCH_FAILED", error_ctx.exception.detail["code"])
        self.assertIn("invalid base64", error_ctx.exception.detail["message"])

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

    def test_resolve_mt5_terminal_common_ini_path_should_match_origin_directory(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            install_dir = root / "MetaTrader 5"
            install_dir.mkdir(parents=True, exist_ok=True)
            terminal_path = install_dir / "terminal64.exe"
            terminal_path.write_text("", encoding="utf-8")

            appdata_dir = root / "Roaming"
            common_ini = appdata_dir / "MetaQuotes" / "Terminal" / "ABC123" / "config" / "common.ini"
            common_ini.parent.mkdir(parents=True, exist_ok=True)
            common_ini.write_text("[Experts]\nEnabled=0\nApi=0\n", encoding="utf-8")
            origin_path = common_ini.parent.parent / "origin.txt"
            origin_path.write_text(str(install_dir), encoding="utf-8")

            with mock.patch.dict(os.environ, {"APPDATA": str(appdata_dir)}, clear=False):
                resolved = server_v2._resolve_mt5_terminal_common_ini_path(str(terminal_path))

            self.assertEqual(common_ini, resolved)

    def test_ensure_mt5_terminal_auto_trading_config_should_enable_experts_and_api(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            install_dir = root / "MetaTrader 5"
            install_dir.mkdir(parents=True, exist_ok=True)
            terminal_path = install_dir / "terminal64.exe"
            terminal_path.write_text("", encoding="utf-8")

            appdata_dir = root / "Roaming"
            common_ini = appdata_dir / "MetaQuotes" / "Terminal" / "ABC123" / "config" / "common.ini"
            common_ini.parent.mkdir(parents=True, exist_ok=True)
            common_ini.write_text("[Experts]\nEnabled=0\nApi=0\nChart=0\n", encoding="utf-8")
            origin_path = common_ini.parent.parent / "origin.txt"
            origin_path.write_text(str(install_dir), encoding="utf-8")

            with mock.patch.dict(os.environ, {"APPDATA": str(appdata_dir)}, clear=False):
                result = server_v2._ensure_mt5_terminal_auto_trading_config(str(terminal_path))

            content = common_ini.read_text(encoding="utf-8")

            self.assertTrue(result["changed"])
            self.assertIn("Enabled=1", content)
            self.assertIn("Api=1", content)
            self.assertIn("Chart=0", content)


if __name__ == "__main__":
    unittest.main()

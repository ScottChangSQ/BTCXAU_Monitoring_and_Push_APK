"""v2 会话接口契约测试。"""

from __future__ import annotations

import sys
import types
import unittest
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


if __name__ == "__main__":
    unittest.main()

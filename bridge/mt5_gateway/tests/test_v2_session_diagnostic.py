"""v2 会话登录诊断契约测试。"""

import base64
import json
import os
import sys
import time
import types
import unittest
import uuid
from pathlib import Path
from unittest import mock


def _install_test_stubs():
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
                pass

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
        gzip_stub.GZipMiddleware = type("GZipMiddleware", (), {})
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


class V2SessionDiagnosticTests(unittest.TestCase):
    def setUp(self):
        server_v2.session_diagnostic_store.clear()

    def _build_login_envelope(self, key_payload: dict, *, key_id: str, client_time: int, nonce: str = "") -> dict:
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
            "requestId": "req-session-diag-001",
            "keyId": str(key_id),
            "algorithm": str(key_payload.get("algorithm") or ""),
            "encryptedKey": base64.b64encode(encrypted_key).decode("ascii"),
            "encryptedPayload": base64.b64encode(encrypted_payload).decode("ascii"),
            "iv": base64.b64encode(iv).decode("ascii"),
            "saveAccount": True,
        }

    def test_probe_failure_should_append_trace_items_to_session_diagnostic_store(self):
        completed = types.SimpleNamespace(
            stdout=json.dumps(
                {
                    "ok": False,
                    "error": "MetaTrader5 login failed after 95s",
                    "trace": [
                        {
                            "stage": "base_initialize_ok",
                            "status": "ok",
                            "message": "已建立基础终端连接",
                            "serverTime": 1776670000000,
                        },
                        {
                            "stage": "legacy_login_failed",
                            "status": "failed",
                            "message": "legacy login 在 95s 后失败",
                            "serverTime": 1776670095000,
                        },
                    ],
                },
                ensure_ascii=False,
            ).encode("utf-8"),
            stderr=b"",
        )

        with mock.patch("bridge.mt5_gateway.server_v2.subprocess.run", return_value=completed):
            with self.assertRaises(RuntimeError):
                server_v2._probe_mt5_authenticated_session(
                    login_value=12345678,
                    password_value="secret",
                    server_value="ICMarketsSC-MT5-6",
                    path_value=r"C:\MT5\terminal64.exe",
                    request_id="req-session-diag-001",
                    action="login",
                )

        payload = server_v2.v2_session_diagnostic_lookup(requestId="req-session-diag-001")
        self.assertEqual("req-session-diag-001", payload["requestId"])
        self.assertTrue(any(item["stage"] == "base_initialize_ok" for item in payload["items"]))
        self.assertTrue(any(item["stage"] == "legacy_login_failed" for item in payload["items"]))

    def test_probe_timeout_should_append_trace_items_from_trace_file_to_session_diagnostic_store(self):
        def _timeout_with_trace(*args, **kwargs):
            probe_payload = json.loads((kwargs.get("input") or b"{}").decode("utf-8"))
            trace_file = str(probe_payload.get("traceFile") or "").strip()
            self.assertTrue(trace_file)
            Path(trace_file).write_text(
                json.dumps(
                    {
                        "trace": [
                            {
                                "stage": "base_initialize_start",
                                "status": "pending",
                                "message": "开始建立基础终端连接",
                                "serverTime": 1776670000000,
                            },
                            {
                                "stage": "current_terminal_identity",
                                "status": "ok",
                                "message": "已读取当前终端账号身份",
                                "serverTime": 1776670001000,
                            },
                        ]
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            raise server_v2.subprocess.TimeoutExpired(cmd="mt5_login_probe.py", timeout=95)

        with mock.patch.object(server_v2.subprocess, "run", side_effect=_timeout_with_trace):
            with self.assertRaises(RuntimeError):
                server_v2._probe_mt5_authenticated_session(
                    login_value=12345678,
                    password_value="secret",
                    server_value="ICMarketsSC-MT5-6",
                    path_value=r"C:\MT5\terminal64.exe",
                    request_id="req-session-diag-timeout-001",
                    action="login",
                )

        payload = server_v2.v2_session_diagnostic_lookup(requestId="req-session-diag-timeout-001")
        self.assertEqual("req-session-diag-timeout-001", payload["requestId"])
        self.assertTrue(any(item["stage"] == "base_initialize_start" for item in payload["items"]))
        self.assertTrue(any(item["stage"] == "current_terminal_identity" for item in payload["items"]))
        self.assertTrue(any(item["stage"] == "probe_timeout" for item in payload["items"]))

    def test_session_diagnostic_latest_should_return_latest_request_timeline(self):
        server_v2.session_diagnostic_store.append(
            request_id="req-old",
            action="login",
            stage="request_received",
            status="accepted",
            message="收到旧请求",
            server_time=100,
        )
        server_v2.session_diagnostic_store.append(
            request_id="req-new",
            action="login",
            stage="request_received",
            status="accepted",
            message="收到新请求",
            server_time=200,
        )
        server_v2.session_diagnostic_store.append(
            request_id="req-new",
            action="login",
            stage="probe_failed",
            status="failed",
            message="最新请求失败",
            server_time=210,
        )

        payload = server_v2.v2_session_diagnostic_latest()

        self.assertEqual("req-new", payload["requestId"])
        self.assertEqual(2, len(payload["items"]))
        self.assertEqual("request_received", payload["items"][0]["stage"])
        self.assertEqual("probe_failed", payload["items"][1]["stage"])

    def test_session_login_failure_should_leave_diagnostic_timeline_for_request_id(self):
        key_payload = server_v2.v2_session_public_key()
        envelope = self._build_login_envelope(
            key_payload,
            key_id=str(key_payload.get("keyId") or ""),
            client_time=int(time.time() * 1000),
        )
        with mock.patch.object(
            server_v2.session_manager,
            "login_new_account",
            side_effect=RuntimeError("MetaTrader5 initialize/login failed: (-6, 'Authorization failed')"),
        ):
            with self.assertRaises(server_v2.HTTPException) as error_ctx:
                server_v2.v2_session_login(envelope)

        self.assertEqual(502, error_ctx.exception.status_code)
        payload = server_v2.v2_session_diagnostic_lookup(requestId="req-session-diag-001")
        self.assertEqual("req-session-diag-001", payload["requestId"])
        self.assertTrue(any(item["stage"] == "request_received" for item in payload["items"]))
        self.assertTrue(any(item["stage"] == "envelope_decrypted" for item in payload["items"]))
        self.assertTrue(any(item["stage"] == "login_failed" for item in payload["items"]))

    def test_direct_login_failure_should_only_use_real_switch_flow_stages(self):
        allowed = {
            "window_not_found_then_launch_failed",
            "window_not_found_then_window_ready_timeout",
            "attach_failed",
            "baseline_account_read_failed",
            "switch_call_exception",
            "final_account_read_failed",
            "switch_timeout_account_not_changed",
        }
        failure = {
            "ok": False,
            "stage": "switch_timeout_account_not_changed",
            "message": "30s 内未切换到目标账号",
            "elapsedMs": 30000,
            "baselineAccount": {"login": "11111111", "server": "Old-Server"},
            "finalAccount": None,
            "loginError": "(-6, 'Authorization failed')",
            "lastObservedAccount": {"login": "11111111", "server": "Old-Server"},
        }
        fake_controller = mock.Mock()
        fake_controller.switch_account.return_value = failure
        with mock.patch.object(server_v2, "_build_mt5_account_switch_controller", return_value=fake_controller):
            payload = server_v2._switch_mt5_account_via_gui_flow(
                login="12345678",
                password="secret",
                server="ICMarketsSC-MT5-6",
                request_id="req-switch-failed",
                action="login",
            )

        self.assertEqual("switch_timeout_account_not_changed", payload["stage"])
        timeline = server_v2.session_diagnostic_store.lookup("req-switch-failed")
        for item in timeline:
            if item.get("status") == "failed":
                self.assertIn(item.get("stage"), allowed)


if __name__ == "__main__":
    unittest.main()

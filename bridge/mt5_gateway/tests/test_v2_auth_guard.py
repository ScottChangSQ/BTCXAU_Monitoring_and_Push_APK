"""网关鉴权守卫测试。"""

import os
import sys
import types
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import auth_guard  # noqa: E402


class AuthGuardTests(unittest.TestCase):
    def setUp(self):
        self.old_token = os.environ.get(auth_guard.AUTH_ENV_NAME)
        self.old_optional = os.environ.get(auth_guard.AUTH_OPTIONAL_ENV_NAME)
        os.environ[auth_guard.AUTH_ENV_NAME] = "secret-token"
        os.environ.pop(auth_guard.AUTH_OPTIONAL_ENV_NAME, None)

    def tearDown(self):
        if self.old_token is None:
            os.environ.pop(auth_guard.AUTH_ENV_NAME, None)
        else:
            os.environ[auth_guard.AUTH_ENV_NAME] = self.old_token
        if self.old_optional is None:
            os.environ.pop(auth_guard.AUTH_OPTIONAL_ENV_NAME, None)
        else:
            os.environ[auth_guard.AUTH_OPTIONAL_ENV_NAME] = self.old_optional

    def test_http_should_accept_bearer_token(self):
        request = types.SimpleNamespace(headers={"authorization": "Bearer secret-token"}, query_params={})
        auth_guard.require_http_auth(request)

    def test_http_should_reject_missing_token(self):
        request = types.SimpleNamespace(headers={}, query_params={})
        with self.assertRaises(Exception):
            auth_guard.require_http_auth(request)

    def test_ws_should_accept_query_token(self):
        websocket = types.SimpleNamespace(headers={}, query_params={"token": "secret-token"})
        self.assertTrue(auth_guard.verify_ws_auth(websocket))

    def test_ws_should_reject_wrong_token(self):
        websocket = types.SimpleNamespace(headers={"x-gateway-token": "wrong"}, query_params={})
        self.assertFalse(auth_guard.verify_ws_auth(websocket))

    def test_should_only_protect_sensitive_http_paths(self):
        self.assertTrue(auth_guard.should_protect_http_path("/v2/trade/submit"))
        self.assertTrue(auth_guard.should_protect_http_path("/internal/runtime/status"))
        self.assertFalse(auth_guard.should_protect_http_path("/health"))


if __name__ == "__main__":
    unittest.main()

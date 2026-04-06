"""v2 会话加密相关的单元测试。"""

from __future__ import annotations

import time
import unittest

from bridge.mt5_gateway import v2_session_crypto as crypto


class V2SessionCryptoTests(unittest.TestCase):
    """验证 v2 会话加密与时间验证行为。"""

    def test_validate_request_time_rejects_expired(self):
        """过期的 client_time_ms 会被拒绝。"""
        now_ms = int(time.time() * 1000)
        allowed_skew_ms = 1000
        expired_timestamp = now_ms - allowed_skew_ms - 1
        with self.assertRaises(ValueError):
            crypto.validate_request_time(expired_timestamp, now_ms, allowed_skew_ms)

    def test_validate_request_time_rejects_future(self):
        """过远的未来时间会被拒绝。"""
        now_ms = int(time.time() * 1000)
        allowed_skew_ms = 1000
        future_timestamp = now_ms + allowed_skew_ms + 1
        with self.assertRaises(ValueError):
            crypto.validate_request_time(future_timestamp, now_ms, allowed_skew_ms)

    def test_validate_request_time_rejects_negative_skew(self):
        """负的 allowed_skew_ms 是非法的。"""
        now_ms = int(time.time() * 1000)
        with self.assertRaises(ValueError):
            crypto.validate_request_time(now_ms, now_ms, -1)

    def test_validate_request_time_allows_exact_skew(self):
        """刚好等于允许漂移的时间应当通过。"""
        now_ms = int(time.time() * 1000)
        allowed_skew_ms = 1000
        client_time = now_ms + allowed_skew_ms
        self.assertTrue(crypto.validate_request_time(client_time, now_ms, allowed_skew_ms))

    def test_protect_unprotect_roundtrip_bytes(self):
        """保护后的数据仍然可以解密回原始字节串。"""
        payload = b"secret payload"
        protected = crypto.protect_secret_for_machine(payload)
        self.assertIsInstance(protected, bytes)
        unprotected = crypto.unprotect_secret_for_machine(protected)
        self.assertEqual(unprotected, payload)

    def test_protect_unprotect_roundtrip_bytearray(self):
        """bytearray 输入也应被接受并可逆返原始 bytes。"""
        payload = bytearray(b"secret payload")
        protected = crypto.protect_secret_for_machine(payload)
        self.assertIsInstance(protected, bytes)
        unprotected = crypto.unprotect_secret_for_machine(protected)
        self.assertEqual(unprotected, bytes(payload))

    def test_protect_secret_rejects_non_bytes(self):
        """非字节类型被拒绝。"""
        with self.assertRaises(TypeError):
            crypto.protect_secret_for_machine("not bytes")

    def test_fingerprint_handles_crlf(self):
        """CRLF 和 LF PEM 的 fingerprint 应相同。"""
        pem_lf = "-----BEGIN PUBLIC KEY-----\nABC\n-----END PUBLIC KEY-----\n"
        pem_crlf = "-----BEGIN PUBLIC KEY-----\r\nABC\r\n-----END PUBLIC KEY-----\r\n"
        self.assertEqual(crypto.fingerprint_public_key(pem_lf), crypto.fingerprint_public_key(pem_crlf))


if __name__ == "__main__":
    unittest.main()

"""v2 会话加密相关的单元测试。"""

from __future__ import annotations

import base64
import json
import os
import time
import unittest
import uuid

from bridge.mt5_gateway import v2_session_crypto as crypto


class V2SessionCryptoTests(unittest.TestCase):
    """验证 v2 会话加密与时间验证行为。"""

    def _build_login_envelope(
        self,
        login_crypto: crypto.LoginEnvelopeCrypto,
        *,
        key_id: str | None = None,
        client_time: int | None = None,
        nonce: str | None = None,
        remember: bool = True,
        save_account: bool = True,
    ) -> dict:
        """按当前公钥构造一个可解密的登录信封。"""
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import padding
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM

        key_payload = login_crypto.build_public_key_payload()
        public_key = serialization.load_pem_public_key(
            str(key_payload.get("publicKeyPem") or "").encode("utf-8")
        )
        aes_key = os.urandom(32)
        iv = os.urandom(12)
        plain_payload = {
            "login": "12345678",
            "password": "secret",
            "server": "ICMarketsSC-MT5-6",
            "remember": remember,
            "nonce": str(nonce or f"nonce-{uuid.uuid4().hex}"),
            "clientTime": int(client_time if client_time is not None else int(time.time() * 1000)),
        }
        encrypted_payload = AESGCM(aes_key).encrypt(
            iv,
            json.dumps(plain_payload).encode("utf-8"),
            None,
        )
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
            "keyId": str(key_id if key_id is not None else key_payload.get("keyId") or ""),
            "algorithm": str(key_payload.get("algorithm") or ""),
            "encryptedKey": base64.b64encode(encrypted_key).decode("ascii"),
            "encryptedPayload": base64.b64encode(encrypted_payload).decode("ascii"),
            "iv": base64.b64encode(iv).decode("ascii"),
            "saveAccount": save_account,
        }

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

    def test_decrypt_login_envelope_roundtrips_payload(self):
        """登录信封应能解密回原始业务字段。"""
        now_ms = int(time.time() * 1000)
        login_crypto = crypto.LoginEnvelopeCrypto(now_ms_provider=lambda: now_ms)
        envelope = self._build_login_envelope(
            login_crypto,
            client_time=now_ms,
            nonce="nonce-roundtrip-1",
        )

        payload = login_crypto.decrypt_login_envelope(envelope)

        self.assertEqual("req-login-1", payload["requestId"])
        self.assertEqual("12345678", payload["login"])
        self.assertEqual("secret", payload["password"])
        self.assertEqual("ICMarketsSC-MT5-6", payload["server"])
        self.assertEqual(True, payload["remember"])
        self.assertEqual("nonce-roundtrip-1", payload["nonce"])
        self.assertEqual(now_ms, payload["clientTime"])

    def test_build_public_key_payload_should_keep_active_flag_for_activated_state(self):
        """public-key 返回里，state=activated 的当前账号也应保留激活标记。"""
        now_ms = int(time.time() * 1000)
        login_crypto = crypto.LoginEnvelopeCrypto(now_ms_provider=lambda: now_ms)

        payload = login_crypto.build_public_key_payload(
            active_account={
                "profileId": "acct_12345678_icmarketssc-mt5-6",
                "login": "12345678",
                "loginMasked": "****5678",
                "server": "ICMarketsSC-MT5-6",
                "displayName": "ICMarketsSC-MT5-6 ****5678",
                "state": "activated",
            }
        )

        self.assertEqual(True, payload["activeAccount"]["active"])
        self.assertEqual("activated", payload["activeAccount"]["state"])

    def test_decrypt_login_envelope_rejects_wrong_key_id(self):
        """错误 keyId 应在解密前被拒绝。"""
        now_ms = int(time.time() * 1000)
        login_crypto = crypto.LoginEnvelopeCrypto(now_ms_provider=lambda: now_ms)
        envelope = self._build_login_envelope(
            login_crypto,
            key_id="wrong-key-id",
            client_time=now_ms,
        )

        with self.assertRaises(ValueError):
            login_crypto.decrypt_login_envelope(envelope)

    def test_decrypt_login_envelope_rejects_replayed_nonce(self):
        """同一 nonce 在时间窗内重复提交时应被拒绝。"""
        now_ms = int(time.time() * 1000)
        login_crypto = crypto.LoginEnvelopeCrypto(now_ms_provider=lambda: now_ms)
        envelope = self._build_login_envelope(
            login_crypto,
            client_time=now_ms,
            nonce="nonce-replay-1",
        )

        first = login_crypto.decrypt_login_envelope(envelope)
        self.assertEqual("nonce-replay-1", first["nonce"])
        with self.assertRaises(ValueError):
            login_crypto.decrypt_login_envelope(envelope)

    def test_decrypt_login_envelope_rejects_expired_client_time(self):
        """过期 clientTime 的登录信封应被拒绝。"""
        now_ms = int(time.time() * 1000)
        login_crypto = crypto.LoginEnvelopeCrypto(
            now_ms_provider=lambda: now_ms,
            allowed_skew_ms=1000,
        )
        envelope = self._build_login_envelope(
            login_crypto,
            client_time=now_ms - 1001,
            nonce="nonce-expired-1",
        )

        with self.assertRaises(ValueError):
            login_crypto.decrypt_login_envelope(envelope)

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

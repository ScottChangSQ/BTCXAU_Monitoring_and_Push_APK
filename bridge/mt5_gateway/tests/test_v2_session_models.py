"""v2 会话领域模型的单元测试。"""

from __future__ import annotations

import unittest

from bridge.mt5_gateway import v2_session_models as models


class V2SessionModelsTests(unittest.TestCase):
    """验证会话模型与真实接口结构保持一致。"""

    def test_session_account_summary_from_mapping_should_keep_canonical_active_flag(self):
        """恢复接口字典时，应只认 canonical active 字段。"""
        summary = models.SessionAccountSummary.from_mapping(
            {
                "profileId": "acct_123_icmarkets",
                "login": "12345678",
                "loginMasked": "****5678",
                "server": "ICMarketsSC-MT5-6",
                "displayName": "ICMarketsSC-MT5-6 ****5678",
                "active": True,
                "state": "activated",
            }
        )

        self.assertIsNotNone(summary)
        assert summary is not None
        self.assertEqual(True, summary.active)
        self.assertEqual("activated", summary.state)

    def test_session_account_summary_should_ignore_legacy_is_active_field(self):
        """旧 isActive 字段不应继续被当成激活态真值。"""
        summary = models.SessionAccountSummary.from_mapping(
            {
                "profileId": "acct_123_icmarkets",
                "login": "12345678",
                "loginMasked": "****5678",
                "server": "ICMarketsSC-MT5-6",
                "displayName": "ICMarketsSC-MT5-6 ****5678",
                "isActive": True,
                "state": "activated",
            }
        )

        self.assertIsNotNone(summary)
        assert summary is not None
        self.assertEqual(False, summary.active)

    def test_session_account_summary_should_treat_string_false_as_inactive(self):
        """字符串 'false' 不应被当成激活态。"""
        summary = models.SessionAccountSummary.from_mapping(
            {
                "profileId": "acct_123_icmarkets",
                "login": "12345678",
                "loginMasked": "****5678",
                "server": "ICMarketsSC-MT5-6",
                "displayName": "ICMarketsSC-MT5-6 ****5678",
                "active": "false",
                "state": "",
            }
        )

        self.assertIsNotNone(summary)
        assert summary is not None
        self.assertEqual(False, summary.active)
        self.assertEqual("", summary.state)

    def test_session_account_summary_should_match_contract_shape(self):
        """账号摘要模型应输出接口统一字段。"""
        summary = models.SessionAccountSummary(
            profile_id="acct_123_icmarkets",
            login="12345678",
            login_masked="****5678",
            server="ICMarketsSC-MT5-6",
            display_name="ICMarketsSC-MT5-6 ****5678",
            active=True,
            state="activated",
        )

        payload = summary.to_dict()

        self.assertEqual("acct_123_icmarkets", payload["profileId"])
        self.assertEqual("12345678", payload["login"])
        self.assertEqual("****5678", payload["loginMasked"])
        self.assertEqual("ICMarketsSC-MT5-6", payload["server"])
        self.assertEqual("ICMarketsSC-MT5-6 ****5678", payload["displayName"])
        self.assertEqual(True, payload["active"])
        self.assertEqual("activated", payload["state"])

    def test_session_public_key_payload_should_match_contract_shape(self):
        """公钥载荷模型应输出 public-key 接口字段。"""
        account = models.SessionAccountSummary(
            profile_id="acct_123_icmarkets",
            login="12345678",
            login_masked="****5678",
            server="ICMarketsSC-MT5-6",
            display_name="ICMarketsSC-MT5-6 ****5678",
            active=True,
            state="activated",
        )
        payload = models.SessionPublicKeyPayload(
            key_id="key-1",
            algorithm="rsa-oaep+aes-gcm",
            public_key_pem="-----BEGIN PUBLIC KEY-----\nABC\n-----END PUBLIC KEY-----\n",
            expires_at=1234567890,
            active_account=account,
            saved_accounts=[account],
        ).to_dict()

        self.assertEqual(True, payload["ok"])
        self.assertEqual("key-1", payload["keyId"])
        self.assertEqual("rsa-oaep+aes-gcm", payload["algorithm"])
        self.assertEqual(1234567890, payload["expiresAt"])
        self.assertEqual(1, payload["savedAccountCount"])
        self.assertEqual("acct_123_icmarkets", payload["activeAccount"]["profileId"])
        self.assertEqual("acct_123_icmarkets", payload["savedAccounts"][0]["profileId"])

    def test_session_receipt_should_fill_optional_defaults(self):
        """动作回执模型应输出统一回执结构和默认错误字段。"""
        account = models.SessionAccountSummary(
            profile_id="acct_123_icmarkets",
            login="12345678",
            login_masked="****5678",
            server="ICMarketsSC-MT5-6",
            display_name="ICMarketsSC-MT5-6 ****5678",
            active=True,
            state="activated",
        )

        payload = models.SessionReceipt(
            state="activated",
            request_id="req-1",
            active_account=account,
            message="登录成功",
        ).to_dict()

        self.assertEqual(True, payload["ok"])
        self.assertEqual("activated", payload["state"])
        self.assertEqual("req-1", payload["requestId"])
        self.assertEqual("登录成功", payload["message"])
        self.assertEqual("", payload["errorCode"])
        self.assertEqual(False, payload["retryable"])
        self.assertEqual("acct_123_icmarkets", payload["activeAccount"]["profileId"])

    def test_session_status_payload_should_fill_saved_account_count(self):
        """状态模型应根据保存账号列表自动计算数量。"""
        account = models.SessionAccountSummary(
            profile_id="acct_123_icmarkets",
            login="12345678",
            login_masked="****5678",
            server="ICMarketsSC-MT5-6",
            display_name="ICMarketsSC-MT5-6 ****5678",
            active=True,
            state="activated",
        )

        payload = models.SessionStatusPayload(
            state="activated",
            active_account=account,
            saved_accounts=[account],
        ).to_dict()

        self.assertEqual(True, payload["ok"])
        self.assertEqual("activated", payload["state"])
        self.assertEqual(1, payload["savedAccountCount"])
        self.assertEqual("acct_123_icmarkets", payload["activeAccount"]["profileId"])


if __name__ == "__main__":
    unittest.main()

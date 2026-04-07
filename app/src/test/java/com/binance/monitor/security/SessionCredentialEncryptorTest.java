/*
 * 会话凭据加密器测试，负责校验登录信封字段和密文结构是否符合协议。
 * 这里的解密逻辑必须与服务端约定保持一致，避免测试误判生产实现。
 */
package com.binance.monitor.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public class SessionCredentialEncryptorTest {

    @Test
    public void encryptShouldOutputEnvelopeWithRequiredFields() throws Exception {
        KeyPair pair = buildRsaKeyPair();
        String pem = toPublicPem(pair);
        SessionCredentialEncryptor encryptor = new SessionCredentialEncryptor();

        SessionCredentialEncryptor.LoginEnvelope envelope = encryptor.encrypt(
                pem,
                "key-1",
                "12345678",
                "secret",
                "ICMarketsSC-MT5-6",
                true,
                1775400000000L
        );

        assertEquals("key-1", envelope.getKeyId());
        assertEquals("rsa-oaep+aes-gcm", envelope.getAlgorithm());
        assertNotNull(envelope.getRequestId());
        assertTrue(envelope.getRequestId().length() > 0);
        assertTrue(envelope.getEncryptedKey().length() > 0);
        assertTrue(envelope.getEncryptedPayload().length() > 0);
        assertTrue(envelope.getIv().length() > 0);
    }

    @Test
    public void encryptShouldKeepPayloadShapeAndAllowRandomNonceRequestIdIv() throws Exception {
        KeyPair pair = buildRsaKeyPair();
        String pem = toPublicPem(pair);
        SessionCredentialEncryptor encryptor = new SessionCredentialEncryptor();

        SessionCredentialEncryptor.LoginEnvelope first = encryptor.encrypt(
                pem,
                "key-2",
                "87654321",
                "secret-pass",
                "ICMarketsSC-MT5-6",
                false,
                1775400000111L
        );
        SessionCredentialEncryptor.LoginEnvelope second = encryptor.encrypt(
                pem,
                "key-2",
                "87654321",
                "secret-pass",
                "ICMarketsSC-MT5-6",
                false,
                1775400000111L
        );

        JSONObject plain = decryptPayload(first, pair.getPrivate());
        assertEquals("87654321", plain.optString("login"));
        assertEquals("secret-pass", plain.optString("password"));
        assertEquals("ICMarketsSC-MT5-6", plain.optString("server"));
        assertFalse(plain.optBoolean("remember", true));
        assertEquals(1775400000111L, plain.optLong("clientTime"));
        assertTrue(plain.optString("nonce").length() > 0);

        assertNotEquals(first.getRequestId(), second.getRequestId());
        assertNotEquals(first.getIv(), second.getIv());

        JSONObject plainSecond = decryptPayload(second, pair.getPrivate());
        assertNotEquals(plain.optString("nonce"), plainSecond.optString("nonce"));
    }

    private static KeyPair buildRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPublicPem(KeyPair pair) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(pair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n"
                + base64
                + "\n-----END PUBLIC KEY-----";
    }

    private static JSONObject decryptPayload(SessionCredentialEncryptor.LoginEnvelope envelope,
                                             PrivateKey privateKey) throws Exception {
        byte[] encryptedAesKey = Base64.getDecoder().decode(envelope.getEncryptedKey());
        byte[] iv = Base64.getDecoder().decode(envelope.getIv());
        byte[] encryptedPayload = Base64.getDecoder().decode(envelope.getEncryptedPayload());

        Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsaCipher.init(
                Cipher.DECRYPT_MODE,
                privateKey,
                new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        MGF1ParameterSpec.SHA256,
                        PSource.PSpecified.DEFAULT
                )
        );
        byte[] aesKey = rsaCipher.doFinal(encryptedAesKey);

        Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
        aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] plain = aesCipher.doFinal(encryptedPayload);
        return new JSONObject(new String(plain, StandardCharsets.UTF_8));
    }
}

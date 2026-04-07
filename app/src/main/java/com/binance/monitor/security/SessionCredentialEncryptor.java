/*
 * 会话凭据加密器，负责把登录信息封装为 rsa-oaep+aes-gcm 的登录信封。
 * 产出的 LoginEnvelope 直接用于 GatewayV2SessionClient.login 请求体。
 */
package com.binance.monitor.security;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public class SessionCredentialEncryptor {
    private static final String ENVELOPE_ALGORITHM = "rsa-oaep+aes-gcm";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final SecureRandom secureRandom;

    // 创建会话凭据加密器。
    public SessionCredentialEncryptor() {
        this.secureRandom = new SecureRandom();
    }

    // 生成登录信封，包含请求字段和加密后的业务载荷。
    public LoginEnvelope encrypt(String publicKeyPem,
                                 String keyId,
                                 String login,
                                 String password,
                                 String server,
                                 boolean remember,
                                 long clientTime) throws Exception {
        PublicKey publicKey = parseRsaPublicKey(publicKeyPem);
        String requestId = UUID.randomUUID().toString();
        JSONObject plainPayload = buildPlainPayload(login, password, server, remember, clientTime);

        SecretKey aesKey = generateAesKey();
        byte[] iv = generateIv();
        byte[] encryptedPayload = encryptPayloadWithAesGcm(plainPayload.toString(), aesKey, iv);
        byte[] encryptedKey = encryptAesKeyWithRsaOaep(aesKey, publicKey);

        return new LoginEnvelope(
                requestId,
                keyId == null ? "" : keyId,
                ENVELOPE_ALGORITHM,
                Base64.getEncoder().encodeToString(encryptedKey),
                Base64.getEncoder().encodeToString(encryptedPayload),
                Base64.getEncoder().encodeToString(iv)
        );
    }

    // 组装明文业务载荷。
    private static JSONObject buildPlainPayload(String login,
                                                String password,
                                                String server,
                                                boolean remember,
                                                long clientTime) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("login", login == null ? "" : login);
        payload.put("password", password == null ? "" : password);
        payload.put("server", server == null ? "" : server);
        payload.put("remember", remember);
        payload.put("nonce", UUID.randomUUID().toString());
        payload.put("clientTime", clientTime);
        return payload;
    }

    // 读取 PEM 公钥文本并转为 RSA 公钥对象。
    private static PublicKey parseRsaPublicKey(String pem) throws Exception {
        String content = pem == null ? "" : pem.trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("publicKeyPem 不能为空");
        }
        String base64 = content
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    // 生成随机 AES 对称密钥。
    private static SecretKey generateAesKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(AES_KEY_BITS);
        return generator.generateKey();
    }

    // 生成随机 GCM iv。
    private byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    // 使用 AES-GCM 加密明文载荷。
    private static byte[] encryptPayloadWithAesGcm(String plainText, SecretKey aesKey, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plainBytes = (plainText == null ? "" : plainText).getBytes(StandardCharsets.UTF_8);
        return cipher.doFinal(plainBytes);
    }

    // 使用 RSA-OAEP(SHA-256) 加密 AES 密钥。
    private static byte[] encryptAesKeyWithRsaOaep(SecretKey aesKey, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                new OAEPParameterSpec(
                        "SHA-256",
                        "MGF1",
                        java.security.spec.MGF1ParameterSpec.SHA256,
                        PSource.PSpecified.DEFAULT
                )
        );
        return cipher.doFinal(aesKey.getEncoded());
    }

    // 登录信封模型，提供给会话客户端序列化请求体使用。
    public static final class LoginEnvelope {
        private final String requestId;
        private final String keyId;
        private final String algorithm;
        private final String encryptedKey;
        private final String encryptedPayload;
        private final String iv;

        // 构造登录信封对象。
        public LoginEnvelope(String requestId,
                             String keyId,
                             String algorithm,
                             String encryptedKey,
                             String encryptedPayload,
                             String iv) {
            this.requestId = requestId == null ? "" : requestId;
            this.keyId = keyId == null ? "" : keyId;
            this.algorithm = algorithm == null ? "" : algorithm;
            this.encryptedKey = encryptedKey == null ? "" : encryptedKey;
            this.encryptedPayload = encryptedPayload == null ? "" : encryptedPayload;
            this.iv = iv == null ? "" : iv;
        }

        // 返回请求 ID。
        public String getRequestId() {
            return requestId;
        }

        // 返回 keyId。
        public String getKeyId() {
            return keyId;
        }

        // 返回算法标识。
        public String getAlgorithm() {
            return algorithm;
        }

        // 返回密钥密文。
        public String getEncryptedKey() {
            return encryptedKey;
        }

        // 返回载荷密文。
        public String getEncryptedPayload() {
            return encryptedPayload;
        }

        // 返回 iv。
        public String getIv() {
            return iv;
        }
    }
}

/*
 * 会话公钥接口模型，承载 key 信息和当前会话账号摘要。
 * 由 GatewayV2SessionClient 的 public-key 解析方法返回。
 */
package com.binance.monitor.data.model.v2.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionPublicKeyPayload {
    private final boolean ok;
    private final String keyId;
    private final String algorithm;
    private final String publicKeyPem;
    private final long expiresAt;
    private final RemoteAccountProfile activeAccount;
    private final List<RemoteAccountProfile> savedAccounts;
    private final int savedAccountCount;
    private final String rawJson;

    // 构造会话公钥载荷对象。
    public SessionPublicKeyPayload(boolean ok,
                                   String keyId,
                                   String algorithm,
                                   String publicKeyPem,
                                   long expiresAt,
                                   RemoteAccountProfile activeAccount,
                                   List<RemoteAccountProfile> savedAccounts,
                                   int savedAccountCount,
                                   String rawJson) {
        this.ok = ok;
        this.keyId = keyId == null ? "" : keyId;
        this.algorithm = algorithm == null ? "" : algorithm;
        this.publicKeyPem = publicKeyPem == null ? "" : publicKeyPem;
        this.expiresAt = expiresAt;
        this.activeAccount = activeAccount;
        this.savedAccounts = savedAccounts == null ? new ArrayList<>() : new ArrayList<>(savedAccounts);
        this.savedAccountCount = Math.max(0, savedAccountCount);
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    // 返回是否成功。
    public boolean isOk() {
        return ok;
    }

    // 返回 keyId。
    public String getKeyId() {
        return keyId;
    }

    // 返回算法标识。
    public String getAlgorithm() {
        return algorithm;
    }

    // 返回 PEM 公钥内容。
    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    // 返回过期时间戳。
    public long getExpiresAt() {
        return expiresAt;
    }

    // 返回当前激活账号。
    public RemoteAccountProfile getActiveAccount() {
        return activeAccount;
    }

    // 返回已保存账号列表。
    public List<RemoteAccountProfile> getSavedAccounts() {
        return Collections.unmodifiableList(savedAccounts);
    }

    // 返回已保存账号数量。
    public int getSavedAccountCount() {
        return savedAccountCount;
    }

    // 返回原始 JSON 字符串。
    public String getRawJson() {
        return rawJson;
    }
}

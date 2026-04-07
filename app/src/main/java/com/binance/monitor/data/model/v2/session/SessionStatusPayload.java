/*
 * 会话状态模型，统一承载当前激活账号与已保存账号列表。
 * 由 GatewayV2SessionClient 的 status 解析和拉取接口返回。
 */
package com.binance.monitor.data.model.v2.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionStatusPayload {
    private final boolean ok;
    private final String state;
    private final RemoteAccountProfile activeAccount;
    private final List<RemoteAccountProfile> savedAccounts;
    private final int savedAccountCount;
    private final String rawJson;

    // 构造会话状态对象。
    public SessionStatusPayload(boolean ok,
                                String state,
                                RemoteAccountProfile activeAccount,
                                List<RemoteAccountProfile> savedAccounts,
                                int savedAccountCount,
                                String rawJson) {
        this.ok = ok;
        this.state = state == null ? "" : state;
        this.activeAccount = activeAccount;
        this.savedAccounts = savedAccounts == null ? new ArrayList<>() : new ArrayList<>(savedAccounts);
        this.savedAccountCount = Math.max(0, savedAccountCount);
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    // 返回是否成功。
    public boolean isOk() {
        return ok;
    }

    // 返回会话状态。
    public String getState() {
        return state;
    }

    // 返回激活账号。
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

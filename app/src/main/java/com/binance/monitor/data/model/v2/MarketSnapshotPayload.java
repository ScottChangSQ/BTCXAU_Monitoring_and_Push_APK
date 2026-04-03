/*
 * v2 行情总快照模型，保存服务端 market snapshot 的原始关键字段。
 * 供图表页和后续同步层统一读取 syncToken 与市场快照。
 */
package com.binance.monitor.data.model.v2;

import org.json.JSONObject;

public class MarketSnapshotPayload {

    private final long serverTime;
    private final String syncToken;
    private final JSONObject market;
    private final JSONObject account;
    private final String rawJson;

    public MarketSnapshotPayload(long serverTime,
                                 String syncToken,
                                 JSONObject market,
                                 JSONObject account,
                                 String rawJson) {
        this.serverTime = serverTime;
        this.syncToken = syncToken == null ? "" : syncToken;
        this.market = market == null ? new JSONObject() : market;
        this.account = account == null ? new JSONObject() : account;
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    public long getServerTime() {
        return serverTime;
    }

    public String getSyncToken() {
        return syncToken;
    }

    public JSONObject getMarket() {
        return market;
    }

    public JSONObject getAccount() {
        return account;
    }

    public String getRawJson() {
        return rawJson;
    }
}

/*
 * v2 增量同步模型，保存 market/account 两类 delta 与下一枚 syncToken。
 * 供统一同步层在 WebSocket 断线后补差时使用。
 */
package com.binance.monitor.data.model.v2;

import org.json.JSONArray;

public class SyncDeltaPayload {

    private final long serverTime;
    private final String nextSyncToken;
    private final JSONArray marketDelta;
    private final JSONArray accountDelta;
    private final String rawJson;

    public SyncDeltaPayload(long serverTime,
                            String nextSyncToken,
                            JSONArray marketDelta,
                            JSONArray accountDelta,
                            String rawJson) {
        this.serverTime = serverTime;
        this.nextSyncToken = nextSyncToken == null ? "" : nextSyncToken;
        this.marketDelta = marketDelta == null ? new JSONArray() : marketDelta;
        this.accountDelta = accountDelta == null ? new JSONArray() : accountDelta;
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    public long getServerTime() {
        return serverTime;
    }

    public String getNextSyncToken() {
        return nextSyncToken;
    }

    public JSONArray getMarketDelta() {
        return marketDelta;
    }

    public JSONArray getAccountDelta() {
        return accountDelta;
    }

    public String getRawJson() {
        return rawJson;
    }
}

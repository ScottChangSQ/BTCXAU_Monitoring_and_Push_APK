/*
 * v2 账户快照模型，保存账户区、当前持仓和挂单原始结构。
 * 供账户页和行情持仓页后续切到 v2 账户真值时复用。
 */
package com.binance.monitor.data.model.v2;

import org.json.JSONArray;
import org.json.JSONObject;

public class AccountSnapshotPayload {

    private final long serverTime;
    private final String syncToken;
    private final JSONObject accountMeta;
    private final JSONObject account;
    private final JSONArray overviewMetrics;
    private final JSONArray curveIndicators;
    private final JSONArray statsMetrics;
    private final JSONArray positions;
    private final JSONArray orders;
    private final String rawJson;

    public AccountSnapshotPayload(long serverTime,
                                  String syncToken,
                                  JSONObject accountMeta,
                                  JSONObject account,
                                  JSONArray overviewMetrics,
                                  JSONArray curveIndicators,
                                  JSONArray statsMetrics,
                                  JSONArray positions,
                                  JSONArray orders,
                                  String rawJson) {
        this.serverTime = serverTime;
        this.syncToken = syncToken == null ? "" : syncToken;
        this.accountMeta = accountMeta == null ? new JSONObject() : accountMeta;
        this.account = account == null ? new JSONObject() : account;
        this.overviewMetrics = overviewMetrics == null ? new JSONArray() : overviewMetrics;
        this.curveIndicators = curveIndicators == null ? new JSONArray() : curveIndicators;
        this.statsMetrics = statsMetrics == null ? new JSONArray() : statsMetrics;
        this.positions = positions == null ? new JSONArray() : positions;
        this.orders = orders == null ? new JSONArray() : orders;
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    public long getServerTime() {
        return serverTime;
    }

    public String getSyncToken() {
        return syncToken;
    }

    public JSONObject getAccountMeta() {
        return accountMeta;
    }

    public JSONObject getAccount() {
        return account;
    }

    public JSONArray getOverviewMetrics() {
        return overviewMetrics;
    }

    public JSONArray getCurveIndicators() {
        return curveIndicators;
    }

    public JSONArray getStatsMetrics() {
        return statsMetrics;
    }

    public JSONArray getPositions() {
        return positions;
    }

    public JSONArray getOrders() {
        return orders;
    }

    public String getRawJson() {
        return rawJson;
    }
}

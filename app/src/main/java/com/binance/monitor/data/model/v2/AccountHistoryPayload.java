/*
 * v2 账户历史载荷模型，保存交易、订单、曲线和分页游标。
 * 供账户预加载把 /v2/account/history 原始数据落到本地缓存。
 */
package com.binance.monitor.data.model.v2;

import org.json.JSONArray;
import org.json.JSONObject;

public class AccountHistoryPayload {

    private final long serverTime;
    private final String syncToken;
    private final JSONObject accountMeta;
    private final JSONArray overviewMetrics;
    private final JSONArray curveIndicators;
    private final JSONArray statsMetrics;
    private final JSONArray trades;
    private final JSONArray orders;
    private final JSONArray curvePoints;
    private final String nextCursor;
    private final String rawJson;

    public AccountHistoryPayload(long serverTime,
                                 String syncToken,
                                 JSONObject accountMeta,
                                 JSONArray overviewMetrics,
                                 JSONArray curveIndicators,
                                 JSONArray statsMetrics,
                                 JSONArray trades,
                                 JSONArray orders,
                                 JSONArray curvePoints,
                                 String nextCursor,
                                 String rawJson) {
        this.serverTime = serverTime;
        this.syncToken = syncToken == null ? "" : syncToken;
        this.accountMeta = accountMeta == null ? new JSONObject() : accountMeta;
        this.overviewMetrics = overviewMetrics == null ? new JSONArray() : overviewMetrics;
        this.curveIndicators = curveIndicators == null ? new JSONArray() : curveIndicators;
        this.statsMetrics = statsMetrics == null ? new JSONArray() : statsMetrics;
        this.trades = trades == null ? new JSONArray() : trades;
        this.orders = orders == null ? new JSONArray() : orders;
        this.curvePoints = curvePoints == null ? new JSONArray() : curvePoints;
        this.nextCursor = nextCursor == null ? "" : nextCursor;
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

    public JSONArray getOverviewMetrics() {
        return overviewMetrics;
    }

    public JSONArray getCurveIndicators() {
        return curveIndicators;
    }

    public JSONArray getStatsMetrics() {
        return statsMetrics;
    }

    public JSONArray getTrades() {
        return trades;
    }

    public JSONArray getOrders() {
        return orders;
    }

    public JSONArray getCurvePoints() {
        return curvePoints;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public String getRawJson() {
        return rawJson;
    }
}

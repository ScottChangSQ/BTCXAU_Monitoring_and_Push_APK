/*
 * v2 账户完整载荷模型，保存强一致刷新所需的当前运行态与历史主体。
 * 供账户页主动刷新和交易后确认复用单次原子接口结果。
 */
package com.binance.monitor.data.model.v2;

import org.json.JSONArray;
import org.json.JSONObject;

public class AccountFullPayload {

    private final long serverTime;
    private final String syncToken;
    private final JSONObject accountMeta;
    private final JSONObject account;
    private final JSONArray overviewMetrics;
    private final JSONArray curveIndicators;
    private final JSONArray statsMetrics;
    private final JSONArray positions;
    private final JSONArray orders;
    private final JSONArray trades;
    private final JSONArray curvePoints;
    private final String rawJson;

    public AccountFullPayload(long serverTime,
                              String syncToken,
                              JSONObject accountMeta,
                              JSONObject account,
                              JSONArray overviewMetrics,
                              JSONArray curveIndicators,
                              JSONArray statsMetrics,
                              JSONArray positions,
                              JSONArray orders,
                              JSONArray trades,
                              JSONArray curvePoints,
                              String rawJson) {
        this.serverTime = serverTime;
        this.syncToken = syncToken == null ? "" : syncToken;
        if (accountMeta == null) {
            throw new IllegalArgumentException("accountMeta is required");
        }
        if (account == null) {
            throw new IllegalArgumentException("account is required");
        }
        if (positions == null) {
            throw new IllegalArgumentException("positions is required");
        }
        if (orders == null) {
            throw new IllegalArgumentException("orders is required");
        }
        if (trades == null) {
            throw new IllegalArgumentException("trades is required");
        }
        if (curvePoints == null) {
            throw new IllegalArgumentException("curvePoints is required");
        }
        this.accountMeta = accountMeta;
        this.account = account;
        this.overviewMetrics = overviewMetrics == null ? new JSONArray() : overviewMetrics;
        this.curveIndicators = curveIndicators == null ? new JSONArray() : curveIndicators;
        this.statsMetrics = statsMetrics == null ? new JSONArray() : statsMetrics;
        this.positions = positions;
        this.orders = orders;
        this.trades = trades;
        this.curvePoints = curvePoints;
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

    public JSONArray getTrades() {
        return trades;
    }

    public JSONArray getCurvePoints() {
        return curvePoints;
    }

    public String getRawJson() {
        return rawJson;
    }
}

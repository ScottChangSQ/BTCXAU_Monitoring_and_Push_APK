/*
 * 账户持仓页运行态载荷，只承载运行态字段，并显式暴露空历史区块。
 */
package com.binance.monitor.ui.account.runtime;

import androidx.annotation.NonNull;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import java.util.Collections;
import java.util.List;

public final class AccountRuntimePayload {
    private final List<AccountMetric> overviewMetrics;
    private final List<PositionItem> positions;
    private final List<PositionItem> pendingOrders;
    private final List<TradeRecordItem> trades;
    private final List<CurvePoint> curvePoints;
    private final String account;
    private final String server;
    private final boolean connected;
    private final long updatedAt;
    private final long fetchedAt;

    public AccountRuntimePayload(@NonNull List<AccountMetric> overviewMetrics,
                                 @NonNull List<PositionItem> positions,
                                 @NonNull List<PositionItem> pendingOrders,
                                 @NonNull List<TradeRecordItem> trades,
                                 @NonNull List<CurvePoint> curvePoints,
                                 @NonNull String account,
                                 @NonNull String server,
                                 boolean connected,
                                 long updatedAt,
                                 long fetchedAt) {
        this.overviewMetrics = overviewMetrics;
        this.positions = positions;
        this.pendingOrders = pendingOrders;
        this.trades = trades;
        this.curvePoints = curvePoints;
        this.account = account;
        this.server = server;
        this.connected = connected;
        this.updatedAt = updatedAt;
        this.fetchedAt = fetchedAt;
    }

    public static AccountRuntimePayload empty() {
        return new AccountRuntimePayload(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "", "", false,
                0L, 0L);
    }

    public List<AccountMetric> getOverviewMetrics() {
        return overviewMetrics;
    }

    public List<PositionItem> getPositions() {
        return positions;
    }

    public List<PositionItem> getPendingOrders() {
        return pendingOrders;
    }

    public List<TradeRecordItem> getTrades() {
        return trades;
    }

    public List<CurvePoint> getCurvePoints() {
        return curvePoints;
    }

    public String getAccount() {
        return account;
    }

    public String getServer() {
        return server;
    }

    public boolean isConnected() {
        return connected;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public long getFetchedAt() {
        return fetchedAt;
    }
}

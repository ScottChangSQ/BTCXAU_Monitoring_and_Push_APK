/*
 * 账户全量快照模型，统一承载指标、曲线、持仓、挂单和交易记录。
 */
package com.binance.monitor.domain.account.model;

import java.util.List;

public class AccountSnapshot {
    private final List<AccountMetric> overviewMetrics;
    private final List<CurvePoint> curvePoints;
    private final List<AccountMetric> curveIndicators;
    private final List<PositionItem> positions;
    private final List<PositionItem> pendingOrders;
    private final List<TradeRecordItem> trades;
    private final List<AccountMetric> statsMetrics;

    public AccountSnapshot(List<AccountMetric> overviewMetrics,
                           List<CurvePoint> curvePoints,
                           List<AccountMetric> curveIndicators,
                           List<PositionItem> positions,
                           List<TradeRecordItem> trades,
                           List<AccountMetric> statsMetrics) {
        this(overviewMetrics, curvePoints, curveIndicators, positions, null, trades, statsMetrics);
    }

    public AccountSnapshot(List<AccountMetric> overviewMetrics,
                           List<CurvePoint> curvePoints,
                           List<AccountMetric> curveIndicators,
                           List<PositionItem> positions,
                           List<PositionItem> pendingOrders,
                           List<TradeRecordItem> trades,
                           List<AccountMetric> statsMetrics) {
        this.overviewMetrics = overviewMetrics;
        this.curvePoints = curvePoints;
        this.curveIndicators = curveIndicators;
        this.positions = positions;
        this.pendingOrders = pendingOrders;
        this.trades = trades;
        this.statsMetrics = statsMetrics;
    }

    public List<AccountMetric> getOverviewMetrics() {
        return overviewMetrics;
    }

    public List<CurvePoint> getCurvePoints() {
        return curvePoints;
    }

    public List<AccountMetric> getCurveIndicators() {
        return curveIndicators;
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

    public List<AccountMetric> getStatsMetrics() {
        return statsMetrics;
    }
}

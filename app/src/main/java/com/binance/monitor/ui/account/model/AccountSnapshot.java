package com.binance.monitor.ui.account.model;

import java.util.List;

public class AccountSnapshot {
    private final List<AccountMetric> overviewMetrics;
    private final List<CurvePoint> curvePoints;
    private final List<AccountMetric> curveIndicators;
    private final List<PositionItem> positions;
    private final List<TradeRecordItem> trades;
    private final List<AccountMetric> statsMetrics;

    public AccountSnapshot(List<AccountMetric> overviewMetrics,
                           List<CurvePoint> curvePoints,
                           List<AccountMetric> curveIndicators,
                           List<PositionItem> positions,
                           List<TradeRecordItem> trades,
                           List<AccountMetric> statsMetrics) {
        this.overviewMetrics = overviewMetrics;
        this.curvePoints = curvePoints;
        this.curveIndicators = curveIndicators;
        this.positions = positions;
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

    public List<TradeRecordItem> getTrades() {
        return trades;
    }

    public List<AccountMetric> getStatsMetrics() {
        return statsMetrics;
    }
}

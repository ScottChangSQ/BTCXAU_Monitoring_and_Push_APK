/*
 * 持仓产品聚合模型，负责承接账户持仓页“按产品统计”的只读展示字段。
 */
package com.binance.monitor.ui.account;

public class PositionAggregateItem {
    private final String displayLabel;
    private final String compactDisplayLabel;
    private final int positionCount;
    private final int pendingCount;
    private final double totalLots;
    private final double signedLots;
    private final double netPnl;
    private final String summaryText;

    public PositionAggregateItem(String displayLabel,
                                 String compactDisplayLabel,
                                 int positionCount,
                                 int pendingCount,
                                 double totalLots,
                                 double signedLots,
                                 double netPnl,
                                 String summaryText) {
        this.displayLabel = displayLabel == null ? "" : displayLabel;
        this.compactDisplayLabel = compactDisplayLabel == null ? "" : compactDisplayLabel;
        this.positionCount = Math.max(0, positionCount);
        this.pendingCount = Math.max(0, pendingCount);
        this.totalLots = totalLots;
        this.signedLots = signedLots;
        this.netPnl = netPnl;
        this.summaryText = summaryText == null ? "" : summaryText;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getCompactDisplayLabel() {
        return compactDisplayLabel;
    }

    public int getPositionCount() {
        return positionCount;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public double getTotalLots() {
        return totalLots;
    }

    public double getSignedLots() {
        return signedLots;
    }

    public double getNetPnl() {
        return netPnl;
    }

    public String getSummaryText() {
        return summaryText;
    }
}

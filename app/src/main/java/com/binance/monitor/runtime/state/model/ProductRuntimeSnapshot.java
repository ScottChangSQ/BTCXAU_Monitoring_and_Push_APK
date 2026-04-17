/*
 * 产品级运行态快照，统一承接图表、账户条目和悬浮窗消费的派生结果。
 */
package com.binance.monitor.runtime.state.model;

import com.binance.monitor.util.ProductSymbolMapper;

public final class ProductRuntimeSnapshot {
    private final String symbol;
    private final long productRevision;
    private final int positionCount;
    private final int pendingCount;
    private final double totalLots;
    private final double signedLots;
    private final double netPnl;
    private final String displayLabel;
    private final String compactDisplayLabel;
    private final String positionSummaryText;
    private final String pendingSummaryText;

    public ProductRuntimeSnapshot(String symbol,
                                  long productRevision,
                                  int positionCount,
                                  int pendingCount,
                                  double totalLots,
                                  double netPnl,
                                  String positionSummaryText,
                                  String pendingSummaryText) {
        this(symbol,
                productRevision,
                positionCount,
                pendingCount,
                totalLots,
                totalLots,
                netPnl,
                symbol,
                resolveCompactDisplayLabel(symbol),
                positionSummaryText,
                pendingSummaryText);
    }

    public ProductRuntimeSnapshot(String symbol,
                                  long productRevision,
                                  int positionCount,
                                  int pendingCount,
                                  double totalLots,
                                  double signedLots,
                                  double netPnl,
                                  String displayLabel,
                                  String compactDisplayLabel,
                                  String positionSummaryText,
                                  String pendingSummaryText) {
        this.symbol = symbol == null ? "" : symbol;
        this.productRevision = productRevision;
        this.positionCount = positionCount;
        this.pendingCount = pendingCount;
        this.totalLots = totalLots;
        this.signedLots = signedLots;
        this.netPnl = netPnl;
        this.displayLabel = displayLabel == null ? "" : displayLabel;
        this.compactDisplayLabel = compactDisplayLabel == null ? "" : compactDisplayLabel;
        this.positionSummaryText = positionSummaryText == null ? "" : positionSummaryText;
        this.pendingSummaryText = pendingSummaryText == null ? "" : pendingSummaryText;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getProductRevision() {
        return productRevision;
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

    public String getDisplayLabel() {
        return displayLabel;
    }

    public String getCompactDisplayLabel() {
        return compactDisplayLabel;
    }

    public String getPositionSummaryText() {
        return positionSummaryText;
    }

    public String getPendingSummaryText() {
        return pendingSummaryText;
    }

    private static String resolveCompactDisplayLabel(String symbol) {
        String tradeSymbol = ProductSymbolMapper.toTradeSymbol(symbol);
        if (ProductSymbolMapper.TRADE_SYMBOL_BTC.equals(tradeSymbol)) {
            return "BTC";
        }
        if (ProductSymbolMapper.TRADE_SYMBOL_XAU.equals(tradeSymbol)) {
            return "XAU";
        }
        return symbol == null ? "" : symbol;
    }
}

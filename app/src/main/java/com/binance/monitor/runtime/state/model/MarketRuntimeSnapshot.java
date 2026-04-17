/*
 * 市场运行态快照，统一表达主界面与图表消费的最新市场真值。
 */
package com.binance.monitor.runtime.state.model;

public final class MarketRuntimeSnapshot {
    private final long marketRevision;
    private final String selectedSymbol;
    private final double latestPrice;
    private final long updatedAt;

    public MarketRuntimeSnapshot(long marketRevision,
                                 String selectedSymbol,
                                 double latestPrice,
                                 long updatedAt) {
        this.marketRevision = marketRevision;
        this.selectedSymbol = selectedSymbol == null ? "" : selectedSymbol;
        this.latestPrice = latestPrice;
        this.updatedAt = updatedAt;
    }

    public long getMarketRevision() {
        return marketRevision;
    }

    public String getSelectedSymbol() {
        return selectedSymbol;
    }

    public double getLatestPrice() {
        return latestPrice;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}

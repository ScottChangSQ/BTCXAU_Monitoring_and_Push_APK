/*
 * 悬浮窗卡片运行态模型，后续供悬浮窗直接消费。
 */
package com.binance.monitor.runtime.state.model;

public final class FloatingCardRuntimeModel {
    private final ProductRuntimeSnapshot productRuntimeSnapshot;

    public FloatingCardRuntimeModel(ProductRuntimeSnapshot productRuntimeSnapshot) {
        this.productRuntimeSnapshot = productRuntimeSnapshot;
    }

    public ProductRuntimeSnapshot getProductRuntimeSnapshot() {
        return productRuntimeSnapshot;
    }

    public int getPositionCount() {
        return productRuntimeSnapshot == null ? 0 : productRuntimeSnapshot.getPositionCount();
    }

    public int getPendingCount() {
        return productRuntimeSnapshot == null ? 0 : productRuntimeSnapshot.getPendingCount();
    }

    public double getTotalLots() {
        return productRuntimeSnapshot == null ? 0d : productRuntimeSnapshot.getTotalLots();
    }

    public double getSignedLots() {
        return productRuntimeSnapshot == null ? 0d : productRuntimeSnapshot.getSignedLots();
    }

    public double getNetPnl() {
        return productRuntimeSnapshot == null ? 0d : productRuntimeSnapshot.getNetPnl();
    }

    public String getDisplayLabel() {
        return productRuntimeSnapshot == null ? "" : productRuntimeSnapshot.getDisplayLabel();
    }

    public String getCompactDisplayLabel() {
        return productRuntimeSnapshot == null ? "" : productRuntimeSnapshot.getCompactDisplayLabel();
    }

    public String getCrossPageSummaryText() {
        return productRuntimeSnapshot == null ? "" : productRuntimeSnapshot.getCrossPageSummaryText();
    }
}

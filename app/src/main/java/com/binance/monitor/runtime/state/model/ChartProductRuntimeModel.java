/*
 * 图表页产品运行态模型，后续供图表摘要与 overlay 输入共用。
 */
package com.binance.monitor.runtime.state.model;

public final class ChartProductRuntimeModel {
    private final ProductRuntimeSnapshot productRuntimeSnapshot;

    public ChartProductRuntimeModel(ProductRuntimeSnapshot productRuntimeSnapshot) {
        this.productRuntimeSnapshot = productRuntimeSnapshot;
    }

    public ProductRuntimeSnapshot getProductRuntimeSnapshot() {
        return productRuntimeSnapshot;
    }

    public long getProductRevision() {
        return productRuntimeSnapshot == null ? 0L : productRuntimeSnapshot.getProductRevision();
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

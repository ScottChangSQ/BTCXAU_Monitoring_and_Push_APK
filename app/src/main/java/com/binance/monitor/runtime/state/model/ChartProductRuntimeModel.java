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
}

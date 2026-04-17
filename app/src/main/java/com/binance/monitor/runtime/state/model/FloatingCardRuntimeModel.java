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
}

/*
 * 图表页刷新事件模型，把市场、产品运行态和 UI 状态变化分开。
 */
package com.binance.monitor.ui.chart.runtime;

public final class ChartRefreshEvent {
    public enum Type {
        MARKET_TICK_CHANGED,
        PRODUCT_RUNTIME_CHANGED,
        UI_STATE_CHANGED,
        DIALOG_STATE_CHANGED
    }

    private final Type type;
    private final boolean overlayChanged;

    private ChartRefreshEvent(Type type, boolean overlayChanged) {
        this.type = type;
        this.overlayChanged = overlayChanged;
    }

    public static ChartRefreshEvent marketTickChanged() {
        return new ChartRefreshEvent(Type.MARKET_TICK_CHANGED, false);
    }

    public static ChartRefreshEvent productRuntimeChanged(boolean overlayChanged) {
        return new ChartRefreshEvent(Type.PRODUCT_RUNTIME_CHANGED, overlayChanged);
    }

    public static ChartRefreshEvent uiStateChanged() {
        return new ChartRefreshEvent(Type.UI_STATE_CHANGED, false);
    }

    public static ChartRefreshEvent dialogStateChanged() {
        return new ChartRefreshEvent(Type.DIALOG_STATE_CHANGED, false);
    }

    public Type getType() {
        return type;
    }

    public boolean isOverlayChanged() {
        return overlayChanged;
    }
}

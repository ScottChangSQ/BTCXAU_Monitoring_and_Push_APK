/*
 * 图表页本次刷新预算，只表达当前允许刷哪些区块。
 */
package com.binance.monitor.ui.chart.runtime;

public final class ChartRefreshBudget {
    private final boolean needsUiStateBind;
    private final boolean needsDialogBind;
    private final boolean needsSummaryBind;
    private final boolean needsOverlayRebuild;
    private final boolean needsRealtimeTailInvalidate;

    private ChartRefreshBudget(boolean needsUiStateBind,
                               boolean needsDialogBind,
                               boolean needsSummaryBind,
                               boolean needsOverlayRebuild,
                               boolean needsRealtimeTailInvalidate) {
        this.needsUiStateBind = needsUiStateBind;
        this.needsDialogBind = needsDialogBind;
        this.needsSummaryBind = needsSummaryBind;
        this.needsOverlayRebuild = needsOverlayRebuild;
        this.needsRealtimeTailInvalidate = needsRealtimeTailInvalidate;
    }

    public static ChartRefreshBudget resolve(ChartRefreshEvent event) {
        if (event == null) {
            return new ChartRefreshBudget(false, false, false, false, false);
        }
        switch (event.getType()) {
            case MARKET_TICK_CHANGED:
                return new ChartRefreshBudget(false, false, false, false, true);
            case PRODUCT_RUNTIME_CHANGED:
                return new ChartRefreshBudget(false, false, true, event.isOverlayChanged(), event.isOverlayChanged());
            case UI_STATE_CHANGED:
                return new ChartRefreshBudget(true, false, false, false, false);
            case DIALOG_STATE_CHANGED:
                return new ChartRefreshBudget(false, true, false, false, false);
            default:
                return new ChartRefreshBudget(false, false, false, false, false);
        }
    }

    public boolean needsUiStateBind() {
        return needsUiStateBind;
    }

    public boolean needsDialogBind() {
        return needsDialogBind;
    }

    public boolean needsSummaryBind() {
        return needsSummaryBind;
    }

    public boolean needsOverlayRebuild() {
        return needsOverlayRebuild;
    }

    public boolean needsRealtimeTailInvalidate() {
        return needsRealtimeTailInvalidate;
    }
}

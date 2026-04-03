/*
 * K 线各子图文本避让辅助，负责统一标题与纵轴文字的安全留白。
 * 与 KlineChartView 配合，减少主图和附图共享边界时的文字重叠。
 */
package com.binance.monitor.ui.chart;

final class KlinePaneTextLayoutHelper {
    private static final float INDICATOR_PLOT_TOP_INSET_DP = 18f;
    private static final float INDICATOR_PLOT_BOTTOM_INSET_DP = 6f;
    private static final float PANE_TITLE_BASELINE_OFFSET_DP = 12f;
    private static final float AXIS_TOP_BASELINE_OFFSET_DP = 12f;
    private static final float AXIS_BOTTOM_INSET_DP = 6f;

    private KlinePaneTextLayoutHelper() {
    }

    static float resolveIndicatorPlotTopInsetDp() {
        return INDICATOR_PLOT_TOP_INSET_DP;
    }

    static float resolveIndicatorPlotBottomInsetDp() {
        return INDICATOR_PLOT_BOTTOM_INSET_DP;
    }

    static float resolvePaneTitleBaselineOffsetDp() {
        return PANE_TITLE_BASELINE_OFFSET_DP;
    }

    static float resolveAxisTopBaselineOffsetDp() {
        return AXIS_TOP_BASELINE_OFFSET_DP;
    }

    static float resolveAxisBottomInsetDp() {
        return AXIS_BOTTOM_INSET_DP;
    }
}

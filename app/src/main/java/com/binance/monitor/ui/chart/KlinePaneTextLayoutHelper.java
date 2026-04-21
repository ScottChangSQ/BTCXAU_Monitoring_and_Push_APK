/*
 * K 线各子图文本避让辅助，负责统一标题与纵轴文字的安全留白。
 * 与 KlineChartView 配合，减少主图和附图共享边界时的文字重叠。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.DimenRes;

import com.binance.monitor.R;

final class KlinePaneTextLayoutHelper {
    private KlinePaneTextLayoutHelper() {
    }

    @DimenRes
    static int resolveIndicatorPlotTopInsetRes() {
        return R.dimen.kline_indicator_plot_top_inset;
    }

    @DimenRes
    static int resolveIndicatorPlotBottomInsetRes() {
        return R.dimen.kline_indicator_plot_bottom_inset;
    }

    @DimenRes
    static int resolvePaneTitleBaselineOffsetRes() {
        return R.dimen.kline_pane_title_baseline_offset;
    }

    @DimenRes
    static int resolveAxisTopBaselineOffsetRes() {
        return R.dimen.kline_axis_top_baseline_offset;
    }

    @DimenRes
    static int resolveAxisBottomInsetRes() {
        return R.dimen.kline_axis_bottom_inset;
    }
}

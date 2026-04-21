/*
 * 账户曲线区横向布局辅助，负责统一主图与附图的左右绘图区边界以及主图线条粗细。
 * 供净值/结余主图、仓位比例、回撤和日收益四张图共用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.DimenRes;

import com.binance.monitor.R;

final class CurvePaneLayoutHelper {

    private CurvePaneLayoutHelper() {
    }

    // 统一四张图的左侧绘图区起点。
    @DimenRes
    static int resolveChartLeftInsetRes() {
        return R.dimen.curve_chart_left_inset;
    }

    // 统一四张图的右侧绘图区终点留白。
    @DimenRes
    static int resolveChartRightInsetRes() {
        return R.dimen.curve_chart_right_inset;
    }

    // 统一四张图横纵坐标轴的线宽。
    @DimenRes
    static int resolveAxisStrokeRes() {
        return R.dimen.curve_axis_stroke;
    }

    // 净值实线线宽。
    @DimenRes
    static int resolveEquityStrokeRes() {
        return R.dimen.curve_equity_stroke;
    }

    // 结余虚线线宽。
    @DimenRes
    static int resolveBalanceStrokeRes() {
        return R.dimen.curve_balance_stroke;
    }
}

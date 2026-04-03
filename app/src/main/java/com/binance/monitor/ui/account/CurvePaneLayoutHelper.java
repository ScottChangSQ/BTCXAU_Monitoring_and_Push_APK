/*
 * 账户曲线区横向布局辅助，负责统一主图与附图的左右绘图区边界以及主图线条粗细。
 * 供净值/结余主图、仓位比例、回撤和日收益四张图共用。
 */
package com.binance.monitor.ui.account;

final class CurvePaneLayoutHelper {

    private CurvePaneLayoutHelper() {
    }

    // 统一四张图的左侧绘图区起点。
    static float resolveChartLeftDp() {
        return 34f;
    }

    // 统一四张图的右侧绘图区终点留白。
    static float resolveChartRightInsetDp() {
        return 28f;
    }

    // 净值实线线宽。
    static float resolveEquityStrokeDp() {
        return 1.6f;
    }

    // 结余虚线线宽。
    static float resolveBalanceStrokeDp() {
        return 1.3f;
    }
}

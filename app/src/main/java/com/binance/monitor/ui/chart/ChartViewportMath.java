/*
 * 图表视口数学工具，负责统一右侧留白与最新 K 线越界判断。
 * KlineChartView 通过这里保持“最新 K 线留白，但旧 K 线能滑过右轴”的行为。
 */
package com.binance.monitor.ui.chart;

public final class ChartViewportMath {

    private static final float DEFAULT_DOCKING_SLOTS = 12.5f;

    private ChartViewportMath() {
    }

    // 返回默认右侧留白槽位数，供默认停靠位和测试复用。
    public static float resolveDefaultRightBlankSlots() {
        return DEFAULT_DOCKING_SLOTS;
    }

    // 把右侧留白槽位计入可视结束索引，让最新 K 线默认停靠在留白左侧。
    public static float resolveVisibleEndFloat(int candleCount, float offsetCandles, float rightBlankSlots) {
        return Math.max(0f, candleCount - 1f + offsetCandles + Math.max(0f, rightBlankSlots));
    }

    // 按“右轴停靠带”投影 K 线中心点，让最新 K 线默认留白，旧 K 线继续向右滑出边界。
    public static float projectCandleCenterX(float priceRectRight,
                                             float slotWidth,
                                             float visibleEndFloat,
                                             float indexFloat) {
        return priceRectRight + (visibleEndFloat - indexFloat - DEFAULT_DOCKING_SLOTS) * slotWidth;
    }

    // 判断 K 线中心是否已经穿过右轴，只有这时才算真正越界。
    public static boolean isOutOfBounds(float candleCenterX, float priceRectRight, float tolerancePx) {
        return candleCenterX > priceRectRight + tolerancePx;
    }
}

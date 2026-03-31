/*
 * 图表视口计算工具，负责处理右侧留白和拖动边界的纯数学逻辑。
 */
package com.binance.monitor.ui.chart;

public final class KlineViewportHelper {

    private KlineViewportHelper() {
    }

    // 右侧留白会随着横向拖动逐步被消耗，直到归零。
    public static float resolveDynamicRightBlankWidth(float pricePaneWidth,
                                                      float rightBlankRatio,
                                                      float offsetCandles,
                                                      float slotWidth) {
        float baseBlankWidth = Math.max(0f, pricePaneWidth * Math.max(0f, rightBlankRatio));
        float safeSlotWidth = Math.max(1f, slotWidth);
        float consumedWidth = Math.max(0f, offsetCandles) * safeSlotWidth;
        return Math.max(0f, baseBlankWidth - consumedWidth);
    }

    // 最大偏移按完整绘图区宽度计算，这样历史 K 线可以继续滑入右侧坐标区。
    public static float resolveMaxOffset(int candleCount, float pricePaneWidth, float slotWidth) {
        float safeSlotWidth = Math.max(1f, slotWidth);
        float safePaneWidth = Math.max(20f, pricePaneWidth);
        float visibleCount = safePaneWidth / safeSlotWidth;
        return Math.max(0f, candleCount - visibleCount);
    }
}

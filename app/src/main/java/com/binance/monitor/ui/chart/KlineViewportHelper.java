/*
 * 图表视口计算工具，负责处理右侧留白和拖动边界的纯数学逻辑。
 */
package com.binance.monitor.ui.chart;

public final class KlineViewportHelper {

    private KlineViewportHelper() {
    }

    // 右侧留白保持固定宽度，历史 K 线向右滑动时会先进入留白区，再越过右轴。
    public static float resolveDynamicRightBlankWidth(float pricePaneWidth,
                                                      float rightBlankRatio,
                                                      float offsetCandles,
                                                      float slotWidth) {
        return Math.max(0f, pricePaneWidth * Math.max(0f, rightBlankRatio));
    }

    // 最大偏移按完整绘图区宽度计算，这样历史 K 线可以继续滑入右侧坐标区。
    public static float resolveMaxOffset(int candleCount, float pricePaneWidth, float slotWidth) {
        float safeSlotWidth = Math.max(1f, slotWidth);
        float safePaneWidth = Math.max(20f, pricePaneWidth);
        float visibleCount = safePaneWidth / safeSlotWidth;
        return Math.max(0f, candleCount - visibleCount);
    }

    // 右侧留白区域内的 K 线仍然应该参与绘制，避免在到达右轴前提前消失。
    public static int resolveVisibleRenderEndIndex(int candleCount,
                                                   float visibleEndFloat,
                                                   float rightBlankSlots) {
        if (candleCount <= 0) {
            return -1;
        }
        float safeBlankSlots = Math.max(0f, rightBlankSlots);
        return Math.min(candleCount - 1, (int) Math.ceil(visibleEndFloat + safeBlankSlots + 2f));
    }

    // 只有视口仍贴着最新K线时，自动刷新才继续跟随到最右侧。
    public static boolean shouldFollowLatestOnAutoRefresh(float offsetCandles) {
        return Math.max(0f, offsetCandles) <= 0.25f;
    }
}

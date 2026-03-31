/*
 * 验证图表右侧留白会随着拖动逐步减少，避免历史 K 线过早消失。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KlineViewportHelperTest {

    // 拖动越多，右侧留白越少；超过阈值后应归零。
    @Test
    public void resolveDynamicRightBlankWidthShrinksWithOffset() {
        assertEquals(100f, KlineViewportHelper.resolveDynamicRightBlankWidth(700f, 1f / 7f, 0f, 10f), 0.0001f);
        assertEquals(60f, KlineViewportHelper.resolveDynamicRightBlankWidth(700f, 1f / 7f, 4f, 10f), 0.0001f);
        assertEquals(0f, KlineViewportHelper.resolveDynamicRightBlankWidth(700f, 1f / 7f, 12f, 10f), 0.0001f);
    }

    // 最大偏移应按完整绘图区计算，而不是按缩短后的绘图区计算。
    @Test
    public void resolveMaxOffsetUsesFullPricePaneWidth() {
        assertEquals(30f, KlineViewportHelper.resolveMaxOffset(100, 700f, 10f), 0.0001f);
    }
}

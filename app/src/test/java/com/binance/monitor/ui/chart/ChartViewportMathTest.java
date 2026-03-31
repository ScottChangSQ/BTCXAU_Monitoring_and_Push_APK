/*
 * 验证图表右侧留白只影响最新 K 线停靠位置，不影响旧 K 线继续滑到右侧坐标轴后再隐藏。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChartViewportMathTest {

    @Test
    public void resolveVisibleEndFloatIncludesRightBlankSlots() {
        assertEquals(105f, ChartViewportMath.resolveVisibleEndFloat(100, 0f, 6f), 0.0001f);
    }

    @Test
    public void projectLatestCandleLeavesRightBlankAtZeroOffset() {
        float x = ChartViewportMath.projectCandleCenterX(700f, 10f, 105f, 99f);

        assertEquals(635f, x, 0.0001f);
        assertFalse(ChartViewportMath.isOutOfBounds(x, 700f, 0.2f));
    }

    @Test
    public void latestCandleIsOutOfBoundsOnlyAfterCrossingAxis() {
        float x = ChartViewportMath.projectCandleCenterX(700f, 10f, 112f, 99f);

        assertTrue(ChartViewportMath.isOutOfBounds(x, 700f, 0.2f));
    }
}

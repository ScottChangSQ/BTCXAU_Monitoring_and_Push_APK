/*
 * 验证历史成交标记在窗口外时仍保留真实时间投影，不再被强行贴到左右边界。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HistoricalTradeViewportHelperTest {

    @Test
    public void resolveOverflowRawIndexProjectsBeforeFirstCandle() {
        float rawIndex = HistoricalTradeViewportHelper.resolveOverflowRawIndex(
                880_000L,
                1_000_000L,
                1_120_000L,
                2,
                60_000L
        );

        assertTrue(rawIndex < 0f);
    }

    @Test
    public void resolveOverflowRawIndexProjectsAfterLastCandle() {
        float rawIndex = HistoricalTradeViewportHelper.resolveOverflowRawIndex(
                1_180_000L,
                1_000_000L,
                1_120_000L,
                2,
                60_000L
        );

        assertTrue(rawIndex > 2f);
    }

    @Test
    public void resolveTimeInsideBucketRawIndexShouldKeepLastCandleTradeInsideVisibleSlot() {
        float rawIndex = HistoricalTradeViewportHelper.resolveTimeInsideBucketRawIndex(
                2,
                1_120_000L,
                1_180_000L,
                1_179_000L
        );

        assertTrue(rawIndex > 2f);
        assertTrue(rawIndex < 2.5f);
    }

    @Test
    public void pointOutsideViewportShouldNotBeVisible() {
        assertFalse(HistoricalTradeViewportHelper.isPointVisible(95f, 100f, 300f));
        assertFalse(HistoricalTradeViewportHelper.isPointVisible(305f, 100f, 300f));
        assertTrue(HistoricalTradeViewportHelper.isPointVisible(180f, 100f, 300f));
    }

    @Test
    public void segmentOutsideViewportShouldNotBeVisible() {
        assertFalse(HistoricalTradeViewportHelper.isSegmentVisible(20f, 80f, 100f, 300f));
        assertFalse(HistoricalTradeViewportHelper.isSegmentVisible(320f, 380f, 100f, 300f));
        assertTrue(HistoricalTradeViewportHelper.isSegmentVisible(80f, 160f, 100f, 300f));
    }
}

/*
 * K 线视口工具测试，确保右侧留白在向右查看历史时不会被提前吞掉。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KlineViewportHelperTest {

    @Test
    public void resolveDynamicRightBlankWidthShouldKeepBaseBlankWhenDraggingRight() {
        float width = KlineViewportHelper.resolveDynamicRightBlankWidth(300f, 0.2f, 5f, 10f);

        assertEquals(60f, width, 0.0001f);
    }

    @Test
    public void resolveVisibleRenderEndIndexShouldKeepLatestCandleVisibleInsideRightBlank() {
        int endIndex = KlineViewportHelper.resolveVisibleRenderEndIndex(100, 93f, 6.5f);

        assertEquals(99, endIndex);
    }
}

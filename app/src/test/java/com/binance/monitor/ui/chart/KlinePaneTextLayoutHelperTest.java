/*
 * K 线子图文本避让测试，确保标题与纵轴文字有足够留白，不再压到线条和共享边界。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KlinePaneTextLayoutHelperTest {

    @Test
    public void indicatorPlotInsetsShouldReserveEnoughSpaceForPaneTitle() {
        assertTrue(KlinePaneTextLayoutHelper.resolveIndicatorPlotTopInsetDp() >= 18f);
        assertTrue(KlinePaneTextLayoutHelper.resolveIndicatorPlotBottomInsetDp() >= 6f);
    }

    @Test
    public void paneTextBaselinesShouldStayAwayFromSharedBoundary() {
        assertTrue(KlinePaneTextLayoutHelper.resolvePaneTitleBaselineOffsetDp() >= 12f);
        assertTrue(KlinePaneTextLayoutHelper.resolveAxisTopBaselineOffsetDp() >= 12f);
        assertTrue(KlinePaneTextLayoutHelper.resolveAxisBottomInsetDp() >= 6f);
    }
}

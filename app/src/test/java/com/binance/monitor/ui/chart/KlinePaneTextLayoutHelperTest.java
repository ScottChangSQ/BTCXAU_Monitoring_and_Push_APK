/*
 * K 线子图文本避让测试，确保标题与纵轴文字有足够留白，不再压到线条和共享边界。
 */
package com.binance.monitor.ui.chart;

import com.binance.monitor.R;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KlinePaneTextLayoutHelperTest {

    @Test
    public void indicatorPlotInsetsShouldReserveEnoughSpaceForPaneTitle() {
        assertEquals(R.dimen.kline_indicator_plot_top_inset, KlinePaneTextLayoutHelper.resolveIndicatorPlotTopInsetRes());
        assertEquals(R.dimen.kline_indicator_plot_bottom_inset, KlinePaneTextLayoutHelper.resolveIndicatorPlotBottomInsetRes());
    }

    @Test
    public void paneTextBaselinesShouldStayAwayFromSharedBoundary() {
        assertEquals(R.dimen.kline_pane_title_baseline_offset, KlinePaneTextLayoutHelper.resolvePaneTitleBaselineOffsetRes());
        assertEquals(R.dimen.kline_axis_top_baseline_offset, KlinePaneTextLayoutHelper.resolveAxisTopBaselineOffsetRes());
        assertEquals(R.dimen.kline_axis_bottom_inset, KlinePaneTextLayoutHelper.resolveAxisBottomInsetRes());
    }
}

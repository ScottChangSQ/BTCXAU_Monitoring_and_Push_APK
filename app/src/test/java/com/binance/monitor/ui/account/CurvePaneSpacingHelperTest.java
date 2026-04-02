/*
 * 账户统计曲线区子图间距测试，确保合并布局时内部留白被正确压缩。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CurvePaneSpacingHelperTest {

    @Test
    public void resolveTopInsetPxShouldDropInsetWhenMergedWithPreviousPane() {
        assertEquals(0f, CurvePaneSpacingHelper.resolveTopInsetPx(true, 12f), 0.0001f);
        assertEquals(12f, CurvePaneSpacingHelper.resolveTopInsetPx(false, 12f), 0.0001f);
    }

    @Test
    public void resolveBottomInsetPxShouldPreferTimeAxisAreaOverMergedSpacing() {
        assertEquals(24f, CurvePaneSpacingHelper.resolveBottomInsetPx(true, true, 10f, 24f), 0.0001f);
        assertEquals(0f, CurvePaneSpacingHelper.resolveBottomInsetPx(true, false, 10f, 24f), 0.0001f);
        assertEquals(10f, CurvePaneSpacingHelper.resolveBottomInsetPx(false, false, 10f, 24f), 0.0001f);
    }

    @Test
    public void resolveBottomLabelBaselineShouldMoveMergedPaneLabelsInsideFrame() {
        assertEquals(98f, CurvePaneSpacingHelper.resolveBottomLabelBaseline(100f, true, 2f), 0.0001f);
        assertEquals(102f, CurvePaneSpacingHelper.resolveBottomLabelBaseline(100f, false, 2f), 0.0001f);
    }
}

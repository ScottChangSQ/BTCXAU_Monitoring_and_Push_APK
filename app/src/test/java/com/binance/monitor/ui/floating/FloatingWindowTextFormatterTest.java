/*
 * 悬浮窗文案格式测试，确保产品盈亏改成“产品（盈亏）”的一体化显示。
 */
package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FloatingWindowTextFormatterTest {

    @Test
    public void formatCardTitleShouldCombineAssetAndPnl() {
        String title = FloatingWindowTextFormatter.formatCardTitle("BTC", -1250d, false);

        assertEquals("BTC（-$1,250）", title);
    }

    @Test
    public void formatCardTitleShouldMaskPnlOnly() {
        String title = FloatingWindowTextFormatter.formatCardTitle("XAU", 88d, true);

        assertEquals("XAU（*）", title);
    }

    @Test
    public void formatCardTitleShouldShowDashWhenPnlIsZero() {
        String title = FloatingWindowTextFormatter.formatCardTitle("BTC", 0d, false);

        assertEquals("BTC（$-）", title);
    }

    @Test
    public void formatPnlAmountShouldShowDashWhenSummaryPnlIsZero() {
        assertEquals("$-", FloatingWindowTextFormatter.formatPnlAmount(0d, false));
    }

    @Test
    public void shouldUseNeutralStyleWhenPnlIsZero() {
        assertEquals(true, FloatingWindowTextFormatter.shouldUseNeutralPnlStyle(0d));
        assertEquals(true, FloatingWindowTextFormatter.shouldUseNeutralPnlStyle(1e-10));
        assertEquals(false, FloatingWindowTextFormatter.shouldUseNeutralPnlStyle(1d));
    }

    @Test
    public void formatVolumeLineShouldUseOneMinuteLabelAndUnit() {
        assertEquals("1M量 12.35 BTC",
                FloatingWindowTextFormatter.formatVolumeLine(12.345d, "BTC", false));
    }

    @Test
    public void formatAmountLineShouldUseOneMinuteLabel() {
        assertEquals("1M额 1.23M$",
                FloatingWindowTextFormatter.formatAmountLine(1_234_567d, false));
    }
}

/*
 * 悬浮窗文案格式测试，确保产品卡片标题只保留产品名和手数主信息。
 */
package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FloatingWindowTextFormatterTest {

    @Test
    public void formatCardTitleShouldOnlyKeepAssetAndLots() {
        String title = FloatingWindowTextFormatter.formatCardTitle("BTC", -0.10d, -1250d, true, false);

        assertEquals("BTC | -0.10手", title);
    }

    @Test
    public void formatCardTitleShouldIgnoreMaskWhenOnlyShowingTitleLine() {
        String title = FloatingWindowTextFormatter.formatCardTitle("XAU", 0.05d, 88d, true, true);

        assertEquals("XAU | +0.05手", title);
    }

    @Test
    public void formatCardTitleShouldOnlyShowAssetWhenThereIsNoPosition() {
        String title = FloatingWindowTextFormatter.formatCardTitle("BTC", 0d, 0d, false, false);

        assertEquals("BTC", title);
    }

    @Test
    public void formatCardTitleShouldKeepSignedLotsWithoutPnlLine() {
        String title = FloatingWindowTextFormatter.formatCardTitle("BTC", 0.03d, -28d, true, false);

        assertEquals("BTC | +0.03手", title);
    }

    @Test
    public void formatLotsTextShouldUseSignedTwoDecimalLots() {
        assertEquals("+0.03手", FloatingWindowTextFormatter.formatLotsText(0.03d));
        assertEquals("-0.10手", FloatingWindowTextFormatter.formatLotsText(-0.10d));
    }

    @Test
    public void formatPnlAmountShouldShowDashWhenSummaryPnlIsZero() {
        assertEquals("$-", FloatingWindowTextFormatter.formatPnlAmount(0d, false));
    }

    @Test
    public void formatPnlAmountShouldKeepOneDecimal() {
        assertEquals("+$12.3", FloatingWindowTextFormatter.formatPnlAmount(12.34d, false));
    }

    @Test
    public void formatPriceTextShouldKeepOneDecimal() {
        assertEquals("$ 45,678.9", FloatingWindowTextFormatter.formatPriceText(45678.94d, false));
    }

    @Test
    public void formatMiniStatusTextShouldShowNoPositionWhenThereIsNoActivePosition() {
        assertEquals("无持仓", FloatingWindowTextFormatter.formatMiniStatusText(false, false, 0d, false));
    }

    @Test
    public void formatMiniStatusTextShouldKeepOfflineAndMaskedRules() {
        assertEquals("离线", FloatingWindowTextFormatter.formatMiniStatusText(true, false, 0d, false));
        assertEquals("*", FloatingWindowTextFormatter.formatMiniStatusText(false, true, 12.3d, true));
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
        assertEquals("1M额 123.46万$",
                FloatingWindowTextFormatter.formatAmountLine(1_234_567d, false));
    }
}

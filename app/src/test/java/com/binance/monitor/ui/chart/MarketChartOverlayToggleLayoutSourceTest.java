package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartOverlayToggleLayoutSourceTest {

    @Test
    public void overlayToggleGroupShouldUseSingleStripInsteadOfManualFloatingPlacement() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml");

        assertTrue(source.contains("binding.btnToggleHistoryTrades.setOnClickListener(v -> pageRuntime.toggleHistoryTradeVisibility());"));
        assertTrue(source.contains("binding.btnTogglePositionOverlays.setOnClickListener(v -> pageRuntime.togglePositionOverlayVisibility());"));
        assertTrue(source.contains("styleOverlayToggleOption(binding.btnToggleHistoryTrades, showHistoryTrades);"));
        assertTrue(source.contains("styleOverlayToggleOption(binding.btnTogglePositionOverlays, showPositionOverlays);"));
        assertTrue(layoutSource.indexOf("@+id/tvChartState") < layoutSource.indexOf("@+id/layoutChartOverlayToggleGroup"));
        assertTrue(layoutSource.indexOf("@+id/layoutChartOverlayToggleGroup") < layoutSource.indexOf("@+id/layoutIndicatorStrip"));
        assertFalse(source.contains("updateHistoryTradeButtonPosition()"));
        assertFalse(source.contains("updatePositionOverlayButtonPosition()"));
        assertFalse(source.contains("KlineOverlayButtonLayoutHelper.resolveBottomLeftInlineButtonPosition("));
        assertTrue(source.contains("private boolean showHistoryTrades = false;"));
        assertTrue(source.contains("showHistoryTrades = preferences.getBoolean(PREF_KEY_SHOW_HISTORY_TRADES, false);"));
    }

    @Test
    public void tradingPageShouldRemoveLegacyInfoStripBindings() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String snapshotSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshot.java");

        assertTrue(screenSource.contains("binding.tvChartPositionSummary.setText(\"盈亏：-- | 持仓：--\");"));
        assertTrue(screenSource.contains("binding.tvChartPositionSummary.setText(\"盈亏：**** | 持仓：****\");"));
        assertTrue(screenSource.contains("IndicatorPresentationPolicy.buildDirectionalSpanAfterAnchor("));
        assertTrue(snapshotSource.contains("return empty(\"盈亏：-- | 持仓：--\", \"更新时间 --\", \"\");"));
        assertFalse(snapshotSource.contains("持仓盈亏: -- | 持仓收益率: --"));
        assertFalse(activitySource.contains("binding.tvChartRefreshCountdown"));
        assertFalse(screenSource.contains("binding.tvChartRefreshCountdown"));
    }

    @Test
    public void positionSummaryShouldAnchorToPricePaneLeftEdge() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml");

        assertTrue(screenSource.contains("int targetLeft = Math.max(0, pricePaneLeftPx);"));
        assertTrue(screenSource.contains("int targetBaseline = pricePaneTopPx + binding.klineChartView.getPricePaneTitleBaselineOffsetPx();"));
        assertTrue(screenSource.contains("int targetTop = Math.max(0, targetBaseline - baseline);"));
        assertTrue(screenSource.contains("int targetWidth = Math.max(0, pricePaneRightPx - pricePaneLeftPx - inset);"));
        assertTrue(screenSource.contains("params.width = targetWidth;"));
        assertTrue(screenSource.contains("params.gravity = targetGravity;"));
        assertTrue(layoutSource.contains("android:gravity=\"start\""));
        assertTrue(layoutSource.contains("android:textAlignment=\"viewStart\""));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

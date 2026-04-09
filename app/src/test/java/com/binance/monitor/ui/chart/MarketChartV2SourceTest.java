package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartV2SourceTest {

    @Test
    public void chartActivityShouldDependOnGatewayV2Client() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("GatewayV2Client"));
        assertTrue(source.contains("latestPatch"));
        assertTrue(source.contains("fetchMarketSeriesBefore("));
        assertTrue(source.contains("fetchMarketSeriesAfter("));
        assertFalse(source.contains("BinanceApiClient"));
        assertFalse(source.contains("fetchChartKlineHistoryBefore("));
        assertFalse(source.contains("fetchKlineHistoryAfter("));
        assertFalse(source.contains("fetchChartKlineFullWindow("));
        assertFalse(source.contains("mergeRealtimeTailIntoSeries("));
        assertFalse(source.contains("ensureMinuteBaseCacheAsync("));
        assertFalse(source.contains("getLatestClosedKlines().observe"));
    }

    @Test
    public void chartActivityShouldStartServiceOnCreateAndAvoidResumeBootstrap() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("ensureMonitorServiceStarted();"));
        assertTrue(source.contains("private void ensureMonitorServiceStarted()"));
        assertTrue(source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();"));
        assertTrue(source.contains("private void enterChartScreen(boolean coldStart)"));
        assertFalse(source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();\n        applyPaletteStyles();\n        applyPrivacyMaskState();\n        if (gatewayV2Client != null) {\n            gatewayV2Client.resetTransport();"));
        assertFalse(source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();\n        applyPaletteStyles();\n        applyPrivacyMaskState();\n        if (gatewayV2TradeClient != null) {\n            gatewayV2TradeClient.resetTransport();"));
        assertFalse(source.contains("protected void onResume() {\n        super.onResume();\n        sendServiceAction(AppConstants.ACTION_BOOTSTRAP);"));
    }

    @Test
    public void historicalTradePopupShouldShowFullDateTime() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("\"开仓 \" + FormatUtils.formatDateTime(item.openTimeMs)"));
        assertTrue(source.contains("\"平仓 \" + FormatUtils.formatDateTime(item.closeTimeMs)"));
        assertFalse(source.contains("\"开仓 \" + FormatUtils.formatTime(item.openTimeMs)"));
        assertFalse(source.contains("\"平仓 \" + FormatUtils.formatTime(item.closeTimeMs)"));
    }

    @Test
    public void yearAggregationShouldUseUtcCalendar() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("Calendar.getInstance(TimeZone.getTimeZone(\"UTC\"))"));
    }

    @Test
    public void chartTradeMatchingShouldOnlyUseCanonicalCode() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("String normalizedCode = MarketChartTradeSupport.toTradeSymbol(code);"));
        assertFalse(source.contains("String normalizedName = MarketChartTradeSupport.toTradeSymbol(productName);"));
    }
}

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
        String runtimeSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("MonitorServiceController.ensureStarted(this);"));
        assertFalse(source.contains("private void ensureMonitorServiceStarted()"));
        assertTrue(source.contains("protected void onResume() {\n        super.onResume();\n        if (pageController != null) {\n            pageController.onPageShown();\n        }\n    }"));
        assertTrue(runtimeSource.contains("public void onPageShown() {\n        MonitorServiceController.ensureStarted(host.requireActivity());"));
        assertTrue(runtimeSource.contains("private void enterChartScreen(boolean coldStart)"));
        assertFalse(source.contains("private void enterChartScreen(boolean coldStart)"));
        assertFalse(runtimeSource.contains("public void onPageShown() {\n        MonitorServiceController.ensureStarted(host.requireActivity());\n        applyPagePalette();\n        applyPrivacyMaskState();\n        if (gatewayV2Client != null) {\n            gatewayV2Client.resetTransport();"));
        assertFalse(runtimeSource.contains("public void onPageShown() {\n        MonitorServiceController.ensureStarted(host.requireActivity());\n        applyPagePalette();\n        applyPrivacyMaskState();\n        if (gatewayV2TradeClient != null) {\n            gatewayV2TradeClient.resetTransport();"));
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

    @Test
    public void chartActivityShouldDelegateHeavyDataChainToCoordinator() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("private MarketChartDataCoordinator dataCoordinator;"));
        assertTrue(activitySource.contains("MarketChartDataHostDelegate"));
        assertTrue(activitySource.contains("dataCoordinator = new MarketChartDataCoordinator("));
        assertTrue(activitySource.contains("new MarketChartDataHostDelegate("));
        assertTrue(activitySource.contains("dataCoordinator.requestKlines(true, false);"));
        assertTrue(activitySource.contains("dataCoordinator.requestKlines(false, true);"));
        assertTrue(activitySource.contains("dataCoordinator.requestMoreHistory(beforeOpenTime);"));
        assertFalse(activitySource.contains("private void requestMoreHistory(long beforeOpenTime)"));
        assertTrue(activitySource.contains("dataCoordinator.observeRealtimeDisplayKlines();"));
        assertTrue(activitySource.contains("dataCoordinator.refreshChartOverlays();"));
        assertTrue(activitySource.contains("dataCoordinator.restoreChartOverlayFromLatestCacheOrEmpty();"));
        assertFalse(activitySource.contains("private void observeRealtimeDisplayKlines()"));
        assertFalse(activitySource.contains("private void refreshChartOverlays()"));
        assertFalse(activitySource.contains("private void restoreChartOverlayFromLatestCacheOrEmpty()"));
        assertTrue(coordinatorSource.contains("final class MarketChartDataCoordinator"));
        assertTrue(coordinatorSource.contains("void requestKlines(boolean allowCancelRunning, boolean autoRefresh)"));
        assertTrue(coordinatorSource.contains("void requestMoreHistory(long beforeOpenTime)"));
        assertTrue(coordinatorSource.contains("void observeRealtimeDisplayKlines()"));
        assertTrue(coordinatorSource.contains("void refreshChartOverlays()"));
        assertTrue(coordinatorSource.contains("void restoreChartOverlayFromLatestCacheOrEmpty()"));
    }

    @Test
    public void chartActivityShouldMoveRequestAndLoadMoreCoreIntoDataCoordinator() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(activitySource.contains("private void requestKlines()"));
        assertFalse(activitySource.contains("private void requestKlines(boolean allowCancelRunning, boolean autoRefresh)"));
        assertFalse(activitySource.contains("private void requestKlinesCore(boolean allowCancelRunning, boolean autoRefresh)"));
        assertFalse(activitySource.contains("private void requestMoreHistory(long beforeOpenTime)"));
        assertFalse(activitySource.contains("private void requestMoreHistoryCore(long beforeOpenTime)"));
        assertTrue(coordinatorSource.contains("private void requestKlinesCore(boolean allowCancelRunning, boolean autoRefresh)"));
        assertTrue(coordinatorSource.contains("private void requestMoreHistoryCore(long beforeOpenTime)"));
        assertTrue(coordinatorSource.contains("applyRequestStartState(autoRefresh);"));
        assertTrue(coordinatorSource.contains("loadCandlesForRequest("));
        assertTrue(coordinatorSource.contains("applyLoadMoreSuccessState(reqSymbol, reqInterval, older);"));
        assertTrue(coordinatorSource.contains("finishLoadMoreState();"));
    }
}

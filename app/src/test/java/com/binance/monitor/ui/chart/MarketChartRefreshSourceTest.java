package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartRefreshSourceTest {

    @Test
    public void activityShouldReuseLoadedCandlesForReadOnlyRefreshChecks() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("? loadedCandles"));
        assertFalse(source.contains("? new ArrayList<>(loadedCandles)\r\n                : getCachedOrPersisted(key);"));
        assertFalse(source.contains("List<CandleEntry> visible = loadedCandles == null ? new ArrayList<>() : new ArrayList<>(loadedCandles);"));
    }

    @Test
    public void activityShouldDebounceAccountOverlayRefreshes() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        Path runtimeFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8);
        String runtimeSource = new String(Files.readAllBytes(runtimeFile), StandardCharsets.UTF_8);

        assertTrue(runtimeSource.contains("scheduleChartOverlayRefresh"));
        assertTrue(runtimeSource.contains("accountOverlayRefreshPending"));
        assertTrue(activitySource.contains("lastAccountOverlaySignature"));
        assertFalse(activitySource.contains("AccountSnapshotDisplayResolver.resolve("));
        assertFalse(activitySource.contains("ChartHistoricalTradeSourceResolver.resolve("));
    }

    @Test
    public void activityShouldNotBuildFallbackAnnotationGroupIds() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertFalse(source.contains("\"|fallback|\""));
    }

    @Test
    public void activityShouldNotGuessAnnotationAnchorsOrSymbolMatches() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertFalse(source.contains("findTradeOpenTimeByCodeAndSide("));
        assertFalse(source.contains("resolveLatestCandleOpenTime()"));
        assertFalse(source.contains("normalizedCode.contains(value)"));
        assertFalse(source.contains("normalizedName.contains(value)"));
        assertTrue(source.contains("MarketChartTradeSupport.toTradeSymbol(selectedSymbol)"));
        assertTrue(source.contains("if (anchorTime <= 0L) {"));
    }
}

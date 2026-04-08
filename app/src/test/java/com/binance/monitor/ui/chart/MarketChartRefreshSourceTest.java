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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("scheduleChartOverlayRefresh"));
        assertTrue(source.contains("accountOverlayRefreshPending"));
        assertTrue(source.contains("lastAccountOverlaySignature"));
        assertFalse(source.contains("AccountSnapshotDisplayResolver.resolve("));
        assertFalse(source.contains("ChartHistoricalTradeSourceResolver.resolve("));
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

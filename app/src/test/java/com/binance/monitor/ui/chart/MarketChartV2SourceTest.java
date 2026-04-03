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
}

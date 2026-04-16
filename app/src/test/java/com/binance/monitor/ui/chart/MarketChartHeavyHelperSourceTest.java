package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartHeavyHelperSourceTest {

    @Test
    public void chartActivityShouldMoveHeavySeriesAndRealtimeHelpersOutOfActivity() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartSeriesLoaderHelper.java")));
        assertTrue(Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java")));
        assertTrue(activitySource.contains("private MarketChartSeriesLoaderHelper chartSeriesLoaderHelper;"));
        assertTrue(activitySource.contains("private MarketChartRealtimeTailHelper realtimeTailHelper;"));
        assertFalse(activitySource.contains("private List<CandleEntry> mergeRealtimeMinuteCache("));
        assertFalse(activitySource.contains("private List<CandleEntry> buildRealtimeDisplayCandles("));
        assertFalse(activitySource.contains("private List<CandleEntry> loadCandlesForRequest("));
        assertFalse(activitySource.contains("private List<CandleEntry> loadYearAggregateCandlesForRequest("));
        assertFalse(activitySource.contains("private List<CandleEntry> fetchV2FullSeries("));
        assertFalse(activitySource.contains("private List<CandleEntry> fetchV2SeriesBefore("));
        assertFalse(activitySource.contains("private List<CandleEntry> fetchV2SeriesAfter("));
        assertFalse(activitySource.contains("private List<CandleEntry> mergeMarketSeriesPayload("));
        assertFalse(activitySource.contains("private List<CandleEntry> aggregateToYear("));
    }
}

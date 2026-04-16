package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartGapFillSourceTest {

    @Test
    public void activityShouldUsePreviousOldestOpenTimeForGapBackfill() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        Path coordinatorFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8);
        String coordinatorSource = new String(Files.readAllBytes(coordinatorFile), StandardCharsets.UTF_8);

        assertTrue(coordinatorSource.contains("long previousOldestOpenTime = loadedCandles.isEmpty()"));
        assertTrue(activitySource.contains("loadCandlesForRequest("));
        assertTrue(coordinatorSource.contains("previousOldestOpenTime,"));
        assertTrue(activitySource.contains("ChartGapFillHelper.shouldBackfillOlderHistory("));
    }

    @Test
    public void activityShouldNotSkipResumeRefreshWhenVisibleSeriesNeedsRepair() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("boolean visibleSeriesNeedsRepair = compatibleVisible"));
        assertTrue(source.contains("hasRealtimeTailSourceForChart()"));
        assertTrue(source.contains("return !MarketChartRefreshHelper.shouldSkipRequestOnResume(\n                compatibleVisible,\n                freshVisibleWindow,\n                visibleSeriesNeedsRepair\n        );"));
    }
}

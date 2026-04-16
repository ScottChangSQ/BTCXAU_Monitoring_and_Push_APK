package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartLocalDisplaySourceTest {

    @Test
    public void activityShouldReuseSharedLocalDisplayApplyMethod() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8);
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java")
        ), StandardCharsets.UTF_8);

        assertTrue(activitySource.contains("private void applyLocalDisplayCandles("));
        assertTrue(coordinatorSource.contains("host.applyLocalDisplayCandles(key, cached);"));
        assertTrue(activitySource.contains("applyLocalDisplayCandles(key, persisted);"));
        assertTrue(activitySource.contains("binding.klineChartView.setCandles(loadedCandles);"));
        assertTrue(activitySource.contains("renderInfoWithLatest();"));
        assertTrue(activitySource.contains("dataCoordinator.refreshChartOverlays();"));
        assertTrue(activitySource.contains("updateStateCount();"));
    }

    @Test
    public void localRestoreShouldTrimPagedHistoryBackToDefaultWindowBeforeApplying() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String screenSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("List<CandleEntry> restoreWindow = ChartWindowSliceHelper.takeLatest(candles, RESTORE_WINDOW_LIMIT);"));
        assertTrue(activitySource.contains("applyDisplayCandles(key, restoreWindow, false, false, false);"));
        assertTrue(screenSource.contains("List<CandleEntry> restoreWindow = ChartWindowSliceHelper.takeLatest(candles, RESTORE_WINDOW_LIMIT);"));
        assertTrue(screenSource.contains("applyDisplayCandles(key, restoreWindow, false, false, false);"));
    }
}

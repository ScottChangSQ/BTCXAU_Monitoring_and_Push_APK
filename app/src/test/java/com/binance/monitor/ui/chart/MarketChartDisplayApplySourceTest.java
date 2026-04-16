package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartDisplayApplySourceTest {

    @Test
    public void activityShouldReuseSharedDisplayApplyCoreMethod() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        Path coordinatorFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(coordinatorFile), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("private void applyDisplayCandles("));
        assertTrue(activitySource.contains("applyDisplayCandles(key, restoreWindow, false, false, false);"));
        assertTrue(coordinatorSource.contains("host.applyDisplayCandles(key, displayUpdate.toDisplay, autoRefresh, displayUpdate.shouldFollowLatest, true);"));
        assertTrue(activitySource.contains("binding.klineChartView.setCandlesKeepingViewport(loadedCandles);"));
        assertTrue(activitySource.contains("if (shouldFollowLatest && !binding.klineChartView.hasActiveCrosshair()) {\n                binding.klineChartView.scrollToLatest();\n            }"));
    }
}

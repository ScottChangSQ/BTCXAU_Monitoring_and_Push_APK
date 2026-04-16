package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartLoadMoreStateSourceTest {

    @Test
    public void activityShouldReuseSharedLoadMoreStateMethods() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        Path coordinatorFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8);
        String coordinatorSource = new String(Files.readAllBytes(coordinatorFile), StandardCharsets.UTF_8);

        assertTrue(activitySource.contains("private void applyLoadMoreSuccessState("));
        assertTrue(activitySource.contains("private void finishLoadMoreState()"));
        assertTrue(coordinatorSource.contains("host.applyLoadMoreSuccessState(reqSymbol, reqInterval, older);"));
        assertTrue(coordinatorSource.contains("host.finishLoadMoreState();"));
    }
}

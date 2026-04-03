package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartRequestStateSourceTest {

    @Test
    public void activityShouldReuseSharedRequestSuccessAndFailureStateMethods() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private void applyRequestSuccessState("));
        assertTrue(source.contains("private void applyRequestFailureState("));
        assertTrue(source.contains("private void clearChartDisplayForEmptyState()"));
        assertTrue(source.contains("applyRequestSuccessState(autoRefresh, requestStartedAtMs);"));
        assertTrue(source.contains("applyRequestFailureState(autoRefresh, message);"));
    }
}

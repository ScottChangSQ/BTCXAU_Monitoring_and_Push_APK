package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartRequestStartSourceTest {

    @Test
    public void activityShouldReuseSharedRequestStartAndSkipStateMethods() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private void applyRequestSkipState("));
        assertTrue(source.contains("private void applyRequestStartState("));
        assertTrue(source.contains("applyRequestSkipState(refreshPlan);"));
        assertTrue(source.contains("applyRequestStartState(autoRefresh);"));
    }
}

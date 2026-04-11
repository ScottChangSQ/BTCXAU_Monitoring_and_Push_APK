package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartWarmDisplaySourceTest {

    @Test
    public void buildWarmDisplayCandlesShouldStopAtFirstUsableSource() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("CandleAggregationHelper.retainClosedTargetCandles("));
        assertTrue(source.contains("return closedAggregated;"));
        assertFalse(source.contains("aggregated.size() < 2"));
        assertFalse(source.contains("bestSourceDurationMs"));
    }
}

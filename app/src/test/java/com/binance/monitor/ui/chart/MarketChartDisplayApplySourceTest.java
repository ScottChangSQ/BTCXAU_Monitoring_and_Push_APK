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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private void applyDisplayCandles("));
        assertTrue(source.contains("applyDisplayCandles(key, candles, false, false, false);"));
        assertTrue(source.contains("applyDisplayCandles(key, displayUpdate.toDisplay, autoRefresh, displayUpdate.shouldFollowLatest, true);"));
        assertTrue(source.contains("binding.klineChartView.setCandlesKeepingViewport(loadedCandles);"));
        assertTrue(source.contains("if (shouldFollowLatest && !binding.klineChartView.hasActiveCrosshair()) {\n                binding.klineChartView.scrollToLatest();\n            }"));
    }
}

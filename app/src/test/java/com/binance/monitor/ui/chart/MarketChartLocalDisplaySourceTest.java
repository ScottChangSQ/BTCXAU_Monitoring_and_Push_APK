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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private void applyLocalDisplayCandles("));
        assertTrue(source.contains("applyLocalDisplayCandles(key, cached);"));
        assertTrue(source.contains("applyLocalDisplayCandles(key, persisted);"));
        assertTrue(source.contains("binding.klineChartView.setCandles(loadedCandles);"));
        assertTrue(source.contains("renderInfoWithLatest();"));
        assertTrue(source.contains("refreshChartOverlays();"));
        assertTrue(source.contains("updateStateCount();"));
    }
}

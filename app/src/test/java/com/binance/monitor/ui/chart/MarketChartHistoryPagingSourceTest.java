package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartHistoryPagingSourceTest {

    @Test
    public void activityShouldLetChartViewHandleOlderCandleSorting() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("ChartHistoryPagingHelper.resolveOlderCandles(loadedCandles, processed);"));
        assertTrue(source.contains("binding.klineChartView.prependCandles(older);"));
        assertFalse(source.contains("Collections.sort(older, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));"));
        assertFalse(source.contains("Set<Long> existing = ConcurrentHashMap.newKeySet();"));
    }
}

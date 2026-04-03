package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartRefreshSourceTest {

    @Test
    public void activityShouldReuseLoadedCandlesForReadOnlyRefreshChecks() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("? loadedCandles"));
        assertFalse(source.contains("? new ArrayList<>(loadedCandles)\r\n                : getCachedOrPersisted(key);"));
        assertFalse(source.contains("List<CandleEntry> visible = loadedCandles == null ? new ArrayList<>() : new ArrayList<>(loadedCandles);"));
    }
}

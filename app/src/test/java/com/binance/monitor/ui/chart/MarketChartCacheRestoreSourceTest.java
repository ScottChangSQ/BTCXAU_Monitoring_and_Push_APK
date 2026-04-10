package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartCacheRestoreSourceTest {

    @Test
    public void restorePersistedCacheShouldReuseSharedCacheLookup() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int methodStart = source.indexOf("private void restorePersistedCache(String key)");
        int nextMethodStart = source.indexOf("private void applyLocalDisplayCandles(");
        String restoreMethod = source.substring(methodStart, nextMethodStart);

        assertTrue(restoreMethod.contains("List<CandleEntry> persisted = getCachedCandles(key);"));
        assertTrue(restoreMethod.contains("schedulePersistedCacheRestore(key, true);"));
        assertFalse(restoreMethod.contains("chartHistoryRepository.loadCandles(key)"));
    }
}

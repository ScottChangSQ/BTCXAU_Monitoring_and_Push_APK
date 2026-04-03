package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartActivityCacheSourceTest {

    @Test
    public void activityShouldInvalidateOldChartCacheBeforeRestore() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("ensureChartCacheSchemaCurrent();"));
        assertTrue(source.contains("chartHistoryRepository.clearAllHistory();"));
        assertFalse(source.contains("KlineCacheStore"));
        assertFalse(source.contains("v2SnapshotStore.clearAll();"));
        assertFalse(source.contains("writeSeriesSnapshot("));
    }
}

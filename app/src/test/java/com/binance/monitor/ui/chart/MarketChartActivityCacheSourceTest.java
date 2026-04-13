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
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("ensureChartCacheSchemaCurrent();"));
        assertTrue(source.contains("private Future<?> chartCacheInvalidationTask;"));
        assertTrue(source.contains("private void scheduleChartCacheInvalidation() {"));
        assertTrue(source.contains("chartCacheInvalidationTask = ioExecutor.submit(() -> {\n            chartHistoryRepository.clearAllHistory();\n            markChartCacheSchemaCurrent();\n        });"));
        assertTrue(source.contains("markChartCacheSchemaCurrent();"));
        assertTrue(source.contains("private void markChartCacheSchemaCurrent() {"));
        assertTrue(source.contains("private void awaitChartCacheInvalidationIfNeeded() {"));
        assertTrue(source.contains("awaitChartCacheInvalidationIfNeeded();\n                List<CandleEntry> persisted = chartHistoryRepository.loadCandles(key);"));
        assertTrue(source.contains("awaitChartCacheInvalidationIfNeeded();\n            persistCandles(key, snapshot, symbol, interval);"));
        assertFalse(source.contains("preferences.edit().putInt(PREF_KEY_CHART_CACHE_SCHEMA_VERSION, CHART_CACHE_SCHEMA_VERSION).apply();\n        scheduleChartCacheInvalidation();"));
        assertFalse(source.contains("KlineCacheStore"));
        assertFalse(source.contains("v2SnapshotStore.clearAll();"));
        assertFalse(source.contains("writeSeriesSnapshot("));
    }
}

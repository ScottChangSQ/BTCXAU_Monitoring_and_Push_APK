package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartLaunchAnrSourceTest {

    @Test
    public void requestKlinesShouldOnlyReadChartCacheFromMemoryOnMainThread() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int methodStart = source.indexOf("private void requestKlines()");
        int nextMethodStart = source.indexOf("private void requestMoreHistory(long beforeOpenTime)");
        String method = source.substring(methodStart, nextMethodStart);

        assertTrue(method.contains("List<CandleEntry> memoryCached = getCachedCandles(key);"));
        assertTrue(method.contains("schedulePersistedCacheRestore(key, shouldWarmDisplay);"));
        assertFalse(method.contains("chartHistoryRepository.loadCandles("));
    }

    @Test
    public void resolveChartOverlaySnapshotShouldScheduleBackgroundRestoreInsteadOfReadingRoomDirectly() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        int methodStart = source.indexOf("private AccountSnapshot resolveChartOverlaySnapshot");
        int nextMethodStart = source.indexOf("private void scheduleStoredChartOverlayRestore()");
        String method = source.substring(methodStart, nextMethodStart);

        assertTrue(method.contains("scheduleStoredChartOverlayRestore();"));
        assertFalse(method.contains("accountStorageRepository.loadStoredSnapshot()"));
    }
}

package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceRuntimePolicySourceTest {

    @Test
    public void serviceShouldUseMinimalFloatingModeWhenBackgroundAndInteractive() throws Exception {
        String serviceSource = readUtf8("src/main/java/com/binance/monitor/service/MonitorService.java");
        String preloadSource = readUtf8("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java");
        String floatingSource = readUtf8("src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java");

        assertTrue(serviceSource.contains("private boolean shouldUseMinimalFloatingMode() {"));
        assertTrue(serviceSource.contains("return !AppForegroundTracker.getInstance().isForeground()"));
        assertTrue(serviceSource.contains("&& deviceInteractive"));
        assertTrue(serviceSource.contains("&& configManager != null"));
        assertTrue(serviceSource.contains("&& configManager.isFloatingEnabled();"));
        assertTrue(preloadSource.contains("if (!AppForegroundTracker.getInstance().isForeground() && !liveScreenActive) {"));
        assertTrue(preloadSource.contains("fullSnapshotActive = false;"));
        assertTrue(floatingSource.contains("return FloatingPositionAggregator.buildSymbolCardsFromRuntime("));
        assertTrue(floatingSource.contains("snapshot.put(symbol, repository.selectCurrentMinuteSnapshot(symbol));"));
    }

    private String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

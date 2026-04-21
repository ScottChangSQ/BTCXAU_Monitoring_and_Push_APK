package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceMarketOverviewSourceTest {

    @Test
    public void streamMarketSnapshotShouldPublishClosedMinuteOverviewSeparately() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue(source.contains("List<SymbolMarketWindow> symbolWindows = new ArrayList<>()"));
        assertTrue(source.contains("JSONObject latestClosed = state.optJSONObject(\"latestClosedCandle\")"));
        assertTrue(source.contains("symbolWindows.add(new SymbolMarketWindow("));
        assertTrue(source.contains("repository.applyMarketRuntimeSnapshot("));
    }

    @Test
    public void floatingWindowShouldConsumeClosedMinuteOverviewFromUnifiedRuntimeSnapshot() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        );

        assertTrue(source.contains("repository.selectClosedMinute(symbol)"));
        assertTrue(source.contains("repository.selectLatestPrice(symbol)"));
        assertTrue(!source.contains("repository.getDisplayOverviewKlineSnapshot()"));
        assertTrue(!source.contains("repository.getDisplayPriceSnapshot()"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MonitorService.java");
    }
}

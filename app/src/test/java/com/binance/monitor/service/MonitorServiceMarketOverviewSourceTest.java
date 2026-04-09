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

        assertTrue(source.contains("Map<String, KlineData> overviewKlineDelta = new HashMap<>()"));
        assertTrue(source.contains("JSONObject latestClosed = state.optJSONObject(\"latestClosedCandle\")"));
        assertTrue(source.contains("overviewKlineDelta.put(symbol, toKlineDataFromJson(symbol, latestClosed, true));"));
        assertTrue(source.contains("repository.applyMarketDelta(klineDelta, priceDelta, overviewKlineDelta);"));
    }

    @Test
    public void floatingWindowShouldConsumeClosedMinuteOverviewSnapshot() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue(source.contains("repository.getDisplayOverviewKlineSnapshot()"));
        assertTrue(!source.contains("FloatingPositionAggregator.buildSymbolCards(\n                positions,\n                repository.getDisplayKlineSnapshot(),"));
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

package com.binance.monitor.data.repository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorRepositoryDisplaySnapshotSourceTest {

    @Test
    public void monitorRepositoryShouldUseDisplaySnapshotNaming() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java",
                "src/main/java/com/binance/monitor/data/repository/MonitorRepository.java"
        );

        assertTrue(source.contains("private final MarketRuntimeStore marketRuntimeStore = new MarketRuntimeStore();"));
        assertTrue(source.contains("selectLatestPrice("));
        assertTrue(source.contains("selectClosedMinute("));
        assertTrue(source.contains("selectDisplayKline("));
        assertTrue(source.contains("selectMarketWindowSignature("));
        assertTrue(source.contains("MutableLiveData<MarketRuntimeSnapshot> marketRuntimeSnapshotLiveData"));
        assertTrue(source.contains("getMarketRuntimeSnapshotLiveData()"));
        assertFalse(source.contains("getMarketRuntimeSnapshot()"));
        assertFalse(source.contains("applyMarketDelta("));
        assertFalse(source.contains("resolveLatestPrice("));
        assertFalse(source.contains("resolveClosedMinute("));
        assertFalse(source.contains("updateDisplayPrice("));
        assertFalse(source.contains("updateDisplayKline("));
        assertFalse(source.contains("getDisplayPriceSnapshot()"));
        assertFalse(source.contains("getDisplayKlineSnapshot()"));
        assertFalse(source.contains("getDisplayOverviewKlineSnapshot()"));
        assertFalse(source.contains("buildDisplayPriceSnapshot("));
        assertFalse(source.contains("buildDisplayKlineSnapshot("));
        assertFalse(source.contains("buildDisplayOverviewKlineSnapshot("));
        assertFalse(source.contains("private final MutableLiveData<Map<String, Double>> displayPrices"));
        assertFalse(source.contains("private final MutableLiveData<Map<String, KlineData>> displayKlines"));
        assertFalse(source.contains("private final MutableLiveData<Map<String, KlineData>> displayOverviewKlines"));
        assertFalse(source.contains("getDisplayPrices()"));
        assertFalse(source.contains("getDisplayKlines()"));
        assertFalse(source.contains("getDisplayOverviewKlines()"));
        assertFalse(source.contains("private final Map<String, Double> displayPriceCache = new HashMap<>();"));
        assertFalse(source.contains("private final Map<String, KlineData> displayKlineCache = new HashMap<>();"));
        assertFalse(source.contains("private final Map<String, KlineData> displayOverviewKlineCache = new HashMap<>();"));
        assertFalse(source.contains("mirrorDisplaySnapshotsLocked()"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MonitorRepository.java");
    }
}

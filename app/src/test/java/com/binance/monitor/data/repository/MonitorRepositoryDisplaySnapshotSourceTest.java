package com.binance.monitor.data.repository;

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

        assertTrue(source.contains("getDisplayPrices()"));
        assertTrue(source.contains("getDisplayKlines()"));
        assertTrue(source.contains("getDisplayOverviewKlines()"));
        assertTrue(source.contains("updateDisplayPrice("));
        assertTrue(source.contains("updateDisplayKline("));
        assertTrue(source.contains("getDisplayPriceSnapshot()"));
        assertTrue(source.contains("getDisplayKlineSnapshot()"));
        assertTrue(source.contains("getDisplayOverviewKlineSnapshot()"));
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

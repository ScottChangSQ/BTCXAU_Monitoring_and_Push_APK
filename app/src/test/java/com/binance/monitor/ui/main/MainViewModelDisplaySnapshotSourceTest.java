package com.binance.monitor.ui.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainViewModelDisplaySnapshotSourceTest {

    @Test
    public void mainViewModelShouldExposeUnifiedMarketRuntimeSnapshotOnly() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainViewModel.java",
                "src/main/java/com/binance/monitor/ui/main/MainViewModel.java"
        );

        assertTrue(source.contains("getMarketRuntimeSnapshotLiveData()"));
        assertTrue(source.contains("selectLatestPrice(String symbol)"));
        assertTrue(source.contains("selectClosedMinute(String symbol)"));
        assertFalse(source.contains("getDisplayPrices()"));
        assertFalse(source.contains("getDisplayKlines()"));
        assertFalse(source.contains("getDisplayOverviewKlines()"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MainViewModel.java");
    }
}

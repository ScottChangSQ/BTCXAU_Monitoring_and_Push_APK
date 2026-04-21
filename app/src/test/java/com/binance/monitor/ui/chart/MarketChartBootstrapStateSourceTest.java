package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartBootstrapStateSourceTest {

    @Test
    public void coldStartShouldBeginBootstrapBeforeStorageRestore() throws Exception {
        String runtimeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(runtimeSource.contains("host.beginChartBootstrap();"));
        assertTrue(runtimeSource.contains("host.beginChartBootstrap();\n        host.restorePersistedCache();"));
    }

    @Test
    public void screenShouldDrivePersistedRestoreThroughBootstrapStateMachine() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(screenSource.contains("private PageBootstrapStateMachine chartBootstrapStateMachine = new PageBootstrapStateMachine();"));
        assertTrue(screenSource.contains("private PageBootstrapSnapshot chartBootstrapSnapshot = PageBootstrapSnapshot.initial();"));
        assertTrue(screenSource.contains("applyBootstrapState(chartBootstrapStateMachine.onStorageRestoreStarted());"));
        assertTrue(screenSource.contains("applyBootstrapState(chartBootstrapStateMachine.onStorageDataReady(key));"));
        assertTrue(screenSource.contains("applyPersistedCacheRestoreMiss(key);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到图表源码");
    }
}

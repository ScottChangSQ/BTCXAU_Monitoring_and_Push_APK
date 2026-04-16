package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartSelectionRoutingSourceTest {

    @Test
    public void symbolAndIntervalSelectionShouldBeRoutedThroughPageRuntime() throws Exception {
        String activitySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String runtimeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("pageRuntime.requestSymbolSelection(symbol);"));
        assertTrue(activitySource.contains("binding.btnInterval1m.setOnClickListener"));
        assertTrue(activitySource.contains("pageRuntime.requestIntervalSelection(INTERVALS[0].key)"));
        assertFalse(activitySource.contains("private void switchSymbol(String symbol)"));
        assertFalse(activitySource.contains("private void switchInterval(IntervalOption option)"));
        assertTrue(runtimeSource.contains("public void requestSymbolSelection(@NonNull String symbol) {"));
        assertTrue(runtimeSource.contains("host.canApplySelectedSymbol(symbol)"));
        assertTrue(runtimeSource.contains("host.commitSelectedSymbol(symbol);"));
        assertTrue(runtimeSource.contains("public void requestIntervalSelection(@NonNull String intervalKey) {"));
        assertTrue(runtimeSource.contains("host.canApplySelectedInterval(intervalKey)"));
        assertTrue(runtimeSource.contains("host.commitSelectedInterval(intervalKey);"));
        assertTrue(runtimeSource.contains("host.invalidateChartDisplayContext();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到图表页源码");
    }
}

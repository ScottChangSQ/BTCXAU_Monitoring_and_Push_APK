package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartIndicatorToggleSourceTest {

    @Test
    public void indicatorToggleShouldBeRoutedThroughPageRuntime() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String runtimeSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("binding.btnIndicatorVolume.setOnClickListener"));
        assertTrue(activitySource.contains("MarketChartPageRuntime.INDICATOR_VOLUME"));
        assertTrue(activitySource.contains("MarketChartPageRuntime.INDICATOR_MACD"));
        assertTrue(activitySource.contains("MarketChartPageRuntime.INDICATOR_KDJ"));
        assertTrue(activitySource.contains("pageRuntime.notifyIndicatorSelectionChanged();"));
        assertFalse(activitySource.contains("private void toggleIndicator(Runnable action)"));
        assertFalse(activitySource.contains("private void onIndicatorChanged()"));
        assertTrue(runtimeSource.contains("public static final String INDICATOR_VOLUME = \"volume\";"));
        assertTrue(runtimeSource.contains("public static final String INDICATOR_KDJ = \"kdj\";"));
        assertTrue(runtimeSource.contains("public void toggleIndicator(@NonNull String indicatorKey) {"));
        assertTrue(runtimeSource.contains("host.toggleIndicatorState(indicatorKey);"));
        assertTrue(runtimeSource.contains("host.notifyIndicatorSelectionChanged();"));
        assertTrue(runtimeSource.contains("public void notifyIndicatorSelectionChanged() {"));
    }
}

package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartActivityBridgeSourceTest {

    @Test
    public void marketChartActivityShouldBridgeLegacyEntryToMainHostChartTab() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private boolean bridgeLegacyEntryToMainHost(@Nullable Intent sourceIntent) {"));
        assertTrue(source.contains("HostNavigationIntentFactory.forTab(this, HostTab.MARKET_CHART)"));
        assertTrue(source.contains("bridgeIntent.putExtras(sourceExtras);"));
        assertTrue(source.contains("startActivity(bridgeIntent);"));
        assertTrue(source.contains("finish();"));
    }
}

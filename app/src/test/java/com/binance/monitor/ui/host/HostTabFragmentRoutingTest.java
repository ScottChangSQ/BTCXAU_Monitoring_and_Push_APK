package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HostTabFragmentRoutingTest {

    @Test
    public void hostNavigatorShouldCreateAllFiveTabFragments() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("case MARKET_MONITOR:"));
        assertTrue(source.contains("return new MarketMonitorFragment();"));
        assertTrue(source.contains("case MARKET_CHART:"));
        assertTrue(source.contains("return new MarketChartFragment();"));
        assertTrue(source.contains("case ACCOUNT_STATS:"));
        assertTrue(source.contains("return new AccountStatsFragment();"));
        assertTrue(source.contains("case ACCOUNT_POSITION:"));
        assertTrue(source.contains("return new AccountPositionFragment();"));
        assertTrue(source.contains("case SETTINGS:"));
        assertTrue(source.contains("return new SettingsFragment();"));
    }
}

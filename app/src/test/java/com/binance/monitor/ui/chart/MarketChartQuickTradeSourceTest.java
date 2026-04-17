package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartQuickTradeSourceTest {

    @Test
    public void marketChartLayoutShouldExposeQuickTradeModesAndRemoveLegacyTradeCard() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_market_chart.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String screen = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/btnChartModeMarket"));
        assertTrue(layout.contains("@+id/btnChartModePending"));
        assertTrue(layout.contains("@+id/layoutChartQuickTradeBar"));
        assertTrue(layout.contains("@+id/btnQuickTradePrimary"));
        assertTrue(layout.contains("@+id/etQuickTradeVolume"));
        assertTrue(layout.contains("@+id/btnQuickTradeSecondary"));
        assertTrue(screen.contains("applyQuickTradeBarLayoutContract()"));
        assertTrue(screen.contains("binding.layoutChartQuickTradeBar.post(this::applyQuickTradeBarLayoutContract);"));
        assertTrue(screen.contains("params.width = 0;"));
        assertTrue(screen.contains("params.weight = 1f;"));
        assertFalse(layout.contains("@+id/cardChartTradeActions"));
        assertFalse(layout.contains("@+id/btnChartTradeBuy"));
        assertFalse(layout.contains("@+id/btnChartTradeSell"));
        assertFalse(layout.contains("@+id/btnChartTradePending"));
    }
}

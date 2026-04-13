package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPositionPanelSourceTest {

    @Test
    public void chartPositionPanelShouldOnlyBindLightweightStatusAndTradeButtons() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");

        assertTrue(source.contains("private void setupChartPositionPanel()"));
        assertTrue(source.contains("binding.btnChartTradeBuy.setOnClickListener"));
        assertTrue(source.contains("binding.btnChartTradeSell.setOnClickListener"));
        assertTrue(source.contains("binding.btnChartTradePending.setOnClickListener"));
        assertTrue(source.contains("bindChartOverlayStatus(lastChartOverlaySnapshot, masked);"));
        assertFalse(source.contains("lastChartPositions"));
        assertFalse(source.contains("lastChartPendingOrders"));
        assertFalse(source.contains("updateChartPositionPanel("));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartTradingStructureSourceTest {

    @Test
    public void tradingPageShouldExposeTradeActionsWithoutLegacySummaryCardOrBottomBanner() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_market_chart.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/btnChartModeMarket"));
        assertTrue(layout.contains("@+id/btnChartModePending"));
        assertTrue(layout.contains("@+id/layoutChartQuickTradeBar"));
        assertFalse(layout.contains("@+id/cardChartRiskBanner"));
        assertFalse(layout.contains("@+id/btnChartRiskAction"));
        assertFalse(layout.contains("@+id/tvChartRiskBanner"));
        assertFalse(layout.contains("@+id/tvChartRiskMeta"));
        assertFalse(layout.contains("@+id/cardChartTradeActions"));
        assertFalse(layout.contains("@+id/btnChartTradeBuy"));
        assertFalse(layout.contains("@+id/btnChartTradeSell"));
        assertFalse(layout.contains("@+id/btnChartTradePending"));
        assertFalse(layout.contains("@+id/cardChartPositions"));
        assertFalse(layout.contains("@+id/tvChartAbnormalSummary"));
        assertFalse(layout.contains("1M量"));
        assertFalse(layout.contains("1M额"));
        assertFalse(source.contains("private void updateChartRiskBanner()"));
        assertFalse(source.contains("binding.btnChartRiskAction.setOnClickListener"));
        assertFalse(source.contains("showChartAbnormalQuickView()"));
        assertFalse(source.contains("ChartAbnormalBottomSheetController"));
    }
}

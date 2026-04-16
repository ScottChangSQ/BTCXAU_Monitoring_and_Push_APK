package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartTradingStructureSourceTest {

    @Test
    public void tradingPageShouldExposeRiskBannerQuickActionsAndAbnormalSummary() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_market_chart.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String quickViewController = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/ChartAbnormalBottomSheetController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/cardChartRiskBanner"));
        assertTrue(layout.contains("@+id/cardChartTradeActions"));
        assertTrue(layout.contains("@+id/tvChartAbnormalSummary"));
        assertTrue(layout.contains("@+id/btnChartRiskAction"));
        assertTrue(source.contains("private void updateChartRiskBanner()"));
        assertTrue(source.contains("private void updateCurrentSymbolAbnormalSummary()"));
        assertTrue(source.contains("binding.btnChartRiskAction.setOnClickListener"));
        assertTrue(source.contains("showChartAbnormalQuickView()"));
        assertTrue(quickViewController.contains("BottomSheetDialog"));
        assertTrue(quickViewController.contains("dialog_chart_abnormal_sheet"));
    }
}

package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartHistoryTradeToggleSourceTest {

    @Test
    public void historyTradeOverlayShouldBeControlledByOwnToggleOnly() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private void toggleHistoryTradeVisibility()"));
        assertTrue(source.contains("showHistoryTrades = !showHistoryTrades;"));
        assertTrue(source.contains("binding.klineChartView.setOverlayVisibility(!masked, !masked, showHistoryTrades, !masked);"));
        assertFalse(source.contains("!masked && showHistoryTrades"));
    }
}

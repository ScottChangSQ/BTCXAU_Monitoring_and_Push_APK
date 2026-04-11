package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPositionOverlayToggleSourceTest {

    @Test
    public void positionOverlayShouldBeControlledByOwnToggleOnly() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private void togglePositionOverlayVisibility()"));
        assertTrue(source.contains("showPositionOverlays = !showPositionOverlays;"));
        assertTrue(source.contains("binding.klineChartView.setOverlayVisibility(\n                !masked && showPositionOverlays,\n                !masked && showPositionOverlays,\n                showHistoryTrades,\n                !masked && showPositionOverlays);"));
        assertFalse(source.contains("showHistoryTrades = !showPositionOverlays"));
    }
}

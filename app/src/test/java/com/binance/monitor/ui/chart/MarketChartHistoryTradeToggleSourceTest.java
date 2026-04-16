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
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String runtimeSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java")
        ), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(activitySource.contains("binding.btnToggleHistoryTrades.setOnClickListener(v -> pageRuntime.toggleHistoryTradeVisibility());"));
        assertFalse(activitySource.contains("private void toggleHistoryTradeVisibility()"));
        assertTrue(activitySource.contains("showHistoryTrades = !showHistoryTrades;"));
        assertTrue(runtimeSource.contains("public void toggleHistoryTradeVisibility() {\n        host.toggleHistoryTradeVisibilityState();\n        host.applyPrivacyMaskState();\n    }"));
        assertTrue(activitySource.contains("binding.klineChartView.setOverlayVisibility(\n                !masked && showPositionOverlays,\n                !masked && showPositionOverlays,\n                showHistoryTrades,\n                !masked && showPositionOverlays);"));
        assertFalse(activitySource.contains("!masked && showHistoryTrades"));
    }
}

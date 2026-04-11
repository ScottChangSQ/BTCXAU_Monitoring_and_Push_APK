package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartStartupGateSourceTest {

    private String readSource() throws Exception {
        Path path = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        if (!Files.exists(path)) {
            path = Paths.get("app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void marketChartActivityShouldCreateStartupGate() throws Exception {
        String source = readSource();
        assertTrue(source.contains("private final MarketChartStartupGate startupGate = new MarketChartStartupGate();"));
    }

    @Test
    public void marketChartActivityShouldDeferRealtimeTailUntilPrimaryDisplayReady() throws Exception {
        String source = readSource();
        assertTrue(source.contains("startupGate.shouldDeferUntilPrimaryDisplay(key)"));
        assertTrue(source.contains("startupGate.replacePendingRealtime(key, () -> applyRealtimeChartTail(latestKline));"));
    }

    @Test
    public void marketChartActivityShouldDeferOverlayRestoreUntilPrimaryDisplayReady() throws Exception {
        String source = readSource();
        assertTrue(source.contains("startupGate.replacePendingOverlay(key, () -> applyChartOverlaySnapshot(snapshot, cache));"));
        assertTrue(source.contains("flushStartupDeferredWorkAfterPrimaryCommit(key);"));
        assertTrue(source.contains("flushStartupDeferredWorkAfterPrimaryDraw("));
    }
}

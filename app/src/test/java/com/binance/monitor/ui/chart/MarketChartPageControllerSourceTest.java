package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartPageControllerSourceTest {

    @Test
    public void marketChartPageControllerShouldOwnColdStartVisibleAndDestroyOrchestration() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartPageController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(!source.contains("private boolean hasEnteredScreen;"));
        assertTrue(source.contains("public void onColdStart() {\n        host.onColdStart();"));
        assertTrue(source.contains("public void onPageShown() {\n        if (!host.isEmbeddedInHostShell()) {"));
        assertTrue(source.contains("host.onPageShown();"));
        assertTrue(source.contains("public void onPageHidden() {\n        host.onPageHidden();"));
        assertTrue(source.contains("public void onDestroy() {\n        host.onPageDestroyed();"));
    }
}

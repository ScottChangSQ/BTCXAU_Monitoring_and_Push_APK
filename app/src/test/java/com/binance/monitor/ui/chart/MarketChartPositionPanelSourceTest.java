package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPositionPanelSourceTest {

    @Test
    public void updateChartPositionPanelShouldSnapshotInputsBeforeRefreshingCachedLists() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        int methodStart = source.indexOf("private void updateChartPositionPanel(List<PositionItem> positions,");
        int nextMethodStart = source.indexOf("private double resolveChartTotalAsset(");
        String method = source.substring(methodStart, nextMethodStart);

        assertTrue(method.contains("List<PositionItem> positionSnapshot = positions == null"));
        assertTrue(method.contains("List<PositionItem> pendingSnapshot = pendingOrders == null"));
        assertTrue(method.contains("lastChartPositions.addAll(positionSnapshot);"));
        assertTrue(method.contains("lastChartPendingOrders.addAll(pendingSnapshot);"));
    }
}

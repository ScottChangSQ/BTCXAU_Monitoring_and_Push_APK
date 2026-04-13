package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPositionSortSourceTest {

    @Test
    public void chartActivityShouldRemoveLegacySortStateAndControls() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml");

        assertFalse(activitySource.contains("selectedPositionSort"));
        assertFalse(activitySource.contains("handleChartPositionSortSelection("));
        assertFalse(activitySource.contains("MarketChartPositionSortHelper.sortPositions("));
        assertFalse(layoutSource.contains("@+id/spinnerChartPositionSort"));
        assertFalse(layoutSource.contains("@+id/tvChartPositionSortLabel"));
        assertTrue(activitySource.contains("private final ChartOverlaySnapshotFactory chartOverlaySnapshotFactory = new ChartOverlaySnapshotFactory();"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

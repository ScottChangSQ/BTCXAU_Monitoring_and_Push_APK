package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPositionSortSourceTest {

    @Test
    public void chartPositionPanelShouldUseDedicatedDetailSortSelection() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private MarketChartPositionSortHelper.SortOption selectedPositionSort"));
        assertTrue(source.contains("handleChartPositionSortSelection("));
        assertTrue(source.contains("filteredPositions = MarketChartPositionSortHelper.sortPositions(filteredPositions, selectedPositionSort);"));
        assertTrue(source.contains("binding.spinnerChartPositionSort.setAdapter("));
        assertTrue(source.contains("binding.spinnerChartPositionSort.setBackgroundColor(Color.TRANSPARENT);"));
        assertTrue(source.contains("binding.tvChartPositionSortLabel.setOnClickListener(v -> binding.spinnerChartPositionSort.performClick());"));
    }
}

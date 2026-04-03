package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartLoadMoreStateSourceTest {

    @Test
    public void activityShouldReuseSharedLoadMoreStateMethods() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private void applyLoadMoreSuccessState("));
        assertTrue(source.contains("private void finishLoadMoreState()"));
        assertTrue(source.contains("applyLoadMoreSuccessState(reqSymbol, reqInterval, older);"));
        assertTrue(source.contains("finishLoadMoreState();"));
    }
}

package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class KlineChartViewMacdSourceTest {

    @Test
    public void macdHistogramShouldUseDifMinusDeaWithoutMultiplyTwo() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("macdHist[i] = (dif - dea);"));
        assertFalse(source.contains("macdHist[i] = (dif - dea) * 2d;"));
    }
}

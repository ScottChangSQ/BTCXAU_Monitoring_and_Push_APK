package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPositionSummarySourceTest {

    @Test
    public void positionSummaryShouldUseTotalAssetAsReturnRateDenominator() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("double ratio = totalAsset <= 1e-9 ? 0d : totalPnl / totalAsset;"));
        assertTrue(source.contains("private double resolveChartTotalAsset(@Nullable AccountSnapshot snapshot) {"));
        assertTrue(source.contains("double balance = resolveMetricNumber(snapshot.getOverviewMetrics(), \"总结余\", \"结余\", \"Balance\", \"Current Balance\");"));
        assertTrue(source.contains("double curveBalance = resolveLatestCurveBalance(snapshot);"));
    }

    @Test
    public void positionSummaryShouldKeepPnlAndReturnRateFontSizeConsistent() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertFalse(source.contains("new AbsoluteSizeSpan("));
    }
}

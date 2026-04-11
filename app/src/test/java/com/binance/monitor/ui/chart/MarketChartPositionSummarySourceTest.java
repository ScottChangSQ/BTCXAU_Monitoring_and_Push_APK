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

    @Test
    public void chartActivityShouldProvidePositionDetailSortOptions() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("binding.spinnerChartPositionSort.setAdapter(positionSortAdapter);"));
        assertTrue(source.contains("binding.tvChartPositionSortLabel.setOnClickListener(v -> binding.spinnerChartPositionSort.performClick());"));
        assertTrue(source.contains("MarketChartPositionSortHelper.buildOptionLabels()"));
        assertTrue(source.contains("filteredPositions = MarketChartPositionSortHelper.sortPositions(filteredPositions, selectedPositionSort);"));
    }

    @Test
    public void chartActivityShouldOwnRealtimeOverviewTogetherWithPositionPanel() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        Path layoutFile = Paths.get("src/main/res/layout/activity_market_chart.xml");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String layoutSource = new String(Files.readAllBytes(layoutFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(activitySource.contains("private AccountMetricAdapter chartOverviewAdapter;"));
        assertTrue(activitySource.contains("binding.recyclerChartOverview.setLayoutManager(new GridLayoutManager(this, 2));"));
        assertTrue(activitySource.contains("binding.recyclerChartOverview.setAdapter(chartOverviewAdapter);"));
        assertTrue(activitySource.contains("private void updateChartOverviewSection("));
        assertTrue(activitySource.contains("updateChartOverviewSection(snapshot);"));
        assertTrue(layoutSource.contains("@+id/tvChartOverviewTitle"));
        assertTrue(layoutSource.contains("@+id/tvChartOverviewMeta"));
        assertTrue(layoutSource.contains("@+id/recyclerChartOverview"));
    }
}

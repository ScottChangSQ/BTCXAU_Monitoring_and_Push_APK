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
    public void chartActivityShouldBindLightweightOverlayStatusFromSnapshot() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");

        assertTrue(activitySource.contains("private void bindChartOverlayStatus(@NonNull ChartOverlaySnapshot overlaySnapshot, boolean masked)"));
        assertTrue(activitySource.contains("binding.tvChartPositionSummary.setText(overlaySnapshot.getPositionSummaryText());"));
        assertTrue(activitySource.contains("binding.tvChartOverlayMeta.setText(overlaySnapshot.getOverlayMetaText());"));
        assertTrue(activitySource.contains("applyBuiltChartOverlaySnapshot("));
        assertFalse(activitySource.contains("chartOverviewAdapter"));
        assertFalse(activitySource.contains("recyclerChartOverview"));
        assertFalse(activitySource.contains("import com.binance.monitor.ui.account.AccountOverviewMetricsHelper;"));
        assertFalse(activitySource.contains("import com.binance.monitor.ui.account.adapter.AccountMetricAdapter;"));
        assertFalse(activitySource.contains("import com.binance.monitor.domain.account.model.AccountMetric;"));
    }

    @Test
    public void chartOverlaySnapshotFactoryShouldOwnOverlaySummaryConstruction() throws Exception {
        String factorySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java");

        assertTrue(factorySource.contains("private String buildSummaryText("));
        assertTrue(factorySource.contains("private String buildUpdatedAtText("));
        assertTrue(factorySource.contains("return new ChartOverlaySnapshot("));
        assertFalse(factorySource.contains("MarketChartPositionSortHelper"));
    }

    @Test
    public void positionSummaryShouldKeepPnlAndReturnRateFontSizeConsistent() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        assertFalse(activitySource.contains("new AbsoluteSizeSpan("));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

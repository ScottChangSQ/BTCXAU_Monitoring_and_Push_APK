package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartLocalFirstStartupSourceTest {

    @Test
    public void screenShouldSplitColdStartInvalidationFromSelectionInvalidation() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private void invalidateForColdStart() {"));
        assertTrue(source.contains("private void invalidateForSelectionChange() {"));
        assertTrue(source.contains("void invalidateChartDisplayContext() {\n        invalidateForSelectionChange();"));
    }

    @Test
    public void loadingShouldBeDrivenByBootstrapStateInsteadOfBareBoolean() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private void renderChartLoadingState() {"));
        assertTrue(source.contains("showChartRestoreHint(getString(R.string.chart_restore_loading_text));"));
        assertTrue(source.contains("showChartRestoreHint(getString(R.string.chart_restore_syncing_text));"));
        assertTrue(source.contains("renderChartLoadingState();"));
        assertFalse(source.contains("restoreChartOverlayFromLatestCacheOrEmpty"));
        assertFalse(source.contains("private void clearChartDisplayForEmptyState() {"));
    }

    @Test
    public void incompatibleLocalSeriesShouldNotBeUsedAsRepairStableSeed() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("if (!MarketChartDisplayHelper.isSeriesCompatibleForInterval(reqInterval.getKey(), localForPlan)) {\n            localForPlan = null;"));
        assertTrue(source.contains("final List<CandleEntry> refreshSeed = localForPlan == null ? new ArrayList<>() : new ArrayList<>(localForPlan);"));
    }

    @Test
    public void realtimeTailShouldObserveUnifiedMarketRuntimeSnapshot() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("repository.getMarketRuntimeSnapshotLiveData().observe(host.getLifecycleOwner(), ignored -> {"));
        assertFalse(source.contains("repository.getDisplayKlines().observe(host.getLifecycleOwner(), ignored -> {"));
    }

    @Test
    public void screenShouldReadCurrentMarketRuntimeFromLiveDataValueInsteadOfRepositorySnapshotGetter() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("monitorRepository.selectMarketWindowSignature(selectedSymbol)"));
        assertFalse(source.contains("monitorRepository.getMarketRuntimeSnapshotLiveData().getValue()"));
        assertFalse(source.contains("monitorRepository.getMarketRuntimeSnapshot()"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到图表源码");
    }
}

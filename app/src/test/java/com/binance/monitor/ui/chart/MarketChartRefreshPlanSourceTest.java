package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartRefreshPlanSourceTest {

    @Test
    public void requestKlinesShouldReusePlanningSeriesForLatestVisibleTime() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("long latestVisibleTime = host.resolveLatestVisibleCandleTime(localForPlan);"));
        assertTrue(source.contains("repository.selectTruthProgressAt(traceSymbol)"));
        assertFalse(source.contains("resolveLatestVisibleCandleTime(selectedSymbol)"));
    }

    @Test
    public void chartScreenShouldUseTruthProgressInsteadOfFutureDraftCloseTimeForFreshness() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(source.contains("return monitorRepository.selectTruthProgressAt(selectedSymbol);"));
        assertFalse(source.contains("long boundedCloseTime = Math.min(latest.getCloseTime(), now);"));
    }

    @Test
    public void requestKlinesShouldPassExplicitRequestReasonIntoRefreshPlanAndFailureState() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertTrue(source.contains("MarketChartRefreshHelper.RequestReason requestReason"));
        assertTrue(source.contains("MarketChartRefreshHelper.RequestReason effectiveRequestReason = requestReason;"));
        assertTrue(source.contains("effectiveRequestReason = MarketChartRefreshHelper.RequestReason.SERIES_REPAIR;"));
        assertTrue(source.contains("MarketChartRefreshHelper.resolvePlan(\n                localForPlan,"));
        assertTrue(source.contains("effectiveRequestReason\n        );"));
        assertTrue(source.contains("host.applyRequestFailureState(autoRefresh, deferTrueEmptyUntilStorageRestore, message);"));
    }
}

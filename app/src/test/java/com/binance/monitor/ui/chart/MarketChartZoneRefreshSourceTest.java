package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartZoneRefreshSourceTest {

    @Test
    public void marketChartScreenShouldRouteUiAndRealtimeRefreshThroughBudgetHelpers() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("dispatchChartRefresh(ChartRefreshEvent.uiStateChanged(), null, null);"));
        assertTrue(source.contains("dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);"));
        assertTrue(source.contains("private void dispatchChartRefresh(@NonNull ChartRefreshEvent event,"));
        assertTrue(source.contains("chartRefreshScheduler.requestDialogBind("));
        assertTrue(source.contains("chartRefreshScheduler.requestOverlayRender("));
        assertTrue(source.contains("chartRefreshScheduler.requestSummaryBind("));
        assertTrue(source.contains("mainHandler.post(dialogDrainRunnable);"));
        assertTrue(source.contains("mainHandler.post(overlaySummaryDrainRunnable);"));
        assertTrue(source.contains("chartRefreshScheduler.requestChartRender(buildRealtimeTailRenderToken(latestKline));"));
        assertTrue(source.contains("mainHandler.post(realtimeTailDrainRunnable);"));
        assertTrue(source.contains("dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);"));
        assertTrue(source.contains("monitorRepository.getConnectionStatus().observe(lifecycleOwner, ignored -> dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null));"));
        assertTrue(source.contains("globalStatusBottomSheetController.updateVisibleSheet(buildGlobalStatusSnapshot());"));
    }
}

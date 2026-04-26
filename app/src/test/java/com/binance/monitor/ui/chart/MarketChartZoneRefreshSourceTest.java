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
        assertTrue(source.contains("AppConstants.CHART_REALTIME_TAIL_UI_WINDOW_MS"));
        assertTrue(source.contains("lastRealtimeTailUiAppliedAt"));
        assertTrue(source.contains("realtimeTailWindowScheduled"));
        assertTrue(source.contains("mainHandler.postDelayed(() -> {"));
        assertTrue(source.contains("realtimeTailDrainRunnable.run();"));
        assertTrue(source.contains("dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);"));
        assertTrue(source.contains("monitorRepository.getConnectionStatus().observe(lifecycleOwner, ignored -> dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null));"));
        assertTrue(source.contains("globalStatusBottomSheetController.updateVisibleSheet(buildGlobalStatusSnapshot());"));
        assertTrue(source.contains("boolean overlayChanged = ChartOverlayRefreshDiff.hasOverlayVisualChange("));
        assertTrue(source.contains("ChartRefreshEvent.productRuntimeChanged(overlayChanged)"));
    }

    @Test
    public void marketChartHiddenPageShouldNotQueueDialogAndOverlayRefresh() throws Exception {
        String screenSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String fragmentSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(screenSource.contains("private boolean hostPageVisible;"));
        assertTrue(screenSource.contains("void onHostPageShown() {\n        hostPageVisible = true;\n        dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);\n    }"));
        assertTrue(screenSource.contains("void onHostPageHidden() {\n        hostPageVisible = false;\n        cancelQueuedUiRefreshCallbacks();\n    }"));
        assertTrue(screenSource.contains("if (!hostPageVisible) {\n            return;\n        }\n        ChartRefreshBudget budget = ChartRefreshBudget.resolve(event);"));
        assertTrue(screenSource.contains("private void cancelQueuedUiRefreshCallbacks() {"));
        assertTrue(screenSource.contains("mainHandler.removeCallbacks(dialogDrainRunnable);"));
        assertTrue(fragmentSource.contains("screen.onHostPageShown();"));
        assertTrue(fragmentSource.contains("screen.onHostPageHidden();"));
    }
}

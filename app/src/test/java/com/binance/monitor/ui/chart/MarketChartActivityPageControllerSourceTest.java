package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartActivityPageControllerSourceTest {

    @Test
    public void marketChartActivityShouldDelegatePageLifecycleToController() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("import com.binance.monitor.ui.chart.MarketChartPageController;"));
        assertTrue(source.contains("MarketChartPageHostDelegate"));
        assertTrue(source.contains("import com.binance.monitor.ui.chart.MarketChartPageRuntime;"));
        assertTrue(source.contains("private MarketChartPageController pageController;"));
        assertTrue(source.contains("private MarketChartPageRuntime pageRuntime;"));
        assertFalse(source.contains("private boolean chartScreenEntered;"));
        assertFalse(source.contains("private boolean accountOverlayRefreshPending;"));
        assertTrue(source.contains("pageController = new MarketChartPageController("));
        assertTrue(source.contains("new MarketChartPageHostDelegate("));
        assertTrue(source.contains("pageRuntime = new MarketChartPageRuntime("));
        assertTrue(source.contains("pageController.bind();"));
        assertTrue(source.contains("pageController.onColdStart();"));
        assertTrue(source.contains("pageController.onPageShown();"));
        assertTrue(source.contains("pageController.onPageHidden();"));
        assertTrue(source.contains("pageController.onDestroy();"));
        assertFalse(source.contains("private void setupBottomNav()"));
        assertFalse(source.contains("private void updateBottomTabs("));
        assertFalse(source.contains("private void styleNavTab("));
        assertFalse(source.contains("public void onPageShown() {\n                ensureMonitorServiceStarted();\n                applyPaletteStyles();"));
        assertTrue(source.contains("dataCoordinator.restoreChartOverlayFromLatestCacheOrEmpty();"));
        assertTrue(source.contains("consumePendingTradeActionIfNeeded();"));
        assertFalse(source.contains("private void enterChartScreen(boolean coldStart)"));
        assertFalse(source.contains("private void observeRealtimeDisplayKlines()"));
        assertFalse(source.contains("private void refreshChartOverlays()"));
        assertFalse(source.contains("private void restoreChartOverlayFromLatestCacheOrEmpty()"));
        assertFalse(source.contains("public void onPageHidden() {\n                stopAutoRefresh();"));
        assertFalse(source.contains("private long nextAutoRefreshAtMs;"));
        assertFalse(source.contains("private final Runnable autoRefreshRunnable"));
        assertFalse(source.contains("private final Runnable refreshCountdownRunnable"));
        assertFalse(source.contains("private void startAutoRefresh()"));
        assertFalse(source.contains("private void stopAutoRefresh()"));
        assertFalse(source.contains("private void scheduleNextAutoRefresh()"));
        assertFalse(source.contains("private void scheduleChartOverlayRefresh()"));
        assertFalse(source.contains("private void removeOverlayRefreshCallbacks()"));
        assertTrue(source.contains("cancelChartBackgroundTasks();"));
        assertTrue(source.contains("tradeDialogCoordinator.cancelTradeTasks();"));
        assertTrue(source.contains("accountStatsPreloadManager.removeCacheListener(accountCacheListener);"));
    }
}

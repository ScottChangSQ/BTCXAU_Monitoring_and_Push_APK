package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.ui.chart.runtime.ChartRefreshBudget;
import com.binance.monitor.ui.chart.runtime.ChartRefreshEvent;
import com.binance.monitor.ui.chart.runtime.ChartRefreshScheduler;

import org.junit.Test;

public class MarketChartZoneRefreshTest {

    @Test
    public void marketTickEventShouldOnlyRequireChartRenderZone() {
        ChartRefreshBudget budget = ChartRefreshBudget.resolve(ChartRefreshEvent.marketTickChanged());

        assertTrue(budget.needsRealtimeTailInvalidate());
        assertFalse(budget.needsUiStateBind());
        assertFalse(budget.needsSummaryBind());
        assertFalse(budget.needsOverlayRebuild());
    }

    @Test
    public void schedulerShouldCoalesceRedundantChartRenderRequests() {
        ChartRefreshScheduler scheduler = new ChartRefreshScheduler();

        scheduler.requestChartRender("tick-1");
        scheduler.requestChartRender("tick-2");

        assertEquals("tick-2", scheduler.drainPendingRenderToken());
        assertEquals(null, scheduler.drainPendingRenderToken());
    }

    @Test
    public void schedulerShouldCoalesceOverlayAndSummaryRequestsIndependently() {
        ChartRefreshScheduler scheduler = new ChartRefreshScheduler();

        scheduler.requestOverlayRender("overlay-1");
        scheduler.requestOverlayRender("overlay-2");
        scheduler.requestSummaryBind("summary-1");
        scheduler.requestSummaryBind("summary-2");

        assertEquals("overlay-2", scheduler.drainPendingOverlayRenderToken());
        assertEquals("summary-2", scheduler.drainPendingSummaryBindToken());
        assertEquals(null, scheduler.drainPendingOverlayRenderToken());
        assertEquals(null, scheduler.drainPendingSummaryBindToken());
    }

    @Test
    public void schedulerShouldCoalesceDialogRequestsIndependently() {
        ChartRefreshScheduler scheduler = new ChartRefreshScheduler();

        scheduler.requestDialogBind("dialog-1");
        scheduler.requestDialogBind("dialog-2");

        assertEquals("dialog-2", scheduler.drainPendingDialogBindToken());
        assertEquals(null, scheduler.drainPendingDialogBindToken());
    }
}

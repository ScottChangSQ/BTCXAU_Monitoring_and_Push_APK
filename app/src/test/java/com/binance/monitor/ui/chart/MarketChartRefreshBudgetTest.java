package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.ui.chart.runtime.ChartRefreshBudget;
import com.binance.monitor.ui.chart.runtime.ChartRefreshEvent;

import org.junit.Test;

public class MarketChartRefreshBudgetTest {

    @Test
    public void productRuntimeChangeShouldOnlyBindSummaryAndOverlay() {
        ChartRefreshBudget budget = ChartRefreshBudget.resolve(
                ChartRefreshEvent.productRuntimeChanged(true)
        );

        assertTrue(budget.needsSummaryBind());
        assertTrue(budget.needsOverlayRebuild());
        assertTrue(budget.needsRealtimeTailInvalidate());
        assertFalse(budget.needsUiStateBind());
    }

    @Test
    public void uiStateChangeShouldOnlyBindUiStateZone() {
        ChartRefreshBudget budget = ChartRefreshBudget.resolve(
                ChartRefreshEvent.uiStateChanged()
        );

        assertTrue(budget.needsUiStateBind());
        assertFalse(budget.needsSummaryBind());
        assertFalse(budget.needsOverlayRebuild());
        assertFalse(budget.needsRealtimeTailInvalidate());
        assertFalse(budget.needsDialogBind());
    }

    @Test
    public void dialogStateChangeShouldOnlyBindDialogZone() {
        ChartRefreshBudget budget = ChartRefreshBudget.resolve(
                ChartRefreshEvent.dialogStateChanged()
        );

        assertFalse(budget.needsUiStateBind());
        assertFalse(budget.needsSummaryBind());
        assertFalse(budget.needsOverlayRebuild());
        assertFalse(budget.needsRealtimeTailInvalidate());
        assertTrue(budget.needsDialogBind());
    }
}

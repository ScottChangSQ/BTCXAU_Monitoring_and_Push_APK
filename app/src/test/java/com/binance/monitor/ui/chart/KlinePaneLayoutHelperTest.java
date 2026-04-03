package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KlinePaneLayoutHelperTest {

    @Test
    public void computeShouldAllocateIndependentLowerPanes() {
        KlinePaneLayoutHelper.PaneLayout layout = KlinePaneLayoutHelper.compute(
                0f,
                100f,
                true,
                true,
                true,
                true,
                true
        );

        assertTrue(layout.volume.height() > 0f);
        assertTrue(layout.macd.height() > 0f);
        assertTrue(layout.stoch.height() > 0f);
        assertTrue(layout.rsi.height() > 0f);
        assertTrue(layout.kdj.height() > 0f);
        assertEquals(layout.volume.bottom, layout.macd.top, 1e-4f);
        assertEquals(layout.macd.bottom, layout.stoch.top, 1e-4f);
        assertEquals(layout.stoch.bottom, layout.rsi.top, 1e-4f);
        assertEquals(layout.rsi.bottom, layout.kdj.top, 1e-4f);
    }
}

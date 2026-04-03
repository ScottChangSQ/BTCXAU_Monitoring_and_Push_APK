package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChartCacheInvalidationHelperTest {

    @Test
    public void shouldInvalidateWhenStoredVersionIsOlder() {
        assertTrue(ChartCacheInvalidationHelper.shouldInvalidate(0, 1));
        assertTrue(ChartCacheInvalidationHelper.shouldInvalidate(1, 2));
    }

    @Test
    public void shouldNotInvalidateWhenVersionIsCurrentOrInvalid() {
        assertFalse(ChartCacheInvalidationHelper.shouldInvalidate(2, 2));
        assertFalse(ChartCacheInvalidationHelper.shouldInvalidate(3, 2));
        assertFalse(ChartCacheInvalidationHelper.shouldInvalidate(0, 0));
    }
}

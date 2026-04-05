package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChartGapFillHelperTest {

    @Test
    public void shouldBackfillOlderHistoryWhenPreviousOldestIsFarEarlierThanLatestWindowOldest() {
        long intervalMs = 60_000L;
        long previousOldestOpenTime = 1_000L;
        long latestWindowOldestOpenTime = 10_000L * intervalMs;

        assertTrue(ChartGapFillHelper.shouldBackfillOlderHistory(
                previousOldestOpenTime,
                latestWindowOldestOpenTime,
                intervalMs));
    }

    @Test
    public void shouldNotBackfillWhenGapIsWithinTolerance() {
        long intervalMs = 60_000L;
        long previousOldestOpenTime = 10_000L;
        long latestWindowOldestOpenTime = previousOldestOpenTime + intervalMs * 2L;

        assertFalse(ChartGapFillHelper.shouldBackfillOlderHistory(
                previousOldestOpenTime,
                latestWindowOldestOpenTime,
                intervalMs));
    }
}

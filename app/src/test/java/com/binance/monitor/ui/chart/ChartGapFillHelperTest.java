package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChartGapFillHelperTest {

    @Test
    public void shouldBackfillOlderHistoryWhenExtendedHistoryIsLoadedAndOldestMovedRight() {
        int previousWindowSize = 2_000;
        int defaultWindowLimit = 1_500;
        long previousOldestOpenTime = 1_000L;
        long latestWindowOldestOpenTime = 2_000L;

        assertTrue(ChartGapFillHelper.shouldBackfillOlderHistory(
                previousWindowSize,
                defaultWindowLimit,
                previousOldestOpenTime,
                latestWindowOldestOpenTime));
    }

    @Test
    public void shouldNotBackfillWhenPreviousWindowIsNotExtended() {
        int previousWindowSize = 1_500;
        int defaultWindowLimit = 1_500;
        long previousOldestOpenTime = 10_000L;
        long latestWindowOldestOpenTime = 20_000L;

        assertFalse(ChartGapFillHelper.shouldBackfillOlderHistory(
                previousWindowSize,
                defaultWindowLimit,
                previousOldestOpenTime,
                latestWindowOldestOpenTime));
    }

    @Test
    public void shouldNotBackfillWhenOnlyInternalGapExistsButWindowWasNotPreviouslyExtended() {
        int previousWindowSize = 300;
        int defaultWindowLimit = 300;
        long previousOldestOpenTime = 10_000L;
        long latestWindowOldestOpenTime = 11_000L;

        assertFalse(ChartGapFillHelper.shouldBackfillOlderHistory(
                previousWindowSize,
                defaultWindowLimit,
                previousOldestOpenTime,
                latestWindowOldestOpenTime));
    }
}

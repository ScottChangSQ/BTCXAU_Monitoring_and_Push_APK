package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ChartPersistenceWindowHelperTest {

    @Test
    public void retainClosedCandlesShouldDropLatestOpenPatchFromPersistenceWindow() {
        List<CandleEntry> source = Arrays.asList(
                candle(1_000L, 1_999L),
                candle(2_000L, 2_999L),
                candle(3_000L, 4_999L)
        );

        List<CandleEntry> persisted = ChartPersistenceWindowHelper.retainClosedCandles(source, 3_500L);

        assertEquals(2, persisted.size());
        assertEquals(1_000L, persisted.get(0).getOpenTime());
        assertEquals(2_000L, persisted.get(1).getOpenTime());
    }

    @Test
    public void retainClosedCandlesShouldKeepFullyClosedWindowUntouched() {
        List<CandleEntry> source = Arrays.asList(
                candle(1_000L, 1_999L),
                candle(2_000L, 2_999L)
        );

        List<CandleEntry> persisted = ChartPersistenceWindowHelper.retainClosedCandles(source, 3_500L);

        assertEquals(2, persisted.size());
        assertEquals(1_000L, persisted.get(0).getOpenTime());
        assertEquals(2_000L, persisted.get(1).getOpenTime());
    }

    private CandleEntry candle(long openTime, long closeTime) {
        return new CandleEntry("BTCUSDT", openTime, closeTime, 100d, 101d, 99d, 100d, 1d, 10d);
    }
}

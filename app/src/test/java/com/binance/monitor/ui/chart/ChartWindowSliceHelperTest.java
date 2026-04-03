package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ChartWindowSliceHelperTest {

    @Test
    public void takeLatestShouldKeepOnlyNewestWindow() {
        List<CandleEntry> source = Arrays.asList(
                candle(1_000L, 100d),
                candle(2_000L, 101d),
                candle(3_000L, 102d),
                candle(4_000L, 103d)
        );

        List<CandleEntry> sliced = ChartWindowSliceHelper.takeLatest(source, 2);

        assertEquals(2, sliced.size());
        assertEquals(3_000L, sliced.get(0).getOpenTime());
        assertEquals(4_000L, sliced.get(1).getOpenTime());
    }

    @Test
    public void takeLatestShouldKeepAllWhenAlreadyWithinLimit() {
        List<CandleEntry> source = Arrays.asList(
                candle(1_000L, 100d),
                candle(2_000L, 101d)
        );

        List<CandleEntry> sliced = ChartWindowSliceHelper.takeLatest(source, 5);

        assertEquals(2, sliced.size());
        assertEquals(1_000L, sliced.get(0).getOpenTime());
        assertEquals(2_000L, sliced.get(1).getOpenTime());
    }

    private CandleEntry candle(long openTime, double close) {
        return new CandleEntry(
                "BTCUSDT",
                openTime,
                openTime + 999L,
                close - 1d,
                close + 1d,
                close - 2d,
                close,
                1d,
                10d
        );
    }
}

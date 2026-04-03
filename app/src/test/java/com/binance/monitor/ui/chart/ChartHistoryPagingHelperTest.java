package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ChartHistoryPagingHelperTest {

    @Test
    public void resolveOlderCandlesShouldReturnOnlyNewerHistoryBeforeCurrentWindow() {
        List<CandleEntry> existing = Arrays.asList(
                candle(3_000L, 103d),
                candle(4_000L, 104d),
                candle(5_000L, 105d)
        );
        List<CandleEntry> incoming = Arrays.asList(
                candle(4_000L, 204d),
                candle(2_000L, 102d),
                candle(1_000L, 101d),
                candle(5_000L, 205d)
        );

        List<CandleEntry> older = ChartHistoryPagingHelper.resolveOlderCandles(existing, incoming);

        assertEquals(2, older.size());
        assertEquals(1_000L, older.get(0).getOpenTime());
        assertEquals(2_000L, older.get(1).getOpenTime());
    }

    @Test
    public void resolveOlderCandlesShouldReturnEmptyWhenNothingNew() {
        List<CandleEntry> existing = Arrays.asList(
                candle(3_000L, 103d),
                candle(4_000L, 104d)
        );
        List<CandleEntry> incoming = Arrays.asList(
                candle(3_000L, 203d),
                candle(4_000L, 204d)
        );

        List<CandleEntry> older = ChartHistoryPagingHelper.resolveOlderCandles(existing, incoming);

        assertEquals(0, older.size());
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

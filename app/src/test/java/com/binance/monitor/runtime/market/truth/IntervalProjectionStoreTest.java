package com.binance.monitor.runtime.market.truth;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.List;

public class IntervalProjectionStoreTest {

    @Test
    public void closeMinute_shouldOnlyAdvanceAffectedProjectionBuckets() {
        IntervalProjectionStore store = new IntervalProjectionStore();

        store.onMinuteClosed("BTCUSDT", minute(1_713_916_800_000L, 68_000.0, 68_020.0));
        store.onMinuteClosed("BTCUSDT", minute(1_713_916_860_000L, 68_020.0, 68_010.0));

        List<CandleEntry> fiveMinute = store.selectClosedSeries("BTCUSDT", "5m", 50);

        assertEquals(1, fiveMinute.size());
        assertEquals(68_010.0, fiveMinute.get(0).getClose(), 0.0001d);
    }

    @Test
    public void applyCanonicalSeries_shouldBeOverriddenByRecentMinuteTailForSameBucket() {
        IntervalProjectionStore store = new IntervalProjectionStore();
        store.applyCanonicalSeries(
                "BTCUSDT",
                "5m",
                java.util.Collections.singletonList(new CandleEntry(
                        "BTCUSDT",
                        1_713_916_800_000L,
                        1_713_917_099_999L,
                        68_000.0,
                        68_010.0,
                        67_990.0,
                        68_005.0,
                        10d,
                        100d
                )),
                null
        );
        store.onMinuteClosed("BTCUSDT", minute(1_713_916_800_000L, 68_000.0, 68_030.0));

        List<CandleEntry> fiveMinute = store.selectClosedSeries("BTCUSDT", "5m", 50);

        assertEquals(1, fiveMinute.size());
        assertEquals(68_030.0, fiveMinute.get(0).getClose(), 0.0001d);
    }

    @Test
    public void applyCanonicalSeries_shouldMergeOlderWindowInsteadOfReplacingExistingSeries() {
        IntervalProjectionStore store = new IntervalProjectionStore();
        store.applyCanonicalSeries(
                "BTCUSDT",
                "5m",
                java.util.Collections.singletonList(candle(1_713_916_800_000L, 68_000.0, 68_010.0)),
                null
        );
        store.applyCanonicalSeries(
                "BTCUSDT",
                "5m",
                java.util.Collections.singletonList(candle(1_713_916_500_000L, 67_900.0, 67_980.0)),
                null
        );

        List<CandleEntry> fiveMinute = store.selectClosedSeries("BTCUSDT", "5m", 50);

        assertEquals(2, fiveMinute.size());
        assertEquals(1_713_916_500_000L, fiveMinute.get(0).getOpenTime());
        assertEquals(1_713_916_800_000L, fiveMinute.get(1).getOpenTime());
    }

    private static CandleEntry candle(long openTime, double open, double close) {
        return new CandleEntry(
                "BTCUSDT",
                openTime,
                openTime + 5L * 60_000L - 1L,
                open,
                Math.max(open, close),
                Math.min(open, close),
                close,
                10d,
                100d
        );
    }

    private static CandleEntry minute(long openTime, double open, double close) {
        return new CandleEntry(
                "BTCUSDT",
                openTime,
                openTime + 60_000L - 1L,
                open,
                Math.max(open, close),
                Math.min(open, close),
                close,
                10d,
                100d
        );
    }
}

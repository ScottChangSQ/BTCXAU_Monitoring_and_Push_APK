package com.binance.monitor.runtime.market.truth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;

public class MinuteBaseStoreTest {

    @Test
    public void applyClosedMinute_shouldReplaceSameBucketDraft() {
        MinuteBaseStore store = new MinuteBaseStore();
        CandleEntry draft = minute(1_713_916_800_000L, 68_000.0, 68_030.0);
        CandleEntry closed = minute(1_713_916_800_000L, 68_000.0, 68_025.0);

        store.applyDraft("BTCUSDT", draft, 68_030.0, 1_713_916_840_000L);
        MinuteBaseStore.ApplyResult result = store.applyClosedMinute("BTCUSDT", closed, 68_025.0, 1_713_916_860_000L);

        assertNull(result.getDraftMinute());
        assertEquals(1, result.getClosedMinutes().size());
        assertEquals(68_025.0, result.getLatestClosedMinute().getClose(), 0.0001d);
    }

    @Test
    public void applyClosedMinutes_shouldKeepAscendingMinuteHistory() {
        MinuteBaseStore store = new MinuteBaseStore();

        MinuteBaseStore.ApplyResult result = store.applyClosedMinutes(
                "BTCUSDT",
                Arrays.asList(
                        minute(1_713_916_800_000L, 68_000.0, 68_010.0),
                        minute(1_713_916_860_000L, 68_010.0, 68_020.0)
                ),
                68_020.0,
                1_713_916_920_000L
        );

        assertEquals(2, result.getClosedMinutes().size());
        assertEquals(1_713_916_860_000L, result.getLatestClosedMinute().getOpenTime());
        assertEquals(68_020.0, result.getLatestPrice(), 0.0001d);
    }

    @Test
    public void applyClosedMinutes_shouldNotRollbackLatestPriceWhenFrontierDidNotAdvance() {
        MinuteBaseStore store = new MinuteBaseStore();
        CandleEntry closed = minute(1_713_916_800_000L, 68_000.0, 68_010.0);

        store.applyClosedMinute("BTCUSDT", closed, 68_030.0, 1_713_916_840_000L);
        MinuteBaseStore.ApplyResult result = store.applyClosedMinutes(
                "BTCUSDT",
                Arrays.asList(closed),
                68_010.0,
                1_713_916_900_000L
        );

        assertEquals(68_030.0, result.getLatestPrice(), 0.0001d);
        assertEquals(1_713_916_840_000L, result.getUpdatedAt());
    }

    @Test
    public void applyDraft_shouldAcceptSameMinutePriceOnlyChangeFromStream() {
        MinuteBaseStore store = new MinuteBaseStore();
        CandleEntry first = minute(1_713_916_800_000L, 68_000.0, 68_000.0);
        CandleEntry second = minute(1_713_916_800_000L, 68_000.0, 68_020.0);

        store.applyDraft("BTCUSDT", first, 68_000.0, 1_713_916_810_000L);
        MinuteBaseStore.ApplyResult result = store.applyDraft("BTCUSDT", second, 68_020.0, 1_713_916_812_000L);

        assertEquals(68_020.0, result.getLatestPrice(), 0.0001d);
        assertEquals(68_020.0, result.getDraftMinute().getClose(), 0.0001d);
        assertEquals(1_713_916_812_000L, result.getUpdatedAt());
        assertTrue(result.getDraftMinute().getOpenTime() == 1_713_916_800_000L);
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

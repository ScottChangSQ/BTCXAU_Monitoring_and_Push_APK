/*
 * K 线本地聚合辅助测试，确保已有小周期数据能快速拼出大周期预显示结果。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CandleAggregationHelperTest {
    private static final long MINUTE = 60_000L;
    private static final long BASE_TIME = 1_700_000_000_000L - Math.floorMod(1_700_000_000_000L, 5L * MINUTE);

    @Test
    public void aggregateShouldGroupMinuteCandlesIntoFiveMinuteBuckets() {
        List<CandleEntry> source = Arrays.asList(
                candle(BASE_TIME, 100d, 101d, 99d, 100.5d, 1d, 10d),
                candle(BASE_TIME + MINUTE, 100.5d, 102d, 100d, 101.5d, 2d, 20d),
                candle(BASE_TIME + MINUTE * 4L, 101.5d, 103d, 101d, 102d, 3d, 30d),
                candle(BASE_TIME + MINUTE * 5L, 102d, 104d, 101.5d, 103d, 4d, 40d)
        );

        List<CandleEntry> result = CandleAggregationHelper.aggregate(source, "BTCUSDT", "5m", 10);

        assertEquals(2, result.size());
        CandleEntry first = result.get(0);
        assertEquals(BASE_TIME, first.getOpenTime());
        assertEquals(BASE_TIME + MINUTE * 5L - 1L, first.getCloseTime());
        assertEquals(100d, first.getOpen(), 1e-9);
        assertEquals(103d, first.getHigh(), 1e-9);
        assertEquals(99d, first.getLow(), 1e-9);
        assertEquals(102d, first.getClose(), 1e-9);
        assertEquals(6d, first.getVolume(), 1e-9);
        assertEquals(60d, first.getQuoteVolume(), 1e-9);

        CandleEntry second = result.get(1);
        assertEquals(BASE_TIME + MINUTE * 5L, second.getOpenTime());
        assertEquals(103d, second.getClose(), 1e-9);
    }

    @Test
    public void aggregateShouldKeepLatestBucketsWithinLimit() {
        List<CandleEntry> source = Arrays.asList(
                candle(BASE_TIME, 100d, 101d, 99d, 100d, 1d, 10d),
                candle(BASE_TIME + MINUTE, 100d, 101d, 99d, 100d, 1d, 10d),
                candle(BASE_TIME + MINUTE * 5L, 101d, 102d, 100d, 101d, 1d, 10d),
                candle(BASE_TIME + MINUTE * 6L, 101d, 102d, 100d, 101d, 1d, 10d)
        );

        List<CandleEntry> result = CandleAggregationHelper.aggregate(source, "BTCUSDT", "5m", 1);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getOpenTime() >= BASE_TIME + MINUTE * 5L);
    }

    @Test
    public void mergeClosedBaseCandleShouldExtendCurrentBucketWhenRealtimeCandleArrives() {
        List<CandleEntry> existing = Arrays.asList(
                new CandleEntry("BTCUSDT", BASE_TIME, BASE_TIME + MINUTE * 2L - 1L,
                        100d, 102d, 99d, 101.5d, 3d, 30d)
        );
        CandleEntry incoming = candle(BASE_TIME + MINUTE * 4L, 101.5d, 103d, 100.5d, 102d, 3d, 30d);

        List<CandleEntry> result = CandleAggregationHelper.mergeClosedBaseCandle(
                existing,
                incoming,
                "BTCUSDT",
                "5m",
                10
        );

        assertEquals(1, result.size());
        CandleEntry merged = result.get(0);
        assertEquals(BASE_TIME, merged.getOpenTime());
        assertEquals(BASE_TIME + MINUTE * 5L - 1L, merged.getCloseTime());
        assertEquals(100d, merged.getOpen(), 1e-9);
        assertEquals(103d, merged.getHigh(), 1e-9);
        assertEquals(99d, merged.getLow(), 1e-9);
        assertEquals(102d, merged.getClose(), 1e-9);
        assertEquals(6d, merged.getVolume(), 1e-9);
        assertEquals(60d, merged.getQuoteVolume(), 1e-9);
    }

    @Test
    public void mergeClosedBaseCandleShouldAppendNextBucketAndIgnoreDuplicateCloseTime() {
        List<CandleEntry> existing = Arrays.asList(
                new CandleEntry("BTCUSDT", BASE_TIME, BASE_TIME + MINUTE * 5L - 1L,
                        100d, 103d, 99d, 102d, 6d, 60d)
        );
        CandleEntry duplicate = candle(BASE_TIME + MINUTE * 4L, 101.5d, 103d, 100.5d, 102d, 3d, 30d);
        CandleEntry nextBucket = candle(BASE_TIME + MINUTE * 5L, 102d, 104d, 101.5d, 103d, 4d, 40d);

        List<CandleEntry> duplicateResult = CandleAggregationHelper.mergeClosedBaseCandle(
                existing,
                duplicate,
                "BTCUSDT",
                "5m",
                10
        );
        List<CandleEntry> appendResult = CandleAggregationHelper.mergeClosedBaseCandle(
                duplicateResult,
                nextBucket,
                "BTCUSDT",
                "5m",
                10
        );

        assertEquals(1, duplicateResult.size());
        assertEquals(2, appendResult.size());
        assertEquals(BASE_TIME + MINUTE * 5L, appendResult.get(1).getOpenTime());
        assertEquals(103d, appendResult.get(1).getClose(), 1e-9);
    }

    @Test
    public void aggregateShouldSupportDerivingHourlySeriesFromMinuteBaseCache() {
        long hourBase = 1_700_000_000_000L - Math.floorMod(1_700_000_000_000L, 60L * MINUTE);
        List<CandleEntry> source = Arrays.asList(
                candle(hourBase, 100d, 101d, 99d, 100.2d, 1d, 10d),
                candle(hourBase + 15L * MINUTE, 100.2d, 102d, 100d, 101.5d, 2d, 20d),
                candle(hourBase + 30L * MINUTE, 101.5d, 103d, 101d, 102d, 3d, 30d),
                candle(hourBase + 59L * MINUTE, 102d, 104d, 101.8d, 103d, 4d, 40d)
        );

        List<CandleEntry> result = CandleAggregationHelper.aggregate(source, "BTCUSDT", "1h", 10);

        assertEquals(1, result.size());
        CandleEntry merged = result.get(0);
        assertEquals(hourBase, merged.getOpenTime());
        assertEquals(hourBase + 60L * MINUTE - 1L, merged.getCloseTime());
        assertEquals(100d, merged.getOpen(), 1e-9);
        assertEquals(104d, merged.getHigh(), 1e-9);
        assertEquals(99d, merged.getLow(), 1e-9);
        assertEquals(103d, merged.getClose(), 1e-9);
        assertEquals(10d, merged.getVolume(), 1e-9);
        assertEquals(100d, merged.getQuoteVolume(), 1e-9);
    }

    @Test
    public void mergeRealtimeBaseCandleShouldReplaceSameMinuteWithoutDoubleCounting() {
        List<CandleEntry> base = Arrays.asList(
                candle(BASE_TIME, 100d, 101d, 99d, 100.5d, 1d, 10d),
                candle(BASE_TIME + MINUTE, 100.5d, 101.2d, 100d, 100.8d, 2d, 20d)
        );
        CandleEntry incoming = new CandleEntry(
                "BTCUSDT",
                BASE_TIME + MINUTE,
                BASE_TIME + MINUTE * 2L - 1L,
                100.5d,
                102d,
                99.8d,
                101.6d,
                3.5d,
                35d
        );

        List<CandleEntry> updated = CandleAggregationHelper.mergeRealtimeBaseCandle(
                base,
                incoming,
                "BTCUSDT",
                10
        );

        assertEquals(2, updated.size());
        CandleEntry latest = updated.get(1);
        assertEquals(BASE_TIME + MINUTE, latest.getOpenTime());
        assertEquals(101.6d, latest.getClose(), 1e-9);
        assertEquals(3.5d, latest.getVolume(), 1e-9);
        assertEquals(35d, latest.getQuoteVolume(), 1e-9);
    }

    @Test
    public void mergeRealtimeBaseCandleShouldRefreshFiveMinutePreviewFromMinuteBase() {
        List<CandleEntry> base = Arrays.asList(
                candle(BASE_TIME, 100d, 101d, 99d, 100.5d, 1d, 10d),
                candle(BASE_TIME + MINUTE, 100.5d, 101.5d, 100d, 101d, 2d, 20d)
        );
        CandleEntry incoming = new CandleEntry(
                "BTCUSDT",
                BASE_TIME + MINUTE * 2L,
                BASE_TIME + MINUTE * 3L - 1L,
                101d,
                103d,
                100.5d,
                102d,
                3d,
                30d
        );

        List<CandleEntry> updatedBase = CandleAggregationHelper.mergeRealtimeBaseCandle(
                base,
                incoming,
                "BTCUSDT",
                10
        );
        List<CandleEntry> aggregated = CandleAggregationHelper.aggregate(
                updatedBase,
                "BTCUSDT",
                "5m",
                10
        );

        assertEquals(1, aggregated.size());
        CandleEntry merged = aggregated.get(0);
        assertEquals(BASE_TIME, merged.getOpenTime());
        assertEquals(BASE_TIME + MINUTE * 3L - 1L, merged.getCloseTime());
        assertEquals(100d, merged.getOpen(), 1e-9);
        assertEquals(103d, merged.getHigh(), 1e-9);
        assertEquals(99d, merged.getLow(), 1e-9);
        assertEquals(102d, merged.getClose(), 1e-9);
        assertEquals(6d, merged.getVolume(), 1e-9);
        assertEquals(60d, merged.getQuoteVolume(), 1e-9);
    }

    private CandleEntry candle(long openTime,
                               double open,
                               double high,
                               double low,
                               double close,
                               double volume,
                               double quoteVolume) {
        return new CandleEntry(
                "BTCUSDT",
                openTime,
                openTime + MINUTE - 1L,
                open,
                high,
                low,
                close,
                volume,
                quoteVolume
        );
    }
}

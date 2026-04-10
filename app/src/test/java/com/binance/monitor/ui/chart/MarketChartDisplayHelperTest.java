/*
 * 验证 K 线页的本地预显示与网络回填合并规则，避免切周期后把更完整的本地窗口覆盖短了。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MarketChartDisplayHelperTest {

    @Test
    public void mergeDisplaySeriesShouldKeepPreviewTailWhenNetworkWindowIsShorter() {
        List<CandleEntry> preview = Arrays.asList(
                candle(1_000L, 100d),
                candle(2_000L, 101d),
                candle(3_000L, 102d)
        );
        List<CandleEntry> fetched = Arrays.asList(
                candle(1_000L, 100.5d),
                candle(2_000L, 101.5d)
        );

        List<CandleEntry> merged = MarketChartDisplayHelper.mergeDisplaySeries(preview, fetched, 10);

        assertEquals(3, merged.size());
        assertEquals(100.5d, merged.get(0).getClose(), 0.0001d);
        assertEquals(101.5d, merged.get(1).getClose(), 0.0001d);
        assertEquals(102d, merged.get(2).getClose(), 0.0001d);
    }

    @Test
    public void mergeDisplaySeriesShouldTrimToLatestLimit() {
        List<CandleEntry> preview = Arrays.asList(
                candle(1_000L, 100d),
                candle(2_000L, 101d),
                candle(3_000L, 102d)
        );
        List<CandleEntry> fetched = Collections.singletonList(candle(4_000L, 103d));

        List<CandleEntry> merged = MarketChartDisplayHelper.mergeDisplaySeries(preview, fetched, 2);

        assertEquals(2, merged.size());
        assertEquals(3_000L, merged.get(0).getOpenTime());
        assertEquals(4_000L, merged.get(1).getOpenTime());
    }

    @Test
    public void mergeDisplaySeriesKeepingHistoryShouldPreserveOlderPagedCandles() {
        List<CandleEntry> preview = Arrays.asList(
                candle(1_000L, 100d),
                candle(2_000L, 101d),
                candle(3_000L, 102d),
                candle(4_000L, 103d)
        );
        List<CandleEntry> fetched = Arrays.asList(
                candle(3_000L, 202d),
                candle(4_000L, 203d),
                candle(5_000L, 204d)
        );

        List<CandleEntry> merged = MarketChartDisplayHelper.mergeDisplaySeriesKeepingHistory(preview, fetched, 2);

        assertEquals(5, merged.size());
        assertEquals(1_000L, merged.get(0).getOpenTime());
        assertEquals(2_000L, merged.get(1).getOpenTime());
        assertEquals(202d, merged.get(2).getClose(), 0.0001d);
        assertEquals(203d, merged.get(3).getClose(), 0.0001d);
        assertEquals(5_000L, merged.get(4).getOpenTime());
    }

    @Test
    public void mergeSeriesByOpenTimeShouldOverrideSameBucketAndKeepAscendingOrder() {
        List<CandleEntry> existing = Arrays.asList(
                candle(3_000L, 103d),
                candle(1_000L, 101d)
        );
        List<CandleEntry> latest = Arrays.asList(
                candle(2_000L, 202d),
                candle(3_000L, 303d)
        );

        List<CandleEntry> merged = MarketChartDisplayHelper.mergeSeriesByOpenTime(existing, latest);

        assertEquals(3, merged.size());
        assertEquals(1_000L, merged.get(0).getOpenTime());
        assertEquals(2_000L, merged.get(1).getOpenTime());
        assertEquals(3_000L, merged.get(2).getOpenTime());
        assertEquals(303d, merged.get(2).getClose(), 0.0001d);
    }

    @Test
    public void mergeSeriesWithLatestPatchShouldOverrideMatchingBucket() {
        List<CandleEntry> candles = Arrays.asList(
                candle(1_000L, 101d),
                candle(2_000L, 102d),
                candle(3_000L, 103d)
        );

        List<CandleEntry> merged = MarketChartDisplayHelper.mergeSeriesWithLatestPatch(
                candles,
                candle(2_000L, 222d)
        );

        assertEquals(3, merged.size());
        assertEquals(101d, merged.get(0).getClose(), 0.0001d);
        assertEquals(222d, merged.get(1).getClose(), 0.0001d);
        assertEquals(103d, merged.get(2).getClose(), 0.0001d);
    }

    @Test
    public void mergeSeriesWithLatestPatchShouldAppendNewerBucket() {
        List<CandleEntry> candles = Arrays.asList(
                candle(1_000L, 101d),
                candle(2_000L, 102d)
        );

        List<CandleEntry> merged = MarketChartDisplayHelper.mergeSeriesWithLatestPatch(
                candles,
                candle(3_000L, 303d)
        );

        assertEquals(3, merged.size());
        assertEquals(1_000L, merged.get(0).getOpenTime());
        assertEquals(2_000L, merged.get(1).getOpenTime());
        assertEquals(3_000L, merged.get(2).getOpenTime());
        assertEquals(303d, merged.get(2).getClose(), 0.0001d);
    }

    @Test
    public void buildDisplayUpdateShouldReuseCompatiblePreviewAndMarkChanged() {
        List<CandleEntry> currentVisible = Arrays.asList(
                candle(900_000L, 101d),
                candle(1_800_000L, 102d)
        );
        List<CandleEntry> preview = Arrays.asList(
                candle(900_000L, 100d),
                candle(1_800_000L, 101d),
                candle(2_700_000L, 102d)
        );
        List<CandleEntry> fetched = Arrays.asList(
                candle(1_800_000L, 202d),
                candle(3_600_000L, 204d)
        );

        MarketChartDisplayHelper.DisplayUpdate update = MarketChartDisplayHelper.buildDisplayUpdate(
                "BTCUSDT",
                "15m",
                preview,
                fetched,
                10,
                currentVisible,
                true,
                true
        );

        assertTrue(update.candlesChanged);
        assertTrue(update.shouldFollowLatest);
        assertEquals(4, update.toDisplay.size());
        assertEquals(900_000L, update.toDisplay.get(0).getOpenTime());
        assertEquals(202d, update.toDisplay.get(1).getClose(), 0.0001d);
        assertEquals(2_700_000L, update.toDisplay.get(2).getOpenTime());
        assertEquals(3_600_000L, update.toDisplay.get(3).getOpenTime());
    }

    @Test
    public void buildDisplayUpdateShouldDropIncompatiblePreviewAndDetectNoChange() {
        List<CandleEntry> currentVisible = Arrays.asList(
                candle(31L * 24L * 60L * 60_000L, 200d),
                candle(62L * 24L * 60L * 60_000L, 210d)
        );
        List<CandleEntry> preview = Arrays.asList(
                candle(60_000L, 100d),
                candle(120_000L, 101d)
        );
        List<CandleEntry> fetched = Arrays.asList(
                candle(31L * 24L * 60L * 60_000L, 200d),
                candle(62L * 24L * 60L * 60_000L, 210d)
        );

        MarketChartDisplayHelper.DisplayUpdate update = MarketChartDisplayHelper.buildDisplayUpdate(
                "BTCUSDT",
                "1M",
                preview,
                fetched,
                10,
                currentVisible,
                false,
                true
        );

        assertFalse(update.candlesChanged);
        assertFalse(update.shouldFollowLatest);
        assertEquals(2, update.toDisplay.size());
        assertEquals(200d, update.toDisplay.get(0).getClose(), 0.0001d);
        assertEquals(210d, update.toDisplay.get(1).getClose(), 0.0001d);
    }

    @Test
    public void buildDisplayUpdateShouldIgnoreCandlesFromDifferentSymbol() {
        List<CandleEntry> currentVisible = Arrays.asList(
                candle("BTCUSDT", 1_000L, 101d),
                candle("BTCUSDT", 2_000L, 102d)
        );
        List<CandleEntry> preview = Arrays.asList(
                candle("BTCUSDT", 1_000L, 101d),
                candle("XAUUSD", 2_000L, 999d)
        );
        List<CandleEntry> fetched = Arrays.asList(
                candle("XAUUSD", 2_000L, 888d),
                candle("BTCUSDT", 3_000L, 103d)
        );

        MarketChartDisplayHelper.DisplayUpdate update = MarketChartDisplayHelper.buildDisplayUpdate(
                "BTCUSDT",
                "1m",
                preview,
                fetched,
                10,
                currentVisible,
                false,
                false
        );

        assertEquals(2, update.toDisplay.size());
        assertEquals("BTCUSDT", update.toDisplay.get(0).getSymbol());
        assertEquals("BTCUSDT", update.toDisplay.get(1).getSymbol());
        assertEquals(101d, update.toDisplay.get(0).getClose(), 0.0001d);
        assertEquals(103d, update.toDisplay.get(1).getClose(), 0.0001d);
    }

    @Test
    public void mergeDisplaySeriesShouldDiscardIncompatiblePreviewForMonthlyInterval() {
        List<CandleEntry> preview = Arrays.asList(
                candle(60_000L, 100d),
                candle(120_000L, 101d),
                candle(180_000L, 102d)
        );
        List<CandleEntry> fetched = Arrays.asList(
                candle(31L * 24L * 60L * 60_000L, 200d),
                candle(62L * 24L * 60L * 60_000L, 210d)
        );

        List<CandleEntry> merged = MarketChartDisplayHelper.mergeDisplaySeries("1M", preview, fetched, 10);

        assertEquals(2, merged.size());
        assertEquals(200d, merged.get(0).getClose(), 0.0001d);
        assertEquals(210d, merged.get(1).getClose(), 0.0001d);
    }

    @Test
    public void isSeriesCompatibleForIntervalShouldRejectMinuteSeriesForWeeklyDisplay() {
        List<CandleEntry> preview = Arrays.asList(
                candle(60_000L, 100d),
                candle(120_000L, 101d),
                candle(180_000L, 102d)
        );

        assertFalse(MarketChartDisplayHelper.isSeriesCompatibleForInterval("1w", preview));
        assertTrue(MarketChartDisplayHelper.isSeriesCompatibleForInterval("1m", preview));
    }

    @Test
    public void isSeriesCompatibleForIntervalShouldRejectMinuteSeriesForMonthlyDisplay() {
        List<CandleEntry> preview = Arrays.asList(
                candle(60_000L, 100d),
                candle(120_000L, 101d),
                candle(180_000L, 102d)
        );

        assertFalse(MarketChartDisplayHelper.isSeriesCompatibleForInterval("1M", preview));
    }

    @Test
    public void isSeriesCompatibleForIntervalShouldRejectSingleBarPreviewForWeeklyDisplay() {
        List<CandleEntry> preview = Collections.singletonList(
                candle(60_000L, 100d)
        );

        assertFalse(MarketChartDisplayHelper.isSeriesCompatibleForInterval("1w", preview));
    }

    @Test
    public void isSeriesCompatibleForIntervalShouldRejectSingleBarPreviewForMonthlyDisplay() {
        List<CandleEntry> preview = Collections.singletonList(
                candle(31L * 24L * 60L * 60_000L, 200d)
        );

        assertFalse(MarketChartDisplayHelper.isSeriesCompatibleForInterval("1M", preview));
    }

    @Test
    public void shouldShowBlockingLoadingShouldOnlyBlockWhenNoVisibleCandles() {
        assertTrue(MarketChartDisplayHelper.shouldShowBlockingLoading(false, Collections.emptyList()));
        assertFalse(MarketChartDisplayHelper.shouldShowBlockingLoading(false, Collections.singletonList(candle(1_000L, 100d))));
        assertFalse(MarketChartDisplayHelper.shouldShowBlockingLoading(true, Collections.emptyList()));
    }

    @Test
    public void mergeRealtimeTailShouldOnlyOverrideLatestBucket() {
        List<CandleEntry> base = Arrays.asList(
                candle(1_000L, 100d),
                candle(2_000L, 101d),
                candle(3_000L, 102d)
        );
        List<CandleEntry> realtimeTail = Arrays.asList(
                candle(1_000L, 900d),
                candle(2_000L, 901d),
                candle(3_000L, 202d),
                candle(4_000L, 203d)
        );

        List<CandleEntry> merged = MarketChartDisplayHelper.mergeRealtimeTail(base, realtimeTail);

        assertEquals(4, merged.size());
        assertEquals(100d, merged.get(0).getClose(), 0.0001d);
        assertEquals(101d, merged.get(1).getClose(), 0.0001d);
        assertEquals(202d, merged.get(2).getClose(), 0.0001d);
        assertEquals(203d, merged.get(3).getClose(), 0.0001d);
    }

    private CandleEntry candle(long openTime, double close) {
        return candle("BTCUSDT", openTime, close);
    }

    private CandleEntry candle(String symbol, long openTime, double close) {
        return new CandleEntry(
                symbol,
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

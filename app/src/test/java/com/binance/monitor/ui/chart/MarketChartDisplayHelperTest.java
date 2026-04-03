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

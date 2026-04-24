/*
 * 验证 K 线页在推送健康时的刷新节奏和延迟展示口径，避免 ms 文案出现误导性的高低交替。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Collections;

public class MarketChartRefreshHelperAdditionalTest {

    @Test
    public void resolveAutoRefreshDelayMsShouldSlowDownWhenRealtimeIsFresh() {
        assertEquals(5_000L, MarketChartRefreshHelper.resolveAutoRefreshDelayMs(true, 5_000L, true, 10_000L));
        assertEquals(5_000L, MarketChartRefreshHelper.resolveAutoRefreshDelayMs(false, 5_000L, true, 10_000L));
    }

    @Test
    public void resolveAutoRefreshDelayMsShouldAlignToNextMinuteWhenRealtimeTailSourceIsUnavailable() {
        assertEquals(50_000L, MarketChartRefreshHelper.resolveAutoRefreshDelayMs(true, 5_000L, false, 10_000L));
        assertEquals(1_000L, MarketChartRefreshHelper.resolveAutoRefreshDelayMs(false, 5_000L, false, 59_000L));
    }

    @Test
    public void resolveDisplayedLatencyMsShouldHideLatencyWhenRequestWasSkipped() {
        CandleEntry candle = new CandleEntry(
                "BTCUSDT",
                60_000L,
                119_999L,
                100d,
                101d,
                99d,
                100.5d,
                1d,
                10d
        );
        MarketChartRefreshHelper.SyncPlan skipPlan = MarketChartRefreshHelper.resolvePlan(
                Collections.singletonList(candle),
                1,
                1,
                120_000L,
                119_500L,
                60_000L,
                false,
                true,
                MarketChartRefreshHelper.RequestReason.AUTO_REFRESH
        );
        MarketChartRefreshHelper.SyncPlan fullPlan = MarketChartRefreshHelper.resolvePlan(
                Collections.emptyList(),
                1,
                1,
                20_000L,
                0L,
                1_000L,
                false,
                false,
                MarketChartRefreshHelper.RequestReason.AUTO_REFRESH
        );

        assertEquals(-1L, MarketChartRefreshHelper.resolveDisplayedLatencyMs(skipPlan, 820L));
        assertEquals(820L, MarketChartRefreshHelper.resolveDisplayedLatencyMs(fullPlan, 820L));
    }

    @Test
    public void smoothDisplayedLatencyMsShouldReduceHighLowAlternation() {
        long first = MarketChartRefreshHelper.smoothDisplayedLatencyMs(-1L, 80L);
        long second = MarketChartRefreshHelper.smoothDisplayedLatencyMs(first, 980L);
        long third = MarketChartRefreshHelper.smoothDisplayedLatencyMs(second, 90L);

        assertEquals(80L, first);
        assertEquals(305L, second);
        assertEquals(251L, third);
    }

    @Test
    public void shouldSkipRequestOnResumeShouldRequireFreshnessAndNoPendingRepair() {
        assertEquals(true, MarketChartRefreshHelper.shouldSkipRequestOnResume(true, true, false));
        assertEquals(false, MarketChartRefreshHelper.shouldSkipRequestOnResume(false, true, false));
        assertEquals(false, MarketChartRefreshHelper.shouldSkipRequestOnResume(true, false, false));
        assertEquals(false, MarketChartRefreshHelper.shouldSkipRequestOnResume(true, true, true));
    }
}

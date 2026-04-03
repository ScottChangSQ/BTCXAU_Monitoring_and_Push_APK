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
        assertEquals(15_000L, MarketChartRefreshHelper.resolveAutoRefreshDelayMs(true, 5_000L));
        assertEquals(5_000L, MarketChartRefreshHelper.resolveAutoRefreshDelayMs(false, 5_000L));
    }

    @Test
    public void resolveDisplayedLatencyMsShouldHideLatencyWhenRequestWasSkipped() {
        CandleEntry candle = new CandleEntry(
                "BTCUSDT",
                1_000L,
                1_999L,
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
                20_000L,
                19_500L,
                1_000L,
                false
        );
        MarketChartRefreshHelper.SyncPlan fullPlan = MarketChartRefreshHelper.resolvePlan(
                Collections.emptyList(),
                1,
                1,
                20_000L,
                0L,
                1_000L,
                false
        );

        assertEquals(-1L, MarketChartRefreshHelper.resolveDisplayedLatencyMs(skipPlan, 820L));
        assertEquals(820L, MarketChartRefreshHelper.resolveDisplayedLatencyMs(fullPlan, 820L));
    }
}

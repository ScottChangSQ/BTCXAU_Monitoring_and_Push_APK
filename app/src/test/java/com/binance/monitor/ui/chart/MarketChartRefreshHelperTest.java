/*
 * 行情图页刷新策略测试，确保前台优先吃推送，只有必要时才走增量补齐或整窗回补。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MarketChartRefreshHelperTest {
    private static final long MINUTE = 60_000L;
    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void resolvePlanShouldSkipWhenLocalWindowIsFullAndRealtimeClosedKlineIsFresh() {
        List<CandleEntry> local = createSeries(1_500, NOW - MINUTE * 30L, MINUTE);

        MarketChartRefreshHelper.SyncPlan plan = MarketChartRefreshHelper.resolvePlan(
                local,
                1_500,
                1_500,
                NOW,
                NOW - 30_000L,
                MINUTE,
                false
        );

        assertEquals(MarketChartRefreshHelper.SyncMode.SKIP, plan.mode);
        assertEquals(-1L, plan.startTimeInclusive);
    }

    @Test
    public void resolvePlanShouldUseIncrementalWhenRealtimeClosedKlineIsStaleButWindowStillCoversGap() {
        List<CandleEntry> local = createSeries(1_500, NOW - MINUTE * 10L, MINUTE);
        long latestOpenTime = local.get(local.size() - 1).getOpenTime();

        MarketChartRefreshHelper.SyncPlan plan = MarketChartRefreshHelper.resolvePlan(
                local,
                1_500,
                1_500,
                NOW,
                NOW - MINUTE * 3L,
                MINUTE,
                false
        );

        assertEquals(MarketChartRefreshHelper.SyncMode.INCREMENTAL, plan.mode);
        assertEquals(latestOpenTime, plan.startTimeInclusive);
    }

    @Test
    public void resolvePlanShouldSkipWhenWarmPreviewExistsAndRealtimeClosedKlineIsFresh() {
        List<CandleEntry> local = createSeries(300, NOW - MINUTE * 30L, MINUTE);

        MarketChartRefreshHelper.SyncPlan plan = MarketChartRefreshHelper.resolvePlan(
                local,
                1_500,
                1_500,
                NOW,
                NOW - 30_000L,
                MINUTE,
                false
        );

        assertEquals(MarketChartRefreshHelper.SyncMode.SKIP, plan.mode);
    }

    @Test
    public void resolvePlanShouldFallbackToFullWhenGapAlreadyExceedsLocalCoverage() {
        List<CandleEntry> local = createSeries(1_500, NOW - MINUTE * 2_000L, MINUTE);

        MarketChartRefreshHelper.SyncPlan plan = MarketChartRefreshHelper.resolvePlan(
                local,
                1_500,
                1_500,
                NOW,
                NOW - MINUTE * 3L,
                MINUTE,
                false
        );

        assertEquals(MarketChartRefreshHelper.SyncMode.FULL, plan.mode);
    }

    @Test
    public void resolvePlanShouldKeepYearAggregateOnFullFallbackWhenRealtimeIsNotFresh() {
        List<CandleEntry> local = createSeries(1_500, NOW - 365L * 24L * 60L * MINUTE, 30L * 24L * 60L * MINUTE);

        MarketChartRefreshHelper.SyncPlan plan = MarketChartRefreshHelper.resolvePlan(
                local,
                1_500,
                1_500,
                NOW,
                NOW - MINUTE * 5L,
                365L * 24L * 60L * MINUTE,
                true
        );

        assertEquals(MarketChartRefreshHelper.SyncMode.FULL, plan.mode);
    }

    private List<CandleEntry> createSeries(int count, long lastOpenTime, long intervalMs) {
        List<CandleEntry> result = new ArrayList<>();
        long startOpenTime = lastOpenTime - (Math.max(0, count - 1) * intervalMs);
        for (int i = 0; i < count; i++) {
            long openTime = startOpenTime + i * intervalMs;
            result.add(new CandleEntry(
                    "BTCUSDT",
                    openTime,
                    openTime + intervalMs - 1L,
                    100d,
                    101d,
                    99d,
                    100.5d,
                    1d,
                    10d
            ));
        }
        return result;
    }
}

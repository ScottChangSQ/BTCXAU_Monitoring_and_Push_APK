/*
 * 历史交易分布与持仓时间分布测试，确保散点与分桶统计按交易区间稳定输出。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TradeDistributionAnalyticsTest {

    @Test
    public void buildTradeScatterPointsShouldUseTradeWindowDrawdownAndReturn() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d),
                new CurvePoint(2_000L, 110d, 109d),
                new CurvePoint(3_000L, 88d, 95d),
                new CurvePoint(4_000L, 112d, 111d),
                new CurvePoint(5_000L, 121d, 120d)
        );

        TradeRecordItem trade = new TradeRecordItem(
                5_000L,
                "BTCUSD",
                "BTCUSD",
                "Buy",
                110d,
                1d,
                100d,
                0d,
                "",
                20d,
                2_000L,
                5_000L,
                0d,
                100d,
                120d,
                1L,
                2L,
                3L,
                1
        );

        List<CurveAnalyticsHelper.TradeScatterPoint> scatter =
                CurveAnalyticsHelper.buildTradeScatterPoints(Arrays.asList(trade), points);

        assertEquals(1, scatter.size());
        assertEquals(-0.20d, scatter.get(0).getMaxDrawdownRate(), 1e-9);
        assertEquals(0.20d, scatter.get(0).getReturnRate(), 1e-9);
        assertEquals(20d, scatter.get(0).getProfitAmount(), 1e-9);
        assertEquals(2_000L, scatter.get(0).getOpenTime());
        assertEquals(5_000L, scatter.get(0).getCloseTime());
        assertEquals(100d, scatter.get(0).getOpenPrice(), 1e-9);
        assertEquals(120d, scatter.get(0).getClosePrice(), 1e-9);
        assertEquals(3_000L, scatter.get(0).getHoldingDurationMs());
        assertTrue(scatter.get(0).isPositive());
    }

    @Test
    public void buildHoldingDurationDistributionShouldBucketTradesByDuration() {
        TradeRecordItem shortTrade = new TradeRecordItem(
                3_600_000L,
                "BTCUSD",
                "BTCUSD",
                "Buy",
                100d,
                1d,
                100d,
                0d,
                "",
                5d,
                0L,
                3_600_000L,
                0d
        );
        TradeRecordItem swingTrade = new TradeRecordItem(
                3L * 24L * 60L * 60L * 1_000L,
                "XAUUSD",
                "XAUUSD",
                "Sell",
                100d,
                1d,
                100d,
                0d,
                "",
                8d,
                0L,
                3L * 24L * 60L * 60L * 1_000L,
                0d
        );
        TradeRecordItem longTrade = new TradeRecordItem(
                10L * 24L * 60L * 60L * 1_000L,
                "BTCUSD",
                "BTCUSD",
                "Buy",
                100d,
                1d,
                100d,
                0d,
                "",
                -4d,
                0L,
                10L * 24L * 60L * 60L * 1_000L,
                0d
        );

        List<CurveAnalyticsHelper.DurationBucket> buckets =
                CurveAnalyticsHelper.buildHoldingDurationDistribution(
                        Arrays.asList(shortTrade, swingTrade, longTrade));

        assertEquals(7, buckets.size());
        assertEquals("30分-4时", buckets.get(1).getLabel());
        assertEquals("1-3天", buckets.get(4).getLabel());
        assertEquals("7天+", buckets.get(6).getLabel());
        assertEquals(1, buckets.get(1).getCount());
        assertEquals(1, buckets.get(1).getWinCount());
        assertEquals(0, buckets.get(1).getLossCount());
        assertEquals(1, buckets.get(4).getCount());
        assertEquals(1, buckets.get(4).getWinCount());
        assertEquals(0, buckets.get(4).getLossCount());
        assertEquals(1, buckets.get(6).getCount());
        assertEquals(0, buckets.get(6).getWinCount());
        assertEquals(1, buckets.get(6).getLossCount());
    }

    @Test
    public void buildTradeScatterPointsShouldUseOpenPriceForReturnRateAndNormalizeSecondTimestamps() {
        TradeRecordItem trade = new TradeRecordItem(
                1_704_074_400L,
                "BTCUSD",
                "BTCUSD",
                "Buy",
                120d,
                0.5d,
                5_000d,
                0d,
                "",
                20d,
                1_704_067_200L,
                1_704_074_400L,
                0d,
                100d,
                120d,
                11L,
                22L,
                33L,
                1
        );

        List<CurveAnalyticsHelper.TradeScatterPoint> scatter =
                CurveAnalyticsHelper.buildTradeScatterPoints(Arrays.asList(trade), null);

        assertEquals(1, scatter.size());
        assertEquals(0.40d, scatter.get(0).getReturnRate(), 1e-9);
        assertEquals(7_200_000L, scatter.get(0).getHoldingDurationMs());
    }

    @Test
    public void buildTradeScatterPointsShouldNotReuseReturnRateAsDrawdownWhenCurveWindowMissing() {
        TradeRecordItem trade = new TradeRecordItem(
                5_000L,
                "BTCUSD",
                "BTCUSD",
                "Buy",
                120d,
                1d,
                100d,
                0d,
                "",
                -10d,
                2_000L,
                5_000L,
                0d,
                100d,
                90d,
                1L,
                2L,
                3L,
                1
        );

        List<CurveAnalyticsHelper.TradeScatterPoint> scatter =
                CurveAnalyticsHelper.buildTradeScatterPoints(Arrays.asList(trade), Arrays.asList(
                        new CurvePoint(1_000L, 100d, 100d)
                ));

        assertEquals(1, scatter.size());
        assertEquals(-0.10d, scatter.get(0).getReturnRate(), 1e-9);
        assertEquals(0d, scatter.get(0).getMaxDrawdownRate(), 1e-9);
    }

    @Test
    public void buildHoldingDurationDistributionShouldNormalizeSecondBasedLifecycleTimestamps() {
        TradeRecordItem trade = new TradeRecordItem(
                1_704_074_400L,
                "BTCUSD",
                "BTCUSD",
                "Buy",
                120d,
                0.5d,
                5_000d,
                0d,
                "",
                20d,
                1_704_067_200L,
                1_704_074_400L,
                0d,
                100d,
                120d,
                11L,
                22L,
                33L,
                1
        );

        List<CurveAnalyticsHelper.DurationBucket> buckets =
                CurveAnalyticsHelper.buildHoldingDurationDistribution(Arrays.asList(trade));

        assertEquals(0, buckets.get(0).getCount());
        assertEquals(1, buckets.get(1).getCount());
    }
}

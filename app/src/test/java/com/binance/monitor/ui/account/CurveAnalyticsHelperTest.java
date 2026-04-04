/*
 * 账户曲线分析辅助逻辑测试，确保最大回撤、高亮区间与日收益序列按当前曲线正确计算。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CurveAnalyticsHelperTest {

    @Test
    public void resolveMaxDrawdownSegmentShouldUseEquityBalanceGapAtSameTimestamp() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d),
                new CurvePoint(2_000L, 80d, 100d),
                new CurvePoint(3_000L, 90d, 100d),
                new CurvePoint(4_000L, 110d, 100d)
        );

        CurveAnalyticsHelper.DrawdownSegment segment =
                CurveAnalyticsHelper.resolveMaxDrawdownSegment(points);

        assertNotNull(segment);
        assertEquals(2_000L, segment.getPeakTimestamp());
        assertEquals(2_000L, segment.getValleyTimestamp());
        assertEquals(100d, segment.getPeakEquity(), 1e-9);
        assertEquals(80d, segment.getValleyEquity(), 1e-9);
        assertEquals(-0.20d, segment.getDrawdownRate(), 1e-9);
    }

    @Test
    public void buildDrawdownSeriesShouldUseEquityBalanceGapAndClampPositiveValuesToZero() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d),
                new CurvePoint(2_000L, 120d, 100d),
                new CurvePoint(3_000L, 80d, 100d),
                new CurvePoint(4_000L, 95d, 100d)
        );

        List<CurveAnalyticsHelper.DrawdownPoint> series =
                CurveAnalyticsHelper.buildDrawdownSeries(points);

        assertEquals(4, series.size());
        assertEquals(0d, series.get(0).getDrawdownRate(), 1e-9);
        assertEquals(0d, series.get(1).getDrawdownRate(), 1e-9);
        assertEquals(-0.20d, series.get(2).getDrawdownRate(), 1e-9);
        assertEquals(-0.05d, series.get(3).getDrawdownRate(), 1e-9);
    }

    @Test
    public void buildDailyReturnSeriesShouldUseDailyCloseEquity() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(buildTime(2026, 3, 1, 9), 100d, 100d),
                new CurvePoint(buildTime(2026, 3, 1, 22), 105d, 104d),
                new CurvePoint(buildTime(2026, 3, 2, 21), 115d, 113d),
                new CurvePoint(buildTime(2026, 3, 3, 20), 103.5d, 103d)
        );

        List<CurveAnalyticsHelper.DailyReturnPoint> series =
                CurveAnalyticsHelper.buildDailyReturnSeries(points);

        assertEquals(2, series.size());
        assertEquals(0.0952380952d, series.get(0).getReturnRate(), 1e-6);
        assertEquals(-0.10d, series.get(1).getReturnRate(), 1e-6);
    }

    @Test
    public void buildHoldingDurationDistributionShouldCountZeroDurationTradesInFirstBucket() {
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        buildTime(2026, 3, 1, 9),
                        "BTC",
                        "BTC",
                        "Buy",
                        66_000d,
                        0.01d,
                        660d,
                        0d,
                        "",
                        10d,
                        buildTime(2026, 3, 1, 9),
                        buildTime(2026, 3, 1, 9),
                        0d,
                        66_000d,
                        66_100d
                )
        );

        List<CurveAnalyticsHelper.DurationBucket> buckets =
                CurveAnalyticsHelper.buildHoldingDurationDistribution(trades);

        assertEquals(1, buckets.get(0).getCount());
        assertEquals(1, buckets.get(0).getWinCount());
    }

    @Test
    public void buildTradeScatterPointsShouldUseBtcNotionalFormulaForReturnRate() {
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        2_000L,
                        "BTCUSD",
                        "BTCUSD",
                        "Sell",
                        120d,
                        0.1d,
                        12d,
                        0d,
                        "",
                        8d,
                        1_000L,
                        2_000L,
                        0d,
                        100d,
                        120d
                )
        );

        List<CurveAnalyticsHelper.TradeScatterPoint> points =
                CurveAnalyticsHelper.buildTradeScatterPoints(trades, null);

        assertEquals(1, points.size());
        assertEquals(0.8d, points.get(0).getReturnRate(), 1e-9);
    }

    @Test
    public void buildTradeScatterPointsShouldUseXauContractMultiplierForReturnRate() {
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        2_000L,
                        "XAUUSD",
                        "XAUUSD",
                        "Sell",
                        2050d,
                        0.01d,
                        2050d,
                        0d,
                        "",
                        20d,
                        1_000L,
                        2_000L,
                        0d,
                        2000d,
                        2050d
                )
        );

        List<CurveAnalyticsHelper.TradeScatterPoint> points =
                CurveAnalyticsHelper.buildTradeScatterPoints(trades, null);

        assertEquals(1, points.size());
        assertEquals(0.01d, points.get(0).getReturnRate(), 1e-9);
    }

    private long buildTime(int year, int month, int day, int hourOfDay) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.YEAR, year);
        calendar.set(java.util.Calendar.MONTH, month - 1);
        calendar.set(java.util.Calendar.DAY_OF_MONTH, day);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}

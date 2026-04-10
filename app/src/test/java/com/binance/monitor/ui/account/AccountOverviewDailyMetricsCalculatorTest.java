/*
 * 账户概览当日指标辅助测试，确保“当日盈亏/当日收益率”统一使用 APP 本地今日口径。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public class AccountOverviewDailyMetricsCalculatorTest {

    @Test
    public void calculateShouldOnlyCountTradesClosedTodayWithStorageFee() {
        long now = buildTime(2026, 4, 9, 12, 0);
        List<TradeRecordItem> trades = Arrays.asList(
                trade(buildTime(2026, 4, 9, 9, 0), 120d, -5d),
                trade(buildTime(2026, 4, 8, 23, 30), 200d, 0d),
                trade(buildTime(2026, 4, 10, 1, 0), 300d, 0d)
        );

        AccountOverviewDailyMetricsCalculator.OverviewDailyValues values =
                AccountOverviewDailyMetricsCalculator.calculate(
                        trades,
                        Collections.emptyList(),
                        now,
                        TimeZone.getTimeZone("Asia/Shanghai")
                );

        assertEquals(115d, values.getTodayPnl(), 1e-9);
    }

    @Test
    public void calculateShouldUseLatestBalanceBeforeTodayStartAsReturnDenominator() {
        long now = buildTime(2026, 4, 9, 12, 0);
        List<TradeRecordItem> trades = Collections.singletonList(
                trade(buildTime(2026, 4, 9, 10, 0), 100d, 0d)
        );
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(buildTime(2026, 4, 8, 23, 0), 980d, 1_000d),
                new CurvePoint(buildTime(2026, 4, 9, 8, 0), 1_060d, 1_050d),
                new CurvePoint(buildTime(2026, 4, 9, 12, 0), 1_120d, 1_100d)
        );

        AccountOverviewDailyMetricsCalculator.OverviewDailyValues values =
                AccountOverviewDailyMetricsCalculator.calculate(
                        trades,
                        points,
                        now,
                        TimeZone.getTimeZone("Asia/Shanghai")
                );

        assertEquals(100d, values.getTodayPnl(), 1e-9);
        assertEquals(0.10d, values.getTodayReturnRate(), 1e-9);
    }

    @Test
    public void calculateShouldFallbackToFirstTodayBalanceWhenNoEarlierCurveExists() {
        long now = buildTime(2026, 4, 9, 12, 0);
        List<TradeRecordItem> trades = Collections.singletonList(
                trade(buildTime(2026, 4, 9, 10, 0), 60d, 0d)
        );
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(buildTime(2026, 4, 9, 1, 0), 1_180d, 1_200d),
                new CurvePoint(buildTime(2026, 4, 9, 12, 0), 1_250d, 1_260d)
        );

        AccountOverviewDailyMetricsCalculator.OverviewDailyValues values =
                AccountOverviewDailyMetricsCalculator.calculate(
                        trades,
                        points,
                        now,
                        TimeZone.getTimeZone("Asia/Shanghai")
                );

        assertEquals(0.05d, values.getTodayReturnRate(), 1e-9);
    }

    private static TradeRecordItem trade(long closeTime, double profit, double storageFee) {
        return new TradeRecordItem(
                closeTime,
                "BTCUSD",
                "BTCUSD",
                "Buy",
                86_000d,
                0.1d,
                8_600d,
                0d,
                "",
                profit,
                closeTime - 60_000L,
                closeTime,
                storageFee,
                85_000d,
                86_000d
        );
    }

    private static long buildTime(int year, int month, int day, int hour, int minute) {
        java.util.Calendar calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.set(java.util.Calendar.YEAR, year);
        calendar.set(java.util.Calendar.MONTH, month - 1);
        calendar.set(java.util.Calendar.DAY_OF_MONTH, day);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
        calendar.set(java.util.Calendar.MINUTE, minute);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.set(java.util.Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}

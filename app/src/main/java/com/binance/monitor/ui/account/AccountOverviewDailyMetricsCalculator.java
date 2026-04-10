/*
 * 账户概览当日指标计算辅助，统一按 APP 本地交易和曲线真值计算当日盈亏与当日收益率。
 * 供账户统计页概览展示复用，避免继续直接透传服务端日指标口径。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

final class AccountOverviewDailyMetricsCalculator {

    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;

    private AccountOverviewDailyMetricsCalculator() {
    }

    // 统一按“今日已平仓盈亏 / 今日起点结余”计算账户概览里的当日口径。
    @NonNull
    static OverviewDailyValues calculate(@Nullable List<TradeRecordItem> trades,
                                         @Nullable List<CurvePoint> curvePoints,
                                         long nowMs,
                                         @NonNull TimeZone timeZone) {
        long dayStartMs = resolveDayStart(nowMs, timeZone);
        long nextDayStartMs = dayStartMs + ONE_DAY_MS;
        double todayPnl = resolveTodayPnl(trades, dayStartMs, nextDayStartMs);
        double todayReturnRate = AccountPeriodReturnHelper.resolvePeriodReturnRate(
                curvePoints,
                dayStartMs,
                todayPnl
        );
        return new OverviewDailyValues(todayPnl, todayReturnRate);
    }

    // 把当前时刻收口到当天 00:00:00.000，统一使用页面时区口径。
    private static long resolveDayStart(long nowMs, TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(nowMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    // 今日盈亏只统计今天已平仓成交的盈亏与库存费。
    private static double resolveTodayPnl(@Nullable List<TradeRecordItem> trades,
                                          long dayStartMs,
                                          long nextDayStartMs) {
        if (trades == null || trades.isEmpty()) {
            return 0d;
        }
        double sum = 0d;
        for (TradeRecordItem item : trades) {
            if (item == null) {
                continue;
            }
            long closeTime = resolveCloseTime(item);
            if (closeTime < dayStartMs || closeTime >= nextDayStartMs) {
                continue;
            }
            sum += item.getProfit() + item.getStorageFee();
        }
        return sum;
    }

    // 成交平仓时间缺失时退回记录时间，保持本地成交模型的一致读法。
    private static long resolveCloseTime(TradeRecordItem item) {
        if (item == null) {
            return 0L;
        }
        return item.getCloseTime() > 0L ? item.getCloseTime() : item.getTimestamp();
    }

    static final class OverviewDailyValues {
        private final double todayPnl;
        private final double todayReturnRate;

        OverviewDailyValues(double todayPnl, double todayReturnRate) {
            this.todayPnl = todayPnl;
            this.todayReturnRate = todayReturnRate;
        }

        double getTodayPnl() {
            return todayPnl;
        }

        double getTodayReturnRate() {
            return todayReturnRate;
        }
    }
}

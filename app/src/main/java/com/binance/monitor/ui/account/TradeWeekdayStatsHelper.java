/*
 * 按星期统计历史交易盈亏，供账户统计页的星期盈亏表复用。
 * 统一负责按开仓时间或平仓时间聚合，并输出周一到周日的固定顺序结果。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;

import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

final class TradeWeekdayStatsHelper {

    enum TimeBasis {
        OPEN_TIME,
        CLOSE_TIME
    }

    private static final String[] WEEKDAY_LABELS = new String[]{
            "周一", "周二", "周三", "周四", "周五", "周六", "周日"
    };

    private TradeWeekdayStatsHelper() {
    }

    // 按指定时间基准聚合交易，始终返回 7 行，避免界面因为缺少某天数据而抖动。
    @NonNull
    static List<Row> buildRows(List<TradeRecordItem> trades, TimeBasis basis) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < WEEKDAY_LABELS.length; i++) {
            rows.add(new Row(WEEKDAY_LABELS[i]));
        }
        if (trades == null || trades.isEmpty()) {
            return rows;
        }
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        for (TradeRecordItem item : trades) {
            if (item == null) {
                continue;
            }
            long targetTime = resolveTargetTime(item, basis);
            if (targetTime <= 0L) {
                continue;
            }
            calendar.setTimeInMillis(targetTime);
            int index = mapCalendarDayToRowIndex(calendar.get(Calendar.DAY_OF_WEEK));
            if (index < 0 || index >= rows.size()) {
                continue;
            }
            Row current = rows.get(index);
            double pnl = item.getProfit() + item.getStorageFee();
            current.tradeCount++;
            current.totalPnl += pnl;
            if (pnl > 0d) {
                current.winCount++;
            } else if (pnl < 0d) {
                current.lossCount++;
            } else {
                current.flatCount++;
            }
        }
        return rows;
    }

    // 统一解析聚合时间，缺失时退回记录时间，避免新旧数据格式不一致时直接丢行。
    private static long resolveTargetTime(TradeRecordItem item, TimeBasis basis) {
        if (basis == TimeBasis.OPEN_TIME) {
            long openTime = normalizePossibleEpochMs(item.getOpenTime());
            return openTime > 0L ? openTime : normalizePossibleEpochMs(item.getTimestamp());
        }
        long closeTime = normalizePossibleEpochMs(item.getCloseTime());
        return closeTime > 0L ? closeTime : normalizePossibleEpochMs(item.getTimestamp());
    }

    // 把系统星期映射成“周一到周日”的固定表格顺序。
    private static int mapCalendarDayToRowIndex(int calendarDay) {
        switch (calendarDay) {
            case Calendar.MONDAY:
                return 0;
            case Calendar.TUESDAY:
                return 1;
            case Calendar.WEDNESDAY:
                return 2;
            case Calendar.THURSDAY:
                return 3;
            case Calendar.FRIDAY:
                return 4;
            case Calendar.SATURDAY:
                return 5;
            case Calendar.SUNDAY:
                return 6;
            default:
                return -1;
        }
    }

    // 兼容旧缓存里残留的秒级时间戳，避免星期统计落到 1970 年。
    private static long normalizePossibleEpochMs(long value) {
        if (value >= 1_000_000_000L && value < 10_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }

    static final class Row {
        final String label;
        int tradeCount;
        int winCount;
        int lossCount;
        int flatCount;
        double totalPnl;

        private Row(String label) {
            this.label = label == null ? "" : label;
        }
    }
}

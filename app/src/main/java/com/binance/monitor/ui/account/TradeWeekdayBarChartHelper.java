/*
 * 星期盈亏柱状图数据辅助，负责把星期聚合结果转换成图表直接可用的数据。
 * 供 AccountStatsBridgeActivity 和 TradeWeekdayBarChartView 复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

final class TradeWeekdayBarChartHelper {

    static final class Entry {
        final String label;
        final double pnl;
        final String summary;

        private Entry(String label, double pnl, String summary) {
            this.label = label == null ? "" : label;
            this.pnl = pnl;
            this.summary = summary == null ? "" : summary;
        }
    }

    private TradeWeekdayBarChartHelper() {
    }

    // 把固定 7 天的星期聚合结果转成柱状图数据，并保留简短摘要供图表展示。
    @NonNull
    static List<Entry> buildEntries(List<TradeWeekdayStatsHelper.Row> rows) {
        List<Entry> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (TradeWeekdayStatsHelper.Row row : rows) {
            if (row == null) {
                continue;
            }
            result.add(new Entry(
                    row.label,
                    row.totalPnl,
                    row.tradeCount + "次  盈" + row.winCount + "/亏" + row.lossCount
            ));
        }
        return result;
    }
}

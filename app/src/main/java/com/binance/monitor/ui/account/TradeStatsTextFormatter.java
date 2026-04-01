/*
 * 交易统计文案格式化，负责把统计区的数值压成更紧凑的一行文本。
 * 供 AccountStatsBridgeActivity 和对应单测复用。
 */
package com.binance.monitor.ui.account;

import com.binance.monitor.util.FormatUtils;

import java.util.Locale;

public final class TradeStatsTextFormatter {

    private TradeStatsTextFormatter() {
    }

    // 把交易次数和占比合并为一行。
    public static String formatTradeRatioMetric(int count, double ratio) {
        return String.format(Locale.getDefault(), "(%d次) %.2f%%", Math.max(0, count), ratio * 100d);
    }

    // 把连续次数和金额合并为一行。
    public static String formatStreakMetric(int count, double amount) {
        if (count <= 0) {
            return "(0次) --";
        }
        return String.format(Locale.getDefault(), "(%d次) %s", count, FormatUtils.formatSignedMoney(amount));
    }

    // 把毛利和毛损压成一条。
    public static String formatGrossPair(double grossProfit, double grossLoss) {
        return FormatUtils.formatSignedMoney(grossProfit) + " / " + FormatUtils.formatSignedMoney(grossLoss);
    }
}

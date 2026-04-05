/*
 * 交易统计样式辅助，负责决定连续盈亏类指标从哪里开始着色。
 * 供交易统计适配器和对应单测复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

public final class TradeStatsMetricStyleHelper {

    private TradeStatsMetricStyleHelper() {
    }

    // 连续盈亏类指标只从金额符号开始着色，次数部分保持中性色。
    public static int resolveStreakTintStart(@Nullable String label, @Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        int signPos = raw.indexOf('+');
        if (signPos < 0) {
            signPos = raw.indexOf('-');
        }
        return Math.max(0, signPos);
    }
}

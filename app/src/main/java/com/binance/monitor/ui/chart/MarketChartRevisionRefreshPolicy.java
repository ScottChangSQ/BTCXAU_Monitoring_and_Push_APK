/*
 * 图表页 revision 刷新策略，负责把“是否需要继续回源拉 K 线”收口成统一 gate。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class MarketChartRevisionRefreshPolicy {

    private MarketChartRevisionRefreshPolicy() {
    }

    // 只有“当前产品市场窗口已经前进”或“当前显示结果已经过期”时，才允许定时器继续触发远端请求。
    public static boolean shouldRequestKlines(@Nullable String currentMarketWindowSignature,
                                              @Nullable String appliedMarketWindowSignature,
                                              long appliedUpdatedAt,
                                              long nowMs,
                                              long staleAfterMs) {
        String runtimeSignature = trimToEmpty(currentMarketWindowSignature);
        String appliedSignature = trimToEmpty(appliedMarketWindowSignature);
        if (runtimeSignature.isEmpty()) {
            return true;
        }
        if (!runtimeSignature.isEmpty() && !runtimeSignature.equals(appliedSignature)) {
            return true;
        }
        long safeAppliedUpdatedAt = Math.max(0L, appliedUpdatedAt);
        if (safeAppliedUpdatedAt <= 0L) {
            return true;
        }
        long safeStaleAfterMs = Math.max(1_000L, staleAfterMs);
        return nowMs - safeAppliedUpdatedAt >= safeStaleAfterMs;
    }

    @NonNull
    static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

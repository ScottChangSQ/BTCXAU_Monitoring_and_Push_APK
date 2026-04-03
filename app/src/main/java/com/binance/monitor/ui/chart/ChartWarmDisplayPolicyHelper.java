/*
 * 图表本地预显示策略辅助，负责限制不同周期之间的本地聚合来源。
 * 与 MarketChartActivity 配合，避免周/月/年线继续被分钟底稿硬聚合成单根假数据。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

final class ChartWarmDisplayPolicyHelper {

    private ChartWarmDisplayPolicyHelper() {
    }

    // 限制长周期预显示来源，周/月线只接受日线底稿，年线只接受月线底稿。
    static boolean canWarmDisplayFrom(@Nullable String sourceKey,
                                      boolean sourceYearAggregate,
                                      @Nullable String targetKey,
                                      boolean targetYearAggregate) {
        if (sourceYearAggregate || sourceKey == null || targetKey == null) {
            return false;
        }
        String normalizedSource = normalizeKey(sourceKey);
        String normalizedTarget = normalizeKey(targetKey);
        if (normalizedSource.isEmpty() || normalizedTarget.isEmpty() || normalizedSource.equals(normalizedTarget)) {
            return false;
        }
        long sourceDurationMs = resolveIntervalDurationMs(normalizedSource);
        long targetDurationMs = resolveIntervalDurationMs(normalizedTarget);
        if (sourceDurationMs <= 0L || targetDurationMs <= 0L || sourceDurationMs > targetDurationMs) {
            return false;
        }
        if (targetYearAggregate) {
            return "1M".equals(normalizedSource);
        }
        if ("1w".equals(normalizedTarget) || "1M".equals(normalizedTarget)) {
            return "1d".equals(normalizedSource);
        }
        return targetDurationMs % sourceDurationMs == 0L;
    }

    // 分钟底稿实时补尾只覆盖 1d 及以下周期，避免长周期继续被短窗口误聚合。
    static boolean canRefreshFromMinuteTail(@Nullable String targetKey, boolean targetYearAggregate) {
        if (targetYearAggregate) {
            return false;
        }
        long targetDurationMs = resolveIntervalDurationMs(targetKey);
        return targetDurationMs > 0L && targetDurationMs <= 24L * 60L * 60_000L;
    }

    // 仅当当前窗口为空，或用户切换到了新的交易对/周期时，才需要重新做本地预显示。
    static boolean shouldWarmDisplay(boolean noVisibleWindow, boolean dataKeyChanged) {
        return noVisibleWindow || dataKeyChanged;
    }

    private static long resolveIntervalDurationMs(@Nullable String intervalKey) {
        String normalized = normalizeKey(intervalKey);
        if ("1M".equals(normalized)) {
            return 30L * 24L * 60L * 60_000L;
        }
        if ("1y".equals(normalized)) {
            return 365L * 24L * 60L * 60_000L;
        }
        if ("1m".equals(normalized)) {
            return 60_000L;
        }
        if ("5m".equals(normalized)) {
            return 5L * 60_000L;
        }
        if ("15m".equals(normalized)) {
            return 15L * 60_000L;
        }
        if ("30m".equals(normalized)) {
            return 30L * 60_000L;
        }
        if ("1h".equals(normalized)) {
            return 60L * 60_000L;
        }
        if ("4h".equals(normalized)) {
            return 4L * 60L * 60_000L;
        }
        if ("1d".equals(normalized)) {
            return 24L * 60L * 60_000L;
        }
        if ("1w".equals(normalized)) {
            return 7L * 24L * 60L * 60_000L;
        }
        return -1L;
    }

    private static String normalizeKey(@Nullable String intervalKey) {
        if (intervalKey == null) {
            return "";
        }
        String value = intervalKey.trim();
        if ("1M".equals(value)) {
            return "1M";
        }
        return value.toLowerCase();
    }
}

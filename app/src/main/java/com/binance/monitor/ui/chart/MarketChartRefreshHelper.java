/*
 * 行情图页刷新策略辅助，负责判断当前应跳过网络、只补最近缺口，还是重新拉整窗数据。
 * 与 MarketChartActivity 的推送优先刷新链路配合，减少不必要的 REST 请求。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.List;

final class MarketChartRefreshHelper {
    private static final long REALTIME_FRESHNESS_MS = 95_000L;

    enum SyncMode {
        SKIP,
        INCREMENTAL,
        FULL
    }

    static final class SyncPlan {
        final SyncMode mode;
        final long startTimeInclusive;

        private SyncPlan(SyncMode mode, long startTimeInclusive) {
            this.mode = mode;
            this.startTimeInclusive = startTimeInclusive;
        }
    }

    private MarketChartRefreshHelper() {
    }

    // 根据本地窗口完整度和最近一根已收盘 1m 的新鲜度，决定最省请求的同步方式。
    static SyncPlan resolvePlan(@Nullable List<CandleEntry> localSeries,
                                int targetLimit,
                                int fullWindowLimit,
                                long nowMs,
                                long latestRealtimeClosedTimeMs,
                                long intervalMs,
                                boolean yearAggregate) {
        int requiredWindowSize = Math.max(1, Math.min(targetLimit, fullWindowLimit));
        boolean hasEnoughLocal = localSeries != null && localSeries.size() >= requiredWindowSize;
        boolean realtimeFresh = isRealtimeFresh(nowMs, latestRealtimeClosedTimeMs);
        if (hasEnoughLocal && realtimeFresh) {
            return new SyncPlan(SyncMode.SKIP, -1L);
        }
        if (!hasEnoughLocal || localSeries == null || localSeries.isEmpty()) {
            return new SyncPlan(SyncMode.FULL, -1L);
        }
        if (yearAggregate || intervalMs <= 0L) {
            return new SyncPlan(SyncMode.FULL, -1L);
        }
        CandleEntry latest = localSeries.get(localSeries.size() - 1);
        long latestOpenTime = latest == null ? -1L : latest.getOpenTime();
        if (latestOpenTime <= 0L) {
            return new SyncPlan(SyncMode.FULL, -1L);
        }
        long maxCoveredDurationMs = intervalMs * Math.max(1L, fullWindowLimit - 1L);
        if (nowMs - latestOpenTime > maxCoveredDurationMs) {
            return new SyncPlan(SyncMode.FULL, -1L);
        }
        return new SyncPlan(SyncMode.INCREMENTAL, latestOpenTime);
    }

    // 只要最近一根已收盘 1m 仍在合理时窗内，就认为实时推送链路健康，无需再主动打 REST。
    static boolean isRealtimeFresh(long nowMs, long latestRealtimeClosedTimeMs) {
        return latestRealtimeClosedTimeMs > 0L && nowMs - latestRealtimeClosedTimeMs <= REALTIME_FRESHNESS_MS;
    }
}

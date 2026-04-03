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
    private static final long HEALTHY_REALTIME_REFRESH_MS = 15_000L;
    private static final int LATENCY_SMOOTHING_WEIGHT_PREVIOUS = 3;
    private static final int LATENCY_SMOOTHING_WEIGHT_CURRENT = 1;

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
                                boolean yearAggregate,
                                boolean hasRealtimeTailSource) {
        boolean hasLocalSeries = localSeries != null && !localSeries.isEmpty();
        boolean realtimeFresh = isRealtimeFresh(nowMs, latestRealtimeClosedTimeMs);
        // 只有页面确实接入了实时尾部数据时，1m 才允许完全依赖本地跳过 REST。
        boolean supportsMinuteDerivedSkip = hasRealtimeTailSource && !yearAggregate && intervalMs == 60_000L;
        int requiredWindowSize = Math.max(1, Math.min(targetLimit, fullWindowLimit));
        if (hasLocalSeries && realtimeFresh && supportsMinuteDerivedSkip) {
            return new SyncPlan(SyncMode.SKIP, -1L);
        }
        if (!hasLocalSeries) {
            return new SyncPlan(SyncMode.FULL, -1L);
        }
        // 本地只有预显示短窗口时，不能走增量补尾，否则永远补不回左侧完整历史。
        if (localSeries.size() < requiredWindowSize) {
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
        if (supportsMinuteDerivedSkip && localSeries.size() >= requiredWindowSize && realtimeFresh) {
            return new SyncPlan(SyncMode.SKIP, -1L);
        }
        return new SyncPlan(SyncMode.INCREMENTAL, latestOpenTime);
    }

    // 只要最近一根已收盘 1m 仍在合理时窗内，就认为实时推送链路健康，无需再主动打 REST。
    static boolean isRealtimeFresh(long nowMs, long latestRealtimeClosedTimeMs) {
        return latestRealtimeClosedTimeMs > 0L && nowMs - latestRealtimeClosedTimeMs <= REALTIME_FRESHNESS_MS;
    }

    // 推送健康时降低主动轮询频率，减少无意义的网络请求和切周期等待。
    static long resolveAutoRefreshDelayMs(boolean realtimeFresh,
                                          long fallbackDelayMs,
                                          boolean hasRealtimeTailSource) {
        long safeFallbackDelayMs = Math.max(1_000L, fallbackDelayMs);
        if (!hasRealtimeTailSource) {
            return safeFallbackDelayMs;
        }
        return realtimeFresh
                ? Math.max(safeFallbackDelayMs, HEALTHY_REALTIME_REFRESH_MS)
                : safeFallbackDelayMs;
    }

    // 只有页面已经接入本地实时尾部时，恢复前台才允许直接复用当前分钟窗口而不立刻请求网络。
    static boolean shouldSkipRequestOnResume(boolean hasCompatibleVisible,
                                             boolean realtimeFresh,
                                             boolean hasRealtimeTailSource) {
        return hasRealtimeTailSource && hasCompatibleVisible && realtimeFresh;
    }

    // 当前轮直接跳过网络时，不再继续展示上一次 REST 请求耗时，避免 ms 文案误导。
    static long resolveDisplayedLatencyMs(@Nullable SyncPlan plan, long lastLatencyMs) {
        if (plan != null && plan.mode == SyncMode.SKIP) {
            return -1L;
        }
        return lastLatencyMs;
    }

    // 对右上角 ms 做轻量平滑，避免短时高低交替造成“网络抖动”的误判。
    static long smoothDisplayedLatencyMs(long previousDisplayedLatencyMs, long latestMeasuredLatencyMs) {
        long safeLatest = Math.max(0L, latestMeasuredLatencyMs);
        if (previousDisplayedLatencyMs < 0L) {
            return safeLatest;
        }
        long weighted = previousDisplayedLatencyMs * LATENCY_SMOOTHING_WEIGHT_PREVIOUS
                + safeLatest * LATENCY_SMOOTHING_WEIGHT_CURRENT;
        return Math.max(0L, weighted
                / (LATENCY_SMOOTHING_WEIGHT_PREVIOUS + LATENCY_SMOOTHING_WEIGHT_CURRENT));
    }
}

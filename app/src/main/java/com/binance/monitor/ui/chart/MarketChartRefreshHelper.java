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
    private static final long SOURCE_MINUTE_REFRESH_MS = 60_000L;
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
        boolean minuteSeriesBroken = supportsMinuteDerivedSkip
                && hasLocalSeries
                && hasInternalGap(localSeries, intervalMs);
        int requiredWindowSize = Math.max(1, Math.min(targetLimit, fullWindowLimit));
        if (minuteSeriesBroken) {
            return new SyncPlan(SyncMode.FULL, -1L);
        }
        if (hasLocalSeries
                && realtimeFresh
                && supportsMinuteDerivedSkip
                && localSeries.size() >= requiredWindowSize) {
            return new SyncPlan(SyncMode.SKIP, -1L);
        }
        if (!hasLocalSeries) {
            return new SyncPlan(SyncMode.FULL, -1L);
        }
        // 本地只有预显示短窗口时，不能走增量补尾，否则永远补不回左侧完整历史。
        if (localSeries.size() < requiredWindowSize) {
            return new SyncPlan(SyncMode.FULL, -1L);
        }
        if (intervalMs <= 0L) {
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

    // 只要本地 1m 窗口里存在时间断层或倒序，就不能继续按“尾部补齐”思路刷新。
    static boolean hasInternalGap(@Nullable List<CandleEntry> localSeries, long expectedGapMs) {
        if (localSeries == null || localSeries.size() < 2 || expectedGapMs <= 0L) {
            return false;
        }
        long previousOpenTime = localSeries.get(0) == null ? -1L : localSeries.get(0).getOpenTime();
        for (int i = 1; i < localSeries.size(); i++) {
            CandleEntry current = localSeries.get(i);
            if (current == null || previousOpenTime <= 0L) {
                return true;
            }
            long currentOpenTime = current.getOpenTime();
            if (currentOpenTime - previousOpenTime != expectedGapMs) {
                return true;
            }
            previousOpenTime = currentOpenTime;
        }
        return false;
    }

    // 只要最近一根已收盘 1m 仍在合理时窗内，就认为实时推送链路健康，无需再主动打 REST。
    static boolean isRealtimeFresh(long nowMs, long latestRealtimeClosedTimeMs) {
        return latestRealtimeClosedTimeMs > 0L && nowMs - latestRealtimeClosedTimeMs <= REALTIME_FRESHNESS_MS;
    }

    // 推送健康时降低主动轮询频率，减少无意义的网络请求和切周期等待。
    static long resolveAutoRefreshDelayMs(boolean realtimeFresh,
                                          long fallbackDelayMs,
                                          boolean hasRealtimeTailSource,
                                          long nowMs) {
        long safeFallbackDelayMs = Math.max(1_000L, fallbackDelayMs);
        if (!hasRealtimeTailSource) {
            return resolveNextMinuteBoundaryDelayMs(nowMs);
        }
        return realtimeFresh
                ? Math.max(safeFallbackDelayMs, HEALTHY_REALTIME_REFRESH_MS)
                : safeFallbackDelayMs;
    }

    // 页面恢复前台时，只要当前窗口仍处于上游分钟源可接受的新鲜范围内，就不应立刻重拉。
    static boolean shouldSkipRequestOnResume(boolean hasCompatibleVisible,
                                             boolean hasFreshVisibleWindow,
                                             boolean visibleSeriesNeedsRepair) {
        return hasCompatibleVisible && hasFreshVisibleWindow && !visibleSeriesNeedsRepair;
    }

    // 当前可见分钟窗口如果自身已经断档，恢复前台时必须先走一次修复请求，不能继续按“窗口新鲜”直接跳过。
    static boolean shouldForceRequestForSeriesRepair(@Nullable List<CandleEntry> visibleSeries,
                                                     long intervalMs,
                                                     boolean yearAggregate,
                                                     boolean hasRealtimeTailSource) {
        boolean supportsMinuteDerivedSkip = hasRealtimeTailSource && !yearAggregate && intervalMs == 60_000L;
        return supportsMinuteDerivedSkip && hasInternalGap(visibleSeries, intervalMs);
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

    // 无实时尾部链路时，长周期快照只会随分钟底稿推进而变化，下一次刷新对齐到最近的分钟边界即可。
    private static long resolveNextMinuteBoundaryDelayMs(long nowMs) {
        long safeNowMs = Math.max(0L, nowMs);
        long elapsedInMinute = safeNowMs % SOURCE_MINUTE_REFRESH_MS;
        long remainingMs = SOURCE_MINUTE_REFRESH_MS - elapsedInMinute;
        if (remainingMs <= 0L || remainingMs > SOURCE_MINUTE_REFRESH_MS) {
            return SOURCE_MINUTE_REFRESH_MS;
        }
        return remainingMs;
    }
}

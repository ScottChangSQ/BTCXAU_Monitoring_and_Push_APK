/*
 * 链路时序追踪器，用统一 key 串联 stream、补拉、仓库发布和前端渲染阶段。
 * 仅用于性能定位日志，不参与任何业务判断。
 */
package com.binance.monitor.util;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChainLatencyTracer {

    private static final String TAG = "ChainTrace";
    private static final int MAX_TRACE_POINTS = 512;
    private static final long TRACE_EXPIRE_MS = 180_000L;
    private static final boolean ENABLED = false;

    private static final Map<String, TracePoint> TRACE_POINTS = new LinkedHashMap<>();
    private static final Map<String, Long> PENDING_MARKET_TRIGGER_AT = new HashMap<>();
    private static long lastStreamMessageAtMs;

    private ChainLatencyTracer() {
    }

    // 记录 stream 到达节奏，并在触发市场刷新时为每个产品登记触发时间。
    public static synchronized void markStreamMessage(@Nullable String messageType, boolean shouldRefreshMarket) {
        if (!ENABLED) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long gapMs = lastStreamMessageAtMs > 0L ? now - lastStreamMessageAtMs : -1L;
        lastStreamMessageAtMs = now;
        Log.i(TAG, "stream type=" + safe(messageType)
                + " gapMs=" + gapMs
                + " refreshMarket=" + shouldRefreshMarket);
        if (shouldRefreshMarket) {
            for (String symbol : AppConstants.MONITOR_SYMBOLS) {
                PENDING_MARKET_TRIGGER_AT.put(normalizeSymbol(symbol), now);
            }
        }
        pruneLocked(now);
    }

    // 取出某个产品最近一次市场触发时间，供补拉链路计算 trigger->fetch 延迟。
    public static synchronized long consumePendingMarketTriggerAt(String symbol) {
        if (!ENABLED) {
            return -1L;
        }
        Long triggerAt = PENDING_MARKET_TRIGGER_AT.remove(normalizeSymbol(symbol));
        return triggerAt == null ? -1L : triggerAt;
    }

    // 记录补拉启动时刻。
    public static synchronized void markMarketFetchStart(String symbol,
                                                         long triggerAtMs,
                                                         long fetchStartAtMs) {
        if (!ENABLED) {
            return;
        }
        long triggerToFetchStartMs = triggerAtMs > 0L
                ? Math.max(0L, fetchStartAtMs - triggerAtMs)
                : -1L;
        Log.i(TAG, "market_fetch_start symbol=" + normalizeSymbol(symbol)
                + " triggerToFetchStartMs=" + triggerToFetchStartMs);
        pruneLocked(fetchStartAtMs);
    }

    // 记录补拉结果完成到可应用阶段的耗时。
    public static synchronized void markMarketPayloadApplied(String symbol,
                                                             long closeTimeMs,
                                                             long triggerAtMs,
                                                             long fetchStartAtMs,
                                                             long fetchDoneAtMs) {
        if (!ENABLED) {
            return;
        }
        if (closeTimeMs <= 0L) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        TracePoint point = getOrCreateLocked(symbol, closeTimeMs, now);
        if (point.payloadAppliedAtMs > 0L) {
            return;
        }
        if (triggerAtMs > 0L) {
            point.triggerAtMs = triggerAtMs;
        }
        if (fetchStartAtMs > 0L) {
            point.fetchStartAtMs = fetchStartAtMs;
        }
        if (fetchDoneAtMs > 0L) {
            point.fetchDoneAtMs = fetchDoneAtMs;
        }
        point.payloadAppliedAtMs = now;
        long fetchMs = point.fetchStartAtMs > 0L && point.fetchDoneAtMs >= point.fetchStartAtMs
                ? point.fetchDoneAtMs - point.fetchStartAtMs
                : -1L;
        long triggerToApplyMs = point.triggerAtMs > 0L ? now - point.triggerAtMs : -1L;
        Log.i(TAG, "market_apply symbol=" + normalizeSymbol(symbol)
                + " closeTime=" + closeTimeMs
                + " fetchMs=" + fetchMs
                + " triggerToApplyMs=" + triggerToApplyMs);
        pruneLocked(now);
    }

    // 记录仓库发布 K 线时刻。
    public static synchronized void markRepositoryKlinePublished(String symbol, long closeTimeMs) {
        if (!ENABLED) {
            return;
        }
        if (closeTimeMs <= 0L) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        TracePoint point = getOrCreateLocked(symbol, closeTimeMs, now);
        if (point.repoPublishedAtMs > 0L) {
            return;
        }
        point.repoPublishedAtMs = now;
        long applyToRepoMs = point.payloadAppliedAtMs > 0L ? now - point.payloadAppliedAtMs : -1L;
        Log.i(TAG, "repo_kline symbol=" + normalizeSymbol(symbol)
                + " closeTime=" + closeTimeMs
                + " applyToRepoMs=" + applyToRepoMs);
        pruneLocked(now);
    }

    // 记录监控主页行情模块渲染时刻。
    public static synchronized void markMainRender(String symbol, long closeTimeMs) {
        if (!ENABLED) {
            return;
        }
        if (closeTimeMs <= 0L) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        TracePoint point = getOrCreateLocked(symbol, closeTimeMs, now);
        if (point.mainRenderedAtMs > 0L) {
            return;
        }
        point.mainRenderedAtMs = now;
        long repoToMainMs = point.repoPublishedAtMs > 0L ? now - point.repoPublishedAtMs : -1L;
        long triggerToMainMs = point.triggerAtMs > 0L ? now - point.triggerAtMs : -1L;
        Log.i(TAG, "main_render symbol=" + normalizeSymbol(symbol)
                + " closeTime=" + closeTimeMs
                + " repoToMainMs=" + repoToMainMs
                + " triggerToMainMs=" + triggerToMainMs);
        pruneLocked(now);
    }

    // 记录图表页实时尾部渲染时刻。
    public static synchronized void markChartRealtimeRender(String symbol,
                                                            long closeTimeMs,
                                                            @Nullable String intervalKey) {
        if (!ENABLED) {
            return;
        }
        if (closeTimeMs <= 0L) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        TracePoint point = getOrCreateLocked(symbol, closeTimeMs, now);
        if (point.chartRenderedAtMs > 0L) {
            return;
        }
        point.chartRenderedAtMs = now;
        long repoToChartMs = point.repoPublishedAtMs > 0L ? now - point.repoPublishedAtMs : -1L;
        long triggerToChartMs = point.triggerAtMs > 0L ? now - point.triggerAtMs : -1L;
        Log.i(TAG, "chart_realtime_render symbol=" + normalizeSymbol(symbol)
                + " closeTime=" + closeTimeMs
                + " interval=" + safe(intervalKey)
                + " repoToChartMs=" + repoToChartMs
                + " triggerToChartMs=" + triggerToChartMs);
        pruneLocked(now);
    }

    // 记录悬浮窗刷新请求进入窗口管理器的时刻。
    public static synchronized void markFloatingUpdate(String symbol, long closeTimeMs) {
        if (!ENABLED) {
            return;
        }
        if (closeTimeMs <= 0L) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        TracePoint point = getOrCreateLocked(symbol, closeTimeMs, now);
        if (point.floatingUpdatedAtMs <= 0L) {
            point.floatingUpdatedAtMs = now;
            long repoToFloatingUpdateMs = point.repoPublishedAtMs > 0L ? now - point.repoPublishedAtMs : -1L;
            Log.i(TAG, "floating_update symbol=" + normalizeSymbol(symbol)
                    + " closeTime=" + closeTimeMs
                    + " repoToFloatingUpdateMs=" + repoToFloatingUpdateMs);
        }
        pruneLocked(now);
    }

    // 记录悬浮窗完成重绘时刻。
    public static synchronized void markFloatingRender(String symbol, long closeTimeMs) {
        if (!ENABLED) {
            return;
        }
        if (closeTimeMs <= 0L) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        TracePoint point = getOrCreateLocked(symbol, closeTimeMs, now);
        if (point.floatingRenderedAtMs > 0L) {
            return;
        }
        point.floatingRenderedAtMs = now;
        long updateToRenderMs = point.floatingUpdatedAtMs > 0L ? now - point.floatingUpdatedAtMs : -1L;
        long repoToFloatingRenderMs = point.repoPublishedAtMs > 0L ? now - point.repoPublishedAtMs : -1L;
        long triggerToFloatingRenderMs = point.triggerAtMs > 0L ? now - point.triggerAtMs : -1L;
        Log.i(TAG, "floating_render symbol=" + normalizeSymbol(symbol)
                + " closeTime=" + closeTimeMs
                + " updateToRenderMs=" + updateToRenderMs
                + " repoToFloatingRenderMs=" + repoToFloatingRenderMs
                + " triggerToFloatingRenderMs=" + triggerToFloatingRenderMs);
        pruneLocked(now);
    }

    // 记录图表补拉链路阶段耗时。
    public static synchronized void markChartPullPhase(String symbol,
                                                       @Nullable String intervalKey,
                                                       int requestVersion,
                                                       String phase,
                                                       long durationMs,
                                                       int itemCount) {
        if (!ENABLED) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        Log.i(TAG, "chart_pull phase=" + safe(phase)
                + " symbol=" + normalizeSymbol(symbol)
                + " interval=" + safe(intervalKey)
                + " request=" + requestVersion
                + " durationMs=" + Math.max(0L, durationMs)
                + " itemCount=" + Math.max(0, itemCount));
        pruneLocked(now);
    }

    // 记录账户页主线程渲染链路各阶段耗时，定位首帧或切回时最重的页面段落。
    public static synchronized void markAccountRenderPhase(@Nullable String accountKey,
                                                           String phase,
                                                           long durationMs,
                                                           int tradeCount,
                                                           int positionCount,
                                                           int curveCount) {
        if (!ENABLED) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        Log.i(TAG, "account_render phase=" + safe(phase)
                + " account=" + safe(accountKey)
                + " durationMs=" + Math.max(0L, durationMs)
                + " trades=" + Math.max(0, tradeCount)
                + " positions=" + Math.max(0, positionCount)
                + " curves=" + Math.max(0, curveCount));
        pruneLocked(now);
    }

    private static TracePoint getOrCreateLocked(String symbol, long closeTimeMs, long nowMs) {
        String key = buildKey(symbol, closeTimeMs);
        TracePoint point = TRACE_POINTS.get(key);
        if (point == null) {
            point = new TracePoint();
            point.symbol = normalizeSymbol(symbol);
            point.closeTimeMs = closeTimeMs;
            point.createdAtMs = nowMs;
            TRACE_POINTS.put(key, point);
        }
        return point;
    }

    private static void pruneLocked(long nowMs) {
        TRACE_POINTS.entrySet().removeIf(entry -> nowMs - entry.getValue().createdAtMs > TRACE_EXPIRE_MS);
        if (TRACE_POINTS.size() <= MAX_TRACE_POINTS) {
            return;
        }
        int overflow = TRACE_POINTS.size() - MAX_TRACE_POINTS;
        for (String key : new java.util.ArrayList<>(TRACE_POINTS.keySet())) {
            if (overflow <= 0) {
                break;
            }
            TRACE_POINTS.remove(key);
            overflow--;
        }
    }

    private static String buildKey(String symbol, long closeTimeMs) {
        return normalizeSymbol(symbol) + "#" + closeTimeMs;
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase();
    }

    private static String safe(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }

    private static final class TracePoint {
        private String symbol;
        private long closeTimeMs;
        private long createdAtMs;
        private long triggerAtMs;
        private long fetchStartAtMs;
        private long fetchDoneAtMs;
        private long payloadAppliedAtMs;
        private long repoPublishedAtMs;
        private long mainRenderedAtMs;
        private long chartRenderedAtMs;
        private long floatingUpdatedAtMs;
        private long floatingRenderedAtMs;
    }
}

/*
 * 周期投影存储，统一保存各周期正式闭合历史，以及由分钟底稿推导出来的最新尾部。
 */
package com.binance.monitor.runtime.market.truth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IntervalProjectionStore {
    private static final int MAX_RECENT_MINUTES = 5_000;

    private final Map<String, ProjectionState> projectionStates = new LinkedHashMap<>();

    // 写入指定周期的正式闭合历史。
    public synchronized void applyCanonicalSeries(@NonNull String symbol,
                                                  @NonNull String intervalKey,
                                                  @Nullable List<CandleEntry> candles,
                                                  @Nullable CandleEntry latestPatch) {
        ProjectionState state = stateForSymbol(symbol);
        String normalizedInterval = MarketTruthAggregationHelper.normalizeIntervalKey(intervalKey);
        LinkedHashMap<Long, CandleEntry> target = new LinkedHashMap<>(
                state.closedSeriesByInterval.getOrDefault(normalizedInterval, new LinkedHashMap<>())
        );
        if (candles != null) {
            for (CandleEntry candle : candles) {
                if (candle != null) {
                    target.put(candle.getOpenTime(), candle);
                }
            }
        }
        state.closedSeriesByInterval.put(normalizedInterval, target);
        if (latestPatch == null) {
            state.intervalDrafts.remove(normalizedInterval);
        } else {
            state.intervalDrafts.put(normalizedInterval, latestPatch);
        }
    }

    // 写入一根闭合分钟，供高周期尾部投影复用。
    public synchronized void onMinuteClosed(@NonNull String symbol, @NonNull CandleEntry minute) {
        ProjectionState state = stateForSymbol(symbol);
        state.recentClosedMinutes.put(minute.getOpenTime(), minute);
        while (state.recentClosedMinutes.size() > MAX_RECENT_MINUTES) {
            Long firstKey = state.recentClosedMinutes.keySet().iterator().next();
            state.recentClosedMinutes.remove(firstKey);
        }
    }

    // 读取当前周期的闭合显示历史。
    @NonNull
    public synchronized List<CandleEntry> selectClosedSeries(@NonNull String symbol,
                                                             @NonNull String intervalKey,
                                                             int limit) {
        ProjectionState state = stateForSymbol(symbol);
        String normalizedInterval = MarketTruthAggregationHelper.normalizeIntervalKey(intervalKey);
        List<CandleEntry> base = new ArrayList<>(state.closedSeriesByInterval
                .getOrDefault(normalizedInterval, new LinkedHashMap<>())
                .values());
        if (!MarketTruthAggregationHelper.supportsMinuteProjection(normalizedInterval) || state.recentClosedMinutes.isEmpty()) {
            return MarketTruthAggregationHelper.mergeSeriesByOpenTime(base, null, limit);
        }
        List<CandleEntry> projected = MarketTruthAggregationHelper.aggregate(
                new ArrayList<>(state.recentClosedMinutes.values()),
                symbol,
                normalizedInterval,
                limit
        );
        return MarketTruthAggregationHelper.mergeSeriesByOpenTime(base, projected, limit);
    }

    @NonNull
    public synchronized Map<String, List<CandleEntry>> snapshotClosedSeries(@NonNull String symbol) {
        ProjectionState state = stateForSymbol(symbol);
        Map<String, List<CandleEntry>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashMap<Long, CandleEntry>> entry : state.closedSeriesByInterval.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @NonNull
    public synchronized Map<String, CandleEntry> snapshotDrafts(@NonNull String symbol) {
        ProjectionState state = stateForSymbol(symbol);
        return Collections.unmodifiableMap(new LinkedHashMap<>(state.intervalDrafts));
    }

    @NonNull
    private ProjectionState stateForSymbol(@NonNull String symbol) {
        String normalized = normalizeSymbol(symbol);
        ProjectionState state = projectionStates.get(normalized);
        if (state != null) {
            return state;
        }
        ProjectionState created = new ProjectionState();
        projectionStates.put(normalized, created);
        return created;
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }

    private static final class ProjectionState {
        private final Map<String, LinkedHashMap<Long, CandleEntry>> closedSeriesByInterval = new LinkedHashMap<>();
        private final LinkedHashMap<Long, CandleEntry> recentClosedMinutes = new LinkedHashMap<>();
        private final Map<String, CandleEntry> intervalDrafts = new LinkedHashMap<>();
    }
}

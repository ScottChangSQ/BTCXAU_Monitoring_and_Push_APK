/*
 * 分钟底稿存储，统一维护每个品种的闭合 1m 历史和当前分钟草稿。
 */
package com.binance.monitor.runtime.market.truth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MinuteBaseStore {
    private static final int MAX_CLOSED_MINUTES = 5_000;
    private static final double EPSILON = 0.0000001d;

    private final Map<String, MinuteState> minuteStates = new LinkedHashMap<>();

    // 应用当前未闭合分钟草稿。
    @NonNull
    public synchronized ApplyResult applyDraft(@NonNull String symbol,
                                               @NonNull CandleEntry draftMinute,
                                               double latestPrice,
                                               long updatedAt) {
        MinuteState state = stateForSymbol(symbol);
        if (shouldIgnoreDraft(state, draftMinute)) {
            return toApplyResult(state);
        }
        state.latestPrice = latestPrice;
        state.updatedAt = Math.max(state.updatedAt, updatedAt);
        state.draftMinute = draftMinute;
        return toApplyResult(state);
    }

    // 应用一根闭合分钟，并同步清理同桶草稿。
    @NonNull
    public synchronized ApplyResult applyClosedMinute(@NonNull String symbol,
                                                      @NonNull CandleEntry closedMinute,
                                                      double latestPrice,
                                                      long updatedAt) {
        MinuteState state = stateForSymbol(symbol);
        boolean frontierAdvanced = wouldAdvanceFrontier(state, closedMinute);
        mergeClosedMinute(state, closedMinute);
        if (frontierAdvanced) {
            state.latestPrice = latestPrice;
            state.updatedAt = Math.max(state.updatedAt, updatedAt);
        }
        trimClosedMinutes(state);
        if (state.draftMinute != null && state.draftMinute.getOpenTime() <= closedMinute.getOpenTime()) {
            state.draftMinute = null;
        }
        return toApplyResult(state);
    }

    // 批量写入闭合分钟历史，供 REST/补档链统一更新。
    @NonNull
    public synchronized ApplyResult applyClosedMinutes(@NonNull String symbol,
                                                       @Nullable List<CandleEntry> closedMinutes,
                                                       double latestPrice,
                                                       long updatedAt) {
        MinuteState state = stateForSymbol(symbol);
        CandleEntry latestAccepted = null;
        boolean frontierAdvanced = false;
        if (closedMinutes != null) {
            for (CandleEntry candle : closedMinutes) {
                if (candle == null) {
                    continue;
                }
                if (wouldAdvanceFrontier(state, candle)) {
                    frontierAdvanced = true;
                }
                if (mergeClosedMinute(state, candle)) {
                    if (latestAccepted == null || candle.getOpenTime() >= latestAccepted.getOpenTime()) {
                        latestAccepted = candle;
                    }
                }
            }
        }
        if (latestAccepted != null && frontierAdvanced) {
            state.latestPrice = latestPrice;
            state.updatedAt = Math.max(state.updatedAt, updatedAt);
        }
        if (state.draftMinute != null
                && latestAccepted != null
                && state.draftMinute.getOpenTime() <= latestAccepted.getOpenTime()) {
            state.draftMinute = null;
        }
        trimClosedMinutes(state);
        return toApplyResult(state);
    }

    @NonNull
    public synchronized ApplyResult selectState(@NonNull String symbol) {
        return toApplyResult(stateForSymbol(symbol));
    }

    private void trimClosedMinutes(@NonNull MinuteState state) {
        while (state.closedMinutes.size() > MAX_CLOSED_MINUTES) {
            Long firstKey = state.closedMinutes.keySet().iterator().next();
            state.closedMinutes.remove(firstKey);
        }
    }

    private boolean shouldIgnoreDraft(@NonNull MinuteState state, @NonNull CandleEntry candidate) {
        CandleEntry latestClosed = latestClosedMinute(state);
        if (latestClosed != null && latestClosed.getOpenTime() >= candidate.getOpenTime()) {
            return true;
        }
        CandleEntry currentDraft = state.draftMinute;
        if (currentDraft == null) {
            return false;
        }
        return candidate.getOpenTime() < currentDraft.getOpenTime();
    }

    private boolean mergeClosedMinute(@NonNull MinuteState state, @NonNull CandleEntry candidate) {
        CandleEntry existing = state.closedMinutes.get(candidate.getOpenTime());
        if (existing != null && !shouldReplaceClosed(existing, candidate)) {
            return false;
        }
        state.closedMinutes.put(candidate.getOpenTime(), candidate);
        return true;
    }

    private boolean wouldAdvanceFrontier(@NonNull MinuteState state, @NonNull CandleEntry candidate) {
        CandleEntry latestClosed = latestClosedMinute(state);
        long latestClosedOpenTime = latestClosed == null ? Long.MIN_VALUE : latestClosed.getOpenTime();
        long draftOpenTime = state.draftMinute == null ? Long.MIN_VALUE : state.draftMinute.getOpenTime();
        long frontierOpenTime = Math.max(latestClosedOpenTime, draftOpenTime);
        if (candidate.getOpenTime() > frontierOpenTime) {
            return true;
        }
        if (candidate.getOpenTime() < frontierOpenTime) {
            return false;
        }
        return state.draftMinute != null && state.draftMinute.getOpenTime() == candidate.getOpenTime();
    }

    @Nullable
    private CandleEntry latestClosedMinute(@NonNull MinuteState state) {
        if (state.closedMinutes.isEmpty()) {
            return null;
        }
        CandleEntry latest = null;
        for (CandleEntry candle : state.closedMinutes.values()) {
            latest = candle;
        }
        return latest;
    }

    private boolean shouldReplaceClosed(@NonNull CandleEntry current, @NonNull CandleEntry candidate) {
        if (candidate.getOpenTime() != current.getOpenTime()) {
            return candidate.getOpenTime() > current.getOpenTime();
        }
        if (candidate.getQuoteVolume() > current.getQuoteVolume() + EPSILON) {
            return true;
        }
        if (candidate.getVolume() > current.getVolume() + EPSILON) {
            return true;
        }
        if (candidate.getCloseTime() > current.getCloseTime()) {
            return true;
        }
        return false;
    }

    @NonNull
    private ApplyResult toApplyResult(@NonNull MinuteState state) {
        List<CandleEntry> closedMinutes = new ArrayList<>(state.closedMinutes.values());
        CandleEntry latestClosedMinute = closedMinutes.isEmpty() ? null : closedMinutes.get(closedMinutes.size() - 1);
        return new ApplyResult(
                state.symbol,
                state.latestPrice,
                state.updatedAt,
                latestClosedMinute,
                state.draftMinute,
                closedMinutes
        );
    }

    @NonNull
    private MinuteState stateForSymbol(@NonNull String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        MinuteState state = minuteStates.get(normalizedSymbol);
        if (state != null) {
            return state;
        }
        MinuteState created = new MinuteState(normalizedSymbol);
        minuteStates.put(normalizedSymbol, created);
        return created;
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }

    private static final class MinuteState {
        private final String symbol;
        private double latestPrice;
        private long updatedAt;
        @Nullable
        private CandleEntry draftMinute;
        private final LinkedHashMap<Long, CandleEntry> closedMinutes = new LinkedHashMap<>();

        private MinuteState(@NonNull String symbol) {
            this.symbol = symbol;
        }
    }

    public static final class ApplyResult {
        private final String symbol;
        private final double latestPrice;
        private final long updatedAt;
        @Nullable
        private final CandleEntry latestClosedMinute;
        @Nullable
        private final CandleEntry draftMinute;
        private final List<CandleEntry> closedMinutes;

        private ApplyResult(@NonNull String symbol,
                            double latestPrice,
                            long updatedAt,
                            @Nullable CandleEntry latestClosedMinute,
                            @Nullable CandleEntry draftMinute,
                            @NonNull List<CandleEntry> closedMinutes) {
            this.symbol = symbol;
            this.latestPrice = latestPrice;
            this.updatedAt = updatedAt;
            this.latestClosedMinute = latestClosedMinute;
            this.draftMinute = draftMinute;
            this.closedMinutes = MarketTruthAggregationHelper.copySeries(closedMinutes);
        }

        @NonNull
        public String getSymbol() {
            return symbol;
        }

        public double getLatestPrice() {
            return latestPrice;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        @Nullable
        public CandleEntry getLatestClosedMinute() {
            return latestClosedMinute;
        }

        @Nullable
        public CandleEntry getDraftMinute() {
            return draftMinute;
        }

        @NonNull
        public List<CandleEntry> getClosedMinutes() {
            return MarketTruthAggregationHelper.copySeries(closedMinutes);
        }
    }
}

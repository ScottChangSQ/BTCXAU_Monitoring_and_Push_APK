/*
 * 市场真值快照，统一作为图表、悬浮窗和其他消费端的只读入口。
 */
package com.binance.monitor.runtime.market.truth.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MarketTruthSnapshot {
    private final long updatedAt;
    private final Map<String, MarketTruthSymbolState> symbolStates;

    public MarketTruthSnapshot(long updatedAt,
                               @Nullable Map<String, MarketTruthSymbolState> symbolStates) {
        this.updatedAt = Math.max(0L, updatedAt);
        this.symbolStates = freeze(symbolStates);
    }

    @NonNull
    public static MarketTruthSnapshot empty() {
        return new MarketTruthSnapshot(0L, Collections.emptyMap());
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    public Map<String, MarketTruthSymbolState> getSymbolStates() {
        return symbolStates;
    }

    @Nullable
    public MarketTruthSymbolState getSymbolState(@Nullable String symbol) {
        return symbolStates.get(normalizeSymbol(symbol));
    }

    public double selectLatestPrice(@Nullable String symbol) {
        MarketTruthSymbolState state = getSymbolState(symbol);
        return state == null ? 0d : state.getLatestPrice();
    }

    @NonNull
    public CurrentMinuteSnapshot selectCurrentMinute(@Nullable String symbol) {
        MarketTruthSymbolState state = getSymbolState(symbol);
        return state == null ? CurrentMinuteSnapshot.empty(symbol) : state.selectCurrentMinute();
    }

    @Nullable
    public CandleEntry selectClosedMinute(@Nullable String symbol) {
        MarketTruthSymbolState state = getSymbolState(symbol);
        return state == null ? null : state.getLatestClosedMinute();
    }

    @Nullable
    public CandleEntry selectDisplayMinute(@Nullable String symbol) {
        MarketTruthSymbolState state = getSymbolState(symbol);
        if (state == null) {
            return null;
        }
        CandleEntry draft = state.getDraftMinute();
        return draft != null ? draft : state.getLatestClosedMinute();
    }

    @NonNull
    public MarketDisplaySeries selectDisplaySeries(@Nullable String symbol,
                                                   @Nullable String intervalKey,
                                                   int limit) {
        MarketTruthSymbolState state = getSymbolState(symbol);
        if (state == null) {
            return MarketDisplaySeries.empty(intervalKey == null ? "" : intervalKey);
        }
        return state.selectDisplaySeries(intervalKey, limit);
    }

    // 返回写入单个品种后的新快照。
    @NonNull
    public MarketTruthSnapshot withSymbolState(@NonNull String symbol,
                                               @NonNull MarketTruthSymbolState state,
                                               long nextUpdatedAt) {
        Map<String, MarketTruthSymbolState> next = new LinkedHashMap<>(symbolStates);
        next.put(normalizeSymbol(symbol), state);
        return new MarketTruthSnapshot(Math.max(updatedAt, nextUpdatedAt), next);
    }

    @NonNull
    private static Map<String, MarketTruthSymbolState> freeze(@Nullable Map<String, MarketTruthSymbolState> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, MarketTruthSymbolState> copy = new LinkedHashMap<>();
        for (Map.Entry<String, MarketTruthSymbolState> entry : source.entrySet()) {
            String symbol = normalizeSymbol(entry.getKey());
            MarketTruthSymbolState state = entry.getValue();
            if (symbol.isEmpty() || state == null) {
                continue;
            }
            copy.put(symbol, state);
        }
        return Collections.unmodifiableMap(copy);
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }
}

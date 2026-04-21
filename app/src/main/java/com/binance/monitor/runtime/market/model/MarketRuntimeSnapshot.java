/*
 * 市场运行态快照，统一表达当前所有交易品种的市场底稿版本与窗口内容。
 */
package com.binance.monitor.runtime.market.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MarketRuntimeSnapshot {
    private final long marketBaseRevision;
    private final long marketWindowRevision;
    private final long updatedAt;
    private final Map<String, SymbolMarketWindow> symbolWindows;

    public MarketRuntimeSnapshot(long marketBaseRevision,
                                 long marketWindowRevision,
                                 long updatedAt,
                                 @Nullable Map<String, SymbolMarketWindow> symbolWindows) {
        this.marketBaseRevision = Math.max(0L, marketBaseRevision);
        this.marketWindowRevision = Math.max(0L, marketWindowRevision);
        this.updatedAt = Math.max(0L, updatedAt);
        this.symbolWindows = freeze(symbolWindows);
    }

    @NonNull
    public static MarketRuntimeSnapshot empty() {
        return new MarketRuntimeSnapshot(0L, 0L, 0L, Collections.emptyMap());
    }

    public long getMarketBaseRevision() {
        return marketBaseRevision;
    }

    public long getMarketWindowRevision() {
        return marketWindowRevision;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    public Map<String, SymbolMarketWindow> getSymbolWindows() {
        return symbolWindows;
    }

    @Nullable
    public SymbolMarketWindow getSymbolWindow(@Nullable String marketSymbol) {
        return symbolWindows.get(normalizeSymbol(marketSymbol));
    }

    @NonNull
    private static Map<String, SymbolMarketWindow> freeze(@Nullable Map<String, SymbolMarketWindow> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SymbolMarketWindow> copy = new LinkedHashMap<>();
        for (Map.Entry<String, SymbolMarketWindow> entry : source.entrySet()) {
            String key = normalizeSymbol(entry.getKey());
            SymbolMarketWindow value = entry.getValue();
            if (key.isEmpty() || value == null) {
                continue;
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }
}

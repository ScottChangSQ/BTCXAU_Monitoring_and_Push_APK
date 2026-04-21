/*
 * 市场运行态真值中心，统一收口 stream 下发的最新价、闭合 1 分钟和实时 patch。
 */
package com.binance.monitor.runtime.market;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;
import com.binance.monitor.runtime.revision.RuntimeRevisionCenter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MarketRuntimeStore {
    private final Object lock = new Object();
    private final RuntimeRevisionCenter revisionCenter = new RuntimeRevisionCenter();
    private MarketRuntimeSnapshot snapshot = MarketRuntimeSnapshot.empty();

    @NonNull
    public MarketRuntimeSnapshot getSnapshot() {
        synchronized (lock) {
            return snapshot;
        }
    }

    @NonNull
    public MarketRuntimeSnapshot applySymbolWindows(@Nullable List<SymbolMarketWindow> symbolWindows,
                                                    long updatedAt) {
        synchronized (lock) {
            Map<String, SymbolMarketWindow> normalized = normalizeSymbolWindows(symbolWindows);
            String signature = buildSnapshotSignature(normalized);
            long marketBaseRevision = revisionCenter.advanceIfChanged(
                    RuntimeRevisionCenter.RevisionType.MARKET_BASE,
                    signature
            );
            long marketWindowRevision = revisionCenter.advanceIfChanged(
                    RuntimeRevisionCenter.RevisionType.MARKET_WINDOW,
                    signature
            );
            snapshot = new MarketRuntimeSnapshot(
                    marketBaseRevision,
                    marketWindowRevision,
                    updatedAt,
                    normalized
            );
            return snapshot;
        }
    }

    public void clear() {
        synchronized (lock) {
            revisionCenter.clear();
            snapshot = MarketRuntimeSnapshot.empty();
        }
    }

    @NonNull
    private static Map<String, SymbolMarketWindow> normalizeSymbolWindows(@Nullable List<SymbolMarketWindow> symbolWindows) {
        if (symbolWindows == null || symbolWindows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SymbolMarketWindow> normalized = new LinkedHashMap<>();
        for (SymbolMarketWindow window : symbolWindows) {
            if (window == null) {
                continue;
            }
            String marketSymbol = normalizeSymbol(window.getMarketSymbol());
            if (marketSymbol.isEmpty()) {
                continue;
            }
            normalized.put(marketSymbol, window);
        }
        return normalized;
    }

    @NonNull
    private static String buildSnapshotSignature(@NonNull Map<String, SymbolMarketWindow> symbolWindows) {
        if (symbolWindows.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, SymbolMarketWindow> entry : symbolWindows.entrySet()) {
            builder.append(entry.getKey())
                    .append('=')
                    .append(entry.getValue() == null ? "" : entry.getValue().buildSignature())
                    .append('\n');
        }
        return builder.toString();
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }
}

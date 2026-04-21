/*
 * 市场 selector，负责从统一市场底稿中导出旧展示层仍需要的只读结果。
 */
package com.binance.monitor.runtime.market;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;

public final class MarketSelector {

    private MarketSelector() {
    }

    public static double selectLatestPrice(@Nullable MarketRuntimeSnapshot snapshot,
                                           @Nullable String marketSymbol) {
        SymbolMarketWindow window = selectSymbolWindow(snapshot, marketSymbol);
        return window == null ? 0d : window.getLatestPrice();
    }

    @Nullable
    public static KlineData selectClosedMinute(@Nullable MarketRuntimeSnapshot snapshot,
                                               @Nullable String marketSymbol) {
        SymbolMarketWindow window = selectSymbolWindow(snapshot, marketSymbol);
        return window == null ? null : window.getLatestClosedMinute();
    }

    @Nullable
    public static KlineData selectDisplayKline(@Nullable MarketRuntimeSnapshot snapshot,
                                               @Nullable String marketSymbol) {
        SymbolMarketWindow window = selectSymbolWindow(snapshot, marketSymbol);
        return window == null ? null : window.getDisplayKline();
    }

    @Nullable
    public static SymbolMarketWindow selectSymbolWindow(@Nullable MarketRuntimeSnapshot snapshot,
                                                        @Nullable String marketSymbol) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.getSymbolWindow(marketSymbol);
    }
}

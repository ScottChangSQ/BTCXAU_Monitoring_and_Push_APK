/*
 * 市场运行态中心测试，锁定市场底稿唯一化后的修订号推进与窗口快照行为。
 */
package com.binance.monitor.runtime.market;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;

import org.junit.Test;

import java.util.Collections;

public class MarketRuntimeStoreTest {

    @Test
    public void applySymbolWindowsShouldAdvanceRevisionOnlyWhenContentChanges() {
        MarketRuntimeStore store = new MarketRuntimeStore();

        MarketRuntimeSnapshot first = store.applySymbolWindows(
                Collections.singletonList(buildWindow("BTCUSDT", 67_100d, 1_000L, 1_060L, false)),
                2_000L
        );
        MarketRuntimeSnapshot second = store.applySymbolWindows(
                Collections.singletonList(buildWindow("BTCUSDT", 67_100d, 1_000L, 1_060L, false)),
                3_000L
        );
        MarketRuntimeSnapshot third = store.applySymbolWindows(
                Collections.singletonList(buildWindow("BTCUSDT", 67_200d, 1_000L, 1_060L, false)),
                4_000L
        );

        assertEquals(1L, first.getMarketBaseRevision());
        assertEquals(1L, second.getMarketBaseRevision());
        assertEquals(2L, third.getMarketBaseRevision());
    }

    @Test
    public void applySymbolWindowsShouldExposePerSymbolWindowSnapshot() {
        MarketRuntimeStore store = new MarketRuntimeStore();
        store.applySymbolWindows(
                Collections.singletonList(buildWindow("BTCUSDT", 67_100d, 1_000L, 1_060L, false)),
                2_000L
        );

        SymbolMarketWindow window = store.getSnapshot().getSymbolWindow("BTCUSDT");

        assertEquals("BTCUSDT", window.getMarketSymbol());
        assertEquals(67_100d, window.getLatestPrice(), 0.0001d);
        assertEquals(1_000L, window.getLatestOpenTime());
        assertEquals(1_060L, window.getLatestCloseTime());
    }

    @Test
    public void clearShouldDropAllWindowsAndResetRevisions() {
        MarketRuntimeStore store = new MarketRuntimeStore();
        store.applySymbolWindows(
                Collections.singletonList(buildWindow("BTCUSDT", 67_100d, 1_000L, 1_060L, false)),
                2_000L
        );

        store.clear();

        MarketRuntimeSnapshot snapshot = store.getSnapshot();
        assertEquals(0L, snapshot.getMarketBaseRevision());
        assertEquals(0L, snapshot.getMarketWindowRevision());
        assertNull(snapshot.getSymbolWindow("BTCUSDT"));
    }

    private static SymbolMarketWindow buildWindow(String symbol,
                                                  double latestPrice,
                                                  long openTime,
                                                  long closeTime,
                                                  boolean closed) {
        KlineData candle = new KlineData(
                symbol,
                latestPrice - 10d,
                latestPrice + 20d,
                latestPrice - 30d,
                latestPrice,
                10d,
                100d,
                openTime,
                closeTime,
                closed
        );
        return new SymbolMarketWindow(
                symbol,
                symbol,
                latestPrice,
                openTime,
                closeTime,
                closed ? candle : null,
                closed ? null : candle
        );
    }
}

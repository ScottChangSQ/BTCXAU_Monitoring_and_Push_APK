/*
 * 市场 selector 测试，锁定旧展示层镜像必须来自统一市场底稿。
 */
package com.binance.monitor.runtime.market;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;

import org.junit.Test;

import java.util.Collections;

public class MarketSelectorTest {

    @Test
    public void displayKlineShouldPreferLatestPatch() {
        KlineData closedMinute = buildCandle("BTCUSDT", 67_000d, 1_000L, 1_060L, true);
        KlineData latestPatch = buildCandle("BTCUSDT", 67_100d, 1_060L, 1_120L, false);
        MarketRuntimeSnapshot snapshot = new MarketRuntimeSnapshot(
                1L,
                1L,
                2_000L,
                Collections.singletonMap("BTCUSDT", new SymbolMarketWindow(
                        "BTCUSDT",
                        "BTCUSDT",
                        67_100d,
                        1_060L,
                        1_120L,
                        closedMinute,
                        latestPatch
                ))
        );

        KlineData display = MarketSelector.selectDisplayKline(snapshot, "BTCUSDT");

        assertEquals(67_100d, display.getClosePrice(), 0.0001d);
        assertEquals(1_060L, display.getOpenTime());
        assertEquals(1_120L, display.getCloseTime());
        assertEquals(false, display.isClosed());
    }

    @Test
    public void closedMinuteSelectorShouldIgnoreLatestPatch() {
        KlineData closedMinute = buildCandle("BTCUSDT", 67_000d, 1_000L, 1_060L, true);
        KlineData latestPatch = buildCandle("BTCUSDT", 67_100d, 1_060L, 1_120L, false);
        MarketRuntimeSnapshot snapshot = new MarketRuntimeSnapshot(
                1L,
                1L,
                2_000L,
                Collections.singletonMap("BTCUSDT", new SymbolMarketWindow(
                        "BTCUSDT",
                        "BTCUSDT",
                        67_100d,
                        1_060L,
                        1_120L,
                        closedMinute,
                        latestPatch
                ))
        );

        KlineData selectedClosedMinute = MarketSelector.selectClosedMinute(snapshot, "BTCUSDT");

        assertEquals(67_000d, selectedClosedMinute.getClosePrice(), 0.0001d);
        assertEquals(true, selectedClosedMinute.isClosed());
    }

    @Test
    public void selectorsShouldReturnEmptyWhenSymbolMissing() {
        MarketRuntimeSnapshot snapshot = MarketRuntimeSnapshot.empty();

        assertEquals(0d, MarketSelector.selectLatestPrice(snapshot, "BTCUSDT"), 0.0001d);
        assertNull(MarketSelector.selectClosedMinute(snapshot, "BTCUSDT"));
        assertNull(MarketSelector.selectDisplayKline(snapshot, "BTCUSDT"));
    }

    private static KlineData buildCandle(String symbol,
                                         double closePrice,
                                         long openTime,
                                         long closeTime,
                                         boolean closed) {
        return new KlineData(
                symbol,
                closePrice - 10d,
                closePrice + 20d,
                closePrice - 30d,
                closePrice,
                10d,
                100d,
                openTime,
                closeTime,
                closed
        );
    }
}

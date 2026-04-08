package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.ui.account.model.PositionItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MarketChartTradeSupportTest {

    @Test
    public void toTradeSymbolShouldMapBinanceSymbolsToMt5Symbols() {
        assertEquals("BTCUSD", MarketChartTradeSupport.toTradeSymbol("BTCUSDT"));
        assertEquals("XAUUSD", MarketChartTradeSupport.toTradeSymbol("XAUUSDT"));
        assertEquals("BTCUSD", MarketChartTradeSupport.toTradeSymbol("BTCUSD"));
        assertEquals("ETHUSDT", MarketChartTradeSupport.toTradeSymbol("ethusdt"));
    }

    @Test
    public void resolveReferencePriceShouldPreferLatestClosedCandle() {
        double price = MarketChartTradeSupport.resolveReferencePrice(
                Arrays.asList(
                        new CandleEntry("BTCUSDT", 1L, 2L, 1d, 2d, 0.5d, 1.5d, 10d, 20d),
                        new CandleEntry("BTCUSDT", 3L, 4L, 1.5d, 3d, 1d, 2.6d, 11d, 21d)
                ),
                null,
                null
        );

        assertEquals(2.6d, price, 0.00001d);
    }

    @Test
    public void resolveReferencePriceShouldFallbackToPositionThenPendingOrder() {
        PositionItem positionItem = new PositionItem(
                "BTCUSD",
                "BTCUSD",
                "Buy",
                7001L,
                0L,
                1.0d,
                1.0d,
                100000d,
                101234d,
                100000d,
                0.1d,
                0d,
                100d,
                0.02d,
                0d,
                0,
                0d,
                0d,
                0d,
                0d
        );
        PositionItem pendingItem = new PositionItem(
                "BTCUSD",
                "BTCUSD",
                "Buy",
                0L,
                8002L,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0.30d,
                1,
                99888d,
                0d,
                0d,
                0d
        );

        assertEquals(
                101234d,
                MarketChartTradeSupport.resolveReferencePrice(Collections.emptyList(), positionItem, pendingItem),
                0.00001d
        );
        assertEquals(
                99888d,
                MarketChartTradeSupport.resolveReferencePrice(Collections.emptyList(), null, pendingItem),
                0.00001d
        );
    }

    @Test
    public void parseInputValuesShouldHandleBlankAndInvalidContent() {
        assertEquals(0d, MarketChartTradeSupport.parseOptionalDouble("", 0d), 0.00001d);
        assertEquals(12.34d, MarketChartTradeSupport.parseOptionalDouble("12.34", 0d), 0.00001d);
        assertEquals(88d, MarketChartTradeSupport.parseOptionalDouble("abc", 88d), 0.00001d);
        assertEquals(77d, MarketChartTradeSupport.parseOptionalDouble("NaN", 77d), 0.00001d);
        assertEquals(66d, MarketChartTradeSupport.parseOptionalDouble("Infinity", 66d), 0.00001d);
    }
}

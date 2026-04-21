package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.runtime.market.model.SymbolMarketWindow;

import org.junit.Test;

public class MarketChartRevisionRefreshPolicyTest {

    @Test
    public void shouldRequestWhenRuntimeWindowMissing() {
        assertTrue(MarketChartRevisionRefreshPolicy.shouldRequestKlines(
                "",
                "",
                0L,
                10_000L,
                5_000L
        ));
    }

    @Test
    public void shouldRequestWhenSelectedWindowSignatureMovesAhead() {
        assertTrue(MarketChartRevisionRefreshPolicy.shouldRequestKlines(
                buildWindow(120_000L).buildSignature(),
                buildWindow(60_000L).buildSignature(),
                3_000L,
                6_000L,
                5_000L
        ));
    }

    @Test
    public void shouldRequestWhenAppliedWindowIsStale() {
        SymbolMarketWindow window = buildWindow(120_000L);
        assertTrue(MarketChartRevisionRefreshPolicy.shouldRequestKlines(
                window.buildSignature(),
                window.buildSignature(),
                1_000L,
                7_000L,
                5_000L
        ));
    }

    @Test
    public void shouldSkipWhenSelectedWindowSignatureMatchesAndIsFresh() {
        SymbolMarketWindow window = buildWindow(120_000L);
        assertFalse(MarketChartRevisionRefreshPolicy.shouldRequestKlines(
                window.buildSignature(),
                window.buildSignature(),
                4_000L,
                6_000L,
                5_000L
        ));
    }

    private static SymbolMarketWindow buildWindow(long latestOpenTime) {
        return new SymbolMarketWindow(
                "BTCUSDT",
                "BTC",
                65_000d,
                latestOpenTime,
                latestOpenTime + 59_999L,
                null,
                null
        );
    }
}

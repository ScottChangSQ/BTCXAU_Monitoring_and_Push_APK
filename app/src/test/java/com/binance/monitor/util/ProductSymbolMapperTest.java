package com.binance.monitor.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.constants.AppConstants;

import org.junit.Test;

public class ProductSymbolMapperTest {

    @Test
    public void toMarketSymbolShouldNormalizeSupportedAliases() {
        assertEquals(AppConstants.SYMBOL_BTC, ProductSymbolMapper.toMarketSymbol("BTC"));
        assertEquals(AppConstants.SYMBOL_BTC, ProductSymbolMapper.toMarketSymbol("BTCUSD"));
        assertEquals(AppConstants.SYMBOL_BTC, ProductSymbolMapper.toMarketSymbol("BTCUSDT"));
        assertEquals(AppConstants.SYMBOL_XAU, ProductSymbolMapper.toMarketSymbol("XAU"));
        assertEquals(AppConstants.SYMBOL_XAU, ProductSymbolMapper.toMarketSymbol("XAUUSD"));
        assertEquals(AppConstants.SYMBOL_XAU, ProductSymbolMapper.toMarketSymbol("XAUUSDT"));
    }

    @Test
    public void toTradeSymbolShouldNormalizeSupportedAliases() {
        assertEquals(ProductSymbolMapper.TRADE_SYMBOL_BTC, ProductSymbolMapper.toTradeSymbol("BTCUSDT"));
        assertEquals(ProductSymbolMapper.TRADE_SYMBOL_BTC, ProductSymbolMapper.toTradeSymbol("BTCUSD"));
        assertEquals(ProductSymbolMapper.TRADE_SYMBOL_XAU, ProductSymbolMapper.toTradeSymbol("XAUUSDT"));
        assertEquals(ProductSymbolMapper.TRADE_SYMBOL_XAU, ProductSymbolMapper.toTradeSymbol("XAUUSD"));
    }

    @Test
    public void isSameProductShouldCompareAcrossMarketAndTradeSymbols() {
        assertTrue(ProductSymbolMapper.isSameProduct("BTCUSDT", "BTCUSD"));
        assertTrue(ProductSymbolMapper.isSameProduct("XAUUSDT", "XAUUSD"));
        assertFalse(ProductSymbolMapper.isSameProduct("BTCUSDT", "XAUUSD"));
    }

    @Test
    public void unsupportedProductShouldNotBeMisclassified() {
        assertEquals("ETHUSDT", ProductSymbolMapper.toMarketSymbol("ethusdt"));
        assertEquals("ETHUSDT", ProductSymbolMapper.toTradeSymbol("ethusdt"));
        assertEquals("ETHUSDT", AppConstants.symbolToAsset("ethusdt"));
        assertFalse(ProductSymbolMapper.isSupportedProduct("ethusdt"));
    }
}

/*
 * 产品代码映射工具，负责统一 Binance 市场品种名和 MT5 交易品种名。
 * 与异常链、图表链、悬浮窗链配合，避免各处散落字符串判断。
 */
package com.binance.monitor.util;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;

import java.util.Locale;

public final class ProductSymbolMapper {

    public static final String TRADE_SYMBOL_BTC = "BTCUSD";
    public static final String TRADE_SYMBOL_XAU = "XAUUSD";

    private ProductSymbolMapper() {
    }

    // 统一转成市场侧 canonical symbol。
    public static String toMarketSymbol(@Nullable String rawSymbol) {
        String normalized = normalize(rawSymbol);
        if (normalized.isEmpty()) {
            return "";
        }
        if ("BTC".equals(normalized)
                || TRADE_SYMBOL_BTC.equals(normalized)
                || AppConstants.SYMBOL_BTC.equals(normalized)
                || "XBT".equals(normalized)) {
            return AppConstants.SYMBOL_BTC;
        }
        if ("XAU".equals(normalized)
                || TRADE_SYMBOL_XAU.equals(normalized)
                || AppConstants.SYMBOL_XAU.equals(normalized)
                || "GOLD".equals(normalized)) {
            return AppConstants.SYMBOL_XAU;
        }
        return normalized;
    }

    // 统一转成 MT5 交易侧 canonical symbol。
    public static String toTradeSymbol(@Nullable String rawSymbol) {
        String normalized = normalize(rawSymbol);
        if (normalized.isEmpty()) {
            return "";
        }
        if ("BTC".equals(normalized)
                || TRADE_SYMBOL_BTC.equals(normalized)
                || AppConstants.SYMBOL_BTC.equals(normalized)
                || "XBT".equals(normalized)) {
            return TRADE_SYMBOL_BTC;
        }
        if ("XAU".equals(normalized)
                || TRADE_SYMBOL_XAU.equals(normalized)
                || AppConstants.SYMBOL_XAU.equals(normalized)
                || "GOLD".equals(normalized)) {
            return TRADE_SYMBOL_XAU;
        }
        return normalized;
    }

    // 判断两个代码是否属于同一产品。
    public static boolean isSameProduct(@Nullable String left, @Nullable String right) {
        String normalizedLeft = toMarketSymbol(left);
        String normalizedRight = toMarketSymbol(right);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
    }

    // 判断是否属于当前明确支持的 BTC/XAU 产品。
    public static boolean isSupportedProduct(@Nullable String rawSymbol) {
        String marketSymbol = toMarketSymbol(rawSymbol);
        return AppConstants.SYMBOL_BTC.equals(marketSymbol)
                || AppConstants.SYMBOL_XAU.equals(marketSymbol);
    }

    private static String normalize(@Nullable String rawSymbol) {
        return rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
    }
}

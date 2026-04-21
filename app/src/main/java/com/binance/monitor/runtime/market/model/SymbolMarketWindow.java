/*
 * 单个交易品种的市场窗口真值，统一承接最新价、最新闭合 1 分钟和最新 patch。
 */
package com.binance.monitor.runtime.market.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.KlineData;

public final class SymbolMarketWindow {
    private final String marketSymbol;
    private final String tradeSymbol;
    private final double latestPrice;
    private final long latestOpenTime;
    private final long latestCloseTime;
    @Nullable
    private final KlineData latestClosedMinute;
    @Nullable
    private final KlineData latestPatch;

    public SymbolMarketWindow(@Nullable String marketSymbol,
                              @Nullable String tradeSymbol,
                              double latestPrice,
                              long latestOpenTime,
                              long latestCloseTime,
                              @Nullable KlineData latestClosedMinute,
                              @Nullable KlineData latestPatch) {
        this.marketSymbol = marketSymbol == null ? "" : marketSymbol.trim();
        this.tradeSymbol = tradeSymbol == null ? "" : tradeSymbol.trim();
        this.latestPrice = latestPrice;
        this.latestOpenTime = latestOpenTime;
        this.latestCloseTime = latestCloseTime;
        this.latestClosedMinute = latestClosedMinute;
        this.latestPatch = latestPatch;
    }

    @NonNull
    public String getMarketSymbol() {
        return marketSymbol;
    }

    @NonNull
    public String getTradeSymbol() {
        return tradeSymbol;
    }

    public double getLatestPrice() {
        return latestPrice;
    }

    public long getLatestOpenTime() {
        return latestOpenTime;
    }

    public long getLatestCloseTime() {
        return latestCloseTime;
    }

    @Nullable
    public KlineData getLatestClosedMinute() {
        return latestClosedMinute;
    }

    @Nullable
    public KlineData getLatestPatch() {
        return latestPatch;
    }

    @Nullable
    public KlineData getDisplayKline() {
        return latestPatch != null ? latestPatch : latestClosedMinute;
    }

    @NonNull
    public String buildSignature() {
        return marketSymbol + '|'
                + tradeSymbol + '|'
                + latestPrice + '|'
                + latestOpenTime + '|'
                + latestCloseTime + '|'
                + buildKlineSignature(latestClosedMinute) + '|'
                + buildKlineSignature(latestPatch);
    }

    @NonNull
    private static String buildKlineSignature(@Nullable KlineData data) {
        if (data == null) {
            return "";
        }
        return data.getSymbol() + ':'
                + data.getOpenPrice() + ':'
                + data.getHighPrice() + ':'
                + data.getLowPrice() + ':'
                + data.getClosePrice() + ':'
                + data.getVolume() + ':'
                + data.getQuoteAssetVolume() + ':'
                + data.getOpenTime() + ':'
                + data.getCloseTime() + ':'
                + data.isClosed();
    }
}

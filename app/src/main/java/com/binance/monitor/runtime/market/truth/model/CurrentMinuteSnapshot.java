/*
 * 当前分钟读模型，统一提供最新价、本分钟量额和分钟时间桶给图表与悬浮窗。
 */
package com.binance.monitor.runtime.market.truth.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class CurrentMinuteSnapshot {
    private final String symbol;
    private final double latestPrice;
    private final double volume;
    private final double amount;
    private final long openTime;
    private final long closeTime;
    private final long updatedAt;

    public CurrentMinuteSnapshot(@Nullable String symbol,
                                 double latestPrice,
                                 double volume,
                                 double amount,
                                 long openTime,
                                 long closeTime,
                                 long updatedAt) {
        this.symbol = normalizeSymbol(symbol);
        this.latestPrice = latestPrice;
        this.volume = volume;
        this.amount = amount;
        this.openTime = Math.max(0L, openTime);
        this.closeTime = Math.max(0L, closeTime);
        this.updatedAt = Math.max(0L, updatedAt);
    }

    @NonNull
    public static CurrentMinuteSnapshot empty(@Nullable String symbol) {
        return new CurrentMinuteSnapshot(symbol, 0d, 0d, 0d, 0L, 0L, 0L);
    }

    @NonNull
    public String getSymbol() {
        return symbol;
    }

    public double getLatestPrice() {
        return latestPrice;
    }

    public double getVolume() {
        return volume;
    }

    public double getAmount() {
        return amount;
    }

    public long getOpenTime() {
        return openTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }
}

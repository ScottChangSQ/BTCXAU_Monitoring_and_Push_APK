/*
 * 图表展示序列模型，统一封装当前周期的最终显示结果和真值时间戳。
 */
package com.binance.monitor.runtime.market.truth.model;

import androidx.annotation.NonNull;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.runtime.market.truth.MarketTruthAggregationHelper;

import java.util.List;

public final class MarketDisplaySeries {
    private final String intervalKey;
    private final long updatedAt;
    private final boolean hasGap;
    private final List<CandleEntry> candles;

    public MarketDisplaySeries(@NonNull String intervalKey,
                               long updatedAt,
                               boolean hasGap,
                               @NonNull List<CandleEntry> candles) {
        this.intervalKey = intervalKey == null ? "" : intervalKey;
        this.updatedAt = Math.max(0L, updatedAt);
        this.hasGap = hasGap;
        this.candles = MarketTruthAggregationHelper.copySeries(candles);
    }

    @NonNull
    public static MarketDisplaySeries empty(@NonNull String intervalKey) {
        return new MarketDisplaySeries(intervalKey, 0L, false, java.util.Collections.emptyList());
    }

    @NonNull
    public String getIntervalKey() {
        return intervalKey;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public boolean hasGap() {
        return hasGap;
    }

    @NonNull
    public List<CandleEntry> getCandles() {
        return MarketTruthAggregationHelper.copySeries(candles);
    }
}

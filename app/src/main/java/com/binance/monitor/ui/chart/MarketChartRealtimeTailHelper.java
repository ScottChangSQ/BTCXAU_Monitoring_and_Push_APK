/*
 * 行情持仓页实时尾部助手，负责把分钟底稿合并和当前周期尾部派生从旧 Activity 中抽离。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.List;

final class MarketChartRealtimeTailHelper {

    @NonNull
    List<CandleEntry> mergeRealtimeMinuteCache(@NonNull String selectedSymbol,
                                               @NonNull String minuteCacheKey,
                                               @Nullable List<CandleEntry> minuteSource,
                                               @NonNull CandleEntry realtimeBaseCandle,
                                               int minuteLimit,
                                               int restoreWindowLimit) {
        return CandleAggregationHelper.mergeRealtimeBaseCandle(
                minuteSource,
                realtimeBaseCandle,
                selectedSymbol,
                Math.max(minuteLimit, restoreWindowLimit)
        );
    }

    @NonNull
    List<CandleEntry> buildRealtimeDisplayCandles(@NonNull String selectedSymbol,
                                                  @NonNull MarketChartDataCoordinator.IntervalSelection interval,
                                                  @NonNull List<CandleEntry> loadedCandles,
                                                  @NonNull CandleEntry realtimeBaseCandle,
                                                  @Nullable List<CandleEntry> minuteCandles) {
        if (interval.isYearAggregate()) {
            return new ArrayList<>();
        }
        if ("1m".equals(interval.getKey())) {
            return CandleAggregationHelper.mergeRealtimeBaseCandle(
                    loadedCandles,
                    realtimeBaseCandle,
                    selectedSymbol,
                    interval.getLimit()
            );
        }
        if (minuteCandles == null || minuteCandles.isEmpty()) {
            return new ArrayList<>();
        }
        return CandleAggregationHelper.aggregate(
                minuteCandles,
                selectedSymbol,
                interval.getApiInterval(),
                interval.getLimit()
        );
    }
}

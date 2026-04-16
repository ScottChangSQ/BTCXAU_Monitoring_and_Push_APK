/*
 * 行情持仓页序列加载助手，负责把 v2 K 线抓取、增量合并和年线聚合从旧 Activity 中抽离。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.v2.MarketSeriesPayload;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

final class MarketChartSeriesLoaderHelper {

    interface Gateway {
        @Nullable
        MarketSeriesPayload fetchMarketSeries(@NonNull String symbol,
                                             @NonNull String apiInterval,
                                             int limit) throws Exception;

        @Nullable
        MarketSeriesPayload fetchMarketSeriesBefore(@NonNull String symbol,
                                                    @NonNull String apiInterval,
                                                    int limit,
                                                    long endTimeInclusive) throws Exception;

        @Nullable
        MarketSeriesPayload fetchMarketSeriesAfter(@NonNull String symbol,
                                                   @NonNull String apiInterval,
                                                   int limit,
                                                   long startTimeInclusive) throws Exception;
    }

    @NonNull
    List<CandleEntry> loadCandlesForRequest(@Nullable MarketChartRefreshHelper.SyncPlan plan,
                                            @Nullable List<CandleEntry> seed,
                                            @NonNull String symbol,
                                            @NonNull MarketChartDataCoordinator.IntervalSelection interval,
                                            int restoreWindowLimit,
                                            @NonNull Gateway gateway) throws Exception {
        if (interval.isYearAggregate()) {
            return loadYearAggregateCandlesForRequest(plan, seed, symbol, interval, restoreWindowLimit, gateway);
        }
        List<CandleEntry> result;
        if (plan != null && plan.mode == MarketChartRefreshHelper.SyncMode.INCREMENTAL) {
            List<CandleEntry> base = ChartWindowSliceHelper.takeLatest(seed, restoreWindowLimit);
            List<CandleEntry> tail = fetchV2SeriesAfter(symbol, interval, restoreWindowLimit, plan.startTimeInclusive, gateway);
            result = MarketChartDisplayHelper.mergeSeriesByOpenTime(base, tail);
        } else {
            result = fetchV2FullSeries(symbol, interval, restoreWindowLimit, gateway);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("币安未返回可用K线数据");
        }
        return result;
    }

    @NonNull
    List<CandleEntry> loadYearAggregateCandlesForRequest(@Nullable MarketChartRefreshHelper.SyncPlan plan,
                                                         @Nullable List<CandleEntry> seed,
                                                         @NonNull String symbol,
                                                         @NonNull MarketChartDataCoordinator.IntervalSelection interval,
                                                         int restoreWindowLimit,
                                                         @NonNull Gateway gateway) throws Exception {
        List<CandleEntry> mergedYear;
        if (plan != null && plan.mode == MarketChartRefreshHelper.SyncMode.INCREMENTAL) {
            List<CandleEntry> baseYear = ChartWindowSliceHelper.takeLatest(seed, restoreWindowLimit);
            List<CandleEntry> tailMonthly = fetchV2SeriesAfter(symbol, interval, restoreWindowLimit, plan.startTimeInclusive, gateway);
            List<CandleEntry> tailYear = aggregateToYear(tailMonthly, symbol);
            mergedYear = MarketChartDisplayHelper.mergeSeriesByOpenTime(baseYear, tailYear);
        } else {
            List<CandleEntry> fullMonthly = fetchV2FullSeries(symbol, interval, restoreWindowLimit, gateway);
            mergedYear = aggregateToYear(fullMonthly, symbol);
        }
        if (mergedYear.isEmpty()) {
            throw new IllegalStateException("币安未返回可用K线数据");
        }
        return mergedYear;
    }

    @NonNull
    List<CandleEntry> fetchV2FullSeries(@NonNull String symbol,
                                        @NonNull MarketChartDataCoordinator.IntervalSelection interval,
                                        int limit,
                                        @NonNull Gateway gateway) throws Exception {
        return mergeMarketSeriesPayload(gateway.fetchMarketSeries(symbol, interval.getApiInterval(), limit));
    }

    @NonNull
    List<CandleEntry> fetchV2SeriesBefore(@NonNull String symbol,
                                          @NonNull MarketChartDataCoordinator.IntervalSelection interval,
                                          int limit,
                                          long endTimeInclusive,
                                          @NonNull Gateway gateway) throws Exception {
        return mergeMarketSeriesPayload(gateway.fetchMarketSeriesBefore(
                symbol,
                interval.getApiInterval(),
                limit,
                endTimeInclusive
        ));
    }

    @NonNull
    List<CandleEntry> fetchV2SeriesAfter(@NonNull String symbol,
                                         @NonNull MarketChartDataCoordinator.IntervalSelection interval,
                                         int limit,
                                         long startTimeInclusive,
                                         @NonNull Gateway gateway) throws Exception {
        return mergeMarketSeriesPayload(gateway.fetchMarketSeriesAfter(
                symbol,
                interval.getApiInterval(),
                limit,
                startTimeInclusive
        ));
    }

    @NonNull
    List<CandleEntry> mergeMarketSeriesPayload(@Nullable MarketSeriesPayload payload) {
        if (payload == null) {
            return new ArrayList<>();
        }
        return MarketChartDisplayHelper.mergeSeriesWithLatestPatch(
                payload.getCandles(),
                payload.getLatestPatch()
        );
    }

    @NonNull
    List<CandleEntry> aggregateToYear(@Nullable List<CandleEntry> source, @NonNull String symbol) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Integer, List<CandleEntry>> grouped = new LinkedHashMap<>();
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        for (CandleEntry item : source) {
            calendar.setTimeInMillis(item.getOpenTime());
            int year = calendar.get(Calendar.YEAR);
            List<CandleEntry> bucket = grouped.get(year);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(year, bucket);
            }
            bucket.add(item);
        }
        List<CandleEntry> out = new ArrayList<>();
        for (Map.Entry<Integer, List<CandleEntry>> entry : grouped.entrySet()) {
            List<CandleEntry> bucket = entry.getValue();
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            Collections.sort(bucket, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));
            CandleEntry first = bucket.get(0);
            CandleEntry last = bucket.get(bucket.size() - 1);
            double high = first.getHigh();
            double low = first.getLow();
            double vol = 0d;
            double quoteVol = 0d;
            for (CandleEntry item : bucket) {
                high = Math.max(high, item.getHigh());
                low = Math.min(low, item.getLow());
                vol += item.getVolume();
                quoteVol += item.getQuoteVolume();
            }
            out.add(new CandleEntry(
                    symbol,
                    first.getOpenTime(),
                    last.getCloseTime(),
                    first.getOpen(),
                    high,
                    low,
                    last.getClose(),
                    vol,
                    quoteVol
            ));
        }
        Collections.sort(out, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));
        return out;
    }
}

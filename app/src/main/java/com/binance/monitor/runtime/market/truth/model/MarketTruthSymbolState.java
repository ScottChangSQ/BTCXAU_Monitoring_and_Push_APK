/*
 * 单个交易品种的市场真值状态，统一保存最新价、分钟底稿、周期历史和断档标记。
 */
package com.binance.monitor.runtime.market.truth.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.runtime.market.truth.MarketTruthAggregationHelper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MarketTruthSymbolState {
    private final String symbol;
    private final double latestPrice;
    private final long lastTruthUpdateAt;
    @Nullable
    private final CandleEntry latestClosedMinute;
    @Nullable
    private final CandleEntry draftMinute;
    private final boolean minuteGap;
    private final List<CandleEntry> closedMinutes;
    private final Map<String, List<CandleEntry>> closedSeriesByInterval;
    private final Map<String, CandleEntry> intervalDrafts;

    public MarketTruthSymbolState(@Nullable String symbol,
                                  double latestPrice,
                                  long lastTruthUpdateAt,
                                  @Nullable CandleEntry latestClosedMinute,
                                  @Nullable CandleEntry draftMinute,
                                  boolean minuteGap,
                                  @Nullable List<CandleEntry> closedMinutes,
                                  @Nullable Map<String, List<CandleEntry>> closedSeriesByInterval,
                                  @Nullable Map<String, CandleEntry> intervalDrafts) {
        this.symbol = normalizeSymbol(symbol);
        this.latestPrice = latestPrice;
        this.lastTruthUpdateAt = Math.max(0L, lastTruthUpdateAt);
        this.latestClosedMinute = latestClosedMinute;
        this.draftMinute = draftMinute;
        this.minuteGap = minuteGap;
        this.closedMinutes = MarketTruthAggregationHelper.copySeries(closedMinutes);
        this.closedSeriesByInterval = freezeSeriesMap(closedSeriesByInterval);
        this.intervalDrafts = freezeDraftMap(intervalDrafts);
    }

    @NonNull
    public static MarketTruthSymbolState empty(@Nullable String symbol) {
        return new MarketTruthSymbolState(
                symbol,
                0d,
                0L,
                null,
                null,
                false,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }

    @NonNull
    public String getSymbol() {
        return symbol;
    }

    public double getLatestPrice() {
        return latestPrice;
    }

    public long getLastTruthUpdateAt() {
        return lastTruthUpdateAt;
    }

    @Nullable
    public CandleEntry getLatestClosedMinute() {
        return latestClosedMinute;
    }

    @Nullable
    public CandleEntry getDraftMinute() {
        return draftMinute;
    }

    public boolean hasGap() {
        return minuteGap;
    }

    // 统一返回当前分钟读模型，有草稿时优先读草稿，否则回退到最近闭合分钟。
    @NonNull
    public CurrentMinuteSnapshot selectCurrentMinute() {
        CandleEntry source = draftMinute != null ? draftMinute : latestClosedMinute;
        if (source == null) {
            return CurrentMinuteSnapshot.empty(symbol);
        }
        return new CurrentMinuteSnapshot(
                symbol,
                latestPrice,
                source.getVolume(),
                source.getQuoteVolume(),
                source.getOpenTime(),
                source.getCloseTime(),
                lastTruthUpdateAt
        );
    }

    @NonNull
    public List<CandleEntry> getClosedMinutes() {
        return MarketTruthAggregationHelper.copySeries(closedMinutes);
    }

    // 统一构造当前周期的显示序列。
    @NonNull
    public MarketDisplaySeries selectDisplaySeries(@Nullable String intervalKey, int limit) {
        String normalizedInterval = MarketTruthAggregationHelper.normalizeIntervalKey(intervalKey);
        if ("1m".equals(normalizedInterval)) {
            List<CandleEntry> oneMinuteDisplay = MarketTruthAggregationHelper.mergeDraft(closedMinutes, draftMinute, limit);
            return new MarketDisplaySeries(normalizedInterval, lastTruthUpdateAt, minuteGap, oneMinuteDisplay);
        }
        List<CandleEntry> baseSeries = closedSeriesByInterval.get(normalizedInterval);
        List<CandleEntry> minuteProjection = buildMinuteProjection(normalizedInterval, limit);
        List<CandleEntry> merged = MarketTruthAggregationHelper.mergeSeriesByOpenTime(baseSeries, minuteProjection, limit);
        CandleEntry intervalDraft = intervalDrafts.get(normalizedInterval);
        if (!minuteProjection.isEmpty() && draftMinute != null) {
            return new MarketDisplaySeries(normalizedInterval, lastTruthUpdateAt, minuteGap, merged);
        }
        return new MarketDisplaySeries(
                normalizedInterval,
                lastTruthUpdateAt,
                minuteGap,
                MarketTruthAggregationHelper.mergeDraft(merged, intervalDraft, limit)
        );
    }

    @NonNull
    private List<CandleEntry> buildMinuteProjection(@NonNull String intervalKey, int limit) {
        if (!MarketTruthAggregationHelper.supportsMinuteProjection(intervalKey) || closedMinutes.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        List<CandleEntry> minuteSource = MarketTruthAggregationHelper.copySeries(closedMinutes);
        if (draftMinute != null) {
            minuteSource.add(draftMinute);
        }
        return MarketTruthAggregationHelper.aggregate(minuteSource, symbol, intervalKey, limit);
    }

    @NonNull
    private static Map<String, List<CandleEntry>> freezeSeriesMap(@Nullable Map<String, List<CandleEntry>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<CandleEntry>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<CandleEntry>> entry : source.entrySet()) {
            String intervalKey = MarketTruthAggregationHelper.normalizeIntervalKey(entry.getKey());
            if (intervalKey.isEmpty()) {
                continue;
            }
            copy.put(intervalKey, MarketTruthAggregationHelper.copySeries(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    @NonNull
    private static Map<String, CandleEntry> freezeDraftMap(@Nullable Map<String, CandleEntry> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, CandleEntry> copy = new LinkedHashMap<>();
        for (Map.Entry<String, CandleEntry> entry : source.entrySet()) {
            String intervalKey = MarketTruthAggregationHelper.normalizeIntervalKey(entry.getKey());
            CandleEntry draft = entry.getValue();
            if (intervalKey.isEmpty() || draft == null) {
                continue;
            }
            copy.put(intervalKey, draft);
        }
        return Collections.unmodifiableMap(copy);
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }
}

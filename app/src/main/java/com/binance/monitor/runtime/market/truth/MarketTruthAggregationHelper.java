/*
 * 市场真值聚合辅助，负责统一处理分钟桶合并、按周期聚合和尾部序列拼装。
 */
package com.binance.monitor.runtime.market.truth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MarketTruthAggregationHelper {
    private MarketTruthAggregationHelper() {
    }

    // 统一归一化周期 key。
    @NonNull
    public static String normalizeIntervalKey(@Nullable String intervalKey) {
        if (intervalKey == null) {
            return "";
        }
        String trimmed = intervalKey.trim();
        if ("1M".equals(trimmed)) {
            return "1M";
        }
        return trimmed.toLowerCase(Locale.US);
    }

    // 返回当前周期是否支持从 1m 本地投影。
    public static boolean supportsMinuteProjection(@Nullable String intervalKey) {
        String normalized = normalizeIntervalKey(intervalKey);
        return "1m".equals(normalized)
                || "5m".equals(normalized)
                || "15m".equals(normalized)
                || "30m".equals(normalized)
                || "1h".equals(normalized)
                || "4h".equals(normalized)
                || "1d".equals(normalized);
    }

    // 返回当前周期对应的固定毫秒步长。
    public static long resolveIntervalMs(@Nullable String intervalKey) {
        String normalized = normalizeIntervalKey(intervalKey);
        if ("1m".equals(normalized)) {
            return 60_000L;
        }
        if ("5m".equals(normalized)) {
            return 5L * 60_000L;
        }
        if ("15m".equals(normalized)) {
            return 15L * 60_000L;
        }
        if ("30m".equals(normalized)) {
            return 30L * 60_000L;
        }
        if ("1h".equals(normalized)) {
            return 60L * 60_000L;
        }
        if ("4h".equals(normalized)) {
            return 4L * 60L * 60_000L;
        }
        if ("1d".equals(normalized)) {
            return 24L * 60L * 60_000L;
        }
        return -1L;
    }

    // 按 openTime 合并两段序列，同桶以后来的为准。
    @NonNull
    public static List<CandleEntry> mergeSeriesByOpenTime(@Nullable List<CandleEntry> existing,
                                                          @Nullable List<CandleEntry> latest,
                                                          int limit) {
        Map<Long, CandleEntry> merged = new LinkedHashMap<>();
        appendSeries(merged, existing);
        appendSeries(merged, latest);
        List<CandleEntry> result = new ArrayList<>(merged.values());
        result.sort((left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        return trimToLimit(result, limit);
    }

    // 把单根草稿线安全并到序列尾部。
    @NonNull
    public static List<CandleEntry> mergeDraft(@Nullable List<CandleEntry> base,
                                               @Nullable CandleEntry draft,
                                               int limit) {
        if (draft == null) {
            return trimToLimit(copySeries(base), limit);
        }
        return mergeSeriesByOpenTime(base, Collections.singletonList(draft), limit);
    }

    // 把 1m 序列聚合到指定周期。
    @NonNull
    public static List<CandleEntry> aggregate(@Nullable List<CandleEntry> source,
                                              @Nullable String symbol,
                                              @Nullable String intervalKey,
                                              int limit) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        String normalizedInterval = normalizeIntervalKey(intervalKey);
        long intervalMs = resolveIntervalMs(normalizedInterval);
        if (intervalMs <= 0L) {
            return trimToLimit(copySeries(source), limit);
        }
        List<CandleEntry> sorted = copySeries(source);
        sorted.sort((left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        List<CandleEntry> aggregated = new ArrayList<>();
        CandleEntry current = null;
        long currentBucket = Long.MIN_VALUE;
        String resolvedSymbol = normalizeSymbol(symbol, source);
        for (CandleEntry candle : sorted) {
            if (candle == null) {
                continue;
            }
            long bucketStart = resolveBucketStart(candle.getOpenTime(), normalizedInterval);
            if (current == null || currentBucket != bucketStart) {
                if (current != null) {
                    aggregated.add(current);
                }
                currentBucket = bucketStart;
                current = new CandleEntry(
                        resolvedSymbol,
                        bucketStart,
                        bucketStart + intervalMs - 1L,
                        candle.getOpen(),
                        candle.getHigh(),
                        candle.getLow(),
                        candle.getClose(),
                        candle.getVolume(),
                        candle.getQuoteVolume()
                );
                continue;
            }
                current = new CandleEntry(
                    resolvedSymbol,
                    current.getOpenTime(),
                    current.getCloseTime(),
                    current.getOpen(),
                    Math.max(current.getHigh(), candle.getHigh()),
                    Math.min(current.getLow(), candle.getLow()),
                    candle.getClose(),
                    current.getVolume() + candle.getVolume(),
                    current.getQuoteVolume() + candle.getQuoteVolume()
            );
        }
        if (current != null) {
            aggregated.add(current);
        }
        return trimToLimit(aggregated, limit);
    }

    // 返回目标周期最后一个桶的起点。
    public static long resolveBucketStart(long openTimeMs, @Nullable String intervalKey) {
        long intervalMs = resolveIntervalMs(intervalKey);
        if (intervalMs <= 0L) {
            return openTimeMs;
        }
        return openTimeMs - Math.floorMod(openTimeMs, intervalMs);
    }

    // 复制一份安全序列。
    @NonNull
    public static List<CandleEntry> copySeries(@Nullable List<CandleEntry> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<CandleEntry> copy = new ArrayList<>();
        for (CandleEntry candle : source) {
            if (candle != null) {
                copy.add(candle);
            }
        }
        return copy;
    }

    @NonNull
    private static List<CandleEntry> trimToLimit(@Nullable List<CandleEntry> source, int limit) {
        List<CandleEntry> safe = copySeries(source);
        int safeLimit = Math.max(1, limit);
        if (safe.size() <= safeLimit) {
            return safe;
        }
        return new ArrayList<>(safe.subList(safe.size() - safeLimit, safe.size()));
    }

    private static void appendSeries(@NonNull Map<Long, CandleEntry> target,
                                     @Nullable List<CandleEntry> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (CandleEntry candle : source) {
            if (candle == null) {
                continue;
            }
            target.put(candle.getOpenTime(), candle);
        }
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String preferredSymbol,
                                          @Nullable List<CandleEntry> source) {
        if (preferredSymbol != null && !preferredSymbol.trim().isEmpty()) {
            return preferredSymbol.trim();
        }
        if (source == null || source.isEmpty()) {
            return "";
        }
        CandleEntry first = source.get(0);
        return first == null ? "" : first.getSymbol();
    }
}

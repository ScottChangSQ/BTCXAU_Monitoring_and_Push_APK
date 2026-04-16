/*
 * K 线本地聚合辅助工具，负责把已有小周期 K 线快速拼成更大周期的预显示结果。
 * 行情图页在切换周期时先用这里做本地聚合，再等待网络回填完整窗口。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

final class CandleAggregationHelper {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private CandleAggregationHelper() {
    }

    // 把已有 K 线按目标周期重新聚合，并裁剪到最近 limit 根。
    static List<CandleEntry> aggregate(@Nullable List<CandleEntry> source,
                                       @Nullable String symbol,
                                       @Nullable String intervalKey,
                                       int limit) {
        if (source == null || source.isEmpty() || intervalKey == null || intervalKey.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<CandleEntry> sorted = new ArrayList<>(source);
        Collections.sort(sorted, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        Calendar calendar = Calendar.getInstance(UTC);
        List<CandleEntry> aggregated = new ArrayList<>();
        CandleEntry current = null;
        long currentBucket = Long.MIN_VALUE;
        for (CandleEntry item : sorted) {
            if (item == null) {
                continue;
            }
            long bucket = resolveBucketStart(item.getOpenTime(), intervalKey, calendar);
            String resolvedSymbol = resolveSymbol(symbol, item);
            if (current == null || bucket != currentBucket) {
                if (current != null) {
                    aggregated.add(current);
                }
                currentBucket = bucket;
                current = new CandleEntry(
                        resolvedSymbol,
                        bucket,
                        item.getCloseTime(),
                        item.getOpen(),
                        item.getHigh(),
                        item.getLow(),
                        item.getClose(),
                        item.getVolume(),
                        item.getQuoteVolume()
                );
                continue;
            }
            current = new CandleEntry(
                    resolveSymbol(symbol, current),
                    current.getOpenTime(),
                    Math.max(current.getCloseTime(), item.getCloseTime()),
                    current.getOpen(),
                    Math.max(current.getHigh(), item.getHigh()),
                    Math.min(current.getLow(), item.getLow()),
                    item.getClose(),
                    current.getVolume() + item.getVolume(),
                    current.getQuoteVolume() + item.getQuoteVolume()
            );
        }
        if (current != null) {
            aggregated.add(current);
        }
        int safeLimit = Math.max(1, limit);
        if (aggregated.size() <= safeLimit) {
            return aggregated;
        }
        return new ArrayList<>(aggregated.subList(aggregated.size() - safeLimit, aggregated.size()));
    }

    // 预显示只保留已经闭合的目标周期 K 线，未走完整个时间桶的末尾数据不能先上屏。
    static List<CandleEntry> retainClosedTargetCandles(@Nullable List<CandleEntry> source,
                                                       @Nullable String intervalKey,
                                                       long nowMs) {
        if (source == null || source.isEmpty() || intervalKey == null || intervalKey.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<CandleEntry> result = new ArrayList<>();
        Calendar calendar = Calendar.getInstance(UTC);
        for (CandleEntry candle : source) {
            if (candle == null) {
                continue;
            }
            long bucketCloseTime = resolveBucketCloseTime(candle.getOpenTime(), intervalKey, calendar);
            if (bucketCloseTime <= 0L || candle.getCloseTime() < bucketCloseTime || bucketCloseTime >= nowMs) {
                continue;
            }
            result.add(candle);
        }
        return result;
    }

    // 把最新一根已收盘的小周期 K 线合并进当前展示序列，只处理向前推进和同桶补齐。
    static List<CandleEntry> mergeClosedBaseCandle(@Nullable List<CandleEntry> existing,
                                                   @Nullable CandleEntry incoming,
                                                   @Nullable String symbol,
                                                   @Nullable String intervalKey,
                                                   int limit) {
        if (incoming == null || intervalKey == null || intervalKey.trim().isEmpty()) {
            return existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        }
        List<CandleEntry> current = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        Collections.sort(current, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        if (current.isEmpty()) {
            return aggregate(Collections.singletonList(incoming), symbol, intervalKey, limit);
        }
        Calendar calendar = Calendar.getInstance(UTC);
        long incomingBucket = resolveBucketStart(incoming.getOpenTime(), intervalKey, calendar);
        String resolvedSymbol = resolveSymbol(symbol, incoming);
        CandleEntry last = current.get(current.size() - 1);
        long lastBucket = resolveBucketStart(last.getOpenTime(), intervalKey, calendar);
        if (incomingBucket < lastBucket) {
            return current;
        }
        if (incomingBucket == lastBucket) {
            if (incoming.getCloseTime() <= last.getCloseTime()) {
                return current;
            }
            current.set(current.size() - 1, new CandleEntry(
                    resolveSymbol(symbol, last),
                    last.getOpenTime(),
                    incoming.getCloseTime(),
                    last.getOpen(),
                    Math.max(last.getHigh(), incoming.getHigh()),
                    Math.min(last.getLow(), incoming.getLow()),
                    incoming.getClose(),
                    last.getVolume() + incoming.getVolume(),
                    last.getQuoteVolume() + incoming.getQuoteVolume()
            ));
            return trimToLimit(current, limit);
        }
        current.add(new CandleEntry(
                resolvedSymbol,
                incomingBucket,
                incoming.getCloseTime(),
                incoming.getOpen(),
                incoming.getHigh(),
                incoming.getLow(),
                incoming.getClose(),
                incoming.getVolume(),
                incoming.getQuoteVolume()
        ));
        return trimToLimit(current, limit);
    }

    // 把实时中的 1 分钟底稿合并进本地分钟缓存；同一分钟只替换，不重复累加。
    static List<CandleEntry> mergeRealtimeBaseCandle(@Nullable List<CandleEntry> existing,
                                                     @Nullable CandleEntry incoming,
                                                     @Nullable String symbol,
                                                     int limit) {
        if (incoming == null) {
            return existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        }
        List<CandleEntry> current = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        String resolvedSymbol = resolveSymbol(symbol, incoming);
        CandleEntry normalizedIncoming = new CandleEntry(
                resolvedSymbol,
                incoming.getOpenTime(),
                incoming.getCloseTime(),
                incoming.getOpen(),
                incoming.getHigh(),
                incoming.getLow(),
                incoming.getClose(),
                incoming.getVolume(),
                incoming.getQuoteVolume()
        );
        boolean replaced = false;
        for (int i = 0; i < current.size(); i++) {
            CandleEntry item = current.get(i);
            if (item != null && item.getOpenTime() == normalizedIncoming.getOpenTime()) {
                current.set(i, normalizedIncoming);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            current.add(normalizedIncoming);
        }
        Collections.sort(current, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        return trimToLimit(current, limit);
    }

    private static String resolveSymbol(@Nullable String preferredSymbol, @Nullable CandleEntry item) {
        if (preferredSymbol != null && !preferredSymbol.trim().isEmpty()) {
            return preferredSymbol.trim();
        }
        return item == null ? "" : item.getSymbol();
    }

    private static long resolveBucketStart(long openTimeMs, String intervalKey, Calendar calendar) {
        String normalized = intervalKey == null ? "" : intervalKey.trim();
        long fixedIntervalMs = resolveFixedIntervalMs(normalized);
        if (fixedIntervalMs > 0L) {
            return openTimeMs - Math.floorMod(openTimeMs, fixedIntervalMs);
        }
        calendar.setTimeInMillis(openTimeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if ("1w".equals(normalized)) {
            int day = calendar.get(Calendar.DAY_OF_WEEK);
            int delta = day == Calendar.SUNDAY ? -6 : (Calendar.MONDAY - day);
            calendar.add(Calendar.DAY_OF_MONTH, delta);
            return calendar.getTimeInMillis();
        }
        if ("1M".equals(normalized)) {
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            return calendar.getTimeInMillis();
        }
        if ("1y".equals(normalized)) {
            calendar.set(Calendar.DAY_OF_YEAR, 1);
            return calendar.getTimeInMillis();
        }
        return openTimeMs;
    }

    private static long resolveBucketCloseTime(long openTimeMs, String intervalKey, Calendar calendar) {
        long bucketStart = resolveBucketStart(openTimeMs, intervalKey, calendar);
        long nextBucketStart = resolveNextBucketStart(bucketStart, intervalKey, calendar);
        if (nextBucketStart <= bucketStart) {
            return bucketStart;
        }
        return nextBucketStart - 1L;
    }

    private static long resolveNextBucketStart(long bucketStartMs, String intervalKey, Calendar calendar) {
        String normalized = intervalKey == null ? "" : intervalKey.trim();
        long fixedIntervalMs = resolveFixedIntervalMs(normalized);
        if (fixedIntervalMs > 0L) {
            return bucketStartMs + fixedIntervalMs;
        }
        calendar.setTimeInMillis(bucketStartMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if ("1w".equals(normalized)) {
            calendar.add(Calendar.DAY_OF_MONTH, 7);
            return calendar.getTimeInMillis();
        }
        if ("1M".equals(normalized)) {
            calendar.add(Calendar.MONTH, 1);
            return calendar.getTimeInMillis();
        }
        if ("1y".equals(normalized)) {
            calendar.add(Calendar.YEAR, 1);
            return calendar.getTimeInMillis();
        }
        return bucketStartMs;
    }

    private static long resolveFixedIntervalMs(String intervalKey) {
        if ("1m".equals(intervalKey)) return 60_000L;
        if ("5m".equals(intervalKey)) return 5L * 60_000L;
        if ("15m".equals(intervalKey)) return 15L * 60_000L;
        if ("30m".equals(intervalKey)) return 30L * 60_000L;
        if ("1h".equals(intervalKey)) return 60L * 60_000L;
        if ("4h".equals(intervalKey)) return 4L * 60L * 60_000L;
        if ("1d".equals(intervalKey)) return 24L * 60L * 60_000L;
        return -1L;
    }

    private static List<CandleEntry> trimToLimit(List<CandleEntry> items, int limit) {
        int safeLimit = Math.max(1, limit);
        if (items == null || items.size() <= safeLimit) {
            return items == null ? new ArrayList<>() : items;
        }
        return new ArrayList<>(items.subList(items.size() - safeLimit, items.size()));
    }
}

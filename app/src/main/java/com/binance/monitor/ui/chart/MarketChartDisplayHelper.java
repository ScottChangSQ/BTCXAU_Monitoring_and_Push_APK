/*
 * 行情图页显示辅助，负责处理本地预显示与网络回填的合并，以及是否需要阻塞式 loading。
 * 与 MarketChartActivity 配合，减少切周期时的卡顿和短窗口覆盖问题。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MarketChartDisplayHelper {

    private MarketChartDisplayHelper() {
    }

    // 按目标周期校验预显示序列是否仍可信，避免错周期缓存把周/月线显示成分钟线。
    static boolean isSeriesCompatibleForInterval(@Nullable String intervalKey,
                                                 @Nullable List<CandleEntry> source) {
        if (source == null || source.isEmpty()) {
            return true;
        }
        if (shouldRejectSparseLongIntervalPreview(intervalKey, source.size())) {
            return false;
        }
        if (source.size() < 2) {
            return true;
        }
        long expectedMinGapMs = resolveExpectedMinGapMs(intervalKey);
        if (expectedMinGapMs <= 0L) {
            return true;
        }
        long minPositiveGapMs = Long.MAX_VALUE;
        for (int i = 1; i < source.size(); i++) {
            CandleEntry previous = source.get(i - 1);
            CandleEntry current = source.get(i);
            if (previous == null || current == null) {
                continue;
            }
            long gapMs = current.getOpenTime() - previous.getOpenTime();
            if (gapMs > 0L) {
                minPositiveGapMs = Math.min(minPositiveGapMs, gapMs);
            }
        }
        return minPositiveGapMs == Long.MAX_VALUE || minPositiveGapMs >= expectedMinGapMs;
    }

    // 长周期图优先让网络结果覆盖不可信的预显示，避免旧缓存继续污染图表。
    static List<CandleEntry> mergeDisplaySeries(@Nullable String intervalKey,
                                                @Nullable List<CandleEntry> preview,
                                                @Nullable List<CandleEntry> fetched,
                                                int limit) {
        List<CandleEntry> safePreview = isSeriesCompatibleForInterval(intervalKey, preview)
                ? preview
                : null;
        return mergeDisplaySeries(safePreview, fetched, limit);
    }

    // 先保留本地预显示，再让网络结果覆盖同一时间桶，避免更短网络窗口把当前图表“盖短”。
    static List<CandleEntry> mergeDisplaySeries(@Nullable List<CandleEntry> preview,
                                                @Nullable List<CandleEntry> fetched,
                                                int limit) {
        Map<Long, CandleEntry> merged = new LinkedHashMap<>();
        appendSeries(merged, preview);
        appendSeries(merged, fetched);
        List<CandleEntry> result = new ArrayList<>(merged.values());
        result.sort((left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        int safeLimit = Math.max(1, limit);
        if (result.size() <= safeLimit) {
            return result;
        }
        return new ArrayList<>(result.subList(result.size() - safeLimit, result.size()));
    }

    // 只有完全没有可见 K 线时，才显示阻塞式 loading；否则静默后台回填即可。
    static boolean shouldShowBlockingLoading(boolean autoRefresh,
                                            @Nullable List<CandleEntry> visibleCandles) {
        return !autoRefresh && (visibleCandles == null || visibleCandles.isEmpty());
    }

    // 把一组 K 线按 openTime 合并进结果，同时间桶以后到的数据覆盖先到的数据。
    private static void appendSeries(Map<Long, CandleEntry> target,
                                     @Nullable List<CandleEntry> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (CandleEntry item : source) {
            if (item == null) {
                continue;
            }
            target.put(item.getOpenTime(), item);
        }
    }

    // 给不同周期定义一个“最小合理间距”，用于识别明显串周期的旧缓存。
    private static long resolveExpectedMinGapMs(@Nullable String intervalKey) {
        if (intervalKey == null) {
            return -1L;
        }
        String normalized = normalizeIntervalKey(intervalKey);
        if ("1m".equals(normalized)) {
            return 30_000L;
        }
        if ("5m".equals(normalized)) {
            return 3L * 60_000L;
        }
        if ("15m".equals(normalized)) {
            return 10L * 60_000L;
        }
        if ("30m".equals(normalized)) {
            return 20L * 60_000L;
        }
        if ("1h".equals(normalized)) {
            return 45L * 60_000L;
        }
        if ("4h".equals(normalized)) {
            return 3L * 60L * 60_000L;
        }
        if ("1d".equals(normalized)) {
            return 20L * 60L * 60_000L;
        }
        if ("1w".equals(normalized)) {
            return 6L * 24L * 60L * 60_000L;
        }
        if ("1M".equals(normalized)) {
            return 25L * 24L * 60L * 60_000L;
        }
        if ("1y".equals(normalized)) {
            return 300L * 24L * 60L * 60_000L;
        }
        return -1L;
    }

    // 周/月/年线如果本地只剩单根，基本可以判定是短底稿误聚合，不应继续拿来预显示。
    private static boolean shouldRejectSparseLongIntervalPreview(@Nullable String intervalKey, int size) {
        if (size > 1) {
            return false;
        }
        String normalized = normalizeIntervalKey(intervalKey);
        return "1w".equals(normalized) || "1M".equals(normalized) || "1y".equals(normalized);
    }

    // 统一周期键大小写，保留月线 `1M`，避免被分钟线 `1m` 的忽略大小写比较误判。
    private static String normalizeIntervalKey(@Nullable String intervalKey) {
        if (intervalKey == null) {
            return "";
        }
        String trimmed = intervalKey.trim();
        if ("1M".equals(trimmed)) {
            return "1M";
        }
        return trimmed.toLowerCase();
    }
}

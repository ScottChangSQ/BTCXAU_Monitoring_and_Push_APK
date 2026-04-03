/*
 * 行情图页显示辅助，负责处理本地预显示与网络回填的合并，以及是否需要阻塞式 loading。
 * 与 MarketChartActivity 配合，减少切周期时的卡顿和短窗口覆盖问题。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MarketChartDisplayHelper {

    static final class DisplayUpdate {
        final List<CandleEntry> toDisplay;
        final boolean candlesChanged;
        final boolean shouldFollowLatest;

        private DisplayUpdate(List<CandleEntry> toDisplay,
                              boolean candlesChanged,
                              boolean shouldFollowLatest) {
            this.toDisplay = toDisplay;
            this.candlesChanged = candlesChanged;
            this.shouldFollowLatest = shouldFollowLatest;
        }
    }

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
        List<CandleEntry> result = mergeSeriesByOpenTime(preview, fetched);
        int safeLimit = Math.max(1, limit);
        if (result.size() <= safeLimit) {
            return result;
        }
        return new ArrayList<>(result.subList(result.size() - safeLimit, result.size()));
    }

    // 用户已左滑翻出的旧历史应继续保留，后台刷新只更新重叠桶和最新尾部，不再把历史裁回默认窗口。
    static List<CandleEntry> mergeDisplaySeriesKeepingHistory(@Nullable List<CandleEntry> preview,
                                                              @Nullable List<CandleEntry> fetched,
                                                              int limit) {
        if (preview == null || preview.size() <= Math.max(1, limit)) {
            return mergeDisplaySeries(preview, fetched, limit);
        }
        return mergeSeriesByOpenTime(preview, fetched);
    }

    // 本地分钟聚合只允许修正最新可见桶，避免把已经拿到的官方历史整段覆盖坏。
    static List<CandleEntry> mergeRealtimeTail(@Nullable List<CandleEntry> base,
                                               @Nullable List<CandleEntry> realtimeTail) {
        List<CandleEntry> safeBase = base == null ? new ArrayList<>() : new ArrayList<>(base);
        if (realtimeTail == null || realtimeTail.isEmpty()) {
            return safeBase;
        }
        if (safeBase.isEmpty()) {
            return new ArrayList<>(realtimeTail);
        }
        long latestBaseOpenTime = safeBase.get(safeBase.size() - 1).getOpenTime();
        List<CandleEntry> filteredTail = new ArrayList<>();
        for (CandleEntry item : realtimeTail) {
            if (item == null || item.getOpenTime() < latestBaseOpenTime) {
                continue;
            }
            filteredTail.add(item);
        }
        Map<Long, CandleEntry> merged = new LinkedHashMap<>();
        appendSeries(merged, safeBase);
        appendSeries(merged, filteredTail);
        List<CandleEntry> result = new ArrayList<>(merged.values());
        result.sort((left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        return result;
    }

    // 只有完全没有可见 K 线时，才显示阻塞式 loading；否则静默后台回填即可。
    static boolean shouldShowBlockingLoading(boolean autoRefresh,
                                            @Nullable List<CandleEntry> visibleCandles) {
        return !autoRefresh && (visibleCandles == null || visibleCandles.isEmpty());
    }

    // 按 openTime 合并两段 K 线，同时间桶由后来的数据覆盖，并统一输出升序结果。
    static List<CandleEntry> mergeSeriesByOpenTime(@Nullable List<CandleEntry> existing,
                                                   @Nullable List<CandleEntry> latest) {
        if ((existing == null || existing.isEmpty()) && (latest == null || latest.isEmpty())) {
            return new ArrayList<>();
        }
        Map<Long, CandleEntry> merged = new LinkedHashMap<>();
        appendSeries(merged, existing);
        appendSeries(merged, latest);
        List<CandleEntry> result = new ArrayList<>(merged.values());
        result.sort((left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        return result;
    }

    // 把服务端闭合 candles 与 latestPatch 统一按 openTime 合并，避免 Activity 再维护一套末尾替换分支。
    static List<CandleEntry> mergeSeriesWithLatestPatch(@Nullable List<CandleEntry> candles,
                                                        @Nullable CandleEntry latestPatch) {
        if (latestPatch == null) {
            return mergeSeriesByOpenTime(candles, null);
        }
        return mergeSeriesByOpenTime(candles, Collections.singletonList(latestPatch));
    }

    // 统一生成一次网络回包后的显示计划，避免 Activity 自己散落预览校验、变化判断和跟随最新逻辑。
    static DisplayUpdate buildDisplayUpdate(@Nullable String intervalKey,
                                            @Nullable List<CandleEntry> preview,
                                            @Nullable List<CandleEntry> fetched,
                                            int limit,
                                            @Nullable List<CandleEntry> currentVisible,
                                            boolean autoRefresh,
                                            boolean followingLatestViewport) {
        List<CandleEntry> safePreview = isSeriesCompatibleForInterval(intervalKey, preview)
                ? preview
                : null;
        List<CandleEntry> toDisplay = mergeDisplaySeriesKeepingHistory(safePreview, fetched, limit);
        boolean candlesChanged = !isSameSeries(currentVisible, toDisplay);
        boolean shouldFollowLatest = autoRefresh && followingLatestViewport;
        return new DisplayUpdate(toDisplay, candlesChanged, shouldFollowLatest);
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

    // 仅在时间、收盘价或成交量发生变化时才视为需要重绘。
    private static boolean isSameSeries(@Nullable List<CandleEntry> left,
                                        @Nullable List<CandleEntry> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            CandleEntry leftItem = left.get(i);
            CandleEntry rightItem = right.get(i);
            if (leftItem == rightItem) {
                continue;
            }
            if (leftItem == null || rightItem == null) {
                return false;
            }
            if (leftItem.getOpenTime() != rightItem.getOpenTime()
                    || leftItem.getCloseTime() != rightItem.getCloseTime()
                    || Double.compare(leftItem.getOpen(), rightItem.getOpen()) != 0
                    || Double.compare(leftItem.getHigh(), rightItem.getHigh()) != 0
                    || Double.compare(leftItem.getLow(), rightItem.getLow()) != 0
                    || Double.compare(leftItem.getClose(), rightItem.getClose()) != 0
                    || Double.compare(leftItem.getVolume(), rightItem.getVolume()) != 0
                    || Double.compare(leftItem.getQuoteVolume(), rightItem.getQuoteVolume()) != 0) {
                return false;
            }
        }
        return true;
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

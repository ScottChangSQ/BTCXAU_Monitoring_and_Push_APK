/*
 * 异常交易图表标注构建器，负责把异常记录按当前 K 线时间桶聚合成图表小圆点。
 * 供行情图表页复用，统一处理颜色强度、堆叠高度和标签文本。
 */
package com.binance.monitor.ui.chart;

import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AbnormalAnnotationOverlayBuilder {
    private static final int START_COLOR = 0xFFF2C94C;
    private static final int END_COLOR = 0xFFF6465D;

    private AbnormalAnnotationOverlayBuilder() {
    }

    // 把异常记录按图表当前 K 线时间桶聚合，生成可直接映射到绘制层的标注数据。
    static List<BucketAnnotation> build(List<AbnormalRecord> records, List<CandleEntry> candles) {
        if (records == null || records.isEmpty() || candles == null || candles.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, BucketState> grouped = new LinkedHashMap<>();
        for (AbnormalRecord record : records) {
            if (record == null) {
                continue;
            }
            long bucketOpenTime = resolveBucketOpenTime(record, candles);
            if (bucketOpenTime <= 0L) {
                continue;
            }
            BucketState state = grouped.get(bucketOpenTime);
            if (state == null) {
                state = new BucketState(bucketOpenTime);
                grouped.put(bucketOpenTime, state);
            }
            state.accept(record);
        }
        List<BucketState> ordered = new ArrayList<>(grouped.values());
        ordered.sort(Comparator.comparingLong(item -> item.anchorTimeMs));
        int maxBucketCount = 1;
        for (BucketState state : ordered) {
            maxBucketCount = Math.max(maxBucketCount, state.count);
        }
        List<BucketAnnotation> out = new ArrayList<>(ordered.size());
        for (BucketState state : ordered) {
            float intensity = resolveIntensity(state.count, maxBucketCount);
            out.add(new BucketAnnotation(
                    state.anchorTimeMs,
                    state.displayPrice,
                    buildLabel(state),
                    interpolateColor(START_COLOR, END_COLOR, intensity),
                    "abn|" + state.anchorTimeMs,
                    state.count,
                    intensity
            ));
        }
        return out;
    }

    // 把异常记录定位到当前图表里所属的 K 线时间桶。
    private static long resolveBucketOpenTime(AbnormalRecord record, List<CandleEntry> candles) {
        long recordTime = resolveRecordTime(record);
        if (recordTime <= 0L || candles.isEmpty()) {
            return -1L;
        }
        int floorIndex = floorCandleIndexByTime(candles, recordTime);
        if (floorIndex < 0) {
            return -1L;
        }
        CandleEntry candle = candles.get(floorIndex);
        if (recordTime < candle.getOpenTime()) {
            return -1L;
        }
        long intervalMs = estimateIntervalMs(candles);
        long candleEndTime = Math.max(
                candle.getOpenTime() + intervalMs,
                candle.getCloseTime() > candle.getOpenTime()
                        ? candle.getCloseTime()
                        : candle.getOpenTime()
        );
        if (recordTime > candleEndTime && floorIndex == candles.size() - 1) {
            return -1L;
        }
        return candle.getOpenTime();
    }

    // 取异常记录在时间轴上的实际锚点，优先使用收盘时刻。
    private static long resolveRecordTime(AbnormalRecord record) {
        if (record == null) {
            return -1L;
        }
        if (record.getCloseTime() > 0L) {
            return Math.max(1L, record.getCloseTime() - 1L);
        }
        return record.getTimestamp();
    }

    // 通过二分查找找到目标时间所在或向下取整后的 K 线。
    private static int floorCandleIndexByTime(List<CandleEntry> candles, long targetTime) {
        int low = 0;
        int high = candles.size() - 1;
        int floorIndex = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long openTime = candles.get(mid).getOpenTime();
            if (openTime <= targetTime) {
                floorIndex = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return floorIndex;
    }

    // 估算当前图表 K 线间隔，兼容最后一根 K 线的边界判断。
    private static long estimateIntervalMs(List<CandleEntry> candles) {
        if (candles.size() < 2) {
            return 60_000L;
        }
        long delta = candles.get(1).getOpenTime() - candles.get(0).getOpenTime();
        return Math.max(1L, delta);
    }

    // 把异常次数映射到 0~1 的强度，次数越多越接近红色和更高位置。
    private static float resolveIntensity(int count, int maxCount) {
        if (count <= 1 || maxCount <= 1) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, (count - 1f) / (maxCount - 1f)));
    }

    // 生成点按后的简要说明，单条异常显示摘要，多条异常显示次数。
    private static String buildLabel(BucketState state) {
        String priceText = FormatUtils.formatPrice(state.displayPrice);
        if (state.count <= 1) {
            String summary = state.summary == null ? "" : state.summary.trim();
            if (summary.isEmpty()) {
                return "ABN $" + priceText;
            }
            String shortSummary = summary.length() > 12 ? summary.substring(0, 12) + "…" : summary;
            return "ABN $" + priceText + " " + shortSummary;
        }
        return String.format(Locale.getDefault(), "ABN x%d $%s", state.count, priceText);
    }

    // 在不依赖 Android 运行时的前提下插值颜色，便于本地单测验证。
    private static int interpolateColor(int startColor, int endColor, float ratio) {
        float safeRatio = Math.max(0f, Math.min(1f, ratio));
        int startA = (startColor >> 24) & 0xFF;
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;
        int endA = (endColor >> 24) & 0xFF;
        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;
        int a = Math.round(startA + (endA - startA) * safeRatio);
        int r = Math.round(startR + (endR - startR) * safeRatio);
        int g = Math.round(startG + (endG - startG) * safeRatio);
        int b = Math.round(startB + (endB - startB) * safeRatio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static final class BucketAnnotation {
        final long anchorTimeMs;
        final double price;
        final String label;
        final int color;
        final String groupId;
        final int count;
        final float intensity;

        BucketAnnotation(long anchorTimeMs,
                         double price,
                         String label,
                         int color,
                         String groupId,
                         int count,
                         float intensity) {
            this.anchorTimeMs = anchorTimeMs;
            this.price = price;
            this.label = label == null ? "" : label;
            this.color = color;
            this.groupId = groupId == null ? "" : groupId;
            this.count = Math.max(1, count);
            this.intensity = Math.max(0f, Math.min(1f, intensity));
        }
    }

    private static final class BucketState {
        private final long anchorTimeMs;
        private int count;
        private long latestRecordTime;
        private double displayPrice;
        private String summary;

        BucketState(long anchorTimeMs) {
            this.anchorTimeMs = anchorTimeMs;
        }

        // 把一条异常记录并入当前时间桶。
        private void accept(AbnormalRecord record) {
            count++;
            long recordTime = resolveRecordTime(record);
            if (recordTime >= latestRecordTime) {
                latestRecordTime = recordTime;
                displayPrice = record.getClosePrice() > 0d ? record.getClosePrice() : record.getOpenPrice();
                summary = record.getTriggerSummary();
            }
        }
    }
}

/*
 * 历史成交视口辅助，负责把窗口外的成交时间继续外推到图表坐标，并判断点线是否真正进入当前可视区域。
 * KlineChartView 通过这里避免把窗口外历史成交强行贴到左右边界。
 */
package com.binance.monitor.ui.chart;

final class HistoricalTradeViewportHelper {

    private HistoricalTradeViewportHelper() {
    }

    // 把窗口外的成交时间按固定周期继续外推成原始索引，保证位置仍然按真实时间延长。
    static float resolveOverflowRawIndex(long targetTime,
                                         long firstOpenTime,
                                         long lastOpenTime,
                                         int lastIndex,
                                         long intervalMs) {
        long safeIntervalMs = Math.max(1L, intervalMs);
        int safeLastIndex = Math.max(0, lastIndex);
        if (targetTime <= firstOpenTime) {
            return (float) (targetTime - firstOpenTime) / (float) safeIntervalMs;
        }
        if (targetTime >= lastOpenTime) {
            return safeLastIndex + (float) (targetTime - lastOpenTime) / (float) safeIntervalMs;
        }
        return Float.NaN;
    }

    // 把成交时间映射到单根 K 线对应的完整时间槽位，保证时间点落在该 K 线的真实区间内。
    static float resolveTimeInsideBucketRawIndex(int bucketIndex,
                                                 long bucketOpenTime,
                                                 long bucketCloseExclusiveTime,
                                                 long targetTime) {
        int safeBucketIndex = Math.max(0, bucketIndex);
        if (bucketCloseExclusiveTime <= bucketOpenTime) {
            return safeBucketIndex;
        }
        long clampedTargetTime = Math.max(bucketOpenTime, Math.min(targetTime, bucketCloseExclusiveTime));
        float ratio = (float) (clampedTargetTime - bucketOpenTime)
                / (float) (bucketCloseExclusiveTime - bucketOpenTime);
        ratio = Math.max(0f, Math.min(1f, ratio));
        return safeBucketIndex - 0.5f + ratio;
    }

    // 只有真正超出当前已加载窗口两端后，才继续向外投影到窗口外侧。
    static float resolveOverflowRawIndexFromWindow(long targetTime,
                                                   long firstOpenTime,
                                                   long lastVisibleExclusiveTime,
                                                   int lastIndex,
                                                   long intervalMs) {
        long safeIntervalMs = Math.max(1L, intervalMs);
        int safeLastIndex = Math.max(0, lastIndex);
        if (targetTime < firstOpenTime) {
            return -0.5f + (float) (targetTime - firstOpenTime) / (float) safeIntervalMs;
        }
        if (targetTime >= lastVisibleExclusiveTime) {
            return safeLastIndex + 0.5f
                    + (float) (targetTime - lastVisibleExclusiveTime) / (float) safeIntervalMs;
        }
        return Float.NaN;
    }

    // 判断单个成交点是否真的进入当前可见横轴范围。
    static boolean isPointVisible(float x, float viewportLeft, float viewportRight) {
        return !Float.isNaN(x) && x >= viewportLeft && x <= viewportRight;
    }

    // 判断成交连线是否和当前可见横轴有交集。
    static boolean isSegmentVisible(float startX, float endX, float viewportLeft, float viewportRight) {
        if (Float.isNaN(startX) || Float.isNaN(endX)) {
            return false;
        }
        float segmentLeft = Math.min(startX, endX);
        float segmentRight = Math.max(startX, endX);
        return segmentRight >= viewportLeft && segmentLeft <= viewportRight;
    }
}

/*
 * 账户曲线共享高亮辅助，负责按当前横轴位置解析主图与附图的联动数据点。
 * 供 AccountStatsBridgeActivity 在多联图长按联动时复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.CurveAnalyticsHelper.DailyReturnPoint;
import com.binance.monitor.ui.account.CurveAnalyticsHelper.DrawdownPoint;
import com.binance.monitor.ui.account.model.CurvePoint;

import java.util.List;

final class AccountCurveHighlightHelper {

    static final class HighlightSnapshot {
        private final long targetTimestamp;
        private final CurvePoint curvePoint;
        @Nullable
        private final DrawdownPoint drawdownPoint;
        @Nullable
        private final DailyReturnPoint dailyReturnPoint;

        HighlightSnapshot(long targetTimestamp,
                          CurvePoint curvePoint,
                          @Nullable DrawdownPoint drawdownPoint,
                          @Nullable DailyReturnPoint dailyReturnPoint) {
            this.targetTimestamp = targetTimestamp;
            this.curvePoint = curvePoint;
            this.drawdownPoint = drawdownPoint;
            this.dailyReturnPoint = dailyReturnPoint;
        }

        long getTargetTimestamp() {
            return targetTimestamp;
        }

        CurvePoint getCurvePoint() {
            return curvePoint;
        }

        @Nullable
        DrawdownPoint getDrawdownPoint() {
            return drawdownPoint;
        }

        @Nullable
        DailyReturnPoint getDailyReturnPoint() {
            return dailyReturnPoint;
        }
    }

    private AccountCurveHighlightHelper() {
    }

    // 按当前横轴位置解析共享高亮，保证附图长按时主图会跟着当前手指位置变化。
    @Nullable
    static HighlightSnapshot resolveSharedHighlight(@Nullable List<CurvePoint> curvePoints,
                                                    @Nullable List<DrawdownPoint> drawdownPoints,
                                                    @Nullable List<DailyReturnPoint> dailyReturnPoints,
                                                    long fallbackTimestamp,
                                                    float xRatio) {
        return resolveSharedHighlight(
                curvePoints,
                drawdownPoints,
                dailyReturnPoints,
                fallbackTimestamp,
                xRatio,
                false
        );
    }

    // 允许调用方声明是否优先使用已经算好的精确时间，避免附图再次被比例换算拉回旧值。
    @Nullable
    static HighlightSnapshot resolveSharedHighlight(@Nullable List<CurvePoint> curvePoints,
                                                    @Nullable List<DrawdownPoint> drawdownPoints,
                                                    @Nullable List<DailyReturnPoint> dailyReturnPoints,
                                                    long fallbackTimestamp,
                                                    float xRatio,
                                                    boolean preferExactTimestamp) {
        long targetTimestamp = resolveTargetTimestamp(curvePoints, fallbackTimestamp, xRatio, preferExactTimestamp);
        CurvePoint curvePoint = CurveSeriesInterpolationHelper.interpolateCurvePoint(curvePoints, targetTimestamp);
        if (curvePoint == null) {
            return null;
        }
        long actualTimestamp = curvePoint.getTimestamp();
        return new HighlightSnapshot(
                actualTimestamp,
                curvePoint,
                CurveSeriesInterpolationHelper.interpolateDrawdownPoint(drawdownPoints, actualTimestamp),
                CurveSeriesInterpolationHelper.interpolateDailyReturnPoint(dailyReturnPoints, actualTimestamp)
        );
    }

    // 优先按共享横轴比例换算目标时间，避免附图点位稀疏时弹窗停在旧值上。
    private static long resolveTargetTimestamp(@Nullable List<CurvePoint> curvePoints,
                                               long fallbackTimestamp,
                                               float xRatio,
                                               boolean preferExactTimestamp) {
        if (curvePoints == null || curvePoints.isEmpty()) {
            return fallbackTimestamp;
        }
        CurvePoint first = curvePoints.get(0);
        CurvePoint last = curvePoints.get(curvePoints.size() - 1);
        long startTs = first.getTimestamp();
        long endTs = Math.max(startTs, last.getTimestamp());
        if (preferExactTimestamp && fallbackTimestamp > 0L) {
            return clampTimestamp(fallbackTimestamp, startTs, endTs);
        }
        if (xRatio >= 0f && xRatio <= 1f && endTs > startTs) {
            return startTs + Math.round(clampRatio(xRatio) * (endTs - startTs));
        }
        if (fallbackTimestamp > 0L) {
            return clampTimestamp(fallbackTimestamp, startTs, endTs);
        }
        return startTs;
    }

    // 按共享时间轴把目标时间换算回比例，保证跨图同步时十字线位置跟最终时间一致。
    static float resolveTimestampRatio(@Nullable List<CurvePoint> curvePoints, long targetTimestamp) {
        if (curvePoints == null || curvePoints.isEmpty()) {
            return -1f;
        }
        CurvePoint first = curvePoints.get(0);
        CurvePoint last = curvePoints.get(curvePoints.size() - 1);
        long startTs = first.getTimestamp();
        long endTs = Math.max(startTs, last.getTimestamp());
        if (endTs <= startTs) {
            return 0f;
        }
        long clampedTimestamp = clampTimestamp(targetTimestamp, startTs, endTs);
        return clampRatio((float) (clampedTimestamp - startTs) / (float) (endTs - startTs));
    }

    private static float clampRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }

    private static long clampTimestamp(long timestamp, long startTs, long endTs) {
        return Math.max(startTs, Math.min(endTs, timestamp));
    }
}

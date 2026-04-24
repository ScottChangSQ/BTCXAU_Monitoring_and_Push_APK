/*
 * 账户曲线共享高亮辅助，负责按当前横轴位置解析主图与附图的联动数据点。
 * 供 AccountStatsBridgeActivity 在多联图长按联动时复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.CurveAnalyticsHelper.DailyReturnPoint;
import com.binance.monitor.ui.account.CurveAnalyticsHelper.DrawdownPoint;
import com.binance.monitor.domain.account.model.CurvePoint;

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
                resolveDefaultViewportStart(curvePoints, fallbackTimestamp),
                resolveDefaultViewportEnd(curvePoints, fallbackTimestamp),
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
        return resolveSharedHighlight(
                curvePoints,
                drawdownPoints,
                dailyReturnPoints,
                resolveDefaultViewportStart(curvePoints, fallbackTimestamp),
                resolveDefaultViewportEnd(curvePoints, fallbackTimestamp),
                fallbackTimestamp,
                xRatio,
                preferExactTimestamp
        );
    }

    // 允许宿主显式传入当前可视时间范围，避免附图右侧空白区再次被真实采样尾点截断。
    @Nullable
    static HighlightSnapshot resolveSharedHighlight(@Nullable List<CurvePoint> curvePoints,
                                                    @Nullable List<DrawdownPoint> drawdownPoints,
                                                    @Nullable List<DailyReturnPoint> dailyReturnPoints,
                                                    long viewportStartTs,
                                                    long viewportEndTs,
                                                    long fallbackTimestamp,
                                                    float xRatio,
                                                    boolean preferExactTimestamp) {
        long targetTimestamp = resolveTargetTimestamp(
                curvePoints,
                viewportStartTs,
                viewportEndTs,
                fallbackTimestamp,
                xRatio,
                preferExactTimestamp
        );
        CurvePoint curvePoint = CurveSeriesInterpolationHelper.interpolateCurvePoint(curvePoints, targetTimestamp);
        if (curvePoint == null) {
            return null;
        }
        return new HighlightSnapshot(
                targetTimestamp,
                curvePoint,
                CurveSeriesInterpolationHelper.interpolateDrawdownPoint(drawdownPoints, targetTimestamp),
                CurveSeriesInterpolationHelper.interpolateDailyReturnPoint(dailyReturnPoints, targetTimestamp)
        );
    }

    // 优先按共享横轴比例换算目标时间，避免附图点位稀疏时弹窗停在旧值上。
    private static long resolveTargetTimestamp(@Nullable List<CurvePoint> curvePoints,
                                               long viewportStartTs,
                                               long viewportEndTs,
                                               long fallbackTimestamp,
                                               float xRatio,
                                               boolean preferExactTimestamp) {
        long startTs = resolveViewportStart(curvePoints, viewportStartTs, viewportEndTs, fallbackTimestamp);
        long endTs = resolveViewportEnd(curvePoints, viewportStartTs, viewportEndTs, startTs, fallbackTimestamp);
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
        return resolveTimestampRatio(
                curvePoints,
                resolveDefaultViewportStart(curvePoints, targetTimestamp),
                resolveDefaultViewportEnd(curvePoints, targetTimestamp),
                targetTimestamp
        );
    }

    // 按宿主给出的可视范围换算比例，避免主图同步时又被真实采样尾点拉回去。
    static float resolveTimestampRatio(@Nullable List<CurvePoint> curvePoints,
                                       long viewportStartTs,
                                       long viewportEndTs,
                                       long targetTimestamp) {
        long startTs = resolveViewportStart(curvePoints, viewportStartTs, viewportEndTs, targetTimestamp);
        long endTs = resolveViewportEnd(curvePoints, viewportStartTs, viewportEndTs, startTs, targetTimestamp);
        if (endTs <= startTs) {
            return 0f;
        }
        long clampedTimestamp = clampTimestamp(targetTimestamp, startTs, endTs);
        return clampRatio((float) (clampedTimestamp - startTs) / (float) (endTs - startTs));
    }

    private static long resolveDefaultViewportStart(@Nullable List<CurvePoint> curvePoints, long fallbackTimestamp) {
        if (curvePoints == null || curvePoints.isEmpty()) {
            return Math.max(0L, fallbackTimestamp);
        }
        return curvePoints.get(0).getTimestamp();
    }

    private static long resolveDefaultViewportEnd(@Nullable List<CurvePoint> curvePoints, long fallbackTimestamp) {
        if (curvePoints == null || curvePoints.isEmpty()) {
            return Math.max(resolveDefaultViewportStart(curvePoints, fallbackTimestamp) + 1L, fallbackTimestamp);
        }
        long startTs = curvePoints.get(0).getTimestamp();
        long lastTs = curvePoints.get(curvePoints.size() - 1).getTimestamp();
        return Math.max(startTs, lastTs);
    }

    private static long resolveViewportStart(@Nullable List<CurvePoint> curvePoints,
                                             long viewportStartTs,
                                             long viewportEndTs,
                                             long fallbackTimestamp) {
        if (viewportEndTs > viewportStartTs) {
            return Math.max(0L, viewportStartTs);
        }
        return resolveDefaultViewportStart(curvePoints, fallbackTimestamp);
    }

    private static long resolveViewportEnd(@Nullable List<CurvePoint> curvePoints,
                                           long viewportStartTs,
                                           long viewportEndTs,
                                           long resolvedStartTs,
                                           long fallbackTimestamp) {
        if (viewportEndTs > viewportStartTs) {
            return Math.max(resolvedStartTs + 1L, viewportEndTs);
        }
        return Math.max(resolvedStartTs, resolveDefaultViewportEnd(curvePoints, fallbackTimestamp));
    }

    private static float clampRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }

    private static long clampTimestamp(long timestamp, long startTs, long endTs) {
        return Math.max(startTs, Math.min(endTs, timestamp));
    }
}

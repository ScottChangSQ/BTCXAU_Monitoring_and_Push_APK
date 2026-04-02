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
        CurvePoint curvePoint = interpolateCurvePoint(curvePoints, targetTimestamp);
        if (curvePoint == null) {
            return null;
        }
        return new HighlightSnapshot(
                targetTimestamp,
                curvePoint,
                interpolateDrawdownPoint(drawdownPoints, targetTimestamp),
                interpolateDailyReturnPoint(dailyReturnPoints, targetTimestamp)
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

    // 按目标时间在线性区间内插值净值、结余和仓位比例，让主图弹窗随手指位置连续变化。
    @Nullable
    private static CurvePoint interpolateCurvePoint(@Nullable List<CurvePoint> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            CurvePoint point = points.get(0);
            return new CurvePoint(point.getTimestamp(), point.getEquity(), point.getBalance(), point.getPositionRatio());
        }
        CurvePoint previous = points.get(0);
        if (timestamp <= previous.getTimestamp()) {
            return new CurvePoint(previous.getTimestamp(), previous.getEquity(), previous.getBalance(), previous.getPositionRatio());
        }
        for (int i = 1; i < points.size(); i++) {
            CurvePoint next = points.get(i);
            if (timestamp <= next.getTimestamp()) {
                return new CurvePoint(
                        timestamp,
                        interpolate(previous.getTimestamp(), previous.getEquity(), next.getTimestamp(), next.getEquity(), timestamp),
                        interpolate(previous.getTimestamp(), previous.getBalance(), next.getTimestamp(), next.getBalance(), timestamp),
                        interpolate(previous.getTimestamp(), previous.getPositionRatio(), next.getTimestamp(), next.getPositionRatio(), timestamp)
                );
            }
            previous = next;
        }
        return new CurvePoint(previous.getTimestamp(), previous.getEquity(), previous.getBalance(), previous.getPositionRatio());
    }

    // 按目标时间在线性区间内插值回撤值。
    @Nullable
    private static DrawdownPoint interpolateDrawdownPoint(@Nullable List<DrawdownPoint> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            DrawdownPoint point = points.get(0);
            return new DrawdownPoint(point.getTimestamp(), point.getDrawdownRate());
        }
        DrawdownPoint previous = points.get(0);
        if (timestamp <= previous.getTimestamp()) {
            return new DrawdownPoint(previous.getTimestamp(), previous.getDrawdownRate());
        }
        for (int i = 1; i < points.size(); i++) {
            DrawdownPoint next = points.get(i);
            if (timestamp <= next.getTimestamp()) {
                return new DrawdownPoint(
                        timestamp,
                        interpolate(previous.getTimestamp(), previous.getDrawdownRate(), next.getTimestamp(), next.getDrawdownRate(), timestamp)
                );
            }
            previous = next;
        }
        return new DrawdownPoint(previous.getTimestamp(), previous.getDrawdownRate());
    }

    // 按目标时间在线性区间内插值日收益值。
    @Nullable
    private static DailyReturnPoint interpolateDailyReturnPoint(@Nullable List<DailyReturnPoint> points,
                                                                long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            DailyReturnPoint point = points.get(0);
            return new DailyReturnPoint(point.getTimestamp(), point.getReturnRate());
        }
        DailyReturnPoint previous = points.get(0);
        if (timestamp <= previous.getTimestamp()) {
            return new DailyReturnPoint(previous.getTimestamp(), previous.getReturnRate());
        }
        for (int i = 1; i < points.size(); i++) {
            DailyReturnPoint next = points.get(i);
            if (timestamp <= next.getTimestamp()) {
                return new DailyReturnPoint(
                        timestamp,
                        interpolate(previous.getTimestamp(), previous.getReturnRate(), next.getTimestamp(), next.getReturnRate(), timestamp)
                );
            }
            previous = next;
        }
        return new DailyReturnPoint(previous.getTimestamp(), previous.getReturnRate());
    }

    private static float clampRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }

    private static double interpolate(long startTs,
                                      double startValue,
                                      long endTs,
                                      double endValue,
                                      long targetTs) {
        if (endTs <= startTs) {
            return endValue;
        }
        double ratio = (double) (targetTs - startTs) / (double) (endTs - startTs);
        ratio = Math.max(0d, Math.min(1d, ratio));
        return startValue + (endValue - startValue) * ratio;
    }

    private static long clampTimestamp(long timestamp, long startTs, long endTs) {
        return Math.max(startTs, Math.min(endTs, timestamp));
    }
}

/*
 * 曲线序列插值辅助，负责按目标时间在相邻真实采样点之间做连续插值。
 * 供分析页四联图共享高亮复用，避免长按拖动时日期和数值卡在旧采样点。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.CurveAnalyticsHelper.DailyReturnPoint;
import com.binance.monitor.ui.account.CurveAnalyticsHelper.DrawdownPoint;
import com.binance.monitor.domain.account.model.CurvePoint;

import java.util.List;

public final class CurveSeriesInterpolationHelper {

    private CurveSeriesInterpolationHelper() {
    }

    // 按目标时间插值净值、结余与仓位比例。
    @Nullable
    public static CurvePoint interpolateCurvePoint(@Nullable List<CurvePoint> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            CurvePoint only = points.get(0);
            return copyCurvePoint(only, only.getTimestamp());
        }
        int upperIndex = findUpperBound(points, timestamp);
        if (upperIndex <= 0) {
            CurvePoint first = points.get(0);
            return copyCurvePoint(first, first.getTimestamp());
        }
        if (upperIndex >= points.size()) {
            CurvePoint last = points.get(points.size() - 1);
            return copyCurvePoint(last, last.getTimestamp());
        }
        CurvePoint left = points.get(upperIndex - 1);
        CurvePoint right = points.get(upperIndex);
        if (timestamp == left.getTimestamp()) {
            return copyCurvePoint(left, left.getTimestamp());
        }
        if (timestamp == right.getTimestamp()) {
            return copyCurvePoint(right, right.getTimestamp());
        }
        double ratio = resolveSegmentRatio(timestamp, left.getTimestamp(), right.getTimestamp());
        return new CurvePoint(
                timestamp,
                interpolateValue(left.getEquity(), right.getEquity(), ratio),
                interpolateValue(left.getBalance(), right.getBalance(), ratio),
                interpolateValue(left.getPositionRatio(), right.getPositionRatio(), ratio)
        );
    }

    // 按目标时间插值回撤率。
    @Nullable
    public static DrawdownPoint interpolateDrawdownPoint(@Nullable List<DrawdownPoint> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            DrawdownPoint only = points.get(0);
            return copyDrawdownPoint(only, only.getTimestamp());
        }
        int upperIndex = findUpperBound(points, timestamp);
        if (upperIndex <= 0) {
            DrawdownPoint first = points.get(0);
            return copyDrawdownPoint(first, first.getTimestamp());
        }
        if (upperIndex >= points.size()) {
            DrawdownPoint last = points.get(points.size() - 1);
            return copyDrawdownPoint(last, last.getTimestamp());
        }
        DrawdownPoint left = points.get(upperIndex - 1);
        DrawdownPoint right = points.get(upperIndex);
        if (timestamp == left.getTimestamp()) {
            return copyDrawdownPoint(left, left.getTimestamp());
        }
        if (timestamp == right.getTimestamp()) {
            return copyDrawdownPoint(right, right.getTimestamp());
        }
        double ratio = resolveSegmentRatio(timestamp, left.getTimestamp(), right.getTimestamp());
        return new DrawdownPoint(
                timestamp,
                interpolateValue(left.getDrawdownRate(), right.getDrawdownRate(), ratio)
        );
    }

    // 按目标时间插值日收益率。
    @Nullable
    public static DailyReturnPoint interpolateDailyReturnPoint(@Nullable List<DailyReturnPoint> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        if (points.size() == 1) {
            DailyReturnPoint only = points.get(0);
            return copyDailyReturnPoint(only, only.getTimestamp());
        }
        int upperIndex = findUpperBound(points, timestamp);
        if (upperIndex <= 0) {
            DailyReturnPoint first = points.get(0);
            return copyDailyReturnPoint(first, first.getTimestamp());
        }
        if (upperIndex >= points.size()) {
            DailyReturnPoint last = points.get(points.size() - 1);
            return copyDailyReturnPoint(last, last.getTimestamp());
        }
        DailyReturnPoint left = points.get(upperIndex - 1);
        DailyReturnPoint right = points.get(upperIndex);
        if (timestamp == left.getTimestamp()) {
            return copyDailyReturnPoint(left, left.getTimestamp());
        }
        if (timestamp == right.getTimestamp()) {
            return copyDailyReturnPoint(right, right.getTimestamp());
        }
        double ratio = resolveSegmentRatio(timestamp, left.getTimestamp(), right.getTimestamp());
        return new DailyReturnPoint(
                timestamp,
                interpolateValue(left.getReturnRate(), right.getReturnRate(), ratio)
        );
    }

    // 二分找到第一个时间戳大于等于目标时间的位置。
    private static <T> int findUpperBound(@Nullable List<T> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return -1;
        }
        int left = 0;
        int right = points.size();
        while (left < right) {
            int mid = (left + right) >>> 1;
            long midTimestamp = resolveTimestamp(points.get(mid));
            if (midTimestamp < timestamp) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        return left;
    }

    // 统一读取三种序列点的时间戳，避免重复写三套二分逻辑。
    private static long resolveTimestamp(Object point) {
        if (point instanceof CurvePoint) {
            return ((CurvePoint) point).getTimestamp();
        }
        if (point instanceof DrawdownPoint) {
            return ((DrawdownPoint) point).getTimestamp();
        }
        return ((DailyReturnPoint) point).getTimestamp();
    }

    // 计算目标时间在左右真实点之间的线性比例。
    private static double resolveSegmentRatio(long timestamp, long leftTimestamp, long rightTimestamp) {
        long span = Math.max(1L, rightTimestamp - leftTimestamp);
        double ratio = (double) (timestamp - leftTimestamp) / (double) span;
        return Math.max(0d, Math.min(1d, ratio));
    }

    // 按比例线性插值数值。
    private static double interpolateValue(double leftValue, double rightValue, double ratio) {
        return leftValue + (rightValue - leftValue) * ratio;
    }

    private static CurvePoint copyCurvePoint(CurvePoint point, long timestamp) {
        return new CurvePoint(timestamp, point.getEquity(), point.getBalance(), point.getPositionRatio());
    }

    private static DrawdownPoint copyDrawdownPoint(DrawdownPoint point, long timestamp) {
        return new DrawdownPoint(timestamp, point.getDrawdownRate());
    }

    private static DailyReturnPoint copyDailyReturnPoint(DailyReturnPoint point, long timestamp) {
        return new DailyReturnPoint(timestamp, point.getReturnRate());
    }
}

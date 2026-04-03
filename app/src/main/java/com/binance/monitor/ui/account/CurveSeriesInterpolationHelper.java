/*
 * 曲线序列插值辅助，负责按目标时间在净值/回撤/日收益序列中生成插值点。
 * 供账户曲线联动和主图长按弹窗复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.CurveAnalyticsHelper.DailyReturnPoint;
import com.binance.monitor.ui.account.CurveAnalyticsHelper.DrawdownPoint;
import com.binance.monitor.ui.account.model.CurvePoint;

import java.util.List;

public final class CurveSeriesInterpolationHelper {

    private CurveSeriesInterpolationHelper() {
    }

    // 按目标时间在线性区间内插值净值、结余和仓位比例。
    @Nullable
    public static CurvePoint interpolateCurvePoint(@Nullable List<CurvePoint> points, long timestamp) {
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
    public static DrawdownPoint interpolateDrawdownPoint(@Nullable List<DrawdownPoint> points, long timestamp) {
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
    public static DailyReturnPoint interpolateDailyReturnPoint(@Nullable List<DailyReturnPoint> points, long timestamp) {
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
}

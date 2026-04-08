/*
 * 曲线序列采样点辅助，负责把目标时间吸附到最近的真实采样点。
 * 不再线性插值生成不存在的数据点，避免长按联动继续造数。
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

    // 按目标时间吸附最近的真实净值点。
    @Nullable
    public static CurvePoint interpolateCurvePoint(@Nullable List<CurvePoint> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        CurvePoint nearest = points.get(0);
        long bestDistance = Math.abs(timestamp - nearest.getTimestamp());
        for (int i = 1; i < points.size(); i++) {
            CurvePoint candidate = points.get(i);
            long distance = Math.abs(timestamp - candidate.getTimestamp());
            if (distance < bestDistance) {
                nearest = candidate;
                bestDistance = distance;
            }
        }
        return new CurvePoint(nearest.getTimestamp(), nearest.getEquity(), nearest.getBalance(), nearest.getPositionRatio());
    }

    // 按目标时间吸附最近的真实回撤点。
    @Nullable
    public static DrawdownPoint interpolateDrawdownPoint(@Nullable List<DrawdownPoint> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        DrawdownPoint nearest = points.get(0);
        long bestDistance = Math.abs(timestamp - nearest.getTimestamp());
        for (int i = 1; i < points.size(); i++) {
            DrawdownPoint candidate = points.get(i);
            long distance = Math.abs(timestamp - candidate.getTimestamp());
            if (distance < bestDistance) {
                nearest = candidate;
                bestDistance = distance;
            }
        }
        return new DrawdownPoint(nearest.getTimestamp(), nearest.getDrawdownRate());
    }

    // 按目标时间吸附最近的真实日收益点。
    @Nullable
    public static DailyReturnPoint interpolateDailyReturnPoint(@Nullable List<DailyReturnPoint> points, long timestamp) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        DailyReturnPoint nearest = points.get(0);
        long bestDistance = Math.abs(timestamp - nearest.getTimestamp());
        for (int i = 1; i < points.size(); i++) {
            DailyReturnPoint candidate = points.get(i);
            long distance = Math.abs(timestamp - candidate.getTimestamp());
            if (distance < bestDistance) {
                nearest = candidate;
                bestDistance = distance;
            }
        }
        return new DailyReturnPoint(nearest.getTimestamp(), nearest.getReturnRate());
    }
}

/*
 * 账户区间收益率辅助，统一按“当期收益额 / 期初总资产”计算收益率。
 * 供账户统计收益表复用，避免按成交名义金额误算收益率。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;

import java.util.List;

final class AccountPeriodReturnHelper {

    private AccountPeriodReturnHelper() {
    }

    // 优先取区间起点之前最近一条结余，没有更早数据时回退到区间内第一条结余。
    static double resolvePeriodStartAsset(@Nullable List<CurvePoint> points, long periodStartMs) {
        if (points == null || points.isEmpty()) {
            return 0d;
        }
        CurvePoint latestBeforeStart = null;
        CurvePoint earliestAfterStart = null;
        for (CurvePoint point : points) {
            if (point == null) {
                continue;
            }
            long timestamp = point.getTimestamp();
            if (timestamp <= periodStartMs) {
                if (latestBeforeStart == null || timestamp > latestBeforeStart.getTimestamp()) {
                    latestBeforeStart = point;
                }
                continue;
            }
            if (earliestAfterStart == null || timestamp < earliestAfterStart.getTimestamp()) {
                earliestAfterStart = point;
            }
        }
        CurvePoint resolvedPoint = latestBeforeStart != null ? latestBeforeStart : earliestAfterStart;
        if (resolvedPoint == null) {
            return 0d;
        }
        return Math.max(0d, resolvedPoint.getBalance());
    }

    // 统一把区间收益额换算为收益率，避免不同表格各自写口径。
    static double resolvePeriodReturnRate(@Nullable List<CurvePoint> points,
                                          long periodStartMs,
                                          double returnAmount) {
        double startAsset = resolvePeriodStartAsset(points, periodStartMs);
        if (startAsset <= 0d) {
            return 0d;
        }
        return returnAmount / startAsset;
    }
}

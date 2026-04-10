/*
 * 账户概览累计指标辅助，负责只在“已有完整真值”时输出累计盈亏和累计收益率。
 * 优先使用净值曲线真值，其次使用历史成交 + 当前持仓；仅有当前持仓时不输出累计指标。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.List;

public final class AccountOverviewCumulativeMetricsCalculator {

    private AccountOverviewCumulativeMetricsCalculator() {
    }

    // 统一计算账户累计盈亏与累计收益率，返回账户页 overview 需要的两项真值。
    @NonNull
    public static OverviewCumulativeValues calculate(@Nullable List<TradeRecordItem> trades,
                                                     @Nullable List<PositionItem> currentPositions,
                                                     @Nullable List<CurvePoint> curvePoints) {
        double openPositionPnl = 0d;
        if (currentPositions != null) {
            for (PositionItem item : currentPositions) {
                if (item == null) {
                    continue;
                }
                openPositionPnl += item.getTotalPnL() + item.getStorageFee();
            }
        }

        CurveTruth curveTruth = resolveCurveTruth(curvePoints);
        if (curveTruth.hasTruth()) {
            return new OverviewCumulativeValues(
                    true,
                    curveTruth.cumulativePnl,
                    true,
                    curveTruth.returnRate
            );
        }
        if (trades != null && !trades.isEmpty()) {
            double realizedPnl = 0d;
            for (TradeRecordItem item : trades) {
                if (item == null) {
                    continue;
                }
                realizedPnl += item.getProfit() + item.getFee() + item.getStorageFee();
            }
            return new OverviewCumulativeValues(true, realizedPnl + openPositionPnl, false, 0d);
        }
        return new OverviewCumulativeValues(false, 0d, false, 0d);
    }

    // 曲线存在时，累计收益真值统一按“最新净值 - 期初结余”计算，避免被仅含当前持仓的轻快照覆盖。
    @NonNull
    private static CurveTruth resolveCurveTruth(@Nullable List<CurvePoint> curvePoints) {
        if (curvePoints == null || curvePoints.isEmpty()) {
            return CurveTruth.empty();
        }
        CurvePoint earliest = null;
        CurvePoint latest = null;
        for (CurvePoint point : curvePoints) {
            if (point == null || point.getTimestamp() <= 0L) {
                continue;
            }
            if (earliest == null || point.getTimestamp() < earliest.getTimestamp()) {
                earliest = point;
            }
            if (latest == null || point.getTimestamp() > latest.getTimestamp()) {
                latest = point;
            }
        }
        if (earliest == null || latest == null) {
            return CurveTruth.empty();
        }
        double startAsset = AccountPeriodReturnHelper.resolvePeriodStartAsset(curvePoints, earliest.getTimestamp());
        double latestEquity = Math.max(0d, latest.getEquity());
        if (startAsset <= 0d || latestEquity <= 0d) {
            return CurveTruth.empty();
        }
        double cumulativePnl = latestEquity - startAsset;
        return new CurveTruth(true, cumulativePnl, cumulativePnl / startAsset);
    }

    public static final class OverviewCumulativeValues {
        private final boolean hasCumulativePnlTruth;
        private final double cumulativePnl;
        private final boolean hasCumulativeReturnRateTruth;
        private final double cumulativeReturnRate;

        OverviewCumulativeValues(boolean hasCumulativePnlTruth,
                                 double cumulativePnl,
                                 boolean hasCumulativeReturnRateTruth,
                                 double cumulativeReturnRate) {
            this.hasCumulativePnlTruth = hasCumulativePnlTruth;
            this.cumulativePnl = cumulativePnl;
            this.hasCumulativeReturnRateTruth = hasCumulativeReturnRateTruth;
            this.cumulativeReturnRate = cumulativeReturnRate;
        }

        public boolean hasLocalTruth() {
            return hasCumulativePnlTruth || hasCumulativeReturnRateTruth;
        }

        public boolean hasCumulativePnlTruth() {
            return hasCumulativePnlTruth;
        }

        public double getCumulativePnl() {
            return cumulativePnl;
        }

        public boolean hasCumulativeReturnRateTruth() {
            return hasCumulativeReturnRateTruth;
        }

        public double getCumulativeReturnRate() {
            return cumulativeReturnRate;
        }
    }

    private static final class CurveTruth {
        private final boolean hasTruth;
        private final double cumulativePnl;
        private final double returnRate;

        private CurveTruth(boolean hasTruth, double cumulativePnl, double returnRate) {
            this.hasTruth = hasTruth;
            this.cumulativePnl = cumulativePnl;
            this.returnRate = returnRate;
        }

        private static CurveTruth empty() {
            return new CurveTruth(false, 0d, 0d);
        }

        private boolean hasTruth() {
            return hasTruth;
        }
    }
}

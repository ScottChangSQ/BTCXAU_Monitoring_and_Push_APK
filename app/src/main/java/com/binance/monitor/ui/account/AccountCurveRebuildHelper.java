/*
 * 账户曲线重建辅助，负责基于历史交易把结余修正为“仅平仓时变化”，并优先复用原始曲线里的浮动差值重建净值。
 * 供 AccountStatsBridgeActivity 在账户曲线口径异常时兜底使用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AccountCurveRebuildHelper {

    private static final double VALUE_EPSILON = 0.01d;

    private AccountCurveRebuildHelper() {
    }

    // 按历史交易重建曲线：结余只在平仓时变化，净值优先沿用真实浮盈亏，否则走近似模拟。
    static List<CurvePoint> rebuild(@Nullable List<CurvePoint> source,
                                    @Nullable List<TradeRecordItem> trades,
                                    double initialBalance) {
        List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(source, initialBalance);
        if (trades == null || trades.isEmpty() || normalized.isEmpty()) {
            return normalized;
        }

        List<TradeRecordItem> sortedTrades = new ArrayList<>();
        for (TradeRecordItem trade : trades) {
            if (trade != null) {
                sortedTrades.add(trade);
            }
        }
        if (sortedTrades.isEmpty()) {
            return normalized;
        }
        if (!shouldRebuild(normalized, sortedTrades)) {
            return normalized;
        }
        sortedTrades.sort(Comparator.comparingLong(AccountCurveRebuildHelper::resolveTradeSortTimestamp));

        long firstTimestamp = normalized.get(0).getTimestamp();
        double startingBalance = normalized.get(0).getBalance() > 0d
                ? normalized.get(0).getBalance()
                : initialBalance;
        List<CurvePoint> rebuilt = new ArrayList<>(normalized.size());
        for (int index = 0; index < normalized.size(); index++) {
            CurvePoint point = normalized.get(index);
            long timestamp = point.getTimestamp();
            double rebuiltBalance = startingBalance + sumClosedTradeDelta(sortedTrades, firstTimestamp, timestamp);
            boolean activeTrade = hasActiveTradeAt(sortedTrades, timestamp);
            double rebuiltEquity = rebuiltBalance;
            if (activeTrade) {
                double sourceFloating = resolveSourceFloating(point);
                double simulatedFloating = sumActiveFloatingDelta(sortedTrades, timestamp);
                double floatingDelta = shouldUseSourceFloating(sourceFloating, simulatedFloating, rebuiltBalance)
                        ? sourceFloating
                        : simulatedFloating;
                rebuiltEquity = rebuiltBalance + floatingDelta;
            }
            rebuilt.add(new CurvePoint(
                    timestamp,
                    rebuiltEquity,
                    rebuiltBalance,
                    point.getPositionRatio()
            ));
        }
        return rebuilt;
    }

    // 只有当源曲线明显不满足“结余平、净值浮”的基本口径时，才启动客户端兜底重建。
    private static boolean shouldRebuild(List<CurvePoint> source, List<TradeRecordItem> trades) {
        for (TradeRecordItem trade : trades) {
            long openTime = resolveOpenTime(trade);
            long closeTime = resolveCloseTime(trade);
            if (openTime <= 0L || closeTime <= openTime) {
                continue;
            }
            LifecycleCurveState state = inspectLifecycleCurveState(source, openTime, closeTime);
            if (!state.hasInteriorPoints) {
                continue;
            }
            if (state.balanceMovesDuringOpen) {
                return true;
            }
            if (Math.abs(resolveTradeDelta(trade)) > VALUE_EPSILON && !state.hasVisibleFloatingSpread) {
                return true;
            }
        }
        return hasMissingCloseBalanceJump(source, trades);
    }

    // 检查单笔持仓生命周期内，源曲线是否已经具备平滑净值和稳定结余。
    private static LifecycleCurveState inspectLifecycleCurveState(List<CurvePoint> source,
                                                                  long openTime,
                                                                  long closeTime) {
        boolean hasInteriorPoints = false;
        boolean hasVisibleFloatingSpread = false;
        boolean balanceMovesDuringOpen = false;
        double anchorBalance = 0d;
        boolean anchorInitialized = false;
        for (CurvePoint point : source) {
            long timestamp = point.getTimestamp();
            if (timestamp <= openTime || timestamp >= closeTime) {
                continue;
            }
            hasInteriorPoints = true;
            if (Math.abs(resolveSourceFloating(point)) > VALUE_EPSILON) {
                hasVisibleFloatingSpread = true;
            }
            double balance = point.getBalance();
            if (!anchorInitialized) {
                anchorBalance = balance;
                anchorInitialized = true;
                continue;
            }
            if (Math.abs(balance - anchorBalance) > VALUE_EPSILON) {
                balanceMovesDuringOpen = true;
            }
        }
        return new LifecycleCurveState(hasInteriorPoints, hasVisibleFloatingSpread, balanceMovesDuringOpen);
    }

    // 若某个平仓时刻的结余跳变与该时刻所有已实现盈亏之和不一致，说明源曲线仍是旧口径。
    private static boolean hasMissingCloseBalanceJump(List<CurvePoint> source, List<TradeRecordItem> trades) {
        Map<Long, Double> expectedDeltaByCloseTime = new LinkedHashMap<>();
        for (TradeRecordItem trade : trades) {
            long closeTime = resolveCloseTime(trade);
            long openTime = resolveOpenTime(trade);
            double tradeDelta = resolveTradeDelta(trade);
            if (closeTime <= 0L || closeTime <= openTime || Math.abs(tradeDelta) <= VALUE_EPSILON) {
                continue;
            }
            expectedDeltaByCloseTime.put(
                    closeTime,
                    expectedDeltaByCloseTime.getOrDefault(closeTime, 0d) + tradeDelta
            );
        }
        for (Map.Entry<Long, Double> entry : expectedDeltaByCloseTime.entrySet()) {
            CurvePoint beforeClosePoint = null;
            CurvePoint afterClosePoint = null;
            long closeTime = entry.getKey();
            for (CurvePoint point : source) {
                long timestamp = point.getTimestamp();
                if (timestamp < closeTime) {
                    beforeClosePoint = point;
                }
                if (afterClosePoint == null && timestamp >= closeTime) {
                    afterClosePoint = point;
                }
            }
            if (beforeClosePoint == null || afterClosePoint == null) {
                continue;
            }
            double actualDelta = afterClosePoint.getBalance() - beforeClosePoint.getBalance();
            if (Math.abs(actualDelta - entry.getValue()) > VALUE_EPSILON) {
                return true;
            }
        }
        return false;
    }

    // 汇总到当前时刻之前已平仓成交的已实现盈亏。
    private static double sumClosedTradeDelta(List<TradeRecordItem> trades, long firstTimestamp, long timestamp) {
        double delta = 0d;
        for (TradeRecordItem trade : trades) {
            long closeTime = resolveCloseTime(trade);
            if (closeTime <= firstTimestamp || closeTime > timestamp) {
                continue;
            }
            delta += resolveTradeDelta(trade);
        }
        return delta;
    }

    // 判断当前时间点是否仍处在任一持仓生命周期中。
    private static boolean hasActiveTradeAt(List<TradeRecordItem> trades, long timestamp) {
        for (TradeRecordItem trade : trades) {
            long openTime = resolveOpenTime(trade);
            long closeTime = resolveCloseTime(trade);
            if (openTime <= 0L || closeTime <= openTime) {
                continue;
            }
            if (timestamp > openTime && timestamp < closeTime) {
                return true;
            }
        }
        return false;
    }

    // 对仍在持仓期间的成交按时间进度线性模拟浮盈亏。
    private static double sumActiveFloatingDelta(List<TradeRecordItem> trades, long timestamp) {
        double delta = 0d;
        for (TradeRecordItem trade : trades) {
            long openTime = resolveOpenTime(trade);
            long closeTime = resolveCloseTime(trade);
            if (openTime <= 0L || closeTime <= openTime) {
                continue;
            }
            if (timestamp <= openTime || timestamp >= closeTime) {
                continue;
            }
            double progress = (double) (timestamp - openTime) / (double) (closeTime - openTime);
            delta += resolveTradeDelta(trade) * Math.max(0d, Math.min(1d, progress));
        }
        return delta;
    }

    // 优先复用原始曲线里“净值 - 结余”的浮动差值，避免把所有长周期都估成直线。
    private static double resolveSourceFloating(CurvePoint point) {
        if (point == null) {
            return 0d;
        }
        return point.getEquity() - point.getBalance();
    }

    // 仅在原始浮动值和交易生命周期估算大体一致时才复用，避免把脏尖刺直接带回净值曲线。
    private static boolean shouldUseSourceFloating(double sourceFloating,
                                                   double simulatedFloating,
                                                   double rebuiltBalance) {
        if (Math.abs(sourceFloating) <= VALUE_EPSILON) {
            return false;
        }
        if (Math.abs(simulatedFloating) > VALUE_EPSILON
                && Math.signum(sourceFloating) != Math.signum(simulatedFloating)) {
            return false;
        }
        if (Math.abs(simulatedFloating) > VALUE_EPSILON) {
            double maxFloatingGap = Math.max(Math.abs(simulatedFloating) * 2.5d, 50d);
            return Math.abs(sourceFloating - simulatedFloating) <= maxFloatingGap;
        }
        double maxReasonableFloating = Math.max(Math.abs(rebuiltBalance) * 0.15d, 50d);
        return Math.abs(sourceFloating) <= maxReasonableFloating;
    }

    // 统一解析单笔成交对应的已实现盈亏。
    private static double resolveTradeDelta(TradeRecordItem trade) {
        if (trade == null) {
            return 0d;
        }
        return trade.getProfit() + trade.getStorageFee();
    }

    // 统一解析开仓时间，缺失时回退到成交时间。
    private static long resolveOpenTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        return trade.getOpenTime() > 0L ? trade.getOpenTime() : trade.getTimestamp();
    }

    // 统一解析平仓时间，平仓成交缺 closeTime 时回退到成交时间。
    private static long resolveCloseTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        if (trade.getCloseTime() > 0L) {
            return trade.getCloseTime();
        }
        return trade.getTimestamp();
    }

    // 历史成交排序优先按平仓时间，其次按开仓时间。
    private static long resolveTradeSortTimestamp(TradeRecordItem trade) {
        long closeTime = resolveCloseTime(trade);
        return closeTime > 0L ? closeTime : resolveOpenTime(trade);
    }

    private static final class LifecycleCurveState {
        final boolean hasInteriorPoints;
        final boolean hasVisibleFloatingSpread;
        final boolean balanceMovesDuringOpen;

        private LifecycleCurveState(boolean hasInteriorPoints,
                                    boolean hasVisibleFloatingSpread,
                                    boolean balanceMovesDuringOpen) {
            this.hasInteriorPoints = hasInteriorPoints;
            this.hasVisibleFloatingSpread = hasVisibleFloatingSpread;
            this.balanceMovesDuringOpen = balanceMovesDuringOpen;
        }
    }
}

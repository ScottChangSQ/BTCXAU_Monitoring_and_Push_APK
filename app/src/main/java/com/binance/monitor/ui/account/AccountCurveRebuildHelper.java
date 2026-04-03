/*
 * 账户曲线重建辅助，负责基于历史交易把结余修正为“仅平仓时变化”，并在净值缺少有效浮盈亏差时补一条近似净值曲线。
 * 供 AccountStatsBridgeActivity 在账户曲线口径异常时兜底使用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        sortedTrades.sort(Comparator.comparingLong(AccountCurveRebuildHelper::resolveTradeSortTimestamp));

        boolean sourceHasMeaningfulFloating = hasMeaningfulFloating(normalized);
        long firstTimestamp = normalized.get(0).getTimestamp();
        double startingBalance = normalized.get(0).getBalance() > 0d
                ? normalized.get(0).getBalance()
                : initialBalance;
        List<CurvePoint> rebuilt = new ArrayList<>(normalized.size());
        for (CurvePoint point : normalized) {
            long timestamp = point.getTimestamp();
            double rebuiltBalance = startingBalance + sumClosedTradeDelta(sortedTrades, firstTimestamp, timestamp);
            double rebuiltEquity = sourceHasMeaningfulFloating
                    ? point.getEquity()
                    : rebuiltBalance + sumActiveFloatingDelta(sortedTrades, timestamp);
            rebuilt.add(new CurvePoint(
                    timestamp,
                    rebuiltEquity,
                    rebuiltBalance,
                    point.getPositionRatio()
            ));
        }
        return rebuilt;
    }

    // 判断原始曲线是否已经存在足够明显的浮盈亏差值。
    private static boolean hasMeaningfulFloating(List<CurvePoint> points) {
        int separatedCount = 0;
        for (CurvePoint point : points) {
            if (point != null && Math.abs(point.getEquity() - point.getBalance()) > VALUE_EPSILON) {
                separatedCount++;
            }
        }
        return separatedCount >= Math.max(2, points.size() / 20);
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
}

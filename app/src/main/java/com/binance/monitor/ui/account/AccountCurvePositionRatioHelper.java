/*
 * 账户曲线仓位比例辅助，负责在历史仓位缺失时优先用历史成交回放仓位，再回退到当前持仓估算。
 * 供 AccountStatsBridgeActivity 在仓位比例子图无数据时补出可展示的曲线。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AccountCurvePositionRatioHelper {

    private static final double RATIO_VISIBLE_THRESHOLD = 1e-6;

    private AccountCurvePositionRatioHelper() {
    }

    // 确保仓位比例图至少有可显示数据；若历史比率全缺失，则优先回放历史成交，再按当前持仓兜底。
    static List<CurvePoint> ensureVisibleRatios(@Nullable List<CurvePoint> source,
                                                @Nullable List<PositionItem> positions,
                                                @Nullable List<TradeRecordItem> trades) {
        List<CurvePoint> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        if (hasVisibleRatio(source)) {
            result.addAll(source);
            return result;
        }
        List<CurvePoint> replayedPoints = rebuildRatiosFromTrades(source, trades);
        if (hasVisibleRatio(replayedPoints)) {
            return replayedPoints;
        }
        double totalMarketValue = resolveCurrentMarketValue(positions);
        if (totalMarketValue <= RATIO_VISIBLE_THRESHOLD) {
            result.addAll(source);
            return result;
        }
        for (CurvePoint point : source) {
            double equityBase = Math.max(1d, Math.abs(point.getEquity()));
            double estimatedRatio = Math.max(0d, totalMarketValue / equityBase);
            result.add(new CurvePoint(
                    point.getTimestamp(),
                    point.getEquity(),
                    point.getBalance(),
                    estimatedRatio
            ));
        }
        return result;
    }

    // 用历史成交回放每个时间点的持仓暴露，补出已经平仓阶段的历史仓位比例。
    private static List<CurvePoint> rebuildRatiosFromTrades(List<CurvePoint> source,
                                                            @Nullable List<TradeRecordItem> trades) {
        List<CurvePoint> sortedPoints = new ArrayList<>(source);
        sortedPoints.sort(Comparator.comparingLong(CurvePoint::getTimestamp));
        if (trades == null || trades.isEmpty()) {
            return sortedPoints;
        }
        Map<Long, Double> exposureEvents = new HashMap<>();
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            long openTime = resolveOpenTime(trade);
            if (openTime <= 0L) {
                continue;
            }
            double exposure = resolveTradeExposure(trade);
            if (exposure <= RATIO_VISIBLE_THRESHOLD) {
                continue;
            }
            accumulateExposure(exposureEvents, openTime, exposure);
            long closeTime = resolveCloseTime(trade);
            if (closeTime > openTime) {
                accumulateExposure(exposureEvents, closeTime, -exposure);
            }
        }
        if (exposureEvents.isEmpty()) {
            return sortedPoints;
        }
        List<Long> eventTimes = new ArrayList<>(exposureEvents.keySet());
        Collections.sort(eventTimes);
        List<CurvePoint> resolved = new ArrayList<>(sortedPoints.size());
        double activeExposure = 0d;
        int eventIndex = 0;
        for (CurvePoint point : sortedPoints) {
            while (eventIndex < eventTimes.size() && eventTimes.get(eventIndex) <= point.getTimestamp()) {
                activeExposure += exposureEvents.get(eventTimes.get(eventIndex));
                eventIndex++;
            }
            double equityBase = Math.max(1d, Math.abs(point.getEquity()));
            double estimatedRatio = Math.max(0d, activeExposure / equityBase);
            resolved.add(new CurvePoint(
                    point.getTimestamp(),
                    point.getEquity(),
                    point.getBalance(),
                    estimatedRatio
            ));
        }
        return resolved;
    }

    // 判断历史曲线里是否已经存在有效仓位比例。
    private static boolean hasVisibleRatio(List<CurvePoint> source) {
        for (CurvePoint point : source) {
            if (point != null && Math.abs(point.getPositionRatio()) > RATIO_VISIBLE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    // 汇总当前持仓市值，作为历史仓位缺失时的保守估算输入。
    private static double resolveCurrentMarketValue(@Nullable List<PositionItem> positions) {
        if (positions == null || positions.isEmpty()) {
            return 0d;
        }
        double total = 0d;
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            total += Math.max(0d, Math.abs(item.getMarketValue()));
        }
        return total;
    }

    // 按时间累计开平仓带来的暴露变化。
    private static void accumulateExposure(Map<Long, Double> exposureEvents, long timestamp, double delta) {
        if (timestamp <= 0L || Math.abs(delta) <= RATIO_VISIBLE_THRESHOLD) {
            return;
        }
        double current = exposureEvents.containsKey(timestamp) ? exposureEvents.get(timestamp) : 0d;
        exposureEvents.put(timestamp, current + delta);
    }

    // 计算单笔成交在持仓期间贡献的名义暴露，优先取成交额，不足时退回开仓价乘数量。
    private static double resolveTradeExposure(TradeRecordItem trade) {
        double amount = Math.abs(trade.getAmount());
        if (amount > RATIO_VISIBLE_THRESHOLD) {
            return amount;
        }
        double price = trade.getOpenPrice() > 0d ? trade.getOpenPrice() : trade.getPrice();
        return Math.abs(trade.getQuantity()) * Math.max(0d, price);
    }

    // 统一解析开仓时间，旧数据没有开仓时间时退回记录时间。
    private static long resolveOpenTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        return trade.getOpenTime() > 0L ? trade.getOpenTime() : trade.getTimestamp();
    }

    // 统一解析平仓时间，只接受晚于开仓时间的记录，避免把未平仓单误算成全程持仓。
    private static long resolveCloseTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        long openTime = resolveOpenTime(trade);
        long closeTime = trade.getCloseTime();
        return closeTime > openTime ? closeTime : 0L;
    }
}

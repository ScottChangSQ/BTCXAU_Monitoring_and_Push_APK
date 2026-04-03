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
                                                @Nullable List<TradeRecordItem> trades,
                                                double leverage) {
        List<CurvePoint> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        double effectiveLeverage = resolveEffectiveLeverage(leverage);
        double totalMargin = resolveCurrentMargin(positions, effectiveLeverage);
        if (hasVisibleRatio(source)) {
            if (totalMargin <= RATIO_VISIBLE_THRESHOLD
                    && !hasOpenExposureTrade(trades)
                    && hasVisibleRatioAtTail(source)) {
                return clearTailRatios(source);
            }
            result.addAll(source);
            return result;
        }
        List<CurvePoint> replayedPoints = rebuildRatiosFromTrades(source, trades, effectiveLeverage);
        if (hasVisibleRatio(replayedPoints)) {
            return replayedPoints;
        }
        if (totalMargin <= RATIO_VISIBLE_THRESHOLD) {
            result.addAll(source);
            return result;
        }
        for (CurvePoint point : source) {
            double equityBase = Math.max(1d, Math.abs(point.getEquity()));
            double estimatedRatio = Math.max(0d, totalMargin / equityBase);
            result.add(new CurvePoint(
                    point.getTimestamp(),
                    point.getEquity(),
                    point.getBalance(),
                    estimatedRatio
            ));
        }
        return result;
    }

    // 用历史成交回放每个时间点的持仓保证金，补出已经平仓阶段的历史仓位比例。
    private static List<CurvePoint> rebuildRatiosFromTrades(List<CurvePoint> source,
                                                            @Nullable List<TradeRecordItem> trades,
                                                            double leverage) {
        List<CurvePoint> sortedPoints = new ArrayList<>(source);
        sortedPoints.sort(Comparator.comparingLong(CurvePoint::getTimestamp));
        Map<Long, Double> marginEvents = buildMarginEvents(trades, leverage);
        if (marginEvents.isEmpty()) {
            return sortedPoints;
        }
        List<Long> eventTimes = new ArrayList<>(marginEvents.keySet());
        Collections.sort(eventTimes);
        List<CurvePoint> resolved = new ArrayList<>(sortedPoints.size());
        double activeMargin = 0d;
        int eventIndex = 0;
        for (CurvePoint point : sortedPoints) {
            while (eventIndex < eventTimes.size() && eventTimes.get(eventIndex) <= point.getTimestamp()) {
                activeMargin = Math.max(0d, activeMargin + marginEvents.get(eventTimes.get(eventIndex)));
                eventIndex++;
            }
            double equityBase = Math.max(1d, Math.abs(point.getEquity()));
            double estimatedRatio = Math.max(0d, activeMargin / equityBase);
            resolved.add(new CurvePoint(
                    point.getTimestamp(),
                    point.getEquity(),
                    point.getBalance(),
                    estimatedRatio
            ));
        }
        return resolved;
    }

    // 当前已空仓但尾部仍残留旧比率时，只清掉最新连续尾段，保留更早的真实历史。
    private static List<CurvePoint> clearTailRatios(List<CurvePoint> source) {
        List<CurvePoint> resolved = new ArrayList<>(source.size());
        boolean clearingTail = true;
        for (int index = source.size() - 1; index >= 0; index--) {
            CurvePoint point = source.get(index);
            double ratio = point.getPositionRatio();
            if (clearingTail && Math.abs(ratio) <= RATIO_VISIBLE_THRESHOLD) {
                clearingTail = false;
            }
            if (clearingTail) {
                ratio = 0d;
            }
            resolved.add(0, new CurvePoint(
                    point.getTimestamp(),
                    point.getEquity(),
                    point.getBalance(),
                    ratio
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

    // 判断当前区间末尾是否仍显示非零仓位比例。
    private static boolean hasVisibleRatioAtTail(List<CurvePoint> source) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        for (int index = source.size() - 1; index >= 0; index--) {
            CurvePoint point = source.get(index);
            if (point == null) {
                continue;
            }
            if (Math.abs(point.getPositionRatio()) > RATIO_VISIBLE_THRESHOLD) {
                return true;
            }
            return false;
        }
        return false;
    }

    // 历史成交里只要仍有未平仓暴露，就不能把尾部仓位比例直接清零。
    private static boolean hasOpenExposureTrade(@Nullable List<TradeRecordItem> trades) {
        Map<Long, Double> marginEvents = buildMarginEvents(trades, 1d);
        if (marginEvents.isEmpty()) {
            return false;
        }
        List<Long> eventTimes = new ArrayList<>(marginEvents.keySet());
        Collections.sort(eventTimes);
        double activeMargin = 0d;
        for (Long eventTime : eventTimes) {
            activeMargin = Math.max(0d, activeMargin + marginEvents.get(eventTime));
        }
        return activeMargin > RATIO_VISIBLE_THRESHOLD;
    }

    // 汇总当前持仓保证金，作为历史仓位缺失时的保守估算输入。
    private static double resolveCurrentMargin(@Nullable List<PositionItem> positions, double leverage) {
        if (positions == null || positions.isEmpty()) {
            return 0d;
        }
        double total = 0d;
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            total += Math.max(0d, Math.abs(item.getMarketValue())) / leverage;
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

    // 把历史成交转换成按时间排序的仓位暴露增减事件，兼容开仓单、平仓单和已聚合生命周期记录。
    private static Map<Long, Double> buildMarginEvents(@Nullable List<TradeRecordItem> trades, double leverage) {
        Map<Long, Double> marginEvents = new HashMap<>();
        if (trades == null || trades.isEmpty()) {
            return marginEvents;
        }
        Map<String, Integer> openingDealCounts = new HashMap<>();
        for (TradeRecordItem trade : trades) {
            if (trade == null || isClosingEntryType(trade.getEntryType())) {
                continue;
            }
            String key = buildExposureKey(trade);
            if (key.isEmpty()) {
                continue;
            }
            openingDealCounts.put(key, openingDealCounts.getOrDefault(key, 0) + 1);
        }
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            double margin = resolveTradeMargin(trade, leverage);
            if (margin <= RATIO_VISIBLE_THRESHOLD) {
                continue;
            }
            long openTime = resolveOpenTime(trade);
            long closeTime = resolveCloseTime(trade);
            if (isClosingEntryType(trade.getEntryType())) {
                if (shouldTreatAsLifecycleRecord(trade, openingDealCounts)) {
                    if (openTime > 0L) {
                        accumulateExposure(marginEvents, openTime, margin);
                    }
                    long eventTime = closeTime > 0L ? closeTime : Math.max(openTime, trade.getTimestamp());
                    accumulateExposure(marginEvents, eventTime, -margin);
                } else {
                    long eventTime = closeTime > 0L ? closeTime : Math.max(openTime, trade.getTimestamp());
                    accumulateExposure(marginEvents, eventTime, -margin);
                }
                continue;
            }
            if (openTime <= 0L) {
                continue;
            }
            accumulateExposure(marginEvents, openTime, margin);
            if (closeTime > openTime) {
                accumulateExposure(marginEvents, closeTime, -margin);
            }
        }
        return marginEvents;
    }

    // 判断平仓记录是否其实是一条自带开平时间的生命周期汇总记录。
    private static boolean shouldTreatAsLifecycleRecord(TradeRecordItem trade,
                                                        Map<String, Integer> openingDealCounts) {
        if (trade == null) {
            return false;
        }
        long openTime = resolveOpenTime(trade);
        long closeTime = resolveCloseTime(trade);
        if (openTime <= 0L || closeTime <= openTime) {
            return false;
        }
        String key = buildExposureKey(trade);
        return key.isEmpty() || openingDealCounts.getOrDefault(key, 0) <= 0;
    }

    // 尽量用持仓维度拼出同一笔暴露的键，优先命中 positionId。
    private static String buildExposureKey(TradeRecordItem trade) {
        if (trade == null) {
            return "";
        }
        if (trade.getPositionId() > 0L) {
            return "position|" + trade.getPositionId();
        }
        if (trade.getOrderId() > 0L) {
            return "order|" + trade.getOrderId();
        }
        String code = trade.getCode() == null ? "" : trade.getCode().trim();
        long openTime = resolveOpenTime(trade);
        if (!code.isEmpty() && openTime > 0L) {
            return "fallback|" + code + "|" + openTime;
        }
        return "";
    }

    // 计算单笔成交在持仓期间贡献的保证金，优先按成交额 / 杠杆估算。
    private static double resolveTradeMargin(TradeRecordItem trade, double leverage) {
        double amount = Math.abs(trade.getAmount());
        if (amount > RATIO_VISIBLE_THRESHOLD) {
            return amount / leverage;
        }
        double price = trade.getOpenPrice() > 0d ? trade.getOpenPrice() : trade.getPrice();
        return Math.abs(trade.getQuantity()) * Math.max(0d, price) / leverage;
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
        if (closeTime > openTime) {
            return closeTime;
        }
        long timestamp = trade.getTimestamp();
        if (isClosingEntryType(trade.getEntryType()) && timestamp >= openTime) {
            return timestamp;
        }
        return 0L;
    }

    // 统一保证杠杆至少为 1，避免缺少杠杆配置时出现除零。
    private static double resolveEffectiveLeverage(double leverage) {
        return leverage > 1d ? leverage : 1d;
    }

    // MT5 的平仓/反手成交缺 closeTime 时，回退到成交时间作为平仓时刻。
    private static boolean isClosingEntryType(int entryType) {
        return entryType == 1 || entryType == 2 || entryType == 3;
    }
}

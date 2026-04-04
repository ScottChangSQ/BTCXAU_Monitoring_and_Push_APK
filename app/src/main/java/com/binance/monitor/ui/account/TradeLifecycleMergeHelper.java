/*
 * 历史交易生命周期归一化辅助，负责把开仓/平仓拆分记录整理成可直接用于统计和图表的闭合成交。
 * 供账户统计页和 K 线历史成交叠加层共用，避免两边各自维护一套口径。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TradeLifecycleMergeHelper {

    private static final double DEFAULT_ZERO_PROFIT_THRESHOLD = 0.01d;

    private TradeLifecycleMergeHelper() {
    }

    // 按默认零盈亏阈值归一化历史交易生命周期。
    @NonNull
    public static List<TradeRecordItem> merge(@Nullable List<TradeRecordItem> source) {
        return merge(source, DEFAULT_ZERO_PROFIT_THRESHOLD);
    }

    // 把开仓/平仓拆分记录合并或补齐为闭合生命周期，输出给统计页和图表页直接使用。
    @NonNull
    public static List<TradeRecordItem> merge(@Nullable List<TradeRecordItem> source, double zeroProfitThreshold) {
        List<TradeRecordItem> merged = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return merged;
        }

        Set<String> dedupeSet = new LinkedHashSet<>();
        Map<String, List<TradeRecordItem>> grouped = new LinkedHashMap<>();
        for (TradeRecordItem item : source) {
            if (item == null) {
                continue;
            }
            String dedupeKey = buildTradeDedupeKey(item);
            if (!dedupeSet.add(dedupeKey)) {
                continue;
            }
            String groupKey = buildTradeGroupKey(item);
            grouped.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(item);
        }

        for (List<TradeRecordItem> group : grouped.values()) {
            merged.addAll(mergeGroup(group, zeroProfitThreshold));
        }
        merged.sort((left, right) -> Long.compare(resolveCloseTime(right), resolveCloseTime(left)));
        return merged;
    }

    // 按同一生命周期分组处理，单平仓记录走聚合，多平仓记录仅补齐缺失的开仓链路。
    @NonNull
    private static List<TradeRecordItem> mergeGroup(@Nullable List<TradeRecordItem> group, double zeroProfitThreshold) {
        List<TradeRecordItem> result = new ArrayList<>();
        if (group == null || group.isEmpty()) {
            return result;
        }
        List<TradeRecordItem> ordered = new ArrayList<>(group);
        ordered.sort(Comparator.comparingLong(TradeLifecycleMergeHelper::resolveCloseTime));
        LifecycleContext context = buildLifecycleContext(ordered);
        List<TradeRecordItem> closeRecords = new ArrayList<>();
        for (TradeRecordItem item : ordered) {
            if (isCloseLikeRecord(item, zeroProfitThreshold)) {
                closeRecords.add(item);
            }
        }
        if (closeRecords.isEmpty()) {
            return result;
        }
        if (closeRecords.size() == 1) {
            result.add(buildMergedTrade(closeRecords.get(0), ordered, context));
            return result;
        }
        for (TradeRecordItem closeRecord : closeRecords) {
            result.add(enrichCloseTrade(closeRecord, context));
        }
        return result;
    }

    // 单个平仓记录对应多个开仓明细时，聚合整组费用、备注和生命周期边界。
    @NonNull
    private static TradeRecordItem buildMergedTrade(TradeRecordItem closeRecord,
                                                    List<TradeRecordItem> group,
                                                    LifecycleContext context) {
        double feeSum = 0d;
        double storageFeeSum = 0d;
        double maxQuantity = Math.max(0d, closeRecord.getQuantity());
        double maxAmount = Math.max(0d, closeRecord.getAmount());
        for (TradeRecordItem item : group) {
            if (item == null) {
                continue;
            }
            feeSum += item.getFee();
            storageFeeSum += item.getStorageFee();
            maxQuantity = Math.max(maxQuantity, Math.abs(item.getQuantity()));
            maxAmount = Math.max(maxAmount, Math.abs(item.getAmount()));
        }
        return new TradeRecordItem(
                context.closeTime > 0L ? context.closeTime : resolveCloseTime(closeRecord),
                closeRecord.getProductName(),
                closeRecord.getCode(),
                context.side.isEmpty() ? closeRecord.getSide() : context.side,
                closeRecord.getPrice(),
                maxQuantity > 0d ? maxQuantity : closeRecord.getQuantity(),
                maxAmount > 0d ? maxAmount : closeRecord.getAmount(),
                feeSum,
                collectMergedRemarks(group, closeRecord.getRemark()),
                closeRecord.getProfit(),
                context.openTime,
                context.closeTime > 0L ? context.closeTime : resolveCloseTime(closeRecord),
                storageFeeSum,
                context.openPrice > 0d ? context.openPrice : resolveOpenPrice(closeRecord),
                context.closePrice > 0d ? context.closePrice : resolveClosePrice(closeRecord),
                closeRecord.getDealTicket(),
                closeRecord.getOrderId(),
                closeRecord.getPositionId(),
                closeRecord.getEntryType()
        );
    }

    // 多次分批平仓时，只补齐缺失的开仓时间和开仓价，保留各自的平仓结果。
    @NonNull
    private static TradeRecordItem enrichCloseTrade(TradeRecordItem closeRecord, LifecycleContext context) {
        long openTime = resolveOpenTime(closeRecord);
        long closeTime = resolveCloseTime(closeRecord);
        boolean invalidLifecycle = openTime <= 0L || closeTime <= 0L || openTime >= closeTime;
        double openPrice = resolveOpenPrice(closeRecord);
        if (invalidLifecycle && context.openTime > 0L && context.openTime < closeTime) {
            openTime = context.openTime;
        }
        if ((openPrice <= 0d || invalidLifecycle) && context.openPrice > 0d) {
            openPrice = context.openPrice;
        }
        String side = normalizeSide(closeRecord.getSide());
        if (side.isEmpty()) {
            side = context.side;
        }
        return new TradeRecordItem(
                closeTime > 0L ? closeTime : closeRecord.getTimestamp(),
                closeRecord.getProductName(),
                closeRecord.getCode(),
                side.isEmpty() ? closeRecord.getSide() : side,
                closeRecord.getPrice(),
                closeRecord.getQuantity(),
                closeRecord.getAmount(),
                closeRecord.getFee(),
                closeRecord.getRemark(),
                closeRecord.getProfit(),
                openTime,
                closeTime,
                closeRecord.getStorageFee(),
                openPrice,
                resolveClosePrice(closeRecord),
                closeRecord.getDealTicket(),
                closeRecord.getOrderId(),
                closeRecord.getPositionId(),
                closeRecord.getEntryType()
        );
    }

    // 汇总当前生命周期里最早开仓、最晚平仓以及可复用的价格/方向信息。
    @NonNull
    private static LifecycleContext buildLifecycleContext(List<TradeRecordItem> group) {
        long minOpenTime = Long.MAX_VALUE;
        long maxCloseTime = 0L;
        double openPrice = 0d;
        double closePrice = 0d;
        String side = "";
        for (TradeRecordItem item : group) {
            if (item == null) {
                continue;
            }
            long openTime = resolveOpenTime(item);
            long closeTime = resolveCloseTime(item);
            double candidateOpenPrice = resolveOpenPrice(item);
            double candidateClosePrice = resolveClosePrice(item);
            if (openTime > 0L && openTime < minOpenTime) {
                minOpenTime = openTime;
                openPrice = candidateOpenPrice;
            }
            if (closeTime > 0L && closeTime >= maxCloseTime) {
                maxCloseTime = closeTime;
                closePrice = candidateClosePrice;
            }
            if (side.isEmpty() && !normalizeSide(item.getSide()).isEmpty()) {
                side = normalizeSide(item.getSide());
            }
        }
        if (minOpenTime == Long.MAX_VALUE) {
            minOpenTime = 0L;
        }
        return new LifecycleContext(minOpenTime, maxCloseTime, openPrice, closePrice, side);
    }

    // 识别“已闭合交易”记录：优先看 entryType，再用时间跨度和盈亏兜底。
    private static boolean isCloseLikeRecord(@Nullable TradeRecordItem item, double zeroProfitThreshold) {
        if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
            return false;
        }
        int entryType = item.getEntryType();
        if (entryType == 1 || entryType == 2 || entryType == 3) {
            return true;
        }
        long openTime = resolveOpenTime(item);
        long closeTime = resolveCloseTime(item);
        if (closeTime > openTime) {
            return true;
        }
        return Math.abs(item.getProfit()) >= zeroProfitThreshold;
    }

    // 统一构建去重键，避免同一笔历史成交在快照累计过程中重复进入。
    @NonNull
    private static String buildTradeDedupeKey(TradeRecordItem item) {
        if (item.getDealTicket() > 0L) {
            return "deal|" + item.getDealTicket();
        }
        if (item.getOrderId() > 0L || item.getPositionId() > 0L) {
            return "trade|" + item.getOrderId() + "|" + item.getPositionId()
                    + "|" + item.getEntryType()
                    + "|" + resolveOpenTime(item)
                    + "|" + resolveCloseTime(item)
                    + "|" + Math.round(Math.abs(item.getQuantity()) * 10_000d);
        }
        String code = resolveCode(item);
        long open = resolveOpenTime(item);
        long close = resolveCloseTime(item);
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long price = Math.round(Math.abs(item.getPrice()) * 100d);
        long profit = Math.round(item.getProfit() * 100d);
        String side = normalizeSide(item.getSide()).toLowerCase(Locale.ROOT);
        return code + "|" + side + "|" + open + "|" + close + "|" + qty + "|" + price + "|" + profit;
    }

    // 优先按 position/order 分组，只有旧数据缺少标识时才回退到时间与价格近似键。
    @NonNull
    private static String buildTradeGroupKey(TradeRecordItem item) {
        String code = resolveCode(item);
        if (item.getPositionId() > 0L || item.getOrderId() > 0L) {
            return code + "|" + item.getOrderId() + "|" + item.getPositionId();
        }
        long openBucket = resolveOpenTime(item) / 60_000L;
        long closeBucket = resolveCloseTime(item) / 60_000L;
        long quantityKey = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long openPriceKey = Math.round(Math.abs(resolveOpenPrice(item)) * 100d);
        long closePriceKey = Math.round(Math.abs(resolveClosePrice(item)) * 100d);
        String side = normalizeSide(item.getSide()).toLowerCase(Locale.ROOT);
        return code + "|" + side + "|" + openBucket + "|" + closeBucket + "|"
                + quantityKey + "|" + openPriceKey + "|" + closePriceKey;
    }

    // 合并备注，避免多条原始记录拆散后丢失上游说明。
    @NonNull
    private static String collectMergedRemarks(List<TradeRecordItem> group, @Nullable String fallback) {
        StringBuilder builder = new StringBuilder();
        for (TradeRecordItem item : group) {
            if (item == null) {
                continue;
            }
            String remark = trim(item.getRemark());
            if (remark.isEmpty()) {
                continue;
            }
            String current = builder.toString();
            if (current.contains(remark)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(remark);
        }
        if (builder.length() > 0) {
            return builder.toString();
        }
        return fallback == null ? "" : fallback;
    }

    // 统一解析开仓时间，缺失时退回成交时间。
    private static long resolveOpenTime(@Nullable TradeRecordItem item) {
        if (item == null) {
            return 0L;
        }
        long openTime = normalizePossibleEpochMs(item.getOpenTime());
        if (openTime > 0L) {
            return openTime;
        }
        return normalizePossibleEpochMs(item.getTimestamp());
    }

    // 统一解析平仓时间，缺失时退回成交时间。
    private static long resolveCloseTime(@Nullable TradeRecordItem item) {
        if (item == null) {
            return 0L;
        }
        long closeTime = normalizePossibleEpochMs(item.getCloseTime());
        if (closeTime > 0L) {
            return closeTime;
        }
        return normalizePossibleEpochMs(item.getTimestamp());
    }

    // 统一解析开仓价，缺失时回退到成交价。
    private static double resolveOpenPrice(@Nullable TradeRecordItem item) {
        if (item == null) {
            return 0d;
        }
        if (item.getOpenPrice() > 0d) {
            return item.getOpenPrice();
        }
        return item.getPrice();
    }

    // 统一解析平仓价，缺失时回退到成交价。
    private static double resolveClosePrice(@Nullable TradeRecordItem item) {
        if (item == null) {
            return 0d;
        }
        if (item.getClosePrice() > 0d) {
            return item.getClosePrice();
        }
        return item.getPrice();
    }

    @NonNull
    private static String resolveCode(@Nullable TradeRecordItem item) {
        if (item == null) {
            return "";
        }
        String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
        if (!code.isEmpty()) {
            return code;
        }
        return trim(item.getProductName()).toUpperCase(Locale.ROOT);
    }

    @NonNull
    private static String normalizeSide(@Nullable String side) {
        String normalized = trim(side).toLowerCase(Locale.ROOT);
        if (normalized.contains("sell") || normalized.contains("short") || normalized.contains("卖")) {
            return "Sell";
        }
        if (normalized.contains("buy") || normalized.contains("long") || normalized.contains("买")) {
            return "Buy";
        }
        return "";
    }

    @NonNull
    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    // 历史缓存里可能保留旧的秒级时间戳，这里统一修正，避免后续生命周期分组与统计落到错误时间轴。
    private static long normalizePossibleEpochMs(long value) {
        if (value >= 1_000_000_000L && value < 10_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }

    private static final class LifecycleContext {
        final long openTime;
        final long closeTime;
        final double openPrice;
        final double closePrice;
        final String side;

        private LifecycleContext(long openTime, long closeTime, double openPrice, double closePrice, String side) {
            this.openTime = openTime;
            this.closeTime = closeTime;
            this.openPrice = openPrice;
            this.closePrice = closePrice;
            this.side = side == null ? "" : side;
        }
    }
}

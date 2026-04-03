/*
 * 历史成交图表标记构建器，负责把已平仓历史成交映射成开仓点、平仓点和连线所需的时间价格信息。
 * 行情图页通过这里把账户成交记录转换成图表叠加层输入，避免在 Activity 里堆积映射细节。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class HistoricalTradeAnnotationBuilder {
    private static final int BUY_COLOR = 0xFF16C784;
    private static final int SELL_COLOR = 0xFFF6465D;

    private HistoricalTradeAnnotationBuilder() {
    }

    // 只把当前产品、已平仓且落在可见 K 线窗口内的成交转换成开平仓标记。
    static List<TradeAnnotation> build(@Nullable String selectedSymbol,
                                       @Nullable List<TradeRecordItem> trades,
                                       @Nullable List<CandleEntry> candles) {
        List<TradeAnnotation> result = new ArrayList<>();
        if (selectedSymbol == null || selectedSymbol.trim().isEmpty()
                || trades == null || trades.isEmpty()
                || candles == null || candles.isEmpty()) {
            return result;
        }
        for (TradeRecordItem trade : trades) {
            if (!isClosedTrade(trade) || !matchesSelectedSymbol(selectedSymbol, trade)) {
                continue;
            }
            long closeTime = resolveCloseTime(trade);
            long exitAnchorTime = resolveAnchorTime(candles, closeTime, false);
            if (exitAnchorTime <= 0L) {
                continue;
            }
            long openTime = resolveOpenTime(trade);
            long entryAnchorTime = resolveAnchorTime(candles, openTime, false);
            double openPrice = resolveOpenPrice(trade);
            double closePrice = resolveClosePrice(trade);
            if (entryAnchorTime <= 0L || openPrice <= 0d || closePrice <= 0d) {
                continue;
            }
            result.add(new TradeAnnotation(
                    entryAnchorTime,
                    openPrice,
                    exitAnchorTime,
                    closePrice,
                    normalizeTradeSide(trade.getSide()),
                    Math.abs(trade.getQuantity()),
                    trade.getProfit() + trade.getStorageFee(),
                    resolveProductName(trade),
                    resolveCode(trade),
                    openTime,
                    closeTime,
                    trade.getOrderId(),
                    trade.getPositionId(),
                    buildGroupId(trade, exitAnchorTime, closePrice)
            ));
        }
        result.sort(Comparator
                .comparingLong((TradeAnnotation item) -> item.exitAnchorTimeMs)
                .thenComparingDouble(item -> item.exitPrice));
        return result;
    }

    // 仅展示已经结束的历史成交，优先按 MT5 entryType 识别平仓/反手成交，旧数据再用时间字段兜底。
    private static boolean isClosedTrade(@Nullable TradeRecordItem trade) {
        if (trade == null) {
            return false;
        }
        long closeTime = resolveCloseTime(trade);
        long openTime = resolveOpenTime(trade);
        if (Math.abs(trade.getQuantity()) <= 1e-9 || closeTime <= 0L || closeTime < openTime) {
            return false;
        }
        int entryType = trade.getEntryType();
        if (entryType == 1 || entryType == 2 || entryType == 3) {
            return true;
        }
        if (entryType == 0) {
            // 开仓成交不画到历史成交层；旧快照若缺少 entryType，但 open/close 时间明显不同，仍保留。
            return closeTime > openTime;
        }
        return closeTime >= openTime;
    }

    // 只校验成交时间是否落在当前图表窗口允许的时间范围内；一旦可见，就保留真实时间。
    // 这样长周期图上的成交点会落在该周期内部的真实相对位置，而不是被压到整根 K 线开头。
    private static long resolveAnchorTime(List<CandleEntry> candles, long targetTime, boolean clampToWindowStart) {
        if (candles == null || candles.isEmpty() || targetTime <= 0L) {
            return 0L;
        }
        long firstOpen = candles.get(0).getOpenTime();
        long lastOpen = candles.get(candles.size() - 1).getOpenTime();
        long intervalMs = estimateIntervalMs(candles);
        long lastVisibleTime = resolveLastVisibleTime(candles, lastOpen, intervalMs);
        if (targetTime < firstOpen) {
            return clampToWindowStart ? firstOpen : targetTime;
        }
        if (targetTime > lastVisibleTime) {
            return 0L;
        }
        for (int i = 0; i < candles.size(); i++) {
            CandleEntry candle = candles.get(i);
            if (candle == null) {
                continue;
            }
            long openTime = candle.getOpenTime();
            long candleClose = candle.getCloseTime() > openTime ? candle.getCloseTime() : openTime + intervalMs;
            boolean isLast = i == candles.size() - 1;
            boolean inBucket = targetTime >= openTime
                    && (targetTime < candleClose || (isLast && targetTime <= candleClose));
            if (inBucket) {
                return targetTime;
            }
        }
        return targetTime;
    }

    private static long resolveLastVisibleTime(List<CandleEntry> candles, long lastOpen, long intervalMs) {
        if (candles == null || candles.isEmpty()) {
            return Math.max(0L, lastOpen + intervalMs);
        }
        CandleEntry lastCandle = candles.get(candles.size() - 1);
        long lastClose = lastCandle == null ? 0L : lastCandle.getCloseTime();
        long fallbackClose = lastOpen + intervalMs;
        return Math.max(fallbackClose, lastClose);
    }

    private static long estimateIntervalMs(List<CandleEntry> candles) {
        if (candles == null || candles.size() < 2) {
            return 60_000L;
        }
        long first = candles.get(0).getOpenTime();
        long second = candles.get(1).getOpenTime();
        return Math.max(1L, second - first);
    }

    private static boolean matchesSelectedSymbol(String selectedSymbol, @Nullable TradeRecordItem trade) {
        if (trade == null) {
            return false;
        }
        String normalizedSelected = normalizeSymbol(selectedSymbol);
        if (normalizedSelected.isEmpty()) {
            return false;
        }
        return normalizedSelected.equals(normalizeSymbol(trade.getCode()))
                || normalizedSelected.equals(normalizeSymbol(trade.getProductName()));
    }

    private static String normalizeSymbol(@Nullable String rawSymbol) {
        String value = rawSymbol == null ? "" : rawSymbol.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        if (value.contains("BTC")) {
            return "BTCUSDT";
        }
        if (value.contains("XAU") || value.contains("GOLD")) {
            return "XAUUSDT";
        }
        return value;
    }

    private static long resolveOpenTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        return trade.getOpenTime() > 0L ? trade.getOpenTime() : trade.getTimestamp();
    }

    private static long resolveCloseTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        if (trade.getCloseTime() > 0L) {
            return trade.getCloseTime();
        }
        return trade.getTimestamp();
    }

    private static double resolveClosePrice(TradeRecordItem trade) {
        if (trade == null) {
            return 0d;
        }
        if (trade.getClosePrice() > 0d) {
            return trade.getClosePrice();
        }
        return trade.getPrice();
    }

    private static double resolveOpenPrice(TradeRecordItem trade) {
        if (trade == null) {
            return 0d;
        }
        if (trade.getOpenPrice() > 0d) {
            return trade.getOpenPrice();
        }
        return trade.getPrice();
    }

    private static String resolveProductName(@Nullable TradeRecordItem trade) {
        if (trade == null || trade.getProductName() == null || trade.getProductName().trim().isEmpty()) {
            return resolveCode(trade);
        }
        return trade.getProductName().trim();
    }

    private static String resolveCode(@Nullable TradeRecordItem trade) {
        return trade == null || trade.getCode() == null ? "" : trade.getCode().trim();
    }

    private static String normalizeTradeSide(@Nullable String side) {
        String normalized = side == null ? "" : side.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("sell") || normalized.contains("卖")) {
            return "SELL";
        }
        return "BUY";
    }

    private static String buildGroupId(TradeRecordItem trade, long anchorTime, double closePrice) {
        if (trade == null) {
            return "tradehist|na|" + anchorTime;
        }
        long quantityKey = Math.round(Math.abs(trade.getQuantity()) * 10_000d);
        if (trade.getDealTicket() > 0L) {
            return "tradehist|deal|"
                    + trade.getDealTicket()
                    + "|"
                    + resolveOpenTime(trade)
                    + "|"
                    + resolveCloseTime(trade)
                    + "|"
                    + quantityKey;
        }
        if (trade.getOrderId() > 0L || trade.getPositionId() > 0L) {
            return "tradehist|trade|"
                    + trade.getOrderId()
                    + "|"
                    + trade.getPositionId()
                    + "|"
                    + resolveCloseTime(trade);
        }
        long priceKey = Math.round(Math.abs(closePrice) * 100d);
        return "tradehist|fallback|"
                + normalizeSymbol(trade.getCode())
                + "|"
                + normalizeTradeSide(trade.getSide())
                + "|"
                + anchorTime
                + "|"
                + quantityKey
                + "|"
                + priceKey;
    }

    static final class TradeAnnotation {
        final long entryAnchorTimeMs;
        final double entryPrice;
        final long exitAnchorTimeMs;
        final double exitPrice;
        final String side;
        final double quantity;
        final double totalPnl;
        final String productName;
        final String code;
        final long openTimeMs;
        final long closeTimeMs;
        final long orderId;
        final long positionId;
        final String groupId;

        private TradeAnnotation(long entryAnchorTimeMs,
                                double entryPrice,
                                long exitAnchorTimeMs,
                                double exitPrice,
                                String side,
                                double quantity,
                                double totalPnl,
                                String productName,
                                String code,
                                long openTimeMs,
                                long closeTimeMs,
                                long orderId,
                                long positionId,
                                String groupId) {
            this.entryAnchorTimeMs = entryAnchorTimeMs;
            this.entryPrice = entryPrice;
            this.exitAnchorTimeMs = exitAnchorTimeMs;
            this.exitPrice = exitPrice;
            this.side = side == null ? "BUY" : side;
            this.quantity = quantity;
            this.totalPnl = totalPnl;
            this.productName = productName == null ? "" : productName;
            this.code = code == null ? "" : code;
            this.openTimeMs = openTimeMs;
            this.closeTimeMs = closeTimeMs;
            this.orderId = orderId;
            this.positionId = positionId;
            this.groupId = groupId == null ? "" : groupId;
        }
    }
}

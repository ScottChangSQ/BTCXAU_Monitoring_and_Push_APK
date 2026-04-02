/*
 * 历史成交图表标记构建器，负责把已平仓历史成交映射到当前可见 K 线时间桶。
 * 行情图页通过这里把账户成交记录转换成轻量图表标记，避免在 Activity 里堆积映射细节。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class HistoricalTradeAnnotationBuilder {
    private static final int BUY_COLOR = 0xFF16C784;
    private static final int SELL_COLOR = 0xFFF6465D;

    private HistoricalTradeAnnotationBuilder() {
    }

    // 只把当前产品、已平仓且落在可见 K 线窗口内的成交转换成图表标记。
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
            long anchorTime = resolveAnchorTime(candles, resolveCloseTime(trade));
            if (anchorTime <= 0L) {
                continue;
            }
            double closePrice = resolveClosePrice(trade);
            if (closePrice <= 0d) {
                continue;
            }
            result.add(new TradeAnnotation(
                    anchorTime,
                    closePrice,
                    buildLabel(trade),
                    resolveTradeColor(trade),
                    buildGroupId(trade, anchorTime, closePrice)
            ));
        }
        result.sort(Comparator
                .comparingLong((TradeAnnotation item) -> item.anchorTimeMs)
                .thenComparingDouble(item -> item.price));
        return result;
    }

    // 仅展示已经结束的历史成交，当前未平仓和无平仓时间的数据不进入图表层。
    private static boolean isClosedTrade(@Nullable TradeRecordItem trade) {
        if (trade == null) {
            return false;
        }
        long closeTime = resolveCloseTime(trade);
        long openTime = resolveOpenTime(trade);
        return closeTime > 0L
                && closeTime >= openTime
                && Math.abs(trade.getQuantity()) > 1e-9;
    }

    // 将平仓时间映射到当前图表窗口内对应的 K 线时间桶，窗口外的历史成交直接忽略。
    private static long resolveAnchorTime(List<CandleEntry> candles, long closeTime) {
        if (candles == null || candles.isEmpty() || closeTime <= 0L) {
            return 0L;
        }
        long firstOpen = candles.get(0).getOpenTime();
        long lastOpen = candles.get(candles.size() - 1).getOpenTime();
        long intervalMs = estimateIntervalMs(candles);
        if (closeTime < firstOpen || closeTime > lastOpen + intervalMs) {
            return 0L;
        }
        for (CandleEntry candle : candles) {
            if (candle == null) {
                continue;
            }
            long openTime = candle.getOpenTime();
            long candleClose = candle.getCloseTime() > openTime ? candle.getCloseTime() : openTime + intervalMs;
            if (closeTime >= openTime && closeTime <= candleClose) {
                return openTime;
            }
        }
        return floorAnchorTime(candles, closeTime);
    }

    private static long floorAnchorTime(List<CandleEntry> candles, long closeTime) {
        long candidate = 0L;
        for (CandleEntry candle : candles) {
            if (candle == null) {
                continue;
            }
            long openTime = candle.getOpenTime();
            if (openTime <= closeTime && openTime >= candidate) {
                candidate = openTime;
            }
        }
        return candidate;
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

    private static String buildLabel(TradeRecordItem trade) {
        String side = normalizeTradeSide(trade == null ? null : trade.getSide());
        double totalPnl = (trade == null ? 0d : trade.getProfit() + trade.getStorageFee());
        return side + " " + FormatUtils.formatSignedMoneyNoDecimal(totalPnl);
    }

    private static int resolveTradeColor(TradeRecordItem trade) {
        return "SELL".equals(normalizeTradeSide(trade == null ? null : trade.getSide()))
                ? SELL_COLOR
                : BUY_COLOR;
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
        if (trade.getDealTicket() > 0L) {
            return "tradehist|deal|" + trade.getDealTicket();
        }
        if (trade.getOrderId() > 0L || trade.getPositionId() > 0L) {
            return "tradehist|trade|"
                    + trade.getOrderId()
                    + "|"
                    + trade.getPositionId()
                    + "|"
                    + resolveCloseTime(trade);
        }
        long quantityKey = Math.round(Math.abs(trade.getQuantity()) * 10_000d);
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
        final long anchorTimeMs;
        final double price;
        final String label;
        final int color;
        final String groupId;

        private TradeAnnotation(long anchorTimeMs, double price, String label, int color, String groupId) {
            this.anchorTimeMs = anchorTimeMs;
            this.price = price;
            this.label = label == null ? "" : label;
            this.color = color;
            this.groupId = groupId == null ? "" : groupId;
        }
    }
}

/*
 * 图表交易线数值辅助工具，统一处理 SL/TP 预计盈亏文案与相关费用口径。
 * 与 ChartOverlaySnapshotFactory、MarketChartScreen、KlineChartView 协同工作。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.util.FormatUtils;

import java.util.List;
import java.util.Locale;

final class ChartTradeLineValueHelper {
    private static final double EPSILON = 1e-9;

    private ChartTradeLineValueHelper() {
    }

    @NonNull
    static String resolveTradeLineLabel(@NonNull ChartTradeLineRole role,
                                        double price,
                                        @NonNull PositionItem targetItem,
                                        double accumulatedFee) {
        if (role == ChartTradeLineRole.TP || role == ChartTradeLineRole.SL) {
            double expectedPnL = resolveExpectedPnL(price, targetItem, accumulatedFee);
            String label = Double.isFinite(expectedPnL)
                    ? FormatUtils.formatSignedMoney(expectedPnL)
                    : "--";
            return role.name() + " " + label;
        }
        return resolveEntryLabel(targetItem);
    }

    static double resolveExpectedPnL(double price,
                                     @NonNull PositionItem targetItem,
                                     double accumulatedFee) {
        if (!Double.isFinite(price) || price <= 0d) {
            return Double.NaN;
        }
        double referencePrice = resolveReferencePrice(targetItem);
        double quantity = resolveTradeQuantity(targetItem);
        if (!Double.isFinite(referencePrice) || referencePrice <= 0d
                || !Double.isFinite(quantity) || quantity <= EPSILON) {
            return Double.NaN;
        }
        double grossPnL = isSellSide(targetItem.getSide())
                ? (referencePrice - price) * quantity
                : (price - referencePrice) * quantity;
        return grossPnL + accumulatedFee + targetItem.getStorageFee();
    }

    static double resolveAccumulatedFee(@Nullable List<TradeRecordItem> trades,
                                        @Nullable String selectedSymbol,
                                        @NonNull PositionItem targetItem) {
        if (trades == null || trades.isEmpty()) {
            return 0d;
        }
        long positionTicket = targetItem.getPositionTicket();
        long orderId = targetItem.getOrderId();
        if (positionTicket <= 0L && orderId <= 0L) {
            return 0d;
        }
        double feeSum = 0d;
        for (TradeRecordItem trade : trades) {
            if (trade == null || !matchesSelectedSymbol(selectedSymbol, trade)) {
                continue;
            }
            boolean samePosition = positionTicket > 0L && trade.getPositionId() == positionTicket;
            boolean sameOrder = orderId > 0L && trade.getOrderId() == orderId;
            if (!samePosition && !sameOrder) {
                continue;
            }
            feeSum += trade.getFee();
        }
        return feeSum;
    }

    static double resolveReferencePrice(@NonNull PositionItem targetItem) {
        if (Math.abs(targetItem.getQuantity()) > EPSILON && targetItem.getCostPrice() > 0d) {
            return targetItem.getCostPrice();
        }
        if (targetItem.getPendingPrice() > 0d) {
            return targetItem.getPendingPrice();
        }
        if (targetItem.getCostPrice() > 0d) {
            return targetItem.getCostPrice();
        }
        return targetItem.getLatestPrice() > 0d ? targetItem.getLatestPrice() : Double.NaN;
    }

    private static double resolveTradeQuantity(@NonNull PositionItem targetItem) {
        if (Math.abs(targetItem.getQuantity()) > EPSILON) {
            return Math.abs(targetItem.getQuantity());
        }
        if (Math.abs(targetItem.getPendingLots()) > EPSILON) {
            return Math.abs(targetItem.getPendingLots());
        }
        return Double.NaN;
    }

    @NonNull
    private static String resolveEntryLabel(@NonNull PositionItem targetItem) {
        String side = resolveTradeSideCn(targetItem.getSide());
        if (Math.abs(targetItem.getQuantity()) > EPSILON) {
            return side + " " + formatLotsLabel(Math.abs(targetItem.getQuantity()));
        }
        double pendingLots = Math.abs(targetItem.getPendingLots());
        String qtyLabel = pendingLots > EPSILON
                ? formatLotsLabel(pendingLots)
                : (targetItem.getPendingCount() > 0 ? (targetItem.getPendingCount() + "单") : "--");
        return "挂单 " + side + " " + qtyLabel;
    }

    @NonNull
    private static String resolveTradeSideCn(@Nullable String side) {
        return isSellSide(side) ? "卖" : "买";
    }

    private static boolean isSellSide(@Nullable String side) {
        return side != null && "sell".equalsIgnoreCase(side.trim());
    }

    @NonNull
    private static String formatLotsLabel(double quantity) {
        return String.format(Locale.getDefault(), "%.2f手", quantity);
    }

    private static boolean matchesSelectedSymbol(@Nullable String selectedSymbol,
                                                 @NonNull TradeRecordItem trade) {
        String normalizedSelected = normalizeSymbol(selectedSymbol);
        if (normalizedSelected.isEmpty()) {
            return true;
        }
        return normalizedSelected.equals(normalizeSymbol(trade.getCode()))
                || normalizedSelected.equals(normalizeSymbol(trade.getProductName()));
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String rawSymbol) {
        String normalized = MarketChartTradeSupport.toTradeSymbol(rawSymbol);
        return normalized == null ? "" : normalized.trim().toUpperCase(Locale.ROOT);
    }
}

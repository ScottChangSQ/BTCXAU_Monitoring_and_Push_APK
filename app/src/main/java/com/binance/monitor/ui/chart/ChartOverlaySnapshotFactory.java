/*
 * 图表叠加层快照工厂，负责把账户快照转换成图上标注和轻量状态文案。
 * 所有筛选、排序和标注文案拼接都在后台线程完成，UI 线程只接收最终结果。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.account.AccountOverviewMetricsHelper;
import com.binance.monitor.util.FormatUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ChartOverlaySnapshotFactory {

    private static final int COLOR_BUY = 0xFF4D8BFF;
    private static final int COLOR_SELL = 0xFFF6465D;
    private static final int COLOR_GAIN = 0xFF16C784;
    private static final int COLOR_HISTORY_EXIT = 0xFFE7EEF7;
    private final DecimalFormat quantityFormat = new DecimalFormat("0.####");

    @NonNull
    public ChartOverlaySnapshot build(@NonNull String selectedSymbol,
                                      @Nullable List<CandleEntry> candles,
                                      @Nullable AccountSnapshot snapshot,
                                      @Nullable AccountStatsPreloadManager.Cache cache) {
        String signature = buildSignature(selectedSymbol, candles, snapshot, cache);
        if (snapshot == null) {
            return ChartOverlaySnapshot.empty("持仓盈亏: -- | 持仓收益率: --", "更新时间 --", signature);
        }
        List<TradeRecordItem> trades = safeTrades(snapshot.getTrades());
        List<PositionItem> positions = safePositions(snapshot.getPositions());
        List<PositionItem> pendingOrders = safePositions(snapshot.getPendingOrders());
        List<KlineChartView.PriceAnnotation> historyTradeAnnotations =
                filterHistoricalTradeAnnotations(selectedSymbol, candles, trades);
        List<KlineChartView.PriceAnnotation> positionAnnotations =
                buildPositionAnnotations(selectedSymbol, candles, positions, trades);
        List<KlineChartView.PriceAnnotation> pendingAnnotations =
                buildPendingAnnotations(selectedSymbol, candles, pendingOrders, trades);
        KlineChartView.AggregateCostAnnotation aggregateCostAnnotation =
                buildAggregateCostAnnotation(selectedSymbol, positions);
        String summaryText = buildSummaryText(snapshot, positions, trades);
        String updatedAtText = buildUpdatedAtText(cache);
        return new ChartOverlaySnapshot(
                positionAnnotations,
                pendingAnnotations,
                historyTradeAnnotations,
                aggregateCostAnnotation,
                summaryText,
                updatedAtText,
                signature
        );
    }

    @NonNull
    private List<TradeRecordItem> safeTrades(@Nullable List<TradeRecordItem> trades) {
        return trades == null ? Collections.emptyList() : new ArrayList<>(trades);
    }

    @NonNull
    private List<PositionItem> safePositions(@Nullable List<PositionItem> positions) {
        return positions == null ? Collections.emptyList() : new ArrayList<>(positions);
    }

    @NonNull
    private List<KlineChartView.PriceAnnotation> filterHistoricalTradeAnnotations(@NonNull String selectedSymbol,
                                                                                  @Nullable List<CandleEntry> candles,
                                                                                  @NonNull List<TradeRecordItem> trades) {
        List<KlineChartView.PriceAnnotation> result = new ArrayList<>();
        List<HistoricalTradeAnnotationBuilder.TradeAnnotation> source =
                HistoricalTradeAnnotationBuilder.build(selectedSymbol, trades, candles == null ? Collections.emptyList() : candles);
        for (HistoricalTradeAnnotationBuilder.TradeAnnotation item : source) {
            if (item == null || !isTradeVisible(candles, item.entryAnchorTimeMs, item.exitAnchorTimeMs)) {
                continue;
            }
            String sideLabel = "SELL".equalsIgnoreCase(item.side) ? "卖出" : "买入";
            int entryColor = "SELL".equalsIgnoreCase(item.side) ? COLOR_SELL : COLOR_BUY;
            int connectorColor = item.totalPnl >= 0d ? COLOR_GAIN : COLOR_SELL;
            String pnlLabel = formatSignedUsd(item.totalPnl);
            String[] detailLines = new String[]{
                    safeTradePopupValue(item.productName, item.code),
                    "方向 " + sideLabel,
                    "开仓 " + FormatUtils.formatDateTime(item.openTimeMs) + " $" + FormatUtils.formatPrice(item.entryPrice),
                    "平仓 " + FormatUtils.formatDateTime(item.closeTimeMs) + " $" + FormatUtils.formatPrice(item.exitPrice),
                    "数量 " + formatQuantity(item.quantity),
                    "盈亏 " + pnlLabel
            };
            result.add(new KlineChartView.PriceAnnotation(
                    item.entryAnchorTimeMs,
                    item.entryPrice,
                    sideLabel,
                    entryColor,
                    item.groupId,
                    1,
                    0f,
                    0L,
                    Double.NaN,
                    "SELL".equalsIgnoreCase(item.side)
                            ? KlineChartView.ANNOTATION_KIND_HISTORY_ENTRY_SELL
                            : KlineChartView.ANNOTATION_KIND_HISTORY_ENTRY_BUY,
                    detailLines
            ));
            result.add(new KlineChartView.PriceAnnotation(
                    item.entryAnchorTimeMs,
                    item.entryPrice,
                    pnlLabel,
                    connectorColor,
                    item.groupId,
                    1,
                    0f,
                    item.exitAnchorTimeMs,
                    item.exitPrice,
                    KlineChartView.ANNOTATION_KIND_HISTORY_CONNECTOR,
                    detailLines
            ));
            result.add(new KlineChartView.PriceAnnotation(
                    item.exitAnchorTimeMs,
                    item.exitPrice,
                    "平仓",
                    COLOR_HISTORY_EXIT,
                    item.groupId,
                    1,
                    0f,
                    0L,
                    Double.NaN,
                    KlineChartView.ANNOTATION_KIND_HISTORY_EXIT,
                    detailLines
            ));
        }
        return result;
    }

    @NonNull
    private List<KlineChartView.PriceAnnotation> buildPositionAnnotations(@NonNull String selectedSymbol,
                                                                          @Nullable List<CandleEntry> candles,
                                                                          @NonNull List<PositionItem> positions,
                                                                          @NonNull List<TradeRecordItem> trades) {
        List<KlineChartView.PriceAnnotation> result = new ArrayList<>();
        for (PositionItem item : positions) {
            if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                continue;
            }
            if (!matchesSelectedSymbol(selectedSymbol, item.getCode(), item.getProductName())) {
                continue;
            }
            double price = item.getCostPrice() > 0d ? item.getCostPrice() : item.getLatestPrice();
            if (price <= 0d) {
                continue;
            }
            long anchorTime = resolvePositionAnchorTime(selectedSymbol, item, trades);
            if (!isWithinVisibleRange(candles, anchorTime)) {
                continue;
            }
            String side = normalizeTradeSideLabel(item.getSide());
            String label = side + " " + formatQuantity(Math.abs(item.getQuantity()))
                    + ", " + formatSignedUsd(item.getTotalPnL());
            int color = item.getTotalPnL() >= 0d ? COLOR_GAIN : COLOR_SELL;
            String groupId = buildAnnotationGroupId("position", item);
            String[] detailLines = new String[]{
                    safeTradePopupValue(item.getProductName(), item.getCode()),
                    "方向 " + side,
                    "开仓 " + formatPositionOpenTime(anchorTime) + " $" + FormatUtils.formatPrice(price),
                    "数量 " + formatQuantity(Math.abs(item.getQuantity())),
                    "浮盈亏 " + formatSignedUsd(item.getTotalPnL()),
                    "止盈 " + formatOptionalPrice(item.getTakeProfit()),
                    "止损 " + formatOptionalPrice(item.getStopLoss())
            };
            result.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    price,
                    label,
                    color,
                    groupId,
                    1,
                    0f,
                    0L,
                    Double.NaN,
                    KlineChartView.ANNOTATION_KIND_DEFAULT,
                    detailLines
            ));
            appendTpSlAnnotations(result, anchorTime, item.getTakeProfit(), item.getStopLoss(), groupId);
        }
        result.sort(Comparator.comparingDouble(annotation -> annotation.price));
        return result;
    }

    @NonNull
    private List<KlineChartView.PriceAnnotation> buildPendingAnnotations(@NonNull String selectedSymbol,
                                                                         @Nullable List<CandleEntry> candles,
                                                                         @NonNull List<PositionItem> pendingOrders,
                                                                         @NonNull List<TradeRecordItem> trades) {
        List<KlineChartView.PriceAnnotation> result = new ArrayList<>();
        for (PositionItem item : pendingOrders) {
            if (item == null) {
                continue;
            }
            if (!matchesSelectedSymbol(selectedSymbol, item.getCode(), item.getProductName())) {
                continue;
            }
            double lots = resolvePendingLots(item);
            if (lots <= 1e-9 && item.getPendingCount() <= 0) {
                continue;
            }
            double price = item.getPendingPrice() > 0d
                    ? item.getPendingPrice()
                    : (item.getCostPrice() > 0d ? item.getCostPrice() : item.getLatestPrice());
            if (price <= 0d) {
                continue;
            }
            long anchorTime = resolvePendingAnchorTime(selectedSymbol, item, trades);
            if (!isWithinVisibleRange(candles, anchorTime)) {
                continue;
            }
            String side = normalizeTradeSideLabel(item.getSide());
            String qtyLabel = lots > 1e-9
                    ? formatQuantity(lots)
                    : (item.getPendingCount() > 0 ? (item.getPendingCount() + "单") : "--");
            String label = "PENDING " + side + " " + qtyLabel + ", @ $" + FormatUtils.formatPrice(price);
            int color = isSellSide(item.getSide()) ? COLOR_SELL : COLOR_GAIN;
            String groupId = buildAnnotationGroupId("pending", item);
            String[] detailLines = new String[]{
                    safeTradePopupValue(item.getProductName(), item.getCode()),
                    "方向 " + side,
                    "挂单 " + formatPositionOpenTime(anchorTime) + " $" + FormatUtils.formatPrice(price),
                    "数量 " + qtyLabel,
                    "止盈 " + formatOptionalPrice(item.getTakeProfit()),
                    "止损 " + formatOptionalPrice(item.getStopLoss())
            };
            result.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    price,
                    label,
                    color,
                    groupId,
                    1,
                    0f,
                    0L,
                    Double.NaN,
                    KlineChartView.ANNOTATION_KIND_DEFAULT,
                    detailLines
            ));
            appendTpSlAnnotations(result, anchorTime, item.getTakeProfit(), item.getStopLoss(), groupId);
        }
        result.sort(Comparator.comparingDouble(annotation -> annotation.price));
        return result;
    }

    @Nullable
    private KlineChartView.AggregateCostAnnotation buildAggregateCostAnnotation(@NonNull String selectedSymbol,
                                                                                @NonNull List<PositionItem> positions) {
        double weightedCost = 0d;
        double qty = 0d;
        for (PositionItem item : positions) {
            if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                continue;
            }
            if (!matchesSelectedSymbol(selectedSymbol, item.getCode(), item.getProductName())) {
                continue;
            }
            if (item.getCostPrice() <= 0d) {
                continue;
            }
            double absQty = Math.abs(item.getQuantity());
            weightedCost += item.getCostPrice() * absQty;
            qty += absQty;
        }
        if (qty <= 1e-9) {
            return null;
        }
        double avgCost = weightedCost / qty;
        return new KlineChartView.AggregateCostAnnotation(avgCost, FormatUtils.formatPrice(avgCost), selectedSymbol);
    }

    @NonNull
    private String buildSummaryText(@NonNull AccountSnapshot snapshot,
                                    @NonNull List<PositionItem> positions,
                                    @NonNull List<TradeRecordItem> trades) {
        boolean hasCanonicalOverviewBase = hasOverviewMetric(snapshot.getOverviewMetrics(), "总资产")
                && hasOverviewMetric(snapshot.getOverviewMetrics(), "净资产");
        List<AccountMetric> overviewMetrics = AccountOverviewMetricsHelper.buildOverviewMetrics(
                snapshot.getOverviewMetrics(),
                positions,
                trades,
                snapshot.getCurvePoints(),
                System.currentTimeMillis(),
                TimeZone.getDefault()
        );
        String positionPnl = findOverviewMetricValue(overviewMetrics, "持仓盈亏");
        String positionReturn = findOverviewMetricValue(overviewMetrics, "持仓收益率");
        if (!hasCanonicalOverviewBase || "--".equals(positionPnl) || "--".equals(positionReturn)) {
            return buildSummaryTextFromPositions(positions);
        }
        return "持仓盈亏: " + positionPnl + " | 持仓收益率: " + positionReturn;
    }

    @NonNull
    private String buildSummaryTextFromPositions(@NonNull List<PositionItem> positions) {
        double positionPnl = 0d;
        double positionCost = 0d;
        for (PositionItem item : positions) {
            if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                continue;
            }
            positionPnl += item.getTotalPnL() + item.getStorageFee();
            if (item.getCostPrice() > 0d) {
                positionCost += Math.abs(item.getQuantity()) * item.getCostPrice();
            }
        }
        double positionReturn = positionCost <= 1e-9 ? 0d : positionPnl / positionCost;
        return "持仓盈亏: " + FormatUtils.formatSignedMoney(positionPnl)
                + " | 持仓收益率: "
                + String.format(Locale.getDefault(), "%+.2f%%", positionReturn * 100d);
    }

    private boolean hasOverviewMetric(@Nullable List<AccountMetric> metrics,
                                      @NonNull String targetName) {
        return !"--".equals(findOverviewMetricValue(metrics, targetName));
    }

    @NonNull
    private String buildUpdatedAtText(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null) {
            return "更新时间 --";
        }
        long updatedAt = cache.getUpdatedAt() > 0L ? cache.getUpdatedAt() : cache.getFetchedAt();
        return updatedAt <= 0L ? "更新时间 --" : "更新时间 " + FormatUtils.formatDateTime(updatedAt);
    }

    @NonNull
    private String findOverviewMetricValue(@Nullable List<AccountMetric> metrics,
                                           @NonNull String targetName) {
        if (metrics == null || metrics.isEmpty()) {
            return "--";
        }
        for (AccountMetric metric : metrics) {
            if (metric == null || metric.getName() == null) {
                continue;
            }
            if (!targetName.equalsIgnoreCase(metric.getName().trim())) {
                continue;
            }
            String value = metric.getValue() == null ? "" : metric.getValue().trim();
            return value.isEmpty() ? "--" : value;
        }
        return "--";
    }

    @NonNull
    private String buildSignature(@NonNull String selectedSymbol,
                                  @Nullable List<CandleEntry> candles,
                                  @Nullable AccountSnapshot snapshot,
                                  @Nullable AccountStatsPreloadManager.Cache cache) {
        StringBuilder builder = new StringBuilder();
        builder.append("symbol=").append(selectedSymbol).append('\n');
        builder.append("windowStart=").append(resolveWindowStart(candles)).append('\n');
        builder.append("windowEnd=").append(resolveWindowEnd(candles)).append('\n');
        builder.append("updatedAt=").append(cache == null ? 0L : cache.getUpdatedAt()).append('\n');
        builder.append("historyRevision=").append(cache == null ? "" : safeText(cache.getHistoryRevision())).append('\n');
        if (snapshot != null) {
            builder.append("positions=").append(snapshot.getPositions() == null ? 0 : snapshot.getPositions().size()).append('\n');
            builder.append("pending=").append(snapshot.getPendingOrders() == null ? 0 : snapshot.getPendingOrders().size()).append('\n');
            builder.append("trades=").append(snapshot.getTrades() == null ? 0 : snapshot.getTrades().size()).append('\n');
        }
        return sha256(builder.toString());
    }

    private long resolvePositionAnchorTime(@NonNull String selectedSymbol,
                                           @NonNull PositionItem position,
                                           @NonNull List<TradeRecordItem> trades) {
        if (position.getOpenTime() > 0L) {
            return position.getOpenTime();
        }
        long byPositionId = findTradeOpenTimeByPositionId(selectedSymbol, position.getPositionTicket(), position.getCostPrice(), trades);
        if (byPositionId > 0L) {
            return byPositionId;
        }
        return findTradeOpenTimeByOrderId(selectedSymbol, position.getOrderId(), position.getCostPrice(), trades);
    }

    private long resolvePendingAnchorTime(@NonNull String selectedSymbol,
                                          @NonNull PositionItem pendingOrder,
                                          @NonNull List<TradeRecordItem> trades) {
        if (pendingOrder.getOpenTime() > 0L) {
            return pendingOrder.getOpenTime();
        }
        long byOrderId = findTradeOpenTimeByOrderId(selectedSymbol, pendingOrder.getOrderId(), pendingOrder.getPendingPrice(), trades);
        if (byOrderId > 0L) {
            return byOrderId;
        }
        return findTradeOpenTimeByPositionId(selectedSymbol, pendingOrder.getPositionTicket(), pendingOrder.getPendingPrice(), trades);
    }

    private long findTradeOpenTimeByPositionId(@NonNull String selectedSymbol,
                                               long positionId,
                                               double targetPrice,
                                               @NonNull List<TradeRecordItem> trades) {
        if (positionId <= 0L) {
            return 0L;
        }
        double bestScore = Double.MAX_VALUE;
        long bestTime = 0L;
        for (TradeRecordItem trade : trades) {
            if (trade == null || trade.getPositionId() != positionId) {
                continue;
            }
            if (!matchesSelectedSymbol(selectedSymbol, trade.getCode(), trade.getProductName())) {
                continue;
            }
            long openTime = trade.getOpenTime();
            if (openTime <= 0L) {
                continue;
            }
            double score = priceDistance(trade.getOpenPrice(), targetPrice);
            if (score < bestScore || (Math.abs(score - bestScore) < 1e-9 && openTime > bestTime)) {
                bestScore = score;
                bestTime = openTime;
            }
        }
        return bestTime;
    }

    private long findTradeOpenTimeByOrderId(@NonNull String selectedSymbol,
                                            long orderId,
                                            double targetPrice,
                                            @NonNull List<TradeRecordItem> trades) {
        if (orderId <= 0L) {
            return 0L;
        }
        double bestScore = Double.MAX_VALUE;
        long bestTime = 0L;
        for (TradeRecordItem trade : trades) {
            if (trade == null || trade.getOrderId() != orderId) {
                continue;
            }
            if (!matchesSelectedSymbol(selectedSymbol, trade.getCode(), trade.getProductName())) {
                continue;
            }
            long openTime = trade.getOpenTime();
            if (openTime <= 0L) {
                continue;
            }
            double score = priceDistance(trade.getOpenPrice(), targetPrice);
            if (score < bestScore || (Math.abs(score - bestScore) < 1e-9 && openTime > bestTime)) {
                bestScore = score;
                bestTime = openTime;
            }
        }
        return bestTime;
    }

    private boolean isTradeVisible(@Nullable List<CandleEntry> candles,
                                   long startTime,
                                   long endTime) {
        long windowStart = resolveWindowStart(candles);
        long windowEnd = resolveWindowEnd(candles);
        if (windowStart <= 0L || windowEnd <= 0L) {
            return true;
        }
        long effectiveEnd = endTime > 0L ? endTime : startTime;
        if (effectiveEnd <= 0L) {
            return false;
        }
        return effectiveEnd >= windowStart && startTime <= windowEnd;
    }

    private boolean isWithinVisibleRange(@Nullable List<CandleEntry> candles, long anchorTime) {
        long windowStart = resolveWindowStart(candles);
        long windowEnd = resolveWindowEnd(candles);
        if (windowStart <= 0L || windowEnd <= 0L) {
            return anchorTime > 0L;
        }
        return anchorTime >= windowStart && anchorTime <= windowEnd;
    }

    private long resolveWindowStart(@Nullable List<CandleEntry> candles) {
        if (candles == null || candles.isEmpty()) {
            return 0L;
        }
        CandleEntry first = candles.get(0);
        return first == null ? 0L : first.getOpenTime();
    }

    private long resolveWindowEnd(@Nullable List<CandleEntry> candles) {
        if (candles == null || candles.isEmpty()) {
            return 0L;
        }
        CandleEntry last = candles.get(candles.size() - 1);
        return last == null ? 0L : last.getCloseTime();
    }

    private boolean matchesSelectedSymbol(@NonNull String selectedSymbol,
                                          @Nullable String code,
                                          @Nullable String productName) {
        String normalizedSelected = normalizeSymbol(selectedSymbol);
        if (normalizedSelected.isEmpty()) {
            return false;
        }
        return normalizedSelected.equals(normalizeSymbol(code))
                || normalizedSelected.equals(normalizeSymbol(productName));
    }

    @NonNull
    private String normalizeSymbol(@Nullable String rawSymbol) {
        String normalized = MarketChartTradeSupport.toTradeSymbol(rawSymbol);
        return normalized == null ? "" : normalized.trim().toUpperCase(Locale.ROOT);
    }

    @NonNull
    private String normalizeTradeSideLabel(@Nullable String side) {
        return isSellSide(side) ? "SELL" : "BUY";
    }

    private boolean isSellSide(@Nullable String side) {
        String normalized = side == null ? "" : side.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("sell") || normalized.contains("卖");
    }

    @NonNull
    private String formatQuantity(double quantity) {
        return quantityFormat.format(Math.max(0d, quantity));
    }

    @NonNull
    private String formatSignedUsd(double value) {
        String sign = value >= 0d ? "+" : "-";
        return sign + "$" + FormatUtils.formatAmount(Math.abs(value));
    }

    @NonNull
    private String formatOptionalPrice(double value) {
        return value <= 0d ? "--" : "$" + FormatUtils.formatPrice(value);
    }

    @NonNull
    private String formatPositionOpenTime(long openTimeMs) {
        return openTimeMs <= 0L ? "--" : FormatUtils.formatDateTime(openTimeMs);
    }

    @NonNull
    private String safeTradePopupValue(@Nullable String productName, @Nullable String code) {
        if (productName != null && !productName.trim().isEmpty()) {
            return productName.trim();
        }
        return code == null ? "--" : code.trim();
    }

    @NonNull
    private String buildAnnotationGroupId(@NonNull String type, @NonNull PositionItem item) {
        if (item.getPositionTicket() > 0L) {
            return type + "|position|" + item.getPositionTicket();
        }
        if (item.getOrderId() > 0L) {
            return type + "|order|" + item.getOrderId();
        }
        return type + "|none";
    }

    private void appendTpSlAnnotations(@NonNull List<KlineChartView.PriceAnnotation> output,
                                       long anchorTime,
                                       double takeProfit,
                                       double stopLoss,
                                       @NonNull String groupId) {
        if (takeProfit > 0d) {
            output.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    takeProfit,
                    "TP $" + FormatUtils.formatPrice(takeProfit),
                    COLOR_GAIN,
                    groupId
            ));
        }
        if (stopLoss > 0d) {
            output.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    stopLoss,
                    "SL $" + FormatUtils.formatPrice(stopLoss),
                    COLOR_SELL,
                    groupId
            ));
        }
    }

    private double resolvePendingLots(@NonNull PositionItem item) {
        return item.getPendingLots() > 1e-9 ? item.getPendingLots() : Math.max(0d, item.getQuantity());
    }

    private double priceDistance(double left, double right) {
        if (left <= 0d || right <= 0d) {
            return Double.MAX_VALUE / 4d;
        }
        return Math.abs(left - right) / Math.max(1d, Math.abs(right));
    }

    @NonNull
    private String safeText(@Nullable String value) {
        return value == null ? "" : value;
    }

    @NonNull
    private String sha256(@NonNull String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("生成图表叠加层签名失败", exception);
        }
    }
}

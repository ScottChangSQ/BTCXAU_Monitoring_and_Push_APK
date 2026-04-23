/*
 * 图表叠加层快照工厂，负责把账户快照转换成图上标注和轻量状态文案。
 * 所有筛选、排序和标注文案拼接都在后台线程完成，UI 线程只接收最终结果。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.state.model.ChartProductRuntimeModel;
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
    static final class ColorScheme {
        final int tradeBuy;
        final int tradeSell;
        final int pnlProfit;
        final int pnlLoss;
        final int historyExit;

        ColorScheme(int tradeBuy,
                    int tradeSell,
                    int pnlProfit,
                    int pnlLoss,
                    int historyExit) {
            this.tradeBuy = tradeBuy;
            this.tradeSell = tradeSell;
            this.pnlProfit = pnlProfit;
            this.pnlLoss = pnlLoss;
            this.historyExit = historyExit;
        }
    }

    private final ColorScheme colorScheme;
    private final DecimalFormat quantityFormat = new DecimalFormat("0.####");

    ChartOverlaySnapshotFactory(@NonNull ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }

    @NonNull
    public ChartOverlaySnapshot build(@NonNull String selectedSymbol,
                                      @Nullable List<CandleEntry> candles,
                                      @Nullable AccountSnapshot snapshot,
                                      @Nullable AccountStatsPreloadManager.Cache cache) {
        return build(selectedSymbol, candles, snapshot, cache, null);
    }

    @NonNull
    public ChartOverlaySnapshot build(@NonNull String selectedSymbol,
                                      @Nullable List<CandleEntry> candles,
                                      @Nullable AccountSnapshot snapshot,
                                      @Nullable AccountStatsPreloadManager.Cache cache,
                                      @Nullable ChartProductRuntimeModel chartRuntimeModel) {
        String signature = buildInputSignature(selectedSymbol, candles, snapshot, cache, chartRuntimeModel);
        if (snapshot == null) {
            return ChartOverlaySnapshot.empty("盈亏：-- | 持仓：--", "更新时间 --", signature);
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
        ChartTradeLayerSnapshot tradeLayerSnapshot =
                buildTradeLayerSnapshot(selectedSymbol, candles, positions, pendingOrders, trades);
        KlineChartView.AggregateCostAnnotation aggregateCostAnnotation =
                buildAggregateCostAnnotation(selectedSymbol, positions);
        String summaryText = buildSummaryText(selectedSymbol, snapshot, positions, trades, chartRuntimeModel);
        String updatedAtText = buildUpdatedAtText(cache);
        return new ChartOverlaySnapshot(
                positionAnnotations,
                pendingAnnotations,
                historyTradeAnnotations,
                tradeLayerSnapshot,
                aggregateCostAnnotation,
                summaryText,
                updatedAtText,
                signature
        );
    }

    // 图表叠加层签名只关心“当前产品 + 当前可见窗口”真正会影响标注的内容。
    @NonNull
    public String buildInputSignature(@NonNull String selectedSymbol,
                                      @Nullable List<CandleEntry> candles,
                                      @Nullable AccountSnapshot snapshot,
                                      @Nullable AccountStatsPreloadManager.Cache cache) {
        return buildInputSignature(selectedSymbol, candles, snapshot, cache, null);
    }

    @NonNull
    public String buildInputSignature(@NonNull String selectedSymbol,
                                      @Nullable List<CandleEntry> candles,
                                      @Nullable AccountSnapshot snapshot,
                                      @Nullable AccountStatsPreloadManager.Cache cache,
                                      @Nullable ChartProductRuntimeModel chartRuntimeModel) {
        StringBuilder builder = new StringBuilder();
        long windowStart = resolveWindowStart(candles);
        long windowEnd = resolveWindowEnd(candles);
        builder.append("symbol=").append(selectedSymbol).append('\n');
        builder.append("windowStart=").append(windowStart).append('\n');
        builder.append("windowEnd=").append(windowEnd).append('\n');
        builder.append("candleCount=").append(candles == null ? 0 : candles.size()).append('\n');
        builder.append("account=").append(cache == null ? "" : safeText(cache.getAccount())).append('\n');
        builder.append("server=").append(cache == null ? "" : safeText(cache.getServer())).append('\n');
        builder.append("historyRevision=").append(cache == null ? "" : safeText(cache.getHistoryRevision())).append('\n');
        if (chartRuntimeModel != null) {
            builder.append("productRevision=")
                    .append(chartRuntimeModel.getProductRevision())
                    .append('\n');
        }
        if (snapshot == null) {
            return sha256(builder.toString());
        }
        appendPositionSignature(builder, "positions", selectedSymbol, snapshot.getPositions(), false);
        appendPositionSignature(builder, "pending", selectedSymbol, snapshot.getPendingOrders(), true);
        appendTradeSignature(builder, selectedSymbol, snapshot.getTrades(), windowStart, windowEnd);
        return sha256(builder.toString());
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
            int entryColor = "SELL".equalsIgnoreCase(item.side) ? colorScheme.tradeSell : colorScheme.tradeBuy;
            int connectorColor = item.totalPnl >= 0d ? colorScheme.pnlProfit : colorScheme.pnlLoss;
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
                    colorScheme.historyExit,
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
            int color = item.getTotalPnL() >= 0d ? colorScheme.pnlProfit : colorScheme.pnlLoss;
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
            int color = isSellSide(item.getSide()) ? colorScheme.tradeSell : colorScheme.tradeBuy;
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

    @NonNull
    private ChartTradeLayerSnapshot buildTradeLayerSnapshot(@NonNull String selectedSymbol,
                                                            @Nullable List<CandleEntry> candles,
                                                            @NonNull List<PositionItem> positions,
                                                            @NonNull List<PositionItem> pendingOrders,
                                                            @NonNull List<TradeRecordItem> trades) {
        List<ChartTradeLine> liveLines = new ArrayList<>();
        appendPositionTradeLines(liveLines, selectedSymbol, candles, positions, trades);
        appendPendingTradeLines(liveLines, selectedSymbol, candles, pendingOrders, trades);
        return new ChartTradeLayerSnapshot(liveLines, null);
    }

    private void appendPositionTradeLines(@NonNull List<ChartTradeLine> output,
                                          @NonNull String selectedSymbol,
                                          @Nullable List<CandleEntry> candles,
                                          @NonNull List<PositionItem> positions,
                                          @NonNull List<TradeRecordItem> trades) {
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
            String groupId = buildAnnotationGroupId("position", item);
            double displayPnl = resolvePositionLinePnl(item);
            double accumulatedFee = ChartTradeLineValueHelper.resolveAccumulatedFee(trades, selectedSymbol, item);
            output.add(new ChartTradeLine(
                    buildTradeLineId(groupId, ChartTradeLineRole.ENTRY),
                    groupId,
                    price,
                    normalizeTradeSideCn(item.getSide()) + " " + formatLotsLabel(Math.abs(item.getQuantity()))
                            + " " + formatSignedUsd(displayPnl),
                    "",
                    ChartTradeLineState.LIVE_POSITION,
                    resolveTradeLineToneByPnl(displayPnl),
                    ChartTradeLineRole.ENTRY,
                    true,
                    false,
                    ""
            ));
            appendTradeLayerTpSlLines(output, groupId, item, accumulatedFee);
        }
    }

    private void appendPendingTradeLines(@NonNull List<ChartTradeLine> output,
                                         @NonNull String selectedSymbol,
                                         @Nullable List<CandleEntry> candles,
                                         @NonNull List<PositionItem> pendingOrders,
                                         @NonNull List<TradeRecordItem> trades) {
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
                    ? formatLotsLabel(lots)
                    : (item.getPendingCount() > 0 ? (item.getPendingCount() + "单") : "--");
            String groupId = buildAnnotationGroupId("pending", item);
            double accumulatedFee = ChartTradeLineValueHelper.resolveAccumulatedFee(trades, selectedSymbol, item);
            output.add(new ChartTradeLine(
                    buildTradeLineId(groupId, ChartTradeLineRole.ENTRY),
                    groupId,
                    price,
                    "挂单 " + normalizeTradeSideCn(item.getSide()) + " " + qtyLabel,
                    "",
                    ChartTradeLineState.LIVE_PENDING,
                    resolveTradeLineToneBySide(item.getSide()),
                    ChartTradeLineRole.ENTRY,
                    true,
                    false,
                    ""
            ));
            appendTradeLayerTpSlLines(output, groupId, item, accumulatedFee);
        }
    }

    private void appendTradeLayerTpSlLines(@NonNull List<ChartTradeLine> output,
                                           @NonNull String groupId,
                                           @NonNull PositionItem item,
                                           double accumulatedFee) {
        double takeProfit = item.getTakeProfit();
        double stopLoss = item.getStopLoss();
        if (takeProfit > 0d) {
            output.add(new ChartTradeLine(
                    buildTradeLineId(groupId, ChartTradeLineRole.TP),
                    groupId,
                    takeProfit,
                    ChartTradeLineValueHelper.resolveTradeLineLabel(
                            ChartTradeLineRole.TP,
                            takeProfit,
                            item,
                            accumulatedFee
                    ),
                    "",
                    ChartTradeLineState.LIVE_TP,
                    ChartTradeLineTone.POSITIVE,
                    ChartTradeLineRole.TP,
                    true,
                    false,
                    ""
            ));
        }
        if (stopLoss > 0d) {
            output.add(new ChartTradeLine(
                    buildTradeLineId(groupId, ChartTradeLineRole.SL),
                    groupId,
                    stopLoss,
                    ChartTradeLineValueHelper.resolveTradeLineLabel(
                            ChartTradeLineRole.SL,
                            stopLoss,
                            item,
                            accumulatedFee
                    ),
                    "",
                    ChartTradeLineState.LIVE_SL,
                    ChartTradeLineTone.NEGATIVE,
                    ChartTradeLineRole.SL,
                    true,
                    false,
                    ""
            ));
        }
    }

    @NonNull
    private String buildTradeLineId(@NonNull String groupId, @NonNull ChartTradeLineRole role) {
        return groupId + "|line|" + role.name().toLowerCase(Locale.ROOT);
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
        return new KlineChartView.AggregateCostAnnotation(avgCost, "$" + FormatUtils.formatPrice(avgCost), selectedSymbol);
    }

    @NonNull
    private String buildSummaryText(@NonNull String selectedSymbol,
                                    @NonNull AccountSnapshot snapshot,
                                    @NonNull List<PositionItem> positions,
                                    @NonNull List<TradeRecordItem> trades,
                                    @Nullable ChartProductRuntimeModel chartRuntimeModel) {
        if (chartRuntimeModel != null
                && !chartRuntimeModel.getCrossPageSummaryText().trim().isEmpty()) {
            return chartRuntimeModel.getCrossPageSummaryText();
        }
        return buildSummaryTextFromPositions(selectedSymbol, positions);
    }

    @NonNull
    private String buildSummaryTextFromPositions(@NonNull String selectedSymbol,
                                                 @NonNull List<PositionItem> positions) {
        return buildSummaryTextFromPositions(selectedSymbol, positions, null);
    }

    @NonNull
    private String buildSummaryTextFromPositions(@NonNull String selectedSymbol,
                                                 @NonNull List<PositionItem> positions,
                                                 @Nullable String resolvedPnlText) {
        double totalLots = 0d;
        double positionPnl = 0d;
        for (PositionItem item : positions) {
            if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                continue;
            }
            if (!matchesSelectedSymbol(selectedSymbol, item.getCode(), item.getProductName())) {
                continue;
            }
            totalLots += Math.abs(item.getQuantity());
            positionPnl += item.getTotalPnL() + item.getStorageFee();
        }
        if (totalLots <= 1e-9) {
            return "盈亏：-- | 持仓：--";
        }
        String pnlText = resolvedPnlText == null || resolvedPnlText.trim().isEmpty()
                ? FormatUtils.formatSignedMoney(positionPnl)
                : resolvedPnlText;
        return "盈亏：" + pnlText + " | 持仓：" + formatQuantity(totalLots) + "手";
    }

    @NonNull
    private String buildUpdatedAtText(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null) {
            return "更新时间 --";
        }
        long updatedAt = cache.getUpdatedAt() > 0L ? cache.getUpdatedAt() : cache.getFetchedAt();
        return updatedAt <= 0L ? "更新时间 --" : "更新时间 " + FormatUtils.formatDateTime(updatedAt);
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

    private void appendPositionSignature(@NonNull StringBuilder builder,
                                         @NonNull String label,
                                         @NonNull String selectedSymbol,
                                         @Nullable List<PositionItem> positions,
                                         boolean pending) {
        List<PositionItem> relevant = safePositions(positions);
        relevant.removeIf(item -> item == null
                || (!pending && Math.abs(item.getQuantity()) <= 1e-9)
                || !matchesSelectedSymbol(selectedSymbol, item.getCode(), item.getProductName()));
        relevant.sort(Comparator
                .comparingLong(PositionItem::getPositionTicket)
                .thenComparingLong(PositionItem::getOrderId)
                .thenComparingLong(PositionItem::getOpenTime)
                .thenComparing(item -> safeText(item.getSide())));
        builder.append(label).append('=').append(relevant.size()).append('\n');
        for (PositionItem item : relevant) {
            builder.append(item.getPositionTicket()).append('|')
                    .append(item.getOrderId()).append('|')
                    .append(item.getOpenTime()).append('|')
                    .append(safeText(item.getSide())).append('|')
                    .append(item.getQuantity()).append('|')
                    .append(item.getCostPrice()).append('|')
                    .append(item.getLatestPrice()).append('|')
                    .append(item.getTotalPnL()).append('|')
                    .append(item.getPendingLots()).append('|')
                    .append(item.getPendingCount()).append('|')
                    .append(item.getPendingPrice()).append('|')
                    .append(item.getTakeProfit()).append('|')
                    .append(item.getStopLoss()).append('|')
                    .append(item.getStorageFee()).append('\n');
        }
    }

    private void appendTradeSignature(@NonNull StringBuilder builder,
                                      @NonNull String selectedSymbol,
                                      @Nullable List<TradeRecordItem> trades,
                                      long windowStart,
                                      long windowEnd) {
        List<TradeRecordItem> relevant = safeTrades(trades);
        relevant.removeIf(item -> item == null
                || !matchesSelectedSymbol(selectedSymbol, item.getCode(), item.getProductName())
                || !isTradeRelevantToWindow(item, windowStart, windowEnd));
        relevant.sort(Comparator
                .comparingLong(TradeRecordItem::getCloseTime)
                .thenComparingLong(TradeRecordItem::getOpenTime)
                .thenComparingLong(TradeRecordItem::getDealTicket)
                .thenComparingLong(TradeRecordItem::getOrderId)
                .thenComparingLong(TradeRecordItem::getPositionId));
        builder.append("trades=").append(relevant.size()).append('\n');
        for (TradeRecordItem item : relevant) {
            builder.append(item.getDealTicket()).append('|')
                    .append(item.getOrderId()).append('|')
                    .append(item.getPositionId()).append('|')
                    .append(item.getOpenTime()).append('|')
                    .append(item.getCloseTime()).append('|')
                    .append(item.getOpenPrice()).append('|')
                    .append(item.getClosePrice()).append('|')
                    .append(item.getQuantity()).append('|')
                    .append(item.getProfit()).append('|')
                    .append(item.getStorageFee()).append('|')
                    .append(item.getEntryType()).append('|')
                    .append(safeText(item.getSide())).append('\n');
        }
    }

    private boolean isTradeRelevantToWindow(@NonNull TradeRecordItem trade,
                                            long windowStart,
                                            long windowEnd) {
        if (windowStart <= 0L || windowEnd <= 0L) {
            return true;
        }
        long openTime = trade.getOpenTime();
        long closeTime = trade.getCloseTime();
        if (closeTime <= 0L) {
            closeTime = openTime;
        }
        return closeTime >= windowStart && openTime <= windowEnd;
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

    @NonNull
    private String normalizeTradeSideCn(@Nullable String side) {
        return isSellSide(side) ? "卖" : "买";
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
    private String formatLotsLabel(double quantity) {
        return formatQuantity(quantity) + "手";
    }

    @NonNull
    private String formatSignedUsd(double value) {
        return FormatUtils.formatSignedMoney(value);
    }

    private double resolvePositionLinePnl(@Nullable PositionItem item) {
        if (item == null) {
            return 0d;
        }
        return item.getTotalPnL() + item.getStorageFee();
    }

    @NonNull
    private ChartTradeLineTone resolveTradeLineToneByPnl(double pnl) {
        if (pnl > 1e-9d) {
            return ChartTradeLineTone.POSITIVE;
        }
        if (pnl < -1e-9d) {
            return ChartTradeLineTone.NEGATIVE;
        }
        return ChartTradeLineTone.NEUTRAL;
    }

    @NonNull
    private ChartTradeLineTone resolveTradeLineToneBySide(@Nullable String side) {
        return isSellSide(side) ? ChartTradeLineTone.NEGATIVE : ChartTradeLineTone.POSITIVE;
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
                    colorScheme.pnlProfit,
                    groupId
            ));
        }
        if (stopLoss > 0d) {
            output.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    stopLoss,
                    "SL $" + FormatUtils.formatPrice(stopLoss),
                    colorScheme.pnlLoss,
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

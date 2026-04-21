/*
 * 图表叠加层快照，承载图上标注和轻量状态文案的最终结果。
 * MarketChartActivity 只消费这个只读结果，不再直接绑定完整账户列表。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChartOverlaySnapshot {
    private final List<KlineChartView.PriceAnnotation> positionAnnotations;
    private final List<KlineChartView.PriceAnnotation> pendingAnnotations;
    private final List<KlineChartView.PriceAnnotation> historyTradeAnnotations;
    private final ChartTradeLayerSnapshot tradeLayerSnapshot;
    private final KlineChartView.AggregateCostAnnotation aggregateCostAnnotation;
    private final String positionSummaryText;
    private final String overlayMetaText;
    private final String signature;

    public ChartOverlaySnapshot(@Nullable List<KlineChartView.PriceAnnotation> positionAnnotations,
                                @Nullable List<KlineChartView.PriceAnnotation> pendingAnnotations,
                                @Nullable List<KlineChartView.PriceAnnotation> historyTradeAnnotations,
                                @Nullable KlineChartView.AggregateCostAnnotation aggregateCostAnnotation,
                                @Nullable String positionSummaryText,
                                @Nullable String overlayMetaText,
                                @Nullable String signature) {
        this(positionAnnotations,
                pendingAnnotations,
                historyTradeAnnotations,
                new ChartTradeLayerSnapshot(null, null),
                aggregateCostAnnotation,
                positionSummaryText,
                overlayMetaText,
                signature);
    }

    public ChartOverlaySnapshot(@Nullable List<KlineChartView.PriceAnnotation> positionAnnotations,
                                @Nullable List<KlineChartView.PriceAnnotation> pendingAnnotations,
                                @Nullable List<KlineChartView.PriceAnnotation> historyTradeAnnotations,
                                @Nullable ChartTradeLayerSnapshot tradeLayerSnapshot,
                                @Nullable KlineChartView.AggregateCostAnnotation aggregateCostAnnotation,
                                @Nullable String positionSummaryText,
                                @Nullable String overlayMetaText,
                                @Nullable String signature) {
        this.positionAnnotations = immutableCopy(positionAnnotations);
        this.pendingAnnotations = immutableCopy(pendingAnnotations);
        this.historyTradeAnnotations = immutableCopy(historyTradeAnnotations);
        this.tradeLayerSnapshot = tradeLayerSnapshot == null ? new ChartTradeLayerSnapshot(null, null) : tradeLayerSnapshot;
        this.aggregateCostAnnotation = aggregateCostAnnotation;
        this.positionSummaryText = positionSummaryText == null ? "" : positionSummaryText;
        this.overlayMetaText = overlayMetaText == null ? "" : overlayMetaText;
        this.signature = signature == null ? "" : signature;
    }

    @NonNull
    public List<KlineChartView.PriceAnnotation> getPositionAnnotations() {
        return positionAnnotations;
    }

    @NonNull
    public List<KlineChartView.PriceAnnotation> getPendingAnnotations() {
        return pendingAnnotations;
    }

    @NonNull
    public List<KlineChartView.PriceAnnotation> getHistoryTradeAnnotations() {
        return historyTradeAnnotations;
    }

    @NonNull
    public ChartTradeLayerSnapshot getTradeLayerSnapshot() {
        return tradeLayerSnapshot;
    }

    @Nullable
    public KlineChartView.AggregateCostAnnotation getAggregateCostAnnotation() {
        return aggregateCostAnnotation;
    }

    @NonNull
    public String getPositionSummaryText() {
        return positionSummaryText;
    }

    @NonNull
    public String getOverlayMetaText() {
        return overlayMetaText;
    }

    @NonNull
    public String getSignature() {
        return signature;
    }

    @NonNull
    public static ChartOverlaySnapshot empty() {
        return empty("盈亏：-- | 持仓：--", "更新时间 --", "");
    }

    @NonNull
    public static ChartOverlaySnapshot empty(@NonNull String summaryText,
                                             @NonNull String overlayMetaText,
                                             @NonNull String signature) {
        return new ChartOverlaySnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                new ChartTradeLayerSnapshot(null, null),
                null,
                summaryText,
                overlayMetaText,
                signature
        );
    }

    @NonNull
    private static <T> List<T> immutableCopy(@Nullable List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}

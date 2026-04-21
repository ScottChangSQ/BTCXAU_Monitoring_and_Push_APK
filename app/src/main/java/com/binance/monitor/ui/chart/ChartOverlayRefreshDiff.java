/*
 * 图表叠加层刷新差异判断器，负责区分“只需刷新摘要”与“必须重刷图上标注”。
 * 供图表页产品运行态刷新链使用，避免摘要文案变化时仍整层重绘。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public final class ChartOverlayRefreshDiff {

    private ChartOverlayRefreshDiff() {
    }

    // 只比较真正会影响图上绘制结果的字段，摘要文案变化不算叠加层重刷。
    public static boolean hasOverlayVisualChange(@Nullable ChartOverlaySnapshot previous,
                                                 @Nullable ChartOverlaySnapshot current) {
        if (previous == null && current == null) {
            return false;
        }
        if (previous == null || current == null) {
            return true;
        }
        if (!buildAnnotationKey(previous.getPositionAnnotations()).equals(buildAnnotationKey(current.getPositionAnnotations()))) {
            return true;
        }
        if (!buildAnnotationKey(previous.getPendingAnnotations()).equals(buildAnnotationKey(current.getPendingAnnotations()))) {
            return true;
        }
        if (!buildAnnotationKey(previous.getHistoryTradeAnnotations()).equals(buildAnnotationKey(current.getHistoryTradeAnnotations()))) {
            return true;
        }
        if (!buildTradeLayerKey(previous.getTradeLayerSnapshot()).equals(buildTradeLayerKey(current.getTradeLayerSnapshot()))) {
            return true;
        }
        return !buildAggregateCostKey(previous.getAggregateCostAnnotation())
                .equals(buildAggregateCostKey(current.getAggregateCostAnnotation()));
    }

    @NonNull
    private static String buildAnnotationKey(@Nullable List<KlineChartView.PriceAnnotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (KlineChartView.PriceAnnotation annotation : annotations) {
            if (annotation == null) {
                builder.append("null;");
                continue;
            }
            builder.append(annotation.anchorTimeMs).append('|')
                    .append(annotation.price).append('|')
                    .append(annotation.label == null ? "" : annotation.label).append('|')
                    .append(annotation.color).append('|')
                    .append(annotation.groupId == null ? "" : annotation.groupId).append('|')
                    .append(annotation.eventCount).append('|')
                    .append(annotation.intensity).append('|')
                    .append(annotation.secondaryAnchorTimeMs).append('|')
                    .append(annotation.secondaryPrice).append('|')
                    .append(annotation.kind).append('|');
            if (annotation.detailLines != null && annotation.detailLines.length > 0) {
                for (String line : annotation.detailLines) {
                    builder.append(line == null ? "" : line).append('#');
                }
            }
            builder.append(';');
        }
        return builder.toString();
    }

    @NonNull
    private static String buildAggregateCostKey(@Nullable KlineChartView.AggregateCostAnnotation annotation) {
        if (annotation == null) {
            return "";
        }
        return annotation.price
                + "|" + (annotation.priceLabel == null ? "" : annotation.priceLabel)
                + "|" + (annotation.symbolLabel == null ? "" : annotation.symbolLabel);
    }

    @NonNull
    private static String buildTradeLayerKey(@Nullable ChartTradeLayerSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendTradeLineKey(builder, snapshot.getLiveLines());
        builder.append("::");
        appendTradeLineKey(builder, snapshot.getDraftLines());
        return builder.toString();
    }

    private static void appendTradeLineKey(@NonNull StringBuilder builder,
                                           @Nullable List<ChartTradeLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (ChartTradeLine line : lines) {
            if (line == null) {
                builder.append("null;");
                continue;
            }
            builder.append(line.getId()).append('|')
                    .append(line.getPrice()).append('|')
                    .append(line.getLabel()).append('|')
                    .append(line.getState()).append(';');
        }
    }
}

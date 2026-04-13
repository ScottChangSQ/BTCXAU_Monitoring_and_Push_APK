/*
 * 账户持仓页分段差异比较器，负责判定概览、持仓、挂单三段是否变化。
 * 该比较器供页面局部刷新使用，避免每次都全量重绘。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.PositionItem;

import java.util.List;

public final class AccountPositionSectionDiff {

    private AccountPositionSectionDiff() {
    }

    // 比较新旧模型的三段内容变化。
    @NonNull
    public static Result diff(@Nullable AccountPositionUiModel previous,
                              @Nullable AccountPositionUiModel current) {
        if (previous == null && current == null) {
            return new Result(false, false, false);
        }
        if (previous == null || current == null) {
            return new Result(true, true, true);
        }
        boolean overviewChanged = !buildOverviewKey(previous).equals(buildOverviewKey(current));
        boolean positionsChanged = !buildPositionKey(previous).equals(buildPositionKey(current));
        boolean pendingChanged = !buildPendingKey(previous).equals(buildPendingKey(current));
        return new Result(overviewChanged, positionsChanged, pendingChanged);
    }

    // 生成概览段比较键。
    @NonNull
    private static String buildOverviewKey(@NonNull AccountPositionUiModel model) {
        StringBuilder builder = new StringBuilder();
        appendMetrics(builder, model.getOverviewMetrics());
        builder.append("|connection=").append(safeText(model.getConnectionStatusText()));
        builder.append("|updatedAt=").append(safeText(model.getUpdatedAtText()));
        return builder.toString();
    }

    // 生成持仓段比较键。
    @NonNull
    private static String buildPositionKey(@NonNull AccountPositionUiModel model) {
        StringBuilder builder = new StringBuilder();
        builder.append("summary=").append(safeText(model.getPositionSummaryText())).append('|');
        appendAggregates(builder, model.getPositionAggregates());
        appendPositions(builder, model.getPositions());
        return builder.toString();
    }

    // 生成挂单段比较键。
    @NonNull
    private static String buildPendingKey(@NonNull AccountPositionUiModel model) {
        StringBuilder builder = new StringBuilder();
        builder.append("summary=").append(safeText(model.getPendingSummaryText())).append('|');
        appendPositions(builder, model.getPendingOrders());
        return builder.toString();
    }

    // 把指标列表追加到比较键中。
    private static void appendMetrics(@NonNull StringBuilder builder, @Nullable List<AccountMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            builder.append("metrics=;");
            return;
        }
        builder.append("metrics=");
        for (AccountMetric metric : metrics) {
            builder.append(safeText(metric == null ? null : metric.getName()))
                    .append('=')
                    .append(safeText(metric == null ? null : metric.getValue()))
                    .append(';');
        }
    }

    // 把持仓列表追加到比较键中。
    private static void appendPositions(@NonNull StringBuilder builder, @Nullable List<PositionItem> items) {
        if (items == null || items.isEmpty()) {
            builder.append("items=;");
            return;
        }
        builder.append("items=");
        for (PositionItem item : items) {
            if (item == null) {
                builder.append("null;");
                continue;
            }
            builder.append(safeText(item.getProductName())).append('|')
                    .append(safeText(item.getCode())).append('|')
                    .append(safeText(item.getSide())).append('|')
                    .append(item.getPositionTicket()).append('|')
                    .append(item.getOrderId()).append('|')
                    .append(item.getOpenTime()).append('|')
                    .append(item.getQuantity()).append('|')
                    .append(item.getSellableQuantity()).append('|')
                    .append(item.getCostPrice()).append('|')
                    .append(item.getLatestPrice()).append('|')
                    .append(item.getMarketValue()).append('|')
                    .append(item.getPositionRatio()).append('|')
                    .append(item.getDayPnL()).append('|')
                    .append(item.getTotalPnL()).append('|')
                    .append(item.getReturnRate()).append('|')
                    .append(item.getPendingLots()).append('|')
                    .append(item.getPendingCount()).append('|')
                    .append(item.getPendingPrice()).append('|')
                    .append(item.getTakeProfit()).append('|')
                    .append(item.getStopLoss()).append('|')
                    .append(item.getStorageFee())
                    .append(';');
        }
    }

    // 把产品聚合列表追加到比较键中。
    private static void appendAggregates(@NonNull StringBuilder builder, @Nullable List<PositionAggregateItem> items) {
        if (items == null || items.isEmpty()) {
            builder.append("aggregates=;");
            return;
        }
        builder.append("aggregates=");
        for (PositionAggregateItem item : items) {
            if (item == null) {
                builder.append("null;");
                continue;
            }
            builder.append(safeText(item.getProductName())).append('|')
                    .append(safeText(item.getSide())).append('|')
                    .append(item.getQuantity()).append('|')
                    .append(item.getAverageCostPrice()).append('|')
                    .append(item.getTotalPnl())
                    .append(';');
        }
    }

    // 统一把空字符串转换为空文本。
    @NonNull
    private static String safeText(@Nullable String value) {
        return value == null ? "" : value;
    }

    public static final class Result {
        private final boolean overviewChanged;
        private final boolean positionsChanged;
        private final boolean pendingChanged;

        public Result(boolean overviewChanged, boolean positionsChanged, boolean pendingChanged) {
            this.overviewChanged = overviewChanged;
            this.positionsChanged = positionsChanged;
            this.pendingChanged = pendingChanged;
        }

        // 返回概览段是否变化。
        public boolean isOverviewChanged() {
            return overviewChanged;
        }

        // 返回持仓段是否变化。
        public boolean isPositionsChanged() {
            return positionsChanged;
        }

        // 返回挂单段是否变化。
        public boolean isPendingChanged() {
            return pendingChanged;
        }

        // 返回是否存在任一段变化。
        public boolean hasAnyChange() {
            return overviewChanged || positionsChanged || pendingChanged;
        }
    }
}

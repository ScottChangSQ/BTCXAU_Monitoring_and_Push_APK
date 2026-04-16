/*
 * 账户统计页渲染签名，只允许基于历史态和本页本地筛选条件构建。
 */
package com.binance.monitor.ui.account.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class AccountStatsRenderSignature {
    private final String text;
    private final String curveSectionKey;
    private final String returnSectionKey;
    private final String tradeStatsSectionKey;
    private final String tradeRecordsSectionKey;

    private AccountStatsRenderSignature(@NonNull String text,
                                        @NonNull String curveSectionKey,
                                        @NonNull String returnSectionKey,
                                        @NonNull String tradeStatsSectionKey,
                                        @NonNull String tradeRecordsSectionKey) {
        this.text = text;
        this.curveSectionKey = curveSectionKey;
        this.returnSectionKey = returnSectionKey;
        this.tradeStatsSectionKey = tradeStatsSectionKey;
        this.tradeRecordsSectionKey = tradeRecordsSectionKey;
    }

    public static AccountStatsRenderSignature from(@Nullable String historyRevision,
                                                   @Nullable List<TradeRecordItem> trades,
                                                   @Nullable List<CurvePoint> curvePoints,
                                                   @Nullable List<AccountMetric> statsMetrics,
                                                   @Nullable String productFilter,
                                                   @Nullable String sideFilter,
                                                   @Nullable String sortFilter,
                                                   boolean sortDescending) {
        return from(historyRevision,
                trades,
                curvePoints,
                statsMetrics,
                Collections.emptyList(),
                productFilter,
                sideFilter,
                sortFilter,
                sortDescending);
    }

    public static AccountStatsRenderSignature from(@Nullable String historyRevision,
                                                   @Nullable List<TradeRecordItem> trades,
                                                   @Nullable List<CurvePoint> curvePoints,
                                                   @Nullable List<AccountMetric> statsMetrics,
                                                   @Nullable List<AccountMetric> curveIndicators,
                                                   @Nullable String productFilter,
                                                   @Nullable String sideFilter,
                                                   @Nullable String sortFilter,
                                                   boolean sortDescending) {
        String curveKey = joinTokens(
                historyRevision,
                summarizeCurves(curvePoints),
                summarizeMetrics(curveIndicators)
        );
        String returnKey = joinTokens(
                historyRevision,
                summarizeCurves(curvePoints)
        );
        String tradeStatsKey = joinTokens(
                historyRevision,
                summarizeTrades(trades),
                summarizeMetrics(statsMetrics),
                productFilter,
                sideFilter
        );
        String tradeRecordsKey = joinTokens(
                historyRevision,
                summarizeTrades(trades),
                productFilter,
                sideFilter,
                sortFilter,
                String.valueOf(sortDescending)
        );
        String text = joinTokens(curveKey, returnKey, tradeStatsKey, tradeRecordsKey);
        return new AccountStatsRenderSignature(text, curveKey, returnKey, tradeStatsKey, tradeRecordsKey);
    }

    public String asText() {
        return text;
    }

    @NonNull
    public String getCurveSectionKey() {
        return curveSectionKey;
    }

    @NonNull
    public String getReturnSectionKey() {
        return returnSectionKey;
    }

    @NonNull
    public String getTradeStatsSectionKey() {
        return tradeStatsSectionKey;
    }

    @NonNull
    public String getTradeRecordsSectionKey() {
        return tradeRecordsSectionKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AccountStatsRenderSignature)) {
            return false;
        }
        AccountStatsRenderSignature that = (AccountStatsRenderSignature) other;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    private static void append(@NonNull StringBuilder builder, @Nullable String token) {
        builder.append(token == null ? "" : token.trim()).append('|');
    }

    @NonNull
    private static String joinTokens(@Nullable String... tokens) {
        StringBuilder builder = new StringBuilder();
        if (tokens != null) {
            for (String token : tokens) {
                append(builder, token);
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String summarizeTrades(@Nullable List<TradeRecordItem> trades) {
        if (trades == null || trades.isEmpty()) {
            return "trades:0";
        }
        TradeRecordItem last = trades.get(trades.size() - 1);
        return String.format(Locale.US, "trades:%d:%d:%.4f",
                trades.size(), last.getCloseTime(), last.getProfit());
    }

    @NonNull
    private static String summarizeCurves(@Nullable List<CurvePoint> curvePoints) {
        if (curvePoints == null || curvePoints.isEmpty()) {
            return "curves:0";
        }
        CurvePoint last = curvePoints.get(curvePoints.size() - 1);
        return String.format(Locale.US, "curves:%d:%d:%.4f",
                curvePoints.size(), last.getTimestamp(), last.getEquity());
    }

    @NonNull
    private static String summarizeMetrics(@Nullable List<AccountMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "metrics:0";
        }
        AccountMetric last = metrics.get(metrics.size() - 1);
        return "metrics:" + metrics.size() + ":" + safe(last.getName()) + ":" + safe(last.getValue());
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}

/*
 * 分析深页目标协议，只表达跳转语义，不承载页面状态或视图依赖。
 */
package com.binance.monitor.ui.account.navigation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AnalysisDeepLinkTarget {

    public enum TargetType {
        ANALYSIS_HOME,
        ANALYSIS_FULL,
        TRADE_HISTORY_FULL
    }

    private final TargetType targetType;
    @Nullable
    private final String accountKey;
    @Nullable
    private final String symbol;
    @Nullable
    private final String timeRange;
    @NonNull
    private final String focusSection;
    @NonNull
    private final Map<String, String> filters;
    @NonNull
    private final String source;

    public AnalysisDeepLinkTarget(@NonNull TargetType targetType,
                                  @Nullable String accountKey,
                                  @Nullable String symbol,
                                  @Nullable String timeRange,
                                  @Nullable String focusSection,
                                  @Nullable Map<String, String> filters,
                                  @Nullable String source) {
        this.targetType = targetType;
        this.accountKey = accountKey;
        this.symbol = symbol;
        this.timeRange = timeRange;
        this.focusSection = focusSection == null ? "" : focusSection;
        this.filters = filters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(filters));
        this.source = source == null ? "" : source;
    }

    @NonNull
    public TargetType getTargetType() {
        return targetType;
    }

    @Nullable
    public String getAccountKey() {
        return accountKey;
    }

    @Nullable
    public String getSymbol() {
        return symbol;
    }

    @Nullable
    public String getTimeRange() {
        return timeRange;
    }

    @NonNull
    public String getFocusSection() {
        return focusSection;
    }

    @NonNull
    public Map<String, String> getFilters() {
        return filters;
    }

    @NonNull
    public String getSource() {
        return source;
    }

    public boolean requiresDirectAnalysisPage() {
        return targetType != TargetType.ANALYSIS_HOME;
    }
}

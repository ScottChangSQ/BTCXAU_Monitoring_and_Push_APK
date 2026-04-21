/*
 * 账户总览指标帮助类，负责把快照指标和本地已知持仓/历史真值整理成统一展示列表。
 * 供账户总览展示区复用，避免多个页面各自重复计算同一套概览指标。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.runtime.account.MetricNameTranslator;
import com.binance.monitor.ui.rules.IndicatorFormatterCenter;
import com.binance.monitor.ui.rules.IndicatorId;
import com.binance.monitor.ui.rules.IndicatorRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class AccountOverviewMetricsHelper {

    private AccountOverviewMetricsHelper() {
    }

    // 把服务端概览和本地历史/持仓真值统一整理成固定展示顺序的账户总览列表。
    @NonNull
    public static List<AccountMetric> buildOverviewMetrics(@Nullable List<AccountMetric> snapshotOverview,
                                                           @Nullable List<PositionItem> positions,
                                                           @Nullable List<TradeRecordItem> trades,
                                                           @Nullable List<CurvePoint> curvePoints,
                                                           long nowMs,
                                                           @NonNull TimeZone timeZone) {
        if (snapshotOverview == null || snapshotOverview.isEmpty()) {
            return new ArrayList<>();
        }
        List<AccountMetric> result = new ArrayList<>(snapshotOverview);
        double totalAsset = metricValue(snapshotOverview, "总资产", "Total Asset", "Total Assets");
        double netAsset = metricValue(snapshotOverview, "净资产", "当前净值", "净值", "Current Equity", "Net Asset");
        AccountOverviewMetricsCalculator.OverviewValues overviewValues =
                AccountOverviewMetricsCalculator.calculate(
                        totalAsset,
                        netAsset,
                        snapshotOverview,
                        castPositions(positions)
                );
        replaceOrAppendOverviewMetric(result,
                IndicatorRegistry.require(IndicatorId.ACCOUNT_AVAILABLE_FUNDS).getDisplayName(),
                IndicatorFormatterCenter.formatMoney(overviewValues.getFreePrepayment(), 2, false));
        replaceOrAppendOverviewMetric(result,
                IndicatorRegistry.require(IndicatorId.ACCOUNT_MARGIN).getDisplayName(),
                IndicatorFormatterCenter.formatMoney(overviewValues.getPrepayment(), 2, false));
        replaceOrAppendOverviewMetric(result,
                IndicatorRegistry.require(IndicatorId.ACCOUNT_POSITION_PNL).getDisplayName(),
                IndicatorFormatterCenter.formatMoney(overviewValues.getPositionPnl(), 2, false));
        replaceOrAppendOverviewMetric(result,
                IndicatorRegistry.require(IndicatorId.ACCOUNT_POSITION_PNL_RATE).getDisplayName(),
                IndicatorFormatterCenter.formatPercent(overviewValues.getPositionPnlRate(), 2, true));

        return sortOverviewMetricsForDisplay(result);
    }

    @NonNull
    private static List<PositionItem> castPositions(@Nullable List<PositionItem> positions) {
        return positions == null ? new ArrayList<>() : new ArrayList<>(positions);
    }

    // 用统一展示名覆盖或补齐总览指标。
    private static void replaceOrAppendOverviewMetric(@NonNull List<AccountMetric> metrics,
                                                      @NonNull String targetName,
                                                      @NonNull String targetValue) {
        String normalizedTarget = trim(targetName);
        for (int i = 0; i < metrics.size(); i++) {
            AccountMetric metric = metrics.get(i);
            if (metric == null) {
                continue;
            }
            String currentName = trim(MetricNameTranslator.toChinese(metric.getName()));
            if (!currentName.equalsIgnoreCase(normalizedTarget)) {
                continue;
            }
            metrics.set(i, new AccountMetric(targetName, targetValue));
            return;
        }
        metrics.add(new AccountMetric(targetName, targetValue));
    }

    // 账户总览统一按固定顺序展示，不跟随服务端字段顺序漂移。
    @NonNull
    private static List<AccountMetric> sortOverviewMetricsForDisplay(@Nullable List<AccountMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, List<AccountMetric>> metricsByKey = new LinkedHashMap<>();
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String displayKey = normalizeOverviewMetricDisplayKey(metric.getName());
            List<AccountMetric> bucket = metricsByKey.get(displayKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                metricsByKey.put(displayKey, bucket);
            }
            bucket.add(metric);
        }
        List<AccountMetric> ordered = new ArrayList<>();
        Set<AccountMetric> appended = new HashSet<>();
        appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, "总资产");
        appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, "净资产");
        appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, "可用预付款");
        appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, "保证金");
        appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, "持仓盈亏");
        appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, "持仓收益率");
        return canonicalizeMetrics(ordered);
    }

    // 从同义指标组里挑出最适合当前展示位的那一项。
    private static void appendOverviewMetricInDisplayOrder(@NonNull List<AccountMetric> ordered,
                                                           @NonNull Map<String, List<AccountMetric>> metricsByKey,
                                                           @NonNull Set<AccountMetric> appended,
                                                           @NonNull String key) {
        List<AccountMetric> bucket = metricsByKey.get(key);
        if (bucket == null || bucket.isEmpty()) {
            return;
        }
        AccountMetric chosen = chooseOverviewMetricForDisplay(bucket, key);
        if (chosen == null) {
            return;
        }
        ordered.add(chosen);
        appended.addAll(bucket);
    }

    @Nullable
    private static AccountMetric chooseOverviewMetricForDisplay(@Nullable List<AccountMetric> bucket,
                                                                @NonNull String targetKey) {
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        for (AccountMetric metric : bucket) {
            if (metric == null) {
                continue;
            }
            String normalizedName = trim(MetricNameTranslator.toChinese(metric.getName()));
            if (normalizedName.equalsIgnoreCase(targetKey)) {
                return metric;
            }
        }
        return bucket.get(0);
    }

    @NonNull
    private static String normalizeOverviewMetricDisplayKey(@Nullable String rawName) {
        String name = trim(MetricNameTranslator.toChinese(rawName));
        if (name.isEmpty()) {
            return "";
        }
        if (IndicatorRegistry.findByDisplayName(name) != null) {
            return IndicatorRegistry.findByDisplayName(name).getDisplayName();
        }
        return name;
    }

    // 把迁移期旧指标名统一改成正式显示名。
    @NonNull
    private static List<AccountMetric> canonicalizeMetrics(@NonNull List<AccountMetric> metrics) {
        List<AccountMetric> result = new ArrayList<>(metrics.size());
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            if (IndicatorRegistry.findByDisplayName(metric.getName()) == null) {
                result.add(metric);
                continue;
            }
            result.add(new AccountMetric(
                    IndicatorRegistry.findByDisplayName(metric.getName()).getDisplayName(),
                    metric.getValue()
            ));
        }
        return result;
    }

    private static double metricValue(@Nullable List<AccountMetric> metrics, String... names) {
        if (metrics == null || metrics.isEmpty() || names == null || names.length == 0) {
            return 0d;
        }
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String metricName = trim(MetricNameTranslator.toChinese(metric.getName())).toLowerCase(Locale.ROOT);
            for (String name : names) {
                String candidate = trim(name).toLowerCase(Locale.ROOT);
                if (!candidate.isEmpty() && metricName.equals(candidate)) {
                    return parseMetricNumber(metric.getValue());
                }
            }
        }
        return 0d;
    }

    private static double parseMetricNumber(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0d;
        }
        StringBuilder builder = new StringBuilder();
        boolean hasDecimal = false;
        boolean hasSign = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '+' || c == '-') && !hasSign && builder.length() == 0) {
                builder.append(c);
                hasSign = true;
            } else if (Character.isDigit(c)) {
                builder.append(c);
            } else if (c == '.' && !hasDecimal) {
                builder.append(c);
                hasDecimal = true;
            }
        }
        if (builder.length() == 0 || "+".contentEquals(builder) || "-".contentEquals(builder)) {
            return 0d;
        }
        try {
            return Double.parseDouble(builder.toString());
        } catch (Exception ignored) {
            return 0d;
        }
    }

    @NonNull
    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

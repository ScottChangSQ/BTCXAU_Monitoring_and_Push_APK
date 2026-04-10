/*
 * 账户总览指标计算辅助，统一收口预付款、可用预付款、持仓市值、仓位占比和持仓收益率的口径。
 * 供账户统计页复用，避免公式继续散落在 Activity 里。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.runtime.account.MetricNameTranslator;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.PositionItem;

import java.util.List;
import java.util.Locale;

public final class AccountOverviewMetricsCalculator {

    private AccountOverviewMetricsCalculator() {
    }

    // 按用户确认的口径统一计算账户总览里的核心持仓指标。
    @NonNull
    public static OverviewValues calculate(double totalAsset,
                                           double netAsset,
                                           @Nullable List<AccountMetric> snapshotOverview,
                                           @Nullable List<PositionItem> currentPositions) {
        double leverage = metricValue(snapshotOverview, "杠杆", "Leverage", "lever");
        if (leverage <= 0d) {
            leverage = 1d;
        }

        double marketValue = 0d;
        double positionPnl = 0d;
        double estimatedPrepayment = 0d;
        if (currentPositions != null) {
            for (PositionItem item : currentPositions) {
                if (item == null) {
                    continue;
                }
                double itemMarketValue = Math.abs(item.getQuantity()) * Math.max(0d, item.getLatestPrice());
                marketValue += itemMarketValue;
                positionPnl += item.getTotalPnL() + item.getStorageFee();
                estimatedPrepayment += safeDivide(itemMarketValue, leverage);
            }
        }

        double prepayment = resolvePrepayment(snapshotOverview, netAsset, estimatedPrepayment);
        double freePrepayment = resolveFreePrepayment(snapshotOverview, netAsset, prepayment);

        double positionRatio = safeDivide(marketValue * leverage, Math.max(1d, netAsset));
        double positionPnlRate = safeDivide(positionPnl, Math.max(1d, totalAsset));
        return new OverviewValues(
                Math.max(0d, prepayment),
                freePrepayment,
                marketValue,
                positionPnl,
                positionRatio,
                positionPnlRate
        );
    }

    // 从总览指标列表里按别名提取数值。
    private static double metricValue(@Nullable List<AccountMetric> metrics, String... names) {
        if (metrics == null || metrics.isEmpty() || names == null || names.length == 0) {
            return 0d;
        }
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String rawName = trim(metric.getName());
            String normalizedName = rawName.toLowerCase(Locale.ROOT);
            String translatedName = trim(MetricNameTranslator.toChinese(rawName)).toLowerCase(Locale.ROOT);
            for (String candidate : names) {
                String normalizedCandidate = trim(candidate).toLowerCase(Locale.ROOT);
                if (normalizedCandidate.isEmpty()) {
                    continue;
                }
                if (normalizedName.contains(normalizedCandidate)
                        || translatedName.contains(normalizedCandidate)) {
                    return parseNumber(metric.getValue());
                }
            }
        }
        return 0d;
    }

    // 对“保证金/可用保证金”这类容易互相包含的字段，优先走精确匹配，避免串值。
    private static double exactMetricValue(@Nullable List<AccountMetric> metrics, String... names) {
        if (metrics == null || metrics.isEmpty() || names == null || names.length == 0) {
            return 0d;
        }
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String rawName = trim(metric.getName()).toLowerCase(Locale.ROOT);
            String translatedName = trim(MetricNameTranslator.toChinese(metric.getName())).toLowerCase(Locale.ROOT);
            for (String candidate : names) {
                String normalizedCandidate = trim(candidate).toLowerCase(Locale.ROOT);
                if (normalizedCandidate.isEmpty()) {
                    continue;
                }
                if (rawName.equals(normalizedCandidate) || translatedName.equals(normalizedCandidate)) {
                    return parseNumber(metric.getValue());
                }
            }
        }
        return 0d;
    }

    // 预付款优先取快照里的保证金；若服务端没给这个字段，则回退用“净资产 - 可用保证金”反推。
    private static double resolvePrepayment(@Nullable List<AccountMetric> metrics,
                                            double netAsset,
                                            double estimatedPrepayment) {
        double direct = exactMetricValue(metrics,
                "预付款", "占用预付款", "已用预付款", "保证金", "保证金金额", "Margin", "Margin Amount");
        if (direct > 0d) {
            return direct;
        }
        double freePrepayment = exactMetricValue(metrics,
                "可用预付款", "可用保证金", "Free Margin", "Free Fund", "Available Funds", "Available");
        if (freePrepayment > 0d) {
            return Math.max(0d, netAsset - freePrepayment);
        }
        return Math.max(0d, estimatedPrepayment);
    }

    // 可用预付款优先取服务端现成值；若缺失，再用“净资产 - 预付款”补齐。
    private static double resolveFreePrepayment(@Nullable List<AccountMetric> metrics,
                                                double netAsset,
                                                double prepayment) {
        double direct = exactMetricValue(metrics,
                "可用预付款", "可用保证金", "Free Margin", "Free Fund", "Available Funds", "Available");
        if (direct > 0d) {
            return direct;
        }
        return Math.max(0d, netAsset - prepayment);
    }

    // 统一从文本里提取数字，兼容货币和百分号。
    private static double parseNumber(@Nullable String raw) {
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
        if (builder.length() == 0 || builder.toString().equals("+") || builder.toString().equals("-")) {
            return 0d;
        }
        try {
            return Double.parseDouble(builder.toString());
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private static double safeDivide(double a, double b) {
        if (Math.abs(b) < 1e-9) {
            return 0d;
        }
        return a / b;
    }

    private static String trim(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    public static final class OverviewValues {
        private final double prepayment;
        private final double freePrepayment;
        private final double marketValue;
        private final double positionPnl;
        private final double positionRatio;
        private final double positionPnlRate;

        OverviewValues(double prepayment,
                       double freePrepayment,
                       double marketValue,
                       double positionPnl,
                       double positionRatio,
                       double positionPnlRate) {
            this.prepayment = prepayment;
            this.freePrepayment = freePrepayment;
            this.marketValue = marketValue;
            this.positionPnl = positionPnl;
            this.positionRatio = positionRatio;
            this.positionPnlRate = positionPnlRate;
        }

        public double getPrepayment() {
            return prepayment;
        }

        public double getFreePrepayment() {
            return freePrepayment;
        }

        public double getMarketValue() {
            return marketValue;
        }

        public double getPositionPnl() {
            return positionPnl;
        }

        public double getPositionRatio() {
            return positionRatio;
        }

        public double getPositionPnlRate() {
            return positionPnlRate;
        }
    }
}

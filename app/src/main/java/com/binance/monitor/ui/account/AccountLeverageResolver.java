/*
 * 杠杆解析辅助类，统一处理页面展示、曲线计算和快照字段回退三种口径。
 * 供账户页标题、账户曲线和预加载快照构建复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.runtime.account.MetricNameTranslator;
import com.binance.monitor.domain.account.model.AccountMetric;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public final class AccountLeverageResolver {

    private AccountLeverageResolver() {
    }

    // 标题展示只显示真实杠杆，缺失时返回空字符串，避免误报 1x。
    public static String formatDisplayLeverage(@Nullable List<AccountMetric> metrics) {
        double leverage = resolveMetricLeverage(metrics);
        if (leverage <= 0d) {
            return "";
        }
        return formatLeverage(leverage);
    }

    // 判断指标列表里是否真的带了杠杆，供页面在轻快照缺字段时回退到上一轮已知值。
    public static boolean hasDisplayLeverage(@Nullable List<AccountMetric> metrics) {
        return resolveMetricLeverage(metrics) > 0d;
    }

    // 曲线计算仍保留 1 倍兜底，避免缺杠杆时除零。
    public static double resolveCurveLeverage(@Nullable List<AccountMetric> metrics) {
        double leverage = resolveMetricLeverage(metrics);
        return leverage > 0d ? leverage : 1d;
    }

    // 预加载快照优先读取 account，其次回退 accountMeta，兼容服务端不同落点。
    public static double resolveSnapshotLeverage(@Nullable JSONObject account,
                                                 @Nullable JSONObject accountMeta) {
        double leverage = optDoubleAny(account, "leverage", "lever");
        if (leverage > 0d) {
            return leverage;
        }
        return optDoubleAny(accountMeta, "leverage", "lever");
    }

    // 从账户指标中提取真实杠杆值，不做 1 倍兜底，供展示层判断是否要显示。
    static double resolveMetricLeverage(@Nullable List<AccountMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0d;
        }
        double best = 0d;
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String rawName = trim(metric.getName());
            String name = trim(MetricNameTranslator.toChinese(rawName));
            String normalizedName = (name + " " + rawName).toLowerCase(Locale.ROOT);
            boolean leverageField = normalizedName.contains("杠杆")
                    || normalizedName.contains("lever");
            if (!leverageField) {
                continue;
            }
            double parsed = parseLeverageNumber(metric.getValue());
            if (parsed > best) {
                best = parsed;
            }
        }
        return best;
    }

    // 杠杆文本可能混有 x、1:400 等片段，这里统一提取最大有效数字。
    static double parseLeverageNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0d;
        }
        String value = raw.replace(",", "");
        double max = 0d;
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                token.append(c);
            } else if (token.length() > 0) {
                max = Math.max(max, parseDouble(token.toString()));
                token.setLength(0);
            }
        }
        if (token.length() > 0) {
            max = Math.max(max, parseDouble(token.toString()));
        }
        return max;
    }

    // 杠杆展示统一补上 x，标题与概览口径保持一致。
    static String formatLeverage(double value) {
        if (Math.abs(value - Math.rint(value)) <= 1e-6) {
            return String.format(Locale.US, "%.0fx", value);
        }
        return String.format(Locale.US, "%.2fx", value);
    }

    // 统一兼容多种字段名，读取服务端快照中的杠杆字段。
    private static double optDoubleAny(@Nullable JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return 0d;
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty() || !object.has(key)) {
                continue;
            }
            Object value = object.opt(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String) {
                return parseDouble(((String) value).trim());
            }
        }
        return 0d;
    }

    // 解析失败时统一回 0，避免噪声文本中断整条链路。
    private static double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (Exception ignored) {
            return 0d;
        }
    }

    // 去掉前后空白，保持名称匹配稳定。
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

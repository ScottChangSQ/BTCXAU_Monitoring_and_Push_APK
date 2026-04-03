/*
 * 账户数值样式辅助，负责统一判断盈亏/收益类数值应显示为涨、跌还是中性。
 * 供账户统计页、持仓列表、交易列表和收益统计表复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import java.util.Locale;

public final class AccountValueStyleHelper {

    private static final double ZERO_EPSILON = 1e-9;

    public enum Direction {
        POSITIVE,
        NEGATIVE,
        NEUTRAL,
        NONE
    }

    private AccountValueStyleHelper() {
    }

    // 判断数值是否应视为 0，避免 0 值仍被误染成红绿。
    public static boolean isZero(double value) {
        return Math.abs(value) < ZERO_EPSILON;
    }

    // 直接按数值方向判断样式方向。
    public static Direction resolveNumericDirection(double value) {
        if (isZero(value)) {
            return Direction.NEUTRAL;
        }
        return value > 0d ? Direction.POSITIVE : Direction.NEGATIVE;
    }

    // 按指标标签和值文本判断方向，便于账户统计指标直接复用。
    public static Direction resolveMetricDirection(@Nullable String label, @Nullable String value) {
        if (!isProfitLikeLabel(label)) {
            return Direction.NONE;
        }
        Double parsed = parseSignedNumber(value);
        if (parsed == null) {
            return Direction.NONE;
        }
        return resolveNumericDirection(parsed);
    }

    // 识别是否属于盈亏/收益相关指标。
    private static boolean isProfitLikeLabel(@Nullable String label) {
        if (label == null) {
            return false;
        }
        String normalized = label.replace(" ", "");
        if (normalized.isEmpty()) {
            return false;
        }
        boolean matched = normalized.contains("盈亏")
                || normalized.contains("收益")
                || normalized.contains("利润")
                || normalized.contains("回撤")
                || normalized.contains("净值")
                || normalized.contains("结余")
                || normalized.contains("毛利")
                || normalized.contains("毛损")
                || normalized.contains("最好交易")
                || normalized.contains("最差交易")
                || normalized.contains("最大连续盈利")
                || normalized.contains("最大连续亏损");
        if (matched) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return lower.contains("consecutive") || lower.contains("streak");
    }

    // 从文本里提取第一个带符号或无符号数字，供收益类文本统一判断方向。
    @Nullable
    private static Double parseSignedNumber(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String safe = raw.trim();
        if (safe.isEmpty() || "--".equals(safe)) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean started = false;
        boolean dotUsed = false;
        for (int i = 0; i < safe.length(); i++) {
            char current = safe.charAt(i);
            if (!started && (current == '+' || current == '-')) {
                builder.append(current);
                started = true;
                continue;
            }
            if (Character.isDigit(current)) {
                builder.append(current);
                started = true;
                continue;
            }
            if (started && current == '.' && !dotUsed) {
                builder.append(current);
                dotUsed = true;
                continue;
            }
            if (started) {
                break;
            }
        }
        String token = builder.toString();
        if (token.isEmpty() || "+".equals(token) || "-".equals(token) || ".".equals(token)) {
            return null;
        }
        try {
            return Double.parseDouble(token);
        } catch (Exception ignored) {
            return null;
        }
    }
}

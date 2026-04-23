/*
 * 全局格式中心，统一金额、百分比、数量、价格与紧凑金额展示。
 */
package com.binance.monitor.ui.rules;

import androidx.annotation.NonNull;

import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.text.DecimalFormat;
import java.util.Locale;

public final class IndicatorFormatterCenter {
    private static final double ZERO_EPSILON = 1e-9;

    public enum SignPolicy {
        ALWAYS,
        NEGATIVE_ONLY
    }

    private IndicatorFormatterCenter() {
    }

    // 统一格式化金额，零值保留货币符号但不带正负号。
    @NonNull
    public static String formatMoney(double value, int precision, boolean masked) {
        return formatMoney(value, precision, masked, SignPolicy.ALWAYS);
    }

    // 统一格式化金额，并按指标语义控制是否展示正号。
    @NonNull
    public static String formatMoney(double value,
                                     int precision,
                                     boolean masked,
                                     @NonNull SignPolicy signPolicy) {
        if (masked) {
            return SensitiveDisplayMasker.MASK_TEXT;
        }
        String pattern = precision <= 0 ? "#,##0" : "#,##0." + repeat('0', precision);
        String amount = new DecimalFormat(pattern).format(Math.abs(value));
        if (Math.abs(value) < ZERO_EPSILON) {
            return "$" + amount;
        }
        if (value < 0d) {
            return "-$" + amount;
        }
        return signPolicy == SignPolicy.ALWAYS ? "+$" + amount : "$" + amount;
    }

    // 统一格式化百分比，入参按比值口径处理。
    @NonNull
    public static String formatPercent(double ratio, int precision, boolean showSign) {
        double percent = ratio * 100d;
        String pattern = precision <= 0 ? "0" : "0." + repeat('0', precision);
        String amount = new DecimalFormat(pattern).format(Math.abs(percent));
        if (Math.abs(percent) < ZERO_EPSILON) {
            return amount + "%";
        }
        if (percent > 0d) {
            return (showSign ? "+" : "") + amount + "%";
        }
        return "-" + amount + "%";
    }

    // 统一格式化数量。
    @NonNull
    public static String formatQuantity(double value, int precision, @NonNull String unit) {
        String pattern = precision <= 0 ? "0" : "0." + repeat('0', precision);
        return new DecimalFormat(pattern).format(Math.abs(value)) + unit;
    }

    // 统一格式化带方向的数量。
    @NonNull
    public static String formatSignedQuantity(double value, int precision, @NonNull String unit) {
        String sign = value >= 0d ? "+" : "-";
        return sign + formatQuantity(value, precision, unit);
    }

    // 统一格式化价格。
    @NonNull
    public static String formatPrice(double value, int precision, boolean masked) {
        if (masked) {
            return SensitiveDisplayMasker.MASK_TEXT;
        }
        String pattern = precision <= 0 ? "#,##0" : "#,##0." + repeat('0', precision);
        return "$" + new DecimalFormat(pattern).format(value);
    }

    // 统一格式化紧凑金额。
    @NonNull
    public static String formatCompactAmount(double value, boolean masked) {
        if (masked) {
            return SensitiveDisplayMasker.MASK_TEXT;
        }
        return FormatUtils.formatAmountWithChineseUnit(value);
    }

    // 统一格式化计数。
    @NonNull
    public static String formatCount(long count, @NonNull String unit) {
        return String.format(Locale.getDefault(), "%d%s", count, unit);
    }

    // 生成指定数量的小数字符串。
    @NonNull
    private static String repeat(char value, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}

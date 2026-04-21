/*
 * 账户数值样式辅助，负责统一判断盈亏/收益类数值应显示为涨、跌还是中性。
 * 供账户统计页、持仓列表、交易列表和收益统计表复用。
 */
package com.binance.monitor.ui.account;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;

import java.util.Locale;

public final class AccountValueStyleHelper {

    private static final double ZERO_EPSILON = 1e-9;

    public enum Direction {
        POSITIVE,
        NEGATIVE,
        NEUTRAL,
        NONE
    }

    public static final class NumericTokenRange {
        private final int start;
        private final int end;

        NumericTokenRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
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

    // 找出文本里第一个可着色的数值片段，供“字段文字 + 数字”混排场景复用。
    @Nullable
    public static NumericTokenRange findFirstNumericTokenRange(@Nullable String raw) {
        return findFirstNumericTokenRange(raw, 0);
    }

    // 找出指定锚点后的第一个数值片段，避免把字段文字本身一并染色。
    @Nullable
    public static NumericTokenRange findNumericTokenRangeAfterAnchor(@Nullable String raw,
                                                                     @Nullable String anchor) {
        if (raw == null || anchor == null || anchor.trim().isEmpty()) {
            return null;
        }
        int anchorIndex = raw.indexOf(anchor);
        if (anchorIndex < 0) {
            return null;
        }
        return findFirstNumericTokenRange(raw, anchorIndex + anchor.length());
    }

    // 在同一行里按指定数值文本定位目标片段，便于多处盈亏/收益值分别复用同一套红绿规则。
    @Nullable
    public static NumericTokenRange findNumericTokenRangeForExactToken(@Nullable String raw,
                                                                       @Nullable String token,
                                                                       boolean preferLastMatch) {
        if (raw == null || token == null || token.trim().isEmpty()) {
            return null;
        }
        int tokenIndex = preferLastMatch ? raw.lastIndexOf(token) : raw.indexOf(token);
        if (tokenIndex < 0) {
            return null;
        }
        NumericTokenRange tokenRange = findFirstNumericTokenRange(token, 0);
        if (tokenRange == null) {
            return null;
        }
        return new NumericTokenRange(
                tokenIndex + tokenRange.getStart(),
                tokenIndex + tokenRange.getEnd()
        );
    }

    // 指标值单独占一列时，只给数值片段着色，字段标签保持在左列。
    @NonNull
    public static CharSequence buildMetricValueSpan(@NonNull Context context,
                                                    @Nullable String label,
                                                    @Nullable String value) {
        return buildMetricValueSpan(context, label, value, R.color.text_primary);
    }

    // 指标值单独占一列时，只给数值片段着色，字段标签保持在左列。
    @NonNull
    public static CharSequence buildMetricValueSpan(@NonNull Context context,
                                                    @Nullable String label,
                                                    @Nullable String value,
                                                    int defaultColorRes) {
        String safeValue = value == null ? "" : value;
        Direction direction = resolveMetricDirection(label, safeValue);
        NumericTokenRange range = findFirstNumericTokenRange(safeValue);
        return buildDirectionalSpan(context, safeValue, range, direction, defaultColorRes);
    }

    // 行内摘要同时带字段文字与数值时，只给锚点后的目标数字着色。
    @NonNull
    public static CharSequence buildDirectionalSpanAfterAnchor(@NonNull Context context,
                                                               @Nullable String raw,
                                                               @Nullable String anchor,
                                                               @NonNull Direction direction,
                                                               int defaultColorRes) {
        String safeRaw = raw == null ? "" : raw;
        NumericTokenRange range = findNumericTokenRangeAfterAnchor(safeRaw, anchor);
        return buildDirectionalSpan(context, safeRaw, range, direction, defaultColorRes);
    }

    // 允许调用方在同一行文本里按具体数值片段继续上色，避免每个页面重复手写 span 范围。
    public static void applyDirectionalSpanForExactToken(@NonNull Spannable builder,
                                                         @NonNull Context context,
                                                         @Nullable String raw,
                                                         @Nullable String token,
                                                         @NonNull Direction direction,
                                                         int defaultColorRes,
                                                         boolean preferLastMatch) {
        String safeRaw = raw == null ? "" : raw;
        NumericTokenRange range = findNumericTokenRangeForExactToken(safeRaw, token, preferLastMatch);
        applyDirectionalSpan(builder, context, range, direction, defaultColorRes);
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
                || normalized.contains("最大连续亏损")
                || normalized.contains("每笔盈利")
                || normalized.contains("每笔亏损");
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
        NumericTokenRange range = findFirstNumericTokenRange(safe);
        if (range == null) {
            return null;
        }
        String token = normalizeNumericToken(safe.substring(range.getStart(), range.getEnd()));
        if (token.isEmpty() || "+".equals(token) || "-".equals(token) || ".".equals(token)) {
            return null;
        }
        try {
            return Double.parseDouble(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    // 忽略金额前缀里的货币符号和空白，兼容 +$123.45 这类展示文本。
    private static boolean isIgnoredNumberDecoration(char current) {
        return Character.isWhitespace(current)
                || current == '$'
                || current == '¥'
                || current == '￥'
                || current == '€'
                || current == '£'
                || current == ',';
    }

    @Nullable
    private static NumericTokenRange findFirstNumericTokenRange(@Nullable String raw, int startIndex) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        int safeStartIndex = Math.max(0, Math.min(startIndex, raw.length()));
        int tokenStart = -1;
        int tokenEnd = -1;
        boolean digitSeen = false;
        boolean dotUsed = false;
        for (int i = safeStartIndex; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if (tokenStart < 0) {
                if (Character.isWhitespace(current)) {
                    continue;
                }
                if (isNumberTokenStarter(current)) {
                    tokenStart = i;
                    if (Character.isDigit(current)) {
                        digitSeen = true;
                    } else if (current == '.') {
                        dotUsed = true;
                    }
                    tokenEnd = i + 1;
                }
                continue;
            }
            if (Character.isDigit(current)) {
                digitSeen = true;
                tokenEnd = i + 1;
                continue;
            }
            if (current == '.' && !dotUsed) {
                dotUsed = true;
                tokenEnd = i + 1;
                continue;
            }
            if (current == ',' && digitSeen) {
                tokenEnd = i + 1;
                continue;
            }
            if (!digitSeen && (isIgnoredNumberDecoration(current) || isSign(current))) {
                tokenEnd = i + 1;
                continue;
            }
            if (current == '%' && digitSeen) {
                tokenEnd = i + 1;
                continue;
            }
            break;
        }
        if (!digitSeen || tokenStart < 0 || tokenEnd <= tokenStart) {
            return null;
        }
        return new NumericTokenRange(tokenStart, tokenEnd);
    }

    private static boolean isNumberTokenStarter(char current) {
        return Character.isDigit(current)
                || current == '.'
                || isSign(current)
                || current == '$'
                || current == '¥'
                || current == '￥'
                || current == '€'
                || current == '£';
    }

    private static boolean isSign(char current) {
        return current == '+' || current == '-';
    }

    @NonNull
    private static String normalizeNumericToken(@NonNull String token) {
        StringBuilder builder = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char current = token.charAt(i);
            if (isIgnoredNumberDecoration(current) || current == '%') {
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    @NonNull
    private static CharSequence buildDirectionalSpan(@NonNull Context context,
                                                     @NonNull String raw,
                                                     @Nullable NumericTokenRange range,
                                                     @NonNull Direction direction,
                                                     int defaultColorRes) {
        SpannableString span = new SpannableString(raw);
        applyDirectionalSpan(span, context, range, direction, defaultColorRes);
        return span;
    }

    private static void applyDirectionalSpan(@NonNull android.text.Spannable builder,
                                             @NonNull Context context,
                                             @Nullable NumericTokenRange range,
                                             @NonNull Direction direction,
                                             int defaultColorRes) {
        if (range == null || direction == Direction.NONE || direction == Direction.NEUTRAL) {
            return;
        }
        builder.setSpan(new ForegroundColorSpan(resolveDirectionColor(context, direction, defaultColorRes)),
                range.getStart(),
                range.getEnd(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static int resolveDirectionColor(@NonNull Context context,
                                             @NonNull Direction direction,
                                             int defaultColorRes) {
        if (direction == Direction.POSITIVE) {
            return ContextCompat.getColor(context, R.color.pnl_profit);
        }
        if (direction == Direction.NEGATIVE) {
            return ContextCompat.getColor(context, R.color.pnl_loss);
        }
        return ContextCompat.getColor(context, defaultColorRes);
    }
}

/*
 * 指标展示策略，把定义、原始值、打码和颜色规则组装成页面可消费的单一结果。
 */
package com.binance.monitor.ui.rules;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;
import com.binance.monitor.runtime.account.MetricNameTranslator;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.Locale;

public final class IndicatorPresentationPolicy {
    private static final double ZERO_EPSILON = 1e-9;

    private IndicatorPresentationPolicy() {
    }

    // 按正式定义和数值生成最终展示对象。
    @NonNull
    public static IndicatorPresentation present(@NonNull IndicatorDefinition definition,
                                                double rawValue,
                                                boolean masked) {
        double normalizedValue = normalizeSignedMetricValue(definition, rawValue);
        String value = formatValue(definition, normalizedValue, masked);
        return new IndicatorPresentation(
                definition,
                definition.getDisplayName(),
                value,
                definition.getColorRule(),
                resolveDirection(definition.getColorRule(), normalizedValue),
                masked
        );
    }

    // 按正式 ID 和数值生成最终展示对象。
    @NonNull
    public static IndicatorPresentation present(@NonNull IndicatorId id,
                                                double rawValue,
                                                boolean masked) {
        return present(IndicatorRegistry.require(id), rawValue, masked);
    }

    // 按迁移期的页面标签和值生成展示对象，供旧页面接回规则中心。
    @NonNull
    public static IndicatorPresentation presentText(@Nullable String rawLabel,
                                                    @Nullable String rawValue,
                                                    boolean masked) {
        String label = canonicalLabel(rawLabel);
        IndicatorDefinition definition = IndicatorRegistry.findByDisplayName(label);
        String safeValue = sanitizeValue(rawValue);
        if (masked && !"--".equals(safeValue)) {
            return new IndicatorPresentation(
                    definition,
                    label,
                    SensitiveDisplayMasker.maskValue(safeValue, true),
                    definition == null ? IndicatorColorRule.NEUTRAL : definition.getColorRule(),
                    IndicatorPresentation.Direction.NONE,
                true
            );
        }
        Double parsedValue = parseSignedNumber(safeValue);
        Double normalizedValue = normalizeSignedTextValue(definition, parsedValue);
        String normalizedText = normalizeLegacySignedText(definition, safeValue, normalizedValue);
        return new IndicatorPresentation(
                definition,
                label,
                normalizedText,
                definition == null ? IndicatorColorRule.NEUTRAL : definition.getColorRule(),
                resolveDirection(definition == null ? IndicatorColorRule.NEUTRAL : definition.getColorRule(),
                        normalizedValue),
                false
        );
    }

    // 统一生成“值单独占一列”的红绿 span。
    @NonNull
    public static CharSequence buildValueSpan(@NonNull Context context,
                                              @Nullable String rawLabel,
                                              @Nullable String rawValue,
                                              int defaultColorRes) {
        IndicatorPresentation presentation = presentText(rawLabel, rawValue, false);
        NumericTokenRange range = findFirstNumericTokenRange(presentation.getFormattedValue(), 0);
        return buildDirectionalSpan(
                context,
                presentation.getFormattedValue(),
                range,
                presentation.getDirection(),
                defaultColorRes
        );
    }

    // 统一生成“字段 + 数值”混排文本里锚点后的红绿 span。
    @NonNull
    public static CharSequence buildDirectionalSpanAfterAnchor(@NonNull Context context,
                                                               @Nullable String raw,
                                                               @Nullable String anchor,
                                                               @NonNull IndicatorId id,
                                                               int defaultColorRes) {
        String safeRaw = sanitizeValue(raw);
        NumericTokenRange range = findNumericTokenRangeAfterAnchor(safeRaw, anchor);
        IndicatorDefinition definition = IndicatorRegistry.require(id);
        IndicatorPresentation.Direction direction = resolveDirection(
                definition.getColorRule(),
                normalizeSignedTextValue(definition, parseTokenValue(safeRaw, range))
        );
        return buildDirectionalSpan(context, safeRaw, range, direction, defaultColorRes);
    }

    // 统一对一段指定数值 token 上色，供同一行多值场景复用。
    public static void applyDirectionalSpanForExactToken(@NonNull Spannable builder,
                                                         @NonNull Context context,
                                                         @Nullable String raw,
                                                         @Nullable String token,
                                                         @NonNull IndicatorId id,
                                                         int defaultColorRes,
                                                         boolean preferLastMatch) {
        String safeRaw = sanitizeValue(raw);
        NumericTokenRange range = findNumericTokenRangeForExactToken(safeRaw, token, preferLastMatch);
        IndicatorDefinition definition = IndicatorRegistry.require(id);
        IndicatorPresentation.Direction direction = resolveDirection(
                definition.getColorRule(),
                normalizeSignedTextValue(definition, parseTokenValue(safeRaw, range))
        );
        applyDirectionalSpan(builder, context, range, direction, defaultColorRes);
    }

    // 统一按调用方给定的值区间着色，供同一行里“次数保留中性、金额或比例单独红绿”的场景复用。
    public static void applyDirectionalSpanForValueRange(@NonNull Spannable builder,
                                                         @NonNull Context context,
                                                         int start,
                                                         int end,
                                                         @NonNull IndicatorId id,
                                                         @Nullable String rawValue,
                                                         int defaultColorRes) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(builder.length(), end);
        if (safeEnd <= safeStart) {
            return;
        }
        IndicatorDefinition definition = IndicatorRegistry.require(id);
        IndicatorPresentation.Direction direction = resolveDirection(
                definition.getColorRule(),
                normalizeSignedTextValue(definition, parseSignedNumber(rawValue))
        );
        applyDirectionalSpan(
                builder,
                context,
                new NumericTokenRange(safeStart, safeEnd),
                direction,
                defaultColorRes
        );
    }

    // 统一输出一个标签的正式中文名。
    @NonNull
    public static String canonicalLabel(@Nullable String rawLabel) {
        String translated = MetricNameTranslator.toChinese(rawLabel);
        IndicatorDefinition definition = IndicatorRegistry.findByDisplayName(translated);
        return definition == null ? sanitizeLabel(translated) : definition.getDisplayName();
    }

    // 统一根据值类型格式化数值。
    @NonNull
    private static String formatValue(@NonNull IndicatorDefinition definition,
                                      double rawValue,
                                      boolean masked) {
        switch (definition.getValueType()) {
            case MONEY:
                return IndicatorFormatterCenter.formatMoney(rawValue, definition.getPrecision(), masked);
            case PERCENT:
                return IndicatorFormatterCenter.formatPercent(rawValue, definition.getPrecision(), true);
            case QUANTITY:
                return IndicatorFormatterCenter.formatQuantity(rawValue, definition.getPrecision(), definition.getUnit());
            case PRICE:
                return IndicatorFormatterCenter.formatPrice(rawValue, definition.getPrecision(), masked);
            case COUNT:
                return IndicatorFormatterCenter.formatCount(Math.round(rawValue), definition.getUnit());
            case TEXT:
            default:
                return masked ? SensitiveDisplayMasker.MASK_TEXT : String.valueOf(rawValue);
        }
    }

    // 对语义上必须显示为负值的指标做展示归一化，避免旧数据把回撤或亏损显示成正数。
    private static double normalizeSignedMetricValue(@NonNull IndicatorDefinition definition,
                                                     double rawValue) {
        if (!shouldForceNegativePresentation(definition)) {
            return rawValue;
        }
        return rawValue > 0d ? -rawValue : rawValue;
    }

    // 对旧页面字符串解析出的数值做同口径归一化，保证颜色判断与正式展示一致。
    @Nullable
    private static Double normalizeSignedTextValue(@Nullable IndicatorDefinition definition,
                                                   @Nullable Double parsedValue) {
        if (definition == null || parsedValue == null) {
            return parsedValue;
        }
        if (!shouldForceNegativePresentation(definition) || parsedValue <= 0d) {
            return parsedValue;
        }
        return -parsedValue;
    }

    // 对迁移期旧文本做符号归一化，仅在命中特定指标且旧文本方向错误时才重写展示值。
    @NonNull
    private static String normalizeLegacySignedText(@Nullable IndicatorDefinition definition,
                                                    @NonNull String rawValue,
                                                    @Nullable Double normalizedValue) {
        if (definition == null || normalizedValue == null) {
            return rawValue;
        }
        Double parsedValue = parseSignedNumber(rawValue);
        if (parsedValue == null || Double.compare(parsedValue, normalizedValue) == 0) {
            return rawValue;
        }
        double formatterValue = definition.getValueType() == IndicatorValueType.PERCENT
                ? normalizedValue / 100d
                : normalizedValue;
        return formatValue(definition, formatterValue, false);
    }

    // 标记那些业务语义上天然只能是负方向的指标。
    private static boolean shouldForceNegativePresentation(@NonNull IndicatorDefinition definition) {
        return definition.getId() == IndicatorId.ACCOUNT_MAX_DRAWDOWN
                || definition.getId() == IndicatorId.TRADE_AVG_LOSS;
    }

    // 统一把颜色规则和数值映射成展示方向。
    @NonNull
    private static IndicatorPresentation.Direction resolveDirection(@NonNull IndicatorColorRule colorRule,
                                                                    double value) {
        if (Math.abs(value) < ZERO_EPSILON) {
            return IndicatorPresentation.Direction.NEUTRAL;
        }
        if (colorRule == IndicatorColorRule.PROFIT_UP_LOSS_DOWN) {
            return value > 0d
                    ? IndicatorPresentation.Direction.POSITIVE
                    : IndicatorPresentation.Direction.NEGATIVE;
        }
        if (colorRule == IndicatorColorRule.LOSS_UP_PROFIT_DOWN) {
            return value > 0d
                    ? IndicatorPresentation.Direction.NEGATIVE
                    : IndicatorPresentation.Direction.POSITIVE;
        }
        return IndicatorPresentation.Direction.NONE;
    }

    // 统一把颜色规则和可空数值映射成展示方向。
    @NonNull
    private static IndicatorPresentation.Direction resolveDirection(@NonNull IndicatorColorRule colorRule,
                                                                    @Nullable Double value) {
        if (value == null) {
            return IndicatorPresentation.Direction.NONE;
        }
        return resolveDirection(colorRule, value.doubleValue());
    }

    // 统一规整标签空态。
    @NonNull
    private static String sanitizeLabel(@Nullable String rawLabel) {
        if (rawLabel == null || rawLabel.trim().isEmpty()) {
            return "--";
        }
        return rawLabel.trim();
    }

    // 统一规整值空态。
    @NonNull
    private static String sanitizeValue(@Nullable String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return "--";
        }
        return rawValue.trim();
    }

    // 从 token range 里解析实际数值。
    @Nullable
    private static Double parseTokenValue(@Nullable String raw, @Nullable NumericTokenRange range) {
        if (raw == null || range == null) {
            return null;
        }
        return parseSignedNumber(raw.substring(range.getStart(), range.getEnd()));
    }

    // 从文本里提取首个可判方向的数字。
    @Nullable
    private static Double parseSignedNumber(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        NumericTokenRange range = findFirstNumericTokenRange(raw, 0);
        if (range == null) {
            return null;
        }
        String normalized = normalizeNumericToken(raw.substring(range.getStart(), range.getEnd()));
        if (normalized.isEmpty() || "+".equals(normalized) || "-".equals(normalized)) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    // 找出给定文本里的第一个数值片段。
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
                    tokenEnd = i + 1;
                    if (Character.isDigit(current)) {
                        digitSeen = true;
                    } else if (current == '.') {
                        dotUsed = true;
                    }
                }
                continue;
            }
            if (Character.isDigit(current)) {
                tokenEnd = i + 1;
                digitSeen = true;
                continue;
            }
            if (current == '.' && !dotUsed) {
                tokenEnd = i + 1;
                dotUsed = true;
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

    // 找出锚点后的第一个数值片段。
    @Nullable
    private static NumericTokenRange findNumericTokenRangeAfterAnchor(@Nullable String raw,
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

    // 在整行文本里按指定 token 定位数值片段。
    @Nullable
    private static NumericTokenRange findNumericTokenRangeForExactToken(@Nullable String raw,
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

    // 判断字符是否可作为数字片段起点。
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

    // 判断字符是否属于正负号。
    private static boolean isSign(char current) {
        return current == '+' || current == '-';
    }

    // 判断字符是否可在数字前忽略。
    private static boolean isIgnoredNumberDecoration(char current) {
        return Character.isWhitespace(current)
                || current == '$'
                || current == '¥'
                || current == '￥'
                || current == '€'
                || current == '£'
                || current == ',';
    }

    // 规整数字 token，去掉货币符号和百分号。
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

    // 生成带方向着色的 span 文本。
    @NonNull
    private static CharSequence buildDirectionalSpan(@NonNull Context context,
                                                     @NonNull String raw,
                                                     @Nullable NumericTokenRange range,
                                                     @NonNull IndicatorPresentation.Direction direction,
                                                     int defaultColorRes) {
        SpannableString span = new SpannableString(raw);
        applyDirectionalSpan(span, context, range, direction, defaultColorRes);
        return span;
    }

    // 把颜色方向应用到指定文本范围。
    private static void applyDirectionalSpan(@NonNull Spannable builder,
                                             @NonNull Context context,
                                             @Nullable NumericTokenRange range,
                                             @NonNull IndicatorPresentation.Direction direction,
                                             int defaultColorRes) {
        if (range == null
                || direction == IndicatorPresentation.Direction.NONE
                || direction == IndicatorPresentation.Direction.NEUTRAL) {
            return;
        }
        builder.setSpan(
                new ForegroundColorSpan(resolveDirectionColor(context, direction, defaultColorRes)),
                range.getStart(),
                range.getEnd(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
    }

    // 把方向映射成 Android 颜色。
    private static int resolveDirectionColor(@NonNull Context context,
                                             @NonNull IndicatorPresentation.Direction direction,
                                             int defaultColorRes) {
        if (direction == IndicatorPresentation.Direction.POSITIVE) {
            return ContextCompat.getColor(context, R.color.pnl_profit);
        }
        if (direction == IndicatorPresentation.Direction.NEGATIVE) {
            return ContextCompat.getColor(context, R.color.pnl_loss);
        }
        return ContextCompat.getColor(context, defaultColorRes);
    }

    // 数字范围对象仅服务规则中心内部实现。
    private static final class NumericTokenRange {
        private final int start;
        private final int end;

        private NumericTokenRange(int start, int end) {
            this.start = start;
            this.end = end;
        }

        private int getStart() {
            return start;
        }

        private int getEnd() {
            return end;
        }
    }
}

/*
 * 统计指标视图绑定工具，统一处理中文名、隐私打码和收益颜色。
 * 供单列统计列表与核心统计展开区的双列行复用，避免两套显示逻辑分叉。
 */
package com.binance.monitor.ui.account.adapter;

import android.content.Context;
import android.text.SpannableString;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.runtime.account.MetricNameTranslator;
import com.binance.monitor.ui.account.TradeStatsMetricStyleHelper;
import com.binance.monitor.ui.rules.IndicatorId;
import com.binance.monitor.ui.rules.IndicatorPresentation;
import com.binance.monitor.ui.rules.IndicatorPresentationPolicy;
import com.binance.monitor.util.SensitiveDisplayMasker;

final class StatsMetricViewBinder {

    private StatsMetricViewBinder() {
    }

    // 把统计项绑定到“名称 + 数值”视图对，并复用统一的颜色与打码规则。
    static void bind(@NonNull TextView nameView,
                     @NonNull TextView valueView,
                     @Nullable AccountMetric item,
                     boolean masked) {
        if (item == null) {
            nameView.setText("--");
            valueView.setText("--");
            valueView.setTextColor(ContextCompat.getColor(valueView.getContext(), R.color.text_primary));
            return;
        }
        String nameCn = MetricNameTranslator.toChinese(item.getName());
        String value = masked
                ? SensitiveDisplayMasker.maskValue(item.getValue(), true)
                : item.getValue();
        IndicatorPresentation presentation = IndicatorPresentationPolicy.presentText(nameCn, item.getValue(), masked);
        nameView.setText(presentation.getLabel());
        if (masked) {
            valueView.setText(presentation.getFormattedValue());
            valueView.setTextColor(ContextCompat.getColor(valueView.getContext(), R.color.text_primary));
            return;
        }
        if (isStreakMetric(presentation.getLabel())) {
            valueView.setText(buildStreakSpan(valueView.getContext(), presentation.getLabel(), value));
            valueView.setTextColor(ContextCompat.getColor(valueView.getContext(), R.color.text_primary));
            return;
        }
        if (isTradeRatioMetric(presentation.getLabel())) {
            valueView.setText(buildTradeRatioSpan(valueView.getContext(), presentation.getLabel(), value));
            valueView.setTextColor(ContextCompat.getColor(valueView.getContext(), R.color.text_primary));
            return;
        }
        if (isGrossPairMetric(presentation.getLabel())) {
            valueView.setText(buildGrossPairSpan(valueView.getContext(), value));
            valueView.setTextColor(ContextCompat.getColor(valueView.getContext(), R.color.text_primary));
            return;
        }
        valueView.setText(IndicatorPresentationPolicy.buildValueSpan(
                valueView.getContext(),
                presentation.getLabel(),
                value,
                R.color.text_primary
        ));
        valueView.setTextColor(ContextCompat.getColor(valueView.getContext(), R.color.text_primary));
    }

    private static boolean isStreakMetric(@Nullable String label) {
        if (label == null) {
            return false;
        }
        String normalized = label.replace(" ", "");
        return normalized.contains("最大连续盈利") || normalized.contains("最大连续亏损");
    }

    private static boolean isTradeRatioMetric(@Nullable String label) {
        if (label == null) {
            return false;
        }
        String normalized = label.replace(" ", "");
        return normalized.contains("盈利交易") || normalized.contains("亏损交易");
    }

    private static boolean isGrossPairMetric(@Nullable String label) {
        if (label == null) {
            return false;
        }
        return label.replace(" ", "").contains("毛利/毛损");
    }

    private static CharSequence buildStreakSpan(@NonNull Context context,
                                                @Nullable String label,
                                                @Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "--";
        }
        SpannableString span = new SpannableString(raw);
        int tintStart = TradeStatsMetricStyleHelper.resolveStreakTintStart(label, raw);
        if (tintStart >= raw.length()) {
            return span;
        }
        IndicatorPresentationPolicy.applyDirectionalSpanForValueRange(
                span,
                context,
                tintStart,
                raw.length(),
                IndicatorId.ACCOUNT_POSITION_PNL,
                raw.substring(tintStart),
                R.color.text_primary
        );
        return span;
    }

    private static CharSequence buildTradeRatioSpan(@NonNull Context context,
                                                    @Nullable String label,
                                                    @Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "--";
        }
        SpannableString span = new SpannableString(raw);
        int divider = raw.indexOf(") ");
        if (divider < 0 || divider >= raw.length() - 2) {
            return span;
        }
        IndicatorPresentationPolicy.applyDirectionalSpanForValueRange(
                span,
                context,
                divider + 2,
                raw.length(),
                resolveTradeRatioIndicatorId(label),
                raw.substring(divider + 2),
                R.color.text_primary
        );
        return span;
    }

    private static CharSequence buildGrossPairSpan(@NonNull Context context, @Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "--";
        }
        SpannableString span = new SpannableString(raw);
        int divider = raw.indexOf(" / ");
        if (divider <= 0 || divider >= raw.length() - 3) {
            return span;
        }
        IndicatorPresentationPolicy.applyDirectionalSpanForValueRange(
                span,
                context,
                0,
                divider,
                IndicatorId.ACCOUNT_POSITION_PNL,
                raw.substring(0, divider),
                R.color.text_primary
        );
        IndicatorPresentationPolicy.applyDirectionalSpanForValueRange(
                span,
                context,
                divider + 3,
                raw.length(),
                IndicatorId.ACCOUNT_POSITION_PNL,
                raw.substring(divider + 3),
                R.color.text_primary
        );
        return span;
    }

    @NonNull
    private static IndicatorId resolveTradeRatioIndicatorId(@Nullable String label) {
        return label != null && label.contains("亏损")
                ? IndicatorId.TRADE_AVG_LOSS
                : IndicatorId.TRADE_AVG_PROFIT;
    }

}

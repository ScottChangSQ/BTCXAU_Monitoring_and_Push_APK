/*
 * 指标展示结果对象，页面只消费这里给出的标题、值和颜色方向。
 */
package com.binance.monitor.ui.rules;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;

public final class IndicatorPresentation {

    public enum Direction {
        POSITIVE,
        NEGATIVE,
        NEUTRAL,
        NONE
    }

    @Nullable
    private final IndicatorDefinition definition;
    private final String label;
    private final String formattedValue;
    private final IndicatorColorRule colorRule;
    private final Direction direction;
    private final boolean masked;

    // 构建页面最终消费的展示对象。
    public IndicatorPresentation(@Nullable IndicatorDefinition definition,
                                 @NonNull String label,
                                 @NonNull String formattedValue,
                                 @NonNull IndicatorColorRule colorRule,
                                 @NonNull Direction direction,
                                 boolean masked) {
        this.definition = definition;
        this.label = label;
        this.formattedValue = formattedValue;
        this.colorRule = colorRule;
        this.direction = direction;
        this.masked = masked;
    }

    // 返回原始指标定义。
    @Nullable
    public IndicatorDefinition getDefinition() {
        return definition;
    }

    // 返回正式标签。
    @NonNull
    public String getLabel() {
        return label;
    }

    // 返回格式化后的展示值。
    @NonNull
    public String getFormattedValue() {
        return formattedValue;
    }

    // 返回该展示值对应的颜色规则。
    @NonNull
    public IndicatorColorRule getColorRule() {
        return colorRule;
    }

    // 返回该展示值的方向。
    @NonNull
    public Direction getDirection() {
        return direction;
    }

    // 返回是否隐私打码。
    public boolean isMasked() {
        return masked;
    }

    // 统一把规则方向映射成 Android 颜色。
    @ColorInt
    public int resolveAndroidColor(@NonNull Context context) {
        return resolveAndroidColor(context, R.color.text_primary);
    }

    // 统一把规则方向映射成 Android 颜色，并允许调用方传默认色。
    @ColorInt
    public int resolveAndroidColor(@NonNull Context context, int defaultColorRes) {
        if (direction == Direction.POSITIVE) {
            return ContextCompat.getColor(context, R.color.pnl_profit);
        }
        if (direction == Direction.NEGATIVE) {
            return ContextCompat.getColor(context, R.color.pnl_loss);
        }
        return ContextCompat.getColor(context, defaultColorRes);
    }
}

/*
 * 从 TextAppearance 读取统一字号，供自定义 View 与 Paint 复用，避免局部硬编码。
 */
package com.binance.monitor.ui.theme;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.widget.TextView;

import androidx.annotation.StyleRes;
import androidx.core.widget.TextViewCompat;

public final class TextAppearanceScaleResolver {

    private TextAppearanceScaleResolver() {
    }

    public static float resolveTextSizePx(Context context, @StyleRes int textAppearanceResId) {
        TypedArray typedArray = context.obtainStyledAttributes(
                textAppearanceResId,
                new int[]{android.R.attr.textSize}
        );
        try {
            return typedArray.getDimension(0, 0f);
        } finally {
            typedArray.recycle();
        }
    }

    public static float resolveTextSizeSp(Context context, @StyleRes int textAppearanceResId) {
        float textSizePx = resolveTextSizePx(context, textAppearanceResId);
        return textSizePx / context.getResources().getDisplayMetrics().scaledDensity;
    }

    public static void applyTextAppearance(TextView textView, @StyleRes int textAppearanceResId) {
        TextViewCompat.setTextAppearance(textView, textAppearanceResId);
    }

    public static void applyTextSize(Paint paint, Context context, @StyleRes int textAppearanceResId) {
        paint.setTextSize(resolveTextSizePx(context, textAppearanceResId));
    }
}

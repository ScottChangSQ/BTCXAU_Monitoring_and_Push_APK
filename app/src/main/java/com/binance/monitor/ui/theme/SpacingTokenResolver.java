/*
 * 统一运行时尺寸解析入口，负责把语义 spacing token 转成运行时像素值。
 */
package com.binance.monitor.ui.theme;

import android.content.Context;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;

import com.binance.monitor.R;

public final class SpacingTokenResolver {

    private SpacingTokenResolver() {
    }

    // 解析任意 spacing dimen 资源为像素值。
    public static int px(@NonNull Context context, @DimenRes int dimenResId) {
        return context.getResources().getDimensionPixelSize(dimenResId);
    }

    // 解析任意 spacing dimen 资源为绘制用浮点值。
    public static float dpFloat(@NonNull Context context, @DimenRes int dimenResId) {
        return context.getResources().getDimension(dimenResId);
    }

    // 页面/抽屉与屏幕边缘距离。
    public static int screenEdgePx(@NonNull Context context) {
        return px(context, R.dimen.screen_edge_padding);
    }

    // 常规行间距。
    public static int rowGapPx(@NonNull Context context) {
        return px(context, R.dimen.row_gap);
    }

    // 紧凑行间距。
    public static int rowGapCompactPx(@NonNull Context context) {
        return px(context, R.dimen.row_gap_compact);
    }

    // 常规横向控件间距。
    public static int inlineGapPx(@NonNull Context context) {
        return px(context, R.dimen.inline_gap);
    }

    // 紧凑横向控件间距。
    public static int inlineGapCompactPx(@NonNull Context context) {
        return px(context, R.dimen.inline_gap_compact);
    }

    // 常规字段左右内边距。
    public static int fieldPaddingPx(@NonNull Context context) {
        return px(context, R.dimen.field_padding_x);
    }

    // 紧凑字段左右内边距。
    public static int fieldPaddingCompactPx(@NonNull Context context) {
        return px(context, R.dimen.field_padding_x_compact);
    }

    // 常规字段尾部预留。
    public static int fieldTrailingReservePx(@NonNull Context context) {
        return px(context, R.dimen.field_trailing_reserve);
    }

    // 紧凑字段尾部预留。
    public static int fieldTrailingReserveCompactPx(@NonNull Context context) {
        return px(context, R.dimen.field_trailing_reserve_compact);
    }
}

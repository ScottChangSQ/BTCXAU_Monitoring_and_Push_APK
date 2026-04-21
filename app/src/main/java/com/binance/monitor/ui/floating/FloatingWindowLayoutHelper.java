/*
 * 悬浮窗产品行布局辅助类，统一管理展开态宽度、列宽和左右留白。
 * 让盈亏列、价格列与整体边距保持稳定，避免每次微调都散落在视图代码里。
 */
package com.binance.monitor.ui.floating;

import android.view.Gravity;

import androidx.annotation.DimenRes;

import com.binance.monitor.R;

final class FloatingWindowLayoutHelper {

    private FloatingWindowLayoutHelper() {
    }

    @DimenRes
    static int resolveExpandedWidthRes() {
        return R.dimen.floating_window_expanded_width;
    }

    @DimenRes
    static int resolveHorizontalPaddingRes() {
        return R.dimen.floating_window_padding_x;
    }

    @DimenRes
    static int resolveTrailingInsetRes() {
        return R.dimen.floating_window_trailing_inset;
    }

    static int resolveSymbolHeaderGravity() {
        return Gravity.START | Gravity.CENTER_VERTICAL;
    }

    static int resolveSymbolTextGravity() {
        return Gravity.START | Gravity.CENTER_VERTICAL;
    }

    @DimenRes
    static int resolveMinimizeButtonSizeRes() {
        return R.dimen.floating_window_minimize_button_size;
    }

    @DimenRes
    static int resolveMiniMinWidthRes() {
        return R.dimen.floating_window_mini_min_width;
    }

    @DimenRes
    static int resolveMiniHorizontalPaddingRes() {
        return R.dimen.floating_window_mini_padding_x;
    }

    @DimenRes
    static int resolveMiniEndMarginRes() {
        return R.dimen.floating_window_mini_end_margin;
    }

    @DimenRes
    static int resolveAmountRowGapRes() {
        return R.dimen.floating_window_amount_row_gap;
    }
}

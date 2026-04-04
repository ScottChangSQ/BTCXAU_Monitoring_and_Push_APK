/*
 * 悬浮窗产品行布局辅助类，统一管理展开态宽度、列宽和左右留白。
 * 让盈亏列、价格列与整体边距保持稳定，避免每次微调都散落在视图代码里。
 */
package com.binance.monitor.ui.floating;

import android.view.Gravity;

final class FloatingWindowLayoutHelper {

    private static final int EXPANDED_WIDTH_DP = 90;
    private static final int HORIZONTAL_PADDING_DP = 4;
    private static final int TRAILING_INSET_DP = 0;
    private static final int MINIMIZE_BUTTON_SIZE_DP = 14;
    private static final int MINI_MIN_WIDTH_DP = 30;
    private static final int MINI_HORIZONTAL_PADDING_DP = 6;
    private static final int MINI_END_MARGIN_DP = 4;

    private FloatingWindowLayoutHelper() {
    }

    static int resolveExpandedWidthDp() {
        return EXPANDED_WIDTH_DP;
    }

    static int resolveExpandedContentWidthDp() {
        return EXPANDED_WIDTH_DP - HORIZONTAL_PADDING_DP * 2;
    }

    static int resolveHorizontalPaddingDp() {
        return HORIZONTAL_PADDING_DP;
    }

    static int resolveTrailingInsetDp() {
        return TRAILING_INSET_DP;
    }

    static int resolveValueRowWidthDp() {
        return resolveExpandedContentWidthDp() - TRAILING_INSET_DP;
    }

    static int resolveSymbolHeaderGravity() {
        return Gravity.START | Gravity.CENTER_VERTICAL;
    }

    static int resolveSymbolTextGravity() {
        return Gravity.START | Gravity.CENTER_VERTICAL;
    }

    static int resolveMinimizeButtonSizeDp() {
        return MINIMIZE_BUTTON_SIZE_DP;
    }

    static int resolveMiniMinWidthDp() {
        return MINI_MIN_WIDTH_DP;
    }

    static int resolveMiniHorizontalPaddingDp() {
        return MINI_HORIZONTAL_PADDING_DP;
    }

    static int resolveMiniEndMarginDp() {
        return MINI_END_MARGIN_DP;
    }
}

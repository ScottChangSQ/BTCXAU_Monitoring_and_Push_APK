/*
 * 悬浮窗产品行布局辅助类，统一管理展开态宽度、列宽和左右留白。
 * 让盈亏列、价格列与整体边距保持稳定，避免每次微调都散落在视图代码里。
 */
package com.binance.monitor.ui.floating;

final class FloatingWindowLayoutHelper {

    private static final int EXPANDED_WIDTH_DP = 110;
    private static final int HORIZONTAL_PADDING_DP = 5;
    private static final int TRAILING_INSET_DP = 4;
    private static final int SYMBOL_LABEL_COLUMN_WIDTH_DP = 28;
    private static final int PNL_COLUMN_WIDTH_DP = 64;

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

    static int resolveSymbolLabelColumnWidthDp() {
        return SYMBOL_LABEL_COLUMN_WIDTH_DP;
    }

    static int resolvePnlColumnWidthDp() {
        return PNL_COLUMN_WIDTH_DP;
    }

    static int resolveValueRowWidthDp() {
        return resolveExpandedContentWidthDp() - TRAILING_INSET_DP;
    }
}

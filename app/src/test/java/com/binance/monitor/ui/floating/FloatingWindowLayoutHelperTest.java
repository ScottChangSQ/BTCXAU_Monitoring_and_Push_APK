/*
 * 悬浮窗布局辅助逻辑测试，确保产品行左右留白和列宽关系保持一致。
 */
package com.binance.monitor.ui.floating;

import android.view.Gravity;

import com.binance.monitor.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FloatingWindowLayoutHelperTest {

    @Test
    public void expandedLayoutShouldUseSharedGeometryTokens() {
        assertEquals(R.dimen.floating_window_expanded_width, FloatingWindowLayoutHelper.resolveExpandedWidthRes());
        assertEquals(R.dimen.floating_window_padding_x, FloatingWindowLayoutHelper.resolveHorizontalPaddingRes());
        assertEquals(R.dimen.floating_window_trailing_inset, FloatingWindowLayoutHelper.resolveTrailingInsetRes());
    }

    @Test
    public void symbolCardContentShouldUseStartAlignedGravity() {
        assertEquals(Gravity.START | Gravity.CENTER_VERTICAL,
                FloatingWindowLayoutHelper.resolveSymbolHeaderGravity());
        assertEquals(Gravity.START | Gravity.CENTER_VERTICAL,
                FloatingWindowLayoutHelper.resolveSymbolTextGravity());
    }

    @Test
    public void minimizedLayoutShouldUseSmallerTrailingSpace() {
        assertEquals(R.dimen.floating_window_minimize_button_size, FloatingWindowLayoutHelper.resolveMinimizeButtonSizeRes());
        assertEquals(R.dimen.floating_window_mini_min_width, FloatingWindowLayoutHelper.resolveMiniMinWidthRes());
        assertEquals(R.dimen.floating_window_mini_padding_x, FloatingWindowLayoutHelper.resolveMiniHorizontalPaddingRes());
        assertEquals(R.dimen.floating_window_mini_end_margin, FloatingWindowLayoutHelper.resolveMiniEndMarginRes());
    }

    @Test
    public void amountRowShouldUseTighterDedicatedGapToken() {
        assertEquals(R.dimen.floating_window_amount_row_gap, FloatingWindowLayoutHelper.resolveAmountRowGapRes());
    }
}

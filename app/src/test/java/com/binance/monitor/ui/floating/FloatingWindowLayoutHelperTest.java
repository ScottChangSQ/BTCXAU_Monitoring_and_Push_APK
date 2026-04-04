/*
 * 悬浮窗布局辅助逻辑测试，确保产品行左右留白和列宽关系保持一致。
 */
package com.binance.monitor.ui.floating;

import android.view.Gravity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FloatingWindowLayoutHelperTest {

    @Test
    public void expandedLayoutShouldUseTighterWidthPreset() {
        assertEquals(90, FloatingWindowLayoutHelper.resolveExpandedWidthDp());
        assertEquals(82, FloatingWindowLayoutHelper.resolveExpandedContentWidthDp());
        assertEquals(0, FloatingWindowLayoutHelper.resolveTrailingInsetDp());
    }

    @Test
    public void valueRowsShouldFillContentWidth() {
        assertEquals(82, FloatingWindowLayoutHelper.resolveValueRowWidthDp());
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
        assertEquals(14, FloatingWindowLayoutHelper.resolveMinimizeButtonSizeDp());
        assertEquals(30, FloatingWindowLayoutHelper.resolveMiniMinWidthDp());
        assertEquals(6, FloatingWindowLayoutHelper.resolveMiniHorizontalPaddingDp());
        assertEquals(4, FloatingWindowLayoutHelper.resolveMiniEndMarginDp());
    }
}

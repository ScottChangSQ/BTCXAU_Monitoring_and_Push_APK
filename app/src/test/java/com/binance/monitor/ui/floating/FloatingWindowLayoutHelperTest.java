/*
 * 悬浮窗布局辅助逻辑测试，确保产品行左右留白和列宽关系保持一致。
 */
package com.binance.monitor.ui.floating;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FloatingWindowLayoutHelperTest {

    @Test
    public void expandedLayoutShouldUseTighterWidthPreset() {
        assertEquals(104, FloatingWindowLayoutHelper.resolveExpandedWidthDp());
        assertEquals(92, FloatingWindowLayoutHelper.resolveExpandedContentWidthDp());
        assertEquals(0, FloatingWindowLayoutHelper.resolveTrailingInsetDp());
    }

    @Test
    public void valueRowsShouldFillContentWidth() {
        assertEquals(92, FloatingWindowLayoutHelper.resolveValueRowWidthDp());
    }
}

/*
 * 悬浮窗布局辅助逻辑测试，确保产品行左右留白和列宽关系保持一致。
 */
package com.binance.monitor.ui.floating;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FloatingWindowLayoutHelperTest {

    @Test
    public void expandedLayoutShouldUseTighterWidthPreset() {
        assertEquals(110, FloatingWindowLayoutHelper.resolveExpandedWidthDp());
        assertEquals(100, FloatingWindowLayoutHelper.resolveExpandedContentWidthDp());
        assertEquals(4, FloatingWindowLayoutHelper.resolveTrailingInsetDp());
    }

    @Test
    public void pnlAndValueRowsShouldUseTighterColumns() {
        assertEquals(28, FloatingWindowLayoutHelper.resolveSymbolLabelColumnWidthDp());
        assertEquals(64, FloatingWindowLayoutHelper.resolvePnlColumnWidthDp());
        assertEquals(96, FloatingWindowLayoutHelper.resolveValueRowWidthDp());
    }
}

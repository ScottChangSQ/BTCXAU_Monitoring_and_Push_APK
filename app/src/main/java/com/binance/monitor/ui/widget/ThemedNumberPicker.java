/*
 * 可主题化的数字滚轮，作为 PickerWheel 的兼容包装层，负责把宿主注入的文字主题稳定应用到 NumberPicker。
 * 供账户统计页的日期选择面板使用。
 */
package com.binance.monitor.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.NumberPicker;

import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;

import com.binance.monitor.ui.theme.UiPaletteManager;

public class ThemedNumberPicker extends NumberPicker {

    private int themedTextColor;
    private int themedTextAppearanceResId;

    public ThemedNumberPicker(Context context) {
        this(context, null);
    }

    public ThemedNumberPicker(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThemedNumberPicker(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setVerticalFadingEdgeEnabled(false);
        setFadingEdgeLength(0);
        setAlpha(1f);
    }

    // 宿主在主题切换后注入滚轮文字样式。
    public void applyThemeTextStyle(int textColor, @StyleRes int textAppearanceResId) {
        themedTextColor = textColor;
        themedTextAppearanceResId = textAppearanceResId;
        applyThemeTextStyleInternal();
        invalidate();
        postInvalidate();
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        applyThemeTextStyleInternal();
        super.dispatchDraw(canvas);
    }

    // 每次绘制前都重新覆盖内部画笔，避免 OEM 在滚动中恢复默认淡化色。
    private void applyThemeTextStyleInternal() {
        UiPaletteManager.applyPickerWheelTextStyle(this, themedTextColor, themedTextAppearanceResId);
    }
}

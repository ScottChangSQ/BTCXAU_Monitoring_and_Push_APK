/*
 * 可主题化的数字滚轮，负责稳定覆盖 NumberPicker 未选中项的系统淡化颜色。
 * 供账户统计页的日期选择面板使用。
 */
package com.binance.monitor.ui.widget;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.NumberPicker;

import androidx.annotation.Nullable;

public class ThemedNumberPicker extends NumberPicker {

    private int themedTextColor;
    private float themedTextSizePx;

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
    public void applyThemeTextStyle(int textColor, float textSizePx) {
        themedTextColor = textColor;
        themedTextSizePx = textSizePx;
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
        try {
            java.lang.reflect.Field selectorWheelPaintField =
                    NumberPicker.class.getDeclaredField("mSelectorWheelPaint");
            selectorWheelPaintField.setAccessible(true);
            Paint paint = (Paint) selectorWheelPaintField.get(this);
            if (paint != null) {
                paint.setColor(themedTextColor);
                paint.setAlpha(255);
                if (themedTextSizePx > 0f) {
                    paint.setTextSize(themedTextSizePx);
                }
            }
        } catch (Exception ignored) {
        }
        for (int index = 0; index < getChildCount(); index++) {
            android.view.View child = getChildAt(index);
            if (child instanceof EditText) {
                EditText editText = (EditText) child;
                editText.setTextColor(themedTextColor);
                editText.setHintTextColor(themedTextColor);
                editText.setAlpha(1f);
                if (themedTextSizePx > 0f) {
                    editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, themedTextSizePx);
                }
            }
        }
    }
}

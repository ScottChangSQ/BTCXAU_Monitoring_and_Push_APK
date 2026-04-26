/*
 * 交易盈亏柱状图，负责展示不同品种在当前筛选条件下的总盈亏。
 * 供 AccountStatsBridgeActivity 的交易统计区直接绘制使用。
 */
package com.binance.monitor.ui.account;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.binance.monitor.R;
import com.binance.monitor.ui.theme.TextAppearanceScaleResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TradePnlBarChartView extends View {

    public static class Entry {
        public final String code;
        public final double pnl;

        public Entry(String code, double pnl) {
            this.code = code;
            this.pnl = pnl;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint positivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint negativePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<BarDrawItem> drawItems = new ArrayList<>();
    private float preparedLeft;
    private float preparedRight;
    private float preparedChartTop;
    private float preparedAxisBottom;
    private float preparedZeroY;
    private boolean masked;

    public TradePnlBarChartView(Context context) {
        this(context, null);
    }

    public TradePnlBarChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TradePnlBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TextAppearanceScaleResolver.applyTextSize(labelPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        TextAppearanceScaleResolver.applyTextSize(valuePaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        valuePaint.setTextAlign(Paint.Align.CENTER);

        TextAppearanceScaleResolver.applyTextSize(emptyPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        refreshPalette();
    }

    // 刷新主题色，保证账户/分析页切换主题后柱状图同步更新。
    public void refreshPalette() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());
        axisPaint.setColor(applyAlpha(palette.textSecondary, 185));
        axisPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(applyAlpha(palette.stroke, 150));
        gridPaint.setStrokeWidth(dp(0.8f));
        labelPaint.setColor(palette.textSecondary);
        valuePaint.setColor(palette.textPrimary);
        positivePaint.setColor(applyAlpha(palette.rise, 220));
        negativePaint.setColor(applyAlpha(palette.fall, 220));
        emptyPaint.setColor(palette.textSecondary);
        invalidate();
    }

    public void setEntries(@Nullable List<Entry> source) {
        entries.clear();
        if (source != null) {
            entries.addAll(source);
        }
        rebuildDrawItems();
        invalidate();
    }

    // 根据隐私状态切换为占位态。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        rebuildDrawItems();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildDrawItems();
    }

    private void rebuildDrawItems() {
        drawItems.clear();
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f || masked || entries.isEmpty()) {
            return;
        }
        preparedLeft = dp(22f);
        preparedRight = width - dp(16f);
        preparedChartTop = dp(30f);
        preparedAxisBottom = height - dp(40f);
        float codeBaseline = height - dp(10f);
        float negativeLabelBaseline = preparedAxisBottom + dp(18f);

        boolean hasPositive = false;
        boolean hasNegative = false;
        double positiveMax = 0d;
        double negativeMin = 0d;
        for (Entry entry : entries) {
            if (entry.pnl > 0d) {
                hasPositive = true;
                positiveMax = Math.max(positiveMax, entry.pnl);
            } else if (entry.pnl < 0d) {
                hasNegative = true;
                negativeMin = Math.min(negativeMin, entry.pnl);
            }
        }
        if (hasPositive && hasNegative) {
            double range = Math.max(1e-9, positiveMax - negativeMin);
            preparedZeroY = (float) (preparedChartTop + (positiveMax / range) * (preparedAxisBottom - preparedChartTop));
        } else if (hasPositive) {
            preparedZeroY = preparedAxisBottom - dp(10f);
            positiveMax = Math.max(positiveMax, 1d);
        } else if (hasNegative) {
            preparedZeroY = preparedChartTop + dp(12f);
            negativeMin = Math.min(negativeMin, -1d);
        } else {
            preparedZeroY = preparedChartTop + (preparedAxisBottom - preparedChartTop) / 2f;
        }

        float slotWidth = (preparedRight - preparedLeft) / entries.size();
        float barWidth = Math.max(dp(10f), slotWidth * 0.4f);
        float positiveHeight = Math.max(dp(1f), preparedZeroY - preparedChartTop - dp(4f));
        float negativeHeight = Math.max(dp(1f), preparedAxisBottom - preparedZeroY - dp(12f));
        double positiveBase = Math.max(1d, positiveMax);
        double negativeBase = Math.max(1d, Math.abs(negativeMin));

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float centerX = preparedLeft + slotWidth * i + slotWidth / 2f;
            boolean positive = entry.pnl >= 0d;
            RectF rect;
            float valueY;
            if (positive) {
                float ratio = (float) (Math.abs(entry.pnl) / positiveBase);
                float barHeight = positiveHeight * ratio;
                rect = new RectF(centerX - barWidth / 2f, preparedZeroY - barHeight, centerX + barWidth / 2f, preparedZeroY);
                valueY = Math.max(dp(18f), rect.top - dp(10f));
            } else {
                float ratio = (float) (Math.abs(entry.pnl) / negativeBase);
                float barHeight = negativeHeight * ratio;
                rect = new RectF(centerX - barWidth / 2f, preparedZeroY, centerX + barWidth / 2f, preparedZeroY + barHeight);
                valueY = Math.max(negativeLabelBaseline, rect.bottom + dp(14f));
                valueY = Math.min(codeBaseline - dp(10f), valueY);
            }
            drawItems.add(new BarDrawItem(rect, positive, formatPnl(entry.pnl), entry.code, centerX, valueY, codeBaseline));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        if (masked) {
            canvas.drawText("****", width / 2f, height / 2f, emptyPaint);
            return;
        }

        if (entries.isEmpty()) {
            canvas.drawText("暂无柱状统计数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        if (drawItems.isEmpty()) {
            rebuildDrawItems();
        }
        canvas.drawLine(preparedLeft, preparedZeroY, preparedRight, preparedZeroY, axisPaint);
        canvas.drawLine(preparedLeft, preparedChartTop, preparedLeft, preparedAxisBottom, gridPaint);
        canvas.drawLine(preparedRight, preparedChartTop, preparedRight, preparedAxisBottom, gridPaint);
        for (BarDrawItem item : drawItems) {
            canvas.drawRoundRect(item.rect, dp(3f), dp(3f), item.positive ? positivePaint : negativePaint);
            valuePaint.setColor(item.positive ? positivePaint.getColor() : negativePaint.getColor());
            canvas.drawText(item.valueText, item.centerX, item.valueY, valuePaint);
            canvas.drawText(item.labelText, item.centerX, item.labelY, labelPaint);
        }
    }

    private String formatPnl(double value) {
        return String.format(Locale.getDefault(), "%s$%s",
                value >= 0d ? "+" : "-",
                FormatUtils.formatPrice(Math.abs(value)));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int applyAlpha(int color, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        return androidx.core.graphics.ColorUtils.setAlphaComponent(color, safeAlpha);
    }

    private static final class BarDrawItem {
        final RectF rect;
        final boolean positive;
        final String valueText;
        final String labelText;
        final float centerX;
        final float valueY;
        final float labelY;

        private BarDrawItem(RectF rect,
                            boolean positive,
                            String valueText,
                            String labelText,
                            float centerX,
                            float valueY,
                            float labelY) {
            this.rect = rect;
            this.positive = positive;
            this.valueText = valueText;
            this.labelText = labelText;
            this.centerX = centerX;
            this.valueY = valueY;
            this.labelY = labelY;
        }
    }
}

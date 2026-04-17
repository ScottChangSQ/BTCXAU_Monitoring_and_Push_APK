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
    private boolean masked;

    public TradePnlBarChartView(Context context) {
        this(context, null);
    }

    public TradePnlBarChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TradePnlBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        labelPaint.setTextSize(dp(9f));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setTextSize(dp(9f));
        valuePaint.setTextAlign(Paint.Align.CENTER);

        emptyPaint.setTextSize(dp(10f));
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
        invalidate();
    }

    // 根据隐私状态切换为占位态。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        float left = dp(22f);
        float right = width - dp(16f);
        float chartTop = dp(30f);
        float axisBottom = height - dp(40f);
        float codeBaseline = height - dp(10f);
        float negativeLabelBaseline = axisBottom + dp(18f);

        boolean hasPositive = false;
        boolean hasNegative = false;
        double maxPositive = 0d;
        double minNegative = 0d;
        for (Entry entry : entries) {
            if (entry.pnl > 0d) {
                hasPositive = true;
                maxPositive = Math.max(maxPositive, entry.pnl);
            } else if (entry.pnl < 0d) {
                hasNegative = true;
                minNegative = Math.min(minNegative, entry.pnl);
            }
        }
        float zeroY;
        if (hasPositive && hasNegative) {
            double range = Math.max(1e-9, maxPositive - minNegative);
            zeroY = (float) (chartTop + (maxPositive / range) * (axisBottom - chartTop));
        } else if (hasPositive) {
            zeroY = axisBottom - dp(10f);
            maxPositive = Math.max(maxPositive, 1d);
        } else if (hasNegative) {
            zeroY = chartTop + dp(12f);
            minNegative = Math.min(minNegative, -1d);
        } else {
            zeroY = chartTop + (axisBottom - chartTop) / 2f;
        }

        canvas.drawLine(left, zeroY, right, zeroY, axisPaint);
        canvas.drawLine(left, chartTop, left, axisBottom, gridPaint);
        canvas.drawLine(right, chartTop, right, axisBottom, gridPaint);

        if (masked) {
            canvas.drawText("****", width / 2f, height / 2f, emptyPaint);
            return;
        }

        if (entries.isEmpty()) {
            canvas.drawText("暂无柱状统计数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        float slotWidth = (right - left) / entries.size();
        float barWidth = Math.max(dp(10f), slotWidth * 0.4f);
        float positiveHeight = Math.max(dp(1f), zeroY - chartTop - dp(4f));
        float negativeHeight = Math.max(dp(1f), axisBottom - zeroY - dp(12f));
        double positiveBase = Math.max(1d, maxPositive);
        double negativeBase = Math.max(1d, Math.abs(minNegative));

        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float centerX = left + slotWidth * i + slotWidth / 2f;

            RectF rect;
            if (entry.pnl >= 0d) {
                float ratio = (float) (Math.abs(entry.pnl) / positiveBase);
                float barHeight = positiveHeight * ratio;
                rect = new RectF(centerX - barWidth / 2f, zeroY - barHeight, centerX + barWidth / 2f, zeroY);
                canvas.drawRoundRect(rect, dp(3f), dp(3f), positivePaint);
                valuePaint.setColor(positivePaint.getColor());
                float positiveValueY = Math.max(dp(18f), rect.top - dp(10f));
                canvas.drawText(formatPnl(entry.pnl), centerX, positiveValueY, valuePaint);
            } else {
                float ratio = (float) (Math.abs(entry.pnl) / negativeBase);
                float barHeight = negativeHeight * ratio;
                rect = new RectF(centerX - barWidth / 2f, zeroY, centerX + barWidth / 2f, zeroY + barHeight);
                canvas.drawRoundRect(rect, dp(3f), dp(3f), negativePaint);
                valuePaint.setColor(negativePaint.getColor());
                float negativeValueY = Math.max(negativeLabelBaseline, rect.bottom + dp(14f));
                negativeValueY = Math.min(codeBaseline - dp(10f), negativeValueY);
                canvas.drawText(formatPnl(entry.pnl), centerX, negativeValueY, valuePaint);
            }
            canvas.drawText(entry.code, centerX, codeBaseline, labelPaint);
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
        return (color & 0x00FFFFFF) | (safeAlpha << 24);
    }
}

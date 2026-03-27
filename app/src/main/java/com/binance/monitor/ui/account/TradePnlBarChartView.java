package com.binance.monitor.ui.account;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;
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

    public TradePnlBarChartView(Context context) {
        this(context, null);
    }

    public TradePnlBarChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TradePnlBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        axisPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        axisPaint.setStrokeWidth(dp(1f));

        gridPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        gridPaint.setStrokeWidth(dp(1f));

        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(dp(9f));
        labelPaint.setTextAlign(Paint.Align.CENTER);

        valuePaint.setTextSize(dp(8f));
        valuePaint.setTextAlign(Paint.Align.CENTER);

        positivePaint.setColor(ContextCompat.getColor(context, R.color.accent_green));
        negativePaint.setColor(ContextCompat.getColor(context, R.color.accent_red));

        emptyPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        emptyPaint.setTextSize(dp(10f));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setEntries(@Nullable List<Entry> source) {
        entries.clear();
        if (source != null) {
            entries.addAll(source);
        }
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

        float left = dp(12f);
        float right = width - dp(12f);
        float top = dp(14f);
        float bottom = height - dp(14f);

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
            zeroY = (float) (top + (maxPositive / range) * (bottom - top));
        } else if (hasPositive) {
            zeroY = bottom - dp(12f);
            maxPositive = Math.max(maxPositive, 1d);
        } else if (hasNegative) {
            zeroY = top + dp(12f);
            minNegative = Math.min(minNegative, -1d);
        } else {
            zeroY = top + (bottom - top) / 2f;
        }

        canvas.drawLine(left, zeroY, right, zeroY, axisPaint);
        canvas.drawLine(left, top, left, bottom, gridPaint);
        canvas.drawLine(right, top, right, bottom, gridPaint);

        if (entries.isEmpty()) {
            canvas.drawText("暂无柱状统计数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        float slotWidth = (right - left) / entries.size();
        float barWidth = Math.max(dp(10f), slotWidth * 0.45f);
        float positiveHeight = Math.max(dp(1f), zeroY - top - dp(2f));
        float negativeHeight = Math.max(dp(1f), bottom - zeroY - dp(2f));
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
                valuePaint.setColor(ContextCompat.getColor(getContext(), R.color.accent_green));
                canvas.drawText(formatPnl(entry.pnl), centerX, rect.top - dp(2f), valuePaint);
            } else {
                float ratio = (float) (Math.abs(entry.pnl) / negativeBase);
                float barHeight = negativeHeight * ratio;
                rect = new RectF(centerX - barWidth / 2f, zeroY, centerX + barWidth / 2f, zeroY + barHeight);
                canvas.drawRoundRect(rect, dp(3f), dp(3f), negativePaint);
                valuePaint.setColor(ContextCompat.getColor(getContext(), R.color.accent_red));
                canvas.drawText(formatPnl(entry.pnl), centerX, rect.bottom + dp(8f), valuePaint);
            }
            canvas.drawText(entry.code, centerX, bottom + dp(7f), labelPaint);
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
}

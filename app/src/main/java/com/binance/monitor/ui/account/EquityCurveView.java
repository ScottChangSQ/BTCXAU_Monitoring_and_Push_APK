package com.binance.monitor.ui.account;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EquityCurveView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint equityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint balancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path equityPath = new Path();
    private final Path balancePath = new Path();
    private final List<CurvePoint> points = new ArrayList<>();

    public EquityCurveView(Context context) {
        this(context, null);
    }

    public EquityCurveView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EquityCurveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        gridPaint.setColor(ContextCompat.getColor(context, R.color.divider));
        gridPaint.setStrokeWidth(dp(1f));
        axisPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        axisPaint.setStrokeWidth(dp(1f));
        labelPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        labelPaint.setTextSize(dp(9f));

        equityPaint.setColor(ContextCompat.getColor(context, R.color.accent_cyan));
        equityPaint.setStyle(Paint.Style.STROKE);
        equityPaint.setStrokeWidth(dp(2f));

        balancePaint.setColor(ContextCompat.getColor(context, R.color.accent_blue));
        balancePaint.setStyle(Paint.Style.STROKE);
        balancePaint.setStrokeWidth(dp(1.6f));

        markerPaint.setStyle(Paint.Style.FILL);
    }

    public void setPoints(List<CurvePoint> data) {
        points.clear();
        if (data != null) {
            points.addAll(data);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float left = dp(56f);
        float top = dp(12f);
        float right = width - dp(8f);
        float bottom = height - dp(24f);
        drawGrid(canvas, left, top, right, bottom);
        if (points.size() < 2) {
            drawAxes(canvas, left, top, right, bottom);
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (CurvePoint point : points) {
            min = Math.min(min, Math.min(point.getEquity(), point.getBalance()));
            max = Math.max(max, Math.max(point.getEquity(), point.getBalance()));
        }
        if (max - min < 1e-6) {
            max += 1d;
            min -= 1d;
        }
        equityPath.reset();
        balancePath.reset();
        int peakIndex = 0;
        int valleyIndex = 0;
        double peak = points.get(0).getEquity();
        double valley = points.get(0).getEquity();

        for (int i = 0; i < points.size(); i++) {
            CurvePoint point = points.get(i);
            float x = left + (right - left) * i / (points.size() - 1f);
            float yEquity = mapY(point.getEquity(), min, max, top, bottom);
            float yBalance = mapY(point.getBalance(), min, max, top, bottom);
            if (i == 0) {
                equityPath.moveTo(x, yEquity);
                balancePath.moveTo(x, yBalance);
            } else {
                equityPath.lineTo(x, yEquity);
                balancePath.lineTo(x, yBalance);
            }
            if (point.getEquity() >= peak) {
                peak = point.getEquity();
                peakIndex = i;
            }
            if (point.getEquity() <= valley) {
                valley = point.getEquity();
                valleyIndex = i;
            }
        }
        canvas.drawPath(balancePath, balancePaint);
        canvas.drawPath(equityPath, equityPaint);

        float peakX = left + (right - left) * peakIndex / (points.size() - 1f);
        float peakY = mapY(peak, min, max, top, bottom);
        float valleyX = left + (right - left) * valleyIndex / (points.size() - 1f);
        float valleyY = mapY(valley, min, max, top, bottom);

        markerPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent_green));
        canvas.drawCircle(peakX, peakY, dp(3f), markerPaint);
        markerPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent_red));
        canvas.drawCircle(valleyX, valleyY, dp(3f), markerPaint);

        drawAxes(canvas, left, top, right, bottom);
        drawYLabels(canvas, left, top, bottom, min, max);
        drawXLabels(canvas, left, right, bottom);
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        float hStep = (bottom - top) / 4f;
        for (int i = 0; i <= 4; i++) {
            float y = top + i * hStep;
            canvas.drawLine(left, y, right, y, gridPaint);
        }
        float vStep = (right - left) / 5f;
        for (int i = 0; i <= 5; i++) {
            float x = left + i * vStep;
            canvas.drawLine(x, top, x, bottom, gridPaint);
        }
    }

    private void drawAxes(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);
    }

    private void drawYLabels(Canvas canvas, float left, float top, float bottom, double min, double max) {
        int step = 4;
        for (int i = 0; i <= step; i++) {
            double value = max - (max - min) * i / step;
            float y = top + (bottom - top) * i / step;
            String label = "$" + FormatUtils.formatPrice(value);
            canvas.drawText(label, dp(4f), y + dp(3f), labelPaint);
        }
    }

    private void drawXLabels(Canvas canvas, float left, float right, float bottom) {
        if (points.isEmpty()) {
            return;
        }
        int mid = points.size() / 2;
        String start = formatLabelTime(points.get(0).getTimestamp());
        String middle = formatLabelTime(points.get(mid).getTimestamp());
        String end = formatLabelTime(points.get(points.size() - 1).getTimestamp());

        canvas.drawText(start, left, bottom + dp(12f), labelPaint);
        float midWidth = labelPaint.measureText(middle);
        canvas.drawText(middle, left + (right - left) / 2f - midWidth / 2f, bottom + dp(12f), labelPaint);
        float endWidth = labelPaint.measureText(end);
        canvas.drawText(end, right - endWidth, bottom + dp(12f), labelPaint);
    }

    private String formatLabelTime(long timestamp) {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return format.format(timestamp);
    }

    private float mapY(double value, double min, double max, float top, float bottom) {
        double ratio = (value - min) / (max - min);
        return (float) (bottom - ratio * (bottom - top));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

package com.binance.monitor.ui.account;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.util.FormatUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EquityCurveView extends View {

    public interface OnPointHighlightListener {
        void onPointHighlight(@Nullable CurvePoint point);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint equityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint balancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path equityPath = new Path();
    private final Path balancePath = new Path();
    private final List<CurvePoint> points = new ArrayList<>();
    private final GestureDetector gestureDetector;

    private float chartLeft;
    private float chartTop;
    private float chartRight;
    private float chartBottom;
    private double chartMin;
    private double chartMax;

    private int highlightedIndex = -1;
    private boolean longPressing;
    private OnPointHighlightListener onPointHighlightListener;

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

        crosshairPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        crosshairPaint.setStrokeWidth(dp(1f));

        tooltipBgPaint.setColor(ContextCompat.getColor(context, R.color.bg_surface));
        tooltipBgPaint.setStyle(Paint.Style.FILL);

        tooltipTextPaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        tooltipTextPaint.setTextSize(dp(9f));

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                clearHighlight();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (points.isEmpty()) {
                    return;
                }
                longPressing = true;
                requestParentDisallowIntercept(true);
                updateHighlightByX(e.getX());
            }
        });
    }

    public void setOnPointHighlightListener(@Nullable OnPointHighlightListener listener) {
        this.onPointHighlightListener = listener;
    }

    public void setPoints(List<CurvePoint> data) {
        points.clear();
        if (data != null) {
            points.addAll(data);
        }
        highlightedIndex = -1;
        longPressing = false;
        dispatchHighlightedPoint();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        if (points.size() < 2) {
            return super.onTouchEvent(event);
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE && longPressing) {
            requestParentDisallowIntercept(true);
            updateHighlightByX(event.getX());
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (longPressing) {
                longPressing = false;
                requestParentDisallowIntercept(false);
                return true;
            }
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        chartLeft = dp(56f);
        chartTop = dp(12f);
        chartRight = width - dp(8f);
        chartBottom = height - dp(24f);

        drawGrid(canvas, chartLeft, chartTop, chartRight, chartBottom);
        if (points.size() < 2) {
            drawAxes(canvas, chartLeft, chartTop, chartRight, chartBottom);
            return;
        }

        chartMin = Double.MAX_VALUE;
        chartMax = -Double.MAX_VALUE;
        for (CurvePoint point : points) {
            chartMin = Math.min(chartMin, Math.min(point.getEquity(), point.getBalance()));
            chartMax = Math.max(chartMax, Math.max(point.getEquity(), point.getBalance()));
        }
        if (chartMax - chartMin < 1e-6) {
            chartMax += 1d;
            chartMin -= 1d;
        }

        equityPath.reset();
        balancePath.reset();
        int peakIndex = 0;
        int valleyIndex = 0;
        double peak = points.get(0).getEquity();
        double valley = points.get(0).getEquity();

        for (int i = 0; i < points.size(); i++) {
            CurvePoint point = points.get(i);
            float x = chartLeft + (chartRight - chartLeft) * i / (points.size() - 1f);
            float yEquity = mapY(point.getEquity(), chartMin, chartMax, chartTop, chartBottom);
            float yBalance = mapY(point.getBalance(), chartMin, chartMax, chartTop, chartBottom);
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

        float peakX = chartLeft + (chartRight - chartLeft) * peakIndex / (points.size() - 1f);
        float peakY = mapY(peak, chartMin, chartMax, chartTop, chartBottom);
        float valleyX = chartLeft + (chartRight - chartLeft) * valleyIndex / (points.size() - 1f);
        float valleyY = mapY(valley, chartMin, chartMax, chartTop, chartBottom);

        markerPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent_green));
        canvas.drawCircle(peakX, peakY, dp(3f), markerPaint);
        markerPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent_red));
        canvas.drawCircle(valleyX, valleyY, dp(3f), markerPaint);

        if (highlightedIndex >= 0 && highlightedIndex < points.size()) {
            drawHighlight(canvas, highlightedIndex);
        }

        drawAxes(canvas, chartLeft, chartTop, chartRight, chartBottom);
        drawYLabels(canvas, chartLeft, chartTop, chartBottom, chartMin, chartMax);
        drawXLabels(canvas, chartLeft, chartRight, chartBottom);
    }

    private void drawHighlight(Canvas canvas, int index) {
        CurvePoint point = points.get(index);
        float x = chartLeft + (chartRight - chartLeft) * index / (points.size() - 1f);
        float yEquity = mapY(point.getEquity(), chartMin, chartMax, chartTop, chartBottom);
        float yBalance = mapY(point.getBalance(), chartMin, chartMax, chartTop, chartBottom);

        canvas.drawLine(x, chartTop, x, chartBottom, crosshairPaint);

        markerPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent_cyan));
        canvas.drawCircle(x, yEquity, dp(4f), markerPaint);
        markerPaint.setColor(ContextCompat.getColor(getContext(), R.color.accent_blue));
        canvas.drawCircle(x, yBalance, dp(3.5f), markerPaint);

        String line1 = formatLabelTime(point.getTimestamp());
        String line2 = "净值 $" + FormatUtils.formatPrice(point.getEquity());
        String line3 = "结余 $" + FormatUtils.formatPrice(point.getBalance());

        float maxWidth = Math.max(tooltipTextPaint.measureText(line1),
                Math.max(tooltipTextPaint.measureText(line2), tooltipTextPaint.measureText(line3)));
        float padding = dp(6f);
        float lineHeight = dp(11f);
        float boxWidth = maxWidth + padding * 2;
        float boxHeight = lineHeight * 3 + padding * 2;

        float boxLeft = x + dp(8f);
        if (boxLeft + boxWidth > chartRight) {
            boxLeft = x - boxWidth - dp(8f);
        }
        boxLeft = Math.max(chartLeft, boxLeft);
        float boxTop = chartTop + dp(4f);

        RectF rect = new RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight);
        canvas.drawRoundRect(rect, dp(6f), dp(6f), tooltipBgPaint);
        canvas.drawText(line1, boxLeft + padding, boxTop + padding + lineHeight * 0.8f, tooltipTextPaint);
        canvas.drawText(line2, boxLeft + padding, boxTop + padding + lineHeight * 1.8f, tooltipTextPaint);
        canvas.drawText(line3, boxLeft + padding, boxTop + padding + lineHeight * 2.8f, tooltipTextPaint);
    }

    private void updateHighlightByX(float x) {
        float range = chartRight - chartLeft;
        if (range <= 0f || points.isEmpty()) {
            return;
        }
        float clamped = Math.max(chartLeft, Math.min(chartRight, x));
        int index = Math.round((clamped - chartLeft) / range * (points.size() - 1));
        index = Math.max(0, Math.min(points.size() - 1, index));
        if (highlightedIndex != index) {
            highlightedIndex = index;
            dispatchHighlightedPoint();
            invalidate();
        }
    }

    private void clearHighlight() {
        longPressing = false;
        requestParentDisallowIntercept(false);
        if (highlightedIndex != -1) {
            highlightedIndex = -1;
            dispatchHighlightedPoint();
            invalidate();
        }
    }

    private void requestParentDisallowIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void dispatchHighlightedPoint() {
        if (onPointHighlightListener == null) {
            return;
        }
        if (highlightedIndex >= 0 && highlightedIndex < points.size()) {
            onPointHighlightListener.onPointHighlight(points.get(highlightedIndex));
        } else {
            onPointHighlightListener.onPointHighlight(null);
        }
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
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
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

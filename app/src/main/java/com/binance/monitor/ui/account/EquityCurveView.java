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
import com.binance.monitor.ui.theme.UiPaletteManager;
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
    private final Paint drawdownFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drawdownStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private long chartStartTs;
    private long chartEndTs;
    private double baseBalance = 1d;
    private long drawdownStartTs;
    private long drawdownEndTs;
    private double drawdownPeakBalance;
    private double drawdownValleyBalance;

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

        UiPaletteManager.Palette palette = UiPaletteManager.resolve(context);
        equityPaint.setColor(palette.primary);
        equityPaint.setStyle(Paint.Style.STROKE);
        equityPaint.setStrokeWidth(dp(2f));

        balancePaint.setColor(palette.btc);
        balancePaint.setStyle(Paint.Style.STROKE);
        balancePaint.setStrokeWidth(dp(1.6f));

        markerPaint.setStyle(Paint.Style.FILL);

        crosshairPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        crosshairPaint.setStrokeWidth(dp(1f));

        drawdownFillPaint.setStyle(Paint.Style.FILL);
        drawdownStrokePaint.setStyle(Paint.Style.STROKE);
        drawdownStrokePaint.setStrokeWidth(dp(1.2f));
        drawdownFillPaint.setColor(applyAlpha(palette.primarySoft, 150));
        drawdownStrokePaint.setColor(applyAlpha(palette.primary, 215));

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

    public void refreshPalette() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());
        labelPaint.setColor(palette.textSecondary);
        axisPaint.setColor(applyAlpha(palette.textSecondary, 210));
        gridPaint.setColor(applyAlpha(palette.stroke, 170));
        equityPaint.setColor(palette.primary);
        balancePaint.setColor(palette.btc);
        drawdownFillPaint.setColor(applyAlpha(palette.primarySoft, 150));
        drawdownStrokePaint.setColor(applyAlpha(palette.primary, 215));
        crosshairPaint.setColor(applyAlpha(palette.textSecondary, 220));
        tooltipBgPaint.setColor(applyAlpha(palette.card, 240));
        tooltipTextPaint.setColor(palette.textPrimary);
        invalidate();
    }

    public void setOnPointHighlightListener(@Nullable OnPointHighlightListener listener) {
        this.onPointHighlightListener = listener;
    }

    public void setPoints(List<CurvePoint> data) {
        points.clear();
        if (data != null) {
            points.addAll(data);
            points.sort((left, right) -> Long.compare(left.getTimestamp(), right.getTimestamp()));
        }
        highlightedIndex = -1;
        longPressing = false;
        dispatchHighlightedPoint();
        invalidate();
    }

    public void setBaseBalance(double value) {
        baseBalance = Math.max(1e-9, value);
        invalidate();
    }

    public void setDrawdownHighlight(long startTs, long endTs, double peakBalance, double valleyBalance) {
        drawdownStartTs = Math.max(0L, startTs);
        drawdownEndTs = Math.max(0L, endTs);
        drawdownPeakBalance = peakBalance;
        drawdownValleyBalance = valleyBalance;
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
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && longPressing) {
            longPressing = false;
            requestParentDisallowIntercept(false);
            return true;
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

        chartLeft = dp(38f);
        chartTop = dp(12f);
        chartRight = width - dp(36f);
        chartBottom = height - dp(24f);

        drawGrid(canvas, chartLeft, chartTop, chartRight, chartBottom);
        if (points.size() < 2) {
            drawAxes(canvas, chartLeft, chartTop, chartRight, chartBottom);
            return;
        }

        double rawMin = Double.MAX_VALUE;
        double rawMax = -Double.MAX_VALUE;
        for (CurvePoint point : points) {
            rawMin = Math.min(rawMin, Math.min(point.getEquity(), point.getBalance()));
            rawMax = Math.max(rawMax, Math.max(point.getEquity(), point.getBalance()));
        }
        if (rawMax - rawMin < 1e-6) {
            rawMax += 1d;
            rawMin -= 1d;
        }
        double rawRange = Math.max(1d, rawMax - rawMin);
        double step = niceStep(rawRange / 4d);
        chartMin = Math.floor(rawMin / step) * step;
        chartMax = Math.ceil(rawMax / step) * step;
        if (chartMax - chartMin < 1e-6) {
            chartMax += 1d;
            chartMin -= 1d;
        }

        chartStartTs = points.get(0).getTimestamp();
        chartEndTs = points.get(points.size() - 1).getTimestamp();
        if (chartEndTs <= chartStartTs) {
            chartEndTs = chartStartTs + 1L;
        }

        drawDrawdownHighlight(canvas);
        equityPath.reset();
        balancePath.reset();
        for (int i = 0; i < points.size(); i++) {
            CurvePoint point = points.get(i);
            float x = mapX(point.getTimestamp(), chartStartTs, chartEndTs, chartLeft, chartRight);
            float yEquity = mapY(point.getEquity(), chartMin, chartMax, chartTop, chartBottom);
            float yBalance = mapY(point.getBalance(), chartMin, chartMax, chartTop, chartBottom);
            if (i == 0) {
                equityPath.moveTo(x, yEquity);
                balancePath.moveTo(x, yBalance);
            } else {
                equityPath.lineTo(x, yEquity);
                balancePath.lineTo(x, yBalance);
            }
        }

        canvas.drawPath(balancePath, balancePaint);
        canvas.drawPath(equityPath, equityPaint);

        if (highlightedIndex >= 0 && highlightedIndex < points.size()) {
            drawHighlight(canvas, highlightedIndex);
        }

        drawAxes(canvas, chartLeft, chartTop, chartRight, chartBottom);
        drawYLabels(canvas, chartLeft, chartRight, chartTop, chartBottom, chartMin, chartMax, baseBalance);
        drawXLabels(canvas, chartLeft, chartRight, chartBottom);
    }

    private void drawDrawdownHighlight(Canvas canvas) {
        if (drawdownStartTs <= 0L || drawdownEndTs <= drawdownStartTs) {
            return;
        }
        float startX = mapX(drawdownStartTs, chartStartTs, chartEndTs, chartLeft, chartRight);
        float endX = mapX(drawdownEndTs, chartStartTs, chartEndTs, chartLeft, chartRight);
        RectF area = new RectF(
                Math.max(chartLeft, Math.min(startX, endX)),
                chartTop,
                Math.min(chartRight, Math.max(startX, endX)),
                chartBottom
        );
        if (area.width() <= dp(1f)) {
            return;
        }
        float peakY = mapY(drawdownPeakBalance, chartMin, chartMax, chartTop, chartBottom);
        float valleyY = mapY(drawdownValleyBalance, chartMin, chartMax, chartTop, chartBottom);
        canvas.drawRoundRect(area, dp(8f), dp(8f), drawdownFillPaint);
        canvas.drawLine(startX, peakY, endX, valleyY, drawdownStrokePaint);
        canvas.drawCircle(startX, peakY, dp(3f), drawdownStrokePaint);
        canvas.drawCircle(endX, valleyY, dp(3f), drawdownStrokePaint);
    }

    private void drawHighlight(Canvas canvas, int index) {
        CurvePoint point = points.get(index);
        float x = mapX(point.getTimestamp(), chartStartTs, chartEndTs, chartLeft, chartRight);
        float yEquity = mapY(point.getEquity(), chartMin, chartMax, chartTop, chartBottom);
        float yBalance = mapY(point.getBalance(), chartMin, chartMax, chartTop, chartBottom);
        canvas.drawLine(x, chartTop, x, chartBottom, crosshairPaint);

        UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());
        markerPaint.setColor(palette.primary);
        canvas.drawCircle(x, yEquity, dp(4f), markerPaint);
        markerPaint.setColor(palette.btc);
        canvas.drawCircle(x, yBalance, dp(3.5f), markerPaint);

        String line1 = formatLabelTime(point.getTimestamp());
        String line2 = "净值 $" + FormatUtils.formatPrice(point.getEquity());
        String line3 = "结余 $" + FormatUtils.formatPrice(point.getBalance());
        double pct = (point.getBalance() - baseBalance) / Math.max(1e-9, baseBalance) * 100d;
        String line4 = String.format(Locale.getDefault(), "收益 %+.2f%%", pct);

        float maxWidth = Math.max(tooltipTextPaint.measureText(line1),
                Math.max(Math.max(tooltipTextPaint.measureText(line2), tooltipTextPaint.measureText(line3)),
                        tooltipTextPaint.measureText(line4)));
        float padding = dp(6f);
        float lineHeight = dp(11f);
        float boxWidth = maxWidth + padding * 2;
        float boxHeight = lineHeight * 4 + padding * 2;

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
        canvas.drawText(line4, boxLeft + padding, boxTop + padding + lineHeight * 3.8f, tooltipTextPaint);
    }

    private void updateHighlightByX(float x) {
        float range = chartRight - chartLeft;
        if (range <= 0f || points.isEmpty()) {
            return;
        }
        float clamped = Math.max(chartLeft, Math.min(chartRight, x));
        double ratio = (clamped - chartLeft) / range;
        long targetTs = chartStartTs + Math.round(ratio * (chartEndTs - chartStartTs));
        int index = findNearestIndexByTimestamp(targetTs);
        index = Math.max(0, Math.min(points.size() - 1, index));
        if (highlightedIndex != index) {
            highlightedIndex = index;
            dispatchHighlightedPoint();
            invalidate();
        }
    }

    private int findNearestIndexByTimestamp(long timestamp) {
        int size = points.size();
        if (size == 0) {
            return -1;
        }
        int left = 0;
        int right = size - 1;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            long midTs = points.get(mid).getTimestamp();
            if (midTs < timestamp) {
                left = mid + 1;
            } else if (midTs > timestamp) {
                right = mid - 1;
            } else {
                return mid;
            }
        }
        if (left >= size) {
            return size - 1;
        }
        if (right < 0) {
            return 0;
        }
        long leftDiff = Math.abs(points.get(left).getTimestamp() - timestamp);
        long rightDiff = Math.abs(points.get(right).getTimestamp() - timestamp);
        return leftDiff < rightDiff ? left : right;
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

    private void dispatchHighlightedPoint() {
        if (onPointHighlightListener == null) {
            return;
        }
        onPointHighlightListener.onPointHighlight(
                highlightedIndex >= 0 && highlightedIndex < points.size() ? points.get(highlightedIndex) : null);
    }

    private void requestParentDisallowIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        float hStep = (bottom - top) / 4f;
        for (int i = 0; i <= 4; i++) {
            canvas.drawLine(left, top + i * hStep, right, top + i * hStep, gridPaint);
        }
        float vStep = (right - left) / 5f;
        for (int i = 0; i <= 5; i++) {
            canvas.drawLine(left + i * vStep, top, left + i * vStep, bottom, gridPaint);
        }
    }

    private void drawAxes(Canvas canvas, float left, float top, float right, float bottom) {
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(right, top, right, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);
    }

    private void drawYLabels(Canvas canvas, float left, float right, float top, float bottom, double min, double max, double base) {
        int tickCount = 4;
        for (int i = 0; i <= tickCount; i++) {
            double value = min + (max - min) * i / tickCount;
            float y = mapY(value, min, max, top, bottom);
            String amount = "$" + String.format(Locale.getDefault(), "%,.0f", value);
            float amountWidth = labelPaint.measureText(amount);
            canvas.drawText(amount, left - dp(4f) - amountWidth, y + dp(3f), labelPaint);

            double pct = (value - base) / Math.max(1e-9, base) * 100d;
            String percent = String.format(Locale.getDefault(), "%+.1f%%", pct);
            canvas.drawText(percent, right + dp(4f), y + dp(3f), labelPaint);
        }
    }

    private double niceStep(double rawStep) {
        if (rawStep <= 1d) {
            return 1d;
        }
        double exponent = Math.pow(10d, Math.floor(Math.log10(rawStep)));
        double fraction = rawStep / exponent;
        double niceFraction;
        if (fraction <= 1d) {
            niceFraction = 1d;
        } else if (fraction <= 2d) {
            niceFraction = 2d;
        } else if (fraction <= 5d) {
            niceFraction = 5d;
        } else {
            niceFraction = 10d;
        }
        return Math.max(1d, niceFraction * exponent);
    }

    private void drawXLabels(Canvas canvas, float left, float right, float bottom) {
        if (points.isEmpty()) {
            return;
        }
        long startTs = points.get(0).getTimestamp();
        long endTs = points.get(points.size() - 1).getTimestamp();
        long middleTs = startTs + (endTs - startTs) / 2L;

        String start = formatLabelTime(startTs);
        String middle = formatLabelTime(middleTs);
        String end = formatLabelTime(endTs);

        canvas.drawText(start, left, bottom + dp(12f), labelPaint);
        float midWidth = labelPaint.measureText(middle);
        canvas.drawText(middle, left + (right - left) / 2f - midWidth / 2f, bottom + dp(12f), labelPaint);
        float endWidth = labelPaint.measureText(end);
        canvas.drawText(end, right - endWidth, bottom + dp(12f), labelPaint);
    }

    private String formatLabelTime(long timestamp) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(timestamp);
    }

    private float mapY(double value, double min, double max, float top, float bottom) {
        double ratio = (value - min) / (max - min);
        return (float) (bottom - ratio * (bottom - top));
    }

    private float mapX(long timestamp, long start, long end, float left, float right) {
        if (end <= start) {
            return left;
        }
        double ratio = (double) (timestamp - start) / (double) (end - start);
        ratio = Math.max(0d, Math.min(1d, ratio));
        return (float) (left + ratio * (right - left));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int applyAlpha(int color, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (safeAlpha << 24);
    }
}

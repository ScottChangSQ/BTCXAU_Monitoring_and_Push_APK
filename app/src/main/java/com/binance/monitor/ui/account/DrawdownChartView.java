/*
 * 回撤附图，负责绘制基于净值序列的回撤曲线。
 * 供 AccountStatsBridgeActivity 展示当前周期下的回撤走势。
 */
package com.binance.monitor.ui.account;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.theme.UiPaletteManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DrawdownChartView extends View {

    public interface OnTimeHighlightListener {
        void onTimeHighlight(@Nullable Long timestamp, float xRatio);
    }

    private final List<CurveAnalyticsHelper.DrawdownPoint> points = new ArrayList<>();
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();
    private final GestureDetector gestureDetector;

    private float chartLeft;
    private float chartTop;
    private float chartRight;
    private float chartBottom;
    private long viewportStartTs;
    private long viewportEndTs;
    private long seriesStartTs;
    private long seriesEndTs;
    private int highlightedIndex = -1;
    private float highlightedXRatio = -1f;
    private boolean longPressing;
    private boolean masked;
    private boolean mergeWithPreviousPane;
    private boolean mergeWithNextPane;
    private OnTimeHighlightListener onTimeHighlightListener;

    public DrawdownChartView(Context context) {
        this(context, null);
    }

    public DrawdownChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DrawdownChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        gridPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setStyle(Paint.Style.STROKE);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.8f));
        fillPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextSize(dp(8.5f));
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextSize(dp(10f));
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(dp(1f));
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
                updateHighlightByX(e.getX(), true);
            }
        });
        refreshPalette();
    }

    // 刷新主题色，保证切换主题后附图颜色同步。
    public void refreshPalette() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());
        gridPaint.setColor(applyAlpha(palette.stroke, 150));
        gridPaint.setStrokeWidth(dp(0.8f));
        axisPaint.setColor(applyAlpha(palette.textSecondary, 180));
        axisPaint.setStrokeWidth(dp(1f));
        linePaint.setColor(applyAlpha(palette.fall, 230));
        fillPaint.setColor(applyAlpha(palette.fall, 55));
        labelPaint.setColor(palette.textSecondary);
        markerPaint.setColor(palette.fall);
        crosshairPaint.setColor(applyAlpha(palette.textSecondary, 220));
        emptyPaint.setColor(palette.textSecondary);
        invalidate();
    }

    // 设置回撤数据并触发重绘。
    public void setPoints(@Nullable List<CurveAnalyticsHelper.DrawdownPoint> source) {
        points.clear();
        if (source != null) {
            points.addAll(source);
        }
        highlightedIndex = -1;
        highlightedXRatio = -1f;
        longPressing = false;
        invalidate();
    }

    // 设置三联图共享的横轴时间范围。
    public void setViewport(long startTs, long endTs) {
        viewportStartTs = Math.max(0L, startTs);
        viewportEndTs = Math.max(viewportStartTs + 1L, endTs);
        invalidate();
    }

    // 根据隐私状态切换为占位态。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        clearSyncedHighlight();
        invalidate();
    }

    // 注册共享十字光标回调。
    public void setOnTimeHighlightListener(@Nullable OnTimeHighlightListener listener) {
        onTimeHighlightListener = listener;
    }

    // 控制是否与上一张图共用边界。
    public void setMergeWithPreviousPane(boolean merge) {
        if (mergeWithPreviousPane == merge) {
            return;
        }
        mergeWithPreviousPane = merge;
        invalidate();
    }

    // 控制是否与下一张图共用边界。
    public void setMergeWithNextPane(boolean merge) {
        if (mergeWithNextPane == merge) {
            return;
        }
        mergeWithNextPane = merge;
        invalidate();
    }

    // 宿主同步外部十字光标。
    public void syncHighlightTimestamp(long timestamp, float xRatio) {
        if (timestamp <= 0L || points.isEmpty()) {
            clearSyncedHighlight();
            return;
        }
        highlightedIndex = Math.max(0, Math.min(points.size() - 1, findNearestIndexByTimestamp(timestamp)));
        highlightedXRatio = clampRatio(xRatio);
        invalidate();
    }

    // 清空宿主同步的十字光标。
    public void clearSyncedHighlight() {
        highlightedIndex = -1;
        highlightedXRatio = -1f;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (masked) {
            clearHighlight();
            return false;
        }
        gestureDetector.onTouchEvent(event);
        if (points.size() < 2) {
            return super.onTouchEvent(event);
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE && longPressing) {
            requestParentDisallowIntercept(true);
            updateHighlightByX(event.getX(), true);
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
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        chartLeft = dp(CurvePaneLayoutHelper.resolveChartLeftDp());
        chartTop = CurvePaneSpacingHelper.resolveTopInsetPx(mergeWithPreviousPane, dp(10f));
        chartRight = width - dp(CurvePaneLayoutHelper.resolveChartRightInsetDp());
        chartBottom = height - CurvePaneSpacingHelper.resolveBottomInsetPx(
                mergeWithNextPane,
                false,
                dp(10f),
                0f
        );
        drawFrame(canvas, chartLeft, chartTop, chartRight, chartBottom);

        if (masked) {
            canvas.drawText("****", width / 2f, height / 2f, emptyPaint);
            return;
        }

        if (points.size() < 2) {
            canvas.drawText("暂无回撤数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        double minDrawdown = 0d;
        CurveAnalyticsHelper.DrawdownPoint valley = points.get(0);
        for (CurveAnalyticsHelper.DrawdownPoint point : points) {
            if (point.getDrawdownRate() < minDrawdown) {
                minDrawdown = point.getDrawdownRate();
                valley = point;
            }
        }
        double chartMin = Math.min(-0.01d, minDrawdown * 1.12d);
        double chartMax = 0d;
        seriesStartTs = points.get(0).getTimestamp();
        seriesEndTs = points.get(points.size() - 1).getTimestamp();
        long startTs = viewportStartTs > 0L ? viewportStartTs : seriesStartTs;
        long endTs = viewportEndTs > startTs ? viewportEndTs : Math.max(startTs + 1L, seriesEndTs);

        float zeroY = mapY(0d, chartMin, chartMax, chartTop, chartBottom);
        linePath.reset();
        fillPath.reset();
        for (int i = 0; i < points.size(); i++) {
            CurveAnalyticsHelper.DrawdownPoint point = points.get(i);
            float x = mapX(point.getTimestamp(), startTs, endTs, chartLeft, chartRight);
            float y = mapY(point.getDrawdownRate(), chartMin, chartMax, chartTop, chartBottom);
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, zeroY);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(mapX(points.get(points.size() - 1).getTimestamp(), startTs, endTs, chartLeft, chartRight), zeroY);
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        float valleyX = mapX(valley.getTimestamp(), startTs, endTs, chartLeft, chartRight);
        float valleyY = mapY(valley.getDrawdownRate(), chartMin, chartMax, chartTop, chartBottom);
        canvas.drawCircle(valleyX, valleyY, dp(3.2f), markerPaint);
        if (highlightedIndex >= 0 && highlightedIndex < points.size()) {
            CurveAnalyticsHelper.DrawdownPoint point = points.get(highlightedIndex);
            float highlightX = resolveHighlightX(startTs, endTs);
            float highlightY = mapY(point.getDrawdownRate(), chartMin, chartMax, chartTop, chartBottom);
            canvas.drawLine(highlightX, chartTop, highlightX, chartBottom, crosshairPaint);
            canvas.drawCircle(highlightX, highlightY, dp(3.4f), markerPaint);
        }
        canvas.drawLine(chartLeft, zeroY, chartRight, zeroY, axisPaint);
        drawLabels(canvas, chartLeft, chartRight, chartTop, chartBottom, minDrawdown);
    }

    // 绘制基础网格和边框。
    private void drawFrame(Canvas canvas, float left, float top, float right, float bottom) {
        float horizontalStep = (bottom - top) / 4f;
        for (int i = 0; i <= 4; i++) {
            canvas.drawLine(left, top + horizontalStep * i, right, top + horizontalStep * i, gridPaint);
        }
        float verticalStep = (right - left) / 4f;
        for (int i = 0; i <= 4; i++) {
            canvas.drawLine(left + verticalStep * i, top, left + verticalStep * i, bottom, gridPaint);
        }
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);
    }

    // 绘制两端刻度和回撤说明。
    private void drawLabels(Canvas canvas, float left, float right, float top, float bottom, double minDrawdown) {
        float centerY = top + (bottom - top) / 2f;
        float verticalCenterBaseline = centerY - (labelPaint.descent() + labelPaint.ascent()) / 2f;
        float topBaseline = top + dp(mergeWithPreviousPane ? 10f : 6f);
        float bottomBaseline = CurvePaneSpacingHelper.resolveBottomLabelBaseline(
                bottom,
                mergeWithNextPane,
                dp(2f)
        );
        Paint.Align originalAlign = labelPaint.getTextAlign();
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        float labelAnchorX = chartLeft - dp(4f);
        canvas.drawText("0%", labelAnchorX, topBaseline, labelPaint);
        canvas.drawText(String.format(Locale.getDefault(), "%.1f%%", minDrawdown * 100d),
                labelAnchorX, bottomBaseline, labelPaint);
        float rightEdge = getWidth() - dp(12f);
        canvas.save();
        canvas.rotate(-90f, rightEdge, centerY);
        canvas.drawText("当前区间回撤", rightEdge, verticalCenterBaseline, labelPaint);
        canvas.restore();
        labelPaint.setTextAlign(originalAlign);
    }

    // 按当前横坐标更新共享十字光标。
    private void updateHighlightByX(float x, boolean notify) {
        float range = chartRight - chartLeft;
        if (range <= 0f || points.isEmpty()) {
            return;
        }
        float clamped = Math.max(chartLeft, Math.min(chartRight, x));
        highlightedXRatio = clampRatio((clamped - chartLeft) / range);
        long startTs = viewportStartTs > 0L ? viewportStartTs : seriesStartTs;
        long endTs = viewportEndTs > startTs ? viewportEndTs : Math.max(startTs + 1L, seriesEndTs);
        long targetTs = startTs + Math.round((clamped - chartLeft) / range * (endTs - startTs));
        highlightedIndex = Math.max(0, Math.min(points.size() - 1, findNearestIndexByTimestamp(targetTs)));
        if (notify && onTimeHighlightListener != null) {
            onTimeHighlightListener.onTimeHighlight(targetTs, highlightedXRatio);
        }
        invalidate();
    }

    // 按时间找到最近的数据点。
    private int findNearestIndexByTimestamp(long timestamp) {
        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            long distance = Math.abs(points.get(i).getTimestamp() - timestamp);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    // 清除用户选中的十字光标并通知宿主。
    private void clearHighlight() {
        longPressing = false;
        requestParentDisallowIntercept(false);
        highlightedXRatio = -1f;
        if (highlightedIndex != -1) {
            highlightedIndex = -1;
            if (onTimeHighlightListener != null) {
                onTimeHighlightListener.onTimeHighlight(null, -1f);
            }
            invalidate();
        }
    }

    // 避免父级滚动抢走长按拖动手势。
    private void requestParentDisallowIntercept(boolean disallow) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    // 把数值映射到纵坐标。
    private float mapY(double value, double min, double max, float top, float bottom) {
        double ratio = (value - min) / Math.max(1e-9, max - min);
        return (float) (bottom - ratio * (bottom - top));
    }

    // 把时间映射到横坐标。
    private float mapX(long timestamp, long start, long end, float left, float right) {
        double ratio = (double) (timestamp - start) / Math.max(1d, (double) (end - start));
        ratio = Math.max(0d, Math.min(1d, ratio));
        return (float) (left + ratio * (right - left));
    }

    // dp 转像素。
    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float resolveHighlightX(long startTs, long endTs) {
        if (highlightedXRatio >= 0f) {
            return chartLeft + highlightedXRatio * (chartRight - chartLeft);
        }
        if (highlightedIndex >= 0 && highlightedIndex < points.size()) {
            return mapX(points.get(highlightedIndex).getTimestamp(), startTs, endTs, chartLeft, chartRight);
        }
        return chartLeft;
    }

    private float clampRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }

    // 应用透明度，避免附图过于厚重。
    private int applyAlpha(int color, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (safeAlpha << 24);
    }
}

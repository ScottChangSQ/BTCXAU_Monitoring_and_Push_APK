/*
 * 日收益率附图，负责绘制正负日收益柱状分布。
 * 供 AccountStatsBridgeActivity 展示当前周期下的日收益变化。
 */
package com.binance.monitor.ui.account;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.theme.UiPaletteManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DailyReturnChartView extends View {

    public interface OnTimeHighlightListener {
        void onTimeHighlight(@Nullable Long timestamp, float xRatio);
    }

    private final List<CurveAnalyticsHelper.DailyReturnPoint> points = new ArrayList<>();
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint positivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint negativePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private boolean showBottomTimeLabels;
    private boolean mergeWithPreviousPane;
    private boolean mergeWithNextPane;
    private OnTimeHighlightListener onTimeHighlightListener;

    public DailyReturnChartView(Context context) {
        this(context, null);
    }

    public DailyReturnChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DailyReturnChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        axisPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStyle(Paint.Style.STROKE);
        labelPaint.setTextSize(dp(8.5f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextSize(dp(10f));
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(dp(1f));
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setStrokeWidth(dp(1.2f));
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

    // 刷新主题色。
    public void refreshPalette() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());
        axisPaint.setColor(applyAlpha(palette.textSecondary, 180));
        axisPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(applyAlpha(palette.stroke, 140));
        gridPaint.setStrokeWidth(dp(0.8f));
        labelPaint.setColor(palette.textSecondary);
        positivePaint.setColor(applyAlpha(palette.rise, 220));
        negativePaint.setColor(applyAlpha(palette.fall, 220));
        crosshairPaint.setColor(applyAlpha(palette.textSecondary, 220));
        selectionPaint.setColor(applyAlpha(palette.primary, 235));
        emptyPaint.setColor(palette.textSecondary);
        invalidate();
    }

    // 设置日收益数据。
    public void setPoints(@Nullable List<CurveAnalyticsHelper.DailyReturnPoint> source) {
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

    // 控制时间刻度是否放在最底部附图。
    public void setShowBottomTimeLabels(boolean show) {
        if (showBottomTimeLabels == show) {
            return;
        }
        showBottomTimeLabels = show;
        invalidate();
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
        if (points.isEmpty()) {
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

        chartLeft = dp(34f);
        chartRight = width - dp(28f);
        chartTop = CurvePaneSpacingHelper.resolveTopInsetPx(mergeWithPreviousPane, dp(10f));
        chartBottom = height - CurvePaneSpacingHelper.resolveBottomInsetPx(
                mergeWithNextPane,
                showBottomTimeLabels,
                dp(10f),
                dp(24f)
        );
        drawFrame(canvas, chartLeft, chartTop, chartRight, chartBottom);

        if (masked) {
            canvas.drawText("****", width / 2f, height / 2f, emptyPaint);
            return;
        }

        if (points.isEmpty()) {
            canvas.drawText("暂无日收益数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        double maxAbs = 0.01d;
        for (CurveAnalyticsHelper.DailyReturnPoint point : points) {
            maxAbs = Math.max(maxAbs, Math.abs(point.getReturnRate()));
        }
        seriesStartTs = points.get(0).getTimestamp();
        seriesEndTs = points.get(points.size() - 1).getTimestamp();
        long startTs = viewportStartTs > 0L ? viewportStartTs : seriesStartTs;
        long endTs = viewportEndTs > startTs ? viewportEndTs : Math.max(startTs + 1L, seriesEndTs);
        float zeroY = mapY(0d, -maxAbs, maxAbs, chartTop, chartBottom);
        canvas.drawLine(chartLeft, zeroY, chartRight, zeroY, axisPaint);

        float barWidth = Math.max(dp(2f), Math.min(dp(10f),
                (chartRight - chartLeft) / Math.max(1f, points.size() * 1.7f)));
        for (int i = 0; i < points.size(); i++) {
            CurveAnalyticsHelper.DailyReturnPoint point = points.get(i);
            float centerX = mapX(point.getTimestamp(), startTs, endTs, chartLeft, chartRight);
            float targetY = mapY(point.getReturnRate(), -maxAbs, maxAbs, chartTop, chartBottom);
            RectF rect = new RectF(
                    centerX - barWidth / 2f,
                    Math.min(zeroY, targetY),
                    centerX + barWidth / 2f,
                    Math.max(zeroY, targetY)
            );
            rect.bottom = Math.max(rect.bottom, rect.top + dp(1.2f));
            canvas.drawRoundRect(rect, dp(2f), dp(2f),
                    point.getReturnRate() >= 0d ? positivePaint : negativePaint);
            if (i == highlightedIndex) {
                canvas.drawRect(rect, selectionPaint);
            }
        }

        if (highlightedIndex >= 0 && highlightedIndex < points.size()) {
            CurveAnalyticsHelper.DailyReturnPoint point = points.get(highlightedIndex);
            float highlightX = resolveHighlightX(startTs, endTs);
            canvas.drawLine(highlightX, chartTop, highlightX, chartBottom, crosshairPaint);
        }

        float topBaseline = chartTop + dp(mergeWithPreviousPane ? 8f : 2f);
        float bottomBaseline = CurvePaneSpacingHelper.resolveBottomLabelBaseline(
                chartBottom,
                mergeWithNextPane && !showBottomTimeLabels,
                dp(2f)
        );
        canvas.drawText(String.format(Locale.getDefault(), "+%.1f%%", maxAbs * 100d),
                dp(4f), topBaseline, labelPaint);
        canvas.drawText(String.format(Locale.getDefault(), "-%.1f%%", maxAbs * 100d),
                dp(4f), bottomBaseline, labelPaint);
        float rightEdge = getWidth() - dp(6f);
        canvas.save();
        canvas.rotate(-90f, rightEdge, chartTop + (chartBottom - chartTop) / 2f);
        canvas.drawText("当前区间日收益", rightEdge, chartTop + (chartBottom - chartTop) / 2f, labelPaint);
        canvas.restore();
        if (showBottomTimeLabels) {
            drawXLabels(canvas, chartLeft, chartRight, chartBottom, startTs, endTs);
        }
    }

    // 绘制基础网格。
    private void drawFrame(Canvas canvas, float left, float top, float right, float bottom) {
        float horizontalStep = (bottom - top) / 4f;
        float verticalStep = (right - left) / 4f;
        for (int i = 0; i <= 4; i++) {
            canvas.drawLine(left, top + horizontalStep * i, right, top + horizontalStep * i, gridPaint);
            canvas.drawLine(left + verticalStep * i, top, left + verticalStep * i, bottom, gridPaint);
        }
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);
    }

    // 把时间映射到横坐标，和主图共用同一时间轴。
    private float mapX(long timestamp, long start, long end, float left, float right) {
        double ratio = (double) (timestamp - start) / Math.max(1d, (double) (end - start));
        ratio = Math.max(0d, Math.min(1d, ratio));
        return (float) (left + ratio * (right - left));
    }

    // 把收益率映射到纵坐标。
    private float mapY(double value, double min, double max, float top, float bottom) {
        double ratio = (value - min) / Math.max(1e-9, max - min);
        return (float) (bottom - ratio * (bottom - top));
    }

    // dp 转像素。
    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // 应用透明度。
    private int applyAlpha(int color, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (safeAlpha << 24);
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

    // 按时间找到最近的日收益柱。
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

    private float resolveHighlightX(long startTs, long endTs) {
        if (highlightedXRatio >= 0f) {
            return chartLeft + highlightedXRatio * (chartRight - chartLeft);
        }
        if (highlightedIndex >= 0 && highlightedIndex < points.size()) {
            return mapX(points.get(highlightedIndex).getTimestamp(), startTs, endTs, chartLeft, chartRight);
        }
        return chartLeft;
    }

    private void drawXLabels(Canvas canvas, float left, float right, float bottom, long startTs, long endTs) {
        String start = formatLabelTime(startTs);
        String middle = formatLabelTime(startTs + (endTs - startTs) / 2L);
        String end = formatLabelTime(endTs);
        canvas.drawText(start, left + labelPaint.measureText(start) / 2f, bottom + dp(12f), labelPaint);
        canvas.drawText(middle, left + (right - left) / 2f, bottom + dp(12f), labelPaint);
        canvas.drawText(end, right - labelPaint.measureText(end) / 2f, bottom + dp(12f), labelPaint);
    }

    private String formatLabelTime(long timestamp) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(timestamp);
    }

    private float clampRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }
}

/*
 * 历史交易分布图，负责绘制“最大回撤 vs 收益率”散点。
 * 供 AccountStatsBridgeActivity 展示交易整体分布特征。
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

import com.binance.monitor.R;
import com.binance.monitor.ui.theme.TextAppearanceScaleResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TradeDistributionScatterView extends View {

    private final List<CurveAnalyticsHelper.TradeScatterPoint> points = new ArrayList<>();
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint positivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint negativePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gestureDetector;
    private final List<ScatterDrawItem> drawItems = new ArrayList<>();
    private float zeroX;
    private float zeroY;

    private float chartLeft;
    private float chartRight;
    private float chartTop;
    private float chartBottom;
    private double chartMinX;
    private double chartMaxX;
    private double chartMinY;
    private double chartMaxY;
    private int selectedIndex = -1;
    private boolean masked;

    public TradeDistributionScatterView(Context context) {
        this(context, null);
    }

    public TradeDistributionScatterView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TradeDistributionScatterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        axisPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStyle(Paint.Style.STROKE);
        TextAppearanceScaleResolver.applyTextSize(labelPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        TextAppearanceScaleResolver.applyTextSize(emptyPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dp(1.4f));
        tooltipBgPaint.setStyle(Paint.Style.FILL);
        TextAppearanceScaleResolver.applyTextSize(tooltipTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartCompact);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                selectNearestPoint(e.getX(), e.getY());
                return true;
            }
        });
        refreshPalette();
    }

    // 刷新主题色。
    public void refreshPalette() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());
        axisPaint.setColor(applyAlpha(palette.textSecondary, 180));
        axisPaint.setStrokeWidth(dp(1f));
        gridPaint.setColor(applyAlpha(palette.stroke, 150));
        gridPaint.setStrokeWidth(dp(0.8f));
        labelPaint.setColor(palette.textSecondary);
        positivePaint.setColor(applyAlpha(palette.rise, 220));
        negativePaint.setColor(applyAlpha(palette.fall, 220));
        highlightPaint.setColor(applyAlpha(palette.primary, 230));
        tooltipBgPaint.setColor(applyAlpha(palette.card, 244));
        tooltipTextPaint.setColor(palette.textPrimary);
        emptyPaint.setColor(palette.textSecondary);
        invalidate();
    }

    // 设置散点数据。
    public void setPoints(@Nullable List<CurveAnalyticsHelper.TradeScatterPoint> source) {
        points.clear();
        if (source != null) {
            points.addAll(source);
        }
        selectedIndex = -1;
        rebuildDrawItems();
        invalidate();
    }

    // 根据隐私状态切换为占位态。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        selectedIndex = -1;
        rebuildDrawItems();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (masked) {
            selectedIndex = -1;
            return false;
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
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
        if (width <= 0f || height <= 0f || masked || points.isEmpty()) {
            return;
        }
        chartLeft = dp(22f);
        chartRight = width - dp(16f);
        chartTop = dp(12f);
        chartBottom = height - dp(24f);

        chartMinX = -0.05d;
        chartMaxX = 0d;
        chartMinY = -0.05d;
        chartMaxY = 0.05d;
        for (CurveAnalyticsHelper.TradeScatterPoint point : points) {
            chartMinX = Math.min(chartMinX, point.getMaxDrawdownRate());
            chartMaxY = Math.max(chartMaxY, point.getReturnRate());
            chartMinY = Math.min(chartMinY, point.getReturnRate());
        }
        chartMaxY = Math.max(chartMaxY, 0.02d);
        chartMinY = Math.min(chartMinY, -0.02d);
        chartMinX = Math.min(chartMinX * 1.08d, -0.01d);
        zeroX = mapX(0d, chartMinX, chartMaxX, chartLeft, chartRight);
        zeroY = mapY(0d, chartMinY, chartMaxY, chartTop, chartBottom);

        for (CurveAnalyticsHelper.TradeScatterPoint point : points) {
            drawItems.add(new ScatterDrawItem(
                    mapX(point.getMaxDrawdownRate(), chartMinX, chartMaxX, chartLeft, chartRight),
                    mapY(point.getReturnRate(), chartMinY, chartMaxY, chartTop, chartBottom),
                    point.isPositive(),
                    point.isHighlight()
            ));
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

        if (points.isEmpty()) {
            canvas.drawText("暂无交易分布数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        if (drawItems.isEmpty()) {
            rebuildDrawItems();
        }
        drawFrame(canvas, chartLeft, chartTop, chartRight, chartBottom);
        canvas.drawLine(chartLeft, zeroY, chartRight, zeroY, axisPaint);
        canvas.drawLine(zeroX, chartTop, zeroX, chartBottom, axisPaint);

        for (int i = 0; i < drawItems.size(); i++) {
            ScatterDrawItem item = drawItems.get(i);
            Paint fillPaint = item.positive ? positivePaint : negativePaint;
            float radius = item.highlight || i == selectedIndex ? dp(4.2f) : dp(3.0f);
            canvas.drawCircle(item.cx, item.cy, radius, fillPaint);
            if (item.highlight || i == selectedIndex) {
                canvas.drawCircle(item.cx, item.cy, radius + dp(1.6f), highlightPaint);
            }
        }

        if (selectedIndex >= 0 && selectedIndex < points.size()) {
            drawSelectedTooltip(canvas, points.get(selectedIndex));
        }

        canvas.drawText(String.format(Locale.getDefault(), "%.1f%%", chartMinX * 100d),
                chartLeft, chartBottom + dp(14f), labelPaint);
        canvas.drawText("0%", getWidth() - dp(20f), chartBottom + dp(14f), labelPaint);
        canvas.drawText(String.format(Locale.getDefault(), "+%.1f%%", chartMaxY * 100d),
                dp(4f), chartTop + dp(2f), labelPaint);
        canvas.drawText(String.format(Locale.getDefault(), "%.1f%%", chartMinY * 100d),
                dp(4f), chartBottom + dp(2f), labelPaint);
        String axisTitle = "最大回撤";
        float titleWidth = labelPaint.measureText(axisTitle);
        canvas.drawText(axisTitle,
                chartLeft + (chartRight - chartLeft - titleWidth) / 2f,
                chartBottom + dp(14f),
                labelPaint);
        canvas.save();
        canvas.rotate(-90f, dp(10f), chartTop + (chartBottom - chartTop) / 2f);
        canvas.drawText("收益率", dp(10f), chartTop + (chartBottom - chartTop) / 2f, labelPaint);
        canvas.restore();
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

    // 把横轴数据映射到视图坐标。
    private float mapX(double value, double min, double max, float left, float right) {
        double ratio = (value - min) / Math.max(1e-9, max - min);
        return (float) (left + ratio * (right - left));
    }

    // 把纵轴数据映射到视图坐标。
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
        return androidx.core.graphics.ColorUtils.setAlphaComponent(color, safeAlpha);
    }

    // 选中离点击位置最近的散点，超出热区则清空选择。
    private void selectNearestPoint(float touchX, float touchY) {
        if (points.isEmpty()) {
            return;
        }
        int bestIndex = -1;
        float bestDistance = Float.MAX_VALUE;
        float hitRadius = dp(18f);
        for (int i = 0; i < points.size(); i++) {
            if (drawItems.isEmpty()) {
                rebuildDrawItems();
            }
            ScatterDrawItem item = i < drawItems.size() ? drawItems.get(i) : null;
            if (item == null) {
                continue;
            }
            float distance = (float) Math.hypot(touchX - item.cx, touchY - item.cy);
            if (distance <= hitRadius && distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        selectedIndex = bestIndex;
        invalidate();
    }

    // 绘制点击散点后的详情浮窗。
    private void drawSelectedTooltip(Canvas canvas, CurveAnalyticsHelper.TradeScatterPoint point) {
        float anchorX = mapX(point.getMaxDrawdownRate(), chartMinX, chartMaxX, chartLeft, chartRight);
        float anchorY = mapY(point.getReturnRate(), chartMinY, chartMaxY, chartTop, chartBottom);
        List<String> lines = Arrays.asList(
                point.getLabel(),
                "收益率 " + String.format(Locale.getDefault(), "%+.2f%%", point.getReturnRate() * 100d),
                "收益额 " + FormatUtils.formatSignedMoney(point.getProfitAmount()),
                "最大回撤 " + String.format(Locale.getDefault(), "%.2f%%", point.getMaxDrawdownRate() * 100d),
                "开仓 $" + FormatUtils.formatPrice(point.getOpenPrice()),
                "平仓 $" + FormatUtils.formatPrice(point.getClosePrice()),
                "时间 " + FormatUtils.formatDateTime(point.getOpenTime()) + " -> " + FormatUtils.formatDateTime(point.getCloseTime()),
                "持仓 " + formatHoldingDuration(point.getHoldingDurationMs())
        );
        float maxWidth = 0f;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, tooltipTextPaint.measureText(line));
        }
        float padding = dp(6f);
        float lineStepPx = dp(10.5f);
        float boxWidth = Math.min(maxWidth + padding * 2, getWidth() - dp(24f));
        float boxHeight = lineStepPx * lines.size() + padding * 2;
        float boxLeft = anchorX + dp(10f);
        if (boxLeft + boxWidth > getWidth() - dp(8f)) {
            boxLeft = anchorX - boxWidth - dp(10f);
        }
        boxLeft = Math.max(dp(8f), boxLeft);
        float boxTop = anchorY - boxHeight / 2f;
        boxTop = Math.max(dp(8f), Math.min(getHeight() - boxHeight - dp(8f), boxTop));
        RectF rect = new RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight);
        canvas.drawRoundRect(rect, dp(2f), dp(2f), tooltipBgPaint);
        for (int i = 0; i < lines.size(); i++) {
            canvas.drawText(lines.get(i),
                    boxLeft + padding,
                    boxTop + padding + lineStepPx * (i + 0.82f),
                    tooltipTextPaint);
        }
    }

    // 将持仓毫秒数转成更易读的中文时长。
    private String formatHoldingDuration(long durationMs) {
        if (durationMs <= 0L) {
            return "--";
        }
        long totalMinutes = durationMs / 60_000L;
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0L) {
            builder.append(days).append("天");
        }
        if (hours > 0L) {
            builder.append(hours).append("小时");
        }
        if (minutes > 0L || builder.length() == 0) {
            builder.append(minutes).append("分钟");
        }
        return builder.toString();
    }

    private static final class ScatterDrawItem {
        final float cx;
        final float cy;
        final boolean positive;
        final boolean highlight;

        private ScatterDrawItem(float cx, float cy, boolean positive, boolean highlight) {
            this.cx = cx;
            this.cy = cy;
            this.positive = positive;
            this.highlight = highlight;
        }
    }
}

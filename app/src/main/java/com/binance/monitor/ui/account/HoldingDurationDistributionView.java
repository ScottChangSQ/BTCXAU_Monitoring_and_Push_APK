/*
 * 持仓时间分布图，负责绘制交易在不同持仓时长桶中的数量。
 * 供 AccountStatsBridgeActivity 展示策略更偏短线还是持有型。
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HoldingDurationDistributionView extends View {

    private final List<CurveAnalyticsHelper.DurationBucket> buckets = new ArrayList<>();
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HoldingDurationDistributionView(Context context) {
        this(context, null);
    }

    public HoldingDurationDistributionView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HoldingDurationDistributionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        axisPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStyle(Paint.Style.STROKE);
        labelPaint.setTextSize(dp(8f));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(dp(8.2f));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextSize(dp(10f));
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
        valuePaint.setColor(palette.textPrimary);
        barPaint.setColor(applyAlpha(palette.primary, 220));
        emptyPaint.setColor(palette.textSecondary);
        invalidate();
    }

    // 设置持仓时长分布数据。
    public void setBuckets(@Nullable List<CurveAnalyticsHelper.DurationBucket> source) {
        buckets.clear();
        if (source != null) {
            buckets.addAll(source);
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

        float left = dp(16f);
        float right = width - dp(12f);
        float top = dp(12f);
        float bottom = height - dp(34f);
        drawFrame(canvas, left, top, right, bottom);

        if (buckets.isEmpty()) {
            canvas.drawText("暂无持仓时长数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        int maxCount = 1;
        for (CurveAnalyticsHelper.DurationBucket bucket : buckets) {
            maxCount = Math.max(maxCount, bucket.getCount());
        }

        float slotWidth = (right - left) / Math.max(1, buckets.size());
        float barWidth = Math.max(dp(12f), slotWidth * 0.52f);
        for (int i = 0; i < buckets.size(); i++) {
            CurveAnalyticsHelper.DurationBucket bucket = buckets.get(i);
            float centerX = left + slotWidth * i + slotWidth / 2f;
            float barTop = bottom - (bottom - top - dp(8f)) * (bucket.getCount() / (float) maxCount);
            RectF rect = new RectF(centerX - barWidth / 2f, barTop, centerX + barWidth / 2f, bottom);
            canvas.drawRoundRect(rect, dp(2f), dp(2f), barPaint);
            canvas.drawText(String.valueOf(bucket.getCount()), centerX, Math.max(top + dp(10f), barTop - dp(4f)), valuePaint);
            drawMultilineLabel(canvas, bucket.getLabel(), centerX, bottom + dp(12f));
        }

        canvas.drawText(String.format(Locale.getDefault(), "最高 %d 笔", maxCount),
                left + dp(24f), top + dp(2f), labelPaint);
    }

    // 绘制基础网格。
    private void drawFrame(Canvas canvas, float left, float top, float right, float bottom) {
        float horizontalStep = (bottom - top) / 4f;
        for (int i = 0; i <= 4; i++) {
            canvas.drawLine(left, top + horizontalStep * i, right, top + horizontalStep * i, gridPaint);
        }
        canvas.drawLine(left, top, left, bottom, axisPaint);
        canvas.drawLine(left, bottom, right, bottom, axisPaint);
    }

    // 绘制两行桶标签，避免横向挤压太重。
    private void drawMultilineLabel(Canvas canvas, String label, float centerX, float baselineY) {
        if (label == null || label.trim().isEmpty()) {
            return;
        }
        String[] parts = label.replace("-", "-\n").split("\n");
        if (parts.length <= 1) {
            canvas.drawText(label, centerX, baselineY, labelPaint);
            return;
        }
        canvas.drawText(parts[0], centerX, baselineY - dp(2f), labelPaint);
        canvas.drawText(parts[1], centerX, baselineY + dp(8f), labelPaint);
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
}

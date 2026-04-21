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

import com.binance.monitor.R;
import com.binance.monitor.ui.theme.TextAppearanceScaleResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HoldingDurationDistributionView extends View {

    private final List<CurveAnalyticsHelper.DurationBucket> buckets = new ArrayList<>();
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint winBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lossBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean masked;

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
        TextAppearanceScaleResolver.applyTextSize(labelPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        TextAppearanceScaleResolver.applyTextSize(valuePaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setTextAlign(Paint.Align.CENTER);
        TextAppearanceScaleResolver.applyTextSize(emptyPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
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
        winBarPaint.setColor(applyAlpha(palette.rise, 225));
        lossBarPaint.setColor(applyAlpha(palette.fall, 225));
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
        float top = dp(12f);
        float bottom = height - dp(24f);
        drawFrame(canvas, left, top, right, bottom);

        if (masked) {
            canvas.drawText("****", width / 2f, height / 2f, emptyPaint);
            return;
        }

        if (buckets.isEmpty()) {
            canvas.drawText("暂无持仓时长数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        int maxCount = 1;
        for (CurveAnalyticsHelper.DurationBucket bucket : buckets) {
            maxCount = Math.max(maxCount, bucket.getCount());
        }

        float slotWidth = (right - left) / Math.max(1, buckets.size());
        float barWidth = Math.max(dp(12f), slotWidth * 0.48f);
        for (int i = 0; i < buckets.size(); i++) {
            CurveAnalyticsHelper.DurationBucket bucket = buckets.get(i);
            float centerX = left + slotWidth * i + slotWidth / 2f;
            float usableHeight = bottom - top - dp(14f);
            float totalTop = bottom - usableHeight * (bucket.getCount() / (float) maxCount);
            float lossTop = bottom - usableHeight * (bucket.getLossCount() / (float) maxCount);
            float winTop = bottom - usableHeight * ((bucket.getLossCount() + bucket.getWinCount()) / (float) maxCount);
            if (bucket.getLossCount() > 0) {
                RectF lossRect = new RectF(centerX - barWidth / 2f, lossTop, centerX + barWidth / 2f, bottom);
                canvas.drawRect(lossRect, lossBarPaint);
            }
            if (bucket.getWinCount() > 0) {
                float winBottom = bucket.getLossCount() > 0 ? lossTop : bottom;
                RectF winRect = new RectF(centerX - barWidth / 2f, Math.min(winTop, winBottom), centerX + barWidth / 2f, winBottom);
                canvas.drawRect(winRect, winBarPaint);
            }
            canvas.drawText(
                    String.format(Locale.getDefault(), "%d(%d/%d)",
                            bucket.getCount(),
                            bucket.getWinCount(),
                            bucket.getLossCount()),
                    centerX,
                    Math.max(top + dp(10f), totalTop - dp(4f)),
                    valuePaint);
            drawSingleLineLabel(canvas, bucket.getLabel(), centerX, bottom + dp(12f));
        }

        canvas.drawText(String.format(Locale.getDefault(), "最高 %d 笔", maxCount),
                left + dp(22f), top + dp(2f), labelPaint);
        canvas.drawText("总(盈/亏)", right - dp(26f), top + dp(2f), labelPaint);
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

    // 绘制单行桶标签，避免横轴文案换行。
    private void drawSingleLineLabel(Canvas canvas, String label, float centerX, float baselineY) {
        if (label == null || label.trim().isEmpty()) {
            return;
        }
        canvas.drawText(label, centerX, baselineY, labelPaint);
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
}

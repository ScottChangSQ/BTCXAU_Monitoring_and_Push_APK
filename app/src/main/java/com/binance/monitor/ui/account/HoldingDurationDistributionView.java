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
    private final List<BucketDrawItem> drawItems = new ArrayList<>();
    private float preparedLeft;
    private float preparedTop;
    private float preparedRight;
    private float preparedBottom;
    private int preparedMaxCount = 1;
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
        rebuildDrawItems();
        invalidate();
    }

    // 根据隐私状态切换为占位态。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        rebuildDrawItems();
        invalidate();
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
        if (width <= 0f || height <= 0f || masked || buckets.isEmpty()) {
            return;
        }
        preparedLeft = dp(22f);
        preparedRight = width - dp(16f);
        preparedTop = dp(12f);
        preparedBottom = height - dp(24f);

        preparedMaxCount = 1;
        for (CurveAnalyticsHelper.DurationBucket bucket : buckets) {
            preparedMaxCount = Math.max(preparedMaxCount, bucket.getCount());
        }

        float slotWidth = (preparedRight - preparedLeft) / Math.max(1, buckets.size());
        float barWidth = Math.max(dp(12f), slotWidth * 0.48f);
        float usableHeight = preparedBottom - preparedTop - dp(14f);
        for (int i = 0; i < buckets.size(); i++) {
            CurveAnalyticsHelper.DurationBucket bucket = buckets.get(i);
            float centerX = preparedLeft + slotWidth * i + slotWidth / 2f;
            float totalTop = preparedBottom - usableHeight * (bucket.getCount() / (float) preparedMaxCount);
            float lossTop = preparedBottom - usableHeight * (bucket.getLossCount() / (float) preparedMaxCount);
            float winTop = preparedBottom - usableHeight * ((bucket.getLossCount() + bucket.getWinCount()) / (float) preparedMaxCount);
            RectF lossRect = bucket.getLossCount() > 0
                    ? new RectF(centerX - barWidth / 2f, lossTop, centerX + barWidth / 2f, preparedBottom)
                    : null;
            float winBottom = bucket.getLossCount() > 0 ? lossTop : preparedBottom;
            RectF winRect = bucket.getWinCount() > 0
                    ? new RectF(centerX - barWidth / 2f, Math.min(winTop, winBottom), centerX + barWidth / 2f, winBottom)
                    : null;
            String valueText = String.format(Locale.getDefault(), "%d(%d/%d)",
                    bucket.getCount(),
                    bucket.getWinCount(),
                    bucket.getLossCount());
            drawItems.add(new BucketDrawItem(
                    lossRect,
                    winRect,
                    valueText,
                    bucket.getLabel(),
                    centerX,
                    Math.max(preparedTop + dp(10f), totalTop - dp(4f)),
                    preparedBottom + dp(12f)
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

        if (buckets.isEmpty()) {
            canvas.drawText("暂无持仓时长数据", width / 2f, height / 2f, emptyPaint);
            return;
        }

        if (drawItems.isEmpty()) {
            rebuildDrawItems();
        }
        drawFrame(canvas, preparedLeft, preparedTop, preparedRight, preparedBottom);
        for (BucketDrawItem item : drawItems) {
            if (item.lossRect != null) {
                canvas.drawRect(item.lossRect, lossBarPaint);
            }
            if (item.winRect != null) {
                canvas.drawRect(item.winRect, winBarPaint);
            }
            canvas.drawText(item.valueText, item.centerX, item.valueY, valuePaint);
            drawSingleLineLabel(canvas, item.labelText, item.centerX, item.labelY);
        }

        canvas.drawText(String.format(Locale.getDefault(), "最高 %d 笔", preparedMaxCount),
                preparedLeft + dp(22f), preparedTop + dp(2f), labelPaint);
        canvas.drawText("总(盈/亏)", preparedRight - dp(26f), preparedTop + dp(2f), labelPaint);
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

    private static final class BucketDrawItem {
        @Nullable
        final RectF lossRect;
        @Nullable
        final RectF winRect;
        final String valueText;
        final String labelText;
        final float centerX;
        final float valueY;
        final float labelY;

        private BucketDrawItem(@Nullable RectF lossRect,
                               @Nullable RectF winRect,
                               String valueText,
                               String labelText,
                               float centerX,
                               float valueY,
                               float labelY) {
            this.lossRect = lossRect;
            this.winRect = winRect;
            this.valueText = valueText;
            this.labelText = labelText;
            this.centerX = centerX;
            this.valueY = valueY;
            this.labelY = labelY;
        }
    }
}

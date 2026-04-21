package com.binance.monitor.ui.account;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.binance.monitor.R;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.ui.theme.SpacingTokenResolver;
import com.binance.monitor.ui.theme.TextAppearanceScaleResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EquityCurveView extends View {

    public interface OnPointHighlightListener {
        void onPointHighlight(@Nullable CurvePoint point, float xRatio);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint equityPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint balancePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeroPercentLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drawdownFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drawdownStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drawdownBoundaryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drawdownPeakMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drawdownValleyMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path equityPath = new Path();
    private final Path balancePath = new Path();
    private final Path drawdownDiamondPath = new Path();
    private final List<CurvePoint> points = new ArrayList<>();
    private final List<String> tooltipExtraLines = new ArrayList<>();
    private final GestureDetector gestureDetector;
    @Nullable
    private CurvePoint tooltipPointOverride;

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
    private float highlightedXRatio = -1f;
    private long highlightedTimestamp = -1L;
    private boolean longPressing;
    private boolean masked;
    private boolean showBottomTimeLabels = true;
    private boolean mergeWithPreviousPane;
    private boolean mergeWithNextPane;
    private OnPointHighlightListener onPointHighlightListener;

    public EquityCurveView(Context context) {
        this(context, null);
    }

    public EquityCurveView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EquityCurveView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TextAppearanceScaleResolver.applyTextSize(labelPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);

        equityPaint.setStyle(Paint.Style.STROKE);
        equityPaint.setStrokeWidth(SpacingTokenResolver.dpFloat(getContext(), CurvePaneLayoutHelper.resolveEquityStrokeRes()));
        equityPaint.setPathEffect(new DashPathEffect(new float[]{dp(3f), dp(2f)}, 0f));

        balancePaint.setStyle(Paint.Style.STROKE);
        balancePaint.setStrokeWidth(SpacingTokenResolver.dpFloat(getContext(), CurvePaneLayoutHelper.resolveBalanceStrokeRes()));
        balancePaint.setPathEffect(null);

        markerPaint.setStyle(Paint.Style.FILL);

        crosshairPaint.setStrokeWidth(dp(1f));
        zeroPercentLinePaint.setStyle(Paint.Style.STROKE);
        zeroPercentLinePaint.setStrokeWidth(dp(1f));
        zeroPercentLinePaint.setPathEffect(null);

        drawdownFillPaint.setStyle(Paint.Style.FILL);
        drawdownStrokePaint.setStyle(Paint.Style.STROKE);
        drawdownBoundaryPaint.setStyle(Paint.Style.STROKE);
        drawdownPeakMarkerPaint.setStyle(Paint.Style.FILL);
        drawdownValleyMarkerPaint.setStyle(Paint.Style.FILL);
        drawdownStrokePaint.setStrokeWidth(dp(1.6f));
        drawdownBoundaryPaint.setStrokeWidth(dp(1f));
        tooltipBgPaint.setStyle(Paint.Style.FILL);
        TextAppearanceScaleResolver.applyTextSize(tooltipTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartCompact);

        emptyPaint.setTextAlign(Paint.Align.CENTER);
        TextAppearanceScaleResolver.applyTextSize(emptyPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        refreshPalette();

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
        axisPaint.setStrokeWidth(SpacingTokenResolver.dpFloat(getContext(), CurvePaneLayoutHelper.resolveAxisStrokeRes()));
        gridPaint.setColor(applyAlpha(palette.stroke, 170));
        equityPaint.setColor(palette.primary);
        equityPaint.setPathEffect(new DashPathEffect(new float[]{dp(3f), dp(2f)}, 0f));
        balancePaint.setColor(applyAlpha(palette.textPrimary, 232));
        balancePaint.setStrokeWidth(SpacingTokenResolver.dpFloat(getContext(), CurvePaneLayoutHelper.resolveBalanceStrokeRes()));
        balancePaint.setPathEffect(null);
        applyDrawdownPalette(palette);
        crosshairPaint.setColor(applyAlpha(palette.textSecondary, 220));
        zeroPercentLinePaint.setColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.text_inverse));
        tooltipBgPaint.setColor(applyAlpha(palette.card, 240));
        tooltipTextPaint.setColor(palette.textPrimary);
        emptyPaint.setColor(palette.textSecondary);
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
        tooltipExtraLines.clear();
        tooltipPointOverride = null;
        highlightedIndex = -1;
        highlightedXRatio = -1f;
        longPressing = false;
        dispatchHighlightedPoint();
        invalidate();
    }

    public void setBaseBalance(double value) {
        baseBalance = Math.max(1e-9, value);
        invalidate();
    }

    // 控制时间刻度放在主图还是最底部附图，便于多图共用同一横轴。
    public void setShowBottomTimeLabels(boolean show) {
        if (showBottomTimeLabels == show) {
            return;
        }
        showBottomTimeLabels = show;
        invalidate();
    }

    // 控制是否与上一张图共享边界。
    public void setMergeWithPreviousPane(boolean merge) {
        if (mergeWithPreviousPane == merge) {
            return;
        }
        mergeWithPreviousPane = merge;
        invalidate();
    }

    // 控制是否与下一张图共享边界。
    public void setMergeWithNextPane(boolean merge) {
        if (mergeWithNextPane == merge) {
            return;
        }
        mergeWithNextPane = merge;
        invalidate();
    }

    // 根据账户统计页隐私状态切换为占位态。
    public void setMasked(boolean masked) {
        if (this.masked == masked) {
            return;
        }
        this.masked = masked;
        clearSyncedHighlight();
        dispatchHighlightedPoint();
        invalidate();
    }

    public void setDrawdownHighlight(long startTs, long endTs, double peakBalance, double valleyBalance) {
        drawdownStartTs = Math.max(0L, startTs);
        drawdownEndTs = Math.max(0L, endTs);
        drawdownPeakBalance = peakBalance;
        drawdownValleyBalance = valleyBalance;
        invalidate();
    }

    // 同步外部共享十字光标时，给顶部弹窗补充附图数据。
    public void setTooltipExtraLines(@Nullable List<String> lines) {
        tooltipExtraLines.clear();
        if (lines != null) {
            tooltipExtraLines.addAll(lines);
        }
        invalidate();
    }

    // 允许宿主按当前横轴位置覆盖弹窗里的净值、结余等数值，避免附图长按时一直停在旧点上。
    public void setTooltipPointOverride(@Nullable CurvePoint point) {
        tooltipPointOverride = point;
        invalidate();
    }

    // 由宿主一次性同步主图高亮点与弹窗插值数据，避免先后两次更新时回到旧值。
    public void syncHighlightPoint(@Nullable CurvePoint point, long timestamp, float xRatio) {
        tooltipPointOverride = point;
        syncHighlightTimestamp(timestamp, xRatio);
    }

    // 由宿主把共享时间戳同步到主图，不触发反向回调。
    public void syncHighlightTimestamp(long timestamp, float xRatio) {
        if (timestamp <= 0L || points.isEmpty()) {
            clearSyncedHighlight();
            return;
        }
        highlightedIndex = Math.max(0, Math.min(points.size() - 1, findNearestIndexByTimestamp(timestamp)));
        highlightedTimestamp = timestamp;
        highlightedXRatio = clampRatio(xRatio);
        invalidate();
    }

    // 清除宿主同步过来的十字光标状态。
    public void clearSyncedHighlight() {
        highlightedIndex = -1;
        highlightedXRatio = -1f;
        highlightedTimestamp = -1L;
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

        chartLeft = SpacingTokenResolver.dpFloat(getContext(), CurvePaneLayoutHelper.resolveChartLeftInsetRes());
        chartTop = CurvePaneSpacingHelper.resolveTopInsetPx(mergeWithPreviousPane, dp(12f));
        chartRight = width - SpacingTokenResolver.dpFloat(getContext(), CurvePaneLayoutHelper.resolveChartRightInsetRes());
        chartBottom = height - CurvePaneSpacingHelper.resolveBottomInsetPx(
                mergeWithNextPane,
                showBottomTimeLabels,
                dp(10f),
                dp(24f)
        );

        drawGrid(canvas, chartLeft, chartTop, chartRight, chartBottom);
        drawAxes(canvas, chartLeft, chartTop, chartRight, chartBottom);
        if (masked) {
            canvas.drawText("****", width / 2f, height / 2f, emptyPaint);
            return;
        }
        if (points.size() < 2) {
            canvas.drawText("暂无曲线数据", width / 2f, height / 2f, emptyPaint);
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

        drawZeroPercentReferenceLine(canvas, chartLeft, chartRight, chartTop, chartBottom, chartMin, chartMax, baseBalance);
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

        drawYLabels(canvas, chartLeft, chartRight, chartTop, chartBottom, chartMin, chartMax, baseBalance);
        if (showBottomTimeLabels) {
            drawXLabels(canvas, chartLeft, chartRight, chartBottom);
        }
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
        float minWidth = dp(12f);
        if (area.width() < minWidth) {
            float center = (area.left + area.right) / 2f;
            area.left = Math.max(chartLeft, center - minWidth / 2f);
            area.right = Math.min(chartRight, center + minWidth / 2f);
            if (area.width() < minWidth) {
                area.left = Math.max(chartLeft, area.right - minWidth);
                area.right = Math.min(chartRight, area.left + minWidth);
            }
        }
        float peakY = mapY(drawdownPeakBalance, chartMin, chartMax, chartTop, chartBottom);
        float valleyY = mapY(drawdownValleyBalance, chartMin, chartMax, chartTop, chartBottom);
        canvas.drawRect(area, drawdownFillPaint);
        canvas.drawLine(area.left, chartTop, area.left, chartBottom, drawdownBoundaryPaint);
        canvas.drawLine(area.right, chartTop, area.right, chartBottom, drawdownBoundaryPaint);
        canvas.drawLine(startX, peakY, endX, valleyY, drawdownStrokePaint);
        drawPeakMarker(canvas, startX, peakY);
        drawValleyMarker(canvas, endX, valleyY);
        canvas.drawText("最大回撤", Math.min(chartRight - dp(34f), area.left + dp(4f)), chartTop + dp(10f), labelPaint);
    }

    private void drawHighlight(Canvas canvas, int index) {
        CurvePoint rawPoint = points.get(index);
        CurvePoint point = tooltipPointOverride != null ? tooltipPointOverride : rawPoint;
        float x = resolveHighlightX(point);
        float yEquity = mapY(point.getEquity(), chartMin, chartMax, chartTop, chartBottom);
        float yBalance = mapY(point.getBalance(), chartMin, chartMax, chartTop, chartBottom);
        canvas.drawLine(x, chartTop, x, chartBottom, crosshairPaint);

        UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());
        markerPaint.setColor(palette.primary);
        canvas.drawCircle(x, yEquity, dp(4f), markerPaint);
        markerPaint.setColor(palette.xau);
        canvas.drawCircle(x, yBalance, dp(3.5f), markerPaint);

        List<String> tooltipLines = new ArrayList<>();
        tooltipLines.add(formatLabelTime(resolveHighlightLabelTimestamp(point)));
        tooltipLines.add("净值 $" + FormatUtils.formatPrice(point.getEquity()));
        tooltipLines.add("结余 $" + FormatUtils.formatPrice(point.getBalance()));
        double pct = (point.getEquity() - baseBalance) / Math.max(1e-9, baseBalance) * 100d;
        tooltipLines.add(String.format(Locale.getDefault(), "收益 %+.2f%%", pct));
        tooltipLines.addAll(tooltipExtraLines);

        float maxWidth = 0f;
        for (String line : tooltipLines) {
            maxWidth = Math.max(maxWidth, tooltipTextPaint.measureText(line));
        }
        float padding = dp(6f);
        float lineStepPx = dp(11f);
        float boxWidth = maxWidth + padding * 2;
        float boxHeight = lineStepPx * tooltipLines.size() + padding * 2;

        float boxLeft = x + dp(8f);
        if (boxLeft + boxWidth > chartRight) {
            boxLeft = x - boxWidth - dp(8f);
        }
        boxLeft = Math.max(chartLeft, boxLeft);
        float boxTop = chartTop + dp(4f);
        RectF rect = new RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight);
        canvas.drawRoundRect(rect, dp(2f), dp(2f), tooltipBgPaint);
        for (int i = 0; i < tooltipLines.size(); i++) {
            canvas.drawText(
                    tooltipLines.get(i),
                    boxLeft + padding,
                    boxTop + padding + lineStepPx * (i + 0.8f),
                    tooltipTextPaint
            );
        }
    }

    private void updateHighlightByX(float x) {
        float range = chartRight - chartLeft;
        if (range <= 0f || points.isEmpty()) {
            return;
        }
        float clamped = Math.max(chartLeft, Math.min(chartRight, x));
        highlightedXRatio = clampRatio((clamped - chartLeft) / range);
        double ratio = (clamped - chartLeft) / range;
        long targetTs = chartStartTs + Math.round(ratio * (chartEndTs - chartStartTs));
        highlightedTimestamp = targetTs;
        int index = findNearestIndexByTimestamp(targetTs);
        index = Math.max(0, Math.min(points.size() - 1, index));
        highlightedIndex = index;
        tooltipPointOverride = CurveSeriesInterpolationHelper.interpolateCurvePoint(points, targetTs);
        dispatchHighlightedPoint();
        invalidate();
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
        highlightedXRatio = -1f;
        highlightedTimestamp = -1L;
        tooltipPointOverride = null;
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
                tooltipPointOverride != null
                        ? tooltipPointOverride
                        : (highlightedIndex >= 0 && highlightedIndex < points.size() ? points.get(highlightedIndex) : null),
                highlightedXRatio);
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

    // 在主图里额外标出基准收益 0% 所在位置，便于快速判断当前收益相对基线的位置。
    private void drawZeroPercentReferenceLine(Canvas canvas,
                                              float left,
                                              float right,
                                              float top,
                                              float bottom,
                                              double min,
                                              double max,
                                              double baseBalance) {
        if (baseBalance < min || baseBalance > max) {
            return;
        }
        float zeroLineY = mapY(baseBalance, min, max, top, bottom);
        canvas.drawLine(left, zeroLineY, right, zeroLineY, zeroPercentLinePaint);
        float percentWidth = labelPaint.measureText("0%") + dp(4f);
        canvas.drawText("0%", right - percentWidth, resolveAxisLabelBaseline(zeroLineY, top, bottom), labelPaint);
    }

    private void drawYLabels(Canvas canvas, float left, float right, float top, float bottom, double min, double max, double base) {
        int tickCount = 4;
        for (int i = 0; i <= tickCount; i++) {
            double value = min + (max - min) * i / tickCount;
            float y = mapY(value, min, max, top, bottom);
            float baseline = resolveAxisLabelBaseline(y, top, bottom);
            String amount = formatAxisAmount(value);
            canvas.drawText(amount, dp(4f), baseline, labelPaint);

            double pct = (value - base) / Math.max(1e-9, base) * 100d;
            String percent = String.format(Locale.getDefault(), "%+.1f%%", pct);
            float percentWidth = labelPaint.measureText(percent);
            canvas.drawText(percent, getWidth() - dp(4f) - percentWidth, baseline, labelPaint);
        }
    }

    // 统一约束纵轴标签与 0% 标签的基线，避免最下方文字压到下一张图。
    private float resolveAxisLabelBaseline(float y, float top, float bottom) {
        return Math.max(top + dp(9f), Math.min(bottom - dp(2f), y + dp(3f)));
    }

    private String formatAxisAmount(double value) {
        double absValue = Math.abs(value);
        if (absValue >= 1_000_000d) {
            return String.format(Locale.getDefault(), "$%.1fM", value / 1_000_000d);
        }
        if (absValue >= 1_000d) {
            return String.format(Locale.getDefault(), "$%.1fk", value / 1_000d);
        }
        return String.format(Locale.getDefault(), "$%.0f", value);
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

    private float resolveHighlightX(CurvePoint point) {
        if (highlightedXRatio >= 0f) {
            return chartLeft + highlightedXRatio * (chartRight - chartLeft);
        }
        return mapX(point.getTimestamp(), chartStartTs, chartEndTs, chartLeft, chartRight);
    }

    private long resolveHighlightLabelTimestamp(@NonNull CurvePoint point) {
        return highlightedTimestamp > 0L ? highlightedTimestamp : point.getTimestamp();
    }

    private float clampRatio(float ratio) {
        return Math.max(0f, Math.min(1f, ratio));
    }

    private void applyDrawdownPalette(@NonNull UiPaletteManager.Palette palette) {
        drawdownFillPaint.setColor(applyAlpha(palette.xau, 56));
        drawdownStrokePaint.setColor(applyAlpha(palette.xau, 225));
        drawdownBoundaryPaint.setColor(applyAlpha(palette.xau, 155));
        drawdownPeakMarkerPaint.setColor(blendColor(palette.card, palette.xau, 0.18f));
        drawdownValleyMarkerPaint.setColor(blendColor(palette.xau, palette.fall, 0.32f));
    }

    private void drawPeakMarker(Canvas canvas, float x, float y) {
        float half = dp(3.6f);
        canvas.drawRect(x - half, y - half, x + half, y + half, drawdownPeakMarkerPaint);
        canvas.drawRect(x - half, y - half, x + half, y + half, drawdownStrokePaint);
    }

    private void drawValleyMarker(Canvas canvas, float x, float y) {
        float radius = dp(4.5f);
        drawdownDiamondPath.reset();
        drawdownDiamondPath.moveTo(x, y - radius);
        drawdownDiamondPath.lineTo(x + radius, y);
        drawdownDiamondPath.lineTo(x, y + radius);
        drawdownDiamondPath.lineTo(x - radius, y);
        drawdownDiamondPath.close();
        canvas.drawPath(drawdownDiamondPath, drawdownValleyMarkerPaint);
        canvas.drawPath(drawdownDiamondPath, drawdownStrokePaint);
    }

    private int applyAlpha(int color, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        return androidx.core.graphics.ColorUtils.setAlphaComponent(color, safeAlpha);
    }

    private int blendColor(int startColor, int endColor, float ratio) {
        float safeRatio = Math.max(0f, Math.min(1f, ratio));
        return androidx.core.graphics.ColorUtils.blendARGB(startColor, endColor, safeRatio);
    }
}

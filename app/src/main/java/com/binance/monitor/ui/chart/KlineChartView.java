package com.binance.monitor.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.util.FormatUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class KlineChartView extends View {

    public static class PriceAnnotation {
        public final long anchorTimeMs;
        public final double price;
        public final String label;
        public final int color;

        public PriceAnnotation(long anchorTimeMs, double price, String label, int color) {
            this.anchorTimeMs = anchorTimeMs;
            this.price = price;
            this.label = label == null ? "" : label;
            this.color = color;
        }
    }

    public static class AggregateCostAnnotation {
        public final double price;
        public final String priceLabel;

        public AggregateCostAnnotation(double price, String priceLabel) {
            this.price = price;
            this.priceLabel = priceLabel == null ? "" : priceLabel;
        }
    }

    public static class CrosshairValue {
        public final CandleEntry candle;
        public final double cursorPrice;
        public final double macdDif;
        public final double macdDea;
        public final double macdHist;
        public final double stochK;
        public final double stochD;

        public CrosshairValue(CandleEntry candle, double cursorPrice, double macdDif, double macdDea, double macdHist, double stochK, double stochD) {
            this.candle = candle;
            this.cursorPrice = cursorPrice;
            this.macdDif = macdDif;
            this.macdDea = macdDea;
            this.macdHist = macdHist;
            this.stochK = stochK;
            this.stochD = stochD;
        }
    }

    public interface OnCrosshairListener {
        void onValue(@Nullable CrosshairValue value);
    }

    public interface OnRequestMoreListener {
        void onRequestMore(long beforeOpenTime);
    }

    public interface OnViewportStateListener {
        void onLatestCandleOutOfBoundsChanged(boolean outOfBounds);
    }

    public interface OnPricePaneLayoutListener {
        void onPricePaneLayoutChanged(int left, int top, int right, int bottom);
    }

    private final List<CandleEntry> candles = new ArrayList<>();
    private double[] bollMid = new double[0];
    private double[] bollUp = new double[0];
    private double[] bollDn = new double[0];
    private double[] macdDif = new double[0];
    private double[] macdDea = new double[0];
    private double[] macdHist = new double[0];
    private double[] stochK = new double[0];
    private double[] stochD = new double[0];

    private boolean showVolume = true;
    private boolean showMacd = true;
    private boolean showStochRsi = true;
    private boolean showBoll = true;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint upPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint downPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bollMidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bollUpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bollDnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint macdDifPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint macdDeaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stochKPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stochDPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossLabelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossLabelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestPriceGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestPriceTagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestPriceTagTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paneBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extremeHighPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extremeLowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extremeConnectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extremeLabelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint extremeLabelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayLabelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayLabelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aggregateCostLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aggregateCostTagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aggregateCostTagTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint volumeThresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF priceRect = new RectF();
    private final RectF volRect = new RectF();
    private final RectF macdRect = new RectF();
    private final RectF stochRect = new RectF();

    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private final SimpleDateFormat axisTimeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private float candleWidth;
    private float candleGap;
    private float minWidth;
    private float maxWidth;
    private float rightBlankSlots = 6.4f;
    private float offsetCandles;
    private float lastX;
    private float downX;
    private float downY;
    private final float touchSlop;
    private boolean scaling;
    private boolean longPressing;
    private boolean horizontalDragging;
    private int highlightedIndex = -1;
    private float crosshairX = Float.NaN;
    private float crosshairY = Float.NaN;
    private double crosshairPrice = Double.NaN;
    private float visibleEndFloat;
    private double visiblePriceMin;
    private double visiblePriceMax;
    private double volumeThresholdValue = Double.NaN;
    private boolean volumeThresholdVisible;

    private final List<PriceAnnotation> positionAnnotations = new ArrayList<>();
    private final List<PriceAnnotation> pendingAnnotations = new ArrayList<>();
    @Nullable
    private AggregateCostAnnotation aggregateCostAnnotation;

    private boolean requestingMore;
    private long lastRequestedBefore = Long.MIN_VALUE;
    private long lastRequestAtMs;
    private boolean latestCandleOutOfBounds;

    private OnCrosshairListener onCrosshairListener;
    private OnRequestMoreListener onRequestMoreListener;
    private OnViewportStateListener onViewportStateListener;
    private OnPricePaneLayoutListener onPricePaneLayoutListener;
    private int lastPricePaneLeft = Integer.MIN_VALUE;
    private int lastPricePaneTop = Integer.MIN_VALUE;
    private int lastPricePaneRight = Integer.MIN_VALUE;
    private int lastPricePaneBottom = Integer.MIN_VALUE;

    public KlineChartView(Context context) {
        this(context, null);
    }

    public KlineChartView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KlineChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        initPaints();
        candleWidth = dp(1.8f);
        candleGap = dp(0.7f);
        minWidth = dp(0.15f);
        maxWidth = dp(24f);

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                lastX = e.getX();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (candles.isEmpty()) return;
                longPressing = true;
                requestDisallow(true);
                updateCrosshair(e.getX(), e.getY());
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                clearCrosshair();
                return true;
            }
        });
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    private void initPaints() {
        bgPaint.setColor(0xFF0E1626);
        gridPaint.setColor(0xFF1E2D43);
        axisPaint.setColor(0xFF3D5577);
        axisPaint.setStrokeWidth(dp(1f));
        textPaint.setColor(0xFF8FA6C7);
        textPaint.setTextSize(dp(9f));
        upPaint.setColor(0xFF16C784);
        downPaint.setColor(0xFFF6465D);
        bollMidPaint.setColor(0xFFF2C94C);
        bollUpPaint.setColor(0xFFD946EF);
        bollDnPaint.setColor(0xFF8B5CF6);
        bollMidPaint.setStrokeWidth(dp(1f));
        bollUpPaint.setStrokeWidth(dp(1f));
        bollDnPaint.setStrokeWidth(dp(1f));
        line1Paint.setColor(0xFFF2C94C);
        line2Paint.setColor(0xFF8B5CF6);
        line1Paint.setStrokeWidth(dp(1f));
        line2Paint.setStrokeWidth(dp(1f));
        macdDifPaint.setColor(0xFFF2C94C);
        macdDeaPaint.setColor(0xFF8B5CF6);
        stochKPaint.setColor(0xFFF2C94C);
        stochDPaint.setColor(0xFF8B5CF6);
        macdDifPaint.setStrokeWidth(dp(1.4f));
        macdDeaPaint.setStrokeWidth(dp(1.4f));
        stochKPaint.setStrokeWidth(dp(1.3f));
        stochDPaint.setStrokeWidth(dp(1.3f));
        crossPaint.setColor(0xFF8FA6C7);
        crossPaint.setStrokeWidth(dp(1f));
        crossLabelBgPaint.setColor(0xFF1D2A3F);
        crossLabelTextPaint.setColor(0xFFE2EDFF);
        crossLabelTextPaint.setTextSize(dp(9f));
        popupBgPaint.setColor(0xEE1B2A40);
        popupTextPaint.setColor(0xFFE2EDFF);
        popupTextPaint.setTextSize(dp(9f));
        latestPriceGuidePaint.setStyle(Paint.Style.STROKE);
        latestPriceGuidePaint.setStrokeWidth(dp(1f));
        latestPriceGuidePaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(3f)}, 0f));
        latestPriceTagPaint.setColor(0xFF16C784);
        latestPriceTagTextPaint.setColor(0xFFFFFFFF);
        latestPriceTagTextPaint.setTextSize(dp(9f));
        latestPriceTagTextPaint.setFakeBoldText(true);
        paneBorderPaint.setStyle(Paint.Style.STROKE);
        paneBorderPaint.setStrokeWidth(dp(1f));
        paneBorderPaint.setColor(0xFF3D5577);
        extremeHighPaint.setColor(0xFFF2C94C);
        extremeHighPaint.setStyle(Paint.Style.FILL);
        extremeLowPaint.setColor(0xFF22D3EE);
        extremeLowPaint.setStyle(Paint.Style.FILL);
        extremeConnectorPaint.setStyle(Paint.Style.STROKE);
        extremeConnectorPaint.setStrokeWidth(dp(1f));
        extremeLabelBgPaint.setColor(0xCC1D2A3F);
        extremeLabelTextPaint.setColor(0xFFE2EDFF);
        extremeLabelTextPaint.setTextSize(dp(8f));
        extremeLabelTextPaint.setFakeBoldText(true);

        overlayDashPaint.setStyle(Paint.Style.STROKE);
        overlayDashPaint.setStrokeWidth(dp(0.55f));
        overlayDashPaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(3f)}, 0f));
        overlayPointPaint.setStyle(Paint.Style.FILL);
        overlayLabelBgPaint.setColor(0xCC1D2A3F);
        overlayLabelTextPaint.setTextSize(dp(8f));
        overlayLabelTextPaint.setFakeBoldText(true);

        aggregateCostLinePaint.setStyle(Paint.Style.STROKE);
        aggregateCostLinePaint.setStrokeWidth(dp(1.1f));
        aggregateCostLinePaint.setColor(0xE6FFFFFF);
        aggregateCostTagPaint.setColor(0xE6FFFFFF);
        aggregateCostTagTextPaint.setColor(0xFF111827);
        aggregateCostTagTextPaint.setTextSize(dp(8.5f));
        aggregateCostTagTextPaint.setFakeBoldText(true);

        volumeThresholdPaint.setStyle(Paint.Style.STROKE);
        volumeThresholdPaint.setStrokeWidth(dp(1f));
        volumeThresholdPaint.setColor(0xE6FFFFFF);
        volumeThresholdPaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(3f)}, 0f));
    }

    public void setOnCrosshairListener(@Nullable OnCrosshairListener listener) {
        onCrosshairListener = listener;
    }

    public void setOnRequestMoreListener(@Nullable OnRequestMoreListener listener) {
        onRequestMoreListener = listener;
    }

    public void setOnViewportStateListener(@Nullable OnViewportStateListener listener) {
        onViewportStateListener = listener;
        dispatchViewportState(true);
    }

    public void setOnPricePaneLayoutListener(@Nullable OnPricePaneLayoutListener listener) {
        onPricePaneLayoutListener = listener;
        if (listener == null) {
            return;
        }
        if (ensureLayoutForMath()) {
            dispatchPricePaneLayout(true);
        }
    }

    public void setVolumeThreshold(double threshold, boolean visible) {
        volumeThresholdValue = threshold;
        volumeThresholdVisible = visible && threshold > 0d;
        invalidate();
    }

    public void setPositionAnnotations(@Nullable List<PriceAnnotation> items) {
        positionAnnotations.clear();
        if (items != null && !items.isEmpty()) {
            positionAnnotations.addAll(items);
        }
        invalidate();
    }

    public void setPendingAnnotations(@Nullable List<PriceAnnotation> items) {
        pendingAnnotations.clear();
        if (items != null && !items.isEmpty()) {
            pendingAnnotations.addAll(items);
        }
        invalidate();
    }

    public void setAggregateCostAnnotation(@Nullable AggregateCostAnnotation annotation) {
        aggregateCostAnnotation = annotation;
        invalidate();
    }

    public void notifyLoadMoreFinished() {
        requestingMore = false;
    }

    public boolean isUserInteracting() {
        return horizontalDragging || scaling;
    }

    public boolean hasActiveCrosshair() {
        return longPressing && highlightedIndex >= 0 && highlightedIndex < candles.size();
    }

    public boolean isLatestCandleOutOfBounds() {
        return latestCandleOutOfBounds;
    }

    public void scrollToLatest() {
        if (candles.isEmpty()) {
            return;
        }
        offsetCandles = 0f;
        clampOffset();
        if (longPressing && !Float.isNaN(crosshairX) && !Float.isNaN(crosshairY)) {
            updateCrosshair(crosshairX, crosshairY);
        } else {
            notifyCrosshair();
            requestFrame();
        }
    }

    public void setIndicatorsVisible(boolean volume, boolean macd, boolean stochRsi, boolean boll) {
        showVolume = volume;
        showMacd = macd;
        showStochRsi = stochRsi;
        showBoll = boll;
        invalidate();
    }

    public void setCandles(@Nullable List<CandleEntry> items) {
        candles.clear();
        if (items != null) {
            candles.addAll(items);
            Collections.sort(candles, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        }
        offsetCandles = 0f;
        requestingMore = false;
        lastRequestedBefore = Long.MIN_VALUE;
        computeIndicators();
        clearCrosshair();
        invalidate();
    }

    public void setCandlesKeepingViewport(@Nullable List<CandleEntry> items) {
        int oldSize = candles.size();
        float oldOffset = offsetCandles;
        float oldVisibleEnd = oldSize > 0 ? (oldSize - 1f - oldOffset) : 0f;
        boolean hasLayout = ensureLayoutForMath();
        float focusX = resolveViewportFocusX(hasLayout);
        float focusIndexFloat = oldVisibleEnd;
        if (oldSize > 0 && hasLayout) {
            focusIndexFloat = xToRawIndex(focusX, oldVisibleEnd);
        }
        int anchorIndex = oldSize > 0 ? Math.max(0, Math.min(oldSize - 1, Math.round(focusIndexFloat))) : -1;
        long anchorOpenTime = anchorIndex >= 0 ? candles.get(anchorIndex).getOpenTime() : Long.MIN_VALUE;
        float anchorFraction = anchorIndex >= 0 ? (focusIndexFloat - anchorIndex) : 0f;

        candles.clear();
        if (items != null) {
            candles.addAll(items);
            Collections.sort(candles, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        }
        requestingMore = false;
        computeIndicators();
        offsetCandles = oldOffset;
        if (anchorOpenTime != Long.MIN_VALUE && !candles.isEmpty()) {
            int newAnchorIndex = indexByOpenTime(anchorOpenTime);
            if (newAnchorIndex >= 0) {
                float targetVisibleEnd = newAnchorIndex + anchorFraction;
                if (hasLayout) {
                    float safeSlot = Math.max(1e-3f, slot());
                    float rightToFocus = (plotRight() - safeSlot * 0.5f - focusX) / safeSlot;
                    targetVisibleEnd += rightToFocus;
                }
                offsetCandles = (candles.size() - 1f) - targetVisibleEnd;
            } else if (candles.size() >= oldSize) {
                offsetCandles = oldOffset + (candles.size() - oldSize);
            }
        }
        clampOffset();
        if (longPressing && !Float.isNaN(crosshairX) && !Float.isNaN(crosshairY)) {
            updateCrosshair(crosshairX, crosshairY);
        } else {
            notifyCrosshair();
        }
        invalidate();
    }

    public void prependCandles(@Nullable List<CandleEntry> olderCandles) {
        requestingMore = false;
        if (olderCandles == null || olderCandles.isEmpty()) {
            return;
        }
        if (candles.isEmpty()) {
            setCandles(olderCandles);
            return;
        }
        List<CandleEntry> sorted = new ArrayList<>(olderCandles);
        Collections.sort(sorted, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        Set<Long> exists = new HashSet<>();
        for (CandleEntry item : candles) {
            exists.add(item.getOpenTime());
        }
        List<CandleEntry> toAdd = new ArrayList<>();
        long oldest = candles.get(0).getOpenTime();
        for (CandleEntry item : sorted) {
            if (item.getOpenTime() < oldest && !exists.contains(item.getOpenTime())) {
                toAdd.add(item);
                exists.add(item.getOpenTime());
            }
        }
        if (toAdd.isEmpty()) {
            return;
        }
        candles.addAll(0, toAdd);
        offsetCandles += toAdd.size();
        computeIndicators();
        clampOffset();
        if (longPressing && !Float.isNaN(crosshairX) && !Float.isNaN(crosshairY)) {
            updateCrosshair(crosshairX, crosshairY);
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            lastX = event.getX();
            horizontalDragging = false;
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            requestDisallow(true);
            horizontalDragging = false;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (longPressing) {
                updateCrosshair(event.getX(), event.getY());
                return true;
            }
            if (event.getPointerCount() > 1 || scaleGestureDetector.isInProgress()) {
                requestDisallow(true);
                return true;
            }
            if (!scaling && event.getPointerCount() == 1) {
                if (!horizontalDragging) {
                    float dxAbs = Math.abs(event.getX() - downX);
                    float dyAbs = Math.abs(event.getY() - downY);
                    if (dyAbs > touchSlop && dyAbs > dxAbs) {
                        requestDisallow(false);
                        return false;
                    }
                    if (dxAbs > touchSlop && dxAbs > dyAbs) {
                        horizontalDragging = true;
                        requestDisallow(true);
                    }
                }
                if (!horizontalDragging) {
                    return true;
                }
                float dx = event.getX() - lastX;
                lastX = event.getX();
                offsetCandles += dx / Math.max(1f, slot());
                clampOffset();
                maybeRequestMore();
                requestFrame();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (!scaling) {
                requestDisallow(false);
            }
            horizontalDragging = false;
            if (action == MotionEvent.ACTION_CANCEL && longPressing) {
                clearCrosshair();
            }
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
        canvas.drawRect(0f, 0f, width, height, bgPaint);
        layoutAreas(width, height);
        dispatchPricePaneLayout(false);
        if (candles.isEmpty()) {
            updateLatestCandleOutOfBounds(false, false);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无K线数据", width / 2f, height / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        visibleEndFloat = candles.size() - 1f - offsetCandles;
        float latestX = xFor(candles.size() - 1, visibleEndFloat);
        updateLatestCandleOutOfBounds(latestX > priceRect.right + dp(0.2f), false);
        float visibleCount = Math.max(8f, effectivePlotWidth() / Math.max(1f, slot()));
        int start = Math.max(0, (int) Math.floor(visibleEndFloat - visibleCount - 2f));
        int end = Math.min(candles.size() - 1, (int) Math.ceil(visibleEndFloat + 2f));

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double candleHighMax = -Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            CandleEntry candle = candles.get(i);
            candleHighMax = Math.max(candleHighMax, candle.getHigh());
            min = Math.min(min, candle.getLow());
            max = Math.max(max, candle.getHigh());
            if (showBoll && i < bollDn.length && !Double.isNaN(bollDn[i])) {
                min = Math.min(min, bollDn[i]);
                max = Math.max(max, bollUp[i]);
            }
        }
        if (max <= min) {
            max = min + 1d;
        }
        double pad = (max - min) * 0.06d;
        visiblePriceMin = min - pad;
        visiblePriceMax = max + pad;
        if (candleHighMax > -Double.MAX_VALUE) {
            float topGapPx = dp(18f);
            float paneHeight = Math.max(dp(1f), priceRect.height());
            double ratio = Math.min(0.95d, topGapPx / paneHeight);
            double requiredMax = (candleHighMax - ratio * visiblePriceMin) / Math.max(1e-6d, 1d - ratio);
            if (requiredMax > visiblePriceMax) {
                visiblePriceMax = requiredMax;
            }
        }

        int drawStep = resolveDrawStep();
        drawGrid(canvas, priceRect);
        drawPriceAxisLabels(canvas, visiblePriceMin, visiblePriceMax);
        drawCandles(canvas, start, end, visiblePriceMin, visiblePriceMax, drawStep);
        drawVisibleExtremes(canvas, start, end, visiblePriceMin, visiblePriceMax);
        drawLatestPriceGuide(canvas);
        drawOverlayAnnotations(canvas, positionAnnotations);
        drawOverlayAnnotations(canvas, pendingAnnotations);
        drawAggregateCostAnnotation(canvas);
        int infoIndex = resolveInfoIndex(end);
        drawPriceOhlcInfo(canvas, infoIndex);
        if (showBoll) {
            drawSeries(canvas, bollMid, start, end, visiblePriceMin, visiblePriceMax, priceRect, bollMidPaint, drawStep);
            drawSeries(canvas, bollUp, start, end, visiblePriceMin, visiblePriceMax, priceRect, bollUpPaint, drawStep);
            drawSeries(canvas, bollDn, start, end, visiblePriceMin, visiblePriceMax, priceRect, bollDnPaint, drawStep);
        }
        if (showVolume && volRect.height() > 0f) {
            drawVolume(canvas, start, end, infoIndex, drawStep);
        }
        if (showMacd && macdRect.height() > 0f) {
            drawMacd(canvas, start, end, infoIndex, drawStep);
        }
        if (showStochRsi && stochRect.height() > 0f) {
            drawStochRsi(canvas, start, end, infoIndex, drawStep);
        }
        if (longPressing && highlightedIndex >= 0 && highlightedIndex < candles.size()) {
            drawCrosshair(canvas, highlightedIndex);
            drawCandlePopup(canvas, highlightedIndex);
            drawCrosshairLabels(canvas, highlightedIndex);
        }
        drawPaneBorder(canvas, priceRect);
        drawPaneBorder(canvas, volRect);
        drawPaneBorder(canvas, macdRect);
        drawPaneBorder(canvas, stochRect);
        drawBottomTimeLabels(canvas, start, end);
    }

    private void drawCandles(Canvas canvas, int start, int end, double min, double max, int step) {
        int saveCount = canvas.save();
        canvas.clipRect(priceRect);
        float halfBody = Math.max(dp(0.2f), candleWidth / 2f);
        boolean lineOnly = halfBody <= dp(0.25f);
        int stride = Math.max(1, step);
        for (int i = start; i <= end; i += stride) {
            CandleEntry candle = candles.get(i);
            float x = xFor(i, visibleEndFloat);
            if (x < priceRect.left - slot() || x > priceRect.right + slot()) {
                continue;
            }
            float yOpen = yFor(candle.getOpen(), min, max, priceRect);
            float yClose = yFor(candle.getClose(), min, max, priceRect);
            float yHigh = yFor(candle.getHigh(), min, max, priceRect);
            float yLow = yFor(candle.getLow(), min, max, priceRect);
            Paint paint = candle.getClose() >= candle.getOpen() ? upPaint : downPaint;
            canvas.drawLine(x, yHigh, x, yLow, paint);
            if (!lineOnly) {
                canvas.drawRect(x - halfBody, Math.min(yOpen, yClose), x + halfBody, Math.max(yOpen, yClose) + dp(0.4f), paint);
            }
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawVolume(Canvas canvas, int start, int end, int infoIndex, int step) {
        double maxVolume = 1d;
        for (int i = start; i <= end; i++) {
            maxVolume = Math.max(maxVolume, candles.get(i).getVolume());
        }
        float halfBody = Math.max(dp(0.18f), candleWidth / 2f);
        int stride = Math.max(1, step);
        int saveCount = canvas.save();
        canvas.clipRect(volRect);
        for (int i = start; i <= end; i += stride) {
            CandleEntry candle = candles.get(i);
            float x = xFor(i, visibleEndFloat);
            float top = (float) (volRect.bottom - (candle.getVolume() / maxVolume) * volRect.height());
            Paint paint = candle.getClose() >= candle.getOpen() ? upPaint : downPaint;
            canvas.drawRect(x - halfBody, top, x + halfBody, volRect.bottom, paint);
        }
        canvas.restoreToCount(saveCount);
        canvas.drawLine(volRect.left, volRect.top, volRect.right, volRect.top, axisPaint);
        textPaint.setColor(0xFF8FA6C7);
        canvas.drawText("VOL: " + formatVolumeNumber(candles.get(infoIndex).getVolume(), false), volRect.left + dp(2f), volRect.top + dp(10f), textPaint);
        canvas.drawLine(volRect.right, volRect.top, volRect.right, volRect.bottom, axisPaint);
        canvas.drawText(formatVolumeNumber(maxVolume, true), volRect.right + dp(4f), volRect.top + dp(3f), textPaint);
        canvas.drawText("0", volRect.right + dp(4f), volRect.bottom + dp(3f), textPaint);
        drawVolumeThreshold(canvas, maxVolume);
    }

    private void drawMacd(Canvas canvas, int start, int end, int infoIndex, int step) {
        double maxAbs = resolveMacdAbsMax(start, end);
        float zeroY = yFor(0d, -maxAbs, maxAbs, macdRect);
        canvas.drawLine(macdRect.left, zeroY, macdRect.right, zeroY, axisPaint);
        float halfBody = Math.max(dp(0.18f), candleWidth / 2f);
        int stride = Math.max(1, step);
        int saveCount = canvas.save();
        canvas.clipRect(macdRect);
        for (int i = start; i <= end; i += stride) {
            if (i >= macdHist.length || Double.isNaN(macdHist[i])) {
                continue;
            }
            float x = xFor(i, visibleEndFloat);
            float y = yFor(macdHist[i], -maxAbs, maxAbs, macdRect);
            Paint paint = macdHist[i] >= 0d ? upPaint : downPaint;
            canvas.drawRect(x - halfBody, Math.min(zeroY, y), x + halfBody, Math.max(zeroY, y), paint);
        }
        drawSeries(canvas, macdDif, start, end, -maxAbs, maxAbs, macdRect, macdDifPaint, stride);
        drawSeries(canvas, macdDea, start, end, -maxAbs, maxAbs, macdRect, macdDeaPaint, stride);
        canvas.restoreToCount(saveCount);
        canvas.drawLine(macdRect.left, macdRect.top, macdRect.right, macdRect.top, gridPaint);
        canvas.drawLine(macdRect.left, macdRect.bottom, macdRect.right, macdRect.bottom, axisPaint);
        canvas.drawLine(macdRect.right, macdRect.top, macdRect.right, macdRect.bottom, axisPaint);
        textPaint.setColor(0xFF8FA6C7);
        canvas.drawText(formatAxisInt(maxAbs), macdRect.right + dp(4f), macdRect.top + dp(3f), textPaint);
        canvas.drawText("0", macdRect.right + dp(4f), zeroY + dp(3f), textPaint);
        canvas.drawText(formatAxisInt(-maxAbs), macdRect.right + dp(4f), macdRect.bottom + dp(3f), textPaint);
        drawMacdInfo(canvas, infoIndex);
    }

    private void drawStochRsi(Canvas canvas, int start, int end, int infoIndex, int step) {
        int stride = Math.max(1, step);
        drawSeries(canvas, stochK, start, end, 0d, 100d, stochRect, stochKPaint, stride);
        drawSeries(canvas, stochD, start, end, 0d, 100d, stochRect, stochDPaint, stride);
        canvas.drawLine(stochRect.left, yFor(20d, 0d, 100d, stochRect), stochRect.right, yFor(20d, 0d, 100d, stochRect), gridPaint);
        canvas.drawLine(stochRect.left, yFor(80d, 0d, 100d, stochRect), stochRect.right, yFor(80d, 0d, 100d, stochRect), gridPaint);
        canvas.drawLine(stochRect.left, stochRect.top, stochRect.right, stochRect.top, gridPaint);
        canvas.drawLine(stochRect.left, stochRect.bottom, stochRect.right, stochRect.bottom, axisPaint);
        canvas.drawLine(stochRect.right, stochRect.top, stochRect.right, stochRect.bottom, axisPaint);
        textPaint.setColor(0xFF8FA6C7);
        canvas.drawText("STOCHRSI K:" + formatDecimal(stochK[infoIndex]) + " D:" + formatDecimal(stochD[infoIndex]),
                stochRect.left + dp(2f),
                stochRect.top + dp(10f),
                textPaint);
        canvas.drawText("100", stochRect.right + dp(4f), stochRect.top + dp(3f), textPaint);
        canvas.drawText("50", stochRect.right + dp(4f), yFor(50d, 0d, 100d, stochRect) + dp(3f), textPaint);
        canvas.drawText("0", stochRect.right + dp(4f), stochRect.bottom + dp(3f), textPaint);
    }

    private void drawGrid(Canvas canvas, RectF rect) {
        for (int i = 0; i <= 4; i++) {
            float y = rect.top + rect.height() * i / 4f;
            canvas.drawLine(rect.left, y, rect.right, y, gridPaint);
        }
        for (int i = 0; i <= 4; i++) {
            float x = rect.left + rect.width() * i / 4f;
            canvas.drawLine(x, rect.top, x, rect.bottom, gridPaint);
        }
        canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, axisPaint);
    }

    private void drawPriceAxisLabels(Canvas canvas, double min, double max) {
        int tick = 4;
        for (int i = 0; i <= tick; i++) {
            double value = max - (max - min) * i / tick;
            float y = yFor(value, min, max, priceRect);
            String label = String.format(Locale.getDefault(), "%,.0f", value);
            canvas.drawText(label, priceRect.right + dp(4f), y + dp(3f), textPaint);
        }
        canvas.drawLine(priceRect.right, priceRect.top, priceRect.right, priceRect.bottom, axisPaint);
    }

    private void drawPriceOhlcInfo(Canvas canvas, int index) {
        if (index < 0 || index >= candles.size()) {
            return;
        }
        CandleEntry candle = candles.get(index);
        textPaint.setColor(0xFF8FA6C7);
        String info = "O:" + FormatUtils.formatPrice(candle.getOpen())
                + "  H:" + FormatUtils.formatPrice(candle.getHigh())
                + "  L:" + FormatUtils.formatPrice(candle.getLow())
                + "  C:" + FormatUtils.formatPrice(candle.getClose());
        canvas.drawText(info, priceRect.left + dp(2f), priceRect.top + dp(10f), textPaint);
    }

    private void drawBollInfo(Canvas canvas, int index) {
        if (index < 0 || index >= candles.size()) {
            return;
        }
        float y = priceRect.top + dp(10f);
        textPaint.setColor(0xFF8FA6C7);
        canvas.drawText("BOLL(20,2)", priceRect.left + dp(2f), y, textPaint);
        if (index < bollUp.length && !Double.isNaN(bollUp[index])) {
            float x = priceRect.left + dp(58f);
            textPaint.setColor(bollUpPaint.getColor());
            canvas.drawText("UP:" + FormatUtils.formatPrice(bollUp[index]), x, y, textPaint);
            x += dp(82f);
            textPaint.setColor(line1Paint.getColor());
            canvas.drawText("MB:" + FormatUtils.formatPrice(bollMid[index]), x, y, textPaint);
            x += dp(82f);
            textPaint.setColor(bollDnPaint.getColor());
            canvas.drawText("DN:" + FormatUtils.formatPrice(bollDn[index]), x, y, textPaint);
        }
        textPaint.setColor(0xFF8FA6C7);
    }

    private void drawMacdInfo(Canvas canvas, int index) {
        if (index < 0 || index >= macdHist.length) {
            return;
        }
        float y = macdRect.top + dp(10f);
        textPaint.setColor(0xFF8FA6C7);
        String text = "DIF:" + formatDecimal(macdDif[index])
                + "  DEA:" + formatDecimal(macdDea[index])
                + "  MACD:" + formatDecimal(macdHist[index]);
        canvas.drawText(text, macdRect.left + dp(2f), y, textPaint);
        textPaint.setColor(0xFF8FA6C7);
    }

    private void drawSeries(Canvas canvas, double[] values, int start, int end, double min, double max, RectF rect, Paint paint, int step) {
        int saveCount = canvas.save();
        canvas.clipRect(rect);
        float prevX = 0f;
        float prevY = 0f;
        boolean hasPrev = false;
        int stride = Math.max(1, step);
        for (int i = start; i <= end; i += stride) {
            if (i < 0 || i >= values.length || Double.isNaN(values[i])) {
                hasPrev = false;
                continue;
            }
            float x = xFor(i, visibleEndFloat);
            float y = yFor(values[i], min, max, rect);
            if (hasPrev) {
                canvas.drawLine(prevX, prevY, x, y, paint);
            }
            prevX = x;
            prevY = y;
            hasPrev = true;
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawVisibleExtremes(Canvas canvas, int start, int end, double min, double max) {
        if (start < 0 || end < start || end >= candles.size()) {
            return;
        }
        int highIndex = start;
        int lowIndex = start;
        double highValue = candles.get(start).getHigh();
        double lowValue = candles.get(start).getLow();
        for (int i = start + 1; i <= end; i++) {
            CandleEntry item = candles.get(i);
            if (item.getHigh() >= highValue) {
                highValue = item.getHigh();
                highIndex = i;
            }
            if (item.getLow() <= lowValue) {
                lowValue = item.getLow();
                lowIndex = i;
            }
        }
        float highX = clamp(xFor(highIndex, visibleEndFloat), priceRect.left, priceRect.right);
        float highY = yFor(highValue, min, max, priceRect);
        float lowX = clamp(xFor(lowIndex, visibleEndFloat), priceRect.left, priceRect.right);
        float lowY = yFor(lowValue, min, max, priceRect);

        canvas.drawCircle(highX, highY, dp(2.6f), extremeHighPaint);
        canvas.drawCircle(lowX, lowY, dp(2.6f), extremeLowPaint);
        if (!scaling) {
            drawExtremeLabel(canvas, highX, highY, FormatUtils.formatPrice(highValue), true, extremeHighPaint.getColor());
            drawExtremeLabel(canvas, lowX, lowY, FormatUtils.formatPrice(lowValue), false, extremeLowPaint.getColor());
        }
    }

    private void drawExtremeLabel(Canvas canvas, float anchorX, float anchorY, String text, boolean highPoint, int markerColor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float padX = dp(4f);
        float padY = dp(2.5f);
        float boxH = dp(12f);
        float boxW = extremeLabelTextPaint.measureText(text) + padX * 2f;
        float preferredGap = dp(8f);
        boolean placeRight = anchorX < priceRect.centerX();
        float left = placeRight ? (anchorX + preferredGap) : (anchorX - preferredGap - boxW);
        left = clamp(left, priceRect.left, priceRect.right - boxW);
        float top = highPoint ? (anchorY - boxH - dp(4f)) : (anchorY + dp(4f));
        top = clamp(top, priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);

        float connectorX = placeRight ? box.left : box.right;
        extremeConnectorPaint.setColor(markerColor);
        canvas.drawLine(anchorX, anchorY, connectorX, anchorY, extremeConnectorPaint);
        canvas.drawRoundRect(box, dp(2.5f), dp(2.5f), extremeLabelBgPaint);
        canvas.drawText(text, box.left + padX, box.bottom - padY, extremeLabelTextPaint);
    }

    private void drawLatestPriceGuide(Canvas canvas) {
        if (candles.isEmpty()) {
            return;
        }
        int latestIndex = candles.size() - 1;
        CandleEntry latest = candles.get(latestIndex);
        float y = yFor(latest.getClose(), visiblePriceMin, visiblePriceMax, priceRect);
        if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
            return;
        }
        float startX = xFor(latestIndex, visibleEndFloat);
        startX = clamp(startX, priceRect.left, priceRect.right);

        int trendColor = latest.getClose() >= latest.getOpen() ? upPaint.getColor() : downPaint.getColor();
        latestPriceGuidePaint.setColor(0xCCFFFFFF);
        latestPriceTagPaint.setColor(trendColor);
        canvas.drawLine(startX, y, priceRect.right, y, latestPriceGuidePaint);

        String priceText = FormatUtils.formatPrice(latest.getClose());
        float padX = dp(5f);
        float padY = dp(3f);
        float boxH = dp(14f);
        float boxW = latestPriceTagTextPaint.measureText(priceText) + padX * 2f;
        float left = priceRect.right + dp(3f);
        float top = clamp(y - boxH / 2f, priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        canvas.drawRoundRect(box, dp(3f), dp(3f), latestPriceTagPaint);
        canvas.drawText(priceText, box.left + padX, box.bottom - padY, latestPriceTagTextPaint);
    }

    private void drawOverlayAnnotations(Canvas canvas, List<PriceAnnotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return;
        }
        int saveCount = canvas.save();
        canvas.clipRect(priceRect);
        for (PriceAnnotation annotation : annotations) {
            if (annotation == null || annotation.price <= 0d) {
                continue;
            }
            float y = yFor(annotation.price, visiblePriceMin, visiblePriceMax, priceRect);
            if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
                continue;
            }
            int lineColor = annotation.color == 0 ? 0xFFE2EDFF : annotation.color;
            overlayDashPaint.setColor(lineColor);
            canvas.drawLine(priceRect.left, y, priceRect.right, y, overlayDashPaint);

            float x = resolveAnnotationX(annotation.anchorTimeMs);
            overlayPointPaint.setColor(lineColor);
            canvas.drawCircle(x, y, dp(1.7f), overlayPointPaint);

            if (!annotation.label.isEmpty()) {
                drawOverlayLabel(canvas, annotation.label, y, lineColor);
            }
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawOverlayLabel(Canvas canvas, String text, float anchorY, int textColor) {
        float padX = dp(4f);
        float padY = dp(2f);
        float boxH = dp(12f);
        float boxW = overlayLabelTextPaint.measureText(text) + padX * 2f;
        float left = priceRect.left + dp(2f);
        float top = clamp(anchorY - boxH - dp(2f), priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        canvas.drawRoundRect(box, dp(2.5f), dp(2.5f), overlayLabelBgPaint);
        overlayLabelTextPaint.setColor(textColor);
        canvas.drawText(text, box.left + padX, box.bottom - padY, overlayLabelTextPaint);
    }

    private void drawAggregateCostAnnotation(Canvas canvas) {
        if (aggregateCostAnnotation == null || aggregateCostAnnotation.price <= 0d) {
            return;
        }
        float y = yFor(aggregateCostAnnotation.price, visiblePriceMin, visiblePriceMax, priceRect);
        if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
            return;
        }
        canvas.drawLine(priceRect.left, y, priceRect.right, y, aggregateCostLinePaint);
        String text = aggregateCostAnnotation.priceLabel.isEmpty()
                ? FormatUtils.formatPrice(aggregateCostAnnotation.price)
                : aggregateCostAnnotation.priceLabel;
        float padX = dp(5f);
        float padY = dp(3f);
        float boxH = dp(14f);
        float boxW = aggregateCostTagTextPaint.measureText(text) + padX * 2f;
        float left = priceRect.right + dp(3f);
        float top = clamp(y - boxH / 2f, priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        canvas.drawRoundRect(box, dp(3f), dp(3f), aggregateCostTagPaint);
        canvas.drawText(text, box.left + padX, box.bottom - padY, aggregateCostTagTextPaint);
    }

    private void drawVolumeThreshold(Canvas canvas, double maxVolume) {
        if (!volumeThresholdVisible || Double.isNaN(volumeThresholdValue) || volumeThresholdValue <= 0d || volRect.isEmpty()) {
            return;
        }
        double denominator = Math.max(maxVolume, 1d);
        float y = (float) (volRect.bottom - (volumeThresholdValue / denominator) * volRect.height());
        y = clamp(y, volRect.top, volRect.bottom);
        canvas.drawLine(volRect.left, y, volRect.right, y, volumeThresholdPaint);
    }

    private void drawPaneBorder(Canvas canvas, RectF rect) {
        if (rect == null || rect.isEmpty()) {
            return;
        }
        canvas.drawRect(rect, paneBorderPaint);
    }

    private int resolveDrawStep() {
        float s = slot();
        float targetPx = dp(2.2f);
        if (s >= targetPx) {
            return 1;
        }
        return Math.max(1, (int) Math.floor(targetPx / Math.max(dp(0.05f), s)));
    }

    private void drawCrosshair(Canvas canvas, int index) {
        float bottom = resolveChartBottom();
        canvas.drawLine(crosshairX, priceRect.top, crosshairX, bottom, crossPaint);
        canvas.drawLine(priceRect.left, crosshairY, priceRect.right, crosshairY, crossPaint);
        CandleEntry candle = candles.get(index);
        float closeY = yFor(candle.getClose(), visiblePriceMin, visiblePriceMax, priceRect);
        Paint marker = candle.getClose() >= candle.getOpen() ? upPaint : downPaint;
        canvas.drawCircle(xFor(index, visibleEndFloat), closeY, dp(2.5f), marker);
    }

    private void drawCrosshairLabels(Canvas canvas, int index) {
        String priceText = FormatUtils.formatPrice(crosshairPrice);
        float pPadX = dp(5f);
        float pPadY = dp(3f);
        float pW = crossLabelTextPaint.measureText(priceText) + pPadX * 2f;
        float pH = dp(14f);
        float pLeft = priceRect.right + dp(3f);
        float pTop = clamp(crosshairY - pH / 2f, priceRect.top, priceRect.bottom - pH);
        RectF pBox = new RectF(pLeft, pTop, pLeft + pW, pTop + pH);
        canvas.drawRoundRect(pBox, dp(3f), dp(3f), crossLabelBgPaint);
        canvas.drawText(priceText, pBox.left + pPadX, pBox.bottom - pPadY, crossLabelTextPaint);

        String timeText = timeFmt.format(new Date(candles.get(index).getOpenTime()));
        float tPadX = dp(6f);
        float tPadY = dp(3f);
        float tW = crossLabelTextPaint.measureText(timeText) + tPadX * 2f;
        float tH = dp(14f);
        float tLeft = clamp(crosshairX - tW / 2f, priceRect.left, priceRect.right - tW);
        float tTop = resolveChartBottom() + dp(2f);
        RectF tBox = new RectF(tLeft, tTop, tLeft + tW, tTop + tH);
        canvas.drawRoundRect(tBox, dp(3f), dp(3f), crossLabelBgPaint);
        canvas.drawText(timeText, tBox.left + tPadX, tBox.bottom - tPadY, crossLabelTextPaint);
    }

    private void drawCandlePopup(Canvas canvas, int index) {
        CandleEntry candle = candles.get(index);
        String[] lines = new String[]{
                "时间 " + axisTimeFmt.format(new Date(candle.getOpenTime())),
                "O " + FormatUtils.formatPrice(candle.getOpen()),
                "H " + FormatUtils.formatPrice(candle.getHigh()),
                "L " + FormatUtils.formatPrice(candle.getLow()),
                "C " + FormatUtils.formatPrice(candle.getClose()),
                "VOL " + formatVolumeNumber(candle.getVolume(), false),
                "TOV " + formatVolumeNumber(candle.getQuoteVolume(), false)
        };

        float pad = dp(6f);
        float lineH = dp(11f);
        float maxLineWidth = 0f;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, popupTextPaint.measureText(line));
        }
        float w = maxLineWidth + pad * 2f;
        float h = lineH * lines.length + pad * 2f;
        float left = crosshairX + dp(8f);
        if (left + w > priceRect.right) left = crosshairX - w - dp(8f);
        left = clamp(left, priceRect.left, priceRect.right - w);
        float top = clamp(priceRect.top + dp(14f), priceRect.top, priceRect.bottom - h);
        RectF box = new RectF(left, top, left + w, top + h);
        canvas.drawRoundRect(box, dp(6f), dp(6f), popupBgPaint);
        for (int i = 0; i < lines.length; i++) {
            float baseline = box.top + pad + lineH * (i + 0.8f);
            canvas.drawText(lines[i], box.left + pad, baseline, popupTextPaint);
        }
    }

    private void drawBottomTimeLabels(Canvas canvas, int start, int end) {
        String leftTime = axisTimeFmt.format(new Date(candles.get(start).getOpenTime()));
        String rightTime = axisTimeFmt.format(new Date(candles.get(end).getOpenTime()));
        float baseline = getHeight() - dp(4f);
        canvas.drawText(leftTime, priceRect.left, baseline, textPaint);
        float rightW = textPaint.measureText(rightTime);
        canvas.drawText(rightTime, priceRect.right - rightW, baseline, textPaint);
    }

    private void layoutAreas(float width, float height) {
        float left = dp(8f);
        float right = width - dp(38f);
        float top = dp(8f);
        float bottom = height - dp(18f);
        float pw = 9.36f;
        float vw = showVolume ? 2f : 0f;
        float mw = showMacd ? 2f : 0f;
        float sw = showStochRsi ? 2f : 0f;
        float gap = dp(10f);
        int sections = 1 + (showVolume ? 1 : 0) + (showMacd ? 1 : 0) + (showStochRsi ? 1 : 0);
        float total = pw + vw + mw + sw;
        float unit = (bottom - top - Math.max(0, sections - 1) * gap) / Math.max(1f, total);

        float cursor = top;
        priceRect.set(left, cursor, right, cursor + pw * unit);
        cursor = priceRect.bottom;
        if (showVolume) {
            cursor += gap;
            volRect.set(left, cursor, right, cursor + vw * unit);
            cursor = volRect.bottom;
        } else {
            volRect.setEmpty();
        }
        if (showMacd) {
            cursor += gap;
            macdRect.set(left, cursor, right, cursor + mw * unit);
            cursor = macdRect.bottom;
        } else {
            macdRect.setEmpty();
        }
        if (showStochRsi) {
            cursor += gap;
            stochRect.set(left, cursor, right, cursor + sw * unit);
        } else {
            stochRect.setEmpty();
        }
    }

    private void computeIndicators() {
        int size = candles.size();
        bollMid = nanArray(size);
        bollUp = nanArray(size);
        bollDn = nanArray(size);
        macdDif = nanArray(size);
        macdDea = nanArray(size);
        macdHist = nanArray(size);
        stochK = nanArray(size);
        stochD = nanArray(size);
        if (size == 0) return;

        for (int i = 19; i < size; i++) {
            double sum = 0d;
            for (int j = i - 19; j <= i; j++) sum += candles.get(j).getClose();
            double mean = sum / 20d;
            double var = 0d;
            for (int j = i - 19; j <= i; j++) {
                double diff = candles.get(j).getClose() - mean;
                var += diff * diff;
            }
            double std = Math.sqrt(var / 20d);
            bollMid[i] = mean;
            bollUp[i] = mean + 2d * std;
            bollDn[i] = mean - 2d * std;
        }

        double ema12 = candles.get(0).getClose();
        double ema26 = ema12;
        double dea = 0d;
        for (int i = 0; i < size; i++) {
            double close = candles.get(i).getClose();
            ema12 = ema12 + (2d / 13d) * (close - ema12);
            ema26 = ema26 + (2d / 27d) * (close - ema26);
            double dif = ema12 - ema26;
            dea = dea + (2d / 10d) * (dif - dea);
            macdDif[i] = dif;
            macdDea[i] = dea;
            macdHist[i] = (dif - dea) * 2d;
        }

        double[] rsi = nanArray(size);
        if (size > 15) {
            double gain = 0d;
            double loss = 0d;
            for (int i = 1; i <= 14; i++) {
                double diff = candles.get(i).getClose() - candles.get(i - 1).getClose();
                gain += Math.max(0d, diff);
                loss += Math.max(0d, -diff);
            }
            gain /= 14d;
            loss /= 14d;
            rsi[14] = loss < 1e-9 ? 100d : 100d - 100d / (1d + gain / loss);
            for (int i = 15; i < size; i++) {
                double diff = candles.get(i).getClose() - candles.get(i - 1).getClose();
                gain = (gain * 13d + Math.max(0d, diff)) / 14d;
                loss = (loss * 13d + Math.max(0d, -diff)) / 14d;
                rsi[i] = loss < 1e-9 ? 100d : 100d - 100d / (1d + gain / loss);
            }
        }
        double[] raw = nanArray(size);
        for (int i = 27; i < size; i++) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (int j = i - 13; j <= i; j++) {
                if (Double.isNaN(rsi[j])) {
                    min = Double.NaN;
                    break;
                }
                min = Math.min(min, rsi[j]);
                max = Math.max(max, rsi[j]);
            }
            if (Double.isNaN(min)) continue;
            raw[i] = Math.abs(max - min) < 1e-9 ? 50d : (rsi[i] - min) * 100d / (max - min);
            stochK[i] = avg(raw, i, 3);
            stochD[i] = avg(stochK, i, 3);
        }
    }

    private void updateCrosshair(float x, float y) {
        if (candles.isEmpty()) return;
        crosshairX = clamp(x, priceRect.left, priceRect.right);
        crosshairY = clamp(y, priceRect.top, priceRect.bottom);
        highlightedIndex = xToIndex(crosshairX, visibleEndFloat);
        if (highlightedIndex < 0) highlightedIndex = 0;
        if (highlightedIndex >= candles.size()) highlightedIndex = candles.size() - 1;
        crosshairPrice = valueForY(crosshairY, visiblePriceMin, visiblePriceMax, priceRect);
        notifyCrosshair();
        invalidate();
    }

    private void clearCrosshair() {
        longPressing = false;
        crosshairX = Float.NaN;
        crosshairY = Float.NaN;
        crosshairPrice = Double.NaN;
        highlightedIndex = -1;
        notifyCrosshair();
        invalidate();
    }

    private void notifyCrosshair() {
        if (onCrosshairListener == null) return;
        if (!longPressing || highlightedIndex < 0 || highlightedIndex >= candles.size()) {
            onCrosshairListener.onValue(null);
            return;
        }
        int i = highlightedIndex;
        onCrosshairListener.onValue(new CrosshairValue(
                candles.get(i),
                crosshairPrice,
                valueAt(macdDif, i),
                valueAt(macdDea, i),
                valueAt(macdHist, i),
                valueAt(stochK, i),
                valueAt(stochD, i)
        ));
    }

    private void maybeRequestMore() {
        if (onRequestMoreListener == null || requestingMore || candles.isEmpty()) return;
        float maxOffset = maxOffset();
        if (maxOffset <= 0f) return;
        if (offsetCandles < maxOffset - 0.5f) return;
        long oldest = candles.get(0).getOpenTime();
        long now = System.currentTimeMillis();
        if (oldest == lastRequestedBefore && now - lastRequestAtMs < 2500L) return;
        requestingMore = true;
        lastRequestedBefore = oldest;
        lastRequestAtMs = now;
        onRequestMoreListener.onRequestMore(oldest);
    }

    private int resolveInfoIndex(int fallbackEnd) {
        if (longPressing && highlightedIndex >= 0 && highlightedIndex < candles.size()) {
            return highlightedIndex;
        }
        return Math.max(0, Math.min(candles.size() - 1, fallbackEnd));
    }

    private float resolveChartBottom() {
        if (!stochRect.isEmpty()) return stochRect.bottom;
        if (!macdRect.isEmpty()) return macdRect.bottom;
        if (!volRect.isEmpty()) return volRect.bottom;
        return priceRect.bottom;
    }

    private boolean ensureLayoutForMath() {
        if (!priceRect.isEmpty()) {
            return true;
        }
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return false;
        }
        layoutAreas(width, height);
        return !priceRect.isEmpty();
    }

    private float resolveViewportFocusX(boolean hasLayout) {
        if (!hasLayout) {
            return 0f;
        }
        if (longPressing && !Float.isNaN(crosshairX)) {
            return clamp(crosshairX, priceRect.left, priceRect.right);
        }
        return priceRect.centerX();
    }

    private void dispatchViewportState(boolean force) {
        boolean outOfBounds = computeLatestCandleOutOfBounds();
        updateLatestCandleOutOfBounds(outOfBounds, force);
    }

    private void dispatchPricePaneLayout(boolean force) {
        if (onPricePaneLayoutListener == null || priceRect.isEmpty()) {
            return;
        }
        int left = Math.round(priceRect.left);
        int top = Math.round(priceRect.top);
        int right = Math.round(priceRect.right);
        int bottom = Math.round(priceRect.bottom);
        if (!force
                && left == lastPricePaneLeft
                && top == lastPricePaneTop
                && right == lastPricePaneRight
                && bottom == lastPricePaneBottom) {
            return;
        }
        lastPricePaneLeft = left;
        lastPricePaneTop = top;
        lastPricePaneRight = right;
        lastPricePaneBottom = bottom;
        onPricePaneLayoutListener.onPricePaneLayoutChanged(left, top, right, bottom);
    }

    private void updateLatestCandleOutOfBounds(boolean outOfBounds, boolean force) {
        if (!force && latestCandleOutOfBounds == outOfBounds) {
            return;
        }
        latestCandleOutOfBounds = outOfBounds;
        if (onViewportStateListener != null) {
            onViewportStateListener.onLatestCandleOutOfBoundsChanged(outOfBounds);
        }
    }

    private boolean computeLatestCandleOutOfBounds() {
        if (candles.isEmpty()) {
            return false;
        }
        if (!ensureLayoutForMath()) {
            return offsetCandles > rightBlankSlots + 0.5f;
        }
        float endFloat = candles.size() - 1f - offsetCandles;
        float latestX = xFor(candles.size() - 1, endFloat);
        return latestX > priceRect.right + dp(0.2f);
    }

    private float plotRight() {
        return priceRect.right - rightBlankSlots * slot();
    }

    private float effectivePlotWidth() {
        return Math.max(dp(20f), plotRight() - priceRect.left);
    }

    private float maxOffset() {
        float visibleCount = effectivePlotWidth() / Math.max(1f, slot());
        return Math.max(0f, candles.size() - visibleCount);
    }

    private void clampOffset() {
        offsetCandles = clamp(offsetCandles, 0f, maxOffset());
    }

    private float xFor(int index, float endFloat) {
        return xFor((float) index, endFloat);
    }

    private float xFor(float indexFloat, float endFloat) {
        return plotRight() - (endFloat - indexFloat) * slot() - slot() * 0.5f;
    }

    private int xToIndex(float x, float endFloat) {
        return Math.round(xToRawIndex(x, endFloat));
    }

    private float xToRawIndex(float x, float endFloat) {
        float safeSlot = Math.max(1e-3f, slot());
        return endFloat - (plotRight() - safeSlot * 0.5f - x) / safeSlot;
    }

    private float resolveAnnotationX(long anchorTimeMs) {
        if (anchorTimeMs <= 0L || candles.isEmpty()) {
            return priceRect.right - dp(6f);
        }
        float rawIndex = rawIndexByOpenTime(anchorTimeMs);
        return clamp(xFor(rawIndex, visibleEndFloat), priceRect.left, priceRect.right);
    }

    private float rawIndexByOpenTime(long openTime) {
        if (candles.isEmpty()) {
            return 0f;
        }
        int last = candles.size() - 1;
        long firstTime = candles.get(0).getOpenTime();
        long lastTime = candles.get(last).getOpenTime();
        if (openTime <= firstTime) {
            return 0f;
        }
        if (openTime >= lastTime) {
            return last;
        }
        int low = 0;
        int high = last;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long value = candles.get(mid).getOpenTime();
            if (value == openTime) {
                return mid;
            }
            if (value < openTime) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        int rightIndex = Math.max(0, Math.min(last, low));
        int leftIndex = Math.max(0, Math.min(last, rightIndex - 1));
        long leftTime = candles.get(leftIndex).getOpenTime();
        long rightTime = candles.get(rightIndex).getOpenTime();
        if (rightTime <= leftTime) {
            return leftIndex;
        }
        float ratio = (float) (openTime - leftTime) / (float) (rightTime - leftTime);
        return leftIndex + ratio;
    }

    private float yFor(double value, double min, double max, RectF rect) {
        if (max <= min) return rect.centerY();
        double ratio = (value - min) / (max - min);
        return (float) (rect.bottom - ratio * rect.height());
    }

    private double valueForY(float y, double min, double max, RectF rect) {
        if (max <= min) return min;
        double ratio = (rect.bottom - y) / Math.max(1e-9f, rect.height());
        return min + ratio * (max - min);
    }

    private double resolveMacdAbsMax(int start, int end) {
        double maxAbs = 1e-6d;
        for (int i = start; i <= end; i++) {
            maxAbs = Math.max(maxAbs, Math.abs(valueAt(macdHist, i)));
            maxAbs = Math.max(maxAbs, Math.abs(valueAt(macdDif, i)));
            maxAbs = Math.max(maxAbs, Math.abs(valueAt(macdDea, i)));
        }
        return maxAbs;
    }

    private double valueAt(double[] values, int index) {
        if (values == null || index < 0 || index >= values.length || Double.isNaN(values[index])) {
            return Double.NaN;
        }
        return values[index];
    }

    private int indexByOpenTime(long openTime) {
        if (candles.isEmpty()) {
            return -1;
        }
        int low = 0;
        int high = candles.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long value = candles.get(mid).getOpenTime();
            if (value == openTime) {
                return mid;
            }
            if (value < openTime) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    private double[] nanArray(int size) {
        double[] out = new double[size];
        for (int i = 0; i < size; i++) out[i] = Double.NaN;
        return out;
    }

    private double avg(double[] values, int endIndex, int period) {
        int start = endIndex - period + 1;
        if (start < 0) return Double.NaN;
        double sum = 0d;
        for (int i = start; i <= endIndex; i++) {
            if (i < 0 || i >= values.length || Double.isNaN(values[i])) return Double.NaN;
            sum += values[i];
        }
        return sum / period;
    }

    private String formatVolumeNumber(double value, boolean axis) {
        DecimalFormat format = new DecimalFormat(axis ? "#,##0" : "#,##0.##");
        return format.format(value);
    }

    private String formatAxisInt(double value) {
        return String.format(Locale.getDefault(), "%,.0f", value);
    }

    private String formatDecimal(double value) {
        if (Double.isNaN(value)) return "--";
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private float slot() {
        return candleWidth + candleGap;
    }

    private void requestDisallow(boolean disallow) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(disallow);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void requestFrame() {
        postInvalidateOnAnimation();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            scaling = true;
            requestDisallow(true);
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (candles.isEmpty()) return false;
            float beforeSlot = slot();
            int focusIndex = xToIndex(detector.getFocusX(), visibleEndFloat);
            float rawScale = detector.getScaleFactor();
            float smoothScale = 1f + (rawScale - 1f) * 0.6f;
            candleWidth = clamp(candleWidth * smoothScale, minWidth, maxWidth);
            float afterSlot = slot();
            if (focusIndex >= 0) {
                float fromRight = plotRight() - detector.getFocusX();
                float focusFromEnd = fromRight / Math.max(1f, afterSlot);
                offsetCandles = (candles.size() - 1f - focusIndex) - focusFromEnd;
            } else if (Math.abs(beforeSlot - afterSlot) > 1e-3f) {
                offsetCandles *= (beforeSlot / afterSlot);
            }
            clampOffset();
            requestFrame();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            scaling = false;
            if (!longPressing) requestDisallow(false);
        }
    }
}

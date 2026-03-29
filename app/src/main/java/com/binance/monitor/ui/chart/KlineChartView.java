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
        public final String groupId;

        public PriceAnnotation(long anchorTimeMs, double price, String label, int color) {
            this(anchorTimeMs, price, label, color, "");
        }

        public PriceAnnotation(long anchorTimeMs, double price, String label, int color, @Nullable String groupId) {
            this.anchorTimeMs = anchorTimeMs;
            this.price = price;
            this.label = label == null ? "" : label;
            this.color = color;
            this.groupId = groupId == null ? "" : groupId.trim();
        }
    }

    public static class AggregateCostAnnotation {
        public final double price;
        public final String priceLabel;
        public final String symbolLabel;

        public AggregateCostAnnotation(double price, String priceLabel) {
            this(price, priceLabel, "");
        }

        public AggregateCostAnnotation(double price, String priceLabel, @Nullable String symbolLabel) {
            this.price = price;
            this.priceLabel = priceLabel == null ? "" : priceLabel;
            this.symbolLabel = symbolLabel == null ? "" : symbolLabel.trim();
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
    private double[] maLine = new double[0];
    private double[] emaLine = new double[0];
    private double[] sraLine = new double[0];
    private double[] avlLine = new double[0];
    private double[] rsiLine = new double[0];
    private double[] kdjK = new double[0];
    private double[] kdjD = new double[0];
    private double[] kdjJ = new double[0];

    private boolean showVolume = true;
    private boolean showMacd = true;
    private boolean showStochRsi = true;
    private boolean showBoll = true;
    private boolean showMa = true;
    private boolean showEma = true;
    private boolean showSra;
    private boolean showAvl;
    private boolean showRsi;
    private boolean showKdj;

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
    private final Paint maPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rsiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint kdjJPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private final Paint aggregateCostHintTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint volumeThresholdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int maPeriod = 20;
    private int emaPeriod = 12;
    private int sraPeriod = 14;
    private int avlPeriod = 20;
    private int rsiPeriod = 14;
    private int kdjPeriod = 9;
    private int kdjSmoothK = 3;
    private int kdjSmoothD = 3;
    private int bollPeriod = 20;
    private int bollStdMultiplier = 2;
    private int macdFastPeriod = 12;
    private int macdSlowPeriod = 26;
    private int macdSignalPeriod = 9;
    private int stochRsiLookback = 14;
    private int stochRsiSmoothK = 3;
    private int stochRsiSmoothD = 3;

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
    private final List<PriceAnnotation> abnormalAnnotations = new ArrayList<>();
    @Nullable
    private AggregateCostAnnotation aggregateCostAnnotation;
    private String highlightedAnnotationGroupId = "";
    private boolean crosshairOnCandle = true;
    private float verticalScale = 1f;
    private static final float MIN_VERTICAL_SCALE = 0.55f;
    private static final float MAX_VERTICAL_SCALE = 3.6f;

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
                if (longPressing) {
                    clearCrosshair();
                    return true;
                }
                if (!selectAnnotationGroupByTouch(e.getX(), e.getY())) {
                    clearHighlightedAnnotationGroup();
                }
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
        maPaint.setColor(0xFF60A5FA);
        emaPaint.setColor(0xFF34D399);
        sraPaint.setColor(0xFFF59E0B);
        avlPaint.setColor(0xFF22D3EE);
        rsiPaint.setColor(0xFFFFB020);
        kdjJPaint.setColor(0xFFFF5A5F);
        macdDifPaint.setStrokeWidth(dp(1.4f));
        macdDeaPaint.setStrokeWidth(dp(1.4f));
        stochKPaint.setStrokeWidth(dp(1.3f));
        stochDPaint.setStrokeWidth(dp(1.3f));
        maPaint.setStrokeWidth(dp(1.2f));
        emaPaint.setStrokeWidth(dp(1.2f));
        sraPaint.setStrokeWidth(dp(1.2f));
        avlPaint.setStrokeWidth(dp(1.2f));
        rsiPaint.setStrokeWidth(dp(1.3f));
        kdjJPaint.setStrokeWidth(dp(1.3f));
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
        aggregateCostLinePaint.setStrokeWidth(dp(0.8f));
        aggregateCostLinePaint.setColor(0xE6FFFFFF);
        aggregateCostTagPaint.setColor(0xE6FFFFFF);
        aggregateCostTagTextPaint.setColor(0xFF111827);
        aggregateCostTagTextPaint.setTextSize(dp(8.5f));
        aggregateCostTagTextPaint.setFakeBoldText(true);
        aggregateCostHintTextPaint.setColor(0xCCE2EDFF);
        aggregateCostHintTextPaint.setTextSize(dp(8f));
        aggregateCostHintTextPaint.setFakeBoldText(true);
        aggregateCostHintTextPaint.setTextAlign(Paint.Align.RIGHT);

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
        sanitizeHighlightedAnnotationGroup();
        invalidate();
    }

    public void setPendingAnnotations(@Nullable List<PriceAnnotation> items) {
        pendingAnnotations.clear();
        if (items != null && !items.isEmpty()) {
            pendingAnnotations.addAll(items);
        }
        sanitizeHighlightedAnnotationGroup();
        invalidate();
    }

    public void setAbnormalAnnotations(@Nullable List<PriceAnnotation> items) {
        abnormalAnnotations.clear();
        if (items != null && !items.isEmpty()) {
            abnormalAnnotations.addAll(items);
        }
        sanitizeHighlightedAnnotationGroup();
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

    public void setExtendedIndicatorsVisible(boolean ma,
                                             boolean ema,
                                             boolean sra,
                                             boolean avl,
                                             boolean rsi,
                                             boolean kdj) {
        showMa = ma;
        showEma = ema;
        showSra = sra;
        showAvl = avl;
        showRsi = rsi;
        showKdj = kdj;
        invalidate();
    }

    public void setAdvancedIndicatorParams(int maPeriod,
                                           int emaPeriod,
                                           int sraPeriod,
                                           int avlPeriod,
                                           int rsiPeriod,
                                           int kdjPeriod,
                                           int kdjSmoothK,
                                           int kdjSmoothD) {
        this.maPeriod = sanitizePeriod(maPeriod, 1, 360, 20);
        this.emaPeriod = sanitizePeriod(emaPeriod, 1, 360, 12);
        this.sraPeriod = sanitizePeriod(sraPeriod, 1, 360, 14);
        this.avlPeriod = sanitizePeriod(avlPeriod, 1, 360, 20);
        this.rsiPeriod = sanitizePeriod(rsiPeriod, 2, 360, 14);
        this.kdjPeriod = sanitizePeriod(kdjPeriod, 2, 360, 9);
        this.kdjSmoothK = sanitizePeriod(kdjSmoothK, 1, 120, 3);
        this.kdjSmoothD = sanitizePeriod(kdjSmoothD, 1, 120, 3);
        computeIndicators();
        invalidate();
    }

    public void setCoreIndicatorParams(int bollPeriod,
                                       int bollStdMultiplier,
                                       int macdFastPeriod,
                                       int macdSlowPeriod,
                                       int macdSignalPeriod,
                                       int stochRsiLookback,
                                       int stochRsiSmoothK,
                                       int stochRsiSmoothD) {
        this.bollPeriod = sanitizePeriod(bollPeriod, 2, 360, 20);
        this.bollStdMultiplier = sanitizePeriod(bollStdMultiplier, 1, 10, 2);
        this.macdFastPeriod = sanitizePeriod(macdFastPeriod, 1, 240, 12);
        this.macdSlowPeriod = sanitizePeriod(macdSlowPeriod, this.macdFastPeriod + 1, 360, 26);
        this.macdSignalPeriod = sanitizePeriod(macdSignalPeriod, 1, 120, 9);
        this.stochRsiLookback = sanitizePeriod(stochRsiLookback, 2, 240, 14);
        this.stochRsiSmoothK = sanitizePeriod(stochRsiSmoothK, 1, 120, 3);
        this.stochRsiSmoothD = sanitizePeriod(stochRsiSmoothD, 1, 120, 3);
        computeIndicators();
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
            if (showMa && i < maLine.length && !Double.isNaN(maLine[i])) {
                min = Math.min(min, maLine[i]);
                max = Math.max(max, maLine[i]);
            }
            if (showEma && i < emaLine.length && !Double.isNaN(emaLine[i])) {
                min = Math.min(min, emaLine[i]);
                max = Math.max(max, emaLine[i]);
            }
            if (showSra && i < sraLine.length && !Double.isNaN(sraLine[i])) {
                min = Math.min(min, sraLine[i]);
                max = Math.max(max, sraLine[i]);
            }
        }
        if (max <= min) {
            max = min + 1d;
        }
        double pad = (max - min) * 0.06d;
        visiblePriceMin = min - pad;
        visiblePriceMax = max + pad;
        double centerPrice = (visiblePriceMin + visiblePriceMax) * 0.5d;
        double halfRange = Math.max(1e-6d, (visiblePriceMax - visiblePriceMin) * 0.5d);
        double scaledHalf = halfRange / Math.max(1e-6f, verticalScale);
        visiblePriceMin = centerPrice - scaledHalf;
        visiblePriceMax = centerPrice + scaledHalf;
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
        drawOverlayAnnotations(canvas, abnormalAnnotations);
        drawAggregateCostAnnotation(canvas);
        int infoIndex = resolveInfoIndex(end);
        drawPriceOhlcInfo(canvas, infoIndex);
        if (showBoll) {
            drawSeries(canvas, bollMid, start, end, visiblePriceMin, visiblePriceMax, priceRect, bollMidPaint, drawStep);
            drawSeries(canvas, bollUp, start, end, visiblePriceMin, visiblePriceMax, priceRect, bollUpPaint, drawStep);
            drawSeries(canvas, bollDn, start, end, visiblePriceMin, visiblePriceMax, priceRect, bollDnPaint, drawStep);
        }
        if (showMa) {
            drawSeries(canvas, maLine, start, end, visiblePriceMin, visiblePriceMax, priceRect, maPaint, drawStep);
        }
        if (showEma) {
            drawSeries(canvas, emaLine, start, end, visiblePriceMin, visiblePriceMax, priceRect, emaPaint, drawStep);
        }
        if (showSra) {
            drawSeries(canvas, sraLine, start, end, visiblePriceMin, visiblePriceMax, priceRect, sraPaint, drawStep);
        }
        if (showVolume && volRect.height() > 0f) {
            drawVolume(canvas, start, end, infoIndex, drawStep);
        }
        if (showMacd && macdRect.height() > 0f) {
            drawMacd(canvas, start, end, infoIndex, drawStep);
        }
        if ((showStochRsi || showRsi || showKdj) && stochRect.height() > 0f) {
            drawOscillator(canvas, start, end, infoIndex, drawStep);
        }
        if (longPressing) {
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
            if (showAvl && i < avlLine.length && !Double.isNaN(avlLine[i])) {
                maxVolume = Math.max(maxVolume, avlLine[i]);
            }
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
        if (showAvl) {
            drawSeries(canvas, avlLine, start, end, 0d, maxVolume, volRect, avlPaint, stride);
        }
        canvas.restoreToCount(saveCount);
        canvas.drawLine(volRect.left, volRect.top, volRect.right, volRect.top, axisPaint);
        textPaint.setColor(0xFF8FA6C7);
        String volText = "VOL: " + formatVolumeNumber(candles.get(infoIndex).getVolume(), false);
        if (showAvl && infoIndex >= 0 && infoIndex < avlLine.length && !Double.isNaN(avlLine[infoIndex])) {
            volText += " AVL(" + avlPeriod + "):" + formatVolumeNumber(avlLine[infoIndex], false);
        }
        canvas.drawText(volText, volRect.left + dp(2f), volRect.top + dp(10f), textPaint);
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

    private void drawOscillator(Canvas canvas, int start, int end, int infoIndex, int step) {
        double min = 0d;
        double max = 100d;
        for (int i = start; i <= end; i++) {
            if (showStochRsi) {
                min = Math.min(min, Math.min(valueOrFallback(stochK, i, 50d), valueOrFallback(stochD, i, 50d)));
                max = Math.max(max, Math.max(valueOrFallback(stochK, i, 50d), valueOrFallback(stochD, i, 50d)));
            }
            if (showRsi) {
                min = Math.min(min, valueOrFallback(rsiLine, i, 50d));
                max = Math.max(max, valueOrFallback(rsiLine, i, 50d));
            }
            if (showKdj) {
                min = Math.min(min, Math.min(valueOrFallback(kdjK, i, 50d),
                        Math.min(valueOrFallback(kdjD, i, 50d), valueOrFallback(kdjJ, i, 50d))));
                max = Math.max(max, Math.max(valueOrFallback(kdjK, i, 50d),
                        Math.max(valueOrFallback(kdjD, i, 50d), valueOrFallback(kdjJ, i, 50d))));
            }
        }
        if (max <= min) {
            max = min + 1d;
        }
        double pad = (max - min) * 0.08d;
        double lower = min - pad;
        double upper = max + pad;

        int stride = Math.max(1, step);
        if (showStochRsi) {
            drawSeries(canvas, stochK, start, end, lower, upper, stochRect, stochKPaint, stride);
            drawSeries(canvas, stochD, start, end, lower, upper, stochRect, stochDPaint, stride);
        }
        if (showRsi) {
            drawSeries(canvas, rsiLine, start, end, lower, upper, stochRect, rsiPaint, stride);
        }
        if (showKdj) {
            drawSeries(canvas, kdjK, start, end, lower, upper, stochRect, stochKPaint, stride);
            drawSeries(canvas, kdjD, start, end, lower, upper, stochRect, stochDPaint, stride);
            drawSeries(canvas, kdjJ, start, end, lower, upper, stochRect, kdjJPaint, stride);
        }

        canvas.drawLine(stochRect.left, stochRect.top, stochRect.right, stochRect.top, gridPaint);
        canvas.drawLine(stochRect.left, stochRect.bottom, stochRect.right, stochRect.bottom, axisPaint);
        canvas.drawLine(stochRect.right, stochRect.top, stochRect.right, stochRect.bottom, axisPaint);

        textPaint.setColor(0xFF8FA6C7);
        canvas.drawText(formatAxisInt(upper), stochRect.right + dp(4f), stochRect.top + dp(3f), textPaint);
        canvas.drawText(formatAxisInt((upper + lower) * 0.5d),
                stochRect.right + dp(4f),
                yFor((upper + lower) * 0.5d, lower, upper, stochRect) + dp(3f),
                textPaint);
        canvas.drawText(formatAxisInt(lower), stochRect.right + dp(4f), stochRect.bottom + dp(3f), textPaint);
        drawOscillatorInfo(canvas, infoIndex);
    }

    private void drawOscillatorInfo(Canvas canvas, int index) {
        if (index < 0 || index >= candles.size()) {
            return;
        }
        float y = stochRect.top + dp(10f);
        float x = stochRect.left + dp(2f);
        textPaint.setColor(0xFF8FA6C7);

        if (showRsi) {
            String text = "RSI(" + rsiPeriod + "):" + formatDecimal(valueAt(rsiLine, index));
            canvas.drawText(text, x, y, textPaint);
            x += textPaint.measureText(text) + dp(8f);
        }
        if (showKdj) {
            String text = "KDJ(" + kdjPeriod + "," + kdjSmoothK + "," + kdjSmoothD + ")"
                    + " K:" + formatDecimal(valueAt(kdjK, index))
                    + " D:" + formatDecimal(valueAt(kdjD, index))
                    + " J:" + formatDecimal(valueAt(kdjJ, index));
            canvas.drawText(text, x, y, textPaint);
            x += textPaint.measureText(text) + dp(8f);
        }
        if (showStochRsi) {
            String text = "STOCHRSI(" + stochRsiLookback + "," + stochRsiSmoothK + "," + stochRsiSmoothD + ")"
                    + " K:" + formatDecimal(valueAt(stochK, index))
                    + " D:" + formatDecimal(valueAt(stochD, index));
            canvas.drawText(text, x, y, textPaint);
        }
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
        canvas.drawText("BOLL(" + bollPeriod + "," + bollStdMultiplier + ")", priceRect.left + dp(2f), y, textPaint);
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
        String text = "MACD(" + macdFastPeriod + "," + macdSlowPeriod + "," + macdSignalPeriod + ")"
                + " DIF:" + formatDecimal(macdDif[index])
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
        boolean hasGroupHighlight = !highlightedAnnotationGroupId.isEmpty();
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
            String groupKey = resolveAnnotationGroupKey(annotation);
            boolean selected = hasGroupHighlight && highlightedAnnotationGroupId.equals(groupKey);
            int baseColor = annotation.color == 0 ? 0xFFE2EDFF : annotation.color;
            int lineColor = hasGroupHighlight && !selected ? applyAlpha(baseColor, 0.34f) : baseColor;
            float lineWidth = hasGroupHighlight
                    ? (selected ? dp(1.2f) : dp(0.35f))
                    : dp(0.55f);
            float pointRadius = hasGroupHighlight
                    ? (selected ? dp(2.4f) : dp(1.1f))
                    : dp(1.7f);
            overlayDashPaint.setColor(lineColor);
            overlayDashPaint.setStrokeWidth(lineWidth);
            canvas.drawLine(priceRect.left, y, priceRect.right, y, overlayDashPaint);

            float x = resolveAnnotationX(annotation.anchorTimeMs);
            overlayPointPaint.setColor(lineColor);
            canvas.drawCircle(x, y, pointRadius, overlayPointPaint);

            if (!annotation.label.isEmpty()) {
                drawOverlayLabel(canvas, annotation.label, y, lineColor, selected);
            }
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawOverlayLabel(Canvas canvas, String text, float anchorY, int textColor, boolean selected) {
        float padX = dp(4f);
        float padY = dp(2f);
        float boxH = dp(12f);
        float boxW = overlayLabelTextPaint.measureText(text) + padX * 2f;
        float left = priceRect.left + dp(2f);
        float top = clamp(anchorY - boxH - dp(2f), priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        int labelBgColor = selected ? 0xE61B2A40 : 0xCC1D2A3F;
        overlayLabelBgPaint.setColor(labelBgColor);
        canvas.drawRoundRect(box, dp(selected ? 3f : 2.5f), dp(selected ? 3f : 2.5f), overlayLabelBgPaint);
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
        if (!aggregateCostAnnotation.symbolLabel.isEmpty()) {
            String hint = aggregateCostAnnotation.symbolLabel + " 成本";
            float hintBaseline = clamp(y - dp(6f), priceRect.top + dp(8f), priceRect.bottom - dp(2f));
            canvas.drawText(hint, priceRect.right - dp(2f), hintBaseline, aggregateCostHintTextPaint);
        }
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
        if (!crosshairOnCandle || index < 0 || index >= candles.size()) {
            return;
        }
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

        String timeText = (crosshairOnCandle && index >= 0 && index < candles.size())
                ? timeFmt.format(new Date(candles.get(index).getOpenTime()))
                : "-";
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
        boolean hasCandle = crosshairOnCandle && index >= 0 && index < candles.size();
        CandleEntry candle = hasCandle ? candles.get(index) : null;
        String timeText = hasCandle ? axisTimeFmt.format(new Date(candle.getOpenTime())) : "-";
        String openText = hasCandle ? FormatUtils.formatPrice(candle.getOpen()) : "-";
        String highText = hasCandle ? FormatUtils.formatPrice(candle.getHigh()) : "-";
        String lowText = hasCandle ? FormatUtils.formatPrice(candle.getLow()) : "-";
        String closeText = hasCandle ? FormatUtils.formatPrice(candle.getClose()) : "-";
        String changeText = hasCandle ? formatSignedPriceDelta(candle.getClose() - candle.getOpen()) : "-";
        String volText = hasCandle ? formatVolumeNumber(candle.getVolume(), false) : "-";
        String tovText = hasCandle ? formatVolumeNumber(candle.getQuoteVolume(), false) : "-";
        String[] lines = new String[]{
                "时间 " + timeText,
                "O " + openText,
                "H " + highText,
                "L " + lowText,
                "C " + closeText,
                "价格变动 " + changeText,
                "VOL " + volText,
                "TOV " + tovText
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
        boolean oscillatorVisible = showStochRsi || showRsi || showKdj;
        float sw = oscillatorVisible ? 2f : 0f;
        float gap = dp(10f);
        int sections = 1 + (showVolume ? 1 : 0) + (showMacd ? 1 : 0) + (oscillatorVisible ? 1 : 0);
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
        if (oscillatorVisible) {
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
        maLine = nanArray(size);
        emaLine = nanArray(size);
        sraLine = nanArray(size);
        avlLine = nanArray(size);
        rsiLine = nanArray(size);
        kdjK = nanArray(size);
        kdjD = nanArray(size);
        kdjJ = nanArray(size);
        if (size == 0) return;

        int bollWindow = Math.max(2, bollPeriod);
        for (int i = bollWindow - 1; i < size; i++) {
            double sum = 0d;
            for (int j = i - (bollWindow - 1); j <= i; j++) sum += candles.get(j).getClose();
            double mean = sum / bollWindow;
            double var = 0d;
            for (int j = i - (bollWindow - 1); j <= i; j++) {
                double diff = candles.get(j).getClose() - mean;
                var += diff * diff;
            }
            double std = Math.sqrt(var / bollWindow);
            bollMid[i] = mean;
            bollUp[i] = mean + bollStdMultiplier * std;
            bollDn[i] = mean - bollStdMultiplier * std;
        }

        for (int i = maPeriod - 1; i < size; i++) {
            double sum = 0d;
            for (int j = i - maPeriod + 1; j <= i; j++) {
                sum += candles.get(j).getClose();
            }
            maLine[i] = sum / maPeriod;
        }

        double ema = candles.get(0).getClose();
        double emaAlpha = 2d / (emaPeriod + 1d);
        for (int i = 0; i < size; i++) {
            double close = candles.get(i).getClose();
            ema = i == 0 ? close : (ema + emaAlpha * (close - ema));
            if (i >= emaPeriod - 1) {
                emaLine[i] = ema;
            }
        }

        double sra = candles.get(0).getClose();
        for (int i = 0; i < size; i++) {
            double close = candles.get(i).getClose();
            sra = i == 0 ? close : ((sra * (sraPeriod - 1d)) + close) / sraPeriod;
            if (i >= sraPeriod - 1) {
                sraLine[i] = sra;
            }
        }

        for (int i = avlPeriod - 1; i < size; i++) {
            double sum = 0d;
            for (int j = i - avlPeriod + 1; j <= i; j++) {
                sum += candles.get(j).getVolume();
            }
            avlLine[i] = sum / avlPeriod;
        }

        double ema12 = candles.get(0).getClose();
        double ema26 = ema12;
        double alphaFast = 2d / (macdFastPeriod + 1d);
        double alphaSlow = 2d / (macdSlowPeriod + 1d);
        double alphaSignal = 2d / (macdSignalPeriod + 1d);
        double dea = 0d;
        for (int i = 0; i < size; i++) {
            double close = candles.get(i).getClose();
            ema12 = ema12 + alphaFast * (close - ema12);
            ema26 = ema26 + alphaSlow * (close - ema26);
            double dif = ema12 - ema26;
            dea = dea + alphaSignal * (dif - dea);
            macdDif[i] = dif;
            macdDea[i] = dea;
            macdHist[i] = (dif - dea) * 2d;
        }

        if (size > rsiPeriod) {
            double gain = 0d;
            double loss = 0d;
            for (int i = 1; i <= rsiPeriod; i++) {
                double diff = candles.get(i).getClose() - candles.get(i - 1).getClose();
                gain += Math.max(0d, diff);
                loss += Math.max(0d, -diff);
            }
            gain /= rsiPeriod;
            loss /= rsiPeriod;
            rsiLine[rsiPeriod] = loss < 1e-9 ? 100d : 100d - 100d / (1d + gain / loss);
            for (int i = rsiPeriod + 1; i < size; i++) {
                double diff = candles.get(i).getClose() - candles.get(i - 1).getClose();
                gain = (gain * (rsiPeriod - 1d) + Math.max(0d, diff)) / rsiPeriod;
                loss = (loss * (rsiPeriod - 1d) + Math.max(0d, -diff)) / rsiPeriod;
                rsiLine[i] = loss < 1e-9 ? 100d : 100d - 100d / (1d + gain / loss);
            }
        }

        double prevK = 50d;
        double prevD = 50d;
        for (int i = 0; i < size; i++) {
            if (i < kdjPeriod - 1) {
                continue;
            }
            double highest = -Double.MAX_VALUE;
            double lowest = Double.MAX_VALUE;
            for (int j = i - kdjPeriod + 1; j <= i; j++) {
                CandleEntry item = candles.get(j);
                highest = Math.max(highest, item.getHigh());
                lowest = Math.min(lowest, item.getLow());
            }
            double close = candles.get(i).getClose();
            double rsv = Math.abs(highest - lowest) < 1e-9 ? 50d : (close - lowest) * 100d / (highest - lowest);
            prevK = ((kdjSmoothK - 1d) * prevK + rsv) / kdjSmoothK;
            prevD = ((kdjSmoothD - 1d) * prevD + prevK) / kdjSmoothD;
            kdjK[i] = prevK;
            kdjD[i] = prevD;
            kdjJ[i] = 3d * prevK - 2d * prevD;
        }

        double[] raw = nanArray(size);
        int stochLookback = Math.max(2, stochRsiLookback);
        int stochSmoothK = Math.max(1, stochRsiSmoothK);
        int stochSmoothD = Math.max(1, stochRsiSmoothD);
        for (int i = rsiPeriod + stochLookback - 1; i < size; i++) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (int j = i - stochLookback + 1; j <= i; j++) {
                if (Double.isNaN(rsiLine[j])) {
                    min = Double.NaN;
                    break;
                }
                min = Math.min(min, rsiLine[j]);
                max = Math.max(max, rsiLine[j]);
            }
            if (Double.isNaN(min)) continue;
            raw[i] = Math.abs(max - min) < 1e-9 ? 50d : (rsiLine[i] - min) * 100d / (max - min);
            stochK[i] = avg(raw, i, stochSmoothK);
            stochD[i] = avg(stochK, i, stochSmoothD);
        }
    }

    private void updateCrosshair(float x, float y) {
        if (candles.isEmpty()) return;
        crosshairX = clamp(x, priceRect.left, priceRect.right);
        crosshairY = clamp(y, priceRect.top, priceRect.bottom);
        float rawIndex = xToRawIndex(crosshairX, visibleEndFloat);
        crosshairOnCandle = rawIndex >= -0.05f && rawIndex <= candles.size() - 1f + 0.05f;
        highlightedIndex = Math.round(rawIndex);
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
        crosshairOnCandle = true;
        notifyCrosshair();
        invalidate();
    }

    private void notifyCrosshair() {
        if (onCrosshairListener == null) return;
        if (!longPressing || !crosshairOnCandle || highlightedIndex < 0 || highlightedIndex >= candles.size()) {
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

    private double valueOrFallback(double[] values, int index, double fallback) {
        double value = valueAt(values, index);
        if (Double.isNaN(value)) {
            return fallback;
        }
        return value;
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

    private int sanitizePeriod(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
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
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return axis ? "0" : "-";
        }
        if (axis) {
            return new DecimalFormat("#,##0").format(value);
        }
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000d) {
            return compactNumber(value / 1_000_000_000d) + "b";
        }
        if (abs >= 1_000_000d) {
            return compactNumber(value / 1_000_000d) + "m";
        }
        if (abs >= 1_000d) {
            return compactNumber(value / 1_000d) + "k";
        }
        return new DecimalFormat("#,##0.##").format(value);
    }

    private String compactNumber(double value) {
        return new DecimalFormat("0.##").format(value);
    }

    private String formatAxisInt(double value) {
        return String.format(Locale.getDefault(), "%,.0f", value);
    }

    private String formatDecimal(double value) {
        if (Double.isNaN(value)) return "--";
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private String formatSignedPriceDelta(double value) {
        return String.format(Locale.getDefault(), "%+.2f", value);
    }

    private boolean selectAnnotationGroupByTouch(float x, float y) {
        if (priceRect.isEmpty() || x < priceRect.left || x > priceRect.right || y < priceRect.top || y > priceRect.bottom) {
            return false;
        }
        PriceAnnotation matched = findNearestAnnotation(y, dp(11f));
        if (matched == null) {
            return false;
        }
        String groupKey = resolveAnnotationGroupKey(matched);
        if (groupKey.isEmpty()) {
            return false;
        }
        if (groupKey.equals(highlightedAnnotationGroupId)) {
            return true;
        }
        highlightedAnnotationGroupId = groupKey;
        invalidate();
        return true;
    }

    private void clearHighlightedAnnotationGroup() {
        if (highlightedAnnotationGroupId.isEmpty()) {
            return;
        }
        highlightedAnnotationGroupId = "";
        invalidate();
    }

    private void sanitizeHighlightedAnnotationGroup() {
        if (highlightedAnnotationGroupId.isEmpty()) {
            return;
        }
        for (PriceAnnotation item : positionAnnotations) {
            if (highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(item))) {
                return;
            }
        }
        for (PriceAnnotation item : pendingAnnotations) {
            if (highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(item))) {
                return;
            }
        }
        for (PriceAnnotation item : abnormalAnnotations) {
            if (highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(item))) {
                return;
            }
        }
        highlightedAnnotationGroupId = "";
    }

    @Nullable
    private PriceAnnotation findNearestAnnotation(float touchY, float thresholdPx) {
        PriceAnnotation nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        nearest = findNearestAnnotationInList(positionAnnotations, touchY, thresholdPx, nearestDistance);
        if (nearest != null) {
            nearestDistance = Math.abs(yFor(nearest.price, visiblePriceMin, visiblePriceMax, priceRect) - touchY);
        }
        PriceAnnotation pendingNearest = findNearestAnnotationInList(pendingAnnotations, touchY, thresholdPx, nearestDistance);
        if (pendingNearest != null) {
            nearest = pendingNearest;
            nearestDistance = Math.abs(yFor(nearest.price, visiblePriceMin, visiblePriceMax, priceRect) - touchY);
        }
        PriceAnnotation abnormalNearest = findNearestAnnotationInList(abnormalAnnotations, touchY, thresholdPx, nearestDistance);
        if (abnormalNearest != null) {
            nearest = abnormalNearest;
        }
        return nearest;
    }

    @Nullable
    private PriceAnnotation findNearestAnnotationInList(List<PriceAnnotation> source,
                                                        float touchY,
                                                        float thresholdPx,
                                                        float currentBestDistance) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        PriceAnnotation nearest = null;
        float best = currentBestDistance;
        for (PriceAnnotation annotation : source) {
            if (annotation == null || annotation.price <= 0d) {
                continue;
            }
            float y = yFor(annotation.price, visiblePriceMin, visiblePriceMax, priceRect);
            if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
                continue;
            }
            float distance = Math.abs(y - touchY);
            if (distance > thresholdPx || distance >= best) {
                continue;
            }
            nearest = annotation;
            best = distance;
        }
        return nearest;
    }

    private String resolveAnnotationGroupKey(@Nullable PriceAnnotation annotation) {
        if (annotation == null) {
            return "";
        }
        if (!annotation.groupId.isEmpty()) {
            return annotation.groupId;
        }
        return annotation.anchorTimeMs + "|" + annotation.price + "|" + annotation.label;
    }

    private int applyAlpha(int color, float factor) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int alpha = Math.max(0, Math.min(255, Math.round(baseAlpha * factor)));
        return (color & 0x00FFFFFF) | (alpha << 24);
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
            float spanXDelta = detector.getCurrentSpanX() - detector.getPreviousSpanX();
            float spanYDelta = detector.getCurrentSpanY() - detector.getPreviousSpanY();
            float absX = Math.abs(spanXDelta);
            float absY = Math.abs(spanYDelta);
            boolean verticalDominant = absY > absX * 1.12f;
            boolean horizontalDominant = absX > absY * 1.12f;
            if (verticalDominant) {
                float rawYScale = detector.getPreviousSpanY() <= 1e-3f
                        ? detector.getScaleFactor()
                        : detector.getCurrentSpanY() / detector.getPreviousSpanY();
                float smoothYScale = 1f + (rawYScale - 1f) * 0.5f;
                verticalScale = clamp(verticalScale * smoothYScale, MIN_VERTICAL_SCALE, MAX_VERTICAL_SCALE);
                requestFrame();
                return true;
            }
            float beforeSlot = slot();
            int focusIndex = xToIndex(detector.getFocusX(), visibleEndFloat);
            float rawScale = horizontalDominant
                    ? (detector.getPreviousSpanX() <= 1e-3f
                    ? detector.getScaleFactor()
                    : detector.getCurrentSpanX() / detector.getPreviousSpanX())
                    : detector.getScaleFactor();
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

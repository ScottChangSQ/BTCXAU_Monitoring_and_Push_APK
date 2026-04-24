/*
 * K 线图自定义绘制控件，负责行情主图、指标、副图和各类价格标注的渲染与交互。
 * 与行情图页、主题系统和账户交易标注模块协同工作。
 */
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.binance.monitor.R;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.ui.theme.SpacingTokenResolver;
import com.binance.monitor.ui.theme.TextAppearanceScaleResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;
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
    public static final int ANNOTATION_KIND_DEFAULT = 0;
    public static final int ANNOTATION_KIND_HISTORY_ENTRY_BUY = 1;
    public static final int ANNOTATION_KIND_HISTORY_ENTRY_SELL = 2;
    public static final int ANNOTATION_KIND_HISTORY_EXIT = 3;
    public static final int ANNOTATION_KIND_HISTORY_CONNECTOR = 4;

    public static class PriceAnnotation {
        public final long anchorTimeMs;
        public final double price;
        public final String label;
        public final int color;
        public final String groupId;
        public final int eventCount;
        public final float intensity;
        public final long secondaryAnchorTimeMs;
        public final double secondaryPrice;
        public final int kind;
        public final String[] detailLines;

        public PriceAnnotation(long anchorTimeMs, double price, String label, int color) {
            this(anchorTimeMs, price, label, color, "", 1, 0f, 0L, Double.NaN, ANNOTATION_KIND_DEFAULT, null);
        }

        public PriceAnnotation(long anchorTimeMs, double price, String label, int color, @Nullable String groupId) {
            this(anchorTimeMs, price, label, color, groupId, 1, 0f, 0L, Double.NaN, ANNOTATION_KIND_DEFAULT, null);
        }

        public PriceAnnotation(long anchorTimeMs,
                               double price,
                               String label,
                               int color,
                               @Nullable String groupId,
                               int eventCount,
                               float intensity) {
            this(anchorTimeMs, price, label, color, groupId, eventCount, intensity,
                    0L, Double.NaN, ANNOTATION_KIND_DEFAULT, null);
        }

        public PriceAnnotation(long anchorTimeMs,
                               double price,
                               String label,
                               int color,
                               @Nullable String groupId,
                               int eventCount,
                               float intensity,
                               long secondaryAnchorTimeMs,
                               double secondaryPrice,
                               int kind,
                               @Nullable String[] detailLines) {
            this.anchorTimeMs = anchorTimeMs;
            this.price = price;
            this.label = label == null ? "" : label;
            this.color = color;
            this.groupId = groupId == null ? "" : groupId.trim();
            this.eventCount = Math.max(1, eventCount);
            this.intensity = Math.max(0f, Math.min(1f, intensity));
            this.secondaryAnchorTimeMs = secondaryAnchorTimeMs;
            this.secondaryPrice = secondaryPrice;
            this.kind = kind;
            this.detailLines = detailLines == null ? new String[0] : detailLines.clone();
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

    public interface OnVolumePaneLayoutListener {
        void onVolumePaneLayoutChanged(int left, int top, int right, int bottom);
    }

    public interface OnQuickPendingLineChangeListener {
        void onPriceChanged(double price);
    }

    private static final class FocusedTradeAxisLabel {
        private final String text;
        private final int color;
        private final float desiredCenterY;
        private float top = Float.NaN;

        private FocusedTradeAxisLabel(@NonNull String text, int color, float desiredCenterY) {
            this.text = text;
            this.color = color;
            this.desiredCenterY = desiredCenterY;
        }
    }

    public interface OnTradeLineEditListener {
        void onTradeLineDragStart(@NonNull ChartTradeLine line);

        void onTradeLineDragUpdate(@NonNull ChartTradeLine line, double price);

        void onTradeLineActionClick(@NonNull ChartTradeLine line);

        void onTradeLineBlankAreaTap();
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
    private boolean showMa = false;
    private boolean showEma = false;
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
    private final Paint popupPositiveTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint popupNegativeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestPriceGuidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestPriceTagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint latestPriceTagTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    private final Paint quickPendingLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint quickPendingTagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint quickPendingTagTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable
    private UiPaletteManager.Palette activePalette;
    private int secondaryTextColor;

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
    private final RectF rsiRect = new RectF();
    private final RectF kdjRect = new RectF();

    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleGestureDetector;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private final SimpleDateFormat axisTimeFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private float candleWidth;
    private float candleGap;
    private float minWidth;
    private float maxWidth;
    private static final float DEFAULT_CANDLE_WIDTH_DP = 1.28f;
    private static final float DEFAULT_CANDLE_GAP_DP = 0.88f;
    // 图表右侧固定保留绘图区 1/7 的空白，避免最新 K 线贴边。
    private static final float RIGHT_BLANK_RATIO = 1f / 7f;
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
    private final List<PriceAnnotation> historyTradeAnnotations = new ArrayList<>();
    @Nullable
    private AggregateCostAnnotation aggregateCostAnnotation;
    private ChartTradeLayerSnapshot tradeLayerSnapshot = new ChartTradeLayerSnapshot(null, null);
    private boolean showPositionAnnotations = true;
    private boolean showPendingAnnotations = true;
    private boolean showHistoryTradeAnnotations = true;
    private boolean showAggregateCostAnnotation = true;
    private String highlightedAnnotationGroupId = "";
    private boolean crosshairOnCandle = true;
    private boolean quickPendingLineVisible;
    private boolean quickPendingLineDragging;
    private double quickPendingLinePrice = Double.NaN;
    private int quickPendingLinePointerId = -1;
    private float quickPendingLineLastTouchY = Float.NaN;
    private boolean quickPendingLineTouchExclusive;
    private boolean tradeLineEditDragging;
    private int tradeLineEditPointerId = -1;
    private float tradeLineEditLastTouchY = Float.NaN;
    private double tradeLineEditCurrentPrice = Double.NaN;
    @Nullable
    private ChartTradeLine activeTradeLineEdit;
    @Nullable
    private ChartTradeLine pressedTradeLineAction;

    private boolean requestingMore;
    private long lastRequestedBefore = Long.MIN_VALUE;
    private long lastRequestAtMs;
    private boolean latestCandleOutOfBounds;

    private OnCrosshairListener onCrosshairListener;
    private OnRequestMoreListener onRequestMoreListener;
    private OnViewportStateListener onViewportStateListener;
    private OnPricePaneLayoutListener onPricePaneLayoutListener;
    private OnVolumePaneLayoutListener onVolumePaneLayoutListener;
    private OnQuickPendingLineChangeListener onQuickPendingLineChangeListener;
    private OnTradeLineEditListener onTradeLineEditListener;
    private int lastPricePaneLeft = Integer.MIN_VALUE;
    private int lastPricePaneTop = Integer.MIN_VALUE;
    private int lastPricePaneRight = Integer.MIN_VALUE;
    private int lastPricePaneBottom = Integer.MIN_VALUE;
    private int lastVolumePaneLeft = Integer.MIN_VALUE;
    private int lastVolumePaneTop = Integer.MIN_VALUE;
    private int lastVolumePaneRight = Integer.MIN_VALUE;
    private int lastVolumePaneBottom = Integer.MIN_VALUE;

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
        candleWidth = dp(DEFAULT_CANDLE_WIDTH_DP);
        candleGap = dp(DEFAULT_CANDLE_GAP_DP);
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
                if (!highlightedAnnotationGroupId.isEmpty()) {
                    if (isTouchInsideHighlightedGroup(e.getX(), e.getY())) {
                        return true;
                    }
                    clearHighlightedAnnotationGroup();
                    if (onTradeLineEditListener != null) {
                        onTradeLineEditListener.onTradeLineBlankAreaTap();
                    }
                    return true;
                }
                if (shouldExitQuickPendingModeByTap(e.getX(), e.getY())) {
                    clearHighlightedAnnotationGroup();
                    if (onTradeLineEditListener != null) {
                        onTradeLineEditListener.onTradeLineBlankAreaTap();
                    }
                    return true;
                }
                if (!selectAnnotationGroupByTouch(e.getX(), e.getY())) {
                    clearHighlightedAnnotationGroup();
                    if (onTradeLineEditListener != null) {
                        onTradeLineEditListener.onTradeLineBlankAreaTap();
                    }
                }
                return true;
            }
        });
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    private void initPaints() {
        axisPaint.setStrokeWidth(dp(1f));
        TextAppearanceScaleResolver.applyTextSize(textPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        bollMidPaint.setStrokeWidth(dp(1f));
        bollUpPaint.setStrokeWidth(dp(1f));
        bollDnPaint.setStrokeWidth(dp(1f));
        line1Paint.setStrokeWidth(dp(1f));
        line2Paint.setStrokeWidth(dp(1f));
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
        crossPaint.setStrokeWidth(dp(1f));
        TextAppearanceScaleResolver.applyTextSize(crossLabelTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        TextAppearanceScaleResolver.applyTextSize(popupTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        TextAppearanceScaleResolver.applyTextSize(popupPositiveTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        TextAppearanceScaleResolver.applyTextSize(popupNegativeTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        latestPriceGuidePaint.setStyle(Paint.Style.STROKE);
        latestPriceGuidePaint.setStrokeWidth(dp(1f));
        latestPriceGuidePaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(3f)}, 0f));
        TextAppearanceScaleResolver.applyTextSize(latestPriceTagTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        latestPriceTagTextPaint.setFakeBoldText(false);
        extremeHighPaint.setStyle(Paint.Style.FILL);
        extremeLowPaint.setStyle(Paint.Style.FILL);
        extremeConnectorPaint.setStyle(Paint.Style.STROKE);
        extremeConnectorPaint.setStrokeWidth(dp(1f));
        TextAppearanceScaleResolver.applyTextSize(extremeLabelTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        extremeLabelTextPaint.setFakeBoldText(false);

        overlayDashPaint.setStyle(Paint.Style.STROKE);
        overlayDashPaint.setStrokeWidth(dp(0.55f));
        overlayDashPaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(3f)}, 0f));
        overlayPointPaint.setStyle(Paint.Style.FILL);
        TextAppearanceScaleResolver.applyTextSize(overlayLabelTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        overlayLabelTextPaint.setFakeBoldText(false);

        aggregateCostLinePaint.setStyle(Paint.Style.STROKE);
        aggregateCostLinePaint.setStrokeWidth(dp(0.9f));
        TextAppearanceScaleResolver.applyTextSize(aggregateCostTagTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        aggregateCostTagTextPaint.setFakeBoldText(false);
        TextAppearanceScaleResolver.applyTextSize(aggregateCostHintTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        aggregateCostHintTextPaint.setFakeBoldText(false);
        aggregateCostHintTextPaint.setTextAlign(Paint.Align.CENTER);

        volumeThresholdPaint.setStyle(Paint.Style.STROKE);
        volumeThresholdPaint.setStrokeWidth(dp(1f));
        volumeThresholdPaint.setPathEffect(new DashPathEffect(new float[]{dp(4f), dp(3f)}, 0f));
        quickPendingLinePaint.setStyle(Paint.Style.STROKE);
        quickPendingLinePaint.setStrokeWidth(dp(0.95f));
        quickPendingLinePaint.setPathEffect(new DashPathEffect(new float[]{dp(6f), dp(4f)}, 0f));
        TextAppearanceScaleResolver.applyTextSize(quickPendingTagTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);
        quickPendingTagTextPaint.setFakeBoldText(false);
        applyPalette(UiPaletteManager.resolve(getContext()));
    }

    public void applyPalette(@Nullable UiPaletteManager.Palette palette) {
        if (palette == null) {
            return;
        }
        activePalette = palette;
        secondaryTextColor = palette.textSecondary;
        bgPaint.setColor(palette.surfaceEnd);
        gridPaint.setColor(applyAlpha(palette.stroke, 185));
        axisPaint.setColor(applyAlpha(palette.stroke, 235));
        textPaint.setColor(palette.textSecondary);
        upPaint.setColor(palette.rise);
        downPaint.setColor(palette.fall);
        applyClassicIndicatorColors(palette);
        crossPaint.setColor(applyAlpha(palette.textSecondary, 235));
        crossLabelBgPaint.setColor(applyAlpha(palette.card, 235));
        crossLabelTextPaint.setColor(palette.textPrimary);
        popupBgPaint.setColor(applyAlpha(palette.card, 230));
        popupTextPaint.setColor(palette.textPrimary);
        popupPositiveTextPaint.setColor(palette.rise);
        popupNegativeTextPaint.setColor(palette.fall);
        latestPriceTagPaint.setColor(palette.primary);
        latestPriceTagTextPaint.setColor(palette.textPrimary);
        latestPriceGuidePaint.setColor(applyAlpha(palette.primary, 220));
        extremeHighPaint.setColor(palette.xau);
        extremeLowPaint.setColor(palette.btc);
        extremeConnectorPaint.setColor(applyAlpha(palette.textSecondary, 215));
        extremeLabelBgPaint.setColor(applyAlpha(palette.card, 230));
        extremeLabelTextPaint.setColor(palette.textPrimary);
        overlayLabelBgPaint.setColor(applyAlpha(palette.card, 225));
        overlayLabelTextPaint.setColor(palette.textPrimary);
        aggregateCostLinePaint.setColor(palette.textPrimary);
        aggregateCostTagPaint.setColor(palette.textPrimary);
        aggregateCostTagTextPaint.setColor(palette.surfaceStart);
        aggregateCostHintTextPaint.setColor(palette.textPrimary);
        volumeThresholdPaint.setColor(applyAlpha(palette.textPrimary, 210));
        quickPendingLinePaint.setColor(applyAlpha(palette.primary, 230));
        quickPendingTagPaint.setColor(applyAlpha(palette.primary, 235));
        quickPendingTagTextPaint.setColor(palette.textPrimary);
        invalidate();
    }

    // 指标曲线使用固定经典配色，避免主题切换后多条线过于接近。
    private void applyClassicIndicatorColors(@NonNull UiPaletteManager.Palette palette) {
        bollMidPaint.setColor(palette.xau);
        bollUpPaint.setColor(palette.primary);
        bollDnPaint.setColor(blendColor(palette.primary, palette.btc, 0.45f));
        line1Paint.setColor(palette.rise);
        line2Paint.setColor(palette.btc);
        macdDifPaint.setColor(palette.xau);
        macdDeaPaint.setColor(palette.btc);
        stochKPaint.setColor(palette.xau);
        stochDPaint.setColor(palette.btc);
        maPaint.setColor(palette.primary);
        emaPaint.setColor(palette.rise);
        sraPaint.setColor(palette.xau);
        avlPaint.setColor(blendColor(palette.primary, palette.fall, 0.35f));
        rsiPaint.setColor(palette.fall);
        kdjJPaint.setColor(blendColor(palette.fall, palette.primary, 0.35f));
    }

    private int blendColor(int fromColor, int toColor, float ratio) {
        return ColorUtils.blendARGB(fromColor, toColor, Math.max(0f, Math.min(1f, ratio)));
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

    public void setOnVolumePaneLayoutListener(@Nullable OnVolumePaneLayoutListener listener) {
        onVolumePaneLayoutListener = listener;
        if (listener == null) {
            return;
        }
        if (ensureLayoutForMath()) {
            dispatchVolumePaneLayout(true);
        }
    }

    // 让外层页面接收拖动挂单线后的最新价格。
    public void setOnQuickPendingLineChangeListener(@Nullable OnQuickPendingLineChangeListener listener) {
        onQuickPendingLineChangeListener = listener;
    }

    public void setOnTradeLineEditListener(@Nullable OnTradeLineEditListener listener) {
        onTradeLineEditListener = listener;
    }

    // 外层页面重建草稿线快照时需要知道当前是否仍处于真实拖拽态，避免把拖拽中途打断。
    public boolean isQuickPendingLineDragging() {
        return quickPendingLineDragging;
    }

    // 显示图表内快捷挂单线，并以当前价格作为初始位置。
    public void showQuickPendingLine(double price) {
        if (!Double.isFinite(price) || price <= 0d) {
            hideQuickPendingLine();
            return;
        }
        quickPendingLineVisible = true;
        quickPendingLineDragging = false;
        quickPendingLinePrice = price;
        syncTradeLayerSnapshotWithQuickPendingLine();
        invalidate();
    }

    // 隐藏图表内快捷挂单线，退出挂单模式时统一调用。
    public void hideQuickPendingLine() {
        quickPendingLineVisible = false;
        quickPendingLineDragging = false;
        quickPendingLinePrice = Double.NaN;
        syncTradeLayerSnapshotWithQuickPendingLine();
        invalidate();
    }

    // 设置图表交易状态层快照，让外层页面统一输入真实线与草稿线。
    public void setTradeLayerSnapshot(@Nullable ChartTradeLayerSnapshot snapshot) {
        tradeLayerSnapshot = snapshot == null ? new ChartTradeLayerSnapshot(null, null) : snapshot;
        syncQuickPendingLineFromTradeLayerSnapshot();
        sanitizeHighlightedAnnotationGroup();
        invalidate();
    }

    // 让外层页面按整组交易线/标注统一设置当前聚焦对象。
    public void setHighlightedTradeGroup(@Nullable String groupId) {
        setHighlightedAnnotationGroup(groupId);
    }

    // 让外层页面在需要时主动回到普通显示态。
    public void clearHighlightedTradeGroup() {
        clearHighlightedAnnotationGroup();
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

    public void setHistoryTradeAnnotations(@Nullable List<PriceAnnotation> items) {
        historyTradeAnnotations.clear();
        if (items != null && !items.isEmpty()) {
            historyTradeAnnotations.addAll(items);
        }
        invalidate();
    }

    public void setAggregateCostAnnotation(@Nullable AggregateCostAnnotation annotation) {
        aggregateCostAnnotation = annotation;
        invalidate();
    }

    // 统一控制敏感叠加层显示，隐私关闭时保留 K 线主体但隐藏持仓相关标注。
    public void setOverlayVisibility(boolean showPositionAnnotations,
                                     boolean showPendingAnnotations,
                                     boolean showHistoryTradeAnnotations,
                                     boolean showAggregateCostAnnotation) {
        this.showPositionAnnotations = showPositionAnnotations;
        this.showPendingAnnotations = showPendingAnnotations;
        this.showHistoryTradeAnnotations = showHistoryTradeAnnotations;
        this.showAggregateCostAnnotation = showAggregateCostAnnotation;
        sanitizeHighlightedAnnotationGroup();
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

    // 当前视口是否仍贴着最新K线，用于决定自动刷新时是否继续跟随。
    public boolean isFollowingLatestViewport() {
        return KlineViewportHelper.shouldFollowLatestOnAutoRefresh(offsetCandles);
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
        resetViewportToDefault();
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
        long highlightedOpenTime = resolveHighlightedOpenTime();
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
            if (!restoreCrosshairByOpenTime(highlightedOpenTime)) {
                updateCrosshair(crosshairX, crosshairY);
            }
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
        float oldOffset = offsetCandles;
        long highlightedOpenTime = resolveHighlightedOpenTime();
        candles.addAll(0, olderCandles);
        computeIndicators();
        // 左补成功后继续停留在原来的视口位置，只把更早历史暴露到左侧，不要自动跳到新的最左边。
        offsetCandles = oldOffset;
        clampOffset();
        if (longPressing && !Float.isNaN(crosshairX) && !Float.isNaN(crosshairY)) {
            if (!restoreCrosshairByOpenTime(highlightedOpenTime)) {
                updateCrosshair(crosshairX, crosshairY);
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
        }
        if (handleQuickPendingLineTouch(event)) {
            return true;
        }
        if (handleTradeLineEditTouch(event)) {
            return true;
        }
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_DOWN) {
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
        dispatchVolumePaneLayout(false);
        if (candles.isEmpty()) {
            updateLatestCandleOutOfBounds(false, false);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无K线数据", width / 2f, height / 2f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
            return;
        }

        visibleEndFloat = candles.size() - 1f - offsetCandles;
        float latestX = xFor(candles.size() - 1, visibleEndFloat);
        updateLatestCandleOutOfBounds(
                latestX > priceRect.right + dp(0.2f),
                false
        );
        float visibleCount = Math.max(8f, effectivePlotWidth() / Math.max(1f, slot()));
        float rightBlankSlots = resolveRightBlankSlotsForOffset();
        int start = Math.max(0, (int) Math.floor(visibleEndFloat - visibleCount - 2f));
        int end = KlineViewportHelper.resolveVisibleRenderEndIndex(
                candles.size(),
                visibleEndFloat,
                rightBlankSlots
        );

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
        double scaledHalf = halfRange;
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
        drawTradeLayerSnapshot(canvas, visiblePriceMin, visiblePriceMax);
        drawHighlightedTradeAxisLabels(canvas, visiblePriceMin, visiblePriceMax);
        if (showHistoryTradeAnnotations) {
            drawOverlayAnnotations(canvas, historyTradeAnnotations);
        }
        if (showAggregateCostAnnotation) {
            drawAggregateCostAnnotation(canvas);
        }
        int infoIndex = resolveInfoIndex(end);
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
        if (showStochRsi && stochRect.height() > 0f) {
            drawStochRsi(canvas, start, end, infoIndex, drawStep);
        }
        if (showRsi && rsiRect.height() > 0f) {
            drawRsi(canvas, start, end, infoIndex, drawStep);
        }
        if (showKdj && kdjRect.height() > 0f) {
            drawKdj(canvas, start, end, infoIndex, drawStep);
        }
        if (longPressing) {
            drawCrosshair(canvas, highlightedIndex);
            drawCandlePopup(canvas, highlightedIndex);
            drawCrosshairLabels(canvas, highlightedIndex);
        }
        drawHighlightedAnnotationPopup(canvas);
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
            if (x < priceRect.left - slot()) {
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
        RectF plotRect = resolveIndicatorPlotRect(volRect);
        double maxVolume = resolveVolumeScaleMax(start, end);
        float halfBody = Math.max(dp(0.18f), candleWidth / 2f);
        int stride = Math.max(1, step);
        int saveCount = canvas.save();
        canvas.clipRect(plotRect);
        for (int i = start; i <= end; i += stride) {
            CandleEntry candle = candles.get(i);
            float x = xFor(i, visibleEndFloat);
            float top = (float) (plotRect.bottom - (candle.getVolume() / maxVolume) * plotRect.height());
            Paint paint = candle.getClose() >= candle.getOpen() ? upPaint : downPaint;
            canvas.drawRect(x - halfBody, top, x + halfBody, plotRect.bottom, paint);
        }
        if (showAvl) {
            drawSeries(canvas, avlLine, start, end, 0d, maxVolume, plotRect, avlPaint, stride);
        }
        canvas.restoreToCount(saveCount);
        canvas.drawLine(plotRect.left, plotRect.top, plotRect.right, plotRect.top, axisPaint);
        textPaint.setColor(secondaryTextColor);
        String volText = "VOL: " + formatVolumeNumber(candles.get(infoIndex).getVolume(), false);
        if (showAvl && infoIndex >= 0 && infoIndex < avlLine.length && !Double.isNaN(avlLine[infoIndex])) {
            volText += " AVL(" + avlPeriod + "):" + formatVolumeNumber(avlLine[infoIndex], false);
        }
        canvas.drawText(volText, volRect.left + dp(2f), resolvePaneTitleBaseline(volRect), textPaint);
        canvas.drawLine(plotRect.right, plotRect.top, plotRect.right, plotRect.bottom, axisPaint);
        canvas.drawText(formatVolumeNumber(maxVolume, true), volRect.right + dp(4f), resolveAxisTopBaseline(plotRect), textPaint);
        canvas.drawText("0", volRect.right + dp(4f), resolveAxisBottomBaseline(plotRect), textPaint);
        drawVolumeThreshold(canvas, maxVolume, plotRect);
    }

    // VOL 柱体、均量线、阈值线和右侧纵轴必须共用同一量纲上限，避免视觉上各用各的刻度。
    private double resolveVolumeScaleMax(int start, int end) {
        double maxVolume = 1d;
        for (int i = start; i <= end; i++) {
            maxVolume = Math.max(maxVolume, candles.get(i).getVolume());
            if (showAvl && i < avlLine.length && !Double.isNaN(avlLine[i])) {
                maxVolume = Math.max(maxVolume, avlLine[i]);
            }
        }
        if (volumeThresholdVisible && Double.isFinite(volumeThresholdValue) && volumeThresholdValue > 0d) {
            maxVolume = Math.max(maxVolume, volumeThresholdValue);
        }
        return maxVolume;
    }

    private void drawMacd(Canvas canvas, int start, int end, int infoIndex, int step) {
        double maxAbs = resolveMacdAbsMax(start, end);
        RectF plotRect = resolveIndicatorPlotRect(macdRect);
        float zeroY = yFor(0d, -maxAbs, maxAbs, plotRect);
        canvas.drawLine(macdRect.left, zeroY, macdRect.right, zeroY, axisPaint);
        float halfBody = Math.max(dp(0.18f), candleWidth / 2f);
        int stride = Math.max(1, step);
        int saveCount = canvas.save();
        canvas.clipRect(plotRect);
        for (int i = start; i <= end; i += stride) {
            if (i >= macdHist.length || Double.isNaN(macdHist[i])) {
                continue;
            }
            float x = xFor(i, visibleEndFloat);
            float y = yFor(macdHist[i], -maxAbs, maxAbs, plotRect);
            Paint paint = macdHist[i] >= 0d ? upPaint : downPaint;
            canvas.drawRect(x - halfBody, Math.min(zeroY, y), x + halfBody, Math.max(zeroY, y), paint);
        }
        drawSeries(canvas, macdDif, start, end, -maxAbs, maxAbs, plotRect, macdDifPaint, stride);
        drawSeries(canvas, macdDea, start, end, -maxAbs, maxAbs, plotRect, macdDeaPaint, stride);
        canvas.restoreToCount(saveCount);
        canvas.drawLine(macdRect.left, macdRect.top, macdRect.right, macdRect.top, gridPaint);
        canvas.drawLine(macdRect.left, macdRect.bottom, macdRect.right, macdRect.bottom, axisPaint);
        canvas.drawLine(macdRect.right, macdRect.top, macdRect.right, macdRect.bottom, axisPaint);
        textPaint.setColor(secondaryTextColor);
        canvas.drawText(formatAxisInt(maxAbs), macdRect.right + dp(4f), resolveAxisTopBaseline(macdRect), textPaint);
        canvas.drawText("0", macdRect.right + dp(4f), clampAxisBaseline(macdRect, zeroY + dp(3f)), textPaint);
        canvas.drawText(formatAxisInt(-maxAbs), macdRect.right + dp(4f), resolveAxisBottomBaseline(macdRect), textPaint);
        drawMacdInfo(canvas, infoIndex);
    }

    private void drawStochRsi(Canvas canvas, int start, int end, int infoIndex, int step) {
        int stride = Math.max(1, step);
        RectF plotRect = resolveIndicatorPlotRect(stochRect);
        drawSeries(canvas, stochK, start, end, 0d, 100d, plotRect, stochKPaint, stride);
        drawSeries(canvas, stochD, start, end, 0d, 100d, plotRect, stochDPaint, stride);
        canvas.drawLine(stochRect.left, yFor(20d, 0d, 100d, plotRect), stochRect.right, yFor(20d, 0d, 100d, plotRect), gridPaint);
        canvas.drawLine(stochRect.left, yFor(80d, 0d, 100d, plotRect), stochRect.right, yFor(80d, 0d, 100d, plotRect), gridPaint);
        canvas.drawLine(stochRect.left, stochRect.top, stochRect.right, stochRect.top, gridPaint);
        canvas.drawLine(stochRect.left, stochRect.bottom, stochRect.right, stochRect.bottom, axisPaint);
        canvas.drawLine(stochRect.right, stochRect.top, stochRect.right, stochRect.bottom, axisPaint);
        textPaint.setColor(secondaryTextColor);
        canvas.drawText("STOCHRSI K:" + formatDecimal(stochK[infoIndex]) + " D:" + formatDecimal(stochD[infoIndex]),
                stochRect.left + dp(2f),
                resolvePaneTitleBaseline(stochRect),
                textPaint);
        canvas.drawText("100", stochRect.right + dp(4f), resolveAxisTopBaseline(stochRect), textPaint);
        canvas.drawText("50", stochRect.right + dp(4f), clampAxisBaseline(stochRect, yFor(50d, 0d, 100d, plotRect) + dp(3f)), textPaint);
        canvas.drawText("0", stochRect.right + dp(4f), resolveAxisBottomBaseline(stochRect), textPaint);
    }

    private void drawRsi(Canvas canvas, int start, int end, int infoIndex, int step) {
        double lower = 0d;
        double upper = 100d;
        int stride = Math.max(1, step);
        RectF plotRect = resolveIndicatorPlotRect(rsiRect);
        drawSeries(canvas, rsiLine, start, end, lower, upper, plotRect, rsiPaint, stride);
        canvas.drawLine(rsiRect.left, yFor(30d, lower, upper, plotRect), rsiRect.right, yFor(30d, lower, upper, plotRect), gridPaint);
        canvas.drawLine(rsiRect.left, yFor(70d, lower, upper, plotRect), rsiRect.right, yFor(70d, lower, upper, plotRect), gridPaint);
        canvas.drawLine(rsiRect.left, rsiRect.top, rsiRect.right, rsiRect.top, gridPaint);
        canvas.drawLine(rsiRect.left, rsiRect.bottom, rsiRect.right, rsiRect.bottom, axisPaint);
        canvas.drawLine(rsiRect.right, rsiRect.top, rsiRect.right, rsiRect.bottom, axisPaint);
        textPaint.setColor(secondaryTextColor);
        canvas.drawText("RSI(" + rsiPeriod + "):" + formatDecimal(valueAt(rsiLine, infoIndex)),
                rsiRect.left + dp(2f),
                resolvePaneTitleBaseline(rsiRect),
                textPaint);
        canvas.drawText("100", rsiRect.right + dp(4f), resolveAxisTopBaseline(rsiRect), textPaint);
        canvas.drawText("50", rsiRect.right + dp(4f), clampAxisBaseline(rsiRect, yFor(50d, lower, upper, plotRect) + dp(3f)), textPaint);
        canvas.drawText("0", rsiRect.right + dp(4f), resolveAxisBottomBaseline(rsiRect), textPaint);
    }

    private void drawKdj(Canvas canvas, int start, int end, int infoIndex, int step) {
        double min = 0d;
        double max = 100d;
        for (int i = start; i <= end; i++) {
            min = Math.min(min, Math.min(valueOrFallback(kdjK, i, 50d),
                    Math.min(valueOrFallback(kdjD, i, 50d), valueOrFallback(kdjJ, i, 50d))));
            max = Math.max(max, Math.max(valueOrFallback(kdjK, i, 50d),
                    Math.max(valueOrFallback(kdjD, i, 50d), valueOrFallback(kdjJ, i, 50d))));
        }
        if (max <= min) {
            max = min + 1d;
        }
        double pad = (max - min) * 0.08d;
        double lower = min - pad;
        double upper = max + pad;
        int stride = Math.max(1, step);
        RectF plotRect = resolveIndicatorPlotRect(kdjRect);
        drawSeries(canvas, kdjK, start, end, lower, upper, plotRect, stochKPaint, stride);
        drawSeries(canvas, kdjD, start, end, lower, upper, plotRect, stochDPaint, stride);
        drawSeries(canvas, kdjJ, start, end, lower, upper, plotRect, kdjJPaint, stride);
        canvas.drawLine(kdjRect.left, kdjRect.top, kdjRect.right, kdjRect.top, gridPaint);
        canvas.drawLine(kdjRect.left, kdjRect.bottom, kdjRect.right, kdjRect.bottom, axisPaint);
        canvas.drawLine(kdjRect.right, kdjRect.top, kdjRect.right, kdjRect.bottom, axisPaint);
        textPaint.setColor(secondaryTextColor);
        String text = "KDJ(" + kdjPeriod + "," + kdjSmoothK + "," + kdjSmoothD + ")"
                + " K:" + formatDecimal(valueAt(kdjK, infoIndex))
                + " D:" + formatDecimal(valueAt(kdjD, infoIndex))
                + " J:" + formatDecimal(valueAt(kdjJ, infoIndex));
        canvas.drawText(text, kdjRect.left + dp(2f), resolvePaneTitleBaseline(kdjRect), textPaint);
        canvas.drawText(formatAxisInt(upper), kdjRect.right + dp(4f), resolveAxisTopBaseline(kdjRect), textPaint);
        canvas.drawText(formatAxisInt((upper + lower) * 0.5d),
                kdjRect.right + dp(4f),
                clampAxisBaseline(kdjRect, yFor((upper + lower) * 0.5d, lower, upper, plotRect) + dp(3f)),
                textPaint);
        canvas.drawText(formatAxisInt(lower), kdjRect.right + dp(4f), resolveAxisBottomBaseline(kdjRect), textPaint);
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
            canvas.drawText(label, priceRect.right + dp(4f), clampAxisBaseline(priceRect, y + dp(3f)), textPaint);
        }
        canvas.drawLine(priceRect.right, priceRect.top, priceRect.right, priceRect.bottom, axisPaint);
    }

    private void drawBollInfo(Canvas canvas, int index) {
        if (index < 0 || index >= candles.size()) {
            return;
        }
        float y = resolvePaneTitleBaseline(priceRect);
        textPaint.setColor(secondaryTextColor);
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
        textPaint.setColor(secondaryTextColor);
    }

    private void drawMacdInfo(Canvas canvas, int index) {
        if (index < 0 || index >= macdHist.length) {
            return;
        }
        float y = resolvePaneTitleBaseline(macdRect);
        textPaint.setColor(secondaryTextColor);
        String text = "MACD(" + macdFastPeriod + "," + macdSlowPeriod + "," + macdSignalPeriod + ")"
                + " DIF:" + formatDecimal(macdDif[index])
                + "  DEA:" + formatDecimal(macdDea[index])
                + "  MACD:" + formatDecimal(macdHist[index]);
        canvas.drawText(text, macdRect.left + dp(2f), y, textPaint);
        textPaint.setColor(secondaryTextColor);
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
        latestPriceGuidePaint.setColor(latestPriceGuideColor());
        latestPriceTagPaint.setColor(trendColor);
        canvas.drawLine(startX, y, priceRect.right, y, latestPriceGuidePaint);

        String priceText = FormatUtils.formatPriceNoDecimal(latest.getClose());
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

    // 在图表内渲染快捷挂单线，并在右侧显示对应价格。
    private void drawQuickPendingLine(Canvas canvas, double min, double max) {
        drawQuickPendingLine(canvas, quickPendingLinePrice, min, max);
    }

    // 按指定价格渲染图表内快捷挂单线，并在右侧显示对应价格。
    private void drawQuickPendingLine(Canvas canvas, double price, double min, double max) {
        if (!Double.isFinite(price) || price <= 0d) {
            return;
        }
        if (Double.compare(price, quickPendingLinePrice) == 0 && !quickPendingLineVisible) {
            return;
        }
        float y = yFor(price, min, max, priceRect);
        if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
            return;
        }
        canvas.drawLine(priceRect.left, y, priceRect.right, y, quickPendingLinePaint);

        String priceText = FormatUtils.formatPrice(price);
        float padX = dp(5f);
        float padY = dp(3f);
        float boxH = dp(14f);
        float boxW = quickPendingTagTextPaint.measureText(priceText) + padX * 2f;
        float left = priceRect.right + dp(3f);
        float top = clamp(y - boxH / 2f, priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        canvas.drawRoundRect(box, dp(3f), dp(3f), quickPendingTagPaint);
        canvas.drawText(priceText, box.left + padX, box.bottom - padY, quickPendingTagTextPaint);
    }

    // 绘制图表交易状态层，先真实线后草稿线。
    private void drawTradeLayerSnapshot(Canvas canvas, double min, double max) {
        for (ChartTradeLine line : tradeLayerSnapshot.getLiveLines()) {
            drawTradeLayerLine(canvas, line, min, max);
        }
        for (ChartTradeLine line : tradeLayerSnapshot.getDraftLines()) {
            drawTradeLayerLine(canvas, line, min, max);
        }
    }

    private void drawHighlightedTradeAxisLabels(@NonNull Canvas canvas, double min, double max) {
        if (highlightedAnnotationGroupId.isEmpty()) {
            return;
        }
        List<FocusedTradeAxisLabel> labels = collectHighlightedTradeAxisLabels(min, max);
        if (labels.isEmpty()) {
            return;
        }
        layoutHighlightedTradeAxisLabels(labels);
        for (FocusedTradeAxisLabel label : labels) {
            drawHighlightedTradeAxisLabel(canvas, label);
        }
    }

    @NonNull
    private List<FocusedTradeAxisLabel> collectHighlightedTradeAxisLabels(double min, double max) {
        List<FocusedTradeAxisLabel> labels = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        appendHighlightedTradeAxisLabelsFromLines(labels, seenKeys, tradeLayerSnapshot.getLiveLines(), min, max);
        appendHighlightedTradeAxisLabelsFromLines(labels, seenKeys, tradeLayerSnapshot.getDraftLines(), min, max);
        labels.sort((left, right) -> Float.compare(left.desiredCenterY, right.desiredCenterY));
        return labels;
    }

    private void appendHighlightedTradeAxisLabelsFromLines(@NonNull List<FocusedTradeAxisLabel> output,
                                                           @NonNull Set<String> seenKeys,
                                                           @Nullable List<ChartTradeLine> source,
                                                           double min,
                                                           double max) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (ChartTradeLine line : source) {
            if (line == null
                    || !shouldDrawTradeLayerLine(line)
                    || !highlightedAnnotationGroupId.equals(resolveTradeLayerGroupKey(line))
                    || !Double.isFinite(line.getPrice())
                    || line.getPrice() <= 0d) {
                continue;
            }
            float centerY = yFor(line.getPrice(), min, max, priceRect);
            if (Float.isNaN(centerY) || centerY < priceRect.top || centerY > priceRect.bottom) {
                continue;
            }
            String dedupeKey = line.getId() + "|" + Math.round(line.getPrice() * 100_000d);
            if (!seenKeys.add(dedupeKey)) {
                continue;
            }
            output.add(new FocusedTradeAxisLabel(
                    FormatUtils.formatPrice(line.getPrice()),
                    resolveTradeLayerLineColor(line),
                    centerY
            ));
        }
    }

    private void layoutHighlightedTradeAxisLabels(@NonNull List<FocusedTradeAxisLabel> labels) {
        float boxHeight = dp(14f);
        float gap = dp(2f);
        float minTop = priceRect.top;
        float maxTop = priceRect.bottom - boxHeight;
        for (FocusedTradeAxisLabel label : labels) {
            label.top = clamp(label.desiredCenterY - boxHeight / 2f, minTop, maxTop);
        }
        for (int index = 1; index < labels.size(); index++) {
            FocusedTradeAxisLabel previous = labels.get(index - 1);
            FocusedTradeAxisLabel current = labels.get(index);
            current.top = Math.max(current.top, previous.top + boxHeight + gap);
        }
        float nextMaxTop = maxTop;
        for (int index = labels.size() - 1; index >= 0; index--) {
            FocusedTradeAxisLabel label = labels.get(index);
            label.top = Math.min(label.top, nextMaxTop);
            nextMaxTop = label.top - boxHeight - gap;
        }
        for (int index = 1; index < labels.size(); index++) {
            FocusedTradeAxisLabel previous = labels.get(index - 1);
            FocusedTradeAxisLabel current = labels.get(index);
            current.top = Math.max(current.top, previous.top + boxHeight + gap);
        }
    }

    private void drawHighlightedTradeAxisLabel(@NonNull Canvas canvas,
                                               @NonNull FocusedTradeAxisLabel label) {
        if (Float.isNaN(label.top)) {
            return;
        }
        float padX = dp(5f);
        float padY = dp(3f);
        float boxHeight = dp(14f);
        float boxWidth = latestPriceTagTextPaint.measureText(label.text) + padX * 2f;
        float left = priceRect.right + dp(3f);
        RectF box = new RectF(left, label.top, left + boxWidth, label.top + boxHeight);
        latestPriceTagPaint.setColor(label.color);
        latestPriceTagTextPaint.setColor(resolveHighlightedTradeAxisTextColor(label.color));
        canvas.drawRoundRect(box, dp(3f), dp(3f), latestPriceTagPaint);
        canvas.drawText(label.text, box.left + padX, box.bottom - padY, latestPriceTagTextPaint);
    }

    // 按状态绘制单条交易线；当前先把草稿挂单线接入正式状态层。
    private void drawTradeLayerLine(Canvas canvas,
                                    @Nullable ChartTradeLine line,
                                    double min,
                                    double max) {
        if (line == null || !Double.isFinite(line.getPrice()) || line.getPrice() <= 0d) {
            return;
        }
        if (!shouldDrawTradeLayerLine(line)) {
            return;
        }
        float y = yFor(line.getPrice(), min, max, priceRect);
        if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
            return;
        }
        boolean hasGroupHighlight = !highlightedAnnotationGroupId.isEmpty();
        boolean selected = !hasGroupHighlight
                || highlightedAnnotationGroupId.equals(resolveTradeLayerGroupKey(line));
        int lineColor = resolveTradeLayerLineColor(line);
        if (hasGroupHighlight && !selected) {
            lineColor = applyAlpha(lineColor, 0.34f);
        }
        quickPendingLinePaint.setColor(lineColor);
        quickPendingLinePaint.setStrokeWidth(resolveTradeLayerLineStrokeWidth(line));
        quickPendingLinePaint.setPathEffect(resolveTradeLayerLinePathEffect(line));
        canvas.drawLine(priceRect.left, y, priceRect.right, y, quickPendingLinePaint);

        String leftLabel = line.getLabel() == null ? "" : line.getLabel().trim();
        String actionText = line.getActionText() == null ? "" : line.getActionText().trim();
        if (!selected
                && (line.getState() == ChartTradeLineState.LIVE_POSITION
                || line.getState() == ChartTradeLineState.LIVE_PENDING
                || line.getState() == ChartTradeLineState.LIVE_TP
                || line.getState() == ChartTradeLineState.LIVE_SL)) {
            actionText = "";
        }
        if (!leftLabel.isEmpty() || !actionText.isEmpty()) {
            drawTradeLayerLeftAffordances(canvas, actionText, leftLabel, y, lineColor, selected);
        }
        String centerLabel = line.getCenterLabel() == null ? "" : line.getCenterLabel().trim();
        if (!centerLabel.isEmpty()) {
            drawTradeLayerCenterLabel(canvas, centerLabel, y, lineColor, selected);
        }
    }

    private boolean shouldDrawTradeLayerLine(@NonNull ChartTradeLine line) {
        if (line.getState() == ChartTradeLineState.LIVE_POSITION
                || line.getState() == ChartTradeLineState.LIVE_TP
                || line.getState() == ChartTradeLineState.LIVE_SL) {
            return showPositionAnnotations;
        }
        if (line.getState() == ChartTradeLineState.LIVE_PENDING) {
            return showPendingAnnotations;
        }
        return true;
    }

    private boolean shouldUseDashedTradeLayerLine(@NonNull ChartTradeLine line) {
        return line.getState() == ChartTradeLineState.LIVE_POSITION
                || line.getState() == ChartTradeLineState.LIVE_PENDING
                || line.getState() == ChartTradeLineState.LIVE_TP
                || line.getState() == ChartTradeLineState.LIVE_SL
                || line.getState() == ChartTradeLineState.DRAFT_PENDING
                || line.getState() == ChartTradeLineState.DRAGGING
                || line.getState() == ChartTradeLineState.SUBMITTING
                || line.getState() == ChartTradeLineState.REJECTED_ROLLBACK;
    }

    private int resolveTradeLayerLineColor(@NonNull ChartTradeLine line) {
        UiPaletteManager.Palette palette = activePalette;
        if (line.isGhost()) {
            return palette == null ? secondaryTextColor : palette.textSecondary;
        }
        if (line.getState() == ChartTradeLineState.LIVE_PENDING) {
            return palette == null ? secondaryTextColor : palette.primary;
        }
        ChartTradeLineTone tone = line.getTone();
        if (tone == ChartTradeLineTone.POSITIVE) {
            return palette == null ? secondaryTextColor : palette.rise;
        }
        if (tone == ChartTradeLineTone.NEGATIVE) {
            return palette == null ? secondaryTextColor : palette.fall;
        }
        if (tone == ChartTradeLineTone.PRIMARY) {
            return palette == null ? secondaryTextColor : palette.primary;
        }
        return palette == null ? secondaryTextColor : palette.textSecondary;
    }

    private float resolveTradeLayerLineStrokeWidth(@NonNull ChartTradeLine line) {
        if (line.getState() == ChartTradeLineState.LIVE_TP
                || line.getState() == ChartTradeLineState.LIVE_SL) {
            return dp(0.72f);
        }
        return dp(0.95f);
    }

    @Nullable
    private DashPathEffect resolveTradeLayerLinePathEffect(@NonNull ChartTradeLine line) {
        if (!shouldUseDashedTradeLayerLine(line)) {
            return null;
        }
        if (line.getState() == ChartTradeLineState.LIVE_TP
                || line.getState() == ChartTradeLineState.LIVE_SL) {
            return new DashPathEffect(new float[]{dp(4f), dp(3f)}, 0f);
        }
        return new DashPathEffect(new float[]{dp(6f), dp(4f)}, 0f);
    }

    private void drawTradeLayerLeftAffordances(@NonNull Canvas canvas,
                                               @NonNull String actionText,
                                               @NonNull String labelText,
                                               float centerY,
                                               int lineColor,
                                               boolean selected) {
        float cursorLeft = priceRect.left + dp(3f);
        if (!actionText.isEmpty()) {
            RectF actionRect = resolveTradeLayerChipRect(actionText, centerY, cursorLeft);
            if (actionRect != null) {
                quickPendingTagPaint.setColor(resolveTradeLayerActionButtonColor(selected));
                quickPendingTagTextPaint.setColor(resolveTradeLayerActionTextColor(selected));
                canvas.drawRoundRect(actionRect, dp(3f), dp(3f), quickPendingTagPaint);
                canvas.drawText(actionText, actionRect.left + dp(5f), actionRect.bottom - dp(3f), quickPendingTagTextPaint);
                cursorLeft = actionRect.right + dp(4f);
            }
        }
        if (!labelText.isEmpty()) {
            RectF box = resolveTradeLayerChipRect(labelText, centerY, cursorLeft);
            if (box == null) {
                return;
            }
            quickPendingTagPaint.setColor(resolveTradeLayerTagColor(selected));
            quickPendingTagTextPaint.setColor(resolveTradeLayerTextColor(lineColor, labelText, selected));
            canvas.drawRoundRect(box, dp(3f), dp(3f), quickPendingTagPaint);
            canvas.drawText(labelText, box.left + dp(5f), box.bottom - dp(3f), quickPendingTagTextPaint);
        }
    }

    private void drawTradeLayerCenterLabel(@NonNull Canvas canvas,
                                           @NonNull String labelText,
                                           float centerY,
                                           int lineColor,
                                           boolean selected) {
        float padX = dp(5f);
        float padY = dp(3f);
        float boxH = dp(14f);
        float boxW = quickPendingTagTextPaint.measureText(labelText) + padX * 2f;
        float left = clamp(priceRect.centerX() - boxW / 2f, priceRect.left, priceRect.right - boxW);
        float top = clamp(centerY - boxH - dp(2f), priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        quickPendingTagPaint.setColor(resolveTradeLayerTagColor(selected));
        quickPendingTagTextPaint.setColor(resolveTradeLayerTextColor(lineColor, labelText, selected));
        canvas.drawRoundRect(box, dp(3f), dp(3f), quickPendingTagPaint);
        canvas.drawText(labelText, box.left + padX, box.bottom - padY, quickPendingTagTextPaint);
    }

    private int resolveTradeLayerTagColor(boolean selected) {
        UiPaletteManager.Palette palette = activePalette;
        int color = palette == null
                ? ColorUtils.setAlphaComponent(secondaryTextColor, 210)
                : ColorUtils.setAlphaComponent(palette.surfaceStart, 210);
        return selected ? color : applyAlpha(color, 0.42f);
    }

    private int resolveTradeLayerActionButtonColor(boolean selected) {
        UiPaletteManager.Palette palette = activePalette;
        int color = palette == null
                ? ColorUtils.setAlphaComponent(secondaryTextColor, 230)
                : ColorUtils.setAlphaComponent(palette.card, 236);
        return selected ? color : applyAlpha(color, 0.62f);
    }

    private int resolveTradeLayerActionTextColor(boolean selected) {
        UiPaletteManager.Palette palette = activePalette;
        int color = palette == null ? defaultAnnotationColor() : palette.textPrimary;
        return selected ? color : applyAlpha(color, 0.62f);
    }

    private int resolveHighlightedTradeAxisTextColor(int backgroundColor) {
        return ColorUtils.calculateLuminance(backgroundColor) >= 0.5d
                ? 0xFF101418
                : 0xFFF5F7FA;
    }

    private int resolveTradeLayerTextColor(int lineColor, @NonNull String labelText, boolean selected) {
        UiPaletteManager.Palette palette = activePalette;
        int textColor = lineColor;
        if (labelText.startsWith("挂单 买")) {
            textColor = palette == null ? lineColor : palette.rise;
        } else if (labelText.startsWith("挂单 卖")) {
            textColor = palette == null ? lineColor : palette.fall;
        }
        if (!selected) {
            return applyAlpha(textColor, 0.42f);
        }
        return textColor;
    }

    @Nullable
    private RectF resolveTradeLayerActionButtonRect(@Nullable ChartTradeLine line) {
        if (line == null || line.getActionText() == null || line.getActionText().trim().isEmpty()) {
            return null;
        }
        if (!Double.isFinite(line.getPrice()) || line.getPrice() <= 0d) {
            return null;
        }
        float y = yFor(line.getPrice(), visiblePriceMin, visiblePriceMax, priceRect);
        if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
            return null;
        }
        return resolveTradeLayerChipRect(line.getActionText().trim(), y, priceRect.left + dp(3f));
    }

    @Nullable
    private RectF resolveTradeLayerLeftLabelRect(@Nullable ChartTradeLine line) {
        if (line == null || line.getLabel() == null || line.getLabel().trim().isEmpty()) {
            return null;
        }
        if (!Double.isFinite(line.getPrice()) || line.getPrice() <= 0d) {
            return null;
        }
        float y = yFor(line.getPrice(), visiblePriceMin, visiblePriceMax, priceRect);
        if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
            return null;
        }
        float left = priceRect.left + dp(3f);
        RectF actionRect = resolveTradeLayerActionButtonRect(line);
        if (actionRect != null) {
            left = actionRect.right + dp(4f);
        }
        return resolveTradeLayerChipRect(line.getLabel().trim(), y, left);
    }

    @Nullable
    private RectF resolveTradeLayerCenterLabelRect(@Nullable ChartTradeLine line) {
        if (line == null || line.getCenterLabel() == null || line.getCenterLabel().trim().isEmpty()) {
            return null;
        }
        if (!Double.isFinite(line.getPrice()) || line.getPrice() <= 0d) {
            return null;
        }
        float centerY = yFor(line.getPrice(), visiblePriceMin, visiblePriceMax, priceRect);
        if (Float.isNaN(centerY) || centerY < priceRect.top || centerY > priceRect.bottom) {
            return null;
        }
        String labelText = line.getCenterLabel().trim();
        float padX = dp(5f);
        float boxH = dp(14f);
        float boxW = quickPendingTagTextPaint.measureText(labelText) + padX * 2f;
        float left = clamp(priceRect.centerX() - boxW / 2f, priceRect.left, priceRect.right - boxW);
        float top = clamp(centerY - boxH - dp(2f), priceRect.top, priceRect.bottom - boxH);
        return new RectF(left, top, left + boxW, top + boxH);
    }

    @Nullable
    private RectF resolveTradeLayerChipRect(@NonNull String text, float centerY, float left) {
        float padX = dp(5f);
        float boxH = dp(14f);
        float boxW = quickPendingTagTextPaint.measureText(text) + padX * 2f;
        float top = clamp(centerY - boxH / 2f, priceRect.top, priceRect.bottom - boxH);
        if (boxW <= 0f) {
            return null;
        }
        return new RectF(left, top, left + boxW, top + boxH);
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
            boolean historyTradePointOnly = isTradePointAnnotation(annotation);
            boolean tradeConnector = isTradeConnectorAnnotation(annotation);
            boolean pointOnly = historyTradePointOnly;
            float y = resolveAnnotationY(annotation);
            if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
                continue;
            }
            String groupKey = resolveAnnotationGroupKey(annotation);
            boolean selected = hasGroupHighlight && highlightedAnnotationGroupId.equals(groupKey);
            int baseColor = annotation.color == 0 ? defaultAnnotationColor() : annotation.color;
            int lineColor = hasGroupHighlight && !selected ? applyAlpha(baseColor, 0.34f) : baseColor;
            float lineWidth = hasGroupHighlight
                    ? (selected ? dp(1.2f) : dp(0.35f))
                    : dp(0.55f);
            float pointRadius = hasGroupHighlight
                    ? (selected ? dp(2.4f) : dp(1.1f))
                    : dp(1.7f);
            if (tradeConnector) {
                float startX = resolveHistoricalAnnotationX(annotation.anchorTimeMs);
                float endX = resolveHistoricalAnnotationX(annotation.secondaryAnchorTimeMs);
                float endY = resolveAnnotationSecondaryY(annotation);
                if (Float.isNaN(startX) || Float.isNaN(endX) || Float.isNaN(endY)
                        || !HistoricalTradeViewportHelper.isSegmentVisible(startX, endX, priceRect.left, priceRect.right)) {
                    continue;
                }
                overlayDashPaint.setColor(lineColor);
                overlayDashPaint.setStrokeWidth(hasGroupHighlight ? (selected ? dp(1.3f) : dp(0.6f)) : dp(0.8f));
                canvas.drawLine(startX, y, endX, endY, overlayDashPaint);
                float midX = (startX + endX) * 0.5f;
                float midY = (y + endY) * 0.5f;
                if (!annotation.label.isEmpty()
                        && HistoricalTradeViewportHelper.isPointVisible(midX, priceRect.left, priceRect.right)) {
                    drawPointOverlayLabel(canvas, annotation.label, midX, midY, lineColor, selected);
                }
                continue;
            }
            if (!pointOnly) {
                overlayDashPaint.setColor(lineColor);
                overlayDashPaint.setStrokeWidth(lineWidth);
                canvas.drawLine(priceRect.left, y, priceRect.right, y, overlayDashPaint);
            }

            float x;
            if (historyTradePointOnly) {
                x = resolveHistoricalAnnotationX(annotation.anchorTimeMs);
            } else {
                x = resolveAnnotationX(annotation.anchorTimeMs);
            }
            if (Float.isNaN(x)) {
                continue;
            }
            if (historyTradePointOnly
                    && !HistoricalTradeViewportHelper.isPointVisible(x, priceRect.left, priceRect.right)) {
                continue;
            }
            overlayPointPaint.setColor(lineColor);
            if (historyTradePointOnly) {
                float radius = selected ? dp(3.3f) : dp(2.9f);
                canvas.drawCircle(x, y, radius, overlayPointPaint);
            } else {
                canvas.drawCircle(x, y, pointRadius, overlayPointPaint);
            }

            if (historyTradePointOnly
                    && !annotation.label.isEmpty()
                    && HistoricalTradeViewportHelper.isPointVisible(x, priceRect.left, priceRect.right)) {
                drawPointOverlayLabel(canvas, annotation.label, x, y, lineColor, selected);
            } else if (!annotation.label.isEmpty()) {
                drawOverlayLabel(canvas, annotation.label, y, lineColor, selected);
            }
        }
        canvas.restoreToCount(saveCount);
    }

    // 历史成交点只绘制收盘点标记和短标签，不绘制整条横线。
    private boolean isHistoricalTradeAnnotation(@Nullable PriceAnnotation annotation) {
        return annotation != null && annotation.groupId.startsWith("tradehist|");
    }

    private boolean isTradePointAnnotation(@Nullable PriceAnnotation annotation) {
        if (!isHistoricalTradeAnnotation(annotation) || annotation == null) {
            return false;
        }
        return annotation.kind == ANNOTATION_KIND_HISTORY_ENTRY_BUY
                || annotation.kind == ANNOTATION_KIND_HISTORY_ENTRY_SELL
                || annotation.kind == ANNOTATION_KIND_HISTORY_EXIT;
    }

    private boolean isTradeConnectorAnnotation(@Nullable PriceAnnotation annotation) {
        return isHistoricalTradeAnnotation(annotation)
                && annotation != null
                && annotation.kind == ANNOTATION_KIND_HISTORY_CONNECTOR;
    }

    private float resolveAnnotationY(@Nullable PriceAnnotation annotation) {
        if (annotation == null) {
            return Float.NaN;
        }
        return yFor(annotation.price, visiblePriceMin, visiblePriceMax, priceRect);
    }

    private float resolveAnnotationSecondaryY(@Nullable PriceAnnotation annotation) {
        if (annotation == null || Double.isNaN(annotation.secondaryPrice) || annotation.secondaryPrice <= 0d) {
            return Float.NaN;
        }
        return yFor(annotation.secondaryPrice, visiblePriceMin, visiblePriceMax, priceRect);
    }

    private void drawOverlayLabel(Canvas canvas, String text, float anchorY, int textColor, boolean selected) {
        float padX = dp(4f);
        float padY = dp(2f);
        float boxH = dp(12f);
        float boxW = overlayLabelTextPaint.measureText(text) + padX * 2f;
        float left = priceRect.left + dp(2f);
        float top = clamp(anchorY - boxH - dp(2f), priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        int labelBgColor = overlayLabelBackgroundColor(selected);
        overlayLabelBgPaint.setColor(labelBgColor);
        canvas.drawRoundRect(box, dp(selected ? 3f : 2.5f), dp(selected ? 3f : 2.5f), overlayLabelBgPaint);
        overlayLabelTextPaint.setColor(textColor);
        canvas.drawText(text, box.left + padX, box.bottom - padY, overlayLabelTextPaint);
    }

    private void drawPointOverlayLabel(Canvas canvas,
                                       String text,
                                       float anchorX,
                                       float anchorY,
                                       int textColor,
                                       boolean selected) {
        float padX = dp(4f);
        float padY = dp(2f);
        float boxH = dp(12f);
        float boxW = overlayLabelTextPaint.measureText(text) + padX * 2f;
        boolean placeAbove = anchorY > priceRect.centerY();
        float top = placeAbove
                ? anchorY - boxH - dp(6f)
                : anchorY + dp(6f);
        top = clamp(top, priceRect.top, priceRect.bottom - boxH);
        float preferredLeft = anchorX + dp(5f);
        if (preferredLeft + boxW > priceRect.right) {
            preferredLeft = anchorX - boxW - dp(5f);
        }
        float left = clamp(preferredLeft, priceRect.left, priceRect.right - boxW);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        int labelBgColor = pointOverlayLabelBackgroundColor(selected);
        overlayLabelBgPaint.setColor(labelBgColor);
        canvas.drawRoundRect(box, dp(selected ? 3f : 2.5f), dp(selected ? 3f : 2.5f), overlayLabelBgPaint);
        overlayLabelTextPaint.setColor(textColor);
        canvas.drawText(text, box.left + padX, box.bottom - padY, overlayLabelTextPaint);
    }

    private void drawHighlightedAnnotationPopup(Canvas canvas) {
        PriceAnnotation selected = findHighlightedAnnotationWithDetails();
        if (selected == null || selected.detailLines.length == 0 || priceRect.isEmpty()) {
            return;
        }
        float anchorX;
        float anchorY;
        if (isTradeConnectorAnnotation(selected)) {
            float startX = resolveHistoricalAnnotationX(selected.anchorTimeMs);
            float startY = resolveAnnotationY(selected);
            float endX = resolveHistoricalAnnotationX(selected.secondaryAnchorTimeMs);
            float endY = resolveAnnotationSecondaryY(selected);
            if (Float.isNaN(startX) || Float.isNaN(startY) || Float.isNaN(endX) || Float.isNaN(endY)) {
                return;
            }
            anchorX = (startX + endX) * 0.5f;
            anchorY = (startY + endY) * 0.5f;
        } else {
            if (isTradePointAnnotation(selected)) {
                anchorX = resolveHistoricalAnnotationX(selected.anchorTimeMs);
            } else {
                anchorX = resolveAnnotationX(selected.anchorTimeMs);
            }
            anchorY = resolveAnnotationY(selected);
            if (Float.isNaN(anchorX) || Float.isNaN(anchorY)) {
                return;
            }
        }
        float padding = dp(6f);
        float lineStepPx = dp(11f);
        float maxWidth = 0f;
        for (String line : selected.detailLines) {
            maxWidth = Math.max(maxWidth, popupTextPaint.measureText(line));
        }
        float boxWidth = maxWidth + padding * 2f;
        float boxHeight = selected.detailLines.length * lineStepPx + padding * 2f;
        float left = anchorX + dp(8f);
        if (left + boxWidth > priceRect.right) {
            left = anchorX - boxWidth - dp(8f);
        }
        left = clamp(left, priceRect.left, priceRect.right - boxWidth);
        float top = anchorY - boxHeight - dp(10f);
        if (top < priceRect.top) {
            top = anchorY + dp(10f);
        }
        top = clamp(top, priceRect.top, priceRect.bottom - boxHeight);
        RectF box = new RectF(left, top, left + boxWidth, top + boxHeight);
        canvas.drawRoundRect(box, dp(6f), dp(6f), popupBgPaint);
        for (int i = 0; i < selected.detailLines.length; i++) {
            String line = selected.detailLines[i];
            Paint paint = line.contains("+$") ? popupPositiveTextPaint
                    : (line.contains("-$") ? popupNegativeTextPaint : popupTextPaint);
            canvas.drawText(line, box.left + padding, box.top + padding + lineStepPx * (i + 0.8f), paint);
        }
    }

    @Nullable
    private PriceAnnotation findHighlightedAnnotationWithDetails() {
        if (highlightedAnnotationGroupId.isEmpty()) {
            return null;
        }
        if (showPositionAnnotations) {
            PriceAnnotation selected = findHighlightedAnnotationWithDetails(positionAnnotations);
            if (selected != null) {
                return selected;
            }
        }
        if (showPendingAnnotations) {
            PriceAnnotation selected = findHighlightedAnnotationWithDetails(pendingAnnotations);
            if (selected != null) {
                return selected;
            }
        }
        if (showHistoryTradeAnnotations) {
            PriceAnnotation selected = findHighlightedAnnotationWithDetails(historyTradeAnnotations);
            if (selected != null) {
                return selected;
            }
        }
        return null;
    }

    @Nullable
    private PriceAnnotation findHighlightedAnnotationWithDetails(@Nullable List<PriceAnnotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            return null;
        }
        for (PriceAnnotation item : annotations) {
            if (item != null
                    && highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(item))
                    && item.detailLines.length > 0) {
                return item;
            }
        }
        return null;
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
                ? "$" + FormatUtils.formatPrice(aggregateCostAnnotation.price)
                : aggregateCostAnnotation.priceLabel;
        float padX = dp(5f);
        float padY = dp(3f);
        float boxH = dp(14f);
        float boxW = aggregateCostHintTextPaint.measureText(text) + padX * 2f;
        float left = clamp(priceRect.centerX() - boxW / 2f, priceRect.left, priceRect.right - boxW);
        float top = clamp(y - boxH - dp(2f), priceRect.top, priceRect.bottom - boxH);
        RectF box = new RectF(left, top, left + boxW, top + boxH);
        aggregateCostTagPaint.setColor(ColorUtils.setAlphaComponent(requirePalette().surfaceStart, 210));
        canvas.drawRoundRect(box, dp(3f), dp(3f), aggregateCostTagPaint);
        canvas.drawText(text, box.left + padX, box.bottom - padY, aggregateCostHintTextPaint);
    }

    private void drawVolumeThreshold(Canvas canvas, double maxVolume, @NonNull RectF plotRect) {
        if (!volumeThresholdVisible || Double.isNaN(volumeThresholdValue) || volumeThresholdValue <= 0d || volRect.isEmpty()) {
            return;
        }
        double denominator = Math.max(maxVolume, 1d);
        float y = (float) (plotRect.bottom - (volumeThresholdValue / denominator) * plotRect.height());
        y = clamp(y, plotRect.top, plotRect.bottom);
        canvas.drawLine(plotRect.left, y, plotRect.right, y, volumeThresholdPaint);
        String thresholdText = formatVolumeNumber(volumeThresholdValue, true);
        canvas.drawText(thresholdText, volRect.right + dp(4f), clampAxisBaseline(plotRect, y), textPaint);
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
        List<KlinePopupDataHelper.Row> rows = KlinePopupDataHelper.buildRows(
                candle,
                showBoll,
                bollPeriod,
                bollStdMultiplier,
                valueAt(bollUp, index),
                valueAt(bollMid, index),
                valueAt(bollDn, index),
                showMa,
                maPeriod,
                valueAt(maLine, index),
                showEma,
                emaPeriod,
                valueAt(emaLine, index),
                showSra,
                sraPeriod,
                valueAt(sraLine, index)
        );

        float pad = dp(6f);
        float lineH = dp(11f);
        float maxLabelWidth = 0f;
        float maxValueWidth = 0f;
        for (KlinePopupDataHelper.Row row : rows) {
            maxLabelWidth = Math.max(maxLabelWidth, popupTextPaint.measureText(row.label));
            maxValueWidth = Math.max(maxValueWidth, popupTextPaint.measureText(row.value));
        }
        float columnGap = dp(10f);
        float w = maxLabelWidth + maxValueWidth + columnGap + pad * 2f;
        float h = lineH * rows.size() + pad * 2f;
        float left = crosshairX + dp(8f);
        if (left + w > priceRect.right) left = crosshairX - w - dp(8f);
        left = clamp(left, priceRect.left, priceRect.right - w);
        float top = clamp(priceRect.top + dp(14f), priceRect.top, priceRect.bottom - h);
        RectF box = new RectF(left, top, left + w, top + h);
        canvas.drawRoundRect(box, dp(6f), dp(6f), popupBgPaint);
        for (int i = 0; i < rows.size(); i++) {
            KlinePopupDataHelper.Row row = rows.get(i);
            float baseline = box.top + pad + lineH * (i + 0.8f);
            float labelX = box.left + pad;
            String value = row.value;
            float valueWidth = popupTextPaint.measureText(value);
            float valueX = box.right - pad - valueWidth;
            canvas.drawText(row.label, labelX, baseline, popupTextPaint);
            canvas.drawText(value, valueX, baseline, resolvePopupValuePaint(i, hasCandle ? candle.getClose() - candle.getOpen() : 0d));
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
        float left = getResources().getDimension(R.dimen.kline_plot_left_inset);
        float right = width - getResources().getDimension(R.dimen.kline_price_axis_reserved_width);
        float top = dp(8f);
        float bottom = height - dp(18f);
        KlinePaneLayoutHelper.PaneLayout layout = KlinePaneLayoutHelper.compute(
                top,
                bottom,
                showVolume,
                showMacd,
                showStochRsi,
                showRsi,
                showKdj
        );

        priceRect.set(left, layout.price.top, right, layout.price.bottom);
        if (showVolume) {
            volRect.set(left, layout.volume.top, right, layout.volume.bottom);
        } else {
            volRect.setEmpty();
        }
        if (showMacd) {
            macdRect.set(left, layout.macd.top, right, layout.macd.bottom);
        } else {
            macdRect.setEmpty();
        }
        if (showStochRsi) {
            stochRect.set(left, layout.stoch.top, right, layout.stoch.bottom);
        } else {
            stochRect.setEmpty();
        }
        if (showRsi) {
            rsiRect.set(left, layout.rsi.top, right, layout.rsi.bottom);
        } else {
            rsiRect.setEmpty();
        }
        if (showKdj) {
            kdjRect.set(left, layout.kdj.top, right, layout.kdj.bottom);
        } else {
            kdjRect.setEmpty();
        }
    }

    // 为指标标题与边界标签预留独立的绘图区，避免折线/柱体压到文字上。
    private RectF resolveIndicatorPlotRect(RectF paneRect) {
        float topInset = SpacingTokenResolver.dpFloat(getContext(), KlinePaneTextLayoutHelper.resolveIndicatorPlotTopInsetRes());
        float bottomInset = SpacingTokenResolver.dpFloat(getContext(), KlinePaneTextLayoutHelper.resolveIndicatorPlotBottomInsetRes());
        float top = Math.min(paneRect.bottom, paneRect.top + topInset);
        float bottom = Math.max(top + dp(8f), paneRect.bottom - bottomInset);
        return new RectF(paneRect.left, top, paneRect.right, bottom);
    }

    // 统一主图和附图左上角标题基线。
    private float resolvePaneTitleBaseline(RectF paneRect) {
        return paneRect.top + SpacingTokenResolver.dpFloat(getContext(), KlinePaneTextLayoutHelper.resolvePaneTitleBaselineOffsetRes());
    }

    // 统一右侧纵坐标顶部文字基线，避免贴到共享边界上。
    private float resolveAxisTopBaseline(RectF paneRect) {
        return paneRect.top + SpacingTokenResolver.dpFloat(getContext(), KlinePaneTextLayoutHelper.resolveAxisTopBaselineOffsetRes());
    }

    // 统一右侧纵坐标底部文字基线，避免与下一张图顶部文字重叠。
    private float resolveAxisBottomBaseline(RectF paneRect) {
        return paneRect.bottom - SpacingTokenResolver.dpFloat(getContext(), KlinePaneTextLayoutHelper.resolveAxisBottomInsetRes());
    }

    // 把中间标签基线限制在当前图内，避免多图共享边界时文字互相压住。
    private float clampAxisBaseline(RectF paneRect, float preferredBaseline) {
        return Math.max(resolveAxisTopBaseline(paneRect),
                Math.min(resolveAxisBottomBaseline(paneRect), preferredBaseline));
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
            // 币安图表口径：MACD 柱值直接使用 DIF-DEA，不再乘 2。
            macdHist[i] = (dif - dea);
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
        float touchX = clamp(x, priceRect.left, priceRect.right);
        crosshairY = clamp(y, priceRect.top, priceRect.bottom);
        float rawIndex = xToRawIndex(touchX, visibleEndFloat);
        crosshairOnCandle = rawIndex >= -0.05f && rawIndex <= candles.size() - 1f + resolveRightBlankSlotsForOffset();
        highlightedIndex = Math.round(rawIndex);
        if (highlightedIndex < 0) highlightedIndex = 0;
        if (highlightedIndex >= candles.size()) highlightedIndex = candles.size() - 1;
        crosshairX = clamp(xFor(highlightedIndex, visibleEndFloat), priceRect.left, priceRect.right);
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

    // 仅在触点靠近挂单线时接管事件，让图表其余区域仍可正常横向拖动与缩放。
    private boolean handleQuickPendingLineTouch(@Nullable MotionEvent event) {
        if (event == null || !quickPendingLineVisible || !Double.isFinite(quickPendingLinePrice) || quickPendingLinePrice <= 0d) {
            return false;
        }
        if (!ensureLayoutForMath() || priceRect.isEmpty()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (shouldSuppressTradeLayerTouchForExistingHighlight(event.getX(), event.getY())) {
                quickPendingLineTouchExclusive = false;
                return false;
            }
            ChartTradeLine matchedDraftLine = findQuickPendingDraftLineAtTouch(event.getX(), event.getY());
            if (!isQuickPendingDraftLine(matchedDraftLine)) {
                quickPendingLineTouchExclusive = false;
                return false;
            }
            quickPendingLineTouchExclusive = true;
            quickPendingLineDragging = true;
            quickPendingLinePointerId = event.getPointerId(event.getActionIndex());
            quickPendingLineLastTouchY = event.getY(event.getActionIndex());
            setHighlightedAnnotationGroup(resolveTradeLayerGroupKey(matchedDraftLine));
            clearCrosshair();
            requestDisallow(true);
            return true;
        }
        if (!quickPendingLineDragging) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            int activePointerIndex = event.findPointerIndex(quickPendingLinePointerId);
            if (activePointerIndex < 0) {
                resetQuickPendingLineDragging();
                return true;
            }
            updateQuickPendingLinePriceByDelta(event.getY(activePointerIndex));
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            requestDisallow(true);
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            if (event.getPointerId(event.getActionIndex()) == quickPendingLinePointerId) {
                resetQuickPendingLineDragging();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            resetQuickPendingLineDragging();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            resetQuickPendingLineDragging();
            return true;
        }
        return true;
    }

    private boolean handleTradeLineEditTouch(@Nullable MotionEvent event) {
        if (event == null || tradeLayerSnapshot == null) {
            return false;
        }
        if (shouldBlockTradeLayerInteractionForQuickPending(event)) {
            return false;
        }
        if (!ensureLayoutForMath() || priceRect.isEmpty()) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (shouldSuppressTradeLayerTouchForExistingHighlight(event.getX(), event.getY())) {
                return false;
            }
            ChartTradeLine actionLine = findTradeLineActionAtTouch(event.getX(), event.getY());
            if (actionLine != null) {
                pressedTradeLineAction = actionLine;
                setHighlightedAnnotationGroup(resolveTradeLayerGroupKey(actionLine));
                clearCrosshair();
                requestDisallow(true);
                return true;
            }
            ChartTradeLine matchedLine = findEditableTradeLine(event.getX(), event.getY(), resolveTradeLineEditTouchThresholdPx());
            if (matchedLine == null) {
                return false;
            }
            tradeLineEditDragging = true;
            tradeLineEditPointerId = event.getPointerId(event.getActionIndex());
            tradeLineEditLastTouchY = event.getY(event.getActionIndex());
            tradeLineEditCurrentPrice = matchedLine.getPrice();
            activeTradeLineEdit = matchedLine;
            setHighlightedAnnotationGroup(resolveTradeLayerGroupKey(matchedLine));
            clearCrosshair();
            requestDisallow(true);
            if (onTradeLineEditListener != null) {
                onTradeLineEditListener.onTradeLineDragStart(matchedLine);
            }
            return true;
        }
        if (pressedTradeLineAction != null) {
            if (action == MotionEvent.ACTION_UP) {
                ChartTradeLine actionLine = pressedTradeLineAction;
                pressedTradeLineAction = null;
                requestDisallow(false);
                if (onTradeLineEditListener != null) {
                    onTradeLineEditListener.onTradeLineActionClick(actionLine);
                }
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (action == MotionEvent.ACTION_MOVE
                        && (Math.abs(event.getX() - downX) <= touchSlop)
                        && (Math.abs(event.getY() - downY) <= touchSlop)) {
                    return true;
                }
                pressedTradeLineAction = null;
                requestDisallow(false);
                return true;
            }
        }
        if (!tradeLineEditDragging || activeTradeLineEdit == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            int activePointerIndex = event.findPointerIndex(tradeLineEditPointerId);
            if (activePointerIndex < 0) {
                resetTradeLineEditingTouch();
                return true;
            }
            double updatedPrice = updateTradeLineEditPriceByDelta(event.getY(activePointerIndex));
            if (updatedPrice > 0d && onTradeLineEditListener != null) {
                onTradeLineEditListener.onTradeLineDragUpdate(activeTradeLineEdit, updatedPrice);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            requestDisallow(true);
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            if (event.getPointerId(event.getActionIndex()) == tradeLineEditPointerId) {
                resetTradeLineEditingTouch();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            resetTradeLineEditingTouch();
            return true;
        }
        return true;
    }

    // 快捷挂单拖拽需要比普通点击更宽的命中热区，避免和图表平移/缩放手势竞争。
    private float resolveQuickPendingLineTouchThresholdPx() {
        return Math.max(dp(56f), touchSlop * 4f);
    }

    @Nullable
    private ChartTradeLine findQuickPendingDraftLineAtTouch(float x, float y) {
        return findNearestTradeLayerLineInList(
                tradeLayerSnapshot.getDraftLines(),
                x,
                y,
                resolveQuickPendingLineTouchThresholdPx(),
                Float.MAX_VALUE
        );
    }

    private boolean shouldExitQuickPendingModeByTap(float x, float y) {
        return quickPendingLineVisible && !isQuickPendingDraftLine(findQuickPendingDraftLineAtTouch(x, y));
    }

    private boolean shouldBlockTradeLayerInteractionForQuickPending(@Nullable MotionEvent event) {
        if (event == null || !quickPendingLineVisible) {
            return false;
        }
        if (quickPendingLineDragging || quickPendingLineTouchExclusive) {
            return false;
        }
        return !isQuickPendingDraftLine(findQuickPendingDraftLineAtTouch(event.getX(), event.getY()));
    }

    // 只有挂单草稿线才允许进入拖拽态，正式持仓线与历史挂单线继续保持只读交互。
    private boolean isQuickPendingDraftLine(@Nullable ChartTradeLine line) {
        if (line == null) {
            return false;
        }
        return line.getState() == ChartTradeLineState.DRAFT_PENDING
                || line.getState() == ChartTradeLineState.DRAGGING;
    }

    // 快捷挂单草稿线进入专用拖拽态后，统一在这里释放手势状态。
    private void resetQuickPendingLineDragging() {
        quickPendingLineDragging = false;
        quickPendingLinePointerId = -1;
        quickPendingLineLastTouchY = Float.NaN;
        quickPendingLineTouchExclusive = false;
        requestDisallow(false);
    }

    private void resetTradeLineEditingTouch() {
        tradeLineEditDragging = false;
        tradeLineEditPointerId = -1;
        tradeLineEditLastTouchY = Float.NaN;
        tradeLineEditCurrentPrice = Double.NaN;
        activeTradeLineEdit = null;
        pressedTradeLineAction = null;
        requestDisallow(false);
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

    // 刷新前记住当前十字焦点所对应的K线时间，供数据更新后优先按同一根K线恢复。
    private long resolveHighlightedOpenTime() {
        if (!longPressing || highlightedIndex < 0 || highlightedIndex >= candles.size()) {
            return Long.MIN_VALUE;
        }
        CandleEntry highlighted = candles.get(highlightedIndex);
        return highlighted == null ? Long.MIN_VALUE : highlighted.getOpenTime();
    }

    // 数据刷新后优先按原焦点的openTime恢复高亮，避免按旧屏幕坐标重算时来回跳到相邻K线。
    private boolean restoreCrosshairByOpenTime(long highlightedOpenTime) {
        if (highlightedOpenTime == Long.MIN_VALUE || candles.isEmpty()) {
            return false;
        }
        int restoredIndex = indexByOpenTime(highlightedOpenTime);
        if (restoredIndex < 0) {
            return false;
        }
        highlightedIndex = restoredIndex;
        crosshairOnCandle = true;
        crosshairY = clamp(crosshairY, priceRect.top, priceRect.bottom);
        crosshairX = clamp(xFor(highlightedIndex, visibleEndFloat), priceRect.left, priceRect.right);
        crosshairPrice = valueForY(crosshairY, visiblePriceMin, visiblePriceMax, priceRect);
        notifyCrosshair();
        return true;
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
        if (!kdjRect.isEmpty()) return kdjRect.bottom;
        if (!rsiRect.isEmpty()) return rsiRect.bottom;
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

    float getPriceInfoTextSizePx() {
        return textPaint.getTextSize();
    }

    int getPricePaneTitleBaselineOffsetPx() {
        return Math.round(SpacingTokenResolver.dpFloat(getContext(), KlinePaneTextLayoutHelper.resolvePaneTitleBaselineOffsetRes()));
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

    private void dispatchVolumePaneLayout(boolean force) {
        if (onVolumePaneLayoutListener == null) {
            return;
        }
        int left;
        int top;
        int right;
        int bottom;
        if (volRect.isEmpty()) {
            left = 0;
            top = 0;
            right = 0;
            bottom = 0;
        } else {
            left = Math.round(volRect.left);
            top = Math.round(volRect.top);
            right = Math.round(volRect.right);
            bottom = Math.round(volRect.bottom);
        }
        if (!force
                && left == lastVolumePaneLeft
                && top == lastVolumePaneTop
                && right == lastVolumePaneRight
                && bottom == lastVolumePaneBottom) {
            return;
        }
        lastVolumePaneLeft = left;
        lastVolumePaneTop = top;
        lastVolumePaneRight = right;
        lastVolumePaneBottom = bottom;
        onVolumePaneLayoutListener.onVolumePaneLayoutChanged(left, top, right, bottom);
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
            return offsetCandles > resolveRightBlankSlotsForOffset() + 0.5f;
        }
        float endFloat = candles.size() - 1f - offsetCandles;
        float latestX = xFor(candles.size() - 1, endFloat);
        return latestX > priceRect.right + dp(0.2f);
    }

    private float plotRight() {
        return priceRect.right - rightBlankWidth();
    }

    private float effectivePlotWidth() {
        return Math.max(dp(20f), plotRight() - priceRect.left);
    }

    private float maxOffset() {
        return KlineViewportHelper.resolveMaxOffset(candles.size(), priceRect.width(), slot());
    }

    private float effectivePlotWidthForBlankSlots(float blankSlots) {
        float right = priceRect.right - Math.max(0f, blankSlots) * slot();
        return Math.max(dp(20f), right - priceRect.left);
    }

    private float resolveRightBlankSlotsForOffset() {
        return rightBlankWidth() / Math.max(1f, slot());
    }

    private float rightBlankWidth() {
        return KlineViewportHelper.resolveDynamicRightBlankWidth(
                priceRect.width(),
                RIGHT_BLANK_RATIO,
                offsetCandles,
                slot()
        );
    }

    // 视口偏移一旦变化就立即刷新可见末端位置，避免高频刷新时十字线继续使用上一帧的旧视口值。
    private void syncVisibleEndFloat() {
        visibleEndFloat = candles.isEmpty() ? 0f : (candles.size() - 1f - offsetCandles);
    }

    private void clampOffset() {
        offsetCandles = clamp(offsetCandles, 0f, maxOffset());
        syncVisibleEndFloat();
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

    // 历史成交点允许把窗口外时间继续外推到视图外侧，避免被压到左右边界。
    private float resolveHistoricalAnnotationX(long anchorTimeMs) {
        if (anchorTimeMs <= 0L || candles.isEmpty()) {
            return Float.NaN;
        }
        float rawIndex = rawIndexByOpenTimeAllowOverflow(anchorTimeMs);
        return xFor(rawIndex, visibleEndFloat);
    }

    private long estimateCandleIntervalMs() {
        if (candles == null || candles.size() < 2) {
            return 60_000L;
        }
        long delta = candles.get(1).getOpenTime() - candles.get(0).getOpenTime();
        return Math.max(1L, delta);
    }

    private int floorIndexByOpenTime(long openTime) {
        if (candles.isEmpty()) {
            return -1;
        }
        int low = 0;
        int high = candles.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long value = candles.get(mid).getOpenTime();
            if (value <= openTime) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return Math.max(0, Math.min(candles.size() - 1, high));
    }

    // 历史成交时间允许超出当前已加载窗口，并按固定周期在两端继续外推。
    private float rawIndexByOpenTimeAllowOverflow(long openTime) {
        if (candles.isEmpty()) {
            return 0f;
        }
        int last = candles.size() - 1;
        long firstTime = candles.get(0).getOpenTime();
        long intervalMs = estimateCandleIntervalMs();
        long lastVisibleExclusiveTime = resolveHistoricalTradeLastVisibleTime();
        float overflowIndex = HistoricalTradeViewportHelper.resolveOverflowRawIndexFromWindow(
                openTime,
                firstTime,
                lastVisibleExclusiveTime,
                last,
                intervalMs
        );
        if (!Float.isNaN(overflowIndex)) {
            return overflowIndex;
        }
        int bucketIndex = floorIndexByOpenTime(openTime);
        if (bucketIndex < 0 || bucketIndex >= candles.size()) {
            return Float.NaN;
        }
        CandleEntry bucket = candles.get(bucketIndex);
        long bucketOpenTime = bucket == null ? 0L : bucket.getOpenTime();
        long bucketCloseExclusiveTime = resolveHistoricalBucketCloseExclusiveTime(bucketIndex, lastVisibleExclusiveTime);
        return HistoricalTradeViewportHelper.resolveTimeInsideBucketRawIndex(
                bucketIndex,
                bucketOpenTime,
                bucketCloseExclusiveTime,
                openTime
        );
    }

    // 统一取历史成交允许映射到的最后一个时间边界，末端使用 K 线时间桶的右边界。
    private long resolveHistoricalTradeLastVisibleTime() {
        if (candles.isEmpty()) {
            return 0L;
        }
        int lastIndex = candles.size() - 1;
        CandleEntry lastCandle = candles.get(lastIndex);
        long lastOpenTime = lastCandle == null ? 0L : lastCandle.getOpenTime();
        long lastCloseTime = lastCandle == null ? 0L : lastCandle.getCloseTime();
        long intervalMs = estimateCandleIntervalMs();
        long fallbackExclusiveTime = lastOpenTime + intervalMs;
        long candleCloseExclusiveTime = lastCloseTime > 0L ? lastCloseTime + 1L : 0L;
        return Math.max(fallbackExclusiveTime, candleCloseExclusiveTime);
    }

    // 每根 K 线的时间槽位右边界优先取下一根开盘时间，最后一根取最后可见时间边界。
    private long resolveHistoricalBucketCloseExclusiveTime(int bucketIndex, long lastVisibleExclusiveTime) {
        if (bucketIndex < 0 || bucketIndex >= candles.size()) {
            return 0L;
        }
        if (bucketIndex < candles.size() - 1) {
            CandleEntry nextCandle = candles.get(bucketIndex + 1);
            long nextOpenTime = nextCandle == null ? 0L : nextCandle.getOpenTime();
            if (nextOpenTime > 0L) {
                return nextOpenTime;
            }
        }
        return lastVisibleExclusiveTime;
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

    private boolean isTouchInsideHighlightedGroup(float x, float y) {
        return isTouchInsideHighlightedTradeGroup(x, y)
                || isTouchInsideHighlightedAnnotationGroup(x, y);
    }

    private boolean shouldSuppressTradeLayerTouchForExistingHighlight(float x, float y) {
        return !highlightedAnnotationGroupId.isEmpty() && !isTouchInsideHighlightedGroup(x, y);
    }

    private boolean isTouchInsideHighlightedTradeGroup(float x, float y) {
        if (highlightedAnnotationGroupId.isEmpty()) {
            return false;
        }
        return isTouchInsideHighlightedTradeGroupInList(tradeLayerSnapshot.getLiveLines(), x, y)
                || isTouchInsideHighlightedTradeGroupInList(tradeLayerSnapshot.getDraftLines(), x, y);
    }

    private boolean isTouchInsideHighlightedTradeGroupInList(@Nullable List<ChartTradeLine> source,
                                                             float x,
                                                             float y) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        for (ChartTradeLine line : source) {
            if (line == null
                    || !highlightedAnnotationGroupId.equals(resolveTradeLayerGroupKey(line))
                    || !shouldDrawTradeLayerLine(line)) {
                continue;
            }
            RectF actionRect = resolveTradeLayerActionButtonRect(line);
            if (actionRect != null && actionRect.contains(x, y)) {
                return true;
            }
            RectF labelRect = resolveTradeLayerLeftLabelRect(line);
            if (labelRect != null && labelRect.contains(x, y)) {
                return true;
            }
            RectF centerRect = resolveTradeLayerCenterLabelRect(line);
            if (centerRect != null && centerRect.contains(x, y)) {
                return true;
            }
            float distance = resolveTradeLayerTouchDistance(line, x, y);
            if (!Float.isNaN(distance) && distance <= dp(14f)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTouchInsideHighlightedAnnotationGroup(float x, float y) {
        if (highlightedAnnotationGroupId.isEmpty()) {
            return false;
        }
        return isTouchInsideHighlightedAnnotationGroupInList(positionAnnotations, x, y)
                || isTouchInsideHighlightedAnnotationGroupInList(pendingAnnotations, x, y)
                || isTouchInsideHighlightedAnnotationGroupInList(historyTradeAnnotations, x, y);
    }

    private boolean isTouchInsideHighlightedAnnotationGroupInList(@Nullable List<PriceAnnotation> source,
                                                                  float x,
                                                                  float y) {
        if (source == null || source.isEmpty()) {
            return false;
        }
        for (PriceAnnotation annotation : source) {
            if (annotation == null
                    || !highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(annotation))) {
                continue;
            }
            float distance = resolveAnnotationTouchDistance(annotation, x, y);
            if (!Float.isNaN(distance) && distance <= dp(14f)) {
                return true;
            }
        }
        return false;
    }

    private boolean selectAnnotationGroupByTouch(float x, float y) {
        if (priceRect.isEmpty() || x < priceRect.left || x > priceRect.right || y < priceRect.top || y > priceRect.bottom) {
            return false;
        }
        ChartTradeLine matchedTradeLine = findNearestTradeLayerLine(x, y, dp(14f));
        if (matchedTradeLine != null) {
            return setHighlightedAnnotationGroup(resolveTradeLayerGroupKey(matchedTradeLine));
        }
        PriceAnnotation matched = findNearestAnnotation(x, y, dp(14f));
        if (matched == null) {
            return false;
        }
        return setHighlightedAnnotationGroup(resolveAnnotationGroupKey(matched));
    }

    private boolean setHighlightedAnnotationGroup(@Nullable String groupKey) {
        String safeGroupKey = groupKey == null ? "" : groupKey.trim();
        if (safeGroupKey.isEmpty()) {
            return false;
        }
        if (safeGroupKey.equals(highlightedAnnotationGroupId)) {
            return true;
        }
        highlightedAnnotationGroupId = safeGroupKey;
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
        if (containsTradeLayerGroup(highlightedAnnotationGroupId)) {
            return;
        }
        if (showPositionAnnotations) {
            for (PriceAnnotation item : positionAnnotations) {
                if (highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(item))) {
                    return;
                }
            }
        }
        if (showPendingAnnotations) {
            for (PriceAnnotation item : pendingAnnotations) {
                if (highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(item))) {
                    return;
                }
            }
        }
        if (showHistoryTradeAnnotations) {
            for (PriceAnnotation item : historyTradeAnnotations) {
                if (highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(item))) {
                    return;
                }
            }
        }
        highlightedAnnotationGroupId = "";
    }

    private boolean containsTradeLayerGroup(@NonNull String groupKey) {
        for (ChartTradeLine line : tradeLayerSnapshot.getLiveLines()) {
            if (shouldDrawTradeLayerLine(line)
                    && groupKey.equals(resolveTradeLayerGroupKey(line))) {
                return true;
            }
        }
        for (ChartTradeLine line : tradeLayerSnapshot.getDraftLines()) {
            if (shouldDrawTradeLayerLine(line)
                    && groupKey.equals(resolveTradeLayerGroupKey(line))) {
                return true;
            }
        }
        return false;
    }

    private float resolveTradeLineEditTouchThresholdPx() {
        return Math.max(dp(44f), touchSlop * 3f);
    }

    @Nullable
    private ChartTradeLine findEditableTradeLine(float touchX, float touchY, float thresholdPx) {
        ChartTradeLine nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        nearest = findNearestEditableTradeLineInList(tradeLayerSnapshot.getDraftLines(), touchX, touchY, thresholdPx, nearestDistance);
        if (nearest != null) {
            nearestDistance = resolveTradeLayerTouchDistance(nearest, touchX, touchY);
        }
        ChartTradeLine liveNearest = findNearestEditableTradeLineInList(tradeLayerSnapshot.getLiveLines(), touchX, touchY, thresholdPx, nearestDistance);
        return liveNearest != null ? liveNearest : nearest;
    }

    @Nullable
    private ChartTradeLine findNearestEditableTradeLineInList(@Nullable List<ChartTradeLine> source,
                                                              float touchX,
                                                              float touchY,
                                                              float thresholdPx,
                                                              float currentBestDistance) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        ChartTradeLine nearest = null;
        float best = currentBestDistance;
        for (ChartTradeLine line : source) {
            if (!isTradeLineEditable(line)) {
                continue;
            }
            float distance = resolveTradeLayerTouchDistance(line, touchX, touchY);
            if (Float.isNaN(distance) || distance > thresholdPx || distance >= best) {
                continue;
            }
            nearest = line;
            best = distance;
        }
        return nearest;
    }

    private boolean isTradeLineEditable(@Nullable ChartTradeLine line) {
        return line != null
                && line.isEditable()
                && !line.isGhost()
                && !isQuickPendingDraftLine(line)
                && shouldDrawTradeLayerLine(line);
    }

    @Nullable
    private ChartTradeLine findTradeLineActionAtTouch(float touchX, float touchY) {
        ChartTradeLine draftLine = findTradeLineActionAtTouchInList(tradeLayerSnapshot.getDraftLines(), touchX, touchY);
        if (draftLine != null) {
            return draftLine;
        }
        return findTradeLineActionAtTouchInList(tradeLayerSnapshot.getLiveLines(), touchX, touchY);
    }

    @Nullable
    private ChartTradeLine findTradeLineActionAtTouchInList(@Nullable List<ChartTradeLine> source, float touchX, float touchY) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        for (ChartTradeLine line : source) {
            RectF actionRect = resolveTradeLayerActionButtonRect(line);
            if (actionRect == null) {
                continue;
            }
            if (actionRect.contains(touchX, touchY)) {
                return line;
            }
        }
        return null;
    }

    @Nullable
    private ChartTradeLine findNearestTradeLayerLine(float touchX, float touchY, float thresholdPx) {
        ChartTradeLine nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        nearest = findNearestTradeLayerLineInList(tradeLayerSnapshot.getLiveLines(), touchX, touchY, thresholdPx, nearestDistance);
        if (nearest != null) {
            nearestDistance = resolveTradeLayerTouchDistance(nearest, touchX, touchY);
        }
        ChartTradeLine draftNearest = findNearestTradeLayerLineInList(
                tradeLayerSnapshot.getDraftLines(),
                touchX,
                touchY,
                thresholdPx,
                nearestDistance
        );
        return draftNearest != null ? draftNearest : nearest;
    }

    @Nullable
    private ChartTradeLine findNearestTradeLayerLineInList(@Nullable List<ChartTradeLine> source,
                                                           float touchX,
                                                           float touchY,
                                                           float thresholdPx,
                                                           float currentBestDistance) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        ChartTradeLine nearest = null;
        float best = currentBestDistance;
        for (ChartTradeLine line : source) {
            if (line == null || !shouldDrawTradeLayerLine(line)) {
                continue;
            }
            float distance = resolveTradeLayerTouchDistance(line, touchX, touchY);
            if (Float.isNaN(distance) || distance > thresholdPx || distance >= best) {
                continue;
            }
            nearest = line;
            best = distance;
        }
        return nearest;
    }

    private float resolveTradeLayerTouchDistance(@Nullable ChartTradeLine line, float touchX, float touchY) {
        if (line == null || !Double.isFinite(line.getPrice()) || line.getPrice() <= 0d) {
            return Float.NaN;
        }
        float y = yFor(line.getPrice(), visiblePriceMin, visiblePriceMax, priceRect);
        if (Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
            return Float.NaN;
        }
        if (touchX < priceRect.left || touchX > priceRect.right) {
            return Float.NaN;
        }
        return Math.abs(y - touchY);
    }

    @NonNull
    private String resolveTradeLayerGroupKey(@Nullable ChartTradeLine line) {
        return line == null || line.getGroupId() == null ? "" : line.getGroupId().trim();
    }

    @Nullable
    private PriceAnnotation findNearestAnnotation(float touchX, float touchY, float thresholdPx) {
        PriceAnnotation nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        if (showPositionAnnotations) {
            nearest = findNearestAnnotationInList(positionAnnotations, touchX, touchY, thresholdPx, nearestDistance);
            if (nearest != null) {
                nearestDistance = resolveAnnotationTouchDistance(nearest, touchX, touchY);
            }
        }
        if (showPendingAnnotations) {
            PriceAnnotation pendingNearest = findNearestAnnotationInList(pendingAnnotations, touchX, touchY, thresholdPx, nearestDistance);
            if (pendingNearest != null) {
                nearest = pendingNearest;
                nearestDistance = resolveAnnotationTouchDistance(nearest, touchX, touchY);
            }
        }
        if (showHistoryTradeAnnotations) {
            PriceAnnotation historyNearest = findNearestAnnotationInList(historyTradeAnnotations, touchX, touchY, thresholdPx, nearestDistance);
            if (historyNearest != null) {
                nearest = historyNearest;
                nearestDistance = resolveAnnotationTouchDistance(nearest, touchX, touchY);
            }
        }
        return nearest;
    }

    @Nullable
    private PriceAnnotation findNearestAnnotationInList(List<PriceAnnotation> source,
                                                        float touchX,
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
            float distance = resolveAnnotationTouchDistance(annotation, touchX, touchY);
            if (Float.isNaN(distance)) {
                continue;
            }
            if (distance > thresholdPx || distance >= best) {
                continue;
            }
            nearest = annotation;
            best = distance;
        }
        return nearest;
    }

    private float resolveAnnotationTouchDistance(@Nullable PriceAnnotation annotation, float touchX, float touchY) {
        if (annotation == null) {
            return Float.NaN;
        }
        if (isTradeConnectorAnnotation(annotation)) {
            float startX = resolveHistoricalAnnotationX(annotation.anchorTimeMs);
            float startY = resolveAnnotationY(annotation);
            float endX = resolveHistoricalAnnotationX(annotation.secondaryAnchorTimeMs);
            float endY = resolveAnnotationSecondaryY(annotation);
            if (Float.isNaN(startX) || Float.isNaN(startY) || Float.isNaN(endX) || Float.isNaN(endY)
                    || !HistoricalTradeViewportHelper.isSegmentVisible(startX, endX, priceRect.left, priceRect.right)) {
                return Float.NaN;
            }
            return distanceToSegment(touchX, touchY, startX, startY, endX, endY);
        }
        float x;
        if (isTradePointAnnotation(annotation)) {
            x = resolveHistoricalAnnotationX(annotation.anchorTimeMs);
        } else {
            x = resolveAnnotationX(annotation.anchorTimeMs);
        }
        float y = resolveAnnotationY(annotation);
        if (Float.isNaN(x) || Float.isNaN(y) || y < priceRect.top || y > priceRect.bottom) {
            return Float.NaN;
        }
        if (isTradePointAnnotation(annotation)
                && !HistoricalTradeViewportHelper.isPointVisible(x, priceRect.left, priceRect.right)) {
            return Float.NaN;
        }
        if (isTradePointAnnotation(annotation)) {
            return distance(touchX, touchY, x, y);
        }
        return Math.abs(y - touchY);
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float denominator = dx * dx + dy * dy;
        if (denominator <= 1e-6f) {
            return distance(px, py, x1, y1);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / denominator;
        t = Math.max(0f, Math.min(1f, t));
        float projX = x1 + t * dx;
        float projY = y1 + t * dy;
        return distance(px, py, projX, projY);
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

    @NonNull
    private UiPaletteManager.Palette requirePalette() {
        if (activePalette == null) {
            activePalette = UiPaletteManager.resolve(getContext());
        }
        return activePalette;
    }

    private int defaultAnnotationColor() {
        return requirePalette().textPrimary;
    }

    private int latestPriceGuideColor() {
        return applyAlpha(requirePalette().controlSelectedText, 0.8f);
    }

    private int overlayLabelBackgroundColor(boolean selected) {
        return applyAlpha(requirePalette().card, selected ? 0.9f : 0.8f);
    }

    private int pointOverlayLabelBackgroundColor(boolean selected) {
        return applyAlpha(requirePalette().card, selected ? 0.9f : 0.82f);
    }

    private int applyAlpha(int color, float factor) {
        int baseAlpha = android.graphics.Color.alpha(color);
        int alpha = Math.max(0, Math.min(255, Math.round(baseAlpha * factor)));
        return ColorUtils.setAlphaComponent(color, alpha);
    }

    private float slot() {
        return candleWidth + candleGap;
    }

    // 将视口恢复到默认状态，供首次进入、切换标的和切换周期时复位。
    private void resetViewportToDefault() {
        candleWidth = dp(DEFAULT_CANDLE_WIDTH_DP);
        candleGap = dp(DEFAULT_CANDLE_GAP_DP);
        offsetCandles = 0f;
    }

    // 十字弹窗中的涨跌金额按正负切换颜色，其余字段沿用默认文字色。
    private Paint resolvePopupValuePaint(int rowIndex, double deltaValue) {
        if (rowIndex != 5) {
            return popupTextPaint;
        }
        if (deltaValue > 0d) {
            return popupPositiveTextPaint;
        }
        if (deltaValue < 0d) {
            return popupNegativeTextPaint;
        }
        return popupTextPaint;
    }

    private void requestDisallow(boolean disallow) {
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(disallow);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    // 根据拖动位置实时换算挂单价格，并同步通知外层页面。
    private void updateQuickPendingLinePrice(float touchY) {
        float safeY = clamp(touchY, priceRect.top, priceRect.bottom);
        double price = valueForY(safeY, visiblePriceMin, visiblePriceMax, priceRect);
        if (!Double.isFinite(price) || price <= 0d) {
            return;
        }
        quickPendingLinePrice = price;
        syncTradeLayerSnapshotWithQuickPendingLine();
        if (onQuickPendingLineChangeListener != null) {
            onQuickPendingLineChangeListener.onPriceChanged(price);
        }
        invalidate();
    }

    // 草稿线拖拽按手指位移增量更新，避免命中热区放宽后重新出现“吸到触点”的跳线感。
    private void updateQuickPendingLinePriceByDelta(float touchY) {
        if (Float.isNaN(quickPendingLineLastTouchY)) {
            quickPendingLineLastTouchY = touchY;
            return;
        }
        float deltaY = touchY - quickPendingLineLastTouchY;
        quickPendingLineLastTouchY = touchY;
        if (Math.abs(deltaY) < 0.5f) {
            return;
        }
        float currentLineY = yFor(quickPendingLinePrice, visiblePriceMin, visiblePriceMax, priceRect);
        if (Float.isNaN(currentLineY)) {
            return;
        }
        updateQuickPendingLinePrice(currentLineY + deltaY);
    }

    private double updateTradeLineEditPriceByDelta(float touchY) {
        if (Float.isNaN(tradeLineEditLastTouchY) || activeTradeLineEdit == null) {
            tradeLineEditLastTouchY = touchY;
            return Double.NaN;
        }
        float deltaY = touchY - tradeLineEditLastTouchY;
        tradeLineEditLastTouchY = touchY;
        if (Math.abs(deltaY) < 0.5f) {
            return Double.NaN;
        }
        double currentPrice = Double.isFinite(tradeLineEditCurrentPrice) && tradeLineEditCurrentPrice > 0d
                ? tradeLineEditCurrentPrice
                : activeTradeLineEdit.getPrice();
        float currentLineY = yFor(currentPrice, visiblePriceMin, visiblePriceMax, priceRect);
        if (Float.isNaN(currentLineY)) {
            return Double.NaN;
        }
        float safeY = clamp(currentLineY + deltaY, priceRect.top, priceRect.bottom);
        double price = valueForY(safeY, visiblePriceMin, visiblePriceMax, priceRect);
        if (!Double.isFinite(price) || price <= 0d) {
            return Double.NaN;
        }
        tradeLineEditCurrentPrice = price;
        return price;
    }

    // 把外层输入的草稿线同步回现有快捷挂单拖拽状态。
    private void syncQuickPendingLineFromTradeLayerSnapshot() {
        ChartTradeLine draftLine = findDraftPendingLine();
        if (draftLine == null) {
            quickPendingLineVisible = false;
            quickPendingLineDragging = false;
            quickPendingLinePrice = Double.NaN;
            quickPendingLinePointerId = -1;
            quickPendingLineTouchExclusive = false;
            return;
        }
        quickPendingLineVisible = true;
        quickPendingLinePrice = draftLine.getPrice();
        quickPendingLineDragging = draftLine.getState() == ChartTradeLineState.DRAGGING;
        if (!quickPendingLineDragging) {
            quickPendingLinePointerId = -1;
            quickPendingLineLastTouchY = Float.NaN;
            quickPendingLineTouchExclusive = false;
        }
    }

    // 把内部拖拽后的最新价格回写到正式状态层草稿线。
    private void syncTradeLayerSnapshotWithQuickPendingLine() {
        List<ChartTradeLine> liveLines = new ArrayList<>(tradeLayerSnapshot.getLiveLines());
        List<ChartTradeLine> draftLines = new ArrayList<>();
        boolean replaced = false;
        for (ChartTradeLine line : tradeLayerSnapshot.getDraftLines()) {
            if (line == null) {
                continue;
            }
            if (line.getState() == ChartTradeLineState.DRAFT_PENDING
                    || line.getState() == ChartTradeLineState.DRAGGING) {
                replaced = true;
                if (quickPendingLineVisible && Double.isFinite(quickPendingLinePrice) && quickPendingLinePrice > 0d) {
                    draftLines.add(new ChartTradeLine(
                            line.getId().isEmpty() ? "quick-pending-draft" : line.getId(),
                            line.getGroupId().isEmpty() ? "quick-pending-draft" : line.getGroupId(),
                            quickPendingLinePrice,
                            line.getLabel(),
                            line.getCenterLabel(),
                            quickPendingLineDragging ? ChartTradeLineState.DRAGGING : ChartTradeLineState.DRAFT_PENDING,
                            line.getTone()
                    ));
                }
            } else {
                draftLines.add(line);
            }
        }
        if (!replaced && quickPendingLineVisible && Double.isFinite(quickPendingLinePrice) && quickPendingLinePrice > 0d) {
            draftLines.add(new ChartTradeLine(
                    "quick-pending-draft",
                    "quick-pending-draft",
                    quickPendingLinePrice,
                    "草稿挂单",
                    "",
                    quickPendingLineDragging ? ChartTradeLineState.DRAGGING : ChartTradeLineState.DRAFT_PENDING,
                    ChartTradeLineTone.PRIMARY
            ));
        }
        tradeLayerSnapshot = new ChartTradeLayerSnapshot(liveLines, draftLines);
    }

    // 取当前快照里的第一条草稿挂单线，作为旧拖拽逻辑的同步输入。
    @Nullable
    private ChartTradeLine findDraftPendingLine() {
        for (ChartTradeLine line : tradeLayerSnapshot.getDraftLines()) {
            if (line == null) {
                continue;
            }
            if (line.getState() == ChartTradeLineState.DRAFT_PENDING
                    || line.getState() == ChartTradeLineState.DRAGGING
                    || line.getState() == ChartTradeLineState.SELECTED
                    || line.getState() == ChartTradeLineState.SUBMITTING
                    || line.getState() == ChartTradeLineState.REJECTED_ROLLBACK) {
                return line;
            }
        }
        return null;
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
            ChartScaleGestureResolver.Mode scaleMode = ChartScaleGestureResolver.resolveMode(absX, absY);
            float beforeSlot = slot();
            int focusIndex = xToIndex(detector.getFocusX(), visibleEndFloat);
            float rawScale = scaleMode == ChartScaleGestureResolver.Mode.HORIZONTAL
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

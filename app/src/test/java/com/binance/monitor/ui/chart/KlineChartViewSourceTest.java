package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class KlineChartViewSourceTest {

    @Test
    public void historicalTradeRawIndexShouldUseVisibleBucketMapping() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("resolveTimeInsideBucketRawIndex("));
        assertTrue(source.contains("resolveHistoricalTradeLastVisibleTime("));
    }

    @Test
    public void defaultViewportShouldUseWiderSlotToShowFewerCandles() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private static final float DEFAULT_CANDLE_WIDTH_DP = 1.28f;"));
        assertTrue(source.contains("private static final float DEFAULT_CANDLE_GAP_DP = 0.88f;"));
        assertTrue(source.contains("candleWidth = dp(DEFAULT_CANDLE_WIDTH_DP);"));
        assertTrue(source.contains("candleGap = dp(DEFAULT_CANDLE_GAP_DP);"));
        assertTrue(source.contains("private void resetViewportToDefault() {\n        candleWidth = dp(DEFAULT_CANDLE_WIDTH_DP);\n        candleGap = dp(DEFAULT_CANDLE_GAP_DP);"));
    }

    @Test
    public void chartShouldOnlyAllowHorizontalScaling() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(!source.contains("private float verticalScale = 1f;"));
        assertTrue(!source.contains("private static final float MIN_VERTICAL_SCALE"));
        assertTrue(!source.contains("private static final float MAX_VERTICAL_SCALE"));
        assertTrue(!source.contains("verticalScale = clamp(verticalScale * smoothYScale"));
        assertTrue(!source.contains("/ Math.max(1e-6f, verticalScale)"));
    }

    @Test
    public void crosshairShouldSnapHorizontallyToHighlightedCandleCenter() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("highlightedIndex = Math.round(rawIndex);"));
        assertTrue(source.contains("crosshairX = clamp(xFor(highlightedIndex, visibleEndFloat), priceRect.left, priceRect.right);"));
        assertTrue(source.contains("crosshairY = clamp(y, priceRect.top, priceRect.bottom);"));
        assertTrue(source.contains("crosshairOnCandle = rawIndex >= -0.05f && rawIndex <= candles.size() - 1f + resolveRightBlankSlotsForOffset();"));
    }

    @Test
    public void activeCrosshairShouldRestoreByHighlightedOpenTimeDuringViewportRefresh() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("long highlightedOpenTime = resolveHighlightedOpenTime();"));
        assertTrue(source.contains("restoreCrosshairByOpenTime(highlightedOpenTime)"));
        assertTrue(source.contains("updateCrosshair(crosshairX, crosshairY);"));
    }

    @Test
    public void prependOlderCandlesShouldRestoreActiveCrosshairByOpenTimeInsteadOfOldIndex() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("public void prependCandles(@Nullable List<CandleEntry> olderCandles) {"));
        assertTrue(source.contains("long highlightedOpenTime = resolveHighlightedOpenTime();"));
        assertTrue(source.contains("if (!restoreCrosshairByOpenTime(highlightedOpenTime)) {\n                updateCrosshair(crosshairX, crosshairY);\n            }"));
    }

    @Test
    public void prependOlderCandlesShouldKeepViewportOffsetInsteadOfJumpingToNewLeftEdge() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("float oldOffset = offsetCandles;"));
        assertTrue(source.contains("offsetCandles = oldOffset;"));
        assertFalse(source.contains("offsetCandles += olderCandles.size();"));
    }

    @Test
    public void offsetClampShouldRefreshVisibleEndFloatBeforeCrosshairMath() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private void syncVisibleEndFloat() {\n        visibleEndFloat = candles.isEmpty() ? 0f : (candles.size() - 1f - offsetCandles);\n    }"));
        assertTrue(source.contains("private void clampOffset() {\n        offsetCandles = clamp(offsetCandles, 0f, maxOffset());\n        syncVisibleEndFloat();\n    }"));
    }

    @Test
    public void klineChartViewShouldExposeQuickPendingLineDrawingAndDragHooks() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private boolean quickPendingLineVisible;"));
        assertTrue(source.contains("private double quickPendingLinePrice = Double.NaN;"));
        assertTrue(source.contains("public void showQuickPendingLine(double price) {"));
        assertTrue(source.contains("public void hideQuickPendingLine() {"));
        assertTrue(source.contains("public void setOnQuickPendingLineChangeListener("));
        assertTrue(source.contains("drawQuickPendingLine(canvas"));
        assertTrue(source.contains("resolveQuickPendingLineTouchThresholdPx()"));
        assertTrue(source.contains("return Math.max(dp(56f), touchSlop * 4f);"));
        assertTrue(source.contains("findNearestTradeLayerLineInList("));
        assertTrue(source.contains("isQuickPendingDraftLine(matchedDraftLine)"));
        assertTrue(source.contains("private int quickPendingLinePointerId = -1;"));
        assertTrue(source.contains("private float quickPendingLineLastTouchY = Float.NaN;"));
        assertTrue(source.contains("private boolean quickPendingLineTouchExclusive;"));
        assertTrue(source.contains("quickPendingLinePointerId = event.getPointerId(event.getActionIndex());"));
        assertTrue(source.contains("quickPendingLineLastTouchY = event.getY(event.getActionIndex());"));
        assertTrue(source.contains("int activePointerIndex = event.findPointerIndex(quickPendingLinePointerId);"));
        assertTrue(source.contains("updateQuickPendingLinePriceByDelta(event.getY(activePointerIndex));"));
        assertTrue(source.contains("quickPendingLineTouchExclusive = true;"));
        assertTrue(source.contains("quickPendingLineTouchExclusive = false;"));
        assertTrue(source.contains("private void resetQuickPendingLineDragging() {"));
        assertTrue(source.contains("quickPendingLinePointerId = -1;"));
        assertTrue(source.contains("quickPendingLineLastTouchY = Float.NaN;"));
        assertTrue(source.contains("private void updateQuickPendingLinePriceByDelta(float touchY) {"));
        assertTrue(source.contains("float deltaY = touchY - quickPendingLineLastTouchY;"));
        assertTrue(source.contains("float currentLineY = yFor(quickPendingLinePrice, visiblePriceMin, visiblePriceMax, priceRect);"));
        assertTrue(source.contains("updateQuickPendingLinePrice(currentLineY + deltaY);"));
        assertTrue(source.contains("private ChartTradeLine findQuickPendingDraftLineAtTouch(float x, float y) {"));
        assertTrue(source.contains("private boolean shouldExitQuickPendingModeByTap(float x, float y) {"));
        assertTrue(source.contains("return quickPendingLineVisible && !isQuickPendingDraftLine(findQuickPendingDraftLineAtTouch(x, y));"));
        assertTrue(source.contains("private boolean shouldBlockTradeLayerInteractionForQuickPending(@Nullable MotionEvent event) {"));
        assertFalse(source.contains("quickPendingLineDragTouchOffsetY"));
        assertFalse(source.contains("resolveQuickPendingLineDragTouchOffsetY("));
        assertFalse(source.contains("updateQuickPendingLinePrice(event.getY(activePointerIndex));"));
    }

    @Test
    public void klineChartViewShouldExposeDedicatedTradeLayerSnapshotEntry() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private ChartTradeLayerSnapshot tradeLayerSnapshot"));
        assertTrue(source.contains("public void setTradeLayerSnapshot("));
        assertTrue(source.contains("public void setHighlightedTradeGroup(@Nullable String groupId) {"));
        assertTrue(source.contains("public void clearHighlightedTradeGroup() {"));
        assertTrue(source.contains("drawTradeLayerSnapshot(canvas"));
        assertTrue(source.contains("drawHighlightedTradeAxisLabels(canvas, visiblePriceMin, visiblePriceMax);"));
        assertTrue(source.contains("private void drawHighlightedTradeAxisLabels(@NonNull Canvas canvas, double min, double max) {"));
        assertTrue(source.contains("collectHighlightedTradeAxisLabels(min, max)"));
        assertTrue(source.contains("appendHighlightedTradeAxisLabelsFromLines(labels, seenKeys, tradeLayerSnapshot.getLiveLines(), min, max);"));
        assertTrue(source.contains("appendHighlightedTradeAxisLabelsFromLines(labels, seenKeys, tradeLayerSnapshot.getDraftLines(), min, max);"));
        assertTrue(source.contains("FormatUtils.formatPrice(line.getPrice())"));
        assertTrue(source.contains("private int resolveHighlightedTradeAxisTextColor(int backgroundColor) {"));
        assertTrue(source.contains("ChartTradeLineState.LIVE_POSITION"));
        assertTrue(source.contains("ChartTradeLineState.LIVE_PENDING"));
        assertTrue(source.contains("ChartTradeLineState.LIVE_TP"));
        assertTrue(source.contains("ChartTradeLineState.LIVE_SL"));
        assertTrue(source.contains("drawTradeLayerLeftAffordances(canvas, actionText, leftLabel, y, lineColor, selected);"));
        assertTrue(source.contains("drawTradeLayerCenterLabel(canvas, centerLabel, y, lineColor, selected);"));
        assertTrue(source.contains("boolean hasGroupHighlight = !highlightedAnnotationGroupId.isEmpty();"));
        assertTrue(source.contains("highlightedAnnotationGroupId.equals(resolveTradeLayerGroupKey(line))"));
        assertTrue(source.contains("lineColor = applyAlpha(lineColor, 0.34f);"));
        assertTrue(source.contains("line.getState() == ChartTradeLineState.LIVE_POSITION"));
        assertTrue(source.contains("line.getState() == ChartTradeLineState.LIVE_PENDING"));
        assertTrue(source.contains("line.getState() == ChartTradeLineState.LIVE_TP"));
        assertTrue(source.contains("line.getState() == ChartTradeLineState.LIVE_SL"));
        assertTrue(source.contains("return palette == null ? secondaryTextColor : palette.primary;"));
        assertTrue(source.contains("return new DashPathEffect(new float[]{dp(6f), dp(4f)}, 0f);"));
        assertTrue(source.contains("return new DashPathEffect(new float[]{dp(4f), dp(3f)}, 0f);"));
        assertTrue(source.contains("labelText.startsWith(\"挂单 买\")"));
        assertTrue(source.contains("labelText.startsWith(\"挂单 卖\")"));
        assertTrue(source.contains("if (!highlightedAnnotationGroupId.isEmpty()) {\n                    if (isTouchInsideHighlightedGroup(e.getX(), e.getY())) {\n                        return true;\n                    }\n                    clearHighlightedAnnotationGroup();\n                    if (onTradeLineEditListener != null) {\n                        onTradeLineEditListener.onTradeLineBlankAreaTap();\n                    }\n                    return true;\n                }"));
        assertTrue(source.contains("private boolean isTouchInsideHighlightedGroup(float x, float y) {"));
        assertTrue(source.contains("return isTouchInsideHighlightedTradeGroup(x, y)\n                || isTouchInsideHighlightedAnnotationGroup(x, y);"));
        assertTrue(source.contains("private boolean shouldSuppressTradeLayerTouchForExistingHighlight(float x, float y) {"));
        assertTrue(source.contains("return !highlightedAnnotationGroupId.isEmpty() && !isTouchInsideHighlightedGroup(x, y);"));
        assertTrue(source.contains("private boolean isTouchInsideHighlightedTradeGroupInList(@Nullable List<ChartTradeLine> source,"));
        assertTrue(source.contains("!highlightedAnnotationGroupId.equals(resolveTradeLayerGroupKey(line))"));
        assertTrue(source.contains("RectF actionRect = resolveTradeLayerActionButtonRect(line);"));
        assertTrue(source.contains("RectF labelRect = resolveTradeLayerLeftLabelRect(line);"));
        assertTrue(source.contains("RectF centerRect = resolveTradeLayerCenterLabelRect(line);"));
        assertTrue(source.contains("distance <= dp(14f)"));
        assertTrue(source.contains("private boolean isTouchInsideHighlightedAnnotationGroupInList(@Nullable List<PriceAnnotation> source,"));
        assertTrue(source.contains("!highlightedAnnotationGroupId.equals(resolveAnnotationGroupKey(annotation))"));
        assertTrue(source.contains("findNearestTradeLayerLine(x, y, dp(14f))"));
        assertTrue(source.contains("setHighlightedAnnotationGroup(resolveTradeLayerGroupKey(matchedTradeLine))"));
        assertTrue(source.contains("if (safeGroupKey.equals(highlightedAnnotationGroupId)) {\n            return true;\n        }"));
        assertTrue(source.contains("containsTradeLayerGroup(highlightedAnnotationGroupId)"));
        assertTrue(source.contains("private float resolveTradeLayerTouchDistance(@Nullable ChartTradeLine line, float touchX, float touchY) {"));
        assertTrue(source.contains("private RectF resolveTradeLayerLeftLabelRect(@Nullable ChartTradeLine line) {"));
        assertTrue(source.contains("private RectF resolveTradeLayerCenterLabelRect(@Nullable ChartTradeLine line) {"));
        assertFalse(source.contains("drawOverlayAnnotations(canvas, positionAnnotations);"));
        assertFalse(source.contains("drawOverlayAnnotations(canvas, pendingAnnotations);"));
        assertFalse(source.contains("drawOverlayAnnotations(canvas, abnormalAnnotations);"));
        assertFalse(source.contains("setAbnormalAnnotations("));
        assertFalse(source.contains("isAbnormalAnnotation("));
    }

    @Test
    public void klineChartViewShouldExposeEditableTradeLineDragAndActionHooks() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("public interface OnTradeLineEditListener {"));
        assertTrue(source.contains("void onTradeLineDragStart(@NonNull ChartTradeLine line);"));
        assertTrue(source.contains("void onTradeLineDragUpdate(@NonNull ChartTradeLine line, double price);"));
        assertTrue(source.contains("void onTradeLineActionClick(@NonNull ChartTradeLine line);"));
        assertTrue(source.contains("void onTradeLineBlankAreaTap();"));
        assertTrue(source.contains("public void setOnTradeLineEditListener(@Nullable OnTradeLineEditListener listener) {"));
        assertTrue(source.contains("if (action == MotionEvent.ACTION_DOWN) {\n            downX = event.getX();\n            downY = event.getY();\n        }\n        if (handleQuickPendingLineTouch(event)) {\n            return true;\n        }\n        if (handleTradeLineEditTouch(event)) {"));
        assertTrue(source.contains("private boolean handleTradeLineEditTouch(@Nullable MotionEvent event) {"));
        assertTrue(source.contains("if (shouldBlockTradeLayerInteractionForQuickPending(event)) {\n            return false;\n        }"));
        assertTrue(source.contains("if (shouldSuppressTradeLayerTouchForExistingHighlight(event.getX(), event.getY())) {\n                quickPendingLineTouchExclusive = false;\n                return false;\n            }"));
        assertTrue(source.contains("if (shouldSuppressTradeLayerTouchForExistingHighlight(event.getX(), event.getY())) {\n                return false;\n            }"));
        assertTrue(source.contains("ChartTradeLine actionLine = findTradeLineActionAtTouch(event.getX(), event.getY());"));
        assertTrue(source.contains("ChartTradeLine matchedLine = findEditableTradeLine(event.getX(), event.getY(), resolveTradeLineEditTouchThresholdPx());"));
        assertTrue(source.contains("tradeLineEditCurrentPrice = matchedLine.getPrice();"));
        assertTrue(source.contains("setHighlightedAnnotationGroup(resolveTradeLayerGroupKey(actionLine));"));
        assertTrue(source.contains("setHighlightedAnnotationGroup(resolveTradeLayerGroupKey(matchedLine));"));
        assertTrue(source.contains("double updatedPrice = updateTradeLineEditPriceByDelta(event.getY(activePointerIndex));"));
        assertFalse(source.contains("if (!highlightedAnnotationGroupId.isEmpty()) {\n                    return setHighlightedAnnotationGroup("));
        assertTrue(source.contains("if (shouldExitQuickPendingModeByTap(e.getX(), e.getY())) {\n                    clearHighlightedAnnotationGroup();\n                    if (onTradeLineEditListener != null) {\n                        onTradeLineEditListener.onTradeLineBlankAreaTap();\n                    }\n                    return true;\n                }"));
        assertTrue(source.contains("if (!selectAnnotationGroupByTouch(e.getX(), e.getY())) {\n                    clearHighlightedAnnotationGroup();\n                    if (onTradeLineEditListener != null) {\n                        onTradeLineEditListener.onTradeLineBlankAreaTap();\n                    }\n                }"));
        assertTrue(source.contains("return Math.max(dp(44f), touchSlop * 3f);"));
        assertTrue(source.contains("line.isEditable()"));
        assertTrue(source.contains("!line.isGhost()"));
        assertTrue(source.contains("!isQuickPendingDraftLine(line)"));
        assertTrue(source.contains("resolveTradeLayerActionButtonRect(line)"));
        assertTrue(source.contains("return line == null || line.getGroupId() == null ? \"\" : line.getGroupId().trim();"));
    }

    @Test
    public void klineChartViewShouldExposePriceInfoTextMetricsForOverlayAlignment() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize(textPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize(crossLabelTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize(popupTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize(popupPositiveTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize(popupNegativeTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize(latestPriceTagTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize(extremeLabelTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize(overlayLabelTextPaint, getContext(), R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertFalse(source.contains("getResources().getDimension(R.dimen.chart_price_info_text_size)"));
        assertFalse(source.contains("textPaint.setTextSize(dp(9f));"));
        assertFalse(source.contains("setTextSize(dp(8f));"));
        assertFalse(source.contains("setTextSize(dp(8.5f));"));
        assertTrue(source.contains("float getPriceInfoTextSizePx() {\n        return textPaint.getTextSize();\n    }"));
        assertTrue(source.contains("int getPricePaneTitleBaselineOffsetPx() {\n        return Math.round(SpacingTokenResolver.dpFloat(getContext(), KlinePaneTextLayoutHelper.resolvePaneTitleBaselineOffsetRes()));\n    }"));
        assertFalse(source.contains("drawPriceOhlcInfo(canvas, infoIndex);"));
        assertFalse(source.contains("private void drawPriceOhlcInfo(Canvas canvas, int index) {"));
    }

    @Test
    public void chartPaletteShouldComeFromUiPaletteManagerInsteadOfLocalColorDefaults() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("activePalette = palette;"));
        assertTrue(source.contains("macdDifPaint.setColor(palette.xau);"));
        assertTrue(source.contains("macdDeaPaint.setColor(palette.btc);"));
        assertTrue(source.contains("stochKPaint.setColor(palette.xau);"));
        assertTrue(source.contains("stochDPaint.setColor(palette.btc);"));
        assertTrue(source.contains("private int defaultAnnotationColor() {"));
        assertTrue(source.contains("private int latestPriceGuideColor() {"));
        assertTrue(source.contains("private int overlayLabelBackgroundColor(boolean selected) {"));
        assertTrue(source.contains("private int pointOverlayLabelBackgroundColor(boolean selected) {"));
    }
}

package com.binance.monitor.ui.chart;

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
    public void offsetClampShouldRefreshVisibleEndFloatBeforeCrosshairMath() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private void syncVisibleEndFloat() {\n        visibleEndFloat = candles.isEmpty() ? 0f : (candles.size() - 1f - offsetCandles);\n    }"));
        assertTrue(source.contains("private void clampOffset() {\n        offsetCandles = clamp(offsetCandles, 0f, maxOffset());\n        syncVisibleEndFloat();\n    }"));
    }
}

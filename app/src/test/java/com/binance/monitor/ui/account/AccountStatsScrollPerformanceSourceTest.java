package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsScrollPerformanceSourceTest {

    @Test
    public void analysisCurveChartsShouldUsePreparedRenderModelsOutsideOnDraw() throws Exception {
        assertPreparedRenderModel("EquityCurveView.java", "EquityCurveRenderModel");
        assertPreparedRenderModel("PositionRatioChartView.java", "PositionRatioRenderModel");
        assertPreparedRenderModel("DrawdownChartView.java", "DrawdownRenderModel");
        assertPreparedRenderModel("DailyReturnChartView.java", "DailyReturnRenderModel");
    }

    @Test
    public void analysisCurveChartsShouldNotInvalidateMultipleTimesPerProjectionBind() throws Exception {
        String helper = readUtf8("app/src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java");

        assertTrue(helper.contains("binding.equityCurveView.setRenderData("));
        assertTrue(helper.contains("binding.positionRatioChartView.setRenderData("));
        assertTrue(helper.contains("binding.drawdownChartView.setRenderData("));
        assertTrue(helper.contains("binding.dailyReturnChartView.setRenderData("));
        assertFalse(helper.contains("binding.equityCurveView.setViewport("));
        assertFalse(helper.contains("binding.positionRatioChartView.setViewport("));
        assertFalse(helper.contains("binding.drawdownChartView.setViewport("));
        assertFalse(helper.contains("binding.dailyReturnChartView.setViewport("));
    }

    @Test
    public void secondaryCurveHighlightLookupShouldUseBinarySearch() throws Exception {
        assertTrue(readUtf8("app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java",
                "src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java")
                .contains("CurvePointBinarySearch.nearestCurvePointIndex("));
        assertTrue(readUtf8("app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java",
                "src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java")
                .contains("CurvePointBinarySearch.nearestDrawdownPointIndex("));
        assertTrue(readUtf8("app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java",
                "src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java")
                .contains("CurvePointBinarySearch.nearestDailyReturnPointIndex("));
    }

    private static void assertPreparedRenderModel(String fileName, String modelName) throws Exception {
        String source = readUtf8("app/src/main/java/com/binance/monitor/ui/account/" + fileName,
                "src/main/java/com/binance/monitor/ui/account/" + fileName);
        String onDraw = methodBody(source, "protected void onDraw");
        assertTrue(fileName + " 必须保留预计算渲染模型", source.contains("private " + modelName + " renderModel"));
        assertTrue(fileName + " 必须在尺寸变化时重建渲染模型", source.contains("protected void onSizeChanged"));
        assertFalse(fileName + " 的 onDraw 不应再全量遍历原始 points", onDraw.contains("for (int i = 0; i < points.size(); i++)"));
        assertFalse(fileName + " 的 onDraw 不应再直接遍历原始 points", onDraw.contains("for (CurvePoint point : points)"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到源码文件");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new IllegalStateException("找不到方法: " + signature);
        }
        int braceStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(braceStart, i + 1);
                }
            }
        }
        throw new IllegalStateException("方法括号不完整: " + signature);
    }
}

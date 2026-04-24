/*
 * 账户曲线视图源码约束测试，确保曲线和回撤高亮跟随当前主题 palette。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EquityCurveViewSourceTest {

    @Test
    public void equityCurveViewShouldUsePaletteDrivenBalanceAndDrawdownColors() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
                "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java"
        );

        assertTrue("结余线应跟随主题主文字色，而不是固定白色",
                source.contains("balancePaint.setColor(applyAlpha(palette.textPrimary"));
        assertTrue("回撤高亮应跟随主题 warning/xau 色",
                source.contains("drawdownStrokePaint.setColor(applyAlpha(palette.xau"));
        assertTrue("回撤填充应跟随主题 warning/xau 色",
                source.contains("drawdownFillPaint.setColor(applyAlpha(palette.xau"));
        assertTrue("回撤峰值标记应跟随主题警示色弱化层",
                source.contains("drawdownPeakMarkerPaint.setColor(blendColor("));
        assertFalse("结余线不能再固定写死白色",
                source.contains("balancePaint.setColor(Color.WHITE)"));
        assertFalse("回撤高亮不能再保留硬编码色值",
                source.contains("0x33FFD54F"));
        assertFalse("回撤高亮不能再保留硬编码色值",
                source.contains("0xFFFFB300"));
    }

    @Test
    public void equityCurveTooltipTimeShouldFollowSharedHighlightTimestamp() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
                "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java"
        );

        assertTrue("主图弹窗时间应优先使用当前共享高亮时间，而不是固定吸附到最近真实点时间",
                source.contains("tooltipLines.add(formatLabelTime(resolveHighlightLabelTimestamp(point)));"));
    }

    @Test
    public void equityCurveShouldDrawZeroPercentReferenceLineFromBaseBalance() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
                "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java"
        );

        assertTrue("净值曲线应单独绘制基准收益 0% 横线，便于定位基准净值",
                source.contains("drawZeroPercentReferenceLine(canvas, chartLeft, chartRight, chartTop, chartBottom, chartMin, chartMax, baseBalance);"));
        assertTrue("0% 横线应按 baseBalance 映射到图内纵坐标",
                source.contains("float zeroLineY = mapY(baseBalance, min, max, top, bottom);"));
        assertTrue("0% 横线应带有独立标识，避免和普通网格线混淆",
                source.contains("canvas.drawText(\"0%\", right - percentWidth, resolveAxisLabelBaseline(zeroLineY, top, bottom), labelPaint);"));
        assertTrue("0% 横线应改为白色，便于在深色背景中作为基准线观察",
                source.contains("zeroPercentLinePaint.setColor(0xFFFFFFFF);"));
        assertTrue("0% 横线应改为实线，不能继续保留虚线路径效果",
                source.contains("zeroPercentLinePaint.setPathEffect(null);"));
    }

    @Test
    public void equityCurveBottomAxisLabelsShouldClampInsideChartBounds() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
                "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java"
        );

        assertTrue("左右纵轴刻度应统一走基线保护方法，避免最下方数字被下方图遮挡",
                source.contains("float baseline = resolveAxisLabelBaseline(y, top, bottom);"));
        assertTrue("左侧金额刻度应使用受保护的基线位置",
                source.contains("canvas.drawText(amount, dp(4f), baseline, labelPaint);"));
        assertTrue("右侧收益率刻度应使用受保护的基线位置",
                source.contains("canvas.drawText(percent, getWidth() - dp(4f) - percentWidth, baseline, labelPaint);"));
        assertTrue("基线保护方法应把底部刻度往上收，避免压到下方图表区域",
                source.contains("return Math.max(top + dp(9f), Math.min(bottom - dp(2f), y + dp(3f)));"));
    }

    @Test
    public void equityCurveShouldRespectSharedViewportRangeForDrawingAndTouch() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
                "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java"
        );

        assertTrue("主图需要显式接收共享 viewport，避免继续只按最后真实点做横轴范围",
                source.contains("public void setViewport(long startTs, long endTs)"));
        assertTrue("主图绘制横轴起点应优先使用共享 viewportStartTs",
                source.contains("chartStartTs = viewportStartTs > 0L ? viewportStartTs : points.get(0).getTimestamp();"));
        assertTrue("主图绘制横轴终点应优先使用共享 viewportEndTs",
                source.contains("chartEndTs = viewportEndTs > chartStartTs ? viewportEndTs : points.get(points.size() - 1).getTimestamp();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 EquityCurveView.java");
    }
}

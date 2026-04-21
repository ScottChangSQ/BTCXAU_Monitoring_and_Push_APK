/*
 * 附图右侧竖排图例源码约束测试，确保回撤、仓位、收益三张图的标题按文字本身垂直居中。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CurvePaneLegendAlignmentSourceTest {

    @Test
    public void paneLegendsShouldSwitchToCenterAlignBeforeDrawingRotatedTitles() throws Exception {
        assertCenteredLegend(
                readUtf8(
                        "app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java",
                        "src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java"
                ),
                "当前区间回撤"
        );
        assertCenteredLegend(
                readUtf8(
                        "app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java",
                        "src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java"
                ),
                "当前区间仓位"
        );
        assertCenteredLegend(
                readUtf8(
                        "app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java",
                        "src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java"
                ),
                "当前区间日收益"
        );
    }

    private static void assertCenteredLegend(String source, String title) {
        assertTrue("旋转图例前应切到居中对齐，避免标题整体偏下: " + title,
                source.contains("labelPaint.setTextAlign(Paint.Align.CENTER);"));
        assertTrue("旋转图例应继续围绕右侧固定锚点绘制: " + title,
                source.contains("canvas.drawText(\"" + title + "\", rightEdge, verticalCenterBaseline, labelPaint);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到附图源码文件");
    }
}

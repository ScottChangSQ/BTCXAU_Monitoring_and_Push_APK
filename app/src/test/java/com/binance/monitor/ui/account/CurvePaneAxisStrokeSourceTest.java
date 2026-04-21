/*
 * 账户曲线区坐标轴线宽源码约束测试，确保主图与三张附图共用同一套轴线粗细真值。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CurvePaneAxisStrokeSourceTest {

    @Test
    public void allCurvePanesShouldUseSharedAxisStrokeWidth() throws Exception {
        String helper = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/CurvePaneLayoutHelper.java",
                "src/main/java/com/binance/monitor/ui/account/CurvePaneLayoutHelper.java"
        );
        assertTrue("曲线区布局辅助应提供统一的轴线粗细真值",
                helper.contains("static int resolveAxisStrokeRes() {"));
        assertTrue("共享轴线粗细当前应保持单一资源 token，避免再次回到裸 dp",
                helper.contains("return R.dimen.curve_axis_stroke;"));

        assertUsesSharedAxisStroke(
                "app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
                "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java"
        );
        assertUsesSharedAxisStroke(
                "app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java",
                "src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java"
        );
        assertUsesSharedAxisStroke(
                "app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java",
                "src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java"
        );
        assertUsesSharedAxisStroke(
                "app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java",
                "src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java"
        );
    }

    private static void assertUsesSharedAxisStroke(String... candidates) throws Exception {
        String source = readUtf8(candidates);
        assertTrue("曲线图坐标轴应从共享 helper 读取线宽，而不是局部写死 dp(1f)",
                source.contains("axisPaint.setStrokeWidth(SpacingTokenResolver.dpFloat(getContext(), CurvePaneLayoutHelper.resolveAxisStrokeRes()));"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到曲线区源码文件");
    }
}

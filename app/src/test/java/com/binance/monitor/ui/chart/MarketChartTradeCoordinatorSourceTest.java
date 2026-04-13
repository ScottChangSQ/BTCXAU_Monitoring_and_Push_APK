/*
 * 图表页拆分源码约束测试，锁定交易对话和执行链下沉。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartTradeCoordinatorSourceTest {

    @Test
    public void marketChartActivityShouldDelegateOnlyButtonDrivenTradeFlow() throws Exception {
        assertTrue(exists("app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java"));

        String source = readUtf8("app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        assertTrue(source.contains("private MarketChartTradeDialogCoordinator tradeDialogCoordinator;"));
        assertTrue(source.contains("tradeDialogCoordinator = new MarketChartTradeDialogCoordinator("));
        assertTrue(source.contains("tradeDialogCoordinator.showTradeCommandDialog("));
        assertTrue(source.contains("tradeDialogCoordinator.cancelTradeTasks();"));
        assertFalse(source.contains("tradeDialogCoordinator.showPositionActionMenu("));
        assertFalse(source.contains("tradeDialogCoordinator.showPendingOrderActionMenu("));
    }

    @Test
    public void tradeCoordinatorShouldNotKeepLegacyListActionHelpers() throws Exception {
        String source = readUtf8("app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");
        assertFalse(source.contains("buildClosePositionInput("));
        assertFalse(source.contains("buildPendingCancelInput("));
    }

    private static boolean exists(String... candidates) {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            if (Files.exists(workingDir.resolve(candidate).normalize())) {
                return true;
            }
        }
        return false;
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MarketChartActivity.java");
    }
}

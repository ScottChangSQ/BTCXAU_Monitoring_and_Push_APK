/*
 * 图表页拆分源码约束测试，锁定交易对话和执行链下沉。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartTradeCoordinatorSourceTest {

    @Test
    public void marketChartActivityShouldDelegateTradeFlow() throws Exception {
        assertTrue("图表交易协调器文件应存在",
                exists("app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java",
                        "src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java"));

        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        );
        assertTrue("图表页应持有交易协调器",
                source.contains("private MarketChartTradeDialogCoordinator tradeDialogCoordinator;"));
        assertTrue("图表页初始化时应装配交易协调器",
                source.contains("tradeDialogCoordinator = new MarketChartTradeDialogCoordinator("));
        assertTrue("持仓操作菜单应委托给交易协调器",
                source.contains("tradeDialogCoordinator.showPositionActionMenu("));
        assertTrue("挂单操作菜单应委托给交易协调器",
                source.contains("tradeDialogCoordinator.showPendingOrderActionMenu("));
        assertTrue("交易弹窗应委托给交易协调器",
                source.contains("tradeDialogCoordinator.showTradeCommandDialog("));
        assertTrue("销毁时应委托协调器取消交易任务",
                source.contains("tradeDialogCoordinator.cancelTradeTasks();"));
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

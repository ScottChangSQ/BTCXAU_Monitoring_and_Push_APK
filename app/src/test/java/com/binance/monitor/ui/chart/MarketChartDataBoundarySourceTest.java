/*
 * 图表页数据边界源码约束测试，锁定 symbol+interval+session 切换时的显示上下文收口。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartDataBoundarySourceTest {

    @Test
    public void symbolAndIntervalSwitchShouldInvalidatePreviousChartContextBeforeRequest() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String runtimeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("切换产品前应先失效旧图表上下文，避免旧 K 线继续挂在新产品名下",
                source.contains("private void invalidateChartDisplayContext() {"));
        assertTrue("切换产品应先走运行时入口，再由运行时统一失效旧上下文并重拉取",
                source.contains("pageRuntime.requestSymbolSelection(symbol);"));
        assertTrue("切换周期也应先走运行时入口，再由运行时统一失效旧上下文并重拉取",
                source.contains("pageRuntime.requestIntervalSelection(INTERVALS[0].key)"));
        assertTrue("运行时里应在提交新的图表选择后先失效旧上下文，再发起统一刷新",
                runtimeSource.contains("host.commitSelectedSymbol(symbol);")
                        && runtimeSource.contains("host.commitSelectedInterval(intervalKey);")
                        && runtimeSource.contains("host.invalidateChartDisplayContext();")
                        && runtimeSource.contains("requestChartSelectionReload();"));
        assertTrue("复用图表页任务栈时，onNewIntent 切换品种也应先失效旧上下文再请求，避免短时混图",
                source.contains("if (triggerReload) {\n            invalidateChartDisplayContext();\n            pageRuntime.requestChartSelectionReload();\n        }"));
        assertFalse("旧 Activity 不应继续手写“请求一次再重排自动刷新”的旧编排",
                source.contains("dataCoordinator.requestKlines(true, false);\n            if (pageRuntime != null) {\n                pageRuntime.scheduleNextAutoRefresh();\n            }"));
    }

    @Test
    public void overlaySignatureShouldBeBoundToChartKeyAndSessionSummary() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户叠加层签名应显式绑定当前图表 key，避免跨 symbol 或 interval 串用",
                source.contains("String chartDataKey = buildCacheKey(selectedSymbol, selectedInterval);"));
        assertTrue("图表页读取激活会话身份时应先消费显式会话摘要，而不是重复直接读 activeAccount",
                source.contains("SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();"));
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

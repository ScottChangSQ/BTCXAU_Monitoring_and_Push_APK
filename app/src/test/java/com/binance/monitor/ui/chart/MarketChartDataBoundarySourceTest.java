/*
 * 图表页数据边界源码约束测试，锁定 symbol+interval+session 切换时的显示上下文收口。
 */
package com.binance.monitor.ui.chart;

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

        assertTrue("切换产品前应先失效旧图表上下文，避免旧 K 线继续挂在新产品名下",
                source.contains("private void invalidateChartDisplayContext() {"));
        assertTrue("切换产品时应先失效旧上下文，再发起新请求",
                source.contains("selectedSymbol = symbol.trim().toUpperCase(Locale.ROOT);\n        syncSymbolSelector();\n        invalidateChartDisplayContext();\n        requestKlines();"));
        assertTrue("切换周期时也应先失效旧上下文，再发起新请求",
                source.contains("selectedInterval = option;\n        persistSelectedInterval();\n        updateIntervalButtons();\n        invalidateChartDisplayContext();\n        requestKlines();"));
        assertTrue("复用图表页任务栈时，onNewIntent 切换品种也应先失效旧上下文再请求，避免短时混图",
                source.contains("if (triggerReload) {\n            invalidateChartDisplayContext();\n            requestKlines();\n            scheduleNextAutoRefresh();\n        }"));
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

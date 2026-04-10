package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartAccountOverlaySourceTest {

    @Test
    public void updateAccountAnnotationsOverlayShouldKeepPreviousPositionsWhileSessionCacheIsNotReady() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("会话仍激活但缓存尚未就绪时，图表页不应先清空当前持仓再等待下一轮刷新",
                source.contains("if (sessionActive && (cache == null || snapshot == null)) {\n            return;\n        }"));
    }

    @Test
    public void setupChartPositionPanelShouldRestoreLatestCacheInsteadOfBindingEmptyStateFirst() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("图表页初次创建时应直接从最新账户缓存恢复持仓和标注，避免先显示空白",
                source.contains("restoreChartOverlayFromLatestCacheOrEmpty();"));
        assertTrue("图表页应提供专门的首帧账户叠加层恢复入口",
                source.contains("private void restoreChartOverlayFromLatestCacheOrEmpty() {"));
        assertTrue("内存缓存尚未回填时，图表页仍应保留本地持久化快照恢复能力",
                source.contains("private AccountSnapshot resolveChartOverlaySnapshot(")
                        && source.contains("scheduleStoredChartOverlayRestore();"));
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

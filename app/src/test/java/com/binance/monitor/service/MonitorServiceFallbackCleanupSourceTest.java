package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceFallbackCleanupSourceTest {

    @Test
    public void monitorServiceShouldUseV2InsteadOfLegacyChartBootstrapAndRestFallback() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("监控服务冷启动应通过 v2 市场序列建立展示快照",
                source.contains("gatewayV2Client.fetchMarketSeries("));
        assertFalse("监控服务不应再在冷启动阶段拉旧整窗 1m 历史",
                source.contains("fetchChartKlineFullWindow("));
        assertFalse("监控服务不应再把旧 1m 底稿写进图表历史库",
                source.contains("persistBootstrapMinuteHistory("));
        assertFalse("监控服务不应继续依赖旧 REST 最近收盘回退",
                source.contains("fetchLatestClosedKline("));
        assertFalse("监控服务不应再持有 ChartHistoryRepository 这一层旧图表落库职责",
                source.contains("ChartHistoryRepository"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MonitorService.java");
    }
}

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

        assertTrue("监控服务应通过 v2 stream 市场快照建立展示真值",
                source.contains("applyMarketSnapshotFromStream("));
        assertFalse("监控服务主链不应再按 stream 消息触发市场 REST 补拉",
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

    @Test
    public void monitorServiceShouldNotRunLocalStaleRecoveryOrForceFallbackReconnect() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("v2 stream 失效后不应再本地补拉账户主链",
                source.contains("refreshAccountFromV2IfStale(now);"));
        assertFalse("v2 stream 失效后不应再按本地 stale 判断补拉市场主链",
                source.contains("refreshStaleSymbolsFromV2(staleSymbols, now);"));
        assertFalse("v2 stream 失效后不应再强制重连 fallback ws",
                source.contains("fallbackKlineSocketManager.forceReconnect("));
        assertFalse("fallback 行情 WebSocket 不应再作为默认后台链路启动",
                source.contains("fallbackKlineSocketManager.connect("));
    }

    @Test
    public void fallbackKlineCallbackShouldNotWriteMainChainDisplayTruth() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("fallback ws 回调不应直接写入主链 K 线真值",
                source.contains("repository.updateDisplayKline(data);"));
        assertFalse("fallback ws 回调不应驱动主链异常判定",
                source.contains("handleClosedKline(data);"));
    }

    @Test
    public void connectionStatusShouldNotFallbackToLegacySocketOrTickMetadata() throws Exception {
        String serviceSource = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );
        String resolverSource = readUtf8(
                "app/src/main/java/com/binance/monitor/service/ConnectionStatusResolver.java",
                "src/main/java/com/binance/monitor/service/ConnectionStatusResolver.java"
        );

        assertFalse("第3步收口后，MonitorService 不应再维护旧 socketStates 状态",
                serviceSource.contains("socketStates"));
        assertFalse("第3步收口后，MonitorService 不应再维护旧 reconnectCounts 状态",
                serviceSource.contains("reconnectCounts"));
        assertFalse("第3步收口后，MonitorService 不应再用最近 tick 时间替代 v2 stream 健康度",
                serviceSource.contains("lastKlineTickAt"));
        assertFalse("连接状态解析不应再按 symbol/socket 元数据兜底",
                resolverSource.contains("isSymbolConnected("));
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

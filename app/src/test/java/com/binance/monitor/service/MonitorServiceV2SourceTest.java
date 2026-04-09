/*
 * 监控服务源码约束测试，确保统一同步层开始接入 v2 stream。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceV2SourceTest {

    @Test
    public void monitorServiceShouldDependOnGatewayV2StreamClient() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("监控服务应接入 GatewayV2StreamClient，统一消费 v2 stream",
                source.contains("GatewayV2StreamClient"));
        assertTrue("监控服务应启动 v2 stream 连接，而不是继续只依赖旧实时链路",
                source.contains("v2StreamClient.connect("));
        assertTrue("监控服务应先应用 stream 里的账户运行态，再按 history revision 拉历史",
                source.contains("accountStatsPreloadManager.applyPublishedAccountRuntime("));
        assertTrue("监控服务应在 history revision 前进时再拉账户历史",
                source.contains("requestAccountHistoryRefreshFromV2("));
        assertTrue("监控服务应在收到 market delta 时直接消费 stream 快照",
                source.contains("applyMarketSnapshotFromStream("));
        assertTrue("当 v2 stream 健康时，不应再让旧 WebSocket 超时检查主导重连",
                source.contains("if (isV2StreamHealthy(now)) {"));
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

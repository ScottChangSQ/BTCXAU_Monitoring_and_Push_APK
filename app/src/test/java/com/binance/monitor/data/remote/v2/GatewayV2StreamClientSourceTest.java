/*
 * v2 流客户端源码约束测试，确保统一流入口和断流状态口径保持唯一。
 */
package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GatewayV2StreamClientSourceTest {

    @Test
    public void sourceShouldNotFallbackToLoopbackStreamUrl() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java"
        );
        assertFalse("统一流入口不应再回退到本地 127.0.0.1",
                source.contains("ws://127.0.0.1:8787/v2/stream"));
    }

    @Test
    public void sourceShouldPublishStructuredReconnectingStateOnTermination() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java"
        );
        int reconnectingIndex = source.indexOf("ConnectionStage.RECONNECTING");
        int errorIndex = source.indexOf("notifyError(reason);");
        int reconnectIndex = source.indexOf("scheduleReconnect();");
        assertTrue("断流后必须先发布结构化 reconnecting 阶段", reconnectingIndex >= 0);
        assertTrue("断流后必须先发布 reconnecting，再上报错误", reconnectingIndex < errorIndex);
        assertTrue("断流后必须先发布 reconnecting，再安排重连", reconnectingIndex < reconnectIndex);
    }

    @Test
    public void sourceShouldSupportExplicitLifecycleRestartAndIgnoreStaleSocketCallbacks() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java"
        );

        assertTrue("前后台恢复时，stream 客户端应支持显式重建连接",
                source.contains("public synchronized void restart("));
        assertTrue("显式重建时应立即取消旧 socket，避免僵死连接继续占位",
                source.contains("current.cancel();"));
        assertTrue("旧 socket 的迟到断连回调不应再污染当前连接状态",
                source.contains("if (socket != terminatedSocket || activeConnectionId != connectionId) {"));
        assertTrue("监听器应接收结构化连接事件，而不是 boolean+message 拼装",
                source.contains("void onStateChanged(ConnectionEvent event);"));
    }

    @Test
    public void sourceShouldSendConfiguredGatewayTokenOnlyThroughGatewayHeader() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java"
        );

        assertTrue("v2 stream WebSocket 应复用统一网关鉴权 helper，避免各客户端继续散落 header 逻辑",
                source.contains("GatewayAuthRequestHelper"));
        assertTrue("v2 stream WebSocket 应通过统一 helper 挂载鉴权头",
                source.contains(".applyGatewayAuth(requestBuilder, configManager)"));
        assertFalse("v2 stream WebSocket 不应把 token 放进 URL query，避免掩盖配置不一致",
                source.contains("access_token") || source.contains("gateway_token") || source.contains("addQueryParameter"));
    }

    @Test
    public void sourceShouldUseSharedWsPingIntervalConstantInsteadOfHardcodedTwentySeconds() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java"
        );

        assertTrue(source.contains(".pingInterval(AppConstants.WS_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)"));
        assertFalse(source.contains(".pingInterval(20, TimeUnit.SECONDS)"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 GatewayV2StreamClient.java");
    }
}

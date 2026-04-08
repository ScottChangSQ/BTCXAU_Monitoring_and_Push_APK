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
    public void sourceShouldPublishDisconnectedStateBeforeReconnectOnTermination() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java"
        );
        int disconnectedIndex = source.indexOf("notifyState(false, reason);");
        int errorIndex = source.indexOf("notifyError(reason);");
        int reconnectIndex = source.indexOf("scheduleReconnect();");
        assertTrue("断流后必须先发布 disconnected 状态", disconnectedIndex >= 0);
        assertTrue("断流后必须先通知 disconnected，再上报错误", disconnectedIndex < errorIndex);
        assertTrue("断流后必须先通知 disconnected，再安排重连", disconnectedIndex < reconnectIndex);
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

package com.binance.monitor.data.remote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FallbackKlineSocketManagerSourceTest {

    @Test
    public void monitorServiceShouldNotStartFallbackKlineSocketByDefault() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("监控服务默认启动链不应再依赖 fallback K 线流管理器",
                source.contains("FallbackKlineSocketManager"));
        assertFalse("监控服务不应再启动 fallback K 线连接",
                source.contains("fallbackKlineSocketManager.connect("));
        assertFalse("监控服务里不应继续保留旧的主链式 WebSocketManager 字段命名",
                source.contains("private WebSocketManager webSocketManager;"));
    }

    @Test
    public void fallbackSocketTerminationShouldIgnoreStaleSocketCallbacks() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java",
                "src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("旧连接的 onClosed/onFailure 不应继续污染当前 socket 状态",
                source.contains("if (socket != terminatedSocket) {\n                return;\n            }\n            socket = null;"));
        assertTrue("只有当前连接终止后才应广播错误",
                source.contains("notifyErrorAll(reason);"));
        assertTrue("只有当前连接终止后才应进入重连计划",
                source.contains("scheduleReconnect(false);"));
    }

    @Test
    public void staleSocketTerminationShouldNotOverrideCurrentConnection() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java",
                "src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("旧连接晚到的 onClosed/onFailure 不应继续污染当前 socket 状态",
                source.contains("if (socket != terminatedSocket) {\n                return;\n            }\n            socket = null;"));
        assertFalse("终止回调不应在未校验当前 socket 身份时直接调度重连",
                source.contains("if (socket == terminatedSocket) {\n                socket = null;\n            }\n        }\n        notifyErrorAll(reason);\n        scheduleReconnect(false);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到源码文件");
    }
}

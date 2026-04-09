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

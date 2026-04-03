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
    public void monitorServiceShouldUseFallbackKlineSocketNaming() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("监控服务应明确依赖回退 K 线流管理器，而不是继续使用主链式命名",
                source.contains("FallbackKlineSocketManager"));
        assertTrue("旧 WebSocket 回调应明确表达自己是回退流状态",
                source.contains("onFallbackStreamStateChanged("));
        assertTrue("旧 WebSocket 回调应明确表达自己是回退 K 线更新",
                source.contains("onFallbackKlineUpdate("));
        assertTrue("旧 WebSocket 回调应明确表达自己是回退流错误",
                source.contains("onFallbackStreamError("));
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

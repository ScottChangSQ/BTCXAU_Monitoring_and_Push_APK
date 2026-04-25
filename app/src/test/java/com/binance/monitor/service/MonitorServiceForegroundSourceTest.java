/*
 * 前台服务源码约束测试，锁定启动入口和服务前台化调用。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceForegroundSourceTest {

    @Test
    public void controllerShouldUseStartForegroundServiceEntry() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorServiceController.java",
                "src/main/java/com/binance/monitor/service/MonitorServiceController.java"
        );

        assertTrue("MonitorServiceController 应使用 ContextCompat.startForegroundService 启动入口",
                source.contains("ContextCompat.startForegroundService("));
    }

    @Test
    public void serviceShouldInvokeStartForeground() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("MonitorService 应真正调用 startForeground 进入前台服务",
                source.contains("startForeground("));
    }

    @Test
    public void notificationHelperShouldRemoveLegacySuppressionHelpers() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/util/NotificationHelper.java",
                "src/main/java/com/binance/monitor/util/NotificationHelper.java"
        );

        assertFalse("NotificationHelper 不应保留旧的前台通知手动更新入口",
                source.contains("updateServiceNotification("));
        assertFalse("NotificationHelper 不应保留用于收起通知口径的 hasServiceNotification 旧入口",
                source.contains("hasServiceNotification()"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到目标源码文件");
    }
}

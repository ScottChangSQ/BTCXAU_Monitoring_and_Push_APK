/*
 * MonitorService 拆分源码约束测试，锁定前台通知与悬浮窗协调职责下沉。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceCoordinatorSourceTest {

    @Test
    public void monitorServiceShouldDelegateForegroundNotificationAndFloatingRefresh() throws Exception {
        assertTrue("前台通知协调器文件应存在",
                exists("app/src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java",
                        "src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java"));
        assertTrue("悬浮窗协调器文件应存在",
                exists("app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                        "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"));

        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );
        assertTrue("MonitorService 应持有前台通知协调器",
                source.contains("private MonitorForegroundNotificationCoordinator foregroundNotificationCoordinator;"));
        assertTrue("MonitorService 应持有悬浮窗协调器",
                source.contains("private MonitorFloatingCoordinator floatingCoordinator;"));
        assertTrue("MonitorService 初始化时应装配前台通知协调器",
                source.contains("foregroundNotificationCoordinator = new MonitorForegroundNotificationCoordinator("));
        assertTrue("MonitorService 初始化时应装配悬浮窗协调器",
                source.contains("floatingCoordinator = new MonitorFloatingCoordinator("));
        assertTrue("前台服务入口应委托给协调器",
                source.contains("foregroundNotificationCoordinator.ensureForeground("));
        assertTrue("前台通知刷新应委托给协调器",
                source.contains("foregroundNotificationCoordinator.refreshNotification("));
        assertTrue("悬浮窗配置应用应委托给协调器",
                source.contains("floatingCoordinator.applyPreferences();"));
        assertTrue("悬浮窗刷新节流应委托给协调器",
                source.contains("floatingCoordinator.requestRefresh("));
    }

    private static boolean exists(String... candidates) {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            if (Files.exists(workingDir.resolve(candidate).normalize())) {
                return true;
            }
        }
        return false;
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

package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ScreenOffAlertOnlySourceTest {

    @Test
    public void screenOffShouldStopFloatingRefreshAndKeepAlertOnly() throws Exception {
        String serviceSource = readUtf8("src/main/java/com/binance/monitor/service/MonitorService.java");
        String floatingSource = readUtf8("src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java");
        String streamSource = readUtf8("src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java");

        assertTrue(serviceSource.contains("private boolean shouldUseScreenOffAlertOnlyMode() {"));
        assertTrue(serviceSource.contains("return !deviceInteractive;"));
        assertTrue(serviceSource.contains("floatingCoordinator.setScreenInteractive(interactive);"));
        assertTrue(floatingSource.contains("void setScreenInteractive(boolean interactive) {"));
        assertTrue(floatingSource.contains("if (!interactive) {"));
        assertTrue(floatingSource.contains("cancelScheduledRefresh();"));
        assertTrue(streamSource.contains("熄屏 alert-only 由 MonitorService 在消费侧裁剪"));
    }

    private String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

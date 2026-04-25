package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceHeartbeatPolicySourceTest {

    @Test
    public void serviceShouldPassScreenInteractiveStateIntoHeartbeatPolicy() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/service/MonitorService.java");

        assertTrue(source.contains("return MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(\n                AppForegroundTracker.getInstance().isForeground(),\n                screenInteractive);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MonitorServiceParsingSourceTest {

    @Test
    public void monitorServiceShouldNotSilentlyIgnoreAbnormalPayloadParsingErrors() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/service/MonitorService.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("catch (Exception ignored)"));
    }
}

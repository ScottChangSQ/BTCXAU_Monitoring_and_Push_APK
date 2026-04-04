package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceSourceTest {

    @Test
    public void monitorServiceShouldNotKeepLegacyAlertDispatchLogic() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/service/MonitorService.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertFalse(source.contains("dispatchSyncedAlert("));
        assertFalse(source.contains("result.getAlerts()"));
    }
}

package com.binance.monitor.ui.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainActivityBridgeSourceTest {

    @Test
    public void mainActivityShouldBridgeToMainHostMarketMonitorTab() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/main/MainActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("import com.binance.monitor.ui.host.HostNavigationIntentFactory;"));
        assertTrue(source.contains("import com.binance.monitor.ui.host.HostTab;"));
        assertTrue(source.contains("startActivity(HostNavigationIntentFactory.forTab(this, HostTab.MARKET_MONITOR));"));
        assertTrue(source.contains("finish();"));
        assertFalse(source.contains("private ActivityMainBinding binding;"));
    }
}

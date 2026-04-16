package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SettingsActivitySourceTest {

    @Test
    public void settingsActivityShouldBridgeToMainHostSettingsTab() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("import com.binance.monitor.ui.host.HostNavigationIntentFactory;"));
        assertTrue(source.contains("import com.binance.monitor.ui.host.HostTab;"));
        assertTrue(source.contains("startActivity(HostNavigationIntentFactory.forTab(this, HostTab.SETTINGS));"));
        assertTrue(source.contains("finish();"));
        assertFalse(source.contains("private ActivitySettingsBinding binding;"));
    }
}

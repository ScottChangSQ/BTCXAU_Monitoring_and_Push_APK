package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SettingsActivitySourceTest {

    @Test
    public void settingsActivityShouldRenderStandaloneSettingsHome() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private ContentSettingsBinding binding;"));
        assertTrue(source.contains("private SettingsPageController pageController;"));
        assertTrue(source.contains("binding = ContentSettingsBinding.inflate(getLayoutInflater());"));
        assertTrue(source.contains("setContentView(binding.getRoot());"));
        assertTrue(source.contains("pageController = new SettingsPageController("));
        assertFalse(source.contains("HostNavigationIntentFactory.forTab(this, HostTab.SETTINGS)"));
    }

    @Test
    public void settingsActivityShouldExposeTradeSectionConstant() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public static final String SECTION_TRADE = \"trade\";"));
    }
}

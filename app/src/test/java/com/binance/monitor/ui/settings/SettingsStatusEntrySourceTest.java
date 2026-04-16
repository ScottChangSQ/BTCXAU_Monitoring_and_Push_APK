package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SettingsStatusEntrySourceTest {

    @Test
    public void settingsPageShouldExposeDiagnosticsAndConnectionEntry() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_settings.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String activity = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String controller = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String section = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("连接与诊断"));
        assertTrue(layout.contains("@+id/itemDiagnostics"));
        assertTrue(activity.contains("SECTION_DIAGNOSTICS = \"diagnostics\""));
        assertTrue(controller.contains("binding.itemDiagnostics.setOnClickListener"));
        assertTrue(section.contains("binding.cardDiagnosticsSection.setVisibility(SettingsActivity.SECTION_DIAGNOSTICS.equals(sectionKey) ? View.VISIBLE : View.GONE);"));
    }
}

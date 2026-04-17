package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsTabNavigationSourceTest {

    @Test
    public void settingsPagesShouldNotKeepLegacyBottomTabNavigation() throws Exception {
        String homeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String sectionSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(homeSource.contains("openMarketMonitor()"));
        assertFalse(homeSource.contains("openMarketChart()"));
        assertFalse(homeSource.contains("openAccountStats()"));
        assertFalse(homeSource.contains("openAccountPosition()"));
        assertFalse(sectionSource.contains("private void setupBottomNav()"));
        assertFalse(sectionSource.contains("private void updateBottomTabs()"));
        assertFalse(sectionSource.contains("openSettingsHome()"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 SettingsActivity 源码");
    }
}

package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainHostBottomTabSourceTest {

    @Test
    public void mainHostShouldUseProjectTextTabsInsteadOfBottomNavigationView() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_main_host.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/MainHostActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/tabMarketMonitor"));
        assertTrue(layout.contains("@+id/tabMarketChart"));
        assertTrue(layout.contains("@+id/tabAccountPosition"));
        assertTrue(layout.contains("@+id/tabAccountStats"));
        assertTrue(layout.contains("@+id/tabSettings"));
        assertTrue(source.contains("BottomTabVisibilityManager.apply("));
        assertTrue(source.contains("UiPaletteManager.styleBottomNavTab("));
        assertTrue(source.contains("tabMarketMonitor.setOnClickListener"));
        assertTrue(source.contains("tabMarketChart.setOnClickListener"));
    }
}

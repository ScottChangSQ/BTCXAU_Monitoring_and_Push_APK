package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainHostLayoutResourceTest {

    @Test
    public void activityMainHostShouldExposeFragmentContainerAndBottomNav() throws Exception {
        String xml = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_main_host.xml")
        ), StandardCharsets.UTF_8);

        assertTrue(xml.contains("@+id/hostFragmentContainer"));
        assertTrue(xml.contains("@+id/hostBottomNavigation"));
        assertTrue(xml.contains("@+id/tabMarketMonitor"));
        assertTrue(xml.contains("@+id/tabMarketChart"));
        assertTrue(xml.contains("@+id/tabAccountPosition"));
        assertTrue(xml.contains("@+id/tabAccountStats"));
        assertTrue(xml.contains("@+id/tabSettings"));
    }
}

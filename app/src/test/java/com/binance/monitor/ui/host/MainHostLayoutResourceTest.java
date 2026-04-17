package com.binance.monitor.ui.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainHostLayoutResourceTest {

    @Test
    public void activityMainHostShouldExposeFragmentContainerAndThreeTabBottomNav() throws Exception {
        String xml = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_main_host.xml")
        ), StandardCharsets.UTF_8);

        assertTrue(xml.contains("@+id/hostFragmentContainer"));
        assertTrue(xml.contains("@+id/hostBottomNavigation"));
        assertTrue(xml.contains("@+id/tabTrading"));
        assertTrue(xml.contains("@+id/tabAccount"));
        assertTrue(xml.contains("@+id/tabAnalysis"));
        assertFalse(xml.contains("@+id/tabSettings"));
        assertFalse(xml.contains("@+id/tabMarketMonitor"));
        assertFalse(xml.contains("@+id/tabMarketChart"));
        assertFalse(xml.contains("@+id/tabAccountPosition"));
        assertFalse(xml.contains("@+id/tabAccountStats"));
    }
}

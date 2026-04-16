package com.binance.monitor.ui.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainActivityPageControllerSourceTest {

    @Test
    public void mainActivityShouldDelegatePageLifecycleToController() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/main/MainActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("import com.binance.monitor.ui.market.MarketMonitorPageController;"));
        assertTrue(source.contains("import com.binance.monitor.ui.market.MarketMonitorPageHostDelegate;"));
        assertTrue(source.contains("import com.binance.monitor.ui.market.MarketMonitorPageRuntime;"));
        assertTrue(source.contains("private MarketMonitorPageController pageController;"));
        assertTrue(source.contains("private MarketMonitorPageRuntime pageRuntime;"));
        assertTrue(source.contains("pageController = new MarketMonitorPageController("));
        assertTrue(source.contains("new MarketMonitorPageHostDelegate("));
        assertTrue(source.contains("pageRuntime = new MarketMonitorPageRuntime("));
        assertTrue(source.contains("pageController.bind();"));
        assertTrue(source.contains("pageController.onPageShown();"));
        assertTrue(source.contains("pageController.onPageHidden();"));
        assertTrue(source.contains("pageController.onDestroy();"));
        assertFalse(source.contains("private void setupBottomNav()"));
        assertFalse(source.contains("private void updateBottomTabs("));
        assertFalse(source.contains("private void styleNavTab("));
    }
}

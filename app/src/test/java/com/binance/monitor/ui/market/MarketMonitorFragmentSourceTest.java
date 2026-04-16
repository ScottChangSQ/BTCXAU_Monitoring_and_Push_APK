package com.binance.monitor.ui.market;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketMonitorFragmentSourceTest {

    @Test
    public void marketMonitorFragmentShouldDelegateLifecycleToPageController() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/market/MarketMonitorFragment.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("MarketMonitorPageHostDelegate"));
        assertTrue(source.contains("MarketMonitorPageRuntime"));
        assertTrue(source.contains("import com.binance.monitor.databinding.ActivityMainBinding;"));
        assertTrue(source.contains("private MarketMonitorPageController pageController;"));
        assertTrue(source.contains("private MarketMonitorPageRuntime pageRuntime;"));
        assertTrue(source.contains("pageController = new MarketMonitorPageController("));
        assertTrue(source.contains("new MarketMonitorPageHostDelegate("));
        assertTrue(source.contains("pageRuntime = new MarketMonitorPageRuntime("));
        assertTrue(source.contains("View monitorContentView = ((ViewGroup) view).getChildAt(0);"));
        assertTrue(source.contains("ActivityMainBinding.bind(monitorContentView)"));
        assertTrue(source.contains("new MarketMonitorPageController.BottomNavBinding("));
        assertTrue(source.contains("public void onHostPageShown() {\n        if (pageController != null) {\n            pageController.onPageShown();"));
        assertTrue(source.contains("public void onHostPageHidden() {\n        if (pageController != null) {\n            pageController.onPageHidden();"));
        assertTrue(source.contains("public void onDestroyView() {\n        if (pageController != null) {\n            pageController.onDestroy();"));
    }
}

package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartFragmentSourceTest {

    @Test
    public void marketChartFragmentShouldDelegateLifecycleToPageController() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("MarketChartPageHostDelegate"));
        assertTrue(source.contains("MarketChartPageRuntime"));
        assertTrue(source.contains("import com.binance.monitor.databinding.ActivityMarketChartBinding;"));
        assertTrue(source.contains("private MarketChartPageController pageController;"));
        assertTrue(source.contains("private MarketChartPageRuntime pageRuntime;"));
        assertTrue(source.contains("private MarketChartScreen screen;"));
        assertTrue(source.contains("pageController = new MarketChartPageController("));
        assertTrue(source.contains("new MarketChartPageHostDelegate("));
        assertTrue(source.contains("pageRuntime = new MarketChartPageRuntime("));
        assertTrue(source.contains("View chartContentView = ((ViewGroup) view).getChildAt(0);"));
        assertTrue(source.contains("ActivityMarketChartBinding.bind(chartContentView)"));
        assertTrue(source.contains("screen = new MarketChartScreen("));
        assertTrue(source.contains("screen.attachPageRuntime(pageRuntime);"));
        assertTrue(source.contains("new MarketChartPageController.BottomNavBinding("));
        assertTrue(source.contains("pageController.onColdStart();"));
        assertTrue(source.contains("public void onHostPageShown() {"));
        assertTrue(source.contains("screen.onHostPageShown();"));
        assertTrue(source.contains("screen.onNewIntent(requireActivity().getIntent());"));
        assertTrue(source.contains("pageController.onPageShown();"));
        assertTrue(source.contains("public void onHostPageHidden() {"));
        assertTrue(source.contains("screen.onHostPageHidden();"));
        assertTrue(source.contains("pageController.onPageHidden();"));
        assertTrue(source.contains("public void onDestroyView() {\n        if (pageController != null) {\n            pageController.onDestroy();"));
    }
}

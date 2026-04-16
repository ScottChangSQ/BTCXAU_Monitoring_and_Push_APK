package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SettingsFragmentSourceTest {

    @Test
    public void settingsFragmentShouldDelegateLifecycleToPageController() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/settings/SettingsFragment.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("import com.binance.monitor.databinding.ContentSettingsBinding;"));
        assertTrue(source.contains("private SettingsPageController pageController;"));
        assertTrue(source.contains("pageController = new SettingsPageController("));
        assertTrue(source.contains("View settingsContentView = ((ViewGroup) view).getChildAt(0);"));
        assertTrue(source.contains("ContentSettingsBinding.bind(settingsContentView)"));
        assertTrue(source.contains("public void onHostPageShown() {\n        if (pageController != null) {\n            pageController.onPageShown();"));
        assertTrue(source.contains("public void onHostPageHidden() {\n        if (pageController != null) {\n            pageController.onPageHidden();"));
        assertTrue(source.contains("public void onDestroyView() {\n        if (pageController != null) {\n            pageController.onDestroy();"));
    }
}

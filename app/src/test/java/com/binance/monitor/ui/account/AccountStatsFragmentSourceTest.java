package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsFragmentSourceTest {

    @Test
    public void accountStatsFragmentShouldDelegateLifecycleToPageController() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("import com.binance.monitor.databinding.ContentAccountStatsBinding;"));
        assertTrue(source.contains("AccountStatsPageHostDelegate"));
        assertTrue(source.contains("AccountStatsPageRuntime"));
        assertTrue(source.contains("ContentAccountStatsBinding.bind(view)"));
        assertTrue(source.contains("private AccountStatsPageController pageController;"));
        assertTrue(source.contains("private AccountStatsPageRuntime pageRuntime;"));
        assertTrue(source.contains("private AccountStatsScreen screen;"));
        assertTrue(source.contains("pageController = new AccountStatsPageController("));
        assertTrue(source.contains("new AccountStatsPageHostDelegate("));
        assertTrue(source.contains("pageRuntime = new AccountStatsPageRuntime("));
        assertTrue(source.contains("screen = new AccountStatsScreen("));
        assertTrue(source.contains("screen.attachPageRuntime(pageRuntime);"));
        assertTrue(source.contains("pageController.onColdStart();"));
        assertTrue(source.contains("public void onHostPageShown() {"));
        assertTrue(source.contains("screen.onNewIntent(requireActivity().getIntent());"));
        assertTrue(source.contains("pageController.onPageShown();"));
        assertTrue(source.contains("public void onHostPageHidden() {\n        if (pageController != null) {\n            pageController.onPageHidden();"));
        assertTrue(source.contains("public void onDestroyView() {\n        if (pageController != null) {\n            pageController.onDestroy();"));
    }
}

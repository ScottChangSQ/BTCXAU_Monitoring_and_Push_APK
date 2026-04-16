package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountPositionFragmentSourceTest {

    @Test
    public void accountPositionFragmentShouldDelegateLifecycleToPageController() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("import com.binance.monitor.databinding.ContentAccountPositionBinding;"));
        assertTrue(source.contains("ContentAccountPositionBinding.bind(view)"));
        assertTrue(source.contains("private AccountPositionPageController pageController;"));
        assertTrue(source.contains("pageController = new AccountPositionPageController("));
        assertTrue(source.contains("public void onHostPageShown() {\n        if (pageController != null) {\n            pageController.onPageShown();"));
        assertTrue(source.contains("public void onHostPageHidden() {\n        if (pageController != null) {\n            pageController.onPageHidden();"));
        assertTrue(source.contains("public void onDestroyView() {\n        if (pageController != null) {\n            pageController.onDestroy();"));
    }
}

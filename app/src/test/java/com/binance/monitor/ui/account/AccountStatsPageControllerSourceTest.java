package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsPageControllerSourceTest {

    @Test
    public void accountStatsPageControllerShouldOwnColdStartAndDestroyOrchestration() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPageController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public void onColdStart() {\n        host.onColdStart();"));
        assertTrue(source.contains("public void onPageShown() {\n        host.onPageShown();"));
        assertTrue(source.contains("public void onPageHidden() {\n        host.onPageHidden();"));
        assertTrue(source.contains("public void onDestroy() {\n        host.onPageDestroyed();"));
    }
}

package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsEmptyStateSourceTest {

    @Test
    public void accountStatsShouldExposeVisibleEmptyStateInsteadOfBlankPage() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_account_stats.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/cardStatsEmptyState"));
        assertTrue(layout.contains("@+id/layoutStatsEmptyChartPlaceholder"));
        assertTrue(layout.contains("@+id/tvStatsEmptyReturnHint"));
        assertTrue(layout.contains("@+id/tvStatsEmptyTradeHint"));
        assertTrue(source.contains("private void updateEmptyStateVisibility() {"));
        assertTrue(source.contains("binding.cardStatsEmptyState.setVisibility("));
    }
}

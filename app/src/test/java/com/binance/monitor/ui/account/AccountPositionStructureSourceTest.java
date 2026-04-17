package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountPositionStructureSourceTest {

    @Test
    public void accountPageShouldExposeHistoryEntryAndSummaryOnlyPositionSection() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_account_position.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/cardHistorySection"));
        assertTrue(layout.contains("@+id/btnOpenAccountHistory"));
        assertFalse(layout.contains("@+id/tvHistorySummary"));
        assertTrue(source.contains("binding.btnOpenAccountHistory.setOnClickListener(v -> openTradeHistorySheet())"));
        assertTrue(source.contains("tradeHistoryBottomSheetController.show(currentTradeHistory);"));
        assertTrue(source.contains("binding.recyclerPositionAggregates.setVisibility(View.GONE);"));
    }
}

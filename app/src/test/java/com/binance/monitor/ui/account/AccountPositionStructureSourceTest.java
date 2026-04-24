package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountPositionStructureSourceTest {

    @Test
    public void accountPageShouldExposeHistoryEntryAndPositionAggregatesAboveDetails() throws Exception {
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
        assertTrue(source.contains("List<PositionAggregateItem> aggregates = model.getPositionAggregates();"));
        assertTrue(source.contains("binding.tvPositionAggregateTitle.setVisibility(aggregates.isEmpty() ? View.GONE : View.VISIBLE);"));
        assertTrue(source.contains("binding.recyclerPositionAggregates.setVisibility(aggregates.isEmpty() ? View.GONE : View.VISIBLE);"));
        assertFalse(source.contains("binding.tvPositionAggregateTitle.setVisibility(View.GONE);"));
        assertFalse(source.contains("binding.recyclerPositionAggregates.setVisibility(View.GONE);"));
    }

    @Test
    public void accountPageShouldRouteTriggersAndHistoryFiltersThroughStandardSubjects() throws Exception {
        String pageSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String historySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountTradeHistoryBottomSheetController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(pageSource.contains("UiPaletteManager.styleTextTrigger(\n                binding.btnOpenAccountHistory,"));
        assertTrue(pageSource.contains("UiPaletteManager.styleTextTrigger(\n                    binding.tvAccountConnectionStatus,"));
        assertTrue(pageSource.contains("UiPaletteManager.styleActionButton(\n                binding.btnAccountBatchActions,"));
        assertFalse(pageSource.contains("binding.btnOpenAccountHistory.setBackground(UiPaletteManager.createOutlinedDrawable("));
        assertFalse(pageSource.contains("binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createFilledDrawable("));
        assertFalse(pageSource.contains("binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createOutlinedDrawable("));

        assertTrue(historySource.contains("UiPaletteManager.styleSelectFieldLabel("));
        assertTrue(historySource.contains("spinner.setBackground(null);"));
        assertFalse(historySource.contains("spinner.setBackground(UiPaletteManager.createOutlinedDrawable("));
        assertFalse(historySource.contains("labelView.setBackground(UiPaletteManager.createOutlinedDrawable("));
    }
}

package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountTradeHistoryBottomSheetSourceTest {

    @Test
    public void accountHistoryEntryShouldOpenBottomSheetInsteadOfAnalysisTab() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");

        assertTrue(source.contains("private AccountTradeHistoryBottomSheetController tradeHistoryBottomSheetController;"));
        assertTrue(source.contains("tradeHistoryBottomSheetController = new AccountTradeHistoryBottomSheetController(host.requireActivity());"));
        assertTrue(source.contains("binding.btnOpenAccountHistory.setOnClickListener(v -> openTradeHistorySheet());"));
        assertTrue(source.contains("private void openTradeHistorySheet() {"));
        assertTrue(source.contains("tradeHistoryBottomSheetController.show(currentTradeHistory);"));
        assertFalse(source.contains("binding.btnOpenAccountHistory.setOnClickListener(v -> host.openAccountStats())"));
    }

    @Test
    public void accountHistoryBottomSheetShouldExposeProductSideAndTimeFilters() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountTradeHistoryBottomSheetController.java");
        String layout = readUtf8("src/main/res/layout/dialog_account_trade_history_sheet.xml");

        assertTrue(source.contains("FILTER_PRODUCT = \"全部产品\""));
        assertTrue(source.contains("FILTER_SIDE = \"全部方向\""));
        assertTrue(source.contains("FILTER_SIDE_BUY = \"买入\""));
        assertTrue(source.contains("FILTER_SIDE_SELL = \"卖出\""));
        assertTrue(source.contains("SORT_TIME_DESC = \"时间倒序\""));
        assertTrue(source.contains("SORT_TIME_ASC = \"时间正序\""));
        assertTrue(source.contains("AccountDeferredSnapshotRenderHelper.buildFilteredTrades(baseTrades, request)"));
        assertTrue(source.contains("AccountDeferredSnapshotRenderHelper.SortMode.CLOSE_TIME"));
        assertTrue(source.contains("UiPaletteManager.applyBottomSheetSurface(dialog, palette);"));
        assertTrue(source.contains("binding.recyclerTradeHistory.setBackground("));
        assertTrue(source.contains("UiPaletteManager.createSectionBackground(activity, palette.surfaceEnd, palette.stroke)"));
        assertTrue(source.contains("styleFilterField(binding.spinnerTradeHistoryProduct, binding.tvTradeHistoryProductLabel, palette);"));
        assertTrue(source.contains("spinner.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.card, palette.stroke));"));
        assertTrue(source.contains("labelView.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.card, palette.stroke));"));

        assertTrue(layout.contains("@+id/spinnerTradeHistoryProduct"));
        assertTrue(layout.contains("@+id/spinnerTradeHistorySide"));
        assertTrue(layout.contains("@+id/spinnerTradeHistorySort"));
        assertTrue(layout.contains("@+id/recyclerTradeHistory"));
        assertTrue(layout.contains("@+id/tvTradeHistoryEmpty"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

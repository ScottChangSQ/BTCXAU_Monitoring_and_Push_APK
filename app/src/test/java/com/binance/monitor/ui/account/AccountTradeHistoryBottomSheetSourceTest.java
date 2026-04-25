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
        assertTrue(source.contains("spinner.setBackground(null);"));
        assertTrue(source.contains("UiPaletteManager.styleSelectFieldLabel("));

        assertTrue(layout.contains("@+id/spinnerTradeHistoryProduct"));
        assertTrue(layout.contains("@+id/spinnerTradeHistorySide"));
        assertTrue(layout.contains("@+id/spinnerTradeHistorySort"));
        assertTrue(layout.contains("@+id/recyclerTradeHistory"));
        assertTrue(layout.contains("@+id/tvTradeHistoryEmpty"));
        assertTrue(layout.contains("@style/Widget.BinanceMonitor.Subject.SelectField.Label"));
        assertFalse(layout.contains("Widget.BinanceMonitor.Spinner.Label"));
        assertFalse(layout.contains("@drawable/bg_spinner_filter"));
    }

    @Test
    public void accountHistoryBottomSheetShouldManageActiveDialogAcrossPageLifecycle() throws Exception {
        String controllerSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountTradeHistoryBottomSheetController.java");
        String pageSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");

        assertTrue(controllerSource.contains("private BottomSheetDialog activeDialog;"));
        assertTrue(controllerSource.contains("private DialogAccountTradeHistorySheetBinding activeBinding;"));
        assertTrue(controllerSource.contains("private TradeRecordAdapterV2 activeTradeAdapter;"));
        assertTrue(controllerSource.contains("private List<TradeRecordItem> pendingShowTrades;"));
        assertTrue(controllerSource.contains("if (tryUpdateVisibleDialog(baseTrades)) {"));
        assertTrue(controllerSource.contains("pendingShowTrades = baseTrades;"));
        assertTrue(controllerSource.contains("List<TradeRecordItem> pendingTrades = consumePendingShowTrades();"));
        assertTrue(controllerSource.contains("private boolean canShowDialogNow() {"));
        assertTrue(controllerSource.contains("dismissActiveDialog();"));
        assertTrue(controllerSource.contains("dialog.setOnDismissListener("));
        assertTrue(controllerSource.contains("public void dismiss() {"));
        assertTrue(controllerSource.contains("if (activeDialog != null && !activeDialog.isShowing()) {"));
        assertTrue(controllerSource.contains("clearInactiveDialogReference();"));
        assertTrue(controllerSource.contains("private void clearInactiveDialogReference() {"));
        assertTrue(pageSource.contains("tradeHistoryBottomSheetController.dismiss();"));
    }

    @Test
    public void accountHistoryEntryStateShouldRebindFromLatestHistorySnapshot() throws Exception {
        String pageSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");

        assertTrue(pageSource.contains("currentTradeHistory = Collections.unmodifiableList(new ArrayList<>(tradeHistory));"));
        assertTrue(pageSource.contains("bindHistorySection(tradeHistory);"));
        assertTrue(pageSource.contains("currentTradeHistory = Collections.emptyList();"));
        assertTrue(pageSource.contains("bindHistorySection(Collections.emptyList());"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

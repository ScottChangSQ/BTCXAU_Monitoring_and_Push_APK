package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartTradeSourceTest {

    @Test
    public void chartActivityShouldWireTradeCoordinatorIntoChartPanel() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("GatewayV2TradeClient"));
        assertTrue(source.contains("TradeExecutionCoordinator"));
        assertTrue(source.contains("chartPositionAdapter.setActionListener"));
        assertTrue(source.contains("chartPendingOrderAdapter.setActionListener"));
        assertTrue(source.contains("binding.btnChartTradeBuy.setOnClickListener"));
        assertTrue(source.contains("binding.btnChartTradeSell.setOnClickListener"));
        assertTrue(source.contains("binding.btnChartTradePending.setOnClickListener"));
        assertTrue(source.contains("tradeExecutionCoordinator.prepareExecution("));
        assertTrue(source.contains("tradeExecutionCoordinator.submitAfterConfirmation("));
        assertTrue(source.contains("applyDialogSurface(dialog);"));
    }

    @Test
    public void chartActivityShouldBuildAllPhaseOneTradeCommands() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("TradeCommandFactory.openMarket("));
        assertTrue(source.contains("TradeCommandFactory.pendingAdd("));
        assertTrue(source.contains("TradeCommandFactory.closePosition("));
        assertTrue(source.contains("TradeCommandFactory.pendingCancel("));
        assertTrue(source.contains("TradeCommandFactory.modifyTpSl("));
        assertTrue(source.contains("MarketChartTradeSupport.toTradeSymbol("));
        assertTrue(source.contains("MarketChartTradeSupport.resolveReferencePrice("));
        assertTrue(source.contains("将按服务器实时价执行"));
    }

    @Test
    public void chartActivityShouldNotBlockModifyTpSlOnlyBecauseTicketOrReferencePriceIsMissing() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertFalse(source.contains("当前持仓缺少 positionTicket，无法改单"));
        assertFalse(source.contains("缺少参考价格，暂时不能改单"));
    }

    @Test
    public void chartActivityShouldShowAcceptedTitleWhenTradeIsAcceptedButNotSettled() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("交易已受理"));
        assertTrue(source.contains("getStateMachine().getStep() == TradeCommandStateMachine.Step.ACCEPTED"));
    }

    @Test
    public void tradeDialogLayoutShouldUseDedicatedContainerAndLargerFieldHeight() throws Exception {
        Path file = Paths.get("src/main/res/layout/dialog_trade_command.xml");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("NestedScrollView") || source.contains("ScrollView"));
        assertTrue(source.contains("@drawable/bg_trade_dialog_surface"));
        assertTrue(source.contains("@dimen/control_height_lg"));
        assertTrue(source.contains("android:id=\"@+id/tvTradeCommandHint\"\n            android:layout_width=\"match_parent\"\n            android:layout_height=\"wrap_content\"\n            android:textColor=\"@color/text_primary\""));
    }

    @Test
    public void pendingOrderTypeSpinnerShouldUseProjectStyledAdapterLayouts() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertFalse(source.contains("android.R.layout.simple_spinner_item"));
        assertFalse(source.contains("android.R.layout.simple_spinner_dropdown_item"));
        assertTrue(source.contains("dialogBinding.spinnerTradeOrderType.setAdapter("));
    }

    @Test
    public void tradeDialogActionTextsShouldUseWhiteTextForDarkDialogSurface() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private ArrayAdapter<String> createTradeActionMenuAdapter(String[] actions) {"));
        assertTrue(source.contains(".setAdapter(createTradeActionMenuAdapter(actions), (menuDialog, which) -> {"));
        assertTrue(source.contains("textView.setTextColor(Color.WHITE);"));
        assertTrue(source.contains("private void styleTradeOrderTypeSpinnerItem(@Nullable View view) {"));
    }

    @Test
    public void positionRowLayoutShouldExposeVisibleActionButton() throws Exception {
        Path file = Paths.get("src/main/res/layout/item_position.xml");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("btnPositionAction"));
        assertFalse(source.contains("tvExpandHint"));
    }

    @Test
    public void chartPositionPanelShouldFilterRowsByCurrentChartSymbolLikeAnnotations() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (!matchesSelectedSymbol(item.getCode(), item.getProductName())) {"));
    }
}

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
    public void chartActivityShouldWireTradeCoordinatorIntoThreeActionButtons() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String coordinatorSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");

        assertTrue(activitySource.contains("private MarketChartTradeDialogCoordinator tradeDialogCoordinator;"));
        assertTrue(activitySource.contains("public static final String EXTRA_TRADE_ACTION = \"extra_trade_action\";"));
        assertTrue(activitySource.contains("public static final String EXTRA_TRADE_ACTION_CLOSE_POSITION = \"close_position\";"));
        assertTrue(activitySource.contains("public static final String EXTRA_TRADE_ACTION_MODIFY_POSITION = \"modify_position\";"));
        assertTrue(activitySource.contains("public static final String EXTRA_TRADE_ACTION_MODIFY_PENDING = \"modify_pending\";"));
        assertTrue(activitySource.contains("public static final String EXTRA_TRADE_ACTION_CANCEL_PENDING = \"cancel_pending\";"));
        assertTrue(activitySource.contains("tradeDialogCoordinator = new MarketChartTradeDialogCoordinator("));
        assertTrue(activitySource.contains("binding.btnChartTradeBuy.setOnClickListener"));
        assertTrue(activitySource.contains("binding.btnChartTradeSell.setOnClickListener"));
        assertTrue(activitySource.contains("binding.btnChartTradePending.setOnClickListener"));
        assertTrue(activitySource.contains("consumePendingTradeActionIfNeeded();"));
        assertFalse(activitySource.contains("chartPositionAdapter.setActionListener"));
        assertFalse(activitySource.contains("chartPendingOrderAdapter.setActionListener"));
        assertTrue(coordinatorSource.contains("tradeExecutionCoordinator.prepareExecution("));
        assertTrue(coordinatorSource.contains("tradeExecutionCoordinator.submitAfterConfirmation("));
        assertTrue(coordinatorSource.contains("applyDialogSurface(dialog);"));
    }

    @Test
    public void chartTradeCoordinatorShouldStillOwnCanonicalTradeCommandBuilders() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");

        assertTrue(source.contains("TradeCommandFactory.openMarket("));
        assertTrue(source.contains("TradeCommandFactory.pendingAdd("));
        assertTrue(source.contains("TradeCommandFactory.pendingModify("));
        assertTrue(source.contains("TradeCommandFactory.closePosition("));
        assertTrue(source.contains("TradeCommandFactory.pendingCancel("));
        assertTrue(source.contains("TradeCommandFactory.modifyTpSl("));
        assertTrue(source.contains("MarketChartTradeSupport.toTradeSymbol("));
        assertTrue(source.contains("MarketChartTradeSupport.resolveReferencePrice("));
        assertTrue(source.contains("if (action == ChartTradeAction.CLOSE_POSITION) {"));
        assertTrue(source.contains("if (action == ChartTradeAction.PENDING_MODIFY) {"));
        assertTrue(source.contains("if (action == ChartTradeAction.PENDING_CANCEL) {"));
        assertTrue(source.contains("将按服务器实时价执行"));
    }

    @Test
    public void chartTradeCoordinatorShouldShowAcceptedTitleWhenTradeIsAcceptedButNotSettled() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");

        assertTrue(source.contains("交易已受理"));
        assertTrue(source.contains("getStateMachine().getStep() == TradeCommandStateMachine.Step.ACCEPTED"));
    }

    @Test
    public void tradeDialogLayoutShouldUseDedicatedContainerAndStyledSpinner() throws Exception {
        String layoutSource = readUtf8("src/main/res/layout/dialog_trade_command.xml");
        String coordinatorSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(layoutSource.contains("NestedScrollView") || layoutSource.contains("ScrollView"));
        assertTrue(layoutSource.contains("@drawable/bg_trade_dialog_surface"));
        assertTrue(layoutSource.contains("@dimen/control_height_lg"));
        assertFalse(coordinatorSource.contains("android.R.layout.simple_spinner_item"));
        assertFalse(coordinatorSource.contains("android.R.layout.simple_spinner_dropdown_item"));
        assertTrue(coordinatorSource.contains("dialogBinding.spinnerTradeOrderType.setAdapter("));
        assertTrue(coordinatorSource.contains("private void styleTradeOrderTypeSpinnerItem(@Nullable View view) {"));
    }

    @Test
    public void tradeCallbacksShouldGuardLifecycleAndCancelOutstandingTasks() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String coordinatorSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(coordinatorSource.contains("private Future<?> tradePrepareTask;"));
        assertTrue(coordinatorSource.contains("private Future<?> tradeSubmitTask;"));
        assertTrue(coordinatorSource.contains("private boolean canPresentTradeUi() {"));
        assertTrue(coordinatorSource.contains("tradePrepareTask = ioExecutor.submit(() -> {"));
        assertTrue(coordinatorSource.contains("tradeSubmitTask = ioExecutor.submit(() -> {"));
        assertTrue(activitySource.contains("tradeDialogCoordinator.cancelTradeTasks();"));
        assertTrue(coordinatorSource.contains("if (!canPresentTradeUi()) {\n            tradeFlowRunning = false;\n            return;\n        }"));
    }

    @Test
    public void pendingIntentTradeActionShouldWaitForCacheReadyInsteadOfBeingConsumedPrematurely() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(activitySource.contains("private final AccountStatsPreloadManager.CacheListener accountCacheListener = cache -> {\n        if (pageRuntime != null) {\n            pageRuntime.scheduleChartOverlayRefresh();\n        }\n        consumePendingTradeActionIfNeeded();\n    };"));
        assertTrue(activitySource.contains("if (snapshot == null) {\n            return;\n        }"));
        assertTrue(activitySource.contains("if (targetItem == null) {\n            Toast.makeText(this, \"未找到目标持仓或挂单，暂时不能执行该操作\", Toast.LENGTH_SHORT).show();\n            lastConsumedTradeActionToken = token;\n            return;\n        }"));
    }

    @Test
    public void targetSymbolFromExternalTradeShortcutShouldBeNormalizedToMarketSymbol() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(activitySource.contains("import com.binance.monitor.util.ProductSymbolMapper;"));
        assertTrue(activitySource.contains("return ProductSymbolMapper.toMarketSymbol(raw);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

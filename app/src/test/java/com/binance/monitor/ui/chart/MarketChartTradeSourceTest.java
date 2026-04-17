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
    public void chartScreenShouldWireTradeCoordinatorAndQuickTradeBar() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String coordinatorSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");

        assertTrue(screenSource.contains("private MarketChartTradeDialogCoordinator tradeDialogCoordinator;"));
        assertTrue(screenSource.contains("private ChartQuickTradeCoordinator chartQuickTradeCoordinator;"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION = \"extra_trade_action\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_CLOSE_POSITION = \"close_position\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_MODIFY_POSITION = \"modify_position\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_MODIFY_PENDING = \"modify_pending\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_CANCEL_PENDING = \"cancel_pending\";"));
        assertTrue(screenSource.contains("tradeDialogCoordinator = new MarketChartTradeDialogCoordinator("));
        assertTrue(screenSource.contains("chartQuickTradeCoordinator = new ChartQuickTradeCoordinator("));
        assertTrue(screenSource.contains("binding.btnQuickTradePrimary.setOnClickListener(v -> executePrimaryQuickTrade());"));
        assertTrue(screenSource.contains("binding.btnQuickTradeSecondary.setOnClickListener(v -> executeSecondaryQuickTrade());"));
        assertTrue(screenSource.contains("consumePendingTradeActionIfNeeded();"));
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
    public void tradeDialogFixedHeightInputsShouldNotClipTextVertically() throws Exception {
        String layoutSource = readUtf8("src/main/res/layout/dialog_trade_command.xml");

        assertTrue(layoutSource.contains("android:id=\"@+id/etTradeVolume\""));
        assertTrue(layoutSource.contains("android:layout_height=\"@dimen/control_height_lg\""));
        assertTrue(layoutSource.contains("android:gravity=\"center_vertical\""));
        assertFalse("固定高度输入框不应再关闭字体内边距，否则文字容易被裁掉下半部分",
                layoutSource.contains("android:includeFontPadding=\"false\""));
        assertFalse("固定高度输入框不应再额外塞统一上下 padding，否则会把文本基线继续往下挤",
                layoutSource.contains("android:paddingVertical=\"10dp\""));
    }

    @Test
    public void tradeCallbacksShouldGuardLifecycleAndCancelOutstandingTasks() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
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
        assertTrue(screenSource.contains("tradeDialogCoordinator.cancelTradeTasks();"));
        assertTrue(coordinatorSource.contains("if (!canPresentTradeUi()) {\n            tradeFlowRunning = false;\n            return;\n        }"));
    }

    @Test
    public void pendingIntentTradeActionShouldWaitForCacheReadyInsteadOfBeingConsumedPrematurely() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("private final AccountStatsPreloadManager.CacheListener accountCacheListener = cache -> {"));
        assertTrue(screenSource.contains("consumePendingTradeActionIfNeeded();"));
        assertTrue(screenSource.contains("if (snapshot == null) {\n            return;\n        }"));
        assertTrue(screenSource.contains("if (targetItem == null) {\n            Toast.makeText(this, \"未找到目标持仓或挂单，暂时不能执行该操作\", Toast.LENGTH_SHORT).show();\n            lastConsumedTradeActionToken = token;\n            return;\n        }"));
    }

    @Test
    public void targetSymbolFromExternalTradeShortcutShouldBeNormalizedToMarketSymbol() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("import com.binance.monitor.util.ProductSymbolMapper;"));
        assertTrue(screenSource.contains("return ProductSymbolMapper.toMarketSymbol(raw);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

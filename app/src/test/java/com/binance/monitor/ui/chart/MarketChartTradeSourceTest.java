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
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml");

        assertTrue(screenSource.contains("private MarketChartTradeDialogCoordinator tradeDialogCoordinator;"));
        assertTrue(screenSource.contains("private ChartQuickTradeCoordinator chartQuickTradeCoordinator;"));
        assertTrue(screenSource.contains("private TradeTemplateRepository tradeTemplateRepository;"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION = \"extra_trade_action\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_CLOSE_POSITION = \"close_position\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_MODIFY_POSITION = \"modify_position\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_MODIFY_PENDING = \"modify_pending\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_CANCEL_PENDING = \"cancel_pending\";"));
        assertTrue(screenSource.contains("tradeDialogCoordinator = new MarketChartTradeDialogCoordinator("));
        assertTrue(screenSource.contains("chartQuickTradeCoordinator = new ChartQuickTradeCoordinator("));
        assertTrue(screenSource.contains("binding.btnQuickTradePrimary.setOnClickListener(v -> executePrimaryQuickTrade());"));
        assertTrue(screenSource.contains("binding.btnQuickTradeSecondary.setOnClickListener(v -> executeSecondaryQuickTrade());"));
        assertTrue(screenSource.contains("binding.btnQuickTradeTemplate"));
        assertTrue(layoutSource.contains("android:id=\"@+id/btnQuickTradeTemplate\""));
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
    public void marketChartTradeDialogShouldUseBatchCoordinatorForComplexActions() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");
        String layoutSource = readUtf8("src/main/res/layout/dialog_trade_command.xml");

        assertTrue(source.contains("BatchTradeCoordinator"));
        assertTrue(source.contains("TradeComplexActionPlanner"));
        assertTrue(source.contains("private static final String COMPLEX_ACTION_ADD_POSITION = \"加仓\";"));
        assertTrue(source.contains("COMPLEX_ACTION_ADD_POSITION"));
        assertTrue(source.contains("TradeComplexActionPlanner.planAddPosition("));
        assertTrue(source.contains("requestBatchTradeExecution("));
        assertTrue(layoutSource.contains("android:id=\"@+id/spinnerTradeComplexAction\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/etTradeTargetVolume\""));
        assertFalse(source.contains("for (TradeCommand"));
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
        assertTrue(layoutSource.contains("android:id=\"@+id/spinnerTradeComplexAction\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/etTradeTargetVolume\""));
        assertFalse(coordinatorSource.contains("android.R.layout.simple_spinner_item"));
        assertFalse(coordinatorSource.contains("android.R.layout.simple_spinner_dropdown_item"));
        assertTrue(coordinatorSource.contains("dialogBinding.spinnerTradeOrderType.setAdapter("));
        assertTrue(coordinatorSource.contains("private void styleTradeOrderTypeSelectFieldItem(@Nullable View view) {"));
        assertTrue(coordinatorSource.contains("UiPaletteManager.styleSelectFieldLabel("));
        assertFalse(coordinatorSource.contains("textView.setTextColor(Color.WHITE);"));
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
    public void indicatorParamDialogInputsShouldUseRealTextFieldStyleInsteadOfSpinnerSkin() throws Exception {
        String layoutSource = readUtf8("src/main/res/layout/dialog_indicator_params.xml");
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");

        assertTrue(layoutSource.contains("android:id=\"@+id/etParamValue1\""));
        assertTrue(layoutSource.contains("android:layout_height=\"@dimen/subject_height_md\""));
        assertTrue(layoutSource.contains("android:singleLine=\"true\""));
        assertFalse(layoutSource.contains("android:background=\"@drawable/bg_trade_field\""));
        assertFalse(layoutSource.contains("android:minHeight=\"@dimen/control_height_lg\""));
        assertFalse(layoutSource.contains("android:paddingHorizontal=\"@dimen/subject_padding_x_md\""));
        assertFalse(layoutSource.contains("android:background=\"@drawable/bg_spinner_filter\""));
        assertTrue(screenSource.contains("inflate(R.layout.dialog_indicator_params, null, false)"));
        assertTrue(screenSource.contains("styleIndicatorParamDialogActions(dialog)"));
        assertTrue(screenSource.contains("UiPaletteManager.styleActionButton(\n                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)"));
        assertTrue(screenSource.contains("UiPaletteManager.styleActionButton(\n                dialog.getButton(AlertDialog.BUTTON_POSITIVE)"));
        assertTrue(screenSource.contains("UiPaletteManager.styleInputField(etValue1, palette, R.style.TextAppearance_BinanceMonitor_Control);"));
        assertTrue(screenSource.contains("UiPaletteManager.styleInputField(etValue2, palette, R.style.TextAppearance_BinanceMonitor_Control);"));
        assertTrue(screenSource.contains("UiPaletteManager.styleInputField(etValue3, palette, R.style.TextAppearance_BinanceMonitor_Control);"));
        assertTrue(screenSource.contains("UiPaletteManager.styleInputField(input, palette, R.style.TextAppearance_BinanceMonitor_Control);"));
    }

    @Test
    public void chartScreenShouldStyleSelectFieldAndTriggersThroughStandardSubjects() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("UiPaletteManager.styleSelectFieldLabel("));
        assertTrue(screenSource.contains("UiPaletteManager.styleSegmentedOption("));
        assertTrue(screenSource.contains("styleOverlayToggleOption(binding.btnToggleHistoryTrades, showHistoryTrades);"));
        assertTrue(screenSource.contains("styleOverlayToggleOption(binding.btnTogglePositionOverlays, showPositionOverlays);"));
        assertTrue(screenSource.contains("styleIndicatorOption(binding.btnIndicatorVolume, showVolume);"));
        assertTrue(screenSource.contains("UiPaletteManager.applyTextAppearance(button, R.style.TextAppearance_BinanceMonitor_Caption);"));
        assertTrue(screenSource.contains("button.setBackgroundColor(android.graphics.Color.TRANSPARENT);"));
        assertTrue(screenSource.contains("button.setTextColor(resolveOverlayToggleTextColor(button, selected, palette));"));
        assertTrue(screenSource.contains("return selected ? palette.primary : palette.textSecondary;"));
        assertTrue(screenSource.contains("button.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));"));
        assertTrue(screenSource.contains("button.setTextColor(resolveIndicatorOptionTextColor(selected, palette));"));
        assertTrue(screenSource.contains("return selected ? palette.primary : palette.textSecondary;"));
        assertTrue(screenSource.contains("R.dimen.field_padding_x_compact"));
        assertTrue(screenSource.contains("button.setPadding(paddingHorizontalPx, 0, paddingHorizontalPx, 0);"));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutIndicatorStrip\""));
        assertTrue(layoutSource.contains("android:background=\"@android:color/transparent\""));
        assertTrue(screenSource.contains("IndicatorPresentationPolicy.buildDirectionalSpanAfterAnchor("));
        assertFalse(screenSource.contains("UiPaletteManager.styleTextTrigger("));
        assertFalse(screenSource.contains("UiPaletteManager.styleSpinnerItemText("));
        assertFalse(screenSource.contains("UiPaletteManager.styleInlineTextButton("));
        assertFalse(screenSource.contains("R.dimen.control_height_md"));
    }

    @Test
    public void chartStateTextShouldUseShorterMetaCopy() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("String base = loadedCandles.size() + \"根\";"));
        assertTrue(screenSource.contains("base += \"，时间：\" + stateTimeFormat.format(lastSuccessUpdateMs);"));
        assertFalse(screenSource.contains("共\" + loadedCandles.size() + \"根K线"));
        assertFalse(screenSource.contains("，更新时间："));
    }

    @Test
    public void topControlRowShouldKeepModeButtonsCenteredWithCompactSizing() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml");

        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartModeToggleGroup\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartSymbolPickerContainer\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartSymbolPickerContainer\"\n                    android:layout_width=\"0dp\""));
        assertTrue(layoutSource.contains("app:layout_constraintStart_toEndOf=\"@+id/layoutChartSymbolPickerContainer\""));
        assertTrue(layoutSource.contains("app:layout_constraintEnd_toStartOf=\"@+id/btnGlobalStatus\""));
        assertTrue(layoutSource.contains("android:layout_marginEnd=\"@dimen/inline_gap\""));
        assertTrue(layoutSource.contains("android:layout_height=\"@dimen/subject_height_compact\""));
        assertTrue(screenSource.contains("R.style.TextAppearance_BinanceMonitor_ControlCompact"));
        assertTrue(screenSource.contains("R.dimen.subject_height_compact"));
        assertTrue(screenSource.contains("R.dimen.chart_top_mode_button_min_width"));
        assertTrue(screenSource.contains("R.dimen.field_padding_x"));
        assertTrue(screenSource.contains("R.dimen.field_trailing_reserve_compact"));
        assertFalse(screenSource.contains("R.dimen.subject_select_field_trailing_reserve"));
        assertTrue(screenSource.contains("button.setMinimumWidth(minWidthPx);"));
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
        assertTrue(screenSource.contains("if (targetItem == null) {\n            Toast.makeText(this, \"未找到目标持仓或挂单，暂时不能执行该操作\", Toast.LENGTH_SHORT).show();\n            lastConsumedTradeActionToken = token;"));
        assertTrue(screenSource.contains("clearPendingTradeActionIntent(intent);"));
    }

    @Test
    public void consumedIntentTradeActionShouldBeClearedAfterDialogDispatchToAvoidReplayOnNextTabEntry() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("private void clearPendingTradeActionIntent(@Nullable Intent intent) {"));
        assertTrue(screenSource.contains("intent.removeExtra(EXTRA_TRADE_ACTION);"));
        assertTrue(screenSource.contains("intent.removeExtra(EXTRA_TRADE_POSITION_TICKET);"));
        assertTrue(screenSource.contains("intent.removeExtra(EXTRA_TRADE_ORDER_TICKET);"));
        assertTrue(screenSource.contains("tradeDialogCoordinator.showTradeCommandDialog(chartAction, targetItem);\n        clearPendingTradeActionIntent(intent);"));
        assertTrue(screenSource.contains("Toast.makeText(this, \"未找到目标持仓或挂单，暂时不能执行该操作\", Toast.LENGTH_SHORT).show();\n            lastConsumedTradeActionToken = token;\n            clearPendingTradeActionIntent(intent);\n            return;"));
    }

    @Test
    public void targetSymbolFromExternalTradeShortcutShouldBeNormalizedToMarketSymbol() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("import com.binance.monitor.util.ProductSymbolMapper;"));
        assertTrue(screenSource.contains("return ProductSymbolMapper.toMarketSymbol(raw);"));
    }

    @Test
    public void chartScreenShouldFeedQuickTradeDraftIntoDedicatedTradeLayerSnapshot() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("binding.klineChartView.setTradeLayerSnapshot(buildTradeLayerSnapshot());"));
        assertFalse(screenSource.contains("showQuickPendingLine(pendingLinePrice);"));
        assertTrue(screenSource.contains("binding.klineChartView.setTradeLayerSnapshot(overlaySnapshot.getTradeLayerSnapshot());"));
        assertTrue(screenSource.contains("binding.klineChartView.setTradeLayerSnapshot(new ChartTradeLayerSnapshot(null, null));"));
    }

    @Test
    public void tradeEntriesShouldReadTemplateRepositoryInsteadOfHardcodedDefaults() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String coordinatorSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("TradeTemplateRepository"));
        assertTrue(screenSource.contains("tradeTemplateRepository = new TradeTemplateRepository("));
        assertTrue(screenSource.contains("tradeTemplateRepository.getDefaultVolume()"));
        assertTrue(screenSource.contains("binding.btnQuickTradeTemplate"));
        assertTrue(screenSource.contains("updateQuickTradeTemplateButton("));
        assertTrue(screenSource.contains("tradeTemplateRepository.applyTemplate("));
        assertFalse(screenSource.contains("binding.etQuickTradeVolume.setText(R.string.chart_quick_trade_default_volume);"));
        assertTrue(coordinatorSource.contains("TradeTemplateRepository"));
        assertTrue(coordinatorSource.contains("resolveDefaultTradeTemplate()"));
        assertTrue(coordinatorSource.contains("tradeTemplateRepository.applyTemplate("));
        assertFalse(coordinatorSource.contains("dialogBinding.etTradeVolume.setText(\"0.10\");"));
        assertTrue(layoutSource.contains("android:id=\"@+id/btnQuickTradeTemplate\""));
    }

    @Test
    public void templateSummaryShouldAppearInConfirmPreviewChain() throws Exception {
        String confirmSource = readUtf8("src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java");
        String previewSource = readUtf8("src/main/java/com/binance/monitor/ui/trade/TradeRiskPreview.java");
        String resolverSource = readUtf8("src/main/java/com/binance/monitor/ui/trade/TradeRiskPreviewResolver.java");

        assertTrue(previewSource.contains("private final String templateName;"));
        assertTrue(previewSource.contains("public String getTemplateName() {"));
        assertTrue(resolverSource.contains("command.getParams().optString(\"templateName\", \"\")"));
        assertTrue(confirmSource.contains("当前模板："));
        assertTrue(confirmSource.contains("riskPreview.getTemplateName()"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

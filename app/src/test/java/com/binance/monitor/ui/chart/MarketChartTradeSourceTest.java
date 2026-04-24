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
        assertTrue(screenSource.contains("private TradeBatchActionDialogCoordinator batchActionDialogCoordinator;"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION = \"extra_trade_action\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_CLOSE_POSITION = \"close_position\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_MODIFY_POSITION = \"modify_position\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_MODIFY_PENDING = \"modify_pending\";"));
        assertTrue(screenSource.contains("public static final String EXTRA_TRADE_ACTION_CANCEL_PENDING = \"cancel_pending\";"));
        assertTrue(screenSource.contains("tradeDialogCoordinator = new MarketChartTradeDialogCoordinator("));
        assertTrue(screenSource.contains("batchActionDialogCoordinator = new TradeBatchActionDialogCoordinator("));
        assertTrue(screenSource.contains("chartQuickTradeCoordinator = new ChartQuickTradeCoordinator("));
        assertTrue(screenSource.contains("binding.btnBatchTradeActions.setOnClickListener(v -> openBatchTradeActions());"));
        assertTrue(screenSource.contains("binding.btnQuickTradePrimary.setOnClickListener(v -> executePrimaryQuickTrade());"));
        assertTrue(screenSource.contains("binding.btnQuickTradeSecondary.setOnClickListener(v -> executeSecondaryQuickTrade());"));
        assertTrue(layoutSource.contains("android:id=\"@+id/btnBatchTradeActions\""));
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
    public void chartScreenShouldUseTruthCenterForMainDisplayAndHistoryPaging() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("return resolveTruthDisplaySeriesOrLoaded(symbol, interval, loaded, Math.max(interval.getLimit(), RESTORE_WINDOW_LIMIT));"));
        assertTrue(screenSource.contains("return resolveTruthDisplaySeriesOrLoaded(symbol, interval, fetched, truthLimit);"));
        assertTrue(screenSource.contains("List<CandleEntry> mergedDisplay = resolveTruthDisplaySeriesOrLoaded("));
        assertTrue(screenSource.contains("private List<CandleEntry> resolveTruthDisplaySeriesOrLoaded(@NonNull String symbol,"));
        assertTrue(screenSource.contains("MarketDisplaySeries truthSeries = monitorRepository.selectDisplaySeries("));
        assertFalse(screenSource.contains("legacyLoadCandlesForRequest("));
        assertFalse(screenSource.contains("legacyFetchV2SeriesAfter("));
        assertFalse(screenSource.contains("legacyMergeRealtimeMinuteCache("));
        assertFalse(screenSource.contains("legacyBuildRealtimeDisplayCandles("));
    }

    @Test
    public void topControlRowShouldKeepModeButtonsCenteredWithCompactSizing() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml");

        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartModeToggleGroup\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartSymbolPickerContainer\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartSymbolPickerContainer\"\n                    android:layout_width=\"0dp\""));
        assertTrue(layoutSource.contains("app:layout_constraintStart_toStartOf=\"parent\""));
        assertTrue(layoutSource.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartStatusContainer\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartStatusContainer\"\n                    android:layout_width=\"0dp\""));
        assertTrue(layoutSource.contains("app:layout_constraintStart_toEndOf=\"@+id/layoutChartModeToggleGroup\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/btnGlobalStatus\"\n                        style=\"@style/Widget.BinanceMonitor.Subject.ActionButton.Secondary\"\n                        android:layout_width=\"match_parent\""));
        assertTrue(layoutSource.contains("android:layout_gravity=\"end|center_vertical\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutChartTopRightActions\""));
        assertTrue(layoutSource.contains("android:layout_gravity=\"end|top\""));
        assertTrue(layoutSource.contains("android:layout_marginEnd=\"@dimen/kline_price_axis_reserved_width\""));
        assertTrue(layoutSource.contains("android:layout_height=\"@dimen/subject_height_compact\""));
        assertTrue(screenSource.contains("R.style.TextAppearance_BinanceMonitor_ControlCompact"));
        assertTrue(screenSource.contains("R.dimen.subject_height_compact"));
        assertTrue(screenSource.contains("R.dimen.chart_top_mode_button_min_width"));
        assertTrue(screenSource.contains("R.dimen.field_padding_x"));
        assertTrue(screenSource.contains("R.dimen.field_trailing_reserve_compact"));
        assertTrue(screenSource.contains("button.setTextColor(palette.primary);"));
        assertTrue(screenSource.contains("styleOverlayActionTrigger(binding.btnBatchTradeActions);"));
        assertFalse(screenSource.contains("R.dimen.subject_select_field_trailing_reserve"));
        assertTrue(screenSource.contains("button.setMinimumWidth(minWidthPx);"));
    }

    @Test
    public void accountOverlaySignatureShouldIncludeProductRuntimeSummaryState() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("private com.binance.monitor.runtime.state.model.ChartProductRuntimeModel resolveChartRuntimeModel("));
        assertTrue("交易页叠加层签名必须把当前产品运行态带进去，否则只变盈亏/手数时会被误判成未变化",
                screenSource.contains("return overlaySnapshotFactory().buildInputSignature(\n                selectedSymbol,\n                loadedCandles,\n                resolvedSnapshot,\n                cache,\n                resolveChartRuntimeModel(cache)\n        );"));
    }

    @Test
    public void quickTradeButtonsShouldUseStrongerBuySellSemanticColors() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("int accentColor = primaryAction ? palette.rise : palette.fall;"));
        assertTrue(screenSource.contains("ColorUtils.setAlphaComponent(accentColor, 44)"));
        assertFalse(screenSource.contains("ColorUtils.setAlphaComponent(accentColor, 26)"));
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
    public void selectedSymbolShouldPersistAcrossTabReturnAndExternalTargetShouldOnlyApplyOnce() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("private static final String PREF_KEY_SELECTED_SYMBOL = \"selected_symbol\";"));
        assertTrue(screenSource.contains("restoreSelectedSymbol();"));
        assertTrue(screenSource.contains("private void restoreSelectedSymbol() {"));
        assertTrue(screenSource.contains("preferences.getString(PREF_KEY_SELECTED_SYMBOL, selectedSymbol)"));
        assertTrue(screenSource.contains("private void persistSelectedSymbol() {"));
        assertTrue(screenSource.contains(".putString(PREF_KEY_SELECTED_SYMBOL, selectedSymbol)"));
        assertTrue(screenSource.contains("void commitSelectedSymbol(@NonNull String symbol) {\n        selectedSymbol = symbol;\n        persistSelectedSymbol();"));
        assertTrue(screenSource.contains("selectedSymbol = symbol;\n        persistSelectedSymbol();\n        if (binding == null) {"));
        assertTrue(screenSource.contains("clearConsumedTargetSymbolIntent(intent);"));
        assertTrue(screenSource.contains("private void clearConsumedTargetSymbolIntent(@Nullable Intent intent) {"));
        assertTrue(screenSource.contains("intent.removeExtra(EXTRA_TARGET_SYMBOL);"));
    }

    @Test
    public void chartScreenShouldFeedQuickTradeDraftIntoDedicatedTradeLayerSnapshot() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String chartViewSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("private ChartQuickTradeCoordinator.PendingDirection pendingDirection = ChartQuickTradeCoordinator.PendingDirection.NONE;"));
        assertTrue(screenSource.contains("binding.klineChartView.setTradeLayerSnapshot(buildTradeLayerSnapshot());"));
        assertTrue(screenSource.contains("pendingDirection = resolveQuickPendingDirection();"));
        assertTrue(screenSource.contains("binding.btnQuickTradePrimary.setEnabled(isQuickPendingBuyEnabled());"));
        assertTrue(screenSource.contains("binding.btnQuickTradeSecondary.setEnabled(isQuickPendingSellEnabled());"));
        assertTrue(screenSource.contains("return \"挂单 买入草稿\";"));
        assertTrue(screenSource.contains("return \"挂单 卖出草稿\";"));
        assertTrue(screenSource.contains("return ChartTradeLineTone.POSITIVE;"));
        assertTrue(screenSource.contains("return ChartTradeLineTone.NEGATIVE;"));
        assertTrue(screenSource.contains("if (quickTradeMode == ChartQuickTradeMode.PENDING && !isQuickPendingBuyEnabled()) {"));
        assertTrue(screenSource.contains("if (quickTradeMode == ChartQuickTradeMode.PENDING && !isQuickPendingSellEnabled()) {"));
        assertTrue(screenSource.contains("binding.klineChartView.isQuickPendingLineDragging()"));
        assertTrue(screenSource.contains("dragging ? ChartTradeLineState.DRAGGING : ChartTradeLineState.DRAFT_PENDING"));
        assertTrue(screenSource.contains("binding.klineChartView.setOnQuickPendingLineChangeListener(price -> {\n            pendingLinePrice = price;\n            pendingDirection = resolveQuickPendingDirection();\n            updateQuickTradeBar();\n        });"));
        assertTrue(chartViewSource.contains("public boolean isQuickPendingLineDragging() {"));
        assertFalse(screenSource.contains("showQuickPendingLine(pendingLinePrice);"));
        assertTrue(screenSource.contains("buildTradeLayerSnapshot(),"));
        assertTrue(screenSource.contains("private ChartOverlaySnapshot lastBaseChartOverlaySnapshot = ChartOverlaySnapshot.empty();"));
        assertTrue(screenSource.contains("binding.klineChartView.setTradeLayerSnapshot(new ChartTradeLayerSnapshot(null, null));"));
    }

    @Test
    public void chartScreenShouldPromoteDraggedLiveTradeLinesIntoEditableModifyDrafts() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("private static final class TradeLineEditSession {"));
        assertTrue(screenSource.contains("private static final class TradeLineEditPreview {"));
        assertTrue(screenSource.contains("private TradeLineEditSession activeTradeLineEditSession;"));
        assertTrue(screenSource.contains("private final double accumulatedFee;"));
        assertTrue(screenSource.contains("private TradeLineEditPreview preview;"));
        assertTrue(screenSource.contains("binding.klineChartView.setOnTradeLineEditListener(new KlineChartView.OnTradeLineEditListener() {"));
        assertTrue(screenSource.contains("String groupId = trimToEmpty(line.getGroupId());"));
        assertTrue(screenSource.contains("private void handleTradeLineDragStart(@Nullable ChartTradeLine line) {"));
        assertTrue(screenSource.contains("private void handleTradeLineDragUpdate(@Nullable ChartTradeLine line, double price) {"));
        assertTrue(screenSource.contains("private void handleTradeLineActionClick(@Nullable ChartTradeLine line) {"));
        assertTrue(screenSource.contains("private void handleTradeLineBlankAreaTap() {"));
        assertTrue(screenSource.contains("private void focusTradeGroup(@Nullable String groupId) {"));
        assertTrue(screenSource.contains("private String buildTradeHighlightGroupId(@Nullable PositionItem item, @Nullable String action) {"));
        assertTrue(screenSource.contains("focusTradeGroup(buildTradeHighlightGroupId(targetItem, action));"));
        assertTrue(screenSource.contains("TradeLineEditSession session = ensureTradeLineEditSession(line);"));
        assertTrue(screenSource.contains("focusTradeGroup(session.groupId);"));
        assertTrue(screenSource.contains("public void onTradeLineBlankAreaTap() {\n                handleTradeLineBlankAreaTap();\n            }"));
        assertTrue(screenSource.contains("if (quickTradeMode == ChartQuickTradeMode.PENDING) {\n            applyQuickTradeMode(ChartQuickTradeMode.CLOSED);\n            focusTradeGroup(\"\");\n            return;\n        }"));
        assertTrue(screenSource.contains("if (activeTradeLineEditSession == null) {\n            return;\n        }\n        clearTradeLineEditSession();\n        focusTradeGroup(\"\");"));
        assertTrue(screenSource.contains("binding.klineChartView.setTradeLayerSnapshot(buildTradeLayerSnapshot());"));
        assertTrue(screenSource.contains("ChartTradeLayerSnapshot previewSnapshot = resolveTradeLineEditPreviewSnapshot(session);"));
        assertTrue(screenSource.contains("private ChartTradeLayerSnapshot resolveTradeLineEditPreviewSnapshot(@NonNull TradeLineEditSession session) {"));
        assertTrue(screenSource.contains("String baseSignature = resolveTradeLineEditPreviewBaseSignature();"));
        assertTrue(screenSource.contains("preview == null\n                || preview.targetRole != targetRole\n                || !preview.baseSignature.equals(baseSignature)"));
        assertTrue(screenSource.contains("preview = buildTradeLineEditPreview(session, targetRole, baseSignature);"));
        assertTrue(screenSource.contains("return buildTradeLineEditPreviewSnapshot(session, preview);"));
        assertTrue(screenSource.contains("private TradeLineEditPreview buildTradeLineEditPreview(@NonNull TradeLineEditSession session,"));
        assertTrue(screenSource.contains("removeTradeLayerGroup(draftLines, session.groupId);"));
        assertTrue(screenSource.contains("private ChartTradeLayerSnapshot buildTradeLineEditPreviewSnapshot(@NonNull TradeLineEditSession session,"));
        assertTrue(screenSource.contains("removeTradeLayerGroup(liveLines, session.groupId);"));
        assertTrue(screenSource.contains("appendFrozenTradeLine(liveLines, draftLines, session, role, targetRole);"));
        assertTrue(screenSource.contains("private void appendFrozenTradeLine(@NonNull List<ChartTradeLine> liveLines,"));
        assertTrue(screenSource.contains("private double resolveFrozenTradeLinePrice(@NonNull TradeLineEditSession session,"));
        assertTrue(screenSource.contains("private boolean shouldGhostTradeLineRole(@NonNull TradeLineEditSession session,"));
        assertTrue(screenSource.contains("double accumulatedFee = resolveTradeLineAccumulatedFee(targetItem);"));
        assertTrue(screenSource.contains("private double resolveTradeLineAccumulatedFee(@NonNull PositionItem targetItem) {"));
        assertTrue(screenSource.contains("return ChartTradeLineValueHelper.resolveTradeLineLabel(role, price, targetItem, accumulatedFee);"));
        assertTrue(screenSource.contains("ChartTradeLineValueHelper.resolveAccumulatedFee(trades, selectedSymbol, targetItem);"));
        assertTrue(screenSource.contains("session.accumulatedFee"));
        assertTrue(screenSource.contains("private double resolveTradeLineEditOriginalPrice(@NonNull String groupId,"));
        assertTrue(screenSource.contains("private double resolveTradeLineRoleOriginalPrice(@NonNull String groupId,"));
        assertTrue(screenSource.contains("private ChartTradeLine findDraftGhostTradeLine(@NonNull String groupId,"));
        assertTrue(screenSource.contains("private ChartTradeLine findBaseLiveTradeLine(@NonNull String groupId,"));
        assertTrue(screenSource.contains("session.groupId + \"|draft|ghost|\" + role.name().toLowerCase(Locale.ROOT)"));
        assertTrue(screenSource.contains("session.groupId + \"|draft|active|\" + targetRole.name().toLowerCase(Locale.ROOT)"));
        assertTrue(screenSource.contains("return line != null && trimToEmpty(line.getGroupId()).equalsIgnoreCase(trimToEmpty(groupId));"));
        assertTrue(screenSource.contains("return groupId + \"|line|\" + role.name().toLowerCase(Locale.ROOT);"));
        assertTrue(screenSource.contains("\"修改\""));
        assertTrue(screenSource.contains("TradeCommandFactory.pendingModify("));
        assertTrue(screenSource.contains("TradeCommandFactory.modifyTpSl("));
        assertTrue(screenSource.contains("if (session.sourceRole == ChartTradeLineRole.ENTRY) {\n            return role == targetRole && resolveTradeLineTargetOriginalPrice(session, role) > 0d;\n        }"));
        assertTrue(screenSource.contains("if (isSellSide(session.targetItem.getSide())) {\n            return movingUp ? ChartTradeLineRole.SL : ChartTradeLineRole.TP;\n        }\n        return movingUp ? ChartTradeLineRole.TP : ChartTradeLineRole.SL;"));
    }

    @Test
    public void chartScreenShouldNoLongerBuildAbnormalDotOverlayOnKlineChart() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String coordinatorSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertFalse(screenSource.contains("updateAbnormalAnnotationsOverlay("));
        assertFalse(screenSource.contains("lastAbnormalOverlaySignature"));
        assertFalse(screenSource.contains("AbnormalAnnotationOverlayBuilder"));
        assertFalse(coordinatorSource.contains("updateAbnormalAnnotationsOverlay();"));
    }

    @Test
    public void tradeEntriesShouldReadSessionVolumeMemoryInsteadOfTemplateDefaults() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String coordinatorSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String layoutSource = readUtf8("src/main/res/layout/activity_market_chart.xml")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("TradeSessionVolumeMemory"));
        assertTrue(screenSource.contains("syncQuickTradeVolumeState(false);"));
        assertTrue(screenSource.contains("TradeSessionVolumeMemory.getInstance().getCurrentVolume()"));
        assertFalse(screenSource.contains("tradeTemplateRepository = new TradeTemplateRepository("));
        assertFalse(screenSource.contains("binding.etQuickTradeVolume.setText(R.string.chart_quick_trade_default_volume);"));
        assertTrue(coordinatorSource.contains("TradeSessionVolumeMemory.getInstance().getCurrentVolume()"));
        assertFalse(coordinatorSource.contains("resolveDefaultTradeTemplate()"));
        assertFalse(coordinatorSource.contains("dialogBinding.etTradeVolume.setText(\"0.10\");"));
        assertFalse(layoutSource.contains("android:id=\"@+id/btnQuickTradeTemplate\""));
    }

    @Test
    public void confirmPreviewShouldNoLongerAppendTemplateSummary() throws Exception {
        String confirmSource = readUtf8("src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java");
        String previewSource = readUtf8("src/main/java/com/binance/monitor/ui/trade/TradeRiskPreview.java");
        String resolverSource = readUtf8("src/main/java/com/binance/monitor/ui/trade/TradeRiskPreviewResolver.java");

        assertTrue(previewSource.contains("private final String templateName;"));
        assertTrue(previewSource.contains("public String getTemplateName() {"));
        assertTrue(resolverSource.contains("command.getParams().optString(\"templateName\", \"\")"));
        assertFalse(confirmSource.contains("当前模板："));
    }

    @Test
    public void chartScreenShouldFilterLatestAccountCacheByCurrentActiveSessionBeforeUsingTradeOverlays() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("private AccountStatsPreloadManager.Cache resolveCurrentSessionAccountCache() {"));
        assertTrue(screenSource.contains("return matchesActiveSessionIdentity(cache.getAccount(), cache.getServer()) ? cache : null;"));
        assertTrue(screenSource.contains("public AccountStatsPreloadManager.Cache getLatestAccountCache() {\n                return resolveCurrentSessionAccountCache();\n            }"));
        assertTrue(screenSource.contains("AccountStatsPreloadManager.Cache cache = resolveCurrentSessionAccountCache();"));
        assertTrue(screenSource.contains("AccountStatsPreloadManager.Cache latestCache = resolveCurrentSessionAccountCache();"));
        assertTrue(screenSource.contains("lastAccountOverlaySignature = buildAccountOverlaySignature(\n                resolveCurrentSessionAccountCache(),\n                null\n        );"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

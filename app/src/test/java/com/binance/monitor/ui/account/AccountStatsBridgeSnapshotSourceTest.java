package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeSnapshotSourceTest {

    @Test
    public void applySnapshotShouldUseCanonicalTradesWithoutLifecycleMerge() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(!source.contains("TradeLifecycleMergeHelper.merge("));
    }

    @Test
    public void applySnapshotShouldReplaceInMemoryHistoryWhenRemoteSnapshotIsConnected() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("replaceTradeHistory(snapshotTrades);"));
        assertTrue(source.contains("replaceCurveHistory(snapshotCurves);"));
    }

    @Test
    public void preloadListenerShouldSkipUiRefreshUntilPageOwnsForegroundSubscription() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("if (cache == null || isFinishing() || isDestroyed() || loading) {"));
        assertTrue(source.contains("preloadManager.addCacheListener(preloadCacheListener);"));
        assertTrue(source.contains("preloadManager.removeCacheListener(preloadCacheListener);"));
        assertTrue(source.contains("preloadManager.setLiveScreenActive(true);"));
        assertTrue(source.contains("preloadManager.setLiveScreenActive(false);"));
    }

    @Test
    public void pageShouldRequestImmediatelyEvenWhenFreshPreloadedCacheAlreadyExists() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertFalse(source.contains("private void requestForegroundEntrySnapshot()"));
        assertTrue(!source.contains("if (hasFreshPreloadedCache()) {\n                    scheduleNextSnapshot(dynamicRefreshDelayMs);"));
    }

    @Test
    public void pageShouldSkipApplyingOlderSnapshotsOverCurrentUiState() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (host.isOlderThanCurrentSnapshot(updateAt)) {"));
        assertTrue(source.contains("if (host.isOlderThanCurrentSnapshot(finalUpdatedAt)) {"));
    }

    @Test
    public void overviewMetricsShouldUseDisplayResolverSnapshotPositions() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(!source.contains("AccountSnapshotDisplayResolver.resolve("));
        assertTrue(!source.contains("AccountSnapshotRestoreHelper.mergeMissingTrades("));
        assertTrue(!source.contains("applyStoredSnapshotIfAvailable()"));
    }

    @Test
    public void applySnapshotShouldNotRebuildPendingOrderDetailsFromPositionSummaryFields() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(!source.contains("basePendingOrders = buildPendingOrders(basePositions);"));
    }

    @Test
    public void curveNormalizationShouldNotRebuildCurveOrRatiosOnAppSide() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("return AccountCurvePointNormalizer.normalize(source, ACCOUNT_INITIAL_BALANCE);"));
        assertTrue(!source.contains("AccountCurveRebuildHelper.rebuild("));
        assertTrue(!source.contains("AccountCurvePositionRatioHelper.ensureVisibleRatios("));
    }

    @Test
    public void overviewAndIndicatorsShouldConsumeServerMetricsDirectly() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8);
        String helperSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java")
        ), StandardCharsets.UTF_8);

        assertFalse(activitySource.contains("overviewAdapter.submitList("));
        assertFalse(activitySource.contains("buildOverviewMetrics(latestOverviewMetrics)"));
        assertTrue(helperSource.contains("indicatorAdapter.submitList(buildCurveIndicators(latestCurveIndicators));"));
        assertTrue(activitySource.contains("bindTradeAnalytics("));
        assertTrue(activitySource.contains("statsAdapter.submitList(tradeStatsMetrics == null ? new ArrayList<>() : new ArrayList<>(tradeStatsMetrics));"));
    }

    @Test
    public void snapshotHelpersShouldBeRemovedFromPageLayer() {
        assertTrue(!Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountSnapshotDisplayResolver.java")));
        assertTrue(!Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountSnapshotRestoreHelper.java")));
        assertTrue(!Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/TradeLifecycleMergeHelper.java")));
        assertTrue(!Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountCurveRebuildHelper.java")));
        assertTrue(!Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountCurvePositionRatioHelper.java")));
    }

    @Test
    public void legacyLiveAccountPageAndGatewayClientShouldBeRemoved() {
        assertTrue(!Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsLiveActivity.java")));
        assertTrue(!Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/Mt5GatewayClient.java")));
        assertTrue(!Files.exists(Paths.get("src/test/java/com/binance/monitor/ui/account/Mt5GatewayClientTest.java")));
    }

    @Test
    public void renderCurveShouldUsePrecalculatedPositionRatiosOnly() throws Exception {
        String helperSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java")
        ), StandardCharsets.UTF_8);

        assertTrue(helperSource.contains("List<CurvePoint> effectivePoints = points == null"));
        assertTrue(helperSource.contains("binding.positionRatioChartView.setPoints(effectivePoints);"));
    }

    @Test
    public void applyPreloadedCacheShouldNotWriteSnapshotBackIntoStorageAgain() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(!source.contains("persistSnapshotToStorage("));
        assertTrue(!source.contains("accountStorageRepository.persistSnapshot("));
    }

    @Test
    public void applyPreloadedCacheShouldRequireCurrentSessionIdentityMatch() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (!isPreloadedCacheForCurrentSession(cache)) {"));
        assertTrue(source.contains("remoteSessionCoordinator != null && remoteSessionCoordinator.isAwaitingSync()"));
        assertTrue(source.contains("expectedAccount = trim(remoteSessionCoordinator.getPendingLogin());"));
        assertTrue(source.contains("expectedServer = trim(remoteSessionCoordinator.getPendingServer());"));
        assertTrue(source.contains("expectedAccount = trim(activeSessionAccount.getLogin());"));
        assertTrue(source.contains("expectedServer = trim(activeSessionAccount.getServer());"));
    }

    @Test
    public void disconnectedSnapshotShouldNotReuseLastConnectedPositionsOrPendingOrders() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("snapshot = host.buildEmptyAccountSnapshot();"));
        assertTrue(!source.contains("basePositions = new ArrayList<>(connectedPositionCache);"));
        assertTrue(!source.contains("basePendingOrders = new ArrayList<>(connectedPendingCache);"));
    }

    @Test
    public void requestSnapshotShouldAlwaysReleaseLoadingStateInFinally() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("host.executeIo(() -> {\n            try {"));
        assertTrue(source.contains("} catch (Exception exception) {"));
        assertTrue(source.contains("host.runOnUiThread(() -> {"));
        assertTrue(source.contains("host.setLoading(false);"));
    }

    @Test
    public void pageShouldRestoreStoredSnapshotOffMainThreadWhenMemoryCacheIsEmpty() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        Path coordinatorFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(coordinatorFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(activitySource.contains("snapshotRefreshCoordinator.primeStoredSnapshotRestoreIfNeeded();"));
        assertTrue(coordinatorSource.contains("private void scheduleStoredSnapshotRestoreIfNeeded()"));
        assertTrue(coordinatorSource.contains("host.executeIo(() -> {"));
        assertTrue(coordinatorSource.contains("host.hydrateLatestCacheFromStorage();"));
        assertTrue(coordinatorSource.contains("applyPreloadedCacheIfAvailable();"));
    }

    @Test
    public void applySnapshotShouldEmitStageLatencyTraceForMainThreadSections() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        Path coordinatorFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(coordinatorFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(activitySource.contains("traceAccountRenderPhase(\"on_create_inflate_and_content\""));
        assertTrue(activitySource.contains("traceAccountRenderPhase(\"on_create_runtime_init\""));
        assertTrue(activitySource.contains("traceAccountRenderPhase(\"on_create_adapter_init\""));
        assertTrue(activitySource.contains("traceAccountRenderPhase(\"on_create_restore_ui_state\""));
        assertTrue(activitySource.contains("traceAccountRenderPhase(\"on_create_setup_static_ui\""));
        assertTrue(activitySource.contains("traceAccountRenderPhase(\"on_create_bind_local_meta\""));
        assertTrue(activitySource.contains("traceAccountRenderPhase(\"on_create_enter_account_screen\""));
        assertTrue(activitySource.contains("traceAccountRenderPhase(\"on_create_total\""));
        assertTrue(activitySource.contains("traceAccountRenderPhase(\"attach_secondary_sections\""));
        assertTrue(coordinatorSource.contains("traceAccountRenderPhase(\"render_returns_table\""));
        assertTrue(coordinatorSource.contains("traceAccountRenderPhase(\"apply_curve_range\""));
        assertTrue(coordinatorSource.contains("traceAccountRenderPhase(\"refresh_trade_stats\""));
        assertTrue(coordinatorSource.contains("traceAccountRenderPhase(\"refresh_trades\""));
        assertTrue(coordinatorSource.contains("host.traceAccountRenderPhase("));
        assertTrue(coordinatorSource.contains("\"apply_snapshot_total\""));
        assertTrue(activitySource.contains("ChainLatencyTracer.markAccountRenderPhase("));
    }

    @Test
    public void refreshSignatureShouldIncludeHistoryRevisionForHistoryDrivenSections() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("payload.getHistoryRevision()"));
    }

    @Test
    public void refreshSignatureShouldIgnoreRuntimePositionsAndUseHistoryRenderSignature() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("AccountStatsRenderSignature.from("));
        assertFalse(source.contains("appendPositionSignature(builder, snapshot.getPositions());"));
        assertFalse(source.contains("appendPositionSignature(builder, snapshot.getPendingOrders());"));
    }

    @Test
    public void deferredSecondaryRenderShouldUseHistoryStoreAndSectionDiff() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        Path coordinatorFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(coordinatorFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(activitySource.contains("AccountHistorySnapshotStore"));
        assertTrue(coordinatorSource.contains("AccountStatsSectionDiff.between("));
    }

    @Test
    public void accountPageShouldDeferSecondarySectionsUntilAfterFirstFrame() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String layoutSource = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_account_stats.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("private boolean secondarySectionsAttached;"));
        assertTrue(activitySource.contains("private boolean deferredSecondaryRenderPending;"));
        assertTrue(activitySource.contains("private boolean firstFrameCompleted;"));
        assertTrue(activitySource.contains("private boolean firstFrameCompletionPosted;"));
        assertTrue(activitySource.contains("private void scheduleDeferredSecondarySectionAttach()"));
        assertTrue(activitySource.contains("private void markFirstFrameCompleted()"));
        assertTrue(activitySource.contains("private void attachDeferredSecondarySections()"));
        assertFalse(activitySource.contains("private void renderDeferredSnapshotSections()"));
        assertFalse(activitySource.contains("private void renderDeferredSnapshotSectionsIfNeeded()"));
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        assertTrue(activitySource.contains("binding.scrollAccountStats.getViewTreeObserver().addOnDrawListener"));
        assertTrue(activitySource.contains("if (firstFrameCompletionPosted) {\n                    return;\n                }"));
        assertTrue(activitySource.contains("firstFrameCompletionPosted = true;"));
        assertTrue(activitySource.contains("clearFirstFrameCompletionListener();"));
        assertTrue(activitySource.contains("markFirstFrameCompleted();"));
        assertTrue(activitySource.contains("attachDeferredSecondarySections();\n        if (secondarySectionsAttached && deferredSecondaryRenderPending && renderCoordinator != null) {\n            renderCoordinator.renderDeferredSnapshotSections();\n        }"));
        assertFalse(activitySource.contains("binding.cardCurveSection.setVisibility(View.VISIBLE);"));
        assertTrue(coordinatorSource.contains("host.renderCurveWithIndicators(host.resolveImmediateCurvePoints());"));
        assertTrue(activitySource.contains("binding.layoutCurveSecondarySection.setVisibility(View.VISIBLE);"));
        assertTrue(activitySource.contains("binding.cardReturnStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(activitySource.contains("binding.cardTradeRecordsSection.setVisibility(View.VISIBLE);"));
        assertFalse(activitySource.contains("binding.cardTradeRecordsSection.setVisibility(View.VISIBLE);\n        binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(activitySource.contains("private void bindTradeAnalytics(List<AccountMetric> tradeStatsMetrics,"));
        assertTrue(activitySource.contains("boolean masked = isPrivacyMasked();"));
        assertTrue(activitySource.contains("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(coordinatorSource.contains("void renderDeferredSnapshotSections()"));
        assertTrue(coordinatorSource.contains("scheduleDeferredSecondarySectionRender();"));

        assertTrue(layoutSource.contains("android:id=\"@+id/cardCurveSection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutCurveSecondarySection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/cardTradeRecordsSection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/cardTradeStatsSection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/cardReturnStatsSection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutCurveSecondarySection\"\n"));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutCurveSecondarySection\"\n"));
    }

    @Test
    public void privacyMaskShouldNotForceSecondarySectionRerenderBeforeImmediateContentExists() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("private boolean hasImmediateAccountContent()"));
        assertTrue(activitySource.contains("if (!hasImmediateAccountContent()) {\n            return;\n        }"));
    }

    @Test
    public void accountPageShouldNoLongerOwnRealtimeOverviewOrPositionSections() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String layoutSource = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_account_stats.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(activitySource.contains("private final Runnable overviewHeaderTicker"));
        assertFalse(activitySource.contains("refreshHandler.post(overviewHeaderTicker);"));
        assertFalse(activitySource.contains("binding.recyclerOverview.setLayoutManager"));
        assertFalse(activitySource.contains("binding.recyclerPositionByProduct.setLayoutManager"));
        assertFalse(activitySource.contains("binding.recyclerPositions.setLayoutManager"));
        assertFalse(activitySource.contains("binding.recyclerPendingOrders.setLayoutManager"));
        assertFalse(activitySource.contains("private void refreshPositions()"));
        assertFalse(activitySource.contains("private List<AccountMetric> buildOverviewMetrics("));
        assertFalse(activitySource.contains("private String formatRefreshMetaText()"));

        assertFalse(layoutSource.contains("@+id/tvAccountOverviewTitle"));
        assertFalse(layoutSource.contains("@+id/tvAccountMeta"));
        assertFalse(layoutSource.contains("@+id/recyclerOverview"));
        assertFalse(layoutSource.contains("@+id/cardCurrentPositions"));
        assertFalse(layoutSource.contains("@+id/recyclerPositionByProduct"));
        assertFalse(layoutSource.contains("@+id/recyclerPositions"));
        assertFalse(layoutSource.contains("@+id/recyclerPendingOrders"));
    }

    @Test
    public void deferredSecondaryRenderShouldPrepareHeavySectionsOffMainThread() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("private int deferredSecondaryRenderRevision;"));
        assertFalse(activitySource.contains("private void scheduleDeferredSecondarySectionRender()"));
        assertTrue(coordinatorSource.contains("private DeferredSecondaryRenderRequest buildDeferredSecondaryRenderRequest()"));
        assertTrue(coordinatorSource.contains("private void applyDeferredSecondaryRenderResult("));
        assertTrue(coordinatorSource.contains("AccountDeferredSnapshotRenderHelper.prepare(request.toHelperRequest())"));
        assertTrue(coordinatorSource.contains("host.executeDeferredSecondaryRender(() -> {"));
        assertFalse(activitySource.contains("private void renderDeferredSnapshotSections() {\n        deferredSecondaryRenderPending = false;\n\n        long stageStartedAt = SystemClock.elapsedRealtime();\n        updateTradeProductOptions();"));
        assertFalse(activitySource.contains("stageStartedAt = SystemClock.elapsedRealtime();\n        refreshTradeStats();"));
        assertFalse(activitySource.contains("stageStartedAt = SystemClock.elapsedRealtime();\n        refreshTrades(false);"));
        assertFalse(activitySource.contains("stageStartedAt = SystemClock.elapsedRealtime();\n        applyCurrentCurveRangeFromAllPoints();"));
    }

    @Test
    public void tradeStatsSectionShouldBecomeVisibleOnlyAfterMetricsAndChartsAreBound() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        int statsSubmitIndex = activitySource.indexOf("statsAdapter.submitList(tradeStatsMetrics == null ? new ArrayList<>() : new ArrayList<>(tradeStatsMetrics));");
        int pnlChartIndex = activitySource.indexOf("binding.tradePnlBarChart.setEntries(");
        int scatterIndex = activitySource.indexOf("binding.tradeDistributionScatterView.setPoints(");
        int visibleIndex = activitySource.indexOf("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);");

        assertTrue(statsSubmitIndex >= 0);
        assertTrue(pnlChartIndex >= 0);
        assertTrue(scatterIndex >= 0);
        assertTrue(visibleIndex >= 0);
        assertTrue("交易统计卡片应在指标和图表数据全部绑定后再整体显示，避免先露出选项卡再补列表",
                visibleIndex > statsSubmitIndex && visibleIndex > pnlChartIndex && visibleIndex > scatterIndex);
    }

    @Test
    public void tradeStatsFirstRevealShouldShowImmediatelyAfterDataBinding() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(activitySource.contains("private void revealTradeStatsSectionWhenReady() {"));
        assertFalse(activitySource.contains("binding.recyclerStats.getViewTreeObserver().addOnPreDrawListener("));
        assertFalse(activitySource.contains("boolean firstReveal = binding.cardTradeStatsSection.getVisibility() != View.VISIBLE;"));
        assertTrue(activitySource.contains("binding.tvTradePnlLegend.setVisibility(View.GONE);\n        binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
    }

    @Test
    public void tradeStatsToggleShouldUseCoordinatorInsteadOfLegacyActivityHelpers() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("tradeWeekdayBasis = checkedId == R.id.btnTradeWeekdayOpenTime"));
        assertTrue(activitySource.contains("if (renderCoordinator != null) {\n                renderCoordinator.refreshTradeStats();\n            }"));
        assertTrue(activitySource.contains("returnStatsMode = ReturnStatsMode.DAY;"));
        assertTrue(activitySource.contains("returnValueMode = checkedId == R.id.btnReturnsAmount"));
        assertTrue(activitySource.contains("renderCoordinator.refreshReturnStats();"));
        assertTrue(activitySource.contains("renderCoordinator.refreshCurveProjection();"));
        assertFalse(activitySource.contains("renderReturnStatsTable(allCurvePoints);"));
        assertFalse(activitySource.contains("applyCurrentCurveRangeFromAllPoints();"));
        assertFalse(activitySource.contains("refreshTradeWeekdayStats(filterTradesBySideMode(baseTrades, tradePnlSideMode));"));
        assertFalse(activitySource.contains("private void refreshTradeWeekdayStats(List<TradeRecordItem> trades)"));
        assertFalse(activitySource.contains("private List<TradePnlBarChartView.Entry> buildTradePnlChartEntries("));
        assertFalse(activitySource.contains("private boolean matchesSideMode(TradeRecordItem item, TradePnlSideMode sideMode)"));
        assertFalse(activitySource.contains("private List<TradeRecordItem> filterTradesBySideMode(List<TradeRecordItem> trades, TradePnlSideMode sideMode)"));
        assertTrue(coordinatorSource.contains("void refreshReturnStats()"));
        assertTrue(coordinatorSource.contains("host.renderReturnStatsTable(host.getAllCurvePoints());"));
        assertTrue(coordinatorSource.contains("void refreshCurveProjection()"));
        assertTrue(coordinatorSource.contains("AccountDeferredSnapshotRenderHelper.buildCurveProjection("));
        assertTrue(coordinatorSource.contains("host.applyPreparedCurveProjection(curveProjection);"));
    }

    @Test
    public void deferredTradeStatsRenderShouldHideOldCardBeforePreparingFreshContent() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("private void hideTradeStatsSectionUntilFreshContentReady() {"));
        assertTrue(activitySource.contains("if (binding.cardTradeStatsSection.getVisibility() != View.VISIBLE) {\n            binding.cardTradeStatsSection.setVisibility(View.INVISIBLE);\n        }"));
        assertTrue(coordinatorSource.contains("host.hideTradeStatsSectionUntilFreshContentReady();"));
        assertTrue(coordinatorSource.contains("host.setDeferredSecondaryRenderPending(false);"));
        assertTrue(coordinatorSource.contains("scheduleDeferredSecondarySectionRender();"));
    }

    @Test
    public void accountStatsActivityShouldDelegatePageBindingToController() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String runtimeSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("AccountStatsPageController"));
        assertTrue(activitySource.contains("AccountStatsPageHostDelegate"));
        assertTrue(activitySource.contains("AccountStatsPageRuntime"));
        assertTrue(activitySource.contains("private AccountStatsPageRuntime pageRuntime;"));
        assertTrue(activitySource.contains("pageController.bind();"));
        assertTrue(activitySource.contains("pageController.onColdStart();"));
        assertTrue(activitySource.contains("new AccountStatsPageHostDelegate("));
        assertTrue(activitySource.contains("pageRuntime = new AccountStatsPageRuntime("));
        assertTrue(activitySource.contains("pageController.onPageShown();"));
        assertTrue(activitySource.contains("pageController.onPageHidden();"));
        assertTrue(activitySource.contains("pageController.onDestroy();"));
        assertFalse(activitySource.contains("private void onAccountStatsPageShown()"));
        assertFalse(activitySource.contains("private void onAccountStatsPageHidden()"));
        assertFalse(activitySource.contains("private boolean snapshotLoopEnabled;"));
        assertFalse(activitySource.contains("private final Handler refreshHandler"));
        assertFalse(activitySource.contains("private final Runnable refreshRunnable"));
        assertFalse(activitySource.contains("private long nextRefreshAtMs;"));
        assertFalse(activitySource.contains("private long scheduledRefreshDelayMs"));
        assertFalse(activitySource.contains("private void scheduleNextSnapshot(long delayMs)"));
        assertFalse(activitySource.contains("private void clearScheduledRefresh()"));
        assertFalse(activitySource.contains("private void setupBottomNav()"));
        assertFalse(activitySource.contains("private void updateBottomTabs("));
        assertFalse(activitySource.contains("private void styleNavTab("));
        assertTrue(runtimeSource.contains("private boolean snapshotLoopEnabled;"));
        assertTrue(runtimeSource.contains("private final Handler refreshHandler = new Handler(Looper.getMainLooper());"));
        assertTrue(runtimeSource.contains("public void scheduleNextSnapshot(long delayMs) {"));
        assertTrue(runtimeSource.contains("public void clearScheduledRefresh() {"));
        assertTrue(runtimeSource.contains("public boolean shouldKeepRefreshLoop(boolean userLoggedIn, boolean finishing, boolean destroyed) {"));
        assertTrue(runtimeSource.contains("public void onPageDestroyed() {\n        disableSnapshotLoop();"));
        assertTrue(runtimeSource.contains("host.shutdownExecutors();"));
        assertFalse(activitySource.contains("protected void onDestroy() {\n        if (pageController != null) {\n            pageController.onDestroy();\n        }\n        snapshotLoopEnabled = false;"));
    }

    @Test
    public void accountStatsActivityShouldDelegateHeavyRenderChainToCoordinator() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("private AccountStatsRenderCoordinator renderCoordinator;"));
        assertTrue(activitySource.contains("AccountStatsRenderHostDelegate"));
        assertTrue(activitySource.contains("renderCoordinator = new AccountStatsRenderCoordinator("));
        assertTrue(activitySource.contains("new AccountStatsRenderHostDelegate("));
        assertTrue(activitySource.contains("renderCoordinator.applySnapshot(snapshot, remoteConnected);"));
        assertTrue(activitySource.contains("renderCoordinator.renderDeferredSnapshotSections();"));
        assertTrue(activitySource.contains("renderCoordinator.refreshTradeStats();"));
        assertTrue(activitySource.contains("renderCoordinator.refreshTrades(true, false);"));
        assertTrue(activitySource.contains("renderCoordinator.refreshTrades(true, true);"));
        assertFalse(activitySource.contains("private void applySnapshot(AccountSnapshot snapshot, boolean remoteConnected)"));
        assertFalse(activitySource.contains("private void enterAccountScreen(boolean coldStart)"));
        assertFalse(activitySource.contains("private void applyPreloadedCacheIfAvailable()"));
        assertFalse(activitySource.contains("private void refreshTradeStats()"));
        assertFalse(activitySource.contains("private void refreshTrades()"));
        assertFalse(activitySource.contains("private void refreshTrades(boolean scrollToTop)"));
        assertFalse(activitySource.contains("private void refreshTrades(boolean scrollToTop, boolean collapseExpanded)"));
        assertTrue(coordinatorSource.contains("final class AccountStatsRenderCoordinator"));
        assertTrue(coordinatorSource.contains("void applySnapshot(@NonNull AccountSnapshot snapshot, boolean remoteConnected)"));
        assertTrue(coordinatorSource.contains("void renderDeferredSnapshotSections()"));
        assertTrue(coordinatorSource.contains("void refreshTradeStats()"));
        assertTrue(coordinatorSource.contains("void refreshTrades(boolean scrollToTop, boolean collapseExpanded)"));
    }

    @Test
    public void accountStatsActivityShouldDelegateDeferredSectionPreparationAndBindingToCoordinator() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(activitySource.contains("renderCoordinator.scheduleDeferredSecondarySectionRender();"));
        assertFalse(activitySource.contains("private DeferredSecondaryRenderRequest buildDeferredSecondaryRenderRequest()"));
        assertFalse(activitySource.contains("private void applyDeferredSecondaryRenderResult("));
        assertFalse(activitySource.contains("private static final class DeferredSecondaryRenderRequest {"));
        assertTrue(coordinatorSource.contains("void scheduleDeferredSecondarySectionRender()"));
        assertTrue(coordinatorSource.contains("private DeferredSecondaryRenderRequest buildDeferredSecondaryRenderRequest()"));
        assertTrue(coordinatorSource.contains("private void applyDeferredSecondaryRenderResult("));
        assertTrue(coordinatorSource.contains("private static final class DeferredSecondaryRenderRequest {"));
    }

    @Test
    public void accountStatsActivityShouldMoveHeavyCurveAndReturnHelpersOutOfActivity() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java")));
        assertTrue(Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java")));
        assertTrue(activitySource.contains("private AccountStatsCurveRenderHelper curveRenderHelper;"));
        assertTrue(activitySource.contains("private AccountStatsReturnsTableHelper returnsTableHelper;"));
        assertFalse(activitySource.contains("private String buildCurveMeta("));
        assertFalse(activitySource.contains("private DrawdownSegment resolveMaxDrawdownSegment("));
        assertFalse(activitySource.contains("private void renderDailyReturnsTableFromTrades("));
        assertFalse(activitySource.contains("private void renderMonthlyReturnsTableFromTrades("));
        assertFalse(activitySource.contains("private void renderYearlyReturnsTableFromTrades("));
        assertFalse(activitySource.contains("private void renderStageReturnsTableFromTrades("));
        assertFalse(activitySource.contains("private void rebuildMonthlyTableThreeRowsV4("));
        assertFalse(activitySource.contains("private LinearLayout createMonthlyGroupedBlock("));
        assertFalse(activitySource.contains("private String formatMonthlyHeatCellValue("));
        assertFalse(activitySource.contains("private List<YearlyReturnRow> buildMonthlyReturnRowsFromTrades("));
    }
}

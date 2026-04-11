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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("replaceTradeHistory(snapshotTrades);"));
        assertTrue(source.contains("replaceCurveHistory(snapshotCurves);"));
    }

    @Test
    public void preloadListenerShouldSkipUiRefreshWhileExplicitRequestIsLoading() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (cache == null || isFinishing() || isDestroyed() || loading) {"));
        assertTrue(source.contains("preloadManager.addCacheListener(preloadCacheListener);"));
        assertTrue(source.contains("preloadManager.removeCacheListener(preloadCacheListener);"));
    }

    @Test
    public void pageShouldRequestImmediatelyEvenWhenFreshPreloadedCacheAlreadyExists() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private void requestForegroundEntrySnapshot()"));
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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("List<AccountMetric> overview = buildOverviewMetrics(latestOverviewMetrics);"));
        assertTrue(source.contains("overviewAdapter.submitList(overview);"));
        assertTrue(!source.contains("overviewAdapter.submitList(buildOverviewMetrics(latestOverviewMetrics));"));
        assertTrue(source.contains("indicatorAdapter.submitList(buildCurveIndicators(latestCurveIndicators));"));
        assertTrue(source.contains("bindTradeAnalytics("));
        assertTrue(source.contains("statsAdapter.submitList(tradeStatsMetrics == null ? new ArrayList<>() : new ArrayList<>(tradeStatsMetrics));"));
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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("List<CurvePoint> effectivePoints = points == null"));
        assertTrue(source.contains("binding.positionRatioChartView.setPoints(effectivePoints);"));
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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private void scheduleStoredSnapshotRestoreIfNeeded()"));
        assertTrue(source.contains("ioExecutor.execute(() -> {"));
        assertTrue(source.contains("preloadManager.hydrateLatestCacheFromStorage();"));
        assertTrue(source.contains("applyPreloadedCacheIfAvailable();"));
    }

    @Test
    public void applySnapshotShouldEmitStageLatencyTraceForMainThreadSections() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("traceAccountRenderPhase(\"on_create_inflate_and_content\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"on_create_runtime_init\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"on_create_adapter_init\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"on_create_restore_ui_state\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"on_create_setup_static_ui\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"on_create_bind_local_meta\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"on_create_enter_account_screen\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"on_create_total\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"build_overview\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"render_returns_table\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"apply_curve_range\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"refresh_trade_stats\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"refresh_positions\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"refresh_trades\""));
        assertTrue(source.contains("traceAccountRenderPhase(\"apply_snapshot_total\""));
        assertTrue(source.contains("ChainLatencyTracer.markAccountRenderPhase("));
    }

    @Test
    public void refreshSignatureShouldDeduplicateByRenderablePayloadInsteadOfHistoryRevision() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(!source.contains("appendStringToken(builder, trim(historyRevision));"));
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
        assertTrue(activitySource.contains("private void renderDeferredSnapshotSections()"));
        assertTrue(activitySource.contains("binding.scrollAccountStats.getViewTreeObserver().addOnDrawListener"));
        assertTrue(activitySource.contains("if (firstFrameCompletionPosted) {\n                    return;\n                }"));
        assertTrue(activitySource.contains("firstFrameCompletionPosted = true;"));
        assertTrue(activitySource.contains("clearFirstFrameCompletionListener();"));
        assertTrue(activitySource.contains("markFirstFrameCompleted();"));
        assertTrue(activitySource.contains("binding.cardCurveSection.setVisibility(View.VISIBLE);"));
        assertTrue(activitySource.contains("binding.layoutCurveSecondarySection.setVisibility(View.VISIBLE);"));
        assertTrue(activitySource.contains("binding.cardReturnStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(activitySource.contains("binding.cardTradeRecordsSection.setVisibility(View.VISIBLE);"));
        assertTrue(activitySource.contains("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));

        assertTrue(layoutSource.contains("android:id=\"@+id/cardCurveSection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutCurveSecondarySection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/cardTradeRecordsSection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/cardTradeStatsSection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/cardReturnStatsSection\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutCurveSecondarySection\"\n"));
        assertTrue(layoutSource.contains("android:visibility=\"gone\""));
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
    public void deferredSecondaryRenderShouldPrepareHeavySectionsOffMainThread() throws Exception {
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activitySource.contains("private int deferredSecondaryRenderRevision;"));
        assertTrue(activitySource.contains("private void scheduleDeferredSecondarySectionRender()"));
        assertTrue(activitySource.contains("private DeferredSecondaryRenderRequest buildDeferredSecondaryRenderRequest()"));
        assertTrue(activitySource.contains("private void applyDeferredSecondaryRenderResult("));
        assertTrue(activitySource.contains("AccountDeferredSnapshotRenderHelper.prepare(request.toHelperRequest())"));
        assertTrue(activitySource.contains("ioExecutor.execute(() -> {"));
        assertFalse(activitySource.contains("private void renderDeferredSnapshotSections() {\n        deferredSecondaryRenderPending = false;\n\n        long stageStartedAt = SystemClock.elapsedRealtime();\n        updateTradeProductOptions();"));
        assertFalse(activitySource.contains("stageStartedAt = SystemClock.elapsedRealtime();\n        refreshTradeStats();"));
        assertFalse(activitySource.contains("stageStartedAt = SystemClock.elapsedRealtime();\n        refreshTrades(false);"));
        assertFalse(activitySource.contains("stageStartedAt = SystemClock.elapsedRealtime();\n        applyCurrentCurveRangeFromAllPoints();"));
    }
}

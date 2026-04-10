package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (isOlderThanCurrentSnapshot(updateAt)) {"));
        assertTrue(source.contains("if (isOlderThanCurrentSnapshot(finalUpdatedAt)) {"));
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
        assertTrue(source.contains("statsAdapter.submitList(buildTradeStatsMetrics(latestStatsMetrics));"));
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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("snapshot = buildEmptyAccountSnapshot();"));
        assertTrue(!source.contains("basePositions = new ArrayList<>(connectedPositionCache);"));
        assertTrue(!source.contains("basePendingOrders = new ArrayList<>(connectedPendingCache);"));
    }

    @Test
    public void requestSnapshotShouldAlwaysReleaseLoadingStateInFinally() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("ioExecutor.execute(() -> {\n            try {"));
        assertTrue(source.contains("} catch (Exception exception) {"));
        assertTrue(source.contains("} finally {\n                runOnUiThread(() -> {"));
        assertTrue(source.contains("loading = false;"));
    }
}

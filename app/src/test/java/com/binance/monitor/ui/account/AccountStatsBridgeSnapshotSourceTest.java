package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeSnapshotSourceTest {

    @Test
    public void applySnapshotShouldMergeTradeLifecycleBeforeBuildingStats() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("baseTrades = TradeLifecycleMergeHelper.merge(effectiveTrades, TRADE_PNL_ZERO_THRESHOLD);"));
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
    public void pageShouldNotRequestImmediatelyWhenFreshPreloadedCacheAlreadyExists() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (hasFreshPreloadedCache()) {"));
        assertTrue(source.contains("scheduleNextSnapshot(dynamicRefreshDelayMs);"));
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

        assertTrue(source.contains("AccountSnapshotDisplayResolver.resolve("));
        assertTrue(source.contains("resolveOverviewPositionsFromDisplaySnapshot("));
    }

    @Test
    public void curveNormalizationShouldRecalculatePositionRatiosFromAppSide() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(source, ACCOUNT_INITIAL_BALANCE);"));
        assertTrue(source.contains("List<CurvePoint> rebuilt = AccountCurveRebuildHelper.rebuild("));
        assertTrue(source.contains("return AccountCurvePositionRatioHelper.ensureVisibleRatios("));
    }

    @Test
    public void renderCurveShouldUsePrecalculatedPositionRatiosOnly() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("List<CurvePoint> effectivePoints = points == null"));
        assertTrue(source.contains("binding.positionRatioChartView.setPoints(effectivePoints);"));
    }
}

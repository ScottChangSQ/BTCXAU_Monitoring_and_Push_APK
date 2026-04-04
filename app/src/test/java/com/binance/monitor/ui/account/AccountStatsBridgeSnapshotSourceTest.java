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
    public void curveNormalizationShouldUseServerCurveWithoutLocalRebuild() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("return AccountCurvePointNormalizer.normalize(source, ACCOUNT_INITIAL_BALANCE);"));
    }

    @Test
    public void renderCurveShouldNotRecalculatePositionRatioFromAppSide() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("List<CurvePoint> effectivePoints = points == null"));
    }
}

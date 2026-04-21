/*
 * 悬浮窗协调器源码约束测试，锁定销毁时必须切断排队刷新与悬浮窗回调链。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorFloatingCoordinatorSourceTest {

    @Test
    public void onDestroyShouldCancelRefreshQueueAndDestroyFloatingWindowManager() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("void onDestroy() {"));
        assertTrue(source.contains("mainHandler.removeCallbacks(floatingRefreshRunnable);"));
        assertTrue(source.contains("floatingRefreshScheduled = false;"));
        assertTrue(source.contains("lastFloatingRefreshAt = 0L;"));
        assertTrue(source.contains("floatingWindowManager.destroy();"));
    }

    @Test
    public void floatingCoordinatorShouldNotDependOnRemovedStreamSnapshotFallback() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("resolveFloatingPositions("));
        assertFalse(source.contains("copyStreamPositions()"));
        assertFalse(source.contains("hasStreamAccountSnapshot()"));
        assertFalse(source.contains("getStreamPositionsUpdatedAt()"));
        assertTrue(source.contains("List<com.binance.monitor.domain.account.model.PositionItem> positions = copyCachePositions(cache);"));
        assertTrue(source.contains("return FloatingPositionAggregator.buildSymbolCards("));
    }

    @Test
    public void floatingSnapshotShouldPreferUnifiedRuntimeCardsWhenCanonicalCacheExists() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private List<FloatingSymbolCardData> buildFloatingCards(@Nullable AccountStatsPreloadManager.Cache cache) {"));
        assertTrue(source.contains("runtimeCards.add(runtimeSnapshotStore.selectFloatingCard(symbol));"));
        assertTrue(source.contains("return FloatingPositionAggregator.buildSymbolCardsFromRuntime("));
    }

    @Test
    public void floatingCoordinatorShouldDeriveMarketSnapshotsFromUnifiedRuntimeSnapshot() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private java.util.Map<String, KlineData> buildVisibleClosedMinuteSnapshot("));
        assertTrue(source.contains("private java.util.Map<String, Double> buildVisiblePriceSnapshot("));
        assertTrue(source.contains("repository.selectClosedMinute(symbol)"));
        assertTrue(source.contains("repository.selectLatestPrice(symbol)"));
        assertTrue(source.contains("repository.selectMarketWindowSignature(symbol)"));
        assertFalse(source.contains("repository == null ? null : repository.getDisplayOverviewKlineSnapshot()"));
        assertFalse(source.contains("repository == null ? null : repository.getDisplayPriceSnapshot()"));
        assertFalse(source.contains("repository.getMarketRuntimeSnapshotLiveData().getValue()"));
        assertFalse(source.contains("runtimeSnapshot.getSymbolWindow(symbol)"));
    }

    @Test
    public void floatingCoordinatorShouldIncludeMarketWindowSignatureInRefreshGate() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("revisionRefreshPolicy.shouldRefresh(resolveVisibleProductRevisions(), resolveVisibleMarketSignatures())"));
        assertTrue(source.contains("revisionRefreshPolicy.markApplied(resolveVisibleProductRevisions(), resolveVisibleMarketSignatures())"));
        assertTrue(source.contains("private List<String> resolveVisibleMarketSignatures() {"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MonitorFloatingCoordinator.java");
    }
}

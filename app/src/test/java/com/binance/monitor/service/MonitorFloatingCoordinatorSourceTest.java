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
        assertTrue(source.contains("runtimeSnapshotStore.selectFloatingCard("));
        assertTrue(source.contains("cache.getAccount(),"));
        assertTrue(source.contains("cache.getServer(),"));
        assertTrue(source.contains("return FloatingPositionAggregator.buildSymbolCardsFromRuntime("));
    }

    @Test
    public void floatingCoordinatorShouldDeriveMarketSnapshotsFromCurrentMinuteTruth() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private java.util.Map<String, CurrentMinuteSnapshot> buildVisibleCurrentMinuteSnapshot("));
        assertTrue(source.contains("repository.selectCurrentMinuteSnapshot(symbol)"));
        assertTrue(source.contains("repository.selectMarketWindowSignature(symbol)"));
        assertFalse(source.contains("repository == null ? null : repository.getDisplayOverviewKlineSnapshot()"));
        assertFalse(source.contains("repository == null ? null : repository.getDisplayPriceSnapshot()"));
        assertFalse(source.contains("repository.getMarketRuntimeSnapshotLiveData().getValue()"));
        assertFalse(source.contains("runtimeSnapshot.getSymbolWindow(symbol)"));
    }

    @Test
    public void floatingCoordinatorLatestPriceShouldOnlyComeFromCurrentMinuteTruthSelector() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private java.util.Map<String, CurrentMinuteSnapshot> buildVisibleCurrentMinuteSnapshot(@NonNull List<String> visibleSymbols) {"));
        assertTrue(source.contains("snapshot.put(symbol, repository.selectCurrentMinuteSnapshot(symbol));"));
        assertFalse(source.contains("gatewayV2Client.fetchMarketSeries("));
        assertFalse(source.contains("klineCache"));
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

    @Test
    public void floatingCoordinatorShouldStopRefreshWhenScreenTurnsOffAndResumeOnScreenOn() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private boolean screenInteractive = true;"));
        assertTrue(source.contains("void setScreenInteractive(boolean interactive) {"));
        assertTrue(source.contains("private void cancelScheduledRefresh() {"));
        assertTrue(source.contains("if (!screenInteractive) {\n            return;\n        }"));
        assertTrue(source.contains("if (!interactive) {\n            cancelScheduledRefresh();\n            return;\n        }\n        requestRefresh(true);"));
        assertTrue(source.contains("void notifyAbnormalEvent(@Nullable String symbol) {"));
        assertTrue(source.contains("if (!screenInteractive || floatingWindowManager == null) {"));
    }

    @Test
    public void floatingCoordinatorShouldUseScenarioBasedThrottleInsteadOfSingleForegroundBackgroundPair() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("boolean hasActivePositions = resolveTotalFloatingPositionCount(cache) > 0;"));
        assertTrue(source.contains("boolean minimized = floatingWindowManager != null && floatingWindowManager.isMinimized();"));
        assertTrue(source.contains("return MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs("));
        assertTrue(source.contains("AppForegroundTracker.getInstance().isForeground(),"));
        assertTrue(source.contains("hasActivePositions,"));
        assertTrue(source.contains("minimized"));
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

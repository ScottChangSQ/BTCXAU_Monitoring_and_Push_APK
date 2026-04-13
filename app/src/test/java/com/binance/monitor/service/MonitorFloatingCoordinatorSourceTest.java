/*
 * 悬浮窗协调器源码约束测试，锁定销毁时必须切断排队刷新与悬浮窗回调链。
 */
package com.binance.monitor.service;

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
    public void emptyStreamSnapshotShouldWaitForCanonicalCacheBeforeShowingNoPositions() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private List<PositionItem> resolveFloatingPositions(@Nullable AccountStatsPreloadManager.Cache cache) {"));
        assertTrue(source.contains("List<PositionItem> streamPositions = dataSource.copyStreamPositions();"));
        assertTrue(source.contains("List<PositionItem> cachePositions = copyCachePositions(cache);"));
        assertTrue(source.contains("boolean cacheCaughtUp = cache != null\n                && cache.getFetchedAt() >= dataSource.getStreamPositionsUpdatedAt();"));
        assertTrue(source.contains("if (!streamPositions.isEmpty()) {\n            return streamPositions;\n        }"));
        assertTrue(source.contains("if (!cachePositions.isEmpty()) {\n            return cachePositions;\n        }"));
        assertTrue(source.contains("if (dataSource.hasStreamAccountSnapshot() && !cacheCaughtUp) {\n            return streamPositions;\n        }"));
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

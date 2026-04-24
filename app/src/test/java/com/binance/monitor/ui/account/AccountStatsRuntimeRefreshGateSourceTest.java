package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsRuntimeRefreshGateSourceTest {

    @Test
    public void analysisPagesShouldSkipHighFrequencyRuntimeOnlyCacheReplay() throws Exception {
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("桥接分析页收到预加载缓存时，应先判断是否真的需要重放整页快照",
                bridgeSource.contains("boolean shouldApplyPreloadedCache = !hasRenderableCurrentSessionState()\n")
                        && bridgeSource.contains("|| !trim(cache.getHistoryRevision()).equals(trim(latestHistoryRevision));"));
        assertTrue("桥接分析页在只有高频运行态变化时，应直接跳过 applyPreloadedCacheIfAvailable",
                bridgeSource.contains("if (!shouldApplyPreloadedCache) {\n            return;\n        }\n        if (snapshotRefreshCoordinator != null) {\n            snapshotRefreshCoordinator.applyPreloadedCacheIfAvailable();\n        }"));

        assertTrue("主壳分析页收到预加载缓存时，也应先判断是否真的需要重放整页快照",
                screenSource.contains("boolean shouldApplyPreloadedCache = !hasRenderableCurrentSessionState()\n")
                        && screenSource.contains("|| !trim(cache.getHistoryRevision()).equals(trim(latestHistoryRevision));"));
        assertTrue("主壳分析页在只有高频运行态变化时，应直接跳过 applyPreloadedCacheIfAvailable",
                screenSource.contains("if (!shouldApplyPreloadedCache) {\n            return;\n        }\n        if (snapshotRefreshCoordinator != null) {\n            snapshotRefreshCoordinator.applyPreloadedCacheIfAvailable();\n        }"));
    }

    private String readUtf8(String... candidates) throws Exception {
        for (String candidate : candidates) {
            Path path = Paths.get(candidate);
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("source file not found");
    }
}

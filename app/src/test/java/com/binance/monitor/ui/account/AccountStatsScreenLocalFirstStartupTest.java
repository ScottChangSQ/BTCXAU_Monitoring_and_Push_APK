package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsScreenLocalFirstStartupTest {

    @Test
    public void bindLocalMetaShouldRenderLocalHistorySnapshotBeforeFallbackPlaceholder() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("if (cache != null) {"));
        assertTrue(source.contains("if (hasRenderableHistorySections(cache.getSnapshot())) {"));
        assertTrue(source.contains("renderCoordinator.applySnapshot(cache.getSnapshot(), cache.isConnected());"));
        assertTrue(source.contains("lastAppliedSnapshotSignature = buildRefreshSignature("));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到分析页源码");
    }
}

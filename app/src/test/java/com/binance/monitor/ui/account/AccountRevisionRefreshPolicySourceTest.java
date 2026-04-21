package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountRevisionRefreshPolicySourceTest {

    @Test
    public void scheduledSnapshotLoopShouldUseRevisionGateBeforeRemoteRequest() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("if (AccountRevisionRefreshPolicy.shouldRequestSnapshot("));
        assertTrue(source.contains("host.getCurrentAccountRuntimeSnapshot(),"));
        assertTrue(source.contains("host.getAppliedAccountHistoryRevision(),"));
        assertTrue(source.contains("host.getAppliedAccountUpdatedAt(),"));
        assertTrue(source.contains("host.getScheduledSnapshotStaleAfterMs())) {"));
        assertTrue(source.contains("scheduleNextSnapshot(host.getScheduledSnapshotStaleAfterMs());"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到账户页运行时源码");
    }
}

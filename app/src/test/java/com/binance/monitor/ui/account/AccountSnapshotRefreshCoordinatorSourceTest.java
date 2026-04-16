package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountSnapshotRefreshCoordinatorSourceTest {

    @Test
    public void accountSnapshotRefreshCoordinatorShouldOwnStoredSnapshotRestoreFlow() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String activitySource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("void enterAccountScreen(boolean coldStart) {\n        scheduleStoredSnapshotRestoreIfNeeded();"));
        assertTrue(source.contains("private void scheduleStoredSnapshotRestoreIfNeeded() {"));
        assertTrue(source.contains("host.setStoredSnapshotRestorePending(true);"));
        assertTrue(source.contains("host.hydrateLatestCacheFromStorage();"));
        assertTrue(source.contains("host.clearLatestCacheIfCurrent(finalStoredCache);"));
        assertTrue(source.contains("applyPreloadedCacheIfAvailable();"));
        assertTrue(activitySource.contains("AccountSnapshotRefreshHostDelegate"));
        assertTrue(activitySource.contains("new AccountSnapshotRefreshHostDelegate("));
    }
}

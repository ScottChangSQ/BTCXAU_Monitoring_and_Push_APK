package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountBootstrapStateSourceTest {

    @Test
    public void coldStartShouldBeginAccountBootstrapBeforeBindingLocalMeta() throws Exception {
        String runtimeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(runtimeSource.contains("host.beginAccountBootstrap();"));
        assertTrue(runtimeSource.contains("host.beginAccountBootstrap();\n        host.bindLocalMeta();"));
    }

    @Test
    public void accountSnapshotRefreshCoordinatorShouldPublishStorageBootstrapTransitions() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("host.onStoredSnapshotRestoreStarted();"));
        assertTrue(source.contains("host.onStoredSnapshotDataReady(finalStoredCache);"));
        assertTrue(source.contains("host.onStoredSnapshotRestoreMiss();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到账户页源码");
    }
}

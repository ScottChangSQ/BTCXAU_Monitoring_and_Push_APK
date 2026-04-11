package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountV2SourceTest {

    @Test
    public void accountPreloadManagerShouldDependOnGatewayV2Client() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java",
                "src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java"
        );

        assertTrue(source.contains("GatewayV2Client"));
        assertTrue(source.contains("applyPublishedAccountRuntime("));
        assertTrue(source.contains("fetchAccountHistory(range,"));
        assertTrue(source.contains("persistIncrementalSnapshot("));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountStatsPreloadManager.java");
    }
}

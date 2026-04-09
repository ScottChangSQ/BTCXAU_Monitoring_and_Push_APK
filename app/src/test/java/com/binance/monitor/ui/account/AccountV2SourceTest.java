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
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("GatewayV2Client"));
        assertTrue(source.contains("applyPublishedAccountRuntime("));
        assertTrue(source.contains("fetchAccountHistory(AccountTimeRange.ALL"));
        assertTrue(source.contains("persistIncrementalSnapshot"));
    }
}

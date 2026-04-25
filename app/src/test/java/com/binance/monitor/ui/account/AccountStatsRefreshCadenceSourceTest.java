package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsRefreshCadenceSourceTest {

    @Test
    public void analysisHostsShouldDelegateDynamicDelayCalculationToSharedHelper() throws Exception {
        String bridgeSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");

        assertTrue(bridgeSource.contains("dynamicRefreshDelayMs = AccountPageRefreshCadenceHelper.resolveDelayMs(false, 0);"));
        assertTrue(bridgeSource.contains("dynamicRefreshDelayMs = AccountPageRefreshCadenceHelper.resolveDelayMs(true, unchangedRefreshStreak);"));
        assertTrue(screenSource.contains("dynamicRefreshDelayMs = AccountPageRefreshCadenceHelper.resolveDelayMs(false, 0);"));
        assertTrue(screenSource.contains("dynamicRefreshDelayMs = AccountPageRefreshCadenceHelper.resolveDelayMs(true, unchangedRefreshStreak);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

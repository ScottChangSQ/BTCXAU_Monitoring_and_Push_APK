package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsFragmentRealHostSourceTest {

    @Test
    public void accountStatsFragmentShouldUseSharedScreenHostInsteadOfPlaceholderCallbacks() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("AccountStatsScreen"));
        assertFalse(source.contains("当前阶段先保留空实现"));
        assertFalse(source.contains("完整统计主链仍由旧 Activity 承接"));
    }
}

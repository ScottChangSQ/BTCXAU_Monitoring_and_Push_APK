package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * 锁定分析页日收益表无交易占位规则，避免空白日期继续显示 +0.0%。
 */
public class AccountStatsDailyReturnFutureDateSourceTest {

    @Test
    public void dailyReturnTableShouldUsePlaceholderForNoTradeDays() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String bridgeSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String helperSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("String valueText = \"--\";"));
        assertTrue(screenSource.contains("boolean noTradePlaceholder = isZeroReturnValue(dayReturn, dayAmount);"));
        assertTrue(screenSource.contains("private boolean isZeroReturnValue(double rate, double amount)"));
        assertFalse(screenSource.contains("resolveLocalTodayDayKey()"));

        assertTrue(bridgeSource.contains("String valueText = \"--\";"));
        assertTrue(bridgeSource.contains("boolean noTradePlaceholder = isZeroReturnValue(dayReturn, dayAmount);"));
        assertTrue(bridgeSource.contains("private boolean isZeroReturnValue(double rate, double amount)"));
        assertFalse(bridgeSource.contains("resolveLocalTodayDayKey()"));

        assertTrue(helperSource.contains("String valueText = \"--\";"));
        assertTrue(helperSource.contains("boolean noTradePlaceholder = isZeroReturnValue(dayReturn, dayAmount);"));
        assertTrue(helperSource.contains("private static boolean isZeroReturnValue(double rate, double amount)"));
        assertFalse(helperSource.contains("resolveLocalTodayDayKey()"));
        assertTrue(screenSource.contains("boolean masked = isPrivacyMasked() && !\"--\".equals(displayValue);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path workingDir = Paths.get(".").toAbsolutePath().normalize();
        Path path = workingDir.resolve(relativePath).normalize();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

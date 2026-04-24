package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * 锁定分析页日收益表未来日期显示规则，避免本地今天之后继续显示 +0.0%。
 */
public class AccountStatsDailyReturnFutureDateSourceTest {

    @Test
    public void dailyReturnTableShouldUseLocalTodayAsPlaceholderBoundary() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String bridgeSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String helperSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(screenSource.contains("int localTodayDayKey = resolveLocalTodayDayKey();"));
        assertTrue(screenSource.contains("boolean afterLocalToday = localTodayDayKey > 0 && currentDayKey > localTodayDayKey;"));
        assertTrue(screenSource.contains("afterLocalToday ? \"--\" : formatReturnValue(0d, 0d, true)"));
        assertTrue(screenSource.contains("afterLocalToday ? null : resolveReturnDisplayColor(0d, 0d, R.color.text_secondary)"));
        assertTrue(screenSource.contains("private int resolveLocalTodayDayKey()"));
        assertTrue(screenSource.contains("Calendar today = Calendar.getInstance();"));

        assertTrue(bridgeSource.contains("int localTodayDayKey = resolveLocalTodayDayKey();"));
        assertTrue(bridgeSource.contains("boolean afterLocalToday = localTodayDayKey > 0 && currentDayKey > localTodayDayKey;"));
        assertTrue(bridgeSource.contains("afterLocalToday ? \"--\" : formatReturnValue(0d, 0d, true)"));
        assertTrue(bridgeSource.contains("private int resolveLocalTodayDayKey()"));

        assertTrue(helperSource.contains("int localTodayDayKey = resolveLocalTodayDayKey();"));
        assertTrue(helperSource.contains("boolean afterLocalToday = localTodayDayKey > 0 && currentDayKey > localTodayDayKey;"));
        assertTrue(helperSource.contains("afterLocalToday ? \"--\" : host.formatReturnValue(0d, 0d, true)"));
        assertTrue(helperSource.contains("private int resolveLocalTodayDayKey()"));
        assertTrue(screenSource.contains("boolean masked = isPrivacyMasked() && !\"--\".equals(displayValue);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path workingDir = Paths.get(".").toAbsolutePath().normalize();
        Path path = workingDir.resolve(relativePath).normalize();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}

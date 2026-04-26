package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 锁定月收益空值占位规则：无交易月份显示 --，真实有交易但结果为 0 的月份仍保留数值格式。
 */
public class AccountStatsMonthlyNoTradePlaceholderSourceTest {

    @Test
    public void monthlyReturnsShouldDifferentiateNoTradeFromRealZeroResult() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        );
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        String helperSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java"
        );

        assertTrue(screenSource.contains("private boolean shouldRenderNoTradePlaceholder(@Nullable MonthReturnInfo info)"));
        assertTrue(screenSource.contains("!info.hasTrades && isZeroReturnValue(info.returnRate, info.returnAmount)"));
        assertTrue(screenSource.contains("legacy.hasTrades = info.hasTrades;"));

        assertTrue(bridgeSource.contains("private boolean shouldRenderNoTradePlaceholder(@Nullable MonthReturnInfo info)"));
        assertTrue(bridgeSource.contains("!info.hasTrades && isZeroReturnValue(info.returnRate, info.returnAmount)"));
        assertTrue(bridgeSource.contains("legacy.hasTrades = info.hasTrades;"));

        assertTrue(helperSource.contains("info.hasTrades = true;"));
        assertTrue(helperSource.contains("boolean hasTrades;"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到收益统计源码文件");
    }
}

/*
 * 账户统计日收益源码约束测试，确保无交易日期也会明确显示 0。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsDailyReturnsSourceTest {

    @Test
    public void dailyReturnsTableShouldRenderZeroForDaysWithoutTrades() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        assertTrue("无交易日期应改成显式 0 文案，而不是留空",
                source.contains("formatReturnValue(0d, 0d, true)"));
    }

    @Test
    public void tradeBasedReturnsShouldUsePeriodStartAssetInsteadOfTradeNotional() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("交易口径收益率应统一走期初总资产辅助逻辑",
                source.contains("AccountPeriodReturnHelper.resolvePeriodReturnRate("));
        assertTrue("旧的成交名义金额收益率计算函数应被移除",
                !source.contains("resolveTradeReturnNotional("));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountStatsBridgeActivity.java");
    }
}

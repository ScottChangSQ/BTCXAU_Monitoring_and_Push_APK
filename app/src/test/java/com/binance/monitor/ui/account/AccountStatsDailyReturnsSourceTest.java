/*
 * 账户统计日收益源码约束测试，确保无交易日期统一显示 --，且真实有交易的 0 收益仍保留数值格式。
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
    public void dailyReturnsTableShouldRenderPlaceholderForDaysWithoutTrades() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        assertTrue("无交易日期应统一显示 --",
                source.contains("String valueText = \"--\";"));
        assertTrue("有交易的日收益仍需保留交易标记，避免把真实 0 收益误判成空值",
                source.contains("info.hasTrades = true;"));
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

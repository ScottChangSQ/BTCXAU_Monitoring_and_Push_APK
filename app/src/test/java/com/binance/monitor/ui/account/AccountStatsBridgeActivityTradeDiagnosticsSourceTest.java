/*
 * 账户统计页源码约束测试，确保交易可见性诊断分别接在快照入页和筛选出表两个边界。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeActivityTradeDiagnosticsSourceTest {

    @Test
    public void applySnapshotShouldLogTradeVisibilitySnapshot() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("applySnapshot 应记录快照与页面基表的交易可见性摘要",
                source.contains("logTradeVisibilitySnapshot(snapshotTrades, effectiveTrades, baseTrades);"));
    }

    @Test
    public void refreshTradesShouldLogTradeFilterVisibility() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("refreshTrades 应记录筛选条件和筛选结果摘要",
                source.contains("logTradeFilterVisibility(filtered, product, side, normalizedSort);"));
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

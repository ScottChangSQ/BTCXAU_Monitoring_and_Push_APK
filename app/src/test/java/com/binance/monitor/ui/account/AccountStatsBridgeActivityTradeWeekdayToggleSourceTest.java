package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeActivityTradeWeekdayToggleSourceTest {

    @Test
    public void tradeWeekdayBasisToggleShouldOnlyRefreshWeekdayChart() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        int start = source.indexOf("private void setupTradeWeekdayBasisToggle()");
        int end = source.indexOf("private int mapRangeButtonId", start);

        assertTrue("应能定位按星期盈亏切换按钮逻辑", start >= 0 && end > start);

        String block = source.substring(start, end);
        assertTrue("切换星期统计基准后应直接刷新星期图", block.contains("refreshTradeWeekdayStats("));
        assertFalse("切换星期统计基准时不应整页重算 refreshTradeStats()", block.contains("refreshTradeStats();"));
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

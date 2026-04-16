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
        assertTrue("切换星期统计基准时应只改动星期统计基准字段",
                block.contains("tradeWeekdayBasis = checkedId == R.id.btnTradeWeekdayOpenTime"));
        assertTrue("切换星期统计基准后应通过统一渲染协调器刷新统计内容",
                block.contains("if (renderCoordinator != null) {")
                        && block.contains("renderCoordinator.refreshTradeStats();"));
        assertFalse("切换星期统计基准时不应直接在按钮逻辑里重建整页数据链",
                block.contains("requestForegroundEntrySnapshot();"));
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

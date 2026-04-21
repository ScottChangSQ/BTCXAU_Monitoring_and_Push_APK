/*
 * 锁定交易页顶部全局状态按钮的紧凑账户文案，避免再次回退为过长标签挤占产品名称。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartCompactStatusLabelSourceTest {

    @Test
    public void compactAccountStatusShouldUseLoggedInLabelInsteadOfAccountOnline() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java"
        );

        assertTrue(source.contains("return sessionSummary.getActiveAccount() == null"));
        assertTrue(source.contains("? getString(R.string.global_status_account_offline)"));
        assertTrue(source.contains(": getString(R.string.global_status_compact_logged_in);"));
        assertFalse(source.contains(": getString(R.string.global_status_account_online);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        for (String candidate : candidates) {
            Path path = Paths.get(System.getProperty("user.dir")).resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("未找到源码文件");
    }
}

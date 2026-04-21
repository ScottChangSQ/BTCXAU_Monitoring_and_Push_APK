/*
 * 页面私有指标与私有格式防回退测试，确保主页面继续通过规则中心消费指标与颜色。
 */
package com.binance.monitor.ui.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PagePrivateIndicatorSourceTest {

    @Test
    public void accountAndChartPagesShouldUseRuleCenterInsteadOfPrivateBusinessColorBranches() throws Exception {
        String accountStatsScreen = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
        String accountStatsBridge = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String marketChartScreen = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");

        assertTrue(accountStatsScreen.contains("IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_RETURN_AMOUNT).getDisplayName()"));
        assertTrue(accountStatsScreen.contains("IndicatorPresentationPolicy.buildValueSpan("));
        assertTrue(accountStatsScreen.contains("IndicatorPresentationPolicy.present("));
        assertTrue(accountStatsBridge.contains("IndicatorPresentationPolicy.present("));
        assertTrue(accountStatsScreen.contains("IndicatorFormatterCenter.formatCount("));
        assertTrue(accountStatsBridge.contains("IndicatorFormatterCenter.formatCount("));
        assertTrue(marketChartScreen.contains("IndicatorPresentationPolicy.buildDirectionalSpanAfterAnchor("));
        assertFalse(accountStatsScreen.contains("R.color.accent_green"));
        assertFalse(accountStatsScreen.contains("R.color.accent_red"));
        assertFalse(accountStatsBridge.contains("R.color.accent_green"));
        assertFalse(accountStatsBridge.contains("R.color.accent_red"));
        assertFalse(accountStatsScreen.contains("private String signedMoney("));
        assertFalse(accountStatsBridge.contains("private String signedMoney("));
        assertFalse(marketChartScreen.contains("R.color.accent_green"));
        assertFalse(marketChartScreen.contains("R.color.accent_red"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

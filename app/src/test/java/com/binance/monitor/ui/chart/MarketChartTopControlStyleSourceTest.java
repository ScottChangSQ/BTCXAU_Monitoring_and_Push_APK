/*
 * 锁定交易页顶部控件统一样式入口，避免产品选择器、模式按钮和状态按钮再次分叉。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartTopControlStyleSourceTest {

    @Test
    public void screenAndLegacyActivityShouldUseSharedTopControlStyling() throws Exception {
        String screen = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java"
        );
        String activity = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        );

        assertTrue(screen.contains("applyTopControlGroupStyles();"));
        assertTrue(screen.contains("styleTopControlButton("));
        assertTrue(screen.contains("styleTopControlLabel("));
        assertTrue(activity.contains("HostNavigationIntentFactory.forTab(this, HostTab.MARKET_CHART)"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        for (String candidate : candidates) {
            java.nio.file.Path path = Paths.get(System.getProperty("user.dir")).resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("未找到源码文件");
    }
}

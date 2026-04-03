/*
 * 图表页持仓面板资源测试，确保空态文案和关键元信息样式不会回退。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPositionPanelResourceTest {

    // 校验图表页当前持仓空态和顶部元信息已切到统一样式。
    @Test
    public void marketChartLayoutShouldContainPositionEmptyStateAndSharedMetaStyle() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        assertTrue("图表页缺少暂无持仓空态控件", xml.contains("android:id=\"@+id/tvChartPositionsEmpty\""));
        assertTrue("图表页缺少暂无持仓文案", xml.contains("android:text=\"暂无持仓\""));
        assertTrue("左上角信息应统一使用 Meta 文本样式",
                xml.contains("android:id=\"@+id/tvChartInfo\"")
                        && xml.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.Meta\""));
        assertTrue("右上角倒计时应统一使用 Meta 文本样式",
                xml.contains("android:id=\"@+id/tvChartRefreshCountdown\"")
                        && xml.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.Meta\""));
    }

    // 按 UTF-8 读取布局资源。
    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到布局文件");
    }
}

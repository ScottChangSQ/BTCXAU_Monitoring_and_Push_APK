/*
 * 锁定交易页一级主模块与二级子模块的颜色层级，避免图表主块再次和内部子块混成同一层。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPrimaryModuleColorSourceTest {

    @Test
    public void primaryModulesShouldUseSurfaceEndWhileIntervalStripStaysOnCardLayer() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java"
        );

        assertTrue(source.contains(
                "binding.cardSymbolPanel.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.surfaceEnd, palette.stroke));"
        ));
        assertTrue(source.contains(
                "binding.cardChartPanel.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.surfaceEnd, palette.stroke));"
        ));
        assertTrue(source.contains(
                "binding.layoutIntervalStrip.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.card, palette.stroke));"
        ));
        assertFalse(source.contains(
                "binding.cardSymbolPanel.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.card, palette.stroke));"
        ));
        assertFalse(source.contains(
                "binding.cardChartPanel.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.card, palette.stroke));"
        ));
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

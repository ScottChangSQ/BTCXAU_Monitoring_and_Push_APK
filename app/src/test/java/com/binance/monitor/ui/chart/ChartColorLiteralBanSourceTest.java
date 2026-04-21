/*
 * 禁止图表主链继续保留颜色字面量，确保图表配色统一从共享主题层派生。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ChartColorLiteralBanSourceTest {

    @Test
    public void klineChartViewShouldNotKeepRawColorLiterals() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertFalse(source.contains("0x"));
        assertFalse(source.matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
        assertTrue(source.contains("applyPalette(UiPaletteManager.resolve(getContext()));"));
        assertTrue(source.contains("private UiPaletteManager.Palette requirePalette() {"));
    }

    @Test
    public void chartOverlayAndScreenShouldNotKeepTradeColorHexes() throws Exception {
        String overlayFactory = readUtf8("src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java");
        String abnormalBuilder = readUtf8("src/main/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilder.java");
        String screen = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");

        assertFalse(overlayFactory.contains("0x"));
        assertFalse(abnormalBuilder.contains("0x"));
        assertFalse(screen.contains("Color.parseColor("));
        assertTrue(overlayFactory.contains("new ChartOverlaySnapshotFactory.ColorScheme(") || screen.contains("new ChartOverlaySnapshotFactory.ColorScheme("));
        assertTrue(screen.contains("colorToken(R.color.trade_buy)"));
        assertTrue(screen.contains("colorToken(R.color.trade_sell)"));
        assertTrue(screen.contains("colorToken(R.color.pnl_profit)"));
        assertTrue(screen.contains("colorToken(R.color.pnl_loss)"));
        assertTrue(screen.contains("createAbnormalAnnotationColorRange()"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FloatingWindowManagerTitleSourceTest {

    @Test
    public void titleViewShouldAllowTwoLinePnlLayout() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("titleView.setSingleLine(false);"));
        assertTrue(source.contains("titleView.setMaxLines(2);"));
    }

    @Test
    public void titleViewShouldKeepProductNameAndLotsInPrimaryTextColor() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("return FloatingWindowTextFormatter.formatCardTitle(label, totalLots, 0d, hasPosition, masked);"));
        assertFalse(source.contains("String lotsText = hasPosition ? FloatingWindowTextFormatter.formatLotsText(totalLots) : \"\";"));
        assertFalse(source.contains("styled.setSpan(new ForegroundColorSpan(lotsColor)"));
        assertFalse(source.contains("ForegroundColorSpan"));
    }

    @Test
    public void titleViewPrimaryColorShouldBeAppliedAfterTextAppearanceToAvoidDenseStyleOverride() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        int appearanceIndex = source.indexOf("UiPaletteManager.applyTextAppearance(titleView, R.style.TextAppearance_BinanceMonitor_OverlayDense);");
        int colorIndex = source.indexOf("titleView.setTextColor(palette.textPrimary);");
        assertTrue(appearanceIndex >= 0);
        assertTrue(colorIndex > appearanceIndex);
    }
}

package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FloatingWindowManagerSourceTest {

    @Test
    public void expandedStateShouldUseSmallerSummaryAndCardTextSizes() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("binding.tvOverlayStatus.setTextSize(hasActivePositions ? 12f : 11f);"));
        assertTrue(source.contains("titleView.setTextSize(9f);"));
        assertTrue(source.contains("priceView.setTextSize(11f);"));
        assertTrue(source.contains("titleView.setTypeface(null, android.graphics.Typeface.BOLD);"));
        assertTrue(source.contains("priceView.setTypeface(null, android.graphics.Typeface.BOLD);"));
    }

    @Test
    public void volumeUnitShouldUseCanonicalProductSymbolMapper() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("ProductSymbolMapper.toTradeSymbol(code)"));
        assertTrue(source.contains("ProductSymbolMapper.TRADE_SYMBOL_XAU"));
    }

    @Test
    public void hideShouldUseImmediateDetachToAvoidDuplicateOverlayWindows() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("windowManager.removeViewImmediate(binding.getRoot());"));
    }

    @Test
    public void showPathShouldUseExplicitWindowOwnershipInsteadOfAttachmentHeuristics() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private boolean windowAdded;"));
        assertTrue(source.contains("if (windowAdded) {"));
        assertFalse(source.contains("isBindingAttachedToWindow()"));
        assertFalse(source.contains("isViewAttachedToWindow("));
    }

    @Test
    public void showPathShouldReuseSingleBindingInsteadOfRecreatingByAttachmentGuess() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private void ensureBinding()"));
        assertTrue(source.contains("ensureBinding();"));
        assertFalse(source.contains("detachCurrentWindow()"));
    }

    @Test
    public void hideShouldReleaseWindowOwnershipOnlyAfterExplicitRemove() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (!windowAdded || binding == null) {"));
        assertTrue(source.contains("windowAdded = false;"));
    }
}

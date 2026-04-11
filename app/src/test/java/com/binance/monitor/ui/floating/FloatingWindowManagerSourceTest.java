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

    @Test
    public void updateShouldSkipRenderWhenSnapshotVisualContentIsUnchanged() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (normalized.hasSameVisualContent(this.snapshot)) {"));
        assertTrue(source.contains("return;"));
    }

    @Test
    public void overlayTapShouldBringExistingAppTaskToFrontInsteadOfDirectChartLaunch() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("new Intent(context, OverlayLaunchBridgeActivity.class)"));
        assertTrue(source.contains("putExtra(OverlayLaunchBridgeActivity.EXTRA_TARGET_SYMBOL"));
        assertTrue(source.contains("putExtra(OverlayLaunchBridgeActivity.EXTRA_TARGET_DESTINATION"));
        assertTrue(source.contains("Intent.FLAG_ACTIVITY_NEW_TASK"));
        assertTrue(source.contains("cardView.setOnClickListener(v -> openChartForCard(card));"));
        assertFalse(source.contains("new Intent(context, MainActivity.class)"));
        assertFalse(source.contains("Intent.FLAG_ACTIVITY_REORDER_TO_FRONT"));
    }

    @Test
    public void fallbackOpenMainScreenShouldAlsoGoThroughBridgeActivity() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private void openMainScreen() {"));
        assertTrue(source.contains("new Intent(context, OverlayLaunchBridgeActivity.class)"));
        assertTrue(source.contains("putExtra(OverlayLaunchBridgeActivity.EXTRA_TARGET_DESTINATION, OverlayLaunchBridgeActivity.TARGET_DESTINATION_HOME)"));
        assertFalse(source.contains("getLaunchIntentForPackage(context.getPackageName())"));
    }

    @Test
    public void reconnectingStateShouldNotBeRenderedAsOffline() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("private boolean shouldRenderOfflineState(ConnectionStage connectionStage,"));
        assertTrue(source.contains("if (safeStage == ConnectionStage.RECONNECTING) {"));
        assertTrue(source.contains("return visibleCards == null || visibleCards.isEmpty();"));
        assertTrue(source.contains("return \"重连中\";"));
    }

    @Test
    public void hideShouldClearWindowOwnershipEvenWhenImmediateRemoveThrows() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertFalse(source.contains("} catch (Exception ignored) {\n            return;\n        }"));
        assertTrue(source.contains("windowAdded = false;"));
    }

    @Test
    public void destroyShouldCutOffAllPendingHandlerCallbacksAndReleaseViewReferences() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private boolean destroyed;"));
        assertTrue(source.contains("private final Runnable forceBlinkRelayoutRunnable = new Runnable() {"));
        assertTrue(source.contains("public void destroy() {"));
        assertTrue(source.contains("destroyed = true;"));
        assertTrue(source.contains("handler.removeCallbacksAndMessages(null);"));
        assertTrue(source.contains("hideWindowInternal();"));
        assertTrue(source.contains("binding = null;"));
        assertTrue(source.contains("layoutParams = null;"));
        assertTrue(source.contains("handler.removeCallbacks(forceBlinkRelayoutRunnable);"));
        assertFalse(source.contains("root.post(() -> {"));
    }
}

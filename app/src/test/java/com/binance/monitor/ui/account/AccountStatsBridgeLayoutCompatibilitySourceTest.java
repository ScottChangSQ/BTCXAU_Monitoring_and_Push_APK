package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsBridgeLayoutCompatibilitySourceTest {

    @Test
    public void legacyBridgeLayoutContainsSharedAnalysisSummaryIds() throws IOException {
        String layout = new String(
                Files.readAllBytes(Paths.get("src/main/res/layout/activity_account_stats.xml")),
                StandardCharsets.UTF_8
        );

        assertTrue(layout.contains("@+id/cardStatsSummarySection"));
        assertTrue(layout.contains("@+id/recyclerStructureAnalysisMetrics"));
        assertTrue(layout.contains("@+id/recyclerTradeAnalysisMetrics"));
        assertTrue(layout.contains("@+id/recyclerStatsSummaryDetails"));
        assertTrue(layout.contains("@+id/cardStatsEmptyState"));
    }

    @Test
    public void legacyBridgeLayoutShouldKeepStandardSubjectContractsForFiltersAndDateActions() throws IOException {
        String layout = new String(
                Files.readAllBytes(Paths.get("src/main/res/layout/activity_account_stats.xml")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@style/Widget.BinanceMonitor.Subject.SelectField.Label"));
        assertTrue(layout.contains("@style/Widget.BinanceMonitor.Subject.TextTrigger"));
        assertTrue(layout.contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Secondary"));
        assertTrue(layout.contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Primary"));
        assertFalse(layout.contains("@drawable/bg_spinner_filter"));
        assertFalse(layout.contains("@drawable/bg_inline_button"));

        assertTrue(source.contains("UiPaletteManager.styleSelectFieldLabel("));
        assertTrue(source.contains("UiPaletteManager.styleTextTrigger("));
        assertTrue(source.contains("UiPaletteManager.styleActionButton("));
        assertTrue(source.contains("UiPaletteManager.styleInputField("));
        assertFalse(source.contains("button.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));"));
    }
}

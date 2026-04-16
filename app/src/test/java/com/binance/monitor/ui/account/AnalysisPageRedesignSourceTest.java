package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AnalysisPageRedesignSourceTest {

    @Test
    public void analysisPageShouldShowCurveStatsStructureCardsAndTradeAnalysisEntry() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_account_stats.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String screen = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/cardCurveSection"));
        assertTrue(layout.contains("@+id/cardStatsSummarySection"));
        assertTrue(layout.contains("@+id/cardStructureAnalysisSection"));
        assertTrue(layout.contains("@+id/cardTradeAnalysisEntrySection"));
        assertTrue(screen.contains("void openTradeAnalysisPage()"));
        assertTrue(screen.contains("new Intent(activity, AccountStatsBridgeActivity.class)"));
    }
}

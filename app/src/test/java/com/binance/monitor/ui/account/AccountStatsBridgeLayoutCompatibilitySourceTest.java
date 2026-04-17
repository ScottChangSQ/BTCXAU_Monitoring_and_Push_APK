package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

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
}

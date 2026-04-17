package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AnalysisPageRedesignSourceTest {

    @Test
    public void analysisPageShouldShowCurveSummaryAndFullReturnTradeStatsSections() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_account_stats.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String screen = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/cardCurveSection"));
        assertTrue(layout.contains("@+id/cardStatsSummarySection"));
        assertTrue(layout.contains("@+id/cardReturnStatsSection"));
        assertTrue(layout.contains("@+id/cardTradeStatsSection"));
        assertTrue(layout.contains("@+id/cardStructureAnalysisSection"));
        assertTrue(layout.contains("@+id/cardTradeAnalysisEntrySection"));
        assertFalse(layout.contains("android:id=\"@+id/recyclerStats\""));
        assertTrue(screen.contains("binding.cardStructureAnalysisSection.setVisibility(View.GONE);"));
        assertTrue(screen.contains("binding.cardTradeAnalysisEntrySection.setVisibility(View.GONE);"));
        assertTrue(screen.contains("binding.cardReturnStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(screen.contains("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
        assertFalse(screen.contains("private StatsMetricAdapter statsAdapter;"));
        assertFalse(screen.contains("binding.recyclerStats.setLayoutManager(new LinearLayoutManager(this));"));
        assertFalse(screen.contains("binding.recyclerStats.setAdapter(statsAdapter);"));
    }
}

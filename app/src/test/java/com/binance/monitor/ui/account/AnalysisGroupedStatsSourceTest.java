package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AnalysisGroupedStatsSourceTest {

    @Test
    public void analysisSummaryShouldNoLongerReplaceChartsWithTwoSmallGroupedCards() throws Exception {
        String screenSource = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(screenSource.contains("private StatsMetricAdapter structureAnalysisAdapter;"));
        assertFalse(screenSource.contains("private StatsMetricAdapter tradeAnalysisAdapter;"));
        assertFalse(screenSource.contains("private List<AccountMetric> resolveStructureAnalysisMetrics() {"));
        assertFalse(screenSource.contains("private List<AccountMetric> resolveTradeAnalysisMetrics() {"));
        assertFalse(screenSource.contains("binding.recyclerStructureAnalysisMetrics.setAdapter(structureAnalysisAdapter);"));
        assertFalse(screenSource.contains("binding.recyclerTradeAnalysisMetrics.setAdapter(tradeAnalysisAdapter);"));
        assertFalse(screenSource.contains("structureAnalysisAdapter.submitList(structureMetrics);"));
        assertFalse(screenSource.contains("tradeAnalysisAdapter.submitList(tradeMetrics);"));
        assertTrue(screenSource.contains("binding.cardReturnStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(screenSource.contains("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
    }
}

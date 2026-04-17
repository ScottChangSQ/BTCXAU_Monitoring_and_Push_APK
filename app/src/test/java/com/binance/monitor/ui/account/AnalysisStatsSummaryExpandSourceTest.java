/*
 * 分析页核心统计展开源码测试，确保首屏摘要卡可以展开详细统计列表。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AnalysisStatsSummaryExpandSourceTest {

    @Test
    public void analysisSummaryCardShouldExposeExpandableDetailedStatsSection() throws Exception {
        String layout = readUtf8("src/main/res/layout/content_account_stats.xml");
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");

        assertTrue(layout.contains("@+id/tvStatsSummaryExpandHint"));
        assertTrue(layout.contains("@+id/recyclerStatsSummaryDetails"));
        assertTrue(layout.contains("@+id/tvStatsSummaryDetailsEmpty"));

        assertTrue(source.contains("private StatsMetricAdapter statsSummaryDetailAdapter;"));
        assertTrue(source.contains("private boolean statsSummaryExpanded;"));
        assertTrue(source.contains("binding.cardStatsSummarySection.setOnClickListener(v -> toggleStatsSummaryExpanded());"));
        assertTrue(source.contains("private void toggleStatsSummaryExpanded() {"));
        assertTrue(source.contains("private List<AccountMetric> resolveStatsSummaryDetailMetrics() {"));
        assertTrue(source.contains("statsSummaryDetailAdapter.submitList(detailMetrics);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

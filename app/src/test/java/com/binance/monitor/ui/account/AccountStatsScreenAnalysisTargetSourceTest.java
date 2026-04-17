package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsScreenAnalysisTargetSourceTest {

    @Test
    public void sharedAccountStatsScreenShouldOwnAnalysisTargetScrollState() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private boolean analysisTargetScrollCompleted;"));
        assertTrue(source.contains("private String pendingAnalysisTargetSection = \"\";"));
        assertTrue(source.contains("void setPendingAnalysisTargetSection(@NonNull String targetSection) {"));
        assertTrue(source.contains("pendingAnalysisTargetSection = targetSection;"));
        assertTrue(source.contains("analysisTargetScrollCompleted = false;"));
        assertTrue(source.contains("private void maybeScrollToAnalysisTarget() {"));
        assertTrue(source.contains("private View resolveAnalysisTargetView() {"));
        assertTrue(source.contains("if (AccountStatsBridgeActivity.ANALYSIS_TARGET_STRUCTURE.equals(pendingAnalysisTargetSection)) {"));
        assertTrue(source.contains("if (AccountStatsBridgeActivity.ANALYSIS_TARGET_TRADE_HISTORY.equals(pendingAnalysisTargetSection)) {"));
    }

    @Test
    public void sharedAccountStatsScreenShouldRetryAnalysisTargetScrollAfterKeySectionsBind() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("refreshAnalysisSummaryCards();\n        maybeScrollToAnalysisTarget();"));
        assertTrue(source.contains("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);\n            maybeScrollToAnalysisTarget();"));
        assertTrue(source.contains("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);\n        maybeScrollToAnalysisTarget();"));
        assertTrue(source.contains("updateTradePnlSummary(tradeSummary, product, side, FILTER_DATE);\n        maybeScrollToAnalysisTarget();"));
        assertTrue(source.contains("updateEmptyStateVisibility();\n        maybeScrollToAnalysisTarget();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountStatsScreen.java");
    }
}

package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsAnalysisAutoRefreshSourceTest {

    @Test
    public void fetchedSnapshotsShouldOnlyAutoRenderOnFirstOpenOrHistoryRevisionAdvance() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("主壳分析页应提供统一的自动重绘判定入口，明确只在首次打开或 historyRevision 变化时才重画",
                screenSource.contains("private boolean shouldAutoRenderFetchedAnalysisSnapshot(@Nullable String incomingHistoryRevision) {"));
        assertTrue("桥接分析页也应提供同口径的自动重绘判定入口，避免直达分析页和主壳页行为不一致",
                bridgeSource.contains("private boolean shouldAutoRenderFetchedAnalysisSnapshot(@Nullable String incomingHistoryRevision) {"));

        assertTrue("主壳分析页已有可渲染内容时，自动重绘应只由 historyRevision 前进触发",
                screenSource.contains("if (!hasRenderableCurrentSessionState()) {\n            return true;\n        }")
                        && screenSource.contains("String incomingRevision = trim(incomingHistoryRevision);")
                        && screenSource.contains("String appliedRevision = trim(latestHistoryRevision);")
                        && screenSource.contains("return !incomingRevision.isEmpty() && !incomingRevision.equals(appliedRevision);"));
        assertTrue("桥接分析页已有可渲染内容时，自动重绘也应只由 historyRevision 前进触发",
                bridgeSource.contains("if (!hasRenderableCurrentSessionState()) {\n            return true;\n        }")
                        && bridgeSource.contains("String incomingRevision = trim(incomingHistoryRevision);")
                        && bridgeSource.contains("String appliedRevision = trim(latestHistoryRevision);")
                        && bridgeSource.contains("return !incomingRevision.isEmpty() && !incomingRevision.equals(appliedRevision);"));

        assertTrue("主壳分析页远端快照应用入口应委托给统一自动重绘门控，避免每轮定时快照都整页重画",
                screenSource.contains("return shouldAutoRenderFetchedAnalysisSnapshot(incomingHistoryRevision);"));
        assertTrue("桥接分析页远端快照应用入口也应委托给统一自动重绘门控",
                bridgeSource.contains("return shouldAutoRenderFetchedAnalysisSnapshot(incomingHistoryRevision);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到分析页源码文件");
    }
}

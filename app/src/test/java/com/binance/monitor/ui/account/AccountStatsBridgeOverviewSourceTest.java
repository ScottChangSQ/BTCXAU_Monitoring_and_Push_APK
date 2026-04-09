package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsBridgeOverviewSourceTest {

    @Test
    public void buildOverviewMetricsShouldExposeMarginLabelInOverviewSecondRow() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("new AccountMetric(\"保证金\""));
    }

    @Test
    public void currentPositionSummaryShouldNotAddStorageFeeToTotalPnl() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(!source.contains("item.getTotalPnL() + item.getStorageFee()"));
    }

    @Test
    public void overviewHeaderShouldSkipUnchangedTitleRender() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("private String lastOverviewTitleSignature = \"\";"));
        assertTrue(source.contains("if (!titleSignature.equals(lastOverviewTitleSignature)) {"));
    }

    @Test
    public void buildOverviewMetricsShouldOverrideDailyMetricsWithAppLocalTruth() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("AccountOverviewDailyMetricsCalculator.calculate("));
        assertTrue(source.contains("replaceOrAppendOverviewMetric(result, \"当日盈亏\""));
        assertTrue(source.contains("replaceOrAppendOverviewMetric(result, \"当日收益率\""));
    }

    @Test
    public void buildOverviewMetricsShouldUseStableDisplayOrder() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("return sortOverviewMetricsForDisplay(result);"));
        assertTrue(source.contains("private List<AccountMetric> sortOverviewMetricsForDisplay(List<AccountMetric> metrics) {"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"总资产\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"净资产\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"可用预付款\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"保证金\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"累计盈亏\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"累计收益率\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"当日盈亏\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"当日收益率\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"持仓盈亏\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"持仓收益率\");"));
    }
}

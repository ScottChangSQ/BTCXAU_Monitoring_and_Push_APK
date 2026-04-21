package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeOverviewSourceTest {

    @Test
    public void overviewMetricsHelperShouldOwnOverviewMetricAssembly() throws Exception {
        Path helperFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelper.java");
        String source = new String(Files.readAllBytes(helperFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("public static List<AccountMetric> buildOverviewMetrics("));
        assertTrue(source.contains("AccountOverviewMetricsCalculator.calculate("));
        assertFalse(source.contains("AccountOverviewDailyMetricsCalculator.calculate("));
        assertFalse(source.contains("AccountOverviewCumulativeMetricsCalculator.calculate("));
    }

    @Test
    public void overviewMetricsHelperShouldApplyCanonicalOverridesAndStableOrder() throws Exception {
        Path helperFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelper.java");
        String source = new String(Files.readAllBytes(helperFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("replaceOrAppendOverviewMetric(result, \"可用预付款\""));
        assertTrue(source.contains("replaceOrAppendOverviewMetric(result, \"保证金\""));
        assertTrue(source.contains("replaceOrAppendOverviewMetric(result, \"持仓盈亏\""));
        assertTrue(source.contains("replaceOrAppendOverviewMetric(result, \"持仓收益率\""));
        assertFalse(source.contains("replaceOrAppendOverviewMetric(result, \"当日盈亏\""));
        assertFalse(source.contains("replaceOrAppendOverviewMetric(result, \"当日收益率\""));
        assertTrue(source.contains("return sortOverviewMetricsForDisplay(result);"));
        assertTrue(source.contains("private static List<AccountMetric> sortOverviewMetricsForDisplay(@Nullable List<AccountMetric> metrics) {"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"总资产\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"净资产\");"));
        assertFalse(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"累计盈亏\");"));
        assertFalse(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"累计收益率\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"可用预付款\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"保证金\");"));
        assertFalse(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"当日盈亏\");"));
        assertFalse(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"当日收益率\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"持仓盈亏\");"));
        assertTrue(source.contains("appendOverviewMetricInDisplayOrder(ordered, metricsByKey, appended, \"持仓收益率\");"));
    }

    @Test
    public void activityShouldNoLongerOwnOverviewMetricAssembly() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8);

        assertFalse(source.contains("private List<AccountMetric> buildOverviewMetrics("));
        assertFalse(source.contains("private List<AccountMetric> sortOverviewMetricsForDisplay("));
        assertFalse(source.contains("private String extractLeverageText("));
    }
}

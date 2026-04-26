/*
 * 分析页核心统计展开源码测试，确保首屏摘要卡可以展开详细统计列表。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
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
        String detailRowLayout = readUtf8("src/main/res/layout/item_stats_summary_detail_row.xml");
        String adapterSource = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/StatsSummaryDetailAdapter.java");

        assertTrue(layout.contains("@+id/ivStatsSummaryExpandToggle"));
        assertTrue(layout.contains("@+id/recyclerStatsSummaryDetails"));
        assertTrue(layout.contains("@+id/tvStatsSummaryDetailsEmpty"));
        assertTrue(layout.contains("app:srcCompat=\"@drawable/ic_chevron_right\""));

        assertTrue(source.contains("private StatsSummaryDetailAdapter statsSummaryDetailAdapter;"));
        assertTrue(source.contains("private boolean statsSummaryExpanded;"));
        assertTrue(source.contains("binding.cardStatsSummarySection.setOnClickListener(v -> toggleStatsSummaryExpanded());"));
        assertTrue(source.contains("private void toggleStatsSummaryExpanded() {"));
        assertTrue(source.contains("private List<AccountMetric> resolveStatsSummaryHeadlineMetrics() {"));
        assertTrue(source.contains("private List<AccountMetric> resolveStatsSummaryDetailMetrics() {"));
        assertTrue(source.contains("bindSummaryMetricValue(binding.tvStatsSummaryMetricOneValue,"));
        assertTrue(source.contains("getHeadlineMetric(headlineMetrics, 0)"));
        assertTrue(source.contains("private AccountMetric getHeadlineMetric(@NonNull List<AccountMetric> metrics, int index) {"));
        assertTrue(source.contains("view.setText(IndicatorPresentationPolicy.buildValueSpan("));
        assertFalse(source.contains("private void bindSummaryMetricValue(@NonNull TextView view, @NonNull String value) {"));
        assertFalse(source.contains("view.setText(value);"));
        assertTrue(source.contains("statsSummaryDetailAdapter.submitList(detailMetrics);"));
        assertFalse(source.contains("private StatsMetricAdapter statsSummaryDetailAdapter;"));
        assertTrue(source.contains("binding.ivStatsSummaryExpandToggle.setRotation(statsSummaryExpanded ? 90f : 0f);"));
        assertTrue(source.contains("binding.ivStatsSummaryExpandToggle.setContentDescription(activity.getString("));
        assertFalse(source.contains("binding.tvStatsSummaryExpandHint.setText("));

        assertTrue(detailRowLayout.contains("@+id/tvMetricLeftLabel"));
        assertTrue(detailRowLayout.contains("@+id/tvMetricLeftValue"));
        assertTrue(detailRowLayout.contains("@+id/tvMetricRightLabel"));
        assertTrue(detailRowLayout.contains("@+id/tvMetricRightValue"));
        assertTrue(detailRowLayout.contains("TextAppearance.BinanceMonitor.Meta"));
        assertTrue(detailRowLayout.contains("TextAppearance.BinanceMonitor.ValueCompact"));

        assertTrue(adapterSource.contains("rows.add(new DetailRow("));
        assertTrue(adapterSource.contains("i + 1 < data.size() ? data.get(i + 1) : null"));
        assertTrue(adapterSource.contains("IndicatorPresentationPolicy.buildValueSpan("));
        assertFalse(adapterSource.contains("不再沿用旧列表的盈亏着色"));
    }

    @Test
    public void analysisSummaryCardShouldKeepSixHeadlineMetricsAndMoveOthersToExpandedList() throws Exception {
        String layout = readUtf8("src/main/res/layout/content_account_stats.xml");
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");

        assertTrue(layout.contains("@+id/tvStatsSummaryMetricFiveLabel"));
        assertTrue(layout.contains("@+id/tvStatsSummaryMetricFiveValue"));
        assertTrue(layout.contains("@+id/tvStatsSummaryMetricSixLabel"));
        assertTrue(layout.contains("@+id/tvStatsSummaryMetricSixValue"));
        assertTrue(layout.contains("android:text=\"累计结余\""));
        assertTrue(layout.contains("android:text=\"累计收益率\""));
        assertTrue(layout.contains("android:text=\"总交易次数\""));
        assertTrue(layout.contains("android:text=\"胜率\""));
        assertTrue(layout.contains("android:text=\"最大回撤\""));
        assertTrue(layout.contains("android:text=\"夏普比率\""));
        assertTrue(layout.contains("android:id=\"@+id/tvStatsSummaryMetricOneLabel\""));
        assertTrue(layout.contains("android:id=\"@+id/tvStatsSummaryMetricOneValue\""));
        assertTrue(layout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.Meta\""));
        assertTrue(layout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.ValueCompact\""));

        assertTrue(source.contains("IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_RETURN_AMOUNT).getDisplayName()"));
        assertTrue(source.contains("IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_RETURN_RATE).getDisplayName()"));
        assertTrue(source.contains("IndicatorRegistry.require(IndicatorId.TRADE_TOTAL_COUNT).getDisplayName()"));
        assertTrue(source.contains("IndicatorRegistry.require(IndicatorId.TRADE_WIN_RATE).getDisplayName()"));
        assertTrue(source.contains("IndicatorRegistry.require(IndicatorId.ACCOUNT_MAX_DRAWDOWN).getDisplayName()"));
        assertTrue(source.contains("IndicatorRegistry.require(IndicatorId.ACCOUNT_SHARPE_RATIO).getDisplayName()"));
        assertTrue(source.contains("appendCurveMetricIfMissing(allMetrics, \"夏普比率\", \"Sharpe\", \"Sharpe Ratio\");"));
        assertTrue(source.contains("buildSharpeRatioValue(allCurvePoints)"));
        assertTrue(source.contains("Set<String> headlineNames = new java.util.HashSet<>();"));
        assertTrue(source.contains("headlineNames.add(normalizeMetricName(metric.getName()));"));
        assertTrue(source.contains("if (!headlineNames.contains(normalizedName) && !isExcludedStatsSummaryMetric(normalizedName)) {"));
        assertTrue(source.contains("private boolean isExcludedStatsSummaryMetric(@NonNull String normalizedName) {"));
        assertTrue(source.contains("\"当前持仓金额\""));
        assertTrue(source.contains("\"资产分布\""));
        assertTrue(source.contains("\"前五大持仓占比\""));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}

package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountValueNumberColorSourceTest {

    @Test
    public void mixedProfitDisplaysShouldUseSharedNumericOnlySpanHelper() throws Exception {
        String metricAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/AccountMetricAdapter.java");
        String statsBinder = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricViewBinder.java");
        String statsSummaryDetailAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/StatsSummaryDetailAdapter.java");
        String chartScreen = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String statsScreen = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
        String statsBridge = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String positionAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");
        String tradeRecordAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapterV2.java");
        String aggregateAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAggregateAdapter.java");
        String formatterCenter = readUtf8("src/main/java/com/binance/monitor/ui/rules/IndicatorFormatterCenter.java");
        String presentationPolicy = readUtf8("src/main/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicy.java");

        assertTrue(metricAdapter.contains("IndicatorPresentationPolicy.buildValueSpan("));
        assertTrue(statsBinder.contains("IndicatorPresentationPolicy.buildValueSpan("));
        assertTrue(statsBinder.contains("IndicatorPresentationPolicy.applyDirectionalSpanForValueRange("));
        assertTrue(statsSummaryDetailAdapter.contains("IndicatorPresentationPolicy.buildValueSpan("));
        assertTrue(chartScreen.contains("IndicatorPresentationPolicy.buildDirectionalSpanAfterAnchor("));
        assertTrue(statsScreen.contains("IndicatorPresentationPolicy.buildDirectionalSpanAfterAnchor("));
        assertTrue(statsBridge.contains("IndicatorPresentationPolicy.buildDirectionalSpanAfterAnchor("));
        assertTrue(statsScreen.contains("IndicatorFormatterCenter.formatCount("));
        assertTrue(statsBridge.contains("IndicatorFormatterCenter.formatCount("));
        assertTrue(statsScreen.contains("IndicatorPresentationPolicy.present("));
        assertTrue(statsBridge.contains("IndicatorPresentationPolicy.present("));
        assertTrue(formatterCenter.contains("formatMoney("));
        assertTrue(formatterCenter.contains("formatPercent("));
        assertTrue(presentationPolicy.contains("applyDirectionalSpanForExactToken("));
        assertTrue(positionAdapter.contains("IndicatorPresentationPolicy.applyDirectionalSpanForExactToken("));
        assertTrue(tradeRecordAdapter.contains("IndicatorPresentationPolicy.applyDirectionalSpanForExactToken("));
        assertTrue(aggregateAdapter.contains("IndicatorPresentationPolicy.applyDirectionalSpanForExactToken("));
        assertFalse(statsScreen.contains("private String signedMoney("));
        assertFalse(statsBridge.contains("private String signedMoney("));
        assertFalse(statsScreen.contains("private int resolveSignedValueColor("));
        assertFalse(statsBridge.contains("private int resolveSignedValueColor("));
        assertFalse(chartScreen.contains("binding.tvChartPositionSummary.setText(overlaySnapshot.getPositionSummaryText());"));
        assertFalse(statsBinder.contains("new ForegroundColorSpan("));
        assertFalse(statsBinder.contains("R.color.pnl_profit"));
        assertFalse(statsBinder.contains("R.color.pnl_loss"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

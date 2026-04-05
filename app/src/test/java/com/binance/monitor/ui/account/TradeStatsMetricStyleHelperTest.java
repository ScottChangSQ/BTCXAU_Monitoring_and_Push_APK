/*
 * 交易统计样式辅助测试，确保最大连续盈亏会把整段数值一起着色。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TradeStatsMetricStyleHelperTest {

    @Test
    public void consecutiveLossMetricShouldTintWholeValue() {
        assertEquals(5, TradeStatsMetricStyleHelper.resolveStreakTintStart("最大连续亏损", "(3次) -$456.78"));
    }

    @Test
    public void consecutiveWinMetricShouldTintWholeValue() {
        assertEquals(5, TradeStatsMetricStyleHelper.resolveStreakTintStart("最大连续盈利", "(5次) +$123.45"));
    }

    @Test
    public void genericProfitMetricShouldStillStartFromAmountSign() {
        assertEquals(5, TradeStatsMetricStyleHelper.resolveStreakTintStart("最好交易", "(1次) +$8.00"));
    }
}

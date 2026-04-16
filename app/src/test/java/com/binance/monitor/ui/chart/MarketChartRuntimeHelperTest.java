/*
 * 行情图运行态辅助测试，确保保存下来的周期键能正确恢复。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MarketChartRuntimeHelperTest {

    @Test
    public void resolveStoredIntervalKeyShouldKeepSupportedValue() {
        String key = MarketChartRuntimeHelper.resolveStoredIntervalKey(
                "4h",
                "15m",
                new String[]{"1m", "5m", "15m", "4h"}
        );

        assertEquals("4h", key);
    }

    @Test
    public void resolveStoredIntervalKeyShouldFallbackWhenValueIsUnknown() {
        String key = MarketChartRuntimeHelper.resolveStoredIntervalKey(
                "2h",
                "15m",
                new String[]{"1m", "5m", "15m", "4h"}
        );

        assertEquals("15m", key);
    }

    @Test
    public void resolveStoredIntervalKeyShouldNotTreatMonthlyAsMinute() {
        String key = MarketChartRuntimeHelper.resolveStoredIntervalKey(
                "1M",
                "15m",
                new String[]{"1m", "15m", "1M"}
        );

        assertEquals("1M", key);
    }
}

/*
 * 交易统计文本格式测试，确保双行指标压成单行后仍保持可读。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TradeStatsTextFormatterTest {

    @Test
    public void formatTradeRatioShouldMergeCountAndRateIntoOneLine() {
        assertEquals("(12次) 58.33%", TradeStatsTextFormatter.formatTradeRatioMetric(12, 0.5833d));
    }

    @Test
    public void formatStreakShouldMergeCountAndAmountIntoOneLine() {
        assertEquals("(3次) +$1,250.50", TradeStatsTextFormatter.formatStreakMetric(3, 1250.5d));
    }

    @Test
    public void formatStreakShouldKeepEmptyStateCompact() {
        assertEquals("(0次) --", TradeStatsTextFormatter.formatStreakMetric(0, 0d));
    }

    @Test
    public void formatGrossPairShouldMergeProfitAndLossIntoOneLine() {
        assertEquals("+$3,200.00 / -$640.00", TradeStatsTextFormatter.formatGrossPair(3200d, -640d));
    }
}

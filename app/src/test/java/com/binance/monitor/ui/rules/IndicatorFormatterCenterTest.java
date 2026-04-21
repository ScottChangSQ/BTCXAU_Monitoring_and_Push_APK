/*
 * 全局格式中心测试，锁定金额、百分比和数量的统一输出规则。
 */
package com.binance.monitor.ui.rules;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IndicatorFormatterCenterTest {

    @Test
    public void formatterCenterShouldFormatPercentAndMoneyConsistently() {
        assertEquals("+12.35%", IndicatorFormatterCenter.formatPercent(0.12345, 2, true));
        assertEquals("-$56.70", IndicatorFormatterCenter.formatMoney(-56.7, 2, false));
    }

    @Test
    public void formatterCenterShouldFormatSignedQuantityConsistently() {
        assertEquals("+0.03手", IndicatorFormatterCenter.formatSignedQuantity(0.03d, 2, "手"));
        assertEquals("-0.10手", IndicatorFormatterCenter.formatSignedQuantity(-0.10d, 2, "手"));
    }
}

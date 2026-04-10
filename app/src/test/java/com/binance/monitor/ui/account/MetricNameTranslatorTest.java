package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.runtime.account.MetricNameTranslator;

import org.junit.Test;

public class MetricNameTranslatorTest {

    @Test
    public void toChineseShouldTranslatePositionReturnToPositionPnlRate() {
        assertEquals("持仓收益率", MetricNameTranslator.toChinese("Position Return"));
    }
}

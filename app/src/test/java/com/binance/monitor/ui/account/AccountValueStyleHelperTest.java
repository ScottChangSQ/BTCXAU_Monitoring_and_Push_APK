package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccountValueStyleHelperTest {

    @Test
    public void resolveMetricDirectionShouldParseSignedUsdValues() {
        assertEquals(
                AccountValueStyleHelper.Direction.POSITIVE,
                AccountValueStyleHelper.resolveMetricDirection("持仓盈亏", "+$123.45")
        );
        assertEquals(
                AccountValueStyleHelper.Direction.NEGATIVE,
                AccountValueStyleHelper.resolveMetricDirection("累计盈亏", "-$67.89")
        );
        assertEquals(
                AccountValueStyleHelper.Direction.NEUTRAL,
                AccountValueStyleHelper.resolveMetricDirection("当日盈亏", "$0.00")
        );
    }
}

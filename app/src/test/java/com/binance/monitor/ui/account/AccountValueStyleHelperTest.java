/*
 * 账户数值样式辅助测试，确保盈亏/收益为 0 时回到中性色，不再误显示成红绿。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccountValueStyleHelperTest {

    @Test
    public void resolveNumericDirection_returnsNeutral_whenValueIsZero() {
        assertEquals(AccountValueStyleHelper.Direction.NEUTRAL,
                AccountValueStyleHelper.resolveNumericDirection(0d));
    }

    @Test
    public void resolveNumericDirection_returnsPositiveAndNegative_forNonZeroValues() {
        assertEquals(AccountValueStyleHelper.Direction.POSITIVE,
                AccountValueStyleHelper.resolveNumericDirection(12.5d));
        assertEquals(AccountValueStyleHelper.Direction.NEGATIVE,
                AccountValueStyleHelper.resolveNumericDirection(-0.8d));
    }

    @Test
    public void resolveMetricDirection_returnsNeutral_whenRenderedValueIsZero() {
        assertEquals(AccountValueStyleHelper.Direction.NEUTRAL,
                AccountValueStyleHelper.resolveMetricDirection("累计收益率", "+0.00%"));
        assertEquals(AccountValueStyleHelper.Direction.NEUTRAL,
                AccountValueStyleHelper.resolveMetricDirection("最大回撤", "0.00%"));
    }

    @Test
    public void resolveMetricDirection_ignoresNonProfitLikeLabels() {
        assertEquals(AccountValueStyleHelper.Direction.NONE,
                AccountValueStyleHelper.resolveMetricDirection("交易次数", "0 次"));
    }

    @Test
    public void resolveMetricDirection_returnsNone_whenValueMissing() {
        assertEquals(AccountValueStyleHelper.Direction.NONE,
                AccountValueStyleHelper.resolveMetricDirection("累计收益率", "--"));
    }
}

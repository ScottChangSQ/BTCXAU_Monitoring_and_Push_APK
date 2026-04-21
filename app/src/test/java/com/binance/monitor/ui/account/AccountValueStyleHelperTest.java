package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

    @Test
    public void resolveMetricDirectionShouldCoverAverageProfitAndLossButSkipCountPairs() {
        assertEquals(
                AccountValueStyleHelper.Direction.POSITIVE,
                AccountValueStyleHelper.resolveMetricDirection("平均每笔盈利", "+$123.45")
        );
        assertEquals(
                AccountValueStyleHelper.Direction.NEGATIVE,
                AccountValueStyleHelper.resolveMetricDirection("平均每笔亏损", "-$67.89")
        );
        assertEquals(
                AccountValueStyleHelper.Direction.NONE,
                AccountValueStyleHelper.resolveMetricDirection("盈利/亏损", "12 / 5")
        );
    }

    @Test
    public void findFirstNumericTokenRangeShouldIncludeSignCurrencyAndPercent() {
        String positiveMoney = "+$123.45";
        AccountValueStyleHelper.NumericTokenRange positiveMoneyRange =
                AccountValueStyleHelper.findFirstNumericTokenRange(positiveMoney);
        assertNotNull(positiveMoneyRange);
        assertEquals("+$123.45",
                positiveMoney.substring(positiveMoneyRange.getStart(), positiveMoneyRange.getEnd()));

        String negativePercent = "-8.88%";
        AccountValueStyleHelper.NumericTokenRange negativePercentRange =
                AccountValueStyleHelper.findFirstNumericTokenRange(negativePercent);
        assertNotNull(negativePercentRange);
        assertEquals("-8.88%",
                negativePercent.substring(negativePercentRange.getStart(), negativePercentRange.getEnd()));
    }

    @Test
    public void findNumericTokenRangeAfterAnchorShouldSkipFieldText() {
        String raw = "盈亏：+$123.45 | 持仓：0.10手";
        AccountValueStyleHelper.NumericTokenRange range =
                AccountValueStyleHelper.findNumericTokenRangeAfterAnchor(raw, "盈亏：");

        assertNotNull(range);
        assertEquals("+$123.45", raw.substring(range.getStart(), range.getEnd()));
    }

    @Test
    public void findNumericTokenRangeAfterAnchorShouldReturnNullForMaskedText() {
        assertNull(AccountValueStyleHelper.findNumericTokenRangeAfterAnchor("盈亏：**** | 持仓：****", "盈亏："));
        assertNull(AccountValueStyleHelper.findFirstNumericTokenRange("--"));
    }

    @Test
    public void findNumericTokenRangeForExactTokenShouldSupportFirstAndLastMatch() {
        String raw = "持仓盈亏 +$12.34 | 收益率 +1.23% | 累计盈亏 +$12.34";

        AccountValueStyleHelper.NumericTokenRange firstRange =
                AccountValueStyleHelper.findNumericTokenRangeForExactToken(raw, "+$12.34", false);
        AccountValueStyleHelper.NumericTokenRange lastRange =
                AccountValueStyleHelper.findNumericTokenRangeForExactToken(raw, "+$12.34", true);

        assertNotNull(firstRange);
        assertNotNull(lastRange);
        assertEquals("+$12.34", raw.substring(firstRange.getStart(), firstRange.getEnd()));
        assertEquals("+$12.34", raw.substring(lastRange.getStart(), lastRange.getEnd()));
        assertEquals(raw.indexOf("+$12.34"), firstRange.getStart());
        assertEquals(raw.lastIndexOf("+$12.34"), lastRange.getStart());
    }
}

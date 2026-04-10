/*
 * 金额格式测试，确保盈亏金额统一输出为 +/-$数字 的样式。
 */
package com.binance.monitor.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FormatUtilsMoneyFormatTest {

    @Test
    public void shouldFormatPositiveSignedMoneyWithDollarAfterSign() {
        assertEquals("+$1,234.56", FormatUtils.formatSignedMoney(1234.56));
    }

    @Test
    public void shouldFormatNegativeSignedMoneyWithDollarAfterSign() {
        assertEquals("-$1,234.56", FormatUtils.formatSignedMoney(-1234.56));
    }

    @Test
    public void shouldFormatPositiveSignedMoneyWithoutDecimal() {
        assertEquals("+$1,235", FormatUtils.formatSignedMoneyNoDecimal(1234.56));
    }

    @Test
    public void shouldFormatNegativeSignedMoneyWithoutDecimal() {
        assertEquals("-$1,235", FormatUtils.formatSignedMoneyNoDecimal(-1234.56));
    }

    @Test
    public void shouldFormatZeroSignedMoneyWithoutPrefix() {
        assertEquals("$0.00", FormatUtils.formatSignedMoney(0d));
    }

    @Test
    public void shouldFormatRoundedZeroSignedMoneyWithoutPrefix() {
        assertEquals("$0.00", FormatUtils.formatSignedMoney(0.004d));
        assertEquals("$0", FormatUtils.formatSignedMoneyNoDecimal(0.4d));
    }

    @Test
    public void shouldFormatSignedMoneyWithOneDecimal() {
        assertEquals("+$1,234.6", FormatUtils.formatSignedMoneyOneDecimal(1234.56));
        assertEquals("-$1,234.6", FormatUtils.formatSignedMoneyOneDecimal(-1234.56));
    }

    @Test
    public void shouldFormatPriceWithOneDecimalUnit() {
        assertEquals("$1,234.6", FormatUtils.formatPriceOneDecimalWithUnit(1234.56));
    }

    @Test
    public void shouldFormatAmountWithChineseWanUnit() {
        assertEquals("123.46万$", FormatUtils.formatAmountWithChineseUnit(1_234_567d));
    }

    @Test
    public void shouldFormatAmountWithChineseYiUnit() {
        assertEquals("1.23亿$", FormatUtils.formatAmountWithChineseUnit(123_456_789d));
    }
}

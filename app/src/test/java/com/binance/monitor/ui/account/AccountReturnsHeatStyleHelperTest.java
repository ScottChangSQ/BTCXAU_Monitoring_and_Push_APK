/*
 * 收益统计热力样式测试，确保收益率越大时月份底色越明显，且正负收益分别走红绿色。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountReturnsHeatStyleHelperTest {

    // 无收益率时应保持基础底色，不要额外着色。
    @Test
    public void resolveFillColor_returnsBaseColor_whenRateMissing() {
        int baseColor = 0xFF101820;
        int positiveColor = 0xFF1DB954;
        int negativeColor = 0xFFE64B4B;

        int actual = AccountReturnsHeatStyleHelper.resolveFillColor(baseColor, positiveColor, negativeColor, null);

        assertEquals(baseColor, actual);
    }

    // 收益率为 0 时也应保持基础底色，不应误染成绿色。
    @Test
    public void resolveFillColor_returnsBaseColor_whenRateIsZero() {
        int baseColor = 0xFF101820;
        int positiveColor = 0xFF1DB954;
        int negativeColor = 0xFFE64B4B;

        int actual = AccountReturnsHeatStyleHelper.resolveFillColor(baseColor, positiveColor, negativeColor, 0d);

        assertEquals(baseColor, actual);
    }

    // 正负收益应分别走不同色系，避免月收益表仍然一片同色。
    @Test
    public void resolveFillColor_usesDifferentHue_forPositiveAndNegativeRate() {
        int baseColor = 0xFF101820;
        int positiveColor = 0xFF1DB954;
        int negativeColor = 0xFFE64B4B;

        int positiveFill = AccountReturnsHeatStyleHelper.resolveFillColor(baseColor, positiveColor, negativeColor, 0.12d);
        int negativeFill = AccountReturnsHeatStyleHelper.resolveFillColor(baseColor, positiveColor, negativeColor, -0.12d);

        assertNotEquals(baseColor, positiveFill);
        assertNotEquals(baseColor, negativeFill);
        assertNotEquals(positiveFill, negativeFill);
    }

    // 收益率幅度越大，月份格底色应越深，保持和日收益表一致的热力感。
    @Test
    public void resolveFillColor_strengthensAsRateMagnitudeIncreases() {
        int baseColor = 0xFF101820;
        int positiveColor = 0xFF1DB954;
        int negativeColor = 0xFFE64B4B;

        int low = AccountReturnsHeatStyleHelper.resolveFillColor(baseColor, positiveColor, negativeColor, 0.03d);
        int high = AccountReturnsHeatStyleHelper.resolveFillColor(baseColor, positiveColor, negativeColor, 0.18d);

        assertTrue(colorDistance(baseColor, high) > colorDistance(baseColor, low));
    }

    // 计算两个颜色的通道距离，用于比较热力强弱。
    private static int colorDistance(int startColor, int endColor) {
        return Math.abs(((startColor >> 16) & 0xFF) - ((endColor >> 16) & 0xFF))
                + Math.abs(((startColor >> 8) & 0xFF) - ((endColor >> 8) & 0xFF))
                + Math.abs((startColor & 0xFF) - (endColor & 0xFF));
    }
}

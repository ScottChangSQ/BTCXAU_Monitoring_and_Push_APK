/*
 * 收益统计热力样式辅助，负责按收益率幅度生成统一的红绿深浅底色。
 * 供账户统计页的日收益、月收益等表格复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

public final class AccountReturnsHeatStyleHelper {

    private AccountReturnsHeatStyleHelper() {
    }

    // 根据收益率生成底色，收益幅度越大颜色越深。
    public static int resolveFillColor(int baseColor,
                                       int riseColor,
                                       int fallColor,
                                       @Nullable Double rate) {
        if (rate == null) {
            return baseColor;
        }
        float magnitude = (float) Math.abs(rate);
        float intensity = Math.min(1f, 0.22f + magnitude * 4.8f);
        float blendRatio = 0.16f + intensity * 0.46f;
        return blendColor(baseColor, rate >= 0d ? riseColor : fallColor, blendRatio);
    }

    // 计算两个颜色的混合结果，避免直接使用纯红纯绿导致文字可读性下降。
    private static int blendColor(int startColor, int endColor, float ratio) {
        float safeRatio = Math.max(0f, Math.min(1f, ratio));
        int startA = (startColor >> 24) & 0xFF;
        int startR = (startColor >> 16) & 0xFF;
        int startG = (startColor >> 8) & 0xFF;
        int startB = startColor & 0xFF;
        int endA = (endColor >> 24) & 0xFF;
        int endR = (endColor >> 16) & 0xFF;
        int endG = (endColor >> 8) & 0xFF;
        int endB = endColor & 0xFF;
        return ((Math.round(startA + (endA - startA) * safeRatio) & 0xFF) << 24)
                | ((Math.round(startR + (endR - startR) * safeRatio) & 0xFF) << 16)
                | ((Math.round(startG + (endG - startG) * safeRatio) & 0xFF) << 8)
                | (Math.round(startB + (endB - startB) * safeRatio) & 0xFF);
    }
}

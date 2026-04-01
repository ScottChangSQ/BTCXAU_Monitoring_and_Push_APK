/*
 * 账户统计页隐私文案格式化工具，统一处理标题、更新时间和通用数值打码。
 * 供 AccountStatsBridgeActivity 与账户统计图表区复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.util.SensitiveDisplayMasker;

public final class AccountStatsPrivacyFormatter {

    private AccountStatsPrivacyFormatter() {
    }

    // 生成账户总览标题，隐私关闭时统一隐藏账户号。
    public static String formatOverviewTitle(@Nullable String displayAccount, boolean masked) {
        return "账户-" + maskValue(displayAccount, masked);
    }

    // 生成更新时间文案，隐私关闭时只保留前缀。
    public static String formatRefreshMeta(@Nullable String refreshMeta, boolean masked) {
        return "更新时间 " + maskValue(refreshMeta, masked);
    }

    // 统一处理账户统计页各类值打码。
    public static String maskValue(@Nullable String value, boolean masked) {
        return SensitiveDisplayMasker.maskValue(value, masked);
    }

    // 隐私隐藏时统一回退到中性色，避免收益表仍保留红绿倾向。
    public static int resolveValueColor(@Nullable Integer actualColor, int neutralColor, boolean masked) {
        if (masked) {
            return neutralColor;
        }
        return actualColor == null ? neutralColor : actualColor;
    }
}

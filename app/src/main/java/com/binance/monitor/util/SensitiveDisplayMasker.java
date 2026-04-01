/*
 * 敏感数据显示辅助类，统一处理隐私模式下的账户号、价格、数量和金额文案遮挡。
 * 供主监控页、行情图页、账户页和悬浮窗复用。
 */
package com.binance.monitor.util;

import android.content.Context;

import com.binance.monitor.data.local.ConfigManager;

public final class SensitiveDisplayMasker {
    public static final String MASK_TEXT = "****";

    private SensitiveDisplayMasker() {
    }

    // 读取当前是否开启隐私隐藏。
    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return ConfigManager.getInstance(context.getApplicationContext()).isDataMasked();
    }

    // 遮挡账户号。
    public static String maskAccount(String value, boolean masked) {
        return maskValue(value, masked);
    }

    // 遮挡账户号文本，供当前持仓模块与账户统计统一复用。
    public static String maskAccountId(String value, boolean masked) {
        return maskValue(value, masked);
    }

    // 遮挡价格文本。
    public static String maskPrice(String value, boolean masked) {
        return maskValue(value, masked);
    }

    // 遮挡数量文本。
    public static String maskQuantity(String value, boolean masked) {
        return maskValue(value, masked);
    }

    // 遮挡金额文本。
    public static String maskAmount(String value, boolean masked) {
        return maskValue(value, masked);
    }

    // 遮挡带正负号的盈亏文本。
    public static String maskSignedValue(String value, boolean masked) {
        return maskValue(value, masked);
    }

    // 遮挡通用敏感值，缺省占位符保持原样。
    public static String maskValue(String value, boolean masked) {
        String safeValue = value == null ? "" : value;
        if (!masked) {
            return safeValue;
        }
        String trimmed = safeValue.trim();
        if (trimmed.isEmpty() || "--".equals(trimmed)) {
            return safeValue;
        }
        return MASK_TEXT;
    }
}

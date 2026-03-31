/*
 * MT5 网关地址兼容帮助类，保留旧测试和旧调用点使用的入口。
 * 真实规范化逻辑统一委托给 GatewayUrlResolver。
 */
package com.binance.monitor.util;

import androidx.annotation.Nullable;

public final class Mt5GatewayAddressHelper {

    private Mt5GatewayAddressHelper() {
    }

    // 兼容旧调用方式，返回可直接拼接 /v1/... 的基础地址。
    public static String normalizeBaseUrl(@Nullable String raw, @Nullable String fallback) {
        return GatewayUrlResolver.resolveBaseUrl(raw, fallback);
    }
}

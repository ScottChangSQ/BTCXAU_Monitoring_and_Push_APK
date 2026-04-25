package com.binance.monitor.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.local.ConfigManager;

import okhttp3.Request;

/**
 * 统一为 MT5 网关请求附带鉴权头，避免各个客户端各自分叉。
 */
public final class GatewayAuthRequestHelper {
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_GATEWAY_AUTH_TOKEN = "X-Gateway-Auth-Token";

    private GatewayAuthRequestHelper() {
    }

    @NonNull
    public static Request.Builder applyGatewayAuth(@NonNull Request.Builder builder,
                                                   @Nullable ConfigManager configManager) {
        String authToken = resolveGatewayAuthToken(configManager);
        if (authToken.isEmpty()) {
            return builder;
        }
        return builder
                .header(HEADER_AUTHORIZATION, "Bearer " + authToken)
                .header(HEADER_GATEWAY_AUTH_TOKEN, authToken);
    }

    @NonNull
    public static String resolveGatewayAuthToken(@Nullable ConfigManager configManager) {
        if (configManager == null) {
            return "";
        }
        String authToken = configManager.getMt5GatewayAuthToken();
        return authToken == null ? "" : authToken.trim();
    }
}

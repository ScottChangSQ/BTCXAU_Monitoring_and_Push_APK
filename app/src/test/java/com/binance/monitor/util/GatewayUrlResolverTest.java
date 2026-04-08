/*
 * 验证 MT5 网关地址输入会被规范化，避免设置页填了 host、health 或完整接口后无法请求。
 */
package com.binance.monitor.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GatewayUrlResolverTest {

    @Test
    public void resolveBaseUrlAddsHttpSchemeAndTrimsSlash() {
        assertEquals(
                "http://43.155.214.62:8787",
                GatewayUrlResolver.resolveBaseUrl("43.155.214.62:8787/", "http://10.0.2.2:8787")
        );
    }

    @Test
    public void resolveBaseUrlShouldUseHttpsSchemeWhenHostOnlyInputTargetsSecureGateway() {
        assertEquals(
                "https://tradeapp.ltd",
                GatewayUrlResolver.resolveBaseUrl("tradeapp.ltd", "https://tradeapp.ltd")
        );
    }

    @Test
    public void resolveBaseUrlStripsHealthAndApiPath() {
        assertEquals(
                "http://43.155.214.62:8787",
                GatewayUrlResolver.resolveBaseUrl("http://43.155.214.62:8787/health", "http://10.0.2.2:8787")
        );
        assertEquals(
                "http://43.155.214.62:8787",
                GatewayUrlResolver.resolveBaseUrl("http://43.155.214.62:8787/v1/live?range=all", "http://10.0.2.2:8787")
        );
    }

    @Test
    public void resolveBaseUrlKeepsReverseProxyPrefix() {
        assertEquals(
                "http://43.155.214.62/mt5",
                GatewayUrlResolver.resolveBaseUrl("http://43.155.214.62/mt5/health", "http://10.0.2.2:8787")
        );
    }

    @Test
    public void buildEndpointJoinsBaseAndPathWithSingleSlash() {
        assertEquals(
                "http://43.155.214.62:8787/v1/live",
                GatewayUrlResolver.buildEndpoint("http://43.155.214.62:8787/", "/v1/live")
        );
    }

    @Test
    public void buildBinanceUrlsFromReverseProxyMt5Base() {
        assertEquals(
                "http://43.155.214.62/binance-rest/fapi/v1/klines",
                GatewayUrlResolver.buildBinanceRestBaseUrl("http://43.155.214.62/mt5", "http://10.0.2.2:8787")
        );
        assertEquals(
                "ws://43.155.214.62/binance-ws",
                GatewayUrlResolver.buildBinanceWebSocketBaseUrl("http://43.155.214.62/mt5", "http://10.0.2.2:8787")
        );
    }

    @Test
    public void resolveBinanceUrlsShouldPreferExplicitDefaultsWhenMt5BaseMatchesDefault() {
        assertEquals(
                "http://43.155.214.62/binance-rest/fapi/v1/klines",
                GatewayUrlResolver.resolveBinanceRestBaseUrl(
                        "http://43.155.214.62:8787",
                        "http://43.155.214.62/binance-rest/fapi/v1/klines",
                        "http://43.155.214.62:8787"
                )
        );
        assertEquals(
                "ws://43.155.214.62/binance-ws/ws/",
                GatewayUrlResolver.resolveBinanceWebSocketBaseUrl(
                        "http://43.155.214.62:8787",
                        "ws://43.155.214.62/binance-ws/ws/",
                        "http://43.155.214.62:8787"
                )
        );
    }

    @Test
    public void resolveBinanceUrlsShouldDeriveFromCustomMt5Base() {
        assertEquals(
                "http://10.10.10.8/binance-rest/fapi/v1/klines",
                GatewayUrlResolver.resolveBinanceRestBaseUrl(
                        "http://10.10.10.8/mt5",
                        "http://43.155.214.62/binance-rest/fapi/v1/klines",
                        "http://43.155.214.62:8787"
                )
        );
        assertEquals(
                "ws://10.10.10.8/binance-ws",
                GatewayUrlResolver.resolveBinanceWebSocketBaseUrl(
                        "http://10.10.10.8/mt5",
                        "ws://43.155.214.62/binance-ws/ws/",
                        "http://43.155.214.62:8787"
                )
        );
    }

    @Test
    public void alignGatewayBaseUrlToTargetShouldCoerceSameHostVariants() {
        assertEquals(
                "http://43.155.214.62:8787",
                GatewayUrlResolver.alignGatewayBaseUrlToTarget(
                        "http://43.155.214.62/mt5",
                        "http://43.155.214.62:8787"
                )
        );
        assertEquals(
                "http://43.155.214.62/mt5",
                GatewayUrlResolver.alignGatewayBaseUrlToTarget(
                        "http://43.155.214.62:8787",
                        "http://43.155.214.62/mt5"
                )
        );
    }

    @Test
    public void alignGatewayBaseUrlToTargetShouldMigrateSameHostLegacyPortToRoot() {
        assertEquals(
                "http://43.155.214.62",
                GatewayUrlResolver.alignGatewayBaseUrlToTarget(
                        "http://43.155.214.62:8787",
                        "http://43.155.214.62"
                )
        );
        assertEquals(
                "http://43.155.214.62",
                GatewayUrlResolver.alignGatewayBaseUrlToTarget(
                        "http://43.155.214.62/mt5",
                        "http://43.155.214.62"
                )
        );
    }

    @Test
    public void resolveBinanceUrlsShouldKeepExplicitDefaultsWhenGatewayBaseIsRoot() {
        assertEquals(
                "http://43.155.214.62/binance-rest/fapi/v1/klines",
                GatewayUrlResolver.resolveBinanceRestBaseUrl(
                        "http://43.155.214.62",
                        "http://43.155.214.62/binance-rest/fapi/v1/klines",
                        "http://43.155.214.62"
                )
        );
        assertEquals(
                "ws://43.155.214.62/binance-ws/ws/",
                GatewayUrlResolver.resolveBinanceWebSocketBaseUrl(
                        "http://43.155.214.62",
                        "ws://43.155.214.62/binance-ws/ws/",
                        "http://43.155.214.62"
                )
        );
    }
}

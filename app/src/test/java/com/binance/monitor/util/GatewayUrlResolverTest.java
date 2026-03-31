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
}

package com.binance.monitor.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Mt5GatewayAddressHelperTest {

    @Test
    public void normalizeFallsBackWhenInputIsBlank() {
        assertEquals(
                "http://43.155.214.62:8787",
                Mt5GatewayAddressHelper.normalizeBaseUrl("  ", "http://43.155.214.62:8787")
        );
    }

    @Test
    public void normalizeAddsSchemeAndDefaultPortForBareHost() {
        assertEquals(
                "http://43.155.214.62:8787",
                Mt5GatewayAddressHelper.normalizeBaseUrl("43.155.214.62", "http://10.0.2.2:8787")
        );
    }

    @Test
    public void normalizeKeepsExplicitPortAndTrimsTrailingSlash() {
        assertEquals(
                "http://43.155.214.62:9999",
                Mt5GatewayAddressHelper.normalizeBaseUrl("http://43.155.214.62:9999/", "http://10.0.2.2:8787")
        );
    }
}

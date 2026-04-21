package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GatewayV2SessionDiagnosticClientTest {

    @Test
    public void buildSessionDiagnosticTimelineShouldFormatChronologicalTrace() throws Exception {
        String body = "{"
                + "\"requestId\":\"req-session-diag-001\","
                + "\"items\":["
                + "{\"stage\":\"request_received\",\"message\":\"收到登录请求\",\"serverTime\":1776670000000},"
                + "{\"stage\":\"legacy_login_failed\",\"message\":\"legacy login 在 95s 后失败\",\"serverTime\":1776670095000}"
                + "]"
                + "}";

        String timeline = GatewayV2SessionClient.buildSessionDiagnosticTimeline(body);

        assertTrue(timeline.contains("requestId=req-session-diag-001"));
        assertTrue(timeline.contains("request_received"));
        assertTrue(timeline.contains("legacy_login_failed"));
        assertTrue(timeline.contains("legacy login 在 95s 后失败"));
    }

    @Test
    public void buildSessionDiagnosticTimelineShouldReturnEmptyWhenItemsMissing() throws Exception {
        String timeline = GatewayV2SessionClient.buildSessionDiagnosticTimeline("{\"requestId\":\"req-empty\",\"items\":[]}");

        assertEquals("", timeline);
    }
}

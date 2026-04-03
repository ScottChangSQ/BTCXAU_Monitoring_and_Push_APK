package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class GatewayV2StreamClientTest {

    @Test
    public void parseMessageShouldKeepTypeSyncTokenAndPayload() throws Exception {
        String body = "{"
                + "\"type\":\"syncDelta\","
                + "\"serverTime\":123,"
                + "\"syncToken\":\"token-1\","
                + "\"payload\":{\"unchanged\":false,\"marketDelta\":[],\"accountDelta\":[]}"
                + "}";

        GatewayV2StreamClient.StreamMessage message = GatewayV2StreamClient.parseMessage(body);

        assertEquals("syncDelta", message.getType());
        assertEquals(123L, message.getServerTime());
        assertEquals("token-1", message.getSyncToken());
        assertFalse(message.getPayload().optBoolean("unchanged", true));
    }

    @Test
    public void parseMessageShouldTreatMissingPayloadAsEmptyObject() throws Exception {
        String body = "{"
                + "\"type\":\"syncSummary\","
                + "\"serverTime\":456,"
                + "\"syncToken\":\"token-2\""
                + "}";

        GatewayV2StreamClient.StreamMessage message = GatewayV2StreamClient.parseMessage(body);

        assertEquals("syncSummary", message.getType());
        assertTrue(message.getPayload() instanceof JSONObject);
        assertEquals(0, message.getPayload().length());
    }
}

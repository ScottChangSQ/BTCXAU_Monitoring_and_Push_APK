package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class GatewayV2StreamClientTest {

    @Test
    public void parseMessageShouldReadRootLevelEventFields() throws Exception {
        String body = "{"
                + "\"type\":\"runtimeChanged\","
                + "\"busSeq\":12,"
                + "\"publishedAt\":1000,"
                + "\"serverTime\":123,"
                + "\"revisions\":{\"marketRevision\":\"market-1\",\"accountRuntimeRevision\":\"account-2\"},"
                + "\"changes\":{\"market\":{\"snapshot\":{\"symbolStates\":[]}},\"accountRuntime\":{\"snapshot\":{\"positions\":[]}}}"
                + "}";

        GatewayV2StreamClient.StreamMessage message = GatewayV2StreamClient.parseMessage(body);

        assertEquals("runtimeChanged", message.getType());
        assertEquals(12L, message.getBusSeq());
        assertEquals(1000L, message.getPublishedAt());
        assertEquals(123L, message.getServerTime());
        assertEquals("market-1", message.getRevisions().optString("marketRevision"));
        assertTrue(message.getChanges().has("market"));
        assertTrue(message.getChanges().has("accountRuntime"));
    }

    @Test
    public void parseMessageShouldTreatMissingRevisionsAndChangesAsEmptyObjects() throws Exception {
        String body = "{"
                + "\"type\":\"heartbeat\","
                + "\"busSeq\":13,"
                + "\"publishedAt\":2000,"
                + "\"serverTime\":456"
                + "}";

        GatewayV2StreamClient.StreamMessage message = GatewayV2StreamClient.parseMessage(body);

        assertEquals("heartbeat", message.getType());
        assertTrue(message.getRevisions() instanceof JSONObject);
        assertTrue(message.getChanges() instanceof JSONObject);
        assertEquals(0, message.getRevisions().length());
        assertEquals(0, message.getChanges().length());
    }
}

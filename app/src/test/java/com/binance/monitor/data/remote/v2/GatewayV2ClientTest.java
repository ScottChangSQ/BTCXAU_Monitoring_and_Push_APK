package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.AccountHistoryPayload;
import com.binance.monitor.data.model.v2.AccountSnapshotPayload;
import com.binance.monitor.data.model.v2.MarketSnapshotPayload;

import org.junit.Test;

public class GatewayV2ClientTest {

    @Test
    public void parseMarketSnapshotShouldKeepSyncToken() throws Exception {
        String body = "{\"serverTime\":1,\"syncToken\":\"abc\",\"market\":{},\"account\":{}}";

        MarketSnapshotPayload payload = GatewayV2Client.parseMarketSnapshot(body);

        assertEquals(1L, payload.getServerTime());
        assertEquals("abc", payload.getSyncToken());
    }

    @Test
    public void parseAccountSnapshotShouldSupportV2TopLevelAccountFields() throws Exception {
        String body = "{"
                + "\"accountMeta\":{\"serverTime\":123456789,\"syncToken\":\"snap-sync\"},"
                + "\"balance\":1000.5,"
                + "\"equity\":1001.5,"
                + "\"margin\":12.3,"
                + "\"freeMargin\":989.2,"
                + "\"marginLevel\":8130.1,"
                + "\"profit\":1.0,"
                + "\"positions\":[{\"code\":\"BTCUSD\",\"quantity\":0.05}],"
                + "\"orders\":[{\"code\":\"XAUUSD\",\"pendingLots\":0.1}]"
                + "}";

        AccountSnapshotPayload payload = GatewayV2Client.parseAccountSnapshot(body);

        assertEquals(123456789L, payload.getServerTime());
        assertEquals("snap-sync", payload.getSyncToken());
        assertEquals(1000.5d, payload.getAccount().optDouble("balance"), 0.0001d);
        assertEquals(1001.5d, payload.getAccount().optDouble("equity"), 0.0001d);
        assertEquals(1, payload.getPositions().length());
        assertEquals(1, payload.getOrders().length());
    }

    @Test
    public void parseAccountHistoryShouldKeepTradesOrdersCurveAndCursor() throws Exception {
        String body = "{"
                + "\"accountMeta\":{\"serverTime\":2222,\"syncToken\":\"history-sync\"},"
                + "\"trades\":[{\"dealTicket\":11,\"code\":\"BTCUSD\"}],"
                + "\"orders\":[{\"orderId\":22,\"code\":\"XAUUSD\"}],"
                + "\"curvePoints\":[{\"timestamp\":1000,\"equity\":100.0,\"balance\":90.0}],"
                + "\"nextCursor\":\"cursor-2\""
                + "}";

        AccountHistoryPayload payload = GatewayV2Client.parseAccountHistory(body);

        assertEquals(2222L, payload.getServerTime());
        assertEquals("history-sync", payload.getSyncToken());
        assertEquals(1, payload.getTrades().length());
        assertEquals(1, payload.getOrders().length());
        assertEquals(1, payload.getCurvePoints().length());
        assertEquals("cursor-2", payload.getNextCursor());
        assertTrue(payload.getRawJson().contains("\"trades\""));
    }
}

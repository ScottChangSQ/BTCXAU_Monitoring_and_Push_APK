package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeReceipt;

import org.json.JSONObject;
import org.junit.Test;

public class GatewayV2TradeClientTest {

    @Test
    public void buildTradeCommandPayloadShouldContainAllRequiredFields() throws Exception {
        TradeCommand command = new TradeCommand(
                "req-1",
                "acc-1",
                "BTCUSD",
                "OPEN_MARKET",
                0.1,
                65000.0,
                64000.0,
                68000.0
        );

        JSONObject payload = GatewayV2TradeClient.buildTradeCommandPayload(command);

        assertEquals("req-1", payload.optString("requestId", ""));
        assertEquals("acc-1", payload.optString("accountId", ""));
        assertEquals("BTCUSD", payload.optString("symbol", ""));
        assertEquals("OPEN_MARKET", payload.optString("action", ""));
        assertEquals(0.1d, payload.optDouble("volume"), 0.0000001d);
        assertEquals(65000.0d, payload.optDouble("price"), 0.0000001d);
        assertEquals(64000.0d, payload.optDouble("sl"), 0.0000001d);
        assertEquals(68000.0d, payload.optDouble("tp"), 0.0000001d);
    }

    @Test
    public void parseTradeCheckShouldKeepStatusAndError() throws Exception {
        String body = "{"
                + "\"requestId\":\"req-check\","
                + "\"action\":\"OPEN_MARKET\","
                + "\"accountMode\":\"netting\","
                + "\"status\":\"NOT_EXECUTABLE\","
                + "\"error\":{\"code\":\"TRADE_INVALID_VOLUME\",\"message\":\"bad\"},"
                + "\"check\":{\"retcode\":10014},"
                + "\"serverTime\":123"
                + "}";

        TradeCheckResult result = GatewayV2TradeClient.parseTradeCheck(body);

        assertEquals("req-check", result.getRequestId());
        assertEquals("NOT_EXECUTABLE", result.getStatus());
        assertEquals("TRADE_INVALID_VOLUME", result.getError().getCode());
    }

    @Test
    public void parseTradeReceiptShouldKeepAcceptedResult() throws Exception {
        String body = "{"
                + "\"requestId\":\"req-submit\","
                + "\"action\":\"OPEN_MARKET\","
                + "\"accountMode\":\"netting\","
                + "\"status\":\"ACCEPTED\","
                + "\"error\":null,"
                + "\"check\":{\"retcode\":0},"
                + "\"result\":{\"order\":1,\"deal\":2},"
                + "\"idempotent\":false,"
                + "\"serverTime\":456"
                + "}";

        TradeReceipt receipt = GatewayV2TradeClient.parseTradeReceipt(body);

        assertEquals("req-submit", receipt.getRequestId());
        assertEquals("ACCEPTED", receipt.getStatus());
        assertTrue(receipt.isAccepted());
        assertEquals(1, receipt.getResult().optInt("order"));
    }

    @Test
    public void parseTradeReceiptShouldKeepIdempotentDuplicate() throws Exception {
        String body = "{"
                + "\"requestId\":\"req-dup\","
                + "\"action\":\"OPEN_MARKET\","
                + "\"accountMode\":\"netting\","
                + "\"status\":\"DUPLICATE\","
                + "\"error\":{\"code\":\"TRADE_DUPLICATE_SUBMISSION\",\"message\":\"dup\"},"
                + "\"check\":{\"retcode\":0},"
                + "\"result\":{\"order\":99,\"deal\":0},"
                + "\"idempotent\":true,"
                + "\"serverTime\":789"
                + "}";

        TradeReceipt receipt = GatewayV2TradeClient.parseTradeReceipt(body);

        assertEquals("DUPLICATE", receipt.getStatus());
        assertTrue(receipt.isIdempotent());
        assertEquals("TRADE_DUPLICATE_SUBMISSION", receipt.getError().getCode());
    }

    @Test
    public void buildTradeCommandPayloadShouldRejectNullCommand() throws Exception {
        try {
            GatewayV2TradeClient.buildTradeCommandPayload(null);
            fail("null 命令不应构造成功");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("command"));
        }
    }

    @Test
    public void buildTradeCommandPayloadShouldRejectBadCommand() throws Exception {
        TradeCommand bad = new TradeCommand(
                "",
                "",
                "",
                "",
                0.0,
                0.0,
                0.0,
                0.0
        );
        try {
            GatewayV2TradeClient.buildTradeCommandPayload(bad);
            fail("坏命令不应构造成功");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().length() > 0);
        }
    }
}

package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradeReceipt;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.TradeAuditEntry;
import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeReceipt;
import com.binance.monitor.ui.trade.TradeCommandFactory;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class GatewayV2TradeClientTest {

    @Test
    public void buildTradeCommandPayloadShouldContainAllRequiredFields() throws Exception {
        JSONObject params = new JSONObject();
        params.put("side", "buy");
        TradeCommand command = new TradeCommand(
                "req-1",
                "acc-1",
                "BTCUSD",
                "OPEN_MARKET",
                0.1,
                65000.0,
                64000.0,
                68000.0,
                params
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
    public void buildBatchTradePlanPayloadShouldContainStrategyAndItemParams() throws Exception {
        JSONObject extras = new JSONObject();
        extras.put("groupKey", "pair-1");
        BatchTradePlan plan = new BatchTradePlan(
                "batch-1",
                "GROUPED",
                "hedging",
                "Close By",
                Collections.singletonList(
                        new BatchTradeItem(
                                "item-1",
                                "平仓 BTCUSD #1",
                                TradeCommandFactory.closePosition("acc-1", "BTCUSD", 11L, 0.10d, 0d),
                                extras
                        )
                )
        );

        JSONObject payload = GatewayV2TradeClient.buildBatchTradePlanPayload(plan);

        assertEquals("batch-1", payload.optString("batchId", ""));
        assertEquals("GROUPED", payload.optString("strategy", ""));
        assertEquals("hedging", payload.optString("accountMode", ""));
        assertEquals(1, payload.optJSONArray("items").length());
        JSONObject item = payload.optJSONArray("items").optJSONObject(0);
        assertEquals("item-1", item.optString("itemId", ""));
        assertEquals("CLOSE_POSITION", item.optString("action", ""));
        assertEquals("pair-1", item.optString("groupKey", ""));
        assertEquals(11L, item.optJSONObject("params").optLong("positionTicket", 0L));
    }

    @Test
    public void parseBatchTradeReceiptShouldKeepPerItemStatuses() throws Exception {
        String body = "{"
                + "\"batchId\":\"batch-1\","
                + "\"strategy\":\"BEST_EFFORT\","
                + "\"accountMode\":\"hedging\","
                + "\"status\":\"PARTIAL\","
                + "\"error\":{\"code\":\"TRADE_EXECUTION_FAILED\",\"message\":\"partial\"},"
                + "\"items\":["
                + "{\"itemId\":\"item-1\",\"action\":\"CLOSE_POSITION\",\"status\":\"ACCEPTED\",\"error\":null,\"check\":{\"retcode\":0},\"result\":{\"order\":1}},"
                + "{\"itemId\":\"item-2\",\"action\":\"CLOSE_POSITION\",\"status\":\"REJECTED\",\"error\":{\"code\":\"TRADE_INVALID_POSITION\",\"message\":\"missing\"},\"check\":{\"retcode\":10013},\"result\":null}"
                + "],"
                + "\"serverTime\":456"
                + "}";

        BatchTradeReceipt receipt = GatewayV2TradeClient.parseBatchTradeReceipt(body);

        assertEquals("batch-1", receipt.getBatchId());
        assertTrue(receipt.isPartial());
        assertEquals(2, receipt.getItems().size());
        assertEquals("item-2", receipt.getItems().get(1).getItemId());
        assertEquals("TRADE_INVALID_POSITION", receipt.getItems().get(1).getError().getCode());
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

    @Test
    public void buildTradeCommandPayloadShouldRejectClosePositionWithoutTicket() throws Exception {
        JSONObject params = new JSONObject();
        params.put("volume", 0.1d);
        TradeCommand command = new TradeCommand(
                "req-close",
                "acc-1",
                "BTCUSD",
                "CLOSE_POSITION",
                0.1d,
                0d,
                0d,
                0d,
                params
        );

        try {
            GatewayV2TradeClient.buildTradeCommandPayload(command);
            fail("缺少 positionTicket 的平仓命令不应构造成功");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("positionTicket"));
        }
    }

    @Test
    public void buildTradeCommandPayloadShouldRejectModifyTpSlWithoutTicket() throws Exception {
        JSONObject params = new JSONObject();
        params.put("tp", 68000.0d);
        TradeCommand command = new TradeCommand(
                "req-modify",
                "acc-1",
                "BTCUSD",
                "MODIFY_TPSL",
                0d,
                0d,
                0d,
                68000.0d,
                params
        );

        try {
            GatewayV2TradeClient.buildTradeCommandPayload(command);
            fail("缺少 positionTicket 的改单命令不应构造成功");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("positionTicket"));
        }
    }

    @Test
    public void buildTradeCommandPayloadShouldRejectPendingModifyWithoutOrderTicket() throws Exception {
        JSONObject params = new JSONObject();
        params.put("price", 65000.0d);
        TradeCommand command = new TradeCommand(
                "req-pending-modify",
                "acc-1",
                "BTCUSD",
                "PENDING_MODIFY",
                0d,
                65000.0d,
                0d,
                0d,
                params
        );

        try {
            GatewayV2TradeClient.buildTradeCommandPayload(command);
            fail("缺少 orderTicket 的挂单修改命令不应构造成功");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("orderTicket"));
        }
    }

    @Test
    public void buildHttpFailureMessageShouldExplainMissingTradeEndpoint() {
        String message = GatewayV2TradeClient.buildHttpFailureMessage(
                404,
                "/v2/trade/check",
                "{\"detail\":\"Not Found\"}"
        );

        assertTrue(message.contains("/v2/trade/check"));
        assertTrue(message.contains("网关"));
        assertTrue(message.contains("升级"));
    }

    @Test
    public void buildTradeCommandPayloadShouldKeepCloseByPairTickets() throws Exception {
        TradeCommand command = TradeCommandFactory.closeBy(
                "acc-1",
                "BTCUSD",
                9001L,
                9002L
        );

        JSONObject payload = GatewayV2TradeClient.buildTradeCommandPayload(command);

        assertEquals("CLOSE_BY", payload.optString("action", ""));
        assertEquals(9001L, payload.optJSONObject("params").optLong("positionTicket", 0L));
        assertEquals(9002L, payload.optJSONObject("params").optLong("oppositePositionTicket", 0L));
    }

    @Test
    public void parseTradeAuditRecentShouldKeepTraceStages() throws Exception {
        String body = "{"
                + "\"items\":["
                + "{\"traceId\":\"req-1\",\"traceType\":\"single\",\"action\":\"OPEN_MARKET\",\"symbol\":\"BTCUSD\",\"accountMode\":\"hedging\",\"stage\":\"check\",\"status\":\"EXECUTABLE\",\"errorCode\":\"\",\"message\":\"检查通过\",\"actionSummary\":\"买入 BTCUSD 0.05 手\",\"serverTime\":101,\"createdAt\":99},"
                + "{\"traceId\":\"batch-1\",\"traceType\":\"batch\",\"action\":\"BATCH\",\"symbol\":\"BTCUSD\",\"accountMode\":\"hedging\",\"stage\":\"batch_submit\",\"status\":\"PARTIAL\",\"errorCode\":\"TRADE_BATCH_PARTIAL\",\"message\":\"批量部分成功\",\"actionSummary\":\"批量平仓 BTCUSD\",\"serverTime\":102,\"createdAt\":100}"
                + "],"
                + "\"serverTime\":103"
                + "}";

        List<TradeAuditEntry> entries = GatewayV2TradeClient.parseTradeAuditRecent(body);

        assertEquals(2, entries.size());
        assertEquals("req-1", entries.get(0).getTraceId());
        assertEquals("check", entries.get(0).getStage());
        assertEquals("batch", entries.get(1).getTraceType());
    }

    @Test
    public void parseTradeAuditLookupShouldKeepSingleTraceTimeline() throws Exception {
        String body = "{"
                + "\"id\":\"req-lookup\","
                + "\"items\":["
                + "{\"traceId\":\"req-lookup\",\"traceType\":\"single\",\"action\":\"OPEN_MARKET\",\"symbol\":\"BTCUSD\",\"accountMode\":\"hedging\",\"stage\":\"check\",\"status\":\"EXECUTABLE\",\"errorCode\":\"\",\"message\":\"检查通过\",\"actionSummary\":\"买入 BTCUSD 0.05 手\",\"serverTime\":101,\"createdAt\":99},"
                + "{\"traceId\":\"req-lookup\",\"traceType\":\"single\",\"action\":\"OPEN_MARKET\",\"symbol\":\"BTCUSD\",\"accountMode\":\"hedging\",\"stage\":\"result\",\"status\":\"SETTLED\",\"errorCode\":\"\",\"message\":\"交易已收敛\",\"actionSummary\":\"买入 BTCUSD 0.05 手\",\"serverTime\":102,\"createdAt\":100}"
                + "],"
                + "\"serverTime\":103"
                + "}";

        List<TradeAuditEntry> entries = GatewayV2TradeClient.parseTradeAuditLookup(body);

        assertEquals(2, entries.size());
        assertEquals("req-lookup", entries.get(1).getTraceId());
        assertEquals("SETTLED", entries.get(1).getStatus());
    }
}

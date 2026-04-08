/*
 * 异常网关客户端测试，确保全量/增量响应解析与品种代码转换都符合客户端预期。
 */
package com.binance.monitor.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.AbnormalAlertItem;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.SymbolConfig;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AbnormalGatewayClientTest {

    @Test
    public void parseSyncBodyShouldReadFullPayloadAndNormalizeSymbols() throws Exception {
        String body = "{"
                + "\"abnormalMeta\":{\"syncSeq\":12},"
                + "\"isDelta\":false,"
                + "\"records\":["
                + "{\"id\":\"r1\",\"symbol\":\"BTCUSDT\",\"timestamp\":1001,\"closeTime\":1000,"
                + "\"openPrice\":10,\"closePrice\":12,\"volume\":3,\"amount\":4,"
                + "\"priceChange\":2,\"percentChange\":0.2,\"triggerSummary\":\"成交量\"},"
                + "{\"id\":\"r2\",\"symbol\":\"XAUUSDT\",\"timestamp\":2001,\"closeTime\":2000,"
                + "\"openPrice\":20,\"closePrice\":21,\"volume\":5,\"amount\":6,"
                + "\"priceChange\":1,\"percentChange\":0.1,\"triggerSummary\":\"价格变化\"}"
                + "],"
                + "\"alerts\":[{"
                + "\"id\":\"a1\",\"symbols\":[\"BTCUSDT\",\"XAUUSDT\"],"
                + "\"title\":\"异常提醒\",\"content\":\"test\",\"closeTime\":2000,\"createdAt\":3000"
                + "}]"
                + "}";

        AbnormalGatewayClient.SyncResult result = AbnormalGatewayClient.parseSyncBody(body);

        assertTrue(result.isSuccess());
        assertFalse(result.isDelta());
        assertEquals(12L, result.getSyncSeq());
        assertEquals(2, result.getRecords().size());
        assertEquals(AppConstants.SYMBOL_BTC, result.getRecords().get(0).getSymbol());
        assertEquals(AppConstants.SYMBOL_XAU, result.getRecords().get(1).getSymbol());
        assertEquals(1, result.getAlerts().size());
        assertEquals(Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU), result.getAlerts().get(0).getSymbols());
    }

    @Test
    public void parseSyncBodyShouldReadDeltaPayload() throws Exception {
        String body = "{"
                + "\"abnormalMeta\":{\"syncSeq\":13},"
                + "\"isDelta\":true,"
                + "\"delta\":{"
                + "\"records\":[{"
                + "\"id\":\"r3\",\"symbol\":\"XAUUSDT\",\"timestamp\":4001,\"closeTime\":4000,"
                + "\"openPrice\":30,\"closePrice\":32,\"volume\":7,\"amount\":8,"
                + "\"priceChange\":2,\"percentChange\":0.2,\"triggerSummary\":\"成交额\""
                + "}],"
                + "\"alerts\":[{"
                + "\"id\":\"a2\",\"symbols\":[\"XAUUSDT\"],"
                + "\"title\":\"异常提醒\",\"content\":\"gold\",\"closeTime\":4000,\"createdAt\":5000"
                + "}]"
                + "}"
                + "}";

        AbnormalGatewayClient.SyncResult result = AbnormalGatewayClient.parseSyncBody(body);

        assertTrue(result.isSuccess());
        assertTrue(result.isDelta());
        assertEquals(13L, result.getSyncSeq());
        assertEquals(1, result.getRecords().size());
        assertEquals(AppConstants.SYMBOL_XAU, result.getRecords().get(0).getSymbol());
        assertEquals(1, result.getAlerts().size());
        assertEquals(AppConstants.SYMBOL_XAU, result.getAlerts().get(0).getSymbols().get(0));
    }

    @Test
    public void buildConfigPayloadShouldConvertLocalSymbolsToGatewaySymbols() throws Exception {
        List<SymbolConfig> configs = Arrays.asList(
                new SymbolConfig(AppConstants.SYMBOL_BTC, 1d, 2d, 3d, true, false, true),
                new SymbolConfig(AppConstants.SYMBOL_XAU, 4d, 5d, 6d, false, true, true)
        );

        JSONObject payload = AbnormalGatewayClient.buildConfigPayload(false, configs);

        assertFalse(payload.getBoolean("logicAnd"));
        assertEquals(2, payload.getJSONArray("configs").length());
        assertEquals("BTCUSDT", payload.getJSONArray("configs").getJSONObject(0).getString("symbol"));
        assertEquals("XAUUSDT", payload.getJSONArray("configs").getJSONObject(1).getString("symbol"));
    }

    @Test
    public void resolveCandidateBaseUrlsShouldAvoidLocalFallbacksForRemoteHost() {
        List<String> urls = AbnormalGatewayClient.resolveCandidateBaseUrls("http://43.155.214.62:8787");

        assertEquals(1, urls.size());
        assertEquals("http://43.155.214.62:8787", urls.get(0));
    }

    @Test
    public void resolveCandidateBaseUrlsShouldKeepSingleConfiguredEntryForLoopbackInput() {
        List<String> urls = AbnormalGatewayClient.resolveCandidateBaseUrls("http://127.0.0.1:8787");

        assertEquals(1, urls.size());
        assertEquals("http://127.0.0.1:8787", urls.get(0));
    }
}

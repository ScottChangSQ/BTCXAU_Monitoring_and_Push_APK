/*
 * 验证 MT5 客户端会把后台摘要请求和前台全量请求分开建同步键，
 * 避免两个接口共用同一增量状态，导致摘要/全量响应串线。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.lang.reflect.Method;
import java.util.List;

public class Mt5BridgeGatewayClientTest {

    // 后台摘要与前台全量必须使用不同同步键，避免 since 序号混用。
    @Test
    public void buildSyncRequestKeySeparatesSummaryAndSnapshot() {
        assertEquals("snapshot:all", Mt5BridgeGatewayClient.buildSyncRequestKey("snapshot", "all"));
        assertEquals("summary:all", Mt5BridgeGatewayClient.buildSyncRequestKey("summary", "all"));
    }

    // 轻实时持仓、挂单、成交、曲线都必须拥有各自的同步键。
    @Test
    public void buildSyncRequestKeySeparatesLiveAndIncrementalScopes() {
        assertEquals("live:all", Mt5BridgeGatewayClient.buildSyncRequestKey("live", "all"));
        assertEquals("pending:all", Mt5BridgeGatewayClient.buildSyncRequestKey("pending", "all"));
        assertEquals("trades:all", Mt5BridgeGatewayClient.buildSyncRequestKey("trades", "all"));
        assertEquals("curve:all", Mt5BridgeGatewayClient.buildSyncRequestKey("curve", "all"));
    }

    // 已明确配置公网网关时，不应再拼接本机和局域网回退地址，避免一次超时放大成整串错误。
    @Test
    public void resolveCandidateBaseUrlsShouldKeepRemoteGatewayOnly() {
        assertEquals(
                Arrays.asList("http://43.155.214.62:8787"),
                Mt5BridgeGatewayClient.resolveCandidateBaseUrls("http://43.155.214.62:8787")
        );
    }

    // 只有本地调试地址才附带模拟器与 localhost 回退。
    @Test
    public void resolveCandidateBaseUrlsShouldAppendLocalFallbacksForLoopbackHost() {
        assertEquals(
                Arrays.asList(
                        "http://127.0.0.1:8787",
                        "http://10.0.2.2:8787",
                        "http://localhost:8787"
                ),
                Mt5BridgeGatewayClient.resolveCandidateBaseUrls("http://127.0.0.1:8787")
        );
    }

    // 曲线点解析时必须把历史仓位比例一并读入，后续图表与缓存才能继续使用。
    @Test
    @SuppressWarnings("unchecked")
    public void parseCurvePointsKeepsHistoricalPositionRatio() throws Exception {
        Mt5BridgeGatewayClient client = new Mt5BridgeGatewayClient();
        Method method = Mt5BridgeGatewayClient.class.getDeclaredMethod("parseCurvePoints", JSONArray.class);
        method.setAccessible(true);

        JSONArray array = new JSONArray("[{\"timestamp\":1000,\"equity\":100.0,\"balance\":90.0,\"positionRatio\":0.35}]");
        List<CurvePoint> points = (List<CurvePoint>) method.invoke(client, array);

        assertEquals(1, points.size());
        assertEquals(0.35d, points.get(0).getPositionRatio(), 1e-9);
    }

    // 秒级曲线时间戳必须在入口处升成毫秒，否则交易区间匹配不到曲线点。
    @Test
    @SuppressWarnings("unchecked")
    public void parseCurvePointsShouldNormalizeSecondTimestamp() throws Exception {
        Mt5BridgeGatewayClient client = new Mt5BridgeGatewayClient();
        Method method = Mt5BridgeGatewayClient.class.getDeclaredMethod("parseCurvePoints", JSONArray.class);
        method.setAccessible(true);

        JSONArray array = new JSONArray("[{\"timestamp\":1704067200,\"equity\":100.0,\"balance\":90.0,\"positionRatio\":0.35}]");
        List<CurvePoint> points = (List<CurvePoint>) method.invoke(client, array);

        assertEquals(1, points.size());
        assertEquals(1_704_067_200_000L, points.get(0).getTimestamp());
    }

    // 历史成交时间字段需要兼容下划线写法，否则持仓时长会被压成接近 0。
    @Test
    public void parseTradeItemShouldSupportSnakeCaseLifecycleTimeFields() throws Exception {
        Mt5BridgeGatewayClient client = new Mt5BridgeGatewayClient();
        Method method = Mt5BridgeGatewayClient.class.getDeclaredMethod("parseTradeItem", JSONObject.class);
        method.setAccessible(true);

        JSONObject item = new JSONObject()
                .put("productName", "BTCUSD")
                .put("code", "BTCUSD")
                .put("side", "Buy")
                .put("price", 100.0d)
                .put("open_price", 95.0d)
                .put("close_price", 100.0d)
                .put("timestamp", 1704069000L)
                .put("open_time", 1704067200L)
                .put("close_time", 1704069000L)
                .put("quantity", 1.0d)
                .put("profit", 5.0d);

        TradeRecordItem trade = (TradeRecordItem) method.invoke(client, item);

        assertEquals(1_704_067_200_000L, trade.getOpenTime());
        assertEquals(1_704_069_000_000L, trade.getCloseTime());
        assertEquals(95.0d, trade.getOpenPrice(), 1e-9);
        assertEquals(100.0d, trade.getClosePrice(), 1e-9);
    }
}

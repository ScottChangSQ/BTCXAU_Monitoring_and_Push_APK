/*
 * 验证 MT5 客户端会把后台摘要请求和前台全量请求分开建同步键，
 * 避免两个接口共用同一增量状态，导致摘要/全量响应串线。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.CurvePoint;

import org.json.JSONArray;
import org.junit.Test;

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
}

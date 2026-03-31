/*
 * 验证 MT5 客户端会把后台摘要请求和前台全量请求分开建同步键，
 * 避免两个接口共用同一增量状态，导致摘要/全量响应串线。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
}

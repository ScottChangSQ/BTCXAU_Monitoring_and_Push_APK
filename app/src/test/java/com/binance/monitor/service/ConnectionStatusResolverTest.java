/*
 * 连接状态文案测试，确保第3步收口后只由 v2 stream 健康度决定主链状态，
 * 不再让 fallback socket、旧 tick 新鲜度或历史重连次数继续影响展示口径。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.binance.monitor.runtime.ConnectionStage;

public class ConnectionStatusResolverTest {

    @Test
    public void shouldTreatInitialUnhealthyV2StreamAsConnecting() {
        String status = ConnectionStatusResolver.resolveStatus(
                ConnectionStage.CONNECTING,
                false,
                0L,
                11_000L,
                5_000L,
                "已连接",
                "连接中",
                "重连中",
                "网络未连接"
        );

        assertEquals("连接中", status);
    }

    @Test
    public void shouldTreatDisconnectedStageAsDisconnected() {
        String status = ConnectionStatusResolver.resolveStatus(
                ConnectionStage.DISCONNECTED,
                false,
                0L,
                11_000L,
                5_000L,
                "已连接",
                "连接中",
                "重连中",
                "网络未连接"
        );

        assertEquals("网络未连接", status);
    }

    @Test
    public void shouldKeepReconnectingStageWhenReconnectIsInProgress() {
        String status = ConnectionStatusResolver.resolveStatus(
                ConnectionStage.RECONNECTING,
                false,
                10_000L,
                11_000L,
                5_000L,
                "已连接",
                "连接中",
                "重连中",
                "网络未连接"
        );

        assertEquals("重连中", status);
    }

    @Test
    public void shouldPreferConnectedWhenV2StreamIsFresh() {
        String status = ConnectionStatusResolver.resolveStatus(
                ConnectionStage.CONNECTED,
                true,
                11_000L,
                12_000L,
                5_000L,
                "已连接",
                "连接中",
                "重连中",
                "网络未连接"
        );

        assertEquals("已连接", status);
    }

    @Test
    public void shouldTreatStaleConnectedStageAsReconnecting() {
        String status = ConnectionStatusResolver.resolveStatus(
                ConnectionStage.CONNECTED,
                true,
                1_000L,
                8_000L,
                5_000L,
                "已连接",
                "连接中",
                "重连中",
                "网络未连接"
        );

        assertEquals("重连中", status);
    }
}

/*
 * 连接状态文案测试，确保第3步收口后只由 v2 stream 健康度决定主链状态，
 * 不再让 fallback socket、旧 tick 新鲜度或历史重连次数继续影响展示口径。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ConnectionStatusResolverTest {

    @Test
    public void shouldTreatUnhealthyV2StreamAsConnectingEvenWhenFreshTicksExist() {
        String status = ConnectionStatusResolver.resolveStatus(
                false,
                0L,
                11_000L,
                5_000L,
                "已连接",
                "连接中"
        );

        assertEquals("连接中", status);
    }

    @Test
    public void shouldIgnoreLegacyFallbackConnectionMetadataWhenV2StreamIsUnhealthy() {
        String status = ConnectionStatusResolver.resolveStatus(
                false,
                0L,
                11_000L,
                5_000L,
                "已连接",
                "连接中"
        );

        assertEquals("连接中", status);
    }

    @Test
    public void shouldPreferV2StreamWhenItIsFresh() {
        String status = ConnectionStatusResolver.resolveStatus(
                true,
                11_000L,
                12_000L,
                5_000L,
                "已连接",
                "连接中"
        );

        assertEquals("已连接", status);
    }
}

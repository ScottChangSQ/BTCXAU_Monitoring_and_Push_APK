/*
 * 连接状态文案测试，确保状态栏优先反映“当前是否真有新行情”，
 * 避免已经恢复数据流后仍长时间显示“重连中”。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConnectionStatusResolverTest {

    @Test
    public void shouldTreatFreshTicksAsConnectedEvenWhenSocketFlagLagged() {
        List<String> symbols = Arrays.asList("BTCUSDT", "XAUUSDT");
        Map<String, Boolean> socketStates = new HashMap<>();
        socketStates.put("BTCUSDT", false);
        socketStates.put("XAUUSDT", false);
        Map<String, Integer> reconnectCounts = new HashMap<>();
        reconnectCounts.put("BTCUSDT", 1);
        reconnectCounts.put("XAUUSDT", 1);
        Map<String, Long> lastTicks = new HashMap<>();
        lastTicks.put("BTCUSDT", 10_000L);
        lastTicks.put("XAUUSDT", 10_500L);

        String status = ConnectionStatusResolver.resolveStatus(
                false,
                0L,
                symbols,
                socketStates,
                reconnectCounts,
                lastTicks,
                11_000L,
                5_000L,
                30,
                "已连接",
                "部分连接",
                "连接中"
        );

        assertEquals("已连接", status);
    }

    @Test
    public void shouldOnlyCountReconnectAttemptsForCurrentlyDisconnectedSymbols() {
        List<String> symbols = Arrays.asList("BTCUSDT", "XAUUSDT");
        Map<String, Boolean> socketStates = new HashMap<>();
        socketStates.put("BTCUSDT", true);
        socketStates.put("XAUUSDT", false);
        Map<String, Integer> reconnectCounts = new HashMap<>();
        reconnectCounts.put("BTCUSDT", 7);
        reconnectCounts.put("XAUUSDT", 0);
        Map<String, Long> lastTicks = new HashMap<>();

        String status = ConnectionStatusResolver.resolveStatus(
                false,
                0L,
                symbols,
                socketStates,
                reconnectCounts,
                lastTicks,
                11_000L,
                5_000L,
                30,
                "已连接",
                "部分连接",
                "连接中"
        );

        assertEquals("部分连接 1/2", status);
    }

    @Test
    public void shouldPreferV2StreamWhenItIsFresh() {
        List<String> symbols = Arrays.asList("BTCUSDT", "XAUUSDT");
        Map<String, Boolean> socketStates = new HashMap<>();
        Map<String, Integer> reconnectCounts = new HashMap<>();
        Map<String, Long> lastTicks = new HashMap<>();

        String status = ConnectionStatusResolver.resolveStatus(
                true,
                11_000L,
                symbols,
                socketStates,
                reconnectCounts,
                lastTicks,
                12_000L,
                5_000L,
                30,
                "已连接",
                "部分连接",
                "连接中"
        );

        assertEquals("已连接", status);
    }

    @Test
    public void normalizeReconnectAttemptShouldClearConnectedState() {
        assertEquals(0, ConnectionStatusResolver.normalizeReconnectAttempt(true, 3));
        assertEquals(3, ConnectionStatusResolver.normalizeReconnectAttempt(false, 3));
    }
}

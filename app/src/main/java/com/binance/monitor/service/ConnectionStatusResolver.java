/*
 * 连接状态解析工具，负责根据 socket 标记、最近行情时间和重连次数生成前台状态文案。
 * 供 MonitorService 统一复用，避免“数据已恢复但文案仍停留在重连中”。
 */
package com.binance.monitor.service;

import java.util.List;
import java.util.Map;

public final class ConnectionStatusResolver {

    private ConnectionStatusResolver() {
    }

    // 规范化重连次数：一旦已连接，就把旧的重连次数视为 0。
    public static int normalizeReconnectAttempt(boolean connected, int reconnectAttempt) {
        if (connected) {
            return 0;
        }
        return Math.max(0, reconnectAttempt);
    }

    // 根据当前连接标记与最近行情时间，生成用户可见的连接状态文案。
    public static String resolveStatus(boolean v2StreamConnected,
                                       long lastV2StreamMessageAt,
                                       List<String> symbols,
                                       Map<String, Boolean> socketStates,
                                       Map<String, Integer> reconnectCounts,
                                       Map<String, Long> lastKlineTickAt,
                                       long nowMs,
                                       long freshnessTimeoutMs,
                                       int maxReconnectAttempts,
                                       String connectedText,
                                       String partialPrefix,
                                       String connectingText) {
        if (isV2StreamHealthy(v2StreamConnected, lastV2StreamMessageAt, nowMs, freshnessTimeoutMs)) {
            return connectedText;
        }
        if (symbols == null || symbols.isEmpty()) {
            return connectingText;
        }
        int connectedCount = 0;
        int maxReconnect = 0;
        for (String symbol : symbols) {
            if (isSymbolConnected(symbol, socketStates, lastKlineTickAt, nowMs, freshnessTimeoutMs)) {
                connectedCount++;
                continue;
            }
            maxReconnect = Math.max(maxReconnect, reconnectCounts.getOrDefault(symbol, 0));
        }
        if (connectedCount == symbols.size()) {
            return connectedText;
        }
        if (connectedCount > 0) {
            return partialPrefix + " " + connectedCount + "/" + symbols.size();
        }
        if (maxReconnect > 0) {
            return "重连中(" + maxReconnect + "/" + maxReconnectAttempts + ")";
        }
        return connectingText;
    }

    // 统一判断 v2 stream 是否仍处于可视为健康的状态。
    public static boolean isV2StreamHealthy(boolean connected,
                                            long lastMessageAt,
                                            long nowMs,
                                            long freshnessTimeoutMs) {
        if (connected) {
            return true;
        }
        return lastMessageAt > 0L && nowMs - lastMessageAt <= freshnessTimeoutMs;
    }

    // 判断某个产品是否处于“可视为已连通”的状态。
    private static boolean isSymbolConnected(String symbol,
                                             Map<String, Boolean> socketStates,
                                             Map<String, Long> lastKlineTickAt,
                                             long nowMs,
                                             long freshnessTimeoutMs) {
        if (symbol == null) {
            return false;
        }
        if (Boolean.TRUE.equals(socketStates.get(symbol))) {
            return true;
        }
        long lastTickAt = lastKlineTickAt.getOrDefault(symbol, 0L);
        return lastTickAt > 0L && nowMs - lastTickAt <= freshnessTimeoutMs;
    }
}

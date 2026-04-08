/*
 * 连接状态解析工具，负责按 v2 stream 健康度生成前台状态文案。
 * 第3步收口后，fallback socket 和本地 tick 新鲜度都不再参与主链状态判断。
 */
package com.binance.monitor.service;

public final class ConnectionStatusResolver {

    private ConnectionStatusResolver() {
    }

    // 根据 v2 stream 健康度生成用户可见的连接状态文案。
    public static String resolveStatus(boolean v2StreamConnected,
                                       long lastV2StreamMessageAt,
                                       long nowMs,
                                       long freshnessTimeoutMs,
                                       String connectedText,
                                       String connectingText) {
        if (isV2StreamHealthy(v2StreamConnected, lastV2StreamMessageAt, nowMs, freshnessTimeoutMs)) {
            return connectedText;
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
}

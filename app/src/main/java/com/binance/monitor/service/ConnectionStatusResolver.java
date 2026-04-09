/*
 * 连接状态解析工具，负责按 v2 stream 健康度生成前台状态文案。
 * 第3步收口后，fallback socket 和本地 tick 新鲜度都不再参与主链状态判断。
 */
package com.binance.monitor.service;

import androidx.annotation.Nullable;

import com.binance.monitor.runtime.ConnectionStage;

public final class ConnectionStatusResolver {

    private ConnectionStatusResolver() {
    }

    // 根据 v2 stream 健康度生成用户可见的连接状态文案。
    public static String resolveStatus(@Nullable ConnectionStage streamStage,
                                       boolean v2StreamConnected,
                                       long lastV2StreamMessageAt,
                                       long nowMs,
                                       long freshnessTimeoutMs,
                                       String connectedText,
                                       String connectingText,
                                       String reconnectingText,
                                       String disconnectedText) {
        ConnectionStage resolvedStage = resolveStage(
                streamStage,
                v2StreamConnected,
                lastV2StreamMessageAt,
                nowMs,
                freshnessTimeoutMs
        );
        if (resolvedStage == ConnectionStage.CONNECTED) {
            return connectedText;
        }
        if (resolvedStage == ConnectionStage.RECONNECTING) {
            return reconnectingText;
        }
        if (resolvedStage == ConnectionStage.DISCONNECTED) {
            return disconnectedText;
        }
        return connectingText;
    }

    // 统一把底层 socket 状态和消息新鲜度解析成用户侧可消费的连接阶段。
    public static ConnectionStage resolveStage(@Nullable ConnectionStage streamStage,
                                               boolean v2StreamConnected,
                                               long lastV2StreamMessageAt,
                                               long nowMs,
                                               long freshnessTimeoutMs) {
        ConnectionStage safeStage = streamStage == null ? ConnectionStage.CONNECTING : streamStage;
        if (safeStage == ConnectionStage.DISCONNECTED) {
            return ConnectionStage.DISCONNECTED;
        }
        if (safeStage == ConnectionStage.RECONNECTING) {
            return ConnectionStage.RECONNECTING;
        }
        if (isV2StreamHealthy(v2StreamConnected, lastV2StreamMessageAt, nowMs, freshnessTimeoutMs)) {
            return ConnectionStage.CONNECTED;
        }
        if (safeStage == ConnectionStage.CONNECTED) {
            return ConnectionStage.RECONNECTING;
        }
        return ConnectionStage.CONNECTING;
    }

    // 统一判断 v2 stream 是否仍处于可视为健康的状态。
    public static boolean isV2StreamHealthy(boolean connected,
                                            long lastMessageAt,
                                            long nowMs,
                                            long freshnessTimeoutMs) {
        if (!connected) {
            return false;
        }
        if (freshnessTimeoutMs <= 0L) {
            return true;
        }
        if (lastMessageAt <= 0L) {
            return false;
        }
        long elapsedMs = nowMs - lastMessageAt;
        if (elapsedMs < 0L) {
            return true;
        }
        return elapsedMs <= freshnessTimeoutMs;
    }
}

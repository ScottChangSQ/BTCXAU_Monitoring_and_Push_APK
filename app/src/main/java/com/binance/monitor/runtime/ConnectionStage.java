/*
 * 统一连接阶段枚举，供 stream 客户端、服务层和悬浮窗共享同一套连接语义。
 * 避免把“首次连接中”“重连中”“已连接”“已断开”混成同一个字符串判断。
 */
package com.binance.monitor.runtime;

public enum ConnectionStage {
    CONNECTING,
    RECONNECTING,
    CONNECTED,
    DISCONNECTED
}

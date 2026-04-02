/*
 * 监控服务运行策略辅助，负责根据前后台状态统一返回各类刷新节奏。
 * 供 MonitorService 等运行态模块复用，避免每处各自写死时间。
 */
package com.binance.monitor.service;

import com.binance.monitor.constants.AppConstants;

public final class MonitorRuntimePolicyHelper {

    private MonitorRuntimePolicyHelper() {
    }

    // 解析连接心跳间隔，前台保持当前体验，后台自动放缓。
    public static long resolveHeartbeatDelayMs(boolean foreground) {
        return foreground
                ? AppConstants.CONNECTION_HEARTBEAT_INTERVAL_MS
                : AppConstants.CONNECTION_HEARTBEAT_BACKGROUND_INTERVAL_MS;
    }

    // 解析异常同步间隔，前台优先实时感，后台优先节电和节流。
    public static long resolveAbnormalSyncDelayMs(boolean foreground) {
        return foreground
                ? AppConstants.ABNORMAL_SYNC_INTERVAL_MS
                : AppConstants.ABNORMAL_SYNC_BACKGROUND_INTERVAL_MS;
    }

    // 解析悬浮窗刷新节流，后台适当放慢以减少无效刷新。
    public static long resolveFloatingRefreshThrottleMs(boolean foreground) {
        return foreground
                ? AppConstants.FLOATING_UPDATE_THROTTLE_MS
                : AppConstants.FLOATING_UPDATE_BACKGROUND_THROTTLE_MS;
    }
}

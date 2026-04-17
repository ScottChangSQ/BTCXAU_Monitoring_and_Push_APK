/*
 * 监控服务通知协调器，统一处理历史常驻通知收口和异常提醒链路隔离。
 */
package com.binance.monitor.service;

import androidx.annotation.Nullable;

import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.util.NotificationHelper;

final class MonitorForegroundNotificationCoordinator {

    interface Host {
        void suppressServiceNotification();
    }

    private final NotificationHelper notificationHelper;
    private final MonitorRepository repository;
    private boolean foregroundStarted;
    private String lastForegroundNotificationSignature = "";

    // 创建前台通知协调器。
    MonitorForegroundNotificationCoordinator(@Nullable NotificationHelper notificationHelper,
                                            @Nullable MonitorRepository repository) {
        this.notificationHelper = notificationHelper;
        this.repository = repository;
    }

    // 确保服务进入前台运行态；已在前台时只做必要刷新。
    void ensureForeground(@Nullable Host host, @Nullable String connectionState) {
        if (host == null || notificationHelper == null || repository == null) {
            return;
        }
        boolean monitoringEnabled = isMonitoringEnabled();
        if (foregroundStarted) {
            refreshNotification(connectionState);
            return;
        }
        String safeConnectionState = safeText(connectionState);
        String signature = buildForegroundNotificationSignature(safeConnectionState, monitoringEnabled);
        host.suppressServiceNotification();
        lastForegroundNotificationSignature = signature;
        foregroundStarted = true;
    }

    // 常驻通知已在启动后收起，这里只保留签名去重状态，避免重复进入前台链路。
    void refreshNotification(@Nullable String connectionState) {
        if (!foregroundStarted || notificationHelper == null || repository == null) {
            return;
        }
        String safeConnectionState = safeText(connectionState);
        boolean monitoringEnabled = isMonitoringEnabled();
        String signature = buildForegroundNotificationSignature(safeConnectionState, monitoringEnabled);
        if (signature.equals(lastForegroundNotificationSignature)) {
            return;
        }
        lastForegroundNotificationSignature = signature;
    }

    // 销毁时撤销前台通知相关状态。
    void onDestroy() {
        foregroundStarted = false;
        lastForegroundNotificationSignature = "";
        if (notificationHelper != null) {
            notificationHelper.cancelServiceNotification();
        }
    }

    // 只把真正影响通知文案的字段纳入签名。
    private String buildForegroundNotificationSignature(String connectionState, boolean monitoringEnabled) {
        return safeText(connectionState) + "|" + monitoringEnabled;
    }

    // 读取当前监控开关真值。
    private boolean isMonitoringEnabled() {
        return Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue());
    }

    private static String safeText(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

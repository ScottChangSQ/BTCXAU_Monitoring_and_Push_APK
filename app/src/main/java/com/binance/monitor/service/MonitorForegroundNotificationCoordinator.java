/*
 * 监控服务通知协调器，负责前台服务通知的进入、刷新与退出收口。
 */
package com.binance.monitor.service;

import android.app.Notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.util.NotificationHelper;

final class MonitorForegroundNotificationCoordinator {

    interface Host {
        void enterForeground(int notificationId, @NonNull Notification notification);

        void exitForeground();
    }

    private final NotificationHelper notificationHelper;
    private final MonitorRepository repository;
    private Host host;
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
        this.host = host;
        boolean monitoringEnabled = isMonitoringEnabled();
        String safeConnectionState = safeText(connectionState);
        String signature = buildForegroundNotificationSignature(safeConnectionState, monitoringEnabled);
        if (foregroundStarted) {
            refreshNotification(connectionState);
            return;
        }
        Notification notification = notificationHelper.buildServiceNotification(
                safeConnectionState,
                monitoringEnabled
        );
        host.enterForeground(AppConstants.SERVICE_NOTIFICATION_ID, notification);
        lastForegroundNotificationSignature = signature;
        foregroundStarted = true;
    }

    // 前台通知文案变化时同步刷新前台服务通知。
    void refreshNotification(@Nullable String connectionState) {
        if (!foregroundStarted || host == null || notificationHelper == null || repository == null) {
            return;
        }
        String safeConnectionState = safeText(connectionState);
        boolean monitoringEnabled = isMonitoringEnabled();
        String signature = buildForegroundNotificationSignature(safeConnectionState, monitoringEnabled);
        if (signature.equals(lastForegroundNotificationSignature)) {
            return;
        }
        Notification notification = notificationHelper.buildServiceNotification(
                safeConnectionState,
                monitoringEnabled
        );
        host.enterForeground(AppConstants.SERVICE_NOTIFICATION_ID, notification);
        lastForegroundNotificationSignature = signature;
    }

    // 销毁时撤销前台通知相关状态。
    void onDestroy() {
        if (foregroundStarted && host != null) {
            host.exitForeground();
        }
        foregroundStarted = false;
        host = null;
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

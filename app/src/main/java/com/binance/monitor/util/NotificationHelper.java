package com.binance.monitor.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;

public class NotificationHelper {

    private final Context context;
    private final NotificationManager manager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        manager = this.context.getSystemService(NotificationManager.class);
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || manager == null) {
            return;
        }
        NotificationChannel serviceChannel = new NotificationChannel(
                AppConstants.SERVICE_CHANNEL_ID,
                context.getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription("前台行情与监控服务");
        NotificationChannel alertChannel = new NotificationChannel(
                AppConstants.ALERT_CHANNEL_ID,
                "异常交易提醒",
                NotificationManager.IMPORTANCE_HIGH
        );
        alertChannel.setDescription("异常交易命中后的消息提醒");

        manager.createNotificationChannel(serviceChannel);
        manager.createNotificationChannel(alertChannel);
    }

    public Notification buildServiceNotification(String connectionState, boolean monitoringEnabled) {
        Intent intent = HostNavigationIntentFactory.forTab(context, HostTab.MARKET_MONITOR)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                100,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String content = monitoringEnabled
                ? context.getString(R.string.service_notification_monitoring)
                : context.getString(R.string.service_notification_idle);
        String merged = content + " · " + (connectionState == null ? "" : connectionState);
        return new NotificationCompat.Builder(context, AppConstants.SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_monitor_logo)
                .setContentTitle(context.getString(R.string.service_notification_title))
                .setContentText(merged)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(merged))
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();
    }

    // 更新前台服务通知内容，但不撤销服务身份。
    public void updateServiceNotification(String connectionState, boolean monitoringEnabled) {
        if (manager == null) {
            return;
        }
        manager.notify(
                AppConstants.SERVICE_NOTIFICATION_ID,
                buildServiceNotification(connectionState, monitoringEnabled)
        );
    }

    public void cancelServiceNotification() {
        if (manager != null) {
            manager.cancel(AppConstants.SERVICE_NOTIFICATION_ID);
        }
    }

    // 判断服务通知是否已经真正进入系统通知列表，供服务在正确时机收起常驻通知。
    public boolean hasServiceNotification() {
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        StatusBarNotification[] notifications = manager.getActiveNotifications();
        if (notifications == null || notifications.length == 0) {
            return false;
        }
        for (StatusBarNotification notification : notifications) {
            if (notification != null && notification.getId() == AppConstants.SERVICE_NOTIFICATION_ID) {
                return true;
            }
        }
        return false;
    }

    public void notifyAbnormalAlert(String title, String content, int notificationId) {
        if (manager == null || !PermissionHelper.hasNotificationPermission(context)) {
            return;
        }
        String safeTitle = title == null || title.trim().isEmpty() ? "异常提醒" : title.trim();
        String safeContent = content == null ? "" : content.trim();
        Intent intent = HostNavigationIntentFactory.forTab(context, HostTab.MARKET_MONITOR)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                200 + Math.max(0, notificationId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new NotificationCompat.Builder(context, AppConstants.ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_monitor_logo)
                .setContentTitle(safeTitle)
                .setContentText(safeContent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(safeContent))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .build();
        manager.notify(notificationId, notification);
    }
}

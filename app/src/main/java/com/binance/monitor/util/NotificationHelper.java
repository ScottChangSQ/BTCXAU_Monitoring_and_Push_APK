package com.binance.monitor.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.ui.main.MainActivity;

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

        manager.createNotificationChannel(serviceChannel);
    }

    public Notification buildServiceNotification(String connectionState, boolean monitoringEnabled) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
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

    public void cancelServiceNotification() {
        if (manager != null) {
            manager.cancel(AppConstants.SERVICE_NOTIFICATION_ID);
        }
    }
}

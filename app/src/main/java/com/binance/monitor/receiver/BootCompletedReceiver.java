package com.binance.monitor.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.service.MonitorService;

public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }
        Intent serviceIntent = new Intent(context, MonitorService.class);
        serviceIntent.setAction(AppConstants.ACTION_BOOTSTRAP);
        ContextCompat.startForegroundService(context, serviceIntent);
    }
}

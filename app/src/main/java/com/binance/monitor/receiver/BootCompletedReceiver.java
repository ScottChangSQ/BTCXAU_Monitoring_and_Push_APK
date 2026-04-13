package com.binance.monitor.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.service.MonitorServiceController;

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
        ConfigManager configManager = ConfigManager.getInstance(context.getApplicationContext());
        if (!configManager.isMonitoringEnabled() && !configManager.isFloatingEnabled()) {
            return;
        }
        MonitorServiceController.dispatch(context, AppConstants.ACTION_BOOTSTRAP);
    }
}

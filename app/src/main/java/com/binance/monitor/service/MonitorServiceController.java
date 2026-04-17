/*
 * 监控服务入口控制器，负责统一分发服务动作和首次启动检查。
 * 供主页面、图表页、账户页和设置页复用，避免各页面重复直接拉起服务。
 */
package com.binance.monitor.service;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;

public final class MonitorServiceController {

    private MonitorServiceController() {
    }

    // 分发一个明确的服务动作。
    public static void dispatch(@NonNull Context context, @Nullable String action) {
        Context appContext = context.getApplicationContext();
        String resolvedAction = action == null ? AppConstants.ACTION_BOOTSTRAP : action;
        ConfigManager configManager = ConfigManager.getInstance(appContext);
        if (!shouldStartService(
                MonitorService.isServiceRunning(),
                resolvedAction,
                configManager.isMonitoringEnabled(),
                configManager.isFloatingEnabled()
        )) {
            return;
        }
        Intent intent = new Intent(appContext, MonitorService.class);
        intent.setAction(resolvedAction);
        appContext.startService(intent);
    }

    // 确保监控服务至少已启动一次，避免页面直达时后台主链尚未建立。
    public static void ensureStarted(@NonNull Context context) {
        if (MonitorService.isServiceRunning()) {
            return;
        }
        dispatch(context, AppConstants.ACTION_BOOTSTRAP);
    }

    // 只有明确需要主链在线时才拉起服务，避免纯设置动作把已关闭的服务重新启动。
    static boolean shouldStartService(boolean serviceRunning,
                                      @Nullable String action,
                                      boolean monitoringEnabled,
                                      boolean floatingEnabled) {
        if (serviceRunning) {
            return true;
        }
        String resolvedAction = action == null ? AppConstants.ACTION_BOOTSTRAP : action;
        if (AppConstants.ACTION_BOOTSTRAP.equals(resolvedAction)) {
            return true;
        }
        if (AppConstants.ACTION_STOP_MONITORING.equals(resolvedAction)) {
            return false;
        }
        if (AppConstants.ACTION_REFRESH_CONFIG.equals(resolvedAction)
                || AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME.equals(resolvedAction)) {
            return monitoringEnabled || floatingEnabled;
        }
        return true;
    }
}

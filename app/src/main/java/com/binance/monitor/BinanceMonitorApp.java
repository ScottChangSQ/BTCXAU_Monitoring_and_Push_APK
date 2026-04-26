package com.binance.monitor;

import android.app.Application;

import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.runtime.AppForegroundTracker;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.theme.ThemeLauncherIconManager;
import com.binance.monitor.util.MainLooperSlowMessageLogger;

public class BinanceMonitorApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.DEBUG) {
            MainLooperSlowMessageLogger.install();
        }
        AppForegroundTracker.init(this);
        ConfigManager.getInstance(this);
        LogManager.getInstance(this);
        AbnormalRecordManager.getInstance(this);
        MonitorRepository.getInstance(this);
        ThemeLauncherIconManager.apply(this, ConfigManager.getInstance(this).getColorPalette());
        AccountStatsPreloadManager.getInstance(this).start();
    }
}

package com.binance.monitor;

import android.app.Application;

import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.ui.account.AccountStatsPreloadManager;

public class BinanceMonitorApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ConfigManager.getInstance(this);
        LogManager.getInstance(this);
        AbnormalRecordManager.getInstance(this);
        MonitorRepository.getInstance(this);
        AccountStatsPreloadManager.getInstance(this).start();
    }
}

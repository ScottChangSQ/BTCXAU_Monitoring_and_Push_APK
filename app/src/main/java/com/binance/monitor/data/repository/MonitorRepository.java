package com.binance.monitor.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.AppLogEntry;
import com.binance.monitor.data.model.KlineData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MonitorRepository {

    private static volatile MonitorRepository instance;

    private final ConfigManager configManager;
    private final LogManager logManager;
    private final AbnormalRecordManager recordManager;
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>("连接中");
    private final MutableLiveData<Boolean> monitoringEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Long> lastUpdateTime = new MutableLiveData<>(0L);
    private final MutableLiveData<Map<String, Double>> latestPrices = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Map<String, KlineData>> latestClosedKlines = new MutableLiveData<>(Collections.emptyMap());
    private final Map<String, Double> pricesCache = new HashMap<>();
    private final Map<String, KlineData> closedKlineCache = new HashMap<>();

    private MonitorRepository(Context context) {
        Context appContext = context.getApplicationContext();
        configManager = ConfigManager.getInstance(appContext);
        logManager = LogManager.getInstance(appContext);
        recordManager = AbnormalRecordManager.getInstance(appContext);
    }

    public static MonitorRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (MonitorRepository.class) {
                if (instance == null) {
                    instance = new MonitorRepository(context);
                }
            }
        }
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public AbnormalRecordManager getRecordManager() {
        return recordManager;
    }

    public LiveData<String> getConnectionStatus() {
        return connectionStatus;
    }

    public LiveData<Boolean> getMonitoringEnabled() {
        return monitoringEnabled;
    }

    public LiveData<Long> getLastUpdateTime() {
        return lastUpdateTime;
    }

    public LiveData<Map<String, Double>> getLatestPrices() {
        return latestPrices;
    }

    public LiveData<Map<String, KlineData>> getLatestClosedKlines() {
        return latestClosedKlines;
    }

    public LiveData<List<AppLogEntry>> getLogs() {
        return logManager.getLogsLiveData();
    }

    public LiveData<List<AbnormalRecord>> getRecords() {
        return recordManager.getRecordsLiveData();
    }

    public synchronized void updatePrice(String symbol, double price) {
        pricesCache.put(symbol, price);
        latestPrices.postValue(Collections.unmodifiableMap(new HashMap<>(pricesCache)));
        lastUpdateTime.postValue(System.currentTimeMillis());
    }

    public synchronized void updateClosedKline(KlineData data) {
        closedKlineCache.put(data.getSymbol(), data);
        latestClosedKlines.postValue(Collections.unmodifiableMap(new HashMap<>(closedKlineCache)));
        lastUpdateTime.postValue(System.currentTimeMillis());
    }

    public void setConnectionStatus(String status) {
        connectionStatus.postValue(status);
    }

    public void setMonitoringEnabled(boolean enabled) {
        monitoringEnabled.postValue(enabled);
    }

    public synchronized Map<String, Double> getLatestPriceSnapshot() {
        return new HashMap<>(pricesCache);
    }

    public synchronized Map<String, KlineData> getLatestClosedKlineSnapshot() {
        return new HashMap<>(closedKlineCache);
    }

    public void addRecord(AbnormalRecord record) {
        recordManager.addRecord(record);
    }
}

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
import com.binance.monitor.util.ChainLatencyTracer;

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
    private final MutableLiveData<Boolean> monitoringEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Long> lastUpdateTime = new MutableLiveData<>(0L);
    private final MutableLiveData<Map<String, Double>> displayPrices = new MutableLiveData<>(Collections.emptyMap());
    private final MutableLiveData<Map<String, KlineData>> displayKlines = new MutableLiveData<>(Collections.emptyMap());
    private final Map<String, Double> displayPriceCache = new HashMap<>();
    private final Map<String, KlineData> displayKlineCache = new HashMap<>();

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

    // 返回监控页和悬浮窗共用的最新价格展示快照。
    public LiveData<Map<String, Double>> getDisplayPrices() {
        return displayPrices;
    }

    // 返回监控页和悬浮窗共用的最新 K 线展示快照。
    public LiveData<Map<String, KlineData>> getDisplayKlines() {
        return displayKlines;
    }

    public LiveData<List<AppLogEntry>> getLogs() {
        return logManager.getLogsLiveData();
    }

    public LiveData<List<AbnormalRecord>> getRecords() {
        return recordManager.getRecordsLiveData();
    }

    // 更新某个产品的最新价格展示快照。
    public synchronized void updateDisplayPrice(String symbol, double price) {
        displayPriceCache.put(symbol, price);
        displayPrices.postValue(Collections.unmodifiableMap(new HashMap<>(displayPriceCache)));
        lastUpdateTime.postValue(System.currentTimeMillis());
    }

    // 更新某个产品的最新 K 线展示快照。
    public synchronized void updateDisplayKline(KlineData data) {
        displayKlineCache.put(data.getSymbol(), data);
        ChainLatencyTracer.markRepositoryKlinePublished(data.getSymbol(), data.getCloseTime());
        displayKlines.postValue(Collections.unmodifiableMap(new HashMap<>(displayKlineCache)));
        lastUpdateTime.postValue(System.currentTimeMillis());
    }

    public void setConnectionStatus(String status) {
        connectionStatus.postValue(status);
    }

    public void setMonitoringEnabled(boolean enabled) {
        monitoringEnabled.postValue(enabled);
    }

    // 读取当前价格展示快照。
    public synchronized Map<String, Double> getDisplayPriceSnapshot() {
        return new HashMap<>(displayPriceCache);
    }

    // 读取当前 K 线展示快照。
    public synchronized Map<String, KlineData> getDisplayKlineSnapshot() {
        return new HashMap<>(displayKlineCache);
    }

    public void addRecord(AbnormalRecord record) {
        recordManager.addRecord(record);
    }
}

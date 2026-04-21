package com.binance.monitor.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.AppLogEntry;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.runtime.market.MarketRuntimeStore;
import com.binance.monitor.runtime.market.MarketSelector;
import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;

import java.util.Collections;
import java.util.List;

public class MonitorRepository {

    private static volatile MonitorRepository instance;

    private final ConfigManager configManager;
    private final LogManager logManager;
    private final AbnormalRecordManager recordManager;
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>("连接中");
    private final MutableLiveData<Boolean> monitoringEnabled;
    private final MutableLiveData<Long> lastUpdateTime = new MutableLiveData<>(0L);
    private final MutableLiveData<MarketRuntimeSnapshot> marketRuntimeSnapshotLiveData =
            new MutableLiveData<>(MarketRuntimeSnapshot.empty());
    private final MarketRuntimeStore marketRuntimeStore = new MarketRuntimeStore();

    private MonitorRepository(Context context) {
        Context appContext = context.getApplicationContext();
        configManager = ConfigManager.getInstance(appContext);
        logManager = LogManager.getInstance(appContext);
        recordManager = AbnormalRecordManager.getInstance(appContext);
        monitoringEnabled = new MutableLiveData<>(configManager.isMonitoringEnabled());
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

    // 返回市场统一运行态快照，供正式市场消费链直接观察。
    public LiveData<MarketRuntimeSnapshot> getMarketRuntimeSnapshotLiveData() {
        return marketRuntimeSnapshotLiveData;
    }

    public LiveData<List<AppLogEntry>> getLogs() {
        return logManager.getLogsLiveData();
    }

    public LiveData<List<AbnormalRecord>> getRecords() {
        return recordManager.getRecordsLiveData();
    }

    public void setConnectionStatus(String status) {
        connectionStatus.postValue(status);
    }

    public void setMonitoringEnabled(boolean enabled) {
        configManager.setMonitoringEnabled(enabled);
        monitoringEnabled.postValue(enabled);
    }

    public synchronized void applyMarketRuntimeSnapshot(@Nullable MarketRuntimeSnapshot runtimeSnapshot) {
        if (runtimeSnapshot == null) {
            return;
        }
        MarketRuntimeSnapshot latestSnapshot = marketRuntimeStore.applySymbolWindows(
                new java.util.ArrayList<>(runtimeSnapshot.getSymbolWindows().values()),
                runtimeSnapshot.getUpdatedAt()
        );
        marketRuntimeSnapshotLiveData.postValue(latestSnapshot);
        publishDisplaySnapshotsLocked(latestSnapshot);
    }

    public synchronized double selectLatestPrice(@Nullable String symbol) {
        return MarketSelector.selectLatestPrice(marketRuntimeStore.getSnapshot(), symbol);
    }

    @Nullable
    public synchronized KlineData selectClosedMinute(@Nullable String symbol) {
        return MarketSelector.selectClosedMinute(marketRuntimeStore.getSnapshot(), symbol);
    }

    @Nullable
    public synchronized KlineData selectDisplayKline(@Nullable String symbol) {
        return MarketSelector.selectDisplayKline(marketRuntimeStore.getSnapshot(), symbol);
    }

    @NonNull
    public synchronized String selectMarketWindowSignature(@Nullable String symbol) {
        SymbolMarketWindow symbolWindow = marketRuntimeStore.getSnapshot().getSymbolWindow(symbol);
        return symbolWindow == null ? "" : symbolWindow.buildSignature();
    }

    public void addRecord(AbnormalRecord record) {
        recordManager.addRecord(record);
    }

    private void publishDisplaySnapshotsLocked(@NonNull MarketRuntimeSnapshot snapshot) {
        lastUpdateTime.postValue(snapshot.getUpdatedAt());
    }
}

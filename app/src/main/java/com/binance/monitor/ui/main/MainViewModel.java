package com.binance.monitor.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.data.repository.MonitorRepository;

import java.util.List;
import java.util.Map;

public class MainViewModel extends AndroidViewModel {

    private final MonitorRepository repository;

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = MonitorRepository.getInstance(application);
    }

    public LiveData<String> getConnectionStatus() {
        return repository.getConnectionStatus();
    }

    public LiveData<Boolean> getMonitoringEnabled() {
        return repository.getMonitoringEnabled();
    }

    public LiveData<Long> getLastUpdateTime() {
        return repository.getLastUpdateTime();
    }

    public LiveData<Map<String, Double>> getLatestPrices() {
        return repository.getLatestPrices();
    }

    public LiveData<Map<String, KlineData>> getLatestClosedKlines() {
        return repository.getLatestClosedKlines();
    }

    public LiveData<List<AbnormalRecord>> getRecords() {
        return repository.getRecords();
    }

    public SymbolConfig getSymbolConfig(String symbol) {
        return repository.getConfigManager().getSymbolConfig(symbol);
    }

    public SymbolConfig resetSymbolConfig(String symbol) {
        return repository.getConfigManager().resetSymbolConfig(symbol);
    }

    public void saveSymbolConfig(SymbolConfig config) {
        repository.getConfigManager().saveSymbolConfig(config);
    }

    public boolean isUseAndMode() {
        return repository.getConfigManager().isUseAndMode();
    }

    public void setUseAndMode(boolean useAndMode) {
        repository.getConfigManager().setUseAndMode(useAndMode);
    }

    public boolean isFloatingEnabled() {
        return repository.getConfigManager().isFloatingEnabled();
    }

    public void setFloatingEnabled(boolean enabled) {
        repository.getConfigManager().setFloatingEnabled(enabled);
    }

    public int getFloatingAlpha() {
        return repository.getConfigManager().getFloatingAlpha();
    }

    public void setFloatingAlpha(int alpha) {
        repository.getConfigManager().setFloatingAlpha(alpha);
    }

    public boolean isShowBtc() {
        return repository.getConfigManager().isShowBtc();
    }

    public void setShowBtc(boolean show) {
        repository.getConfigManager().setShowBtc(show);
    }

    public boolean isShowXau() {
        return repository.getConfigManager().isShowXau();
    }

    public void setShowXau(boolean show) {
        repository.getConfigManager().setShowXau(show);
    }

    public int getColorPalette() {
        return repository.getConfigManager().getColorPalette();
    }

    public void setColorPalette(int paletteId) {
        repository.getConfigManager().setColorPalette(paletteId);
    }

    public boolean isTabMarketMonitorVisible() {
        return repository.getConfigManager().isTabMarketMonitorVisible();
    }

    public void setTabMarketMonitorVisible(boolean visible) {
        repository.getConfigManager().setTabMarketMonitorVisible(visible);
    }

    public boolean isTabMarketChartVisible() {
        return repository.getConfigManager().isTabMarketChartVisible();
    }

    public void setTabMarketChartVisible(boolean visible) {
        repository.getConfigManager().setTabMarketChartVisible(visible);
    }

    public boolean isTabAccountStatsVisible() {
        return repository.getConfigManager().isTabAccountStatsVisible();
    }

    public void setTabAccountStatsVisible(boolean visible) {
        repository.getConfigManager().setTabAccountStatsVisible(visible);
    }
}

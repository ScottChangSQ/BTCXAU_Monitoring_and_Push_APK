package com.binance.monitor.ui.log;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.binance.monitor.data.model.AppLogEntry;
import com.binance.monitor.data.repository.MonitorRepository;

import java.util.List;
import java.util.Set;

public class LogViewModel extends AndroidViewModel {

    private final MonitorRepository repository;

    public LogViewModel(@NonNull Application application) {
        super(application);
        repository = MonitorRepository.getInstance(application);
    }

    public LiveData<List<AppLogEntry>> getLogs() {
        return repository.getLogs();
    }

    public void delete(String id) {
        repository.getLogManager().delete(id);
    }

    public void deleteSelected(Set<String> ids) {
        repository.getLogManager().delete(ids);
    }

    public void clearAll() {
        repository.getLogManager().clearAll();
    }
}

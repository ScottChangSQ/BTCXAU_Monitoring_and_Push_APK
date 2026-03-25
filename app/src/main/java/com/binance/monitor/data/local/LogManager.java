package com.binance.monitor.data.local;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.AppLogEntry;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LogManager {

    private static final int MAX_LOGS = 2000;
    private static volatile LogManager instance;

    private final File storeFile;
    private final MutableLiveData<List<AppLogEntry>> logsLiveData = new MutableLiveData<>();
    private final List<AppLogEntry> cache = new ArrayList<>();

    private LogManager(Context context) {
        storeFile = new File(context.getApplicationContext().getFilesDir(), "logs.json");
        loadFromDisk();
    }

    public static LogManager getInstance(Context context) {
        if (instance == null) {
            synchronized (LogManager.class) {
                if (instance == null) {
                    instance = new LogManager(context);
                }
            }
        }
        return instance;
    }

    public LiveData<List<AppLogEntry>> getLogsLiveData() {
        return logsLiveData;
    }

    public void info(String message) {
        add(AppConstants.LOG_INFO, message);
    }

    public void warn(String message) {
        add(AppConstants.LOG_WARN, message);
    }

    public void error(String message) {
        add(AppConstants.LOG_ERROR, message);
    }

    public synchronized void add(String level, String message) {
        cache.add(0, new AppLogEntry(UUID.randomUUID().toString(), System.currentTimeMillis(), level, message));
        trimLocked();
        persistLocked();
        publishLocked();
    }

    public synchronized void delete(String id) {
        cache.removeIf(entry -> entry.getId().equals(id));
        persistLocked();
        publishLocked();
    }

    public synchronized void delete(Set<String> ids) {
        Set<String> snapshot = new HashSet<>(ids);
        cache.removeIf(entry -> snapshot.contains(entry.getId()));
        persistLocked();
        publishLocked();
    }

    public synchronized void clearAll() {
        cache.clear();
        persistLocked();
        publishLocked();
    }

    private void trimLocked() {
        while (cache.size() > MAX_LOGS) {
            cache.remove(cache.size() - 1);
        }
    }

    private void publishLocked() {
        logsLiveData.postValue(Collections.unmodifiableList(new ArrayList<>(cache)));
    }

    private void loadFromDisk() {
        synchronized (this) {
            cache.clear();
            if (storeFile.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(storeFile), StandardCharsets.UTF_8))) {
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    if (builder.length() > 0) {
                        JSONArray array = new JSONArray(builder.toString());
                        for (int i = 0; i < array.length(); i++) {
                            cache.add(AppLogEntry.fromJson(array.getJSONObject(i)));
                        }
                    }
                } catch (Exception ignored) {
                    cache.clear();
                }
            }
            trimLocked();
            publishLocked();
        }
    }

    private void persistLocked() {
        JSONArray array = new JSONArray();
        for (AppLogEntry entry : cache) {
            try {
                array.put(entry.toJson());
            } catch (JSONException ignored) {
            }
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new java.io.FileOutputStream(storeFile, false), StandardCharsets.UTF_8)) {
            writer.write(array.toString());
        } catch (Exception ignored) {
        }
    }
}

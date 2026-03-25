package com.binance.monitor.data.local;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.binance.monitor.data.model.AbnormalRecord;

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
import java.util.List;
import java.util.UUID;

public class AbnormalRecordManager {

    private static final int MAX_RECORDS = 500;
    private static volatile AbnormalRecordManager instance;

    private final File storeFile;
    private final MutableLiveData<List<AbnormalRecord>> recordsLiveData = new MutableLiveData<>();
    private final List<AbnormalRecord> cache = new ArrayList<>();

    private AbnormalRecordManager(Context context) {
        storeFile = new File(context.getApplicationContext().getFilesDir(), "abnormal_records.json");
        loadFromDisk();
    }

    public static AbnormalRecordManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AbnormalRecordManager.class) {
                if (instance == null) {
                    instance = new AbnormalRecordManager(context);
                }
            }
        }
        return instance;
    }

    public LiveData<List<AbnormalRecord>> getRecordsLiveData() {
        return recordsLiveData;
    }

    public synchronized void addRecord(AbnormalRecord record) {
        cache.add(0, record);
        trimLocked();
        persistLocked();
        publishLocked();
    }

    public synchronized AbnormalRecord createRecord(String symbol,
                                                    long closeTime,
                                                    double openPrice,
                                                    double closePrice,
                                                    double volume,
                                                    double amount,
                                                    double priceChange,
                                                    double percentChange,
                                                    String triggerSummary) {
        return new AbnormalRecord(
                UUID.randomUUID().toString(),
                symbol,
                System.currentTimeMillis(),
                closeTime,
                openPrice,
                closePrice,
                volume,
                amount,
                priceChange,
                percentChange,
                triggerSummary
        );
    }

    private void trimLocked() {
        while (cache.size() > MAX_RECORDS) {
            cache.remove(cache.size() - 1);
        }
    }

    private void publishLocked() {
        recordsLiveData.postValue(Collections.unmodifiableList(new ArrayList<>(cache)));
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
                            cache.add(AbnormalRecord.fromJson(array.getJSONObject(i)));
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
        for (AbnormalRecord record : cache) {
            try {
                array.put(record.toJson());
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

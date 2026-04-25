/*
 * 异常记录本地存储管理器，负责把异常事件落盘并提供列表观察能力。
 * 主页面、图表页和监控服务都通过这里共享异常记录数据。
 */
package com.binance.monitor.data.local;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.util.ProductSymbolMapper;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AbnormalRecordManager {

    private static final String TAG = "AbnormalRecordManager";
    // 图表页需要保留更长的异常历史，避免圆点因为本地缓存过小而缺失。
    private static final int MAX_RECORDS = 5000;
    private static final long PERSIST_DELAY_MS = 1_200L;
    private static final int PERSIST_BATCH_THRESHOLD = 8;
    private static volatile AbnormalRecordManager instance;

    private final File storeFile;
    private final MutableLiveData<List<AbnormalRecord>> recordsLiveData = new MutableLiveData<>();
    private final List<AbnormalRecord> cache = new ArrayList<>();
    private final ScheduledExecutorService persistExecutor = Executors.newSingleThreadScheduledExecutor();
    private volatile String lastStorageError = "";
    private long persistGeneration;
    private int pendingPersistMutationCount;
    private ScheduledFuture<?> pendingPersistFuture;

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

    public String getLastStorageError() {
        return lastStorageError;
    }

    public synchronized void addRecord(AbnormalRecord record) {
        cache.add(0, record);
        trimLocked();
        schedulePersistLocked(false);
        publishLocked();
    }

    // 仅在本地不存在相同记录时才写入，避免服务端增量同步导致重复项。
    public synchronized boolean addRecordIfAbsent(AbnormalRecord record) {
        if (record == null || containsRecordIdLocked(record.getId())) {
            return false;
        }
        cache.add(0, record);
        trimLocked();
        schedulePersistLocked(false);
        publishLocked();
        return true;
    }

    // 用服务端真值整批替换当前异常记录缓存，供 stream bootstrap 全量同步复用。
    public synchronized void replaceAll(List<AbnormalRecord> records) {
        cache.clear();
        if (records != null) {
            for (AbnormalRecord item : records) {
                if (item == null || containsRecordIdLocked(item.getId())) {
                    continue;
                }
                cache.add(item);
            }
        }
        trimLocked();
        schedulePersistLocked(false);
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
                buildStableRecordId(symbol, closeTime, triggerSummary),
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

    // 生成与服务端同口径的稳定记录 ID，便于 stream 全量/增量写入时去重。
    static String buildStableRecordId(String symbol, long closeTime, String triggerSummary) {
        String raw = normalizeStableRecordSymbol(symbol)
                + ":"
                + closeTime
                + ":"
                + (triggerSummary == null ? "" : triggerSummary.trim());
        return sha1(raw);
    }

    public synchronized void clearAll() {
        cache.clear();
        schedulePersistLocked(true);
        publishLocked();
    }

    private void trimLocked() {
        while (cache.size() > MAX_RECORDS) {
            cache.remove(cache.size() - 1);
        }
    }

    // 判断当前缓存里是否已经存在同一条异常记录。
    private boolean containsRecordIdLocked(String recordId) {
        if (recordId == null || recordId.trim().isEmpty()) {
            return false;
        }
        for (AbnormalRecord item : cache) {
            if (item != null && recordId.equals(item.getId())) {
                return true;
            }
        }
        return false;
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
                            AbnormalRecord record = AbnormalRecord.parseOrNull(array.optJSONObject(i));
                            if (record == null) {
                                lastStorageError = "skip malformed abnormal record at index " + i;
                                Log.w(TAG, lastStorageError);
                                continue;
                            }
                            cache.add(record);
                        }
                    }
                } catch (Exception exception) {
                    lastStorageError = "load abnormal records failed: " + exception.getMessage();
                    Log.w(TAG, lastStorageError, exception);
                    cache.clear();
                }
            }
            trimLocked();
            publishLocked();
        }
    }

    private void schedulePersistLocked(boolean forceImmediate) {
        persistGeneration++;
        long generation = persistGeneration;
        pendingPersistMutationCount++;
        long delayMs = forceImmediate || pendingPersistMutationCount >= PERSIST_BATCH_THRESHOLD
                ? 0L
                : PERSIST_DELAY_MS;
        if (pendingPersistFuture != null) {
            pendingPersistFuture.cancel(false);
        }
        List<AbnormalRecord> snapshot = new ArrayList<>(cache);
        pendingPersistFuture = persistExecutor.schedule(() -> persistSnapshot(snapshot, generation), delayMs, TimeUnit.MILLISECONDS);
    }

    private void persistSnapshot(@NonNull List<AbnormalRecord> snapshot, long generation) {
        if (snapshot.isEmpty()) {
            deleteStoreFile();
            synchronized (this) {
                if (generation != persistGeneration) {
                    return;
                }
                pendingPersistMutationCount = 0;
                pendingPersistFuture = null;
            }
            return;
        }
        JSONArray array = new JSONArray();
        for (AbnormalRecord record : snapshot) {
            try {
                array.put(record.toJson());
            } catch (JSONException exception) {
                lastStorageError = "serialize abnormal record failed: " + exception.getMessage();
                Log.w(TAG, lastStorageError, exception);
            }
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new java.io.FileOutputStream(storeFile, false), StandardCharsets.UTF_8)) {
            writer.write(array.toString());
            lastStorageError = "";
        } catch (Exception exception) {
            lastStorageError = "persist abnormal records failed: " + exception.getMessage();
            Log.w(TAG, lastStorageError, exception);
        }
        synchronized (this) {
            if (generation != persistGeneration) {
                return;
            }
            pendingPersistMutationCount = 0;
            pendingPersistFuture = null;
        }
    }

    private void deleteStoreFile() {
        if (!storeFile.exists()) {
            lastStorageError = "";
            return;
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            storeFile.delete();
            lastStorageError = "";
        } catch (Exception exception) {
            lastStorageError = "delete abnormal record store failed: " + exception.getMessage();
            Log.w(TAG, lastStorageError, exception);
        }
    }

    private static String normalizeStableRecordSymbol(String symbol) {
        return ProductSymbolMapper.toMarketSymbol(symbol);
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("生成异常记录ID失败", exception);
        }
    }
}

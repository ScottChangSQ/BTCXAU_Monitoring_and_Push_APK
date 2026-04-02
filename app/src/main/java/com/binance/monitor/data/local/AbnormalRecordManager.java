/*
 * 异常记录本地存储管理器，负责把异常事件落盘并提供列表观察能力。
 * 主页面、图表页和监控服务都通过这里共享异常记录数据。
 */
package com.binance.monitor.data.local;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.AbnormalRecord;

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

public class AbnormalRecordManager {

    // 图表页需要保留更长的异常历史，避免圆点因为本地缓存过小而缺失。
    private static final int MAX_RECORDS = 5000;
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

    // 仅在本地不存在相同记录时才写入，避免服务端增量同步导致重复项。
    public synchronized boolean addRecordIfAbsent(AbnormalRecord record) {
        if (record == null || containsRecordIdLocked(record.getId())) {
            return false;
        }
        cache.add(0, record);
        trimLocked();
        persistLocked();
        publishLocked();
        return true;
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

    // 生成与服务端同口径的稳定记录 ID，便于本地补判和服务端回补去重。
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
        if (storeFile.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                storeFile.delete();
            } catch (Exception ignored) {
            }
        }
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

    private static String normalizeStableRecordSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (AppConstants.SYMBOL_XAU.equals(normalized) || "XAU".equals(normalized) || "GOLD".equals(normalized)) {
            return "XAUUSD";
        }
        if (AppConstants.SYMBOL_BTC.equals(normalized) || "BTC".equals(normalized) || "BTCUSD".equals(normalized) || "XBT".equals(normalized)) {
            return "BTCUSDT";
        }
        return normalized;
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

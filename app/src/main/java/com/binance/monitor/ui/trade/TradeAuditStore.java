/*
 * 交易审计本地存储，负责保存最近交易链的关键阶段事实。
 * 与 TradeAuditEntry、TradeExecutionCoordinator、BatchTradeCoordinator 协同工作。
 */
package com.binance.monitor.ui.trade;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TradeAuditStore {
    private static final String PREF_NAME = "trade_audit_store";
    private static final String KEY_ENTRIES_JSON = "entries_json";
    private static final int MAX_ENTRIES = 200;

    interface JsonStore {
        @NonNull
        String getEntriesJson();

        void setEntriesJson(@Nullable String json);
    }

    interface Clock {
        long now();
    }

    private final JsonStore jsonStore;
    private final Clock clock;

    // 创建正式本地审计存储。
    public TradeAuditStore(@NonNull Context context) {
        this(new SharedPreferenceStore(context.getApplicationContext()), System::currentTimeMillis);
    }

    // 创建仅用于单测的内存存储。
    @NonNull
    public static TradeAuditStore createInMemory(@NonNull Clock clock) {
        return new TradeAuditStore(new MemoryStore(), clock);
    }

    TradeAuditStore(@NonNull JsonStore jsonStore, @NonNull Clock clock) {
        this.jsonStore = jsonStore;
        this.clock = clock;
    }

    // 记录一条审计事实，并保持最近优先。
    public void record(@Nullable TradeAuditEntry entry) {
        if (entry == null || entry.getTraceId().isEmpty()) {
            return;
        }
        List<TradeAuditEntry> entries = readEntries();
        TradeAuditEntry normalized = entry.getCreatedAt() > 0L ? entry : entry.withCreatedAt(clock.now());
        entries.add(0, normalized);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        writeEntries(entries);
    }

    // 读取最近记录。
    @NonNull
    public List<TradeAuditEntry> getRecent(int limit) {
        List<TradeAuditEntry> entries = readEntries();
        if (limit <= 0 || entries.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(entries.subList(0, Math.min(limit, entries.size())));
    }

    // 按 traceId 返回同一交易链的所有记录。
    @NonNull
    public List<TradeAuditEntry> lookup(@Nullable String traceId) {
        String safeTraceId = traceId == null ? "" : traceId.trim();
        if (safeTraceId.isEmpty()) {
            return Collections.emptyList();
        }
        List<TradeAuditEntry> matched = new ArrayList<>();
        for (TradeAuditEntry entry : readEntries()) {
            if (safeTraceId.equals(entry.getTraceId())) {
                matched.add(entry);
            }
        }
        return matched;
    }

    @NonNull
    private List<TradeAuditEntry> readEntries() {
        String json = jsonStore.getEntriesJson();
        if (json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JSONArray array = new JSONArray(json);
            List<TradeAuditEntry> entries = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                entries.add(TradeAuditEntry.fromJson(array.optJSONObject(index)));
            }
            return entries;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private void writeEntries(@NonNull List<TradeAuditEntry> entries) {
        JSONArray array = new JSONArray();
        for (TradeAuditEntry entry : entries) {
            if (entry != null) {
                array.put(entry.toJson());
            }
        }
        jsonStore.setEntriesJson(array.toString());
    }

    private static final class SharedPreferenceStore implements JsonStore {
        private final SharedPreferences preferences;

        private SharedPreferenceStore(@NonNull Context context) {
            this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        @NonNull
        @Override
        public String getEntriesJson() {
            String stored = preferences.getString(KEY_ENTRIES_JSON, "");
            return stored == null ? "" : stored;
        }

        @Override
        public void setEntriesJson(@Nullable String json) {
            preferences.edit().putString(KEY_ENTRIES_JSON, json == null ? "" : json).apply();
        }
    }

    private static final class MemoryStore implements JsonStore {
        private String json = "";

        @NonNull
        @Override
        public String getEntriesJson() {
            return json;
        }

        @Override
        public void setEntriesJson(@Nullable String json) {
            this.json = json == null ? "" : json;
        }
    }
}

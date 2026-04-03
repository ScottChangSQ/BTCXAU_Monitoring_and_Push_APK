/*
 * v2 快照存储，仅保存 market/account 两类原始 JSON 快照。
 * 新架构下它只负责快速恢复页面基础状态，不参与图表最终真值判断。
 */
package com.binance.monitor.data.local;

import android.content.Context;
import android.content.SharedPreferences;

public class V2SnapshotStore {

    private static final String PREF_NAME = "v2_snapshot_store";
    private static final String KEY_MARKET_SNAPSHOT = "market_snapshot";
    private static final String KEY_ACCOUNT_SNAPSHOT = "account_snapshot";

    private final KeyValueStore store;

    public V2SnapshotStore(Context context) {
        this(new SharedPreferencesStore(
                context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        ));
    }

    V2SnapshotStore(KeyValueStore store) {
        this.store = store;
    }

    // 保存 market snapshot 原始 JSON。
    public void writeMarketSnapshot(String body) {
        store.put(KEY_MARKET_SNAPSHOT, safe(body));
    }

    // 读取 market snapshot 原始 JSON。
    public String readMarketSnapshot() {
        return store.get(KEY_MARKET_SNAPSHOT);
    }

    // 保存 account snapshot 原始 JSON。
    public void writeAccountSnapshot(String body) {
        store.put(KEY_ACCOUNT_SNAPSHOT, safe(body));
    }

    // 读取 account snapshot 原始 JSON。
    public String readAccountSnapshot() {
        return store.get(KEY_ACCOUNT_SNAPSHOT);
    }

    // 清空全部 v2 快照缓存。
    public void clearAll() {
        store.clear();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    interface KeyValueStore {
        String get(String key);

        void put(String key, String value);

        void clear();
    }

    private static final class SharedPreferencesStore implements KeyValueStore {
        private final SharedPreferences preferences;

        private SharedPreferencesStore(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public String get(String key) {
            return preferences.getString(key, "");
        }

        @Override
        public void put(String key, String value) {
            preferences.edit().putString(key, value).apply();
        }

        @Override
        public void clear() {
            preferences.edit().clear().apply();
        }
    }
}

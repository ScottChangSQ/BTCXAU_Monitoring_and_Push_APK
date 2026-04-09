/*
 * 异常提醒派发记录存储，负责持久化最近已经消费过的服务端告警 ID。
 * MonitorService 通过这里保证相同 alert 即使在重连或重新打开 APP 后也不会重复通知。
 */
package com.binance.monitor.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;

import java.util.LinkedHashSet;
import java.util.Set;

public class AbnormalAlertDispatchStore {

    private static final String PREF_NAME = "abnormal_alert_dispatch_store";
    private static final String KEY_ALERT_IDS = "alert_ids";
    private static final int MAX_IDS = 256;

    private final SharedPreferences preferences;
    private final LinkedHashSet<String> alertIds = new LinkedHashSet<>();

    public AbnormalAlertDispatchStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadFromPrefs();
    }

    // 返回当前是否已经消费过该服务端告警。
    public synchronized boolean hasDispatched(String alertId) {
        String safeAlertId = safeTrim(alertId);
        return !safeAlertId.isEmpty() && alertIds.contains(safeAlertId);
    }

    // 记录已消费的服务端告警，并持久化到本地。
    public synchronized void markDispatched(String alertId) {
        String safeAlertId = safeTrim(alertId);
        if (safeAlertId.isEmpty()) {
            return;
        }
        if (alertIds.contains(safeAlertId)) {
            return;
        }
        alertIds.add(safeAlertId);
        trimLocked();
        persistLocked();
    }

    // 返回最近已消费告警 ID 的只读快照，供运行时去重辅助逻辑直接使用。
    public synchronized Set<String> snapshot() {
        return new LinkedHashSet<>(alertIds);
    }

    private void loadFromPrefs() {
        synchronized (this) {
            alertIds.clear();
            String raw = preferences.getString(KEY_ALERT_IDS, "");
            if (raw == null || raw.trim().isEmpty()) {
                return;
            }
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    String alertId = safeTrim(array.optString(i, ""));
                    if (!alertId.isEmpty()) {
                        alertIds.add(alertId);
                    }
                }
            } catch (Exception ignored) {
                alertIds.clear();
            }
            trimLocked();
        }
    }

    private void trimLocked() {
        while (alertIds.size() > MAX_IDS) {
            String oldest = alertIds.iterator().next();
            alertIds.remove(oldest);
        }
    }

    private void persistLocked() {
        JSONArray array = new JSONArray();
        for (String alertId : alertIds) {
            array.put(alertId);
        }
        preferences.edit().putString(KEY_ALERT_IDS, array.toString()).apply();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}

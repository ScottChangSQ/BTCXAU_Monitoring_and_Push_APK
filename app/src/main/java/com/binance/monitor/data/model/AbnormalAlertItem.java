/*
 * 异常提醒模型，负责承接网关返回的提醒内容并供前台服务统一发通知。
 */
package com.binance.monitor.data.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AbnormalAlertItem {

    private final String id;
    private final List<String> symbols;
    private final String title;
    private final String content;
    private final long closeTime;
    private final long createdAt;

    public AbnormalAlertItem(String id,
                             List<String> symbols,
                             String title,
                             String content,
                             long closeTime,
                             long createdAt) {
        this.id = id == null ? "" : id;
        this.symbols = symbols == null ? new ArrayList<>() : new ArrayList<>(symbols);
        this.title = title == null ? "" : title;
        this.content = content == null ? "" : content;
        this.closeTime = closeTime;
        this.createdAt = createdAt;
    }

    public static AbnormalAlertItem fromJson(JSONObject object) throws JSONException {
        JSONArray symbolsArray = object == null ? null : object.optJSONArray("symbols");
        List<String> symbols = new ArrayList<>();
        if (symbolsArray != null) {
            for (int i = 0; i < symbolsArray.length(); i++) {
                symbols.add(symbolsArray.optString(i, ""));
            }
        }
        return new AbnormalAlertItem(
                object == null ? "" : object.optString("id", ""),
                symbols,
                object == null ? "" : object.optString("title", ""),
                object == null ? "" : object.optString("content", ""),
                object == null ? 0L : object.optLong("closeTime", 0L),
                object == null ? 0L : object.optLong("createdAt", 0L)
        );
    }

    public String getId() {
        return id;
    }

    public List<String> getSymbols() {
        return Collections.unmodifiableList(symbols);
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}

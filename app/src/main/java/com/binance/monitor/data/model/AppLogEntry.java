package com.binance.monitor.data.model;

import org.json.JSONException;
import org.json.JSONObject;

public class AppLogEntry {

    private final String id;
    private final long timestamp;
    private final String level;
    private final String message;

    public AppLogEntry(String id, long timestamp, String level, String message) {
        this.id = id;
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("timestamp", timestamp);
        object.put("level", level);
        object.put("message", message);
        return object;
    }

    public static AppLogEntry fromJson(JSONObject object) throws JSONException {
        return new AppLogEntry(
                object.getString("id"),
                object.getLong("timestamp"),
                object.getString("level"),
                object.getString("message")
        );
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}

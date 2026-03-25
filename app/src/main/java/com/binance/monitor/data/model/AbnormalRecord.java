package com.binance.monitor.data.model;

import org.json.JSONException;
import org.json.JSONObject;

public class AbnormalRecord {

    private final String id;
    private final String symbol;
    private final long timestamp;
    private final long closeTime;
    private final double openPrice;
    private final double closePrice;
    private final double volume;
    private final double amount;
    private final double priceChange;
    private final double percentChange;
    private final String triggerSummary;

    public AbnormalRecord(String id,
                          String symbol,
                          long timestamp,
                          long closeTime,
                          double openPrice,
                          double closePrice,
                          double volume,
                          double amount,
                          double priceChange,
                          double percentChange,
                          String triggerSummary) {
        this.id = id;
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.closeTime = closeTime;
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.amount = amount;
        this.priceChange = priceChange;
        this.percentChange = percentChange;
        this.triggerSummary = triggerSummary;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("symbol", symbol);
        object.put("timestamp", timestamp);
        object.put("closeTime", closeTime);
        object.put("openPrice", openPrice);
        object.put("closePrice", closePrice);
        object.put("volume", volume);
        object.put("amount", amount);
        object.put("priceChange", priceChange);
        object.put("percentChange", percentChange);
        object.put("triggerSummary", triggerSummary);
        return object;
    }

    public static AbnormalRecord fromJson(JSONObject object) throws JSONException {
        return new AbnormalRecord(
                object.getString("id"),
                object.getString("symbol"),
                object.getLong("timestamp"),
                object.getLong("closeTime"),
                object.getDouble("openPrice"),
                object.getDouble("closePrice"),
                object.getDouble("volume"),
                object.getDouble("amount"),
                object.getDouble("priceChange"),
                object.getDouble("percentChange"),
                object.getString("triggerSummary")
        );
    }

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public double getVolume() {
        return volume;
    }

    public double getAmount() {
        return amount;
    }

    public double getPriceChange() {
        return priceChange;
    }

    public double getPercentChange() {
        return percentChange;
    }

    public String getTriggerSummary() {
        return triggerSummary;
    }
}

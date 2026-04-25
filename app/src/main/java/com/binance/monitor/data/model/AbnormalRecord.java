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
        AbnormalRecord record = parseOrNull(object);
        if (record == null) {
            throw new JSONException("invalid abnormal record");
        }
        return record;
    }

    public static AbnormalRecord parseOrNull(JSONObject object) {
        if (object == null) {
            return null;
        }
        try {
            String id = object.getString("id").trim();
            String symbol = object.getString("symbol").trim();
            long timestamp = object.getLong("timestamp");
            long closeTime = object.getLong("closeTime");
            double openPrice = object.getDouble("openPrice");
            double closePrice = object.getDouble("closePrice");
            double volume = object.getDouble("volume");
            double amount = object.getDouble("amount");
            double priceChange = object.getDouble("priceChange");
            double percentChange = object.getDouble("percentChange");
            if (id.isEmpty()
                    || symbol.isEmpty()
                    || timestamp <= 0L
                    || closeTime <= 0L
                    || !isFinite(openPrice)
                    || !isFinite(closePrice)
                    || !isFinite(volume)
                    || !isFinite(amount)
                    || !isFinite(priceChange)
                    || !isFinite(percentChange)) {
                return null;
            }
            return new AbnormalRecord(
                    id,
                    symbol,
                    timestamp,
                    closeTime,
                    openPrice,
                    closePrice,
                    volume,
                    amount,
                    priceChange,
                    percentChange,
                    object.optString("triggerSummary", "")
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
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

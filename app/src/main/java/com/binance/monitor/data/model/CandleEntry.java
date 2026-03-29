package com.binance.monitor.data.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CandleEntry {

    private final String symbol;
    private final long openTime;
    private final long closeTime;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final double volume;
    private final double quoteVolume;

    public CandleEntry(String symbol,
                       long openTime,
                       long closeTime,
                       double open,
                       double high,
                       double low,
                       double close,
                       double volume,
                       double quoteVolume) {
        this.symbol = symbol;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.quoteVolume = quoteVolume;
    }

    public static CandleEntry fromRest(String symbol, JSONArray item) throws JSONException {
        return new CandleEntry(
                symbol,
                item.getLong(0),
                item.getLong(6),
                item.getDouble(1),
                item.getDouble(2),
                item.getDouble(3),
                item.getDouble(4),
                item.getDouble(5),
                item.getDouble(7)
        );
    }

    public static CandleEntry fromJson(JSONObject item) throws JSONException {
        return new CandleEntry(
                item.getString("symbol"),
                item.getLong("openTime"),
                item.getLong("closeTime"),
                item.getDouble("open"),
                item.getDouble("high"),
                item.getDouble("low"),
                item.getDouble("close"),
                item.getDouble("volume"),
                item.optDouble("quoteVolume", 0d)
        );
    }

    public JSONObject toJson() throws JSONException {
        JSONObject out = new JSONObject();
        out.put("symbol", symbol);
        out.put("openTime", openTime);
        out.put("closeTime", closeTime);
        out.put("open", open);
        out.put("high", high);
        out.put("low", low);
        out.put("close", close);
        out.put("volume", volume);
        out.put("quoteVolume", quoteVolume);
        return out;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getOpenTime() {
        return openTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public double getVolume() {
        return volume;
    }

    public double getQuoteVolume() {
        return quoteVolume;
    }
}

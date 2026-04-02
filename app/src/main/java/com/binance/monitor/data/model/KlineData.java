package com.binance.monitor.data.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class KlineData {

    private final String symbol;
    private final double openPrice;
    private final double highPrice;
    private final double lowPrice;
    private final double closePrice;
    private final double volume;
    private final double quoteAssetVolume;
    private final long openTime;
    private final long closeTime;
    private final boolean closed;

    public KlineData(String symbol,
                     double openPrice,
                     double highPrice,
                     double lowPrice,
                     double closePrice,
                     double volume,
                     double quoteAssetVolume,
                     long openTime,
                     long closeTime,
                     boolean closed) {
        this.symbol = symbol;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.quoteAssetVolume = quoteAssetVolume;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.closed = closed;
    }

    public static KlineData fromRest(String symbol, JSONArray item) throws JSONException {
        return new KlineData(
                symbol,
                item.getDouble(1),
                item.getDouble(2),
                item.getDouble(3),
                item.getDouble(4),
                item.getDouble(5),
                item.getDouble(7),
                item.getLong(0),
                item.getLong(6),
                true
        );
    }

    public static KlineData fromSocket(String symbol, JSONObject kline) throws JSONException {
        return new KlineData(
                symbol,
                Double.parseDouble(kline.getString("o")),
                Double.parseDouble(kline.getString("h")),
                Double.parseDouble(kline.getString("l")),
                Double.parseDouble(kline.getString("c")),
                Double.parseDouble(kline.getString("v")),
                Double.parseDouble(kline.getString("q")),
                kline.getLong("t"),
                kline.getLong("T"),
                kline.getBoolean("x")
        );
    }

    public String getSymbol() {
        return symbol;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getHighPrice() {
        return highPrice;
    }

    public double getLowPrice() {
        return lowPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public double getVolume() {
        return volume;
    }

    public double getQuoteAssetVolume() {
        return quoteAssetVolume;
    }

    public long getOpenTime() {
        return openTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public boolean isClosed() {
        return closed;
    }

    public double getPriceChange() {
        return closePrice - openPrice;
    }

    public double getAbsolutePriceChange() {
        return Math.abs(getPriceChange());
    }

    public double getPercentChange() {
        if (openPrice == 0d) {
            return 0d;
        }
        return (getPriceChange() / openPrice) * 100d;
    }
}

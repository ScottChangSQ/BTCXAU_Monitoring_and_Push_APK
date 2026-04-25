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
        KlineData data = parseRestOrNull(symbol, item);
        if (data == null) {
            throw new JSONException("invalid REST kline");
        }
        return data;
    }

    public static KlineData fromSocket(String symbol, JSONObject kline) throws JSONException {
        KlineData data = parseSocketOrNull(symbol, kline);
        if (data == null) {
            throw new JSONException("invalid socket kline");
        }
        return data;
    }

    public static KlineData parseRestOrNull(String symbol, JSONArray item) {
        try {
            String safeSymbol = safeSymbol(symbol);
            if (safeSymbol.isEmpty() || item == null || item.length() <= 7) {
                return null;
            }
            return buildOrNull(
                    safeSymbol,
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
        } catch (Exception exception) {
            return null;
        }
    }

    public static KlineData parseSocketOrNull(String symbol, JSONObject kline) {
        try {
            String safeSymbol = safeSymbol(symbol);
            if (safeSymbol.isEmpty() || kline == null) {
                return null;
            }
            return buildOrNull(
                    safeSymbol,
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
        } catch (Exception exception) {
            return null;
        }
    }

    private static KlineData buildOrNull(String symbol,
                                         double openPrice,
                                         double highPrice,
                                         double lowPrice,
                                         double closePrice,
                                         double volume,
                                         double quoteAssetVolume,
                                         long openTime,
                                         long closeTime,
                                         boolean closed) {
        if (!isFinite(openPrice)
                || !isFinite(highPrice)
                || !isFinite(lowPrice)
                || !isFinite(closePrice)
                || !isFinite(volume)
                || !isFinite(quoteAssetVolume)
                || openTime <= 0L
                || closeTime <= 0L) {
            return null;
        }
        return new KlineData(
                symbol,
                openPrice,
                highPrice,
                lowPrice,
                closePrice,
                volume,
                quoteAssetVolume,
                openTime,
                closeTime,
                closed
        );
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String safeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
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

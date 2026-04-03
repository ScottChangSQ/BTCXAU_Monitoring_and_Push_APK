/*
 * 实时成交模型，负责承接 Binance aggTrade 推送并提供本地 1 分钟 K 线构建所需字段。
 * WebSocketManager 通过它把成交流转换成统一的内部数据结构。
 */
package com.binance.monitor.data.model;

import org.json.JSONException;
import org.json.JSONObject;

public class TradeTickData {

    private final String symbol;
    private final double price;
    private final double quantity;
    private final long tradeTime;
    private final long eventTime;

    public TradeTickData(String symbol,
                         double price,
                         double quantity,
                         long tradeTime,
                         long eventTime) {
        this.symbol = symbol == null ? "" : symbol;
        this.price = price;
        this.quantity = quantity;
        this.tradeTime = tradeTime;
        this.eventTime = eventTime;
    }

    public static TradeTickData fromSocket(JSONObject payload) throws JSONException {
        if (payload == null) {
            throw new JSONException("payload is null");
        }
        return new TradeTickData(
                payload.optString("s", ""),
                Double.parseDouble(payload.optString("p", "0")),
                Double.parseDouble(payload.optString("q", "0")),
                payload.optLong("T", payload.optLong("E", 0L)),
                payload.optLong("E", payload.optLong("T", 0L))
        );
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public double getQuantity() {
        return quantity;
    }

    public long getTradeTime() {
        return tradeTime;
    }

    public long getEventTime() {
        return eventTime;
    }
}

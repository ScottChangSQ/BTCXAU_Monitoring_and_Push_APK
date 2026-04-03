/*
 * v2 图表序列模型，保存闭合 K 线列表与当前未收盘 patch。
 * MarketChartActivity 后续会基于它区分历史真值与实时补丁。
 */
package com.binance.monitor.data.model.v2;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MarketSeriesPayload {

    private final String symbol;
    private final String interval;
    private final long serverTime;
    private final List<CandleEntry> candles;
    private final CandleEntry latestPatch;
    private final String nextSyncToken;
    private final String rawJson;

    public MarketSeriesPayload(String symbol,
                               String interval,
                               long serverTime,
                               List<CandleEntry> candles,
                               CandleEntry latestPatch,
                               String nextSyncToken,
                               String rawJson) {
        this.symbol = symbol == null ? "" : symbol;
        this.interval = interval == null ? "" : interval;
        this.serverTime = serverTime;
        this.candles = candles == null ? new ArrayList<>() : new ArrayList<>(candles);
        this.latestPatch = latestPatch;
        this.nextSyncToken = nextSyncToken == null ? "" : nextSyncToken;
        this.rawJson = rawJson == null ? "" : rawJson;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getInterval() {
        return interval;
    }

    public long getServerTime() {
        return serverTime;
    }

    public List<CandleEntry> getCandles() {
        return Collections.unmodifiableList(candles);
    }

    public CandleEntry getLatestPatch() {
        return latestPatch;
    }

    public String getNextSyncToken() {
        return nextSyncToken;
    }

    public String getRawJson() {
        return rawJson;
    }
}

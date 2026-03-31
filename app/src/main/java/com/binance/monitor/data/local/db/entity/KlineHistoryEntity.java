/*
 * 行情历史实体，按交易对和周期保存 K 线。
 * 与图表页的 ChartHistoryRepository 对应。
 */
package com.binance.monitor.data.local.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(
        tableName = "kline_history",
        primaryKeys = {"seriesKey", "openTime"}
)
public class KlineHistoryEntity {
    @NonNull
    public String seriesKey = "";
    @NonNull
    public String symbol = "";
    @NonNull
    public String intervalKey = "";
    @NonNull
    public String apiInterval = "";
    public boolean yearAggregate;
    public long openTime;
    public long closeTime;
    public double open;
    public double high;
    public double low;
    public double close;
    public double volume;
    public double quoteVolume;
}

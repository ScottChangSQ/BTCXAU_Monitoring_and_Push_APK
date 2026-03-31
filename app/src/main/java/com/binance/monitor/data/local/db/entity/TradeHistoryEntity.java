/*
 * 历史交易实体，按稳定交易键长期保存历史成交。
 * 与账户页和交易历史恢复逻辑对应。
 */
package com.binance.monitor.data.local.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "trade_history")
public class TradeHistoryEntity {
    @PrimaryKey
    @NonNull
    public String tradeKey = "";
    public long timestamp;
    @NonNull
    public String productName = "";
    @NonNull
    public String code = "";
    @NonNull
    public String side = "";
    public double price;
    public double quantity;
    public double amount;
    public double fee;
    @NonNull
    public String remark = "";
    public double profit;
    public long openTime;
    public long closeTime;
    public double storageFee;
    public double openPrice;
    public double closePrice;
    public long dealTicket;
    public long orderId;
    public long positionId;
    public int entryType;
}

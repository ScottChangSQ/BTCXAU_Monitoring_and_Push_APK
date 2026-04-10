/*
 * 当前持仓快照实体，每次账户刷新时整批替换。
 * 悬浮窗和账户页持仓区都读取这张表。
 */
package com.binance.monitor.data.local.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "position_snapshot")
public class PositionSnapshotEntity {
    @PrimaryKey
    @NonNull
    public String snapshotKey = "";
    @NonNull
    public String productName = "";
    @NonNull
    public String code = "";
    @NonNull
    public String side = "";
    public long positionTicket;
    public long orderId;
    public long openTime;
    public double quantity;
    public double sellableQuantity;
    public double costPrice;
    public double latestPrice;
    public double marketValue;
    public double positionRatio;
    public double dayPnL;
    public double totalPnL;
    public double returnRate;
    public double pendingLots;
    public int pendingCount;
    public double pendingPrice;
    public double takeProfit;
    public double stopLoss;
    public double storageFee;
}

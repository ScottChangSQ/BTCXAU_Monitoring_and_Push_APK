/*
 * 当前挂单快照实体，每次账户刷新时整批替换。
 * 仅用于恢复和展示当前挂单。
 */
package com.binance.monitor.data.local.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_order_snapshot")
public class PendingOrderSnapshotEntity {
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

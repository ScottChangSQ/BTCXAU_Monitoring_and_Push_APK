package com.binance.monitor.ui.account.model;

public class TradeRecordItem {
    private final long timestamp;
    private final String productName;
    private final String code;
    private final String side;
    private final double price;
    private final double quantity;
    private final double amount;
    private final double fee;
    private final String remark;
    private final double profit;
    private final long openTime;
    private final long closeTime;
    private final double storageFee;
    private final double openPrice;
    private final double closePrice;
    private final long dealTicket;
    private final long orderId;
    private final long positionId;
    private final int entryType;

    public TradeRecordItem(long timestamp,
                           String productName,
                           String code,
                           String side,
                           double price,
                           double quantity,
                           double amount,
                           double fee,
                           String remark) {
        this(timestamp, productName, code, side, price, quantity, amount, fee, remark,
                0d, timestamp, timestamp, fee, price, price, 0L, 0L, 0L, 0);
    }

    public TradeRecordItem(long timestamp,
                           String productName,
                           String code,
                           String side,
                           double price,
                           double quantity,
                           double amount,
                           double fee,
                           String remark,
                           double profit,
                           long openTime,
                           long closeTime,
                           double storageFee) {
        this(timestamp, productName, code, side, price, quantity, amount, fee, remark,
                profit, openTime, closeTime, storageFee, price, price, 0L, 0L, 0L, 0);
    }

    public TradeRecordItem(long timestamp,
                           String productName,
                           String code,
                           String side,
                           double price,
                           double quantity,
                           double amount,
                           double fee,
                           String remark,
                           double profit,
                           long openTime,
                           long closeTime,
                           double storageFee,
                           double openPrice,
                           double closePrice) {
        this(timestamp, productName, code, side, price, quantity, amount, fee, remark,
                profit, openTime, closeTime, storageFee, openPrice, closePrice, 0L, 0L, 0L, 0);
    }

    public TradeRecordItem(long timestamp,
                           String productName,
                           String code,
                           String side,
                           double price,
                           double quantity,
                           double amount,
                           double fee,
                           String remark,
                           double profit,
                           long openTime,
                           long closeTime,
                           double storageFee,
                           double openPrice,
                           double closePrice,
                           long dealTicket,
                           long orderId,
                           long positionId,
                           int entryType) {
        this.timestamp = timestamp;
        this.productName = productName;
        this.code = code;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.amount = amount;
        this.fee = fee;
        this.remark = remark;
        this.profit = profit;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.storageFee = storageFee;
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.dealTicket = dealTicket;
        this.orderId = orderId;
        this.positionId = positionId;
        this.entryType = entryType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getProductName() {
        return productName;
    }

    public String getCode() {
        return code;
    }

    public String getSide() {
        return side;
    }

    public double getPrice() {
        return price;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getAmount() {
        return amount;
    }

    public double getFee() {
        return fee;
    }

    public String getRemark() {
        return remark;
    }

    public double getProfit() {
        return profit;
    }

    public long getOpenTime() {
        return openTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public double getStorageFee() {
        return storageFee;
    }

    public double getOpenPrice() {
        return openPrice;
    }

    public double getClosePrice() {
        return closePrice;
    }

    public long getDealTicket() {
        return dealTicket;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getPositionId() {
        return positionId;
    }

    public int getEntryType() {
        return entryType;
    }
}

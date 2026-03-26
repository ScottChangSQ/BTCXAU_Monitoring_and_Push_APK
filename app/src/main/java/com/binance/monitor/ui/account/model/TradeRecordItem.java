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

    public TradeRecordItem(long timestamp,
                           String productName,
                           String code,
                           String side,
                           double price,
                           double quantity,
                           double amount,
                           double fee,
                           String remark) {
        this.timestamp = timestamp;
        this.productName = productName;
        this.code = code;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.amount = amount;
        this.fee = fee;
        this.remark = remark;
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
}

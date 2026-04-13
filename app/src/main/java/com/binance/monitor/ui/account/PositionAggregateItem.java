/*
 * 持仓产品聚合模型，负责承接账户持仓页“按产品统计”的只读展示字段。
 */
package com.binance.monitor.ui.account;

public class PositionAggregateItem {
    private final String productName;
    private final String side;
    private final double quantity;
    private final double averageCostPrice;
    private final double totalPnl;

    public PositionAggregateItem(String productName,
                                 String side,
                                 double quantity,
                                 double averageCostPrice,
                                 double totalPnl) {
        this.productName = productName == null ? "" : productName;
        this.side = side == null ? "" : side;
        this.quantity = quantity;
        this.averageCostPrice = averageCostPrice;
        this.totalPnl = totalPnl;
    }

    public String getProductName() {
        return productName;
    }

    public String getSide() {
        return side;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getAverageCostPrice() {
        return averageCostPrice;
    }

    public double getTotalPnl() {
        return totalPnl;
    }
}

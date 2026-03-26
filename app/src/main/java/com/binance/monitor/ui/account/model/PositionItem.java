package com.binance.monitor.ui.account.model;

public class PositionItem {
    private final String productName;
    private final String code;
    private final String side;
    private final double quantity;
    private final double sellableQuantity;
    private final double costPrice;
    private final double latestPrice;
    private final double marketValue;
    private final double positionRatio;
    private final double dayPnL;
    private final double totalPnL;
    private final double returnRate;
    private final double pendingLots;
    private final int pendingCount;

    public PositionItem(String productName,
                        String code,
                        double quantity,
                        double sellableQuantity,
                        double costPrice,
                        double latestPrice,
                        double marketValue,
                        double positionRatio,
                        double dayPnL,
                        double totalPnL,
                        double returnRate) {
        this(productName, code, "Buy", quantity, sellableQuantity, costPrice, latestPrice,
                marketValue, positionRatio, dayPnL, totalPnL, returnRate, 0d, 0);
    }

    public PositionItem(String productName,
                        String code,
                        String side,
                        double quantity,
                        double sellableQuantity,
                        double costPrice,
                        double latestPrice,
                        double marketValue,
                        double positionRatio,
                        double dayPnL,
                        double totalPnL,
                        double returnRate,
                        double pendingLots,
                        int pendingCount) {
        this.productName = productName;
        this.code = code;
        this.side = side;
        this.quantity = quantity;
        this.sellableQuantity = sellableQuantity;
        this.costPrice = costPrice;
        this.latestPrice = latestPrice;
        this.marketValue = marketValue;
        this.positionRatio = positionRatio;
        this.dayPnL = dayPnL;
        this.totalPnL = totalPnL;
        this.returnRate = returnRate;
        this.pendingLots = pendingLots;
        this.pendingCount = pendingCount;
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

    public double getQuantity() {
        return quantity;
    }

    public double getSellableQuantity() {
        return sellableQuantity;
    }

    public double getCostPrice() {
        return costPrice;
    }

    public double getLatestPrice() {
        return latestPrice;
    }

    public double getMarketValue() {
        return marketValue;
    }

    public double getPositionRatio() {
        return positionRatio;
    }

    public double getDayPnL() {
        return dayPnL;
    }

    public double getTotalPnL() {
        return totalPnL;
    }

    public double getReturnRate() {
        return returnRate;
    }

    public double getPendingLots() {
        return pendingLots;
    }

    public int getPendingCount() {
        return pendingCount;
    }
}

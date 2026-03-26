package com.binance.monitor.ui.account.model;

public class CurvePoint {
    private final long timestamp;
    private final double equity;
    private final double balance;

    public CurvePoint(long timestamp, double equity, double balance) {
        this.timestamp = timestamp;
        this.equity = equity;
        this.balance = balance;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getEquity() {
        return equity;
    }

    public double getBalance() {
        return balance;
    }
}

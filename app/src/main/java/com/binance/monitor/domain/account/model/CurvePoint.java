/*
 * 账户曲线点模型，同时承载净值、结余和历史仓位比例。
 * 账户统计页、图表分析辅助和本地缓存都会复用这个对象。
 */
package com.binance.monitor.domain.account.model;

public class CurvePoint {
    private final long timestamp;
    private final double equity;
    private final double balance;
    private final double positionRatio;

    public CurvePoint(long timestamp, double equity, double balance) {
        this(timestamp, equity, balance, 0d);
    }

    public CurvePoint(long timestamp, double equity, double balance, double positionRatio) {
        this.timestamp = timestamp;
        this.equity = equity;
        this.balance = balance;
        this.positionRatio = positionRatio;
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

    public double getPositionRatio() {
        return positionRatio;
    }
}

/*
 * 悬浮窗产品卡片数据，统一承载单个产品的盈亏、价格、成交量和成交额。
 * 由 MonitorService 在同一次快照刷新中组装，FloatingWindowManager 只负责展示。
 */
package com.binance.monitor.ui.floating;

public class FloatingSymbolCardData {

    private final String code;
    private final String label;
    private final double totalPnl;
    private final double latestPrice;
    private final boolean hasLatestPrice;
    private final double volume;
    private final double amount;
    private final long updatedAt;

    public FloatingSymbolCardData(String code,
                                  String label,
                                  double totalPnl,
                                  double latestPrice,
                                  boolean hasLatestPrice,
                                  double volume,
                                  double amount,
                                  long updatedAt) {
        this.code = code == null ? "" : code;
        this.label = label == null ? "" : label;
        this.totalPnl = totalPnl;
        this.latestPrice = latestPrice;
        this.hasLatestPrice = hasLatestPrice;
        this.volume = volume;
        this.amount = amount;
        this.updatedAt = updatedAt;
    }

    // 返回产品代码。
    public String getCode() {
        return code;
    }

    // 返回悬浮窗展示名称。
    public String getLabel() {
        return label;
    }

    // 返回该产品当前合计盈亏。
    public double getTotalPnl() {
        return totalPnl;
    }

    // 返回当前最新价格。
    public double getLatestPrice() {
        return latestPrice;
    }

    // 返回是否存在可用价格。
    public boolean hasLatestPrice() {
        return hasLatestPrice;
    }

    // 返回成交量。
    public double getVolume() {
        return volume;
    }

    // 返回成交额。
    public double getAmount() {
        return amount;
    }

    // 返回本卡片对应的行情时间。
    public long getUpdatedAt() {
        return updatedAt;
    }
}

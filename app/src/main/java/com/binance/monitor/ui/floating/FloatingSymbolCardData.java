/*
 * 悬浮窗产品卡片数据，统一承载单个产品的盈亏、价格、成交量和成交额。
 * 由 MonitorService 在同一次快照刷新中组装，FloatingWindowManager 只负责展示。
 */
package com.binance.monitor.ui.floating;

public class FloatingSymbolCardData {

    private final String code;
    private final String label;
    private final double totalPnl;
    private final double totalLots;
    private final double latestPrice;
    private final boolean hasLatestPrice;
    private final double volume;
    private final double amount;
    private final long updatedAt;
    private final boolean hasPosition;

    public FloatingSymbolCardData(String code,
                                  String label,
                                  double totalPnl,
                                  double totalLots,
                                  double latestPrice,
                                  boolean hasLatestPrice,
                                  double volume,
                                  double amount,
                                  long updatedAt,
                                  boolean hasPosition) {
        this.code = code == null ? "" : code;
        this.label = label == null ? "" : label;
        this.totalPnl = totalPnl;
        this.totalLots = totalLots;
        this.latestPrice = latestPrice;
        this.hasLatestPrice = hasLatestPrice;
        this.volume = volume;
        this.amount = amount;
        this.updatedAt = updatedAt;
        this.hasPosition = hasPosition;
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

    // 返回带方向的展示总手数；大小按绝对手数汇总，正负按净方向确定。
    public double getTotalLots() {
        return totalLots;
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

    public boolean hasPosition() {
        return hasPosition;
    }

    // 兼容更明确的命名，供悬浮窗状态判断和测试复用。
    public boolean hasActivePosition() {
        return hasPosition;
    }

    // 返回用于判定悬浮窗卡片是否需要重绘的稳定签名。
    public String buildVisualSignature() {
        return code
                + "|"
                + label
                + "|"
                + Double.doubleToLongBits(totalPnl)
                + "|"
                + Double.doubleToLongBits(totalLots)
                + "|"
                + Double.doubleToLongBits(latestPrice)
                + "|"
                + hasLatestPrice
                + "|"
                + Double.doubleToLongBits(volume)
                + "|"
                + Double.doubleToLongBits(amount)
                + "|"
                + hasPosition;
    }
}

/*
 * 悬浮窗分产品盈亏项，表示某个产品当前聚合后的总盈亏。
 * 由 FloatingPositionAggregator 负责生成。
 */
package com.binance.monitor.ui.floating;

public class FloatingPositionPnlItem {
    private final String code;
    private final String label;
    private final double totalPnl;
    private final double marketPrice;
    private final boolean hasMarketPrice;

    public FloatingPositionPnlItem(String code, String label, double totalPnl) {
        this(code, label, totalPnl, 0d, false);
    }

    public FloatingPositionPnlItem(String code,
                                   String label,
                                   double totalPnl,
                                   double marketPrice,
                                   boolean hasMarketPrice) {
        this.code = code == null ? "" : code;
        this.label = label == null ? "" : label;
        this.totalPnl = totalPnl;
        this.marketPrice = marketPrice;
        this.hasMarketPrice = hasMarketPrice;
    }

    // 返回产品代码。
    public String getCode() {
        return code;
    }

    // 返回展示标签。
    public String getLabel() {
        return label;
    }

    // 返回该产品聚合后的总盈亏。
    public double getTotalPnl() {
        return totalPnl;
    }

    // 返回是否存在可展示的实时行情价格。
    public boolean hasMarketPrice() {
        return hasMarketPrice;
    }

    // 返回当前产品对应的实时行情价格。
    public double getMarketPrice() {
        return marketPrice;
    }
}

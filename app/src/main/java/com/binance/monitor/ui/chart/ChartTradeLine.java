/*
 * 图表交易线模型，承载单条活动交易线的价格、标签和状态。
 * 与 ChartTradeLayerSnapshot、KlineChartView 协同工作。
 */
package com.binance.monitor.ui.chart;

final class ChartTradeLine {
    private final String id;
    private final double price;
    private final String label;
    private final ChartTradeLineState state;

    ChartTradeLine(String id, double price, String label, ChartTradeLineState state) {
        this.id = id == null ? "" : id;
        this.price = price;
        this.label = label == null ? "" : label;
        this.state = state == null ? ChartTradeLineState.LIVE_PENDING : state;
    }

    String getId() {
        return id;
    }

    double getPrice() {
        return price;
    }

    String getLabel() {
        return label;
    }

    ChartTradeLineState getState() {
        return state;
    }
}

/*
 * 图表交易线角色枚举，明确一条线是主线、止盈线还是止损线。
 * 与 ChartTradeLine、KlineChartView、MarketChartScreen 协同工作。
 */
package com.binance.monitor.ui.chart;

enum ChartTradeLineRole {
    ENTRY,
    TP,
    SL
}

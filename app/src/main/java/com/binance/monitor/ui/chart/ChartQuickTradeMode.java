/*
 * 图表页快捷交易模式枚举，统一描述关闭、市价和挂单三种主状态。
 * 与 MarketChartScreen 和 ChartQuickTradeCoordinator 协同工作。
 */
package com.binance.monitor.ui.chart;

enum ChartQuickTradeMode {
    CLOSED,
    MARKET,
    PENDING
}

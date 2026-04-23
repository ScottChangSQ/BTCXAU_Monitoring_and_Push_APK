/*
 * 图表交易线颜色语义，统一描述交易线应按盈利、亏损、主色还是中性色渲染。
 * 与 ChartTradeLine、KlineChartView 协同工作。
 */
package com.binance.monitor.ui.chart;

enum ChartTradeLineTone {
    POSITIVE,
    NEGATIVE,
    PRIMARY,
    NEUTRAL
}

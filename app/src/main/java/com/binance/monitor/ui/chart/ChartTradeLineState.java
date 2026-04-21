/*
 * 图表交易线状态枚举，统一描述真实线、草稿线和交互中的临时态。
 * 与 ChartTradeLine、ChartTradeLayerSnapshot、KlineChartView 协同工作。
 */
package com.binance.monitor.ui.chart;

enum ChartTradeLineState {
    LIVE_POSITION,
    LIVE_PENDING,
    LIVE_TP,
    LIVE_SL,
    DRAFT_PENDING,
    SELECTED,
    DRAGGING,
    SUBMITTING,
    REJECTED_ROLLBACK
}

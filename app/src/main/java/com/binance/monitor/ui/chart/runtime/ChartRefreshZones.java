/*
 * 图表页刷新分区定义，后续用于约束不同事件只刷新必要区域。
 */
package com.binance.monitor.ui.chart.runtime;

public enum ChartRefreshZones {
    STATIC_ZONE,
    UI_STATE_ZONE,
    SUMMARY_ZONE,
    CHART_RENDER_ZONE
}

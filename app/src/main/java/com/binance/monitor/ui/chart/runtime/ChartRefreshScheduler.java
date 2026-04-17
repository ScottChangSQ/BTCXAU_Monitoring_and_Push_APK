/*
 * 图表高频刷新调度器，用于把同一帧内重复刷新请求合并成最后一次有效请求。
 */
package com.binance.monitor.ui.chart.runtime;

public final class ChartRefreshScheduler {
    private String pendingRenderToken;
    private String pendingOverlayRenderToken;
    private String pendingSummaryBindToken;
    private String pendingDialogBindToken;

    public void requestChartRender(String renderToken) {
        pendingRenderToken = renderToken;
    }

    public void requestOverlayRender(String renderToken) {
        pendingOverlayRenderToken = renderToken;
    }

    public void requestSummaryBind(String renderToken) {
        pendingSummaryBindToken = renderToken;
    }

    public void requestDialogBind(String renderToken) {
        pendingDialogBindToken = renderToken;
    }

    public String drainPendingRenderToken() {
        String token = pendingRenderToken;
        pendingRenderToken = null;
        return token;
    }

    public String drainPendingOverlayRenderToken() {
        String token = pendingOverlayRenderToken;
        pendingOverlayRenderToken = null;
        return token;
    }

    public String drainPendingSummaryBindToken() {
        String token = pendingSummaryBindToken;
        pendingSummaryBindToken = null;
        return token;
    }

    public String drainPendingDialogBindToken() {
        String token = pendingDialogBindToken;
        pendingDialogBindToken = null;
        return token;
    }
}

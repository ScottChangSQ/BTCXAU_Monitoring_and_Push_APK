/*
 * 行情图右上角倒计时文案格式化工具，统一处理剩余秒数、周期秒数和延迟显示。
 */
package com.binance.monitor.ui.chart;

public final class ChartRefreshMetaFormatter {

    private ChartRefreshMetaFormatter() {
    }

    // 生成“剩余秒数/周期秒数 延迟ms”文案，无有效结果时显示占位符。
    public static String buildCountdownText(long nextAutoRefreshAtMs,
                                            long nowMs,
                                            long periodMs,
                                            long latencyMs) {
        int periodSeconds = (int) Math.max(1L, periodMs / 1_000L);
        String latencyText = latencyMs >= 0L ? (latencyMs + "ms") : "--ms";
        if (nextAutoRefreshAtMs <= 0L) {
            return "--秒/" + periodSeconds + "秒 " + latencyText;
        }
        long remainingMs = Math.max(0L, nextAutoRefreshAtMs - nowMs);
        int remainSeconds = (int) Math.ceil(remainingMs / 1000d);
        return remainSeconds + "秒/" + periodSeconds + "秒 " + latencyText;
    }
}

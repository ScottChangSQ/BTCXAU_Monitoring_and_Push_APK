/*
 * 图表缓存键辅助，负责统一生成 K 线历史分组 key。
 * MonitorService 与 MarketChartActivity 通过这里共享同一套 1m 底稿缓存命名规则。
 */
package com.binance.monitor.ui.chart;

public final class MarketChartCacheKeyHelper {

    private MarketChartCacheKeyHelper() {
    }

    // 用 symbol + 展示周期 + 请求周期 + 年聚合标记生成稳定缓存键。
    public static String build(String symbol,
                               String intervalKey,
                               String apiInterval,
                               boolean yearAggregate) {
        String safeSymbol = symbol == null ? "" : symbol;
        String safeIntervalKey = intervalKey == null ? "default" : intervalKey;
        String safeApiInterval = apiInterval == null ? safeIntervalKey : apiInterval;
        return safeSymbol + "|" + safeIntervalKey + "|" + safeApiInterval + "|" + yearAggregate;
    }
}

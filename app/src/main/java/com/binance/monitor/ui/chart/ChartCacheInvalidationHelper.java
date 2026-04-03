/*
 * 图表缓存版本辅助，用于在图表数据口径发生变化时一次性清掉旧缓存。
 * MarketChartActivity 通过这里判断是否需要触发本地 K 线历史迁移。
 */
package com.binance.monitor.ui.chart;

final class ChartCacheInvalidationHelper {

    private ChartCacheInvalidationHelper() {
    }

    // 只要本地记录版本落后于当前图表缓存版本，就触发一次性清理。
    static boolean shouldInvalidate(int storedVersion, int currentVersion) {
        return currentVersion > 0 && storedVersion < currentVersion;
    }
}

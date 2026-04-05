/*
 * 图表缺口补拉辅助，负责判断当前新窗口是否丢失了上一轮已经展示过的更早历史。
 * 与 MarketChartActivity 配合，避免左侧缺段时一直停留在不完整窗口。
 */
package com.binance.monitor.ui.chart;

final class ChartGapFillHelper {

    private ChartGapFillHelper() {
    }

    // 只要上一轮最老一根明显早于当前窗口最老一根，就继续向左补历史。
    static boolean shouldBackfillOlderHistory(long previousOldestOpenTime,
                                              long latestWindowOldestOpenTime,
                                              long intervalMs) {
        if (previousOldestOpenTime <= 0L || latestWindowOldestOpenTime <= 0L || intervalMs <= 0L) {
            return false;
        }
        return latestWindowOldestOpenTime - previousOldestOpenTime > intervalMs * 2L;
    }
}

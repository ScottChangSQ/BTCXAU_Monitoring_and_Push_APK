/*
 * 图表缺口补拉辅助，负责判断当前新窗口是否丢失了上一轮已经展示过的更早历史。
 * 与 MarketChartActivity 配合，避免左侧缺段时一直停留在不完整窗口。
 */
package com.binance.monitor.ui.chart;

final class ChartGapFillHelper {

    private ChartGapFillHelper() {
    }

    // 只有当用户已加载过超出标准窗口的历史，且本轮最早一根右移时，才继续向左补历史。
    static boolean shouldBackfillOlderHistory(int previousWindowSize,
                                              int defaultWindowLimit,
                                              long previousOldestOpenTime,
                                              long latestWindowOldestOpenTime) {
        if (previousWindowSize <= Math.max(1, defaultWindowLimit)) {
            return false;
        }
        if (previousOldestOpenTime <= 0L || latestWindowOldestOpenTime <= 0L) {
            return false;
        }
        return latestWindowOldestOpenTime > previousOldestOpenTime;
    }
}

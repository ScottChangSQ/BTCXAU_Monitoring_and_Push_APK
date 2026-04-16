/*
 * 图表缺口补拉辅助，负责判断当前新窗口是否丢失了上一轮已经展示过的更早历史。
 * 与 MarketChartActivity 配合，避免左侧缺段时一直停留在不完整窗口。
 */
package com.binance.monitor.ui.chart;

final class ChartGapFillHelper {

    private ChartGapFillHelper() {
    }

    // 自动向左补历史只适用于“之前已经翻过更老数据，但这次刷新把左边挤掉了”的场景。
    // 窗口内部缺口应走主刷新修复，继续向左翻页既修不好中间缺口，也会把正常停盘误当成缺页。
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

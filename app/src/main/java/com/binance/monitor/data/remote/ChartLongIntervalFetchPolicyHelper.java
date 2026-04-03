/*
 * 图表长周期拉取策略辅助，负责判断周/月线是否应优先直连官方周期接口，以及何时回退日线聚合。
 * 与 BinanceApiClient 协同工作。
 */
package com.binance.monitor.data.remote;

final class ChartLongIntervalFetchPolicyHelper {

    private ChartLongIntervalFetchPolicyHelper() {
    }

    // 周线和月线优先直连官方周期接口，避免客户端日线聚合异常时把长周期显示错。
    static boolean shouldUseDirectRestFirst(String intervalKey) {
        return "1w".equalsIgnoreCase(safe(intervalKey)) || "1M".equalsIgnoreCase(safe(intervalKey));
    }

    // 当周/月线只返回极少根数时，认为结果不可信，回退到日线聚合兜底。
    static boolean shouldFallbackToDailyAggregation(String intervalKey,
                                                    int requestedLimit,
                                                    int directCount) {
        if (!shouldUseDirectRestFirst(intervalKey)) {
            return false;
        }
        return Math.max(1, requestedLimit) > 1 && directCount <= 1;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

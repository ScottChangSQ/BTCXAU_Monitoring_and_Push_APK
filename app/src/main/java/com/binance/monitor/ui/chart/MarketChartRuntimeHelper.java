/*
 * 行情图运行态辅助工具，负责校验可恢复的周期键，避免恢复逻辑散落在 Activity 中。
 */
package com.binance.monitor.ui.chart;

final class MarketChartRuntimeHelper {

    private MarketChartRuntimeHelper() {
    }

    // 只恢复当前仍受支持的周期键，未知值统一回退到默认周期。
    static String resolveStoredIntervalKey(String storedKey, String fallbackKey, String[] supportedKeys) {
        String safeFallbackKey = fallbackKey == null || fallbackKey.trim().isEmpty()
                ? ""
                : fallbackKey.trim();
        if (storedKey == null || storedKey.trim().isEmpty() || supportedKeys == null || supportedKeys.length == 0) {
            return safeFallbackKey;
        }
        String candidate = storedKey.trim();
        for (String supportedKey : supportedKeys) {
            if (supportedKey != null && candidate.equals(supportedKey.trim())) {
                return supportedKey.trim();
            }
        }
        return safeFallbackKey;
    }
}

/*
 * 设置页缓存分类器，把用户选择映射为具体的缓存清理范围。
 * 用于保证历史行情、历史交易和运行时缓存可以分别清理。
 */
package com.binance.monitor.ui.settings;

public class CacheSectionClassifier {

    // 根据三个布尔开关生成最终的缓存清理范围。
    public static CacheSelection fromSelection(boolean clearHistoryMarket,
                                              boolean clearHistoryTrade,
                                              boolean clearRuntime) {
        return new CacheSelection(clearHistoryMarket, clearHistoryTrade, clearRuntime);
    }

    public static class CacheSelection {
        private final boolean clearHistoryMarket;
        private final boolean clearHistoryTrade;
        private final boolean clearRuntime;

        public CacheSelection(boolean clearHistoryMarket,
                              boolean clearHistoryTrade,
                              boolean clearRuntime) {
            this.clearHistoryMarket = clearHistoryMarket;
            this.clearHistoryTrade = clearHistoryTrade;
            this.clearRuntime = clearRuntime;
        }

        // 是否清除历史行情数据。
        public boolean shouldClearHistoryMarket() {
            return clearHistoryMarket;
        }

        // 是否清除历史交易数据。
        public boolean shouldClearHistoryTrade() {
            return clearHistoryTrade;
        }

        // 是否清除运行时缓存。
        public boolean shouldClearRuntime() {
            return clearRuntime;
        }
    }
}

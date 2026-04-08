/*
 * 账户历史刷新判定辅助，负责决定轻量快照轮询后是否需要补拉全量历史。
 * 供图表页前台和服务端账户 delta 补拉共用，避免每个入口都重复拉 all 历史。
 */
package com.binance.monitor.ui.account;

public final class AccountHistoryRefreshPolicyHelper {

    private AccountHistoryRefreshPolicyHelper() {
    }

    // 仅当远端历史成交数量变化，或本地还没有历史但远端已经存在成交时，才补拉全量历史。
    public static boolean shouldRefreshAllHistory(int remoteTradeCount,
                                                  int cachedTradeCount,
                                                  boolean hasStoredTradeHistory) {
        if (remoteTradeCount < 0) {
            return !hasStoredTradeHistory || cachedTradeCount < 0;
        }
        if (cachedTradeCount < 0) {
            return remoteTradeCount > 0;
        }
        return remoteTradeCount != cachedTradeCount;
    }
}

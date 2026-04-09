/*
 * 账户历史刷新判定辅助，负责决定轻量快照轮询后是否需要补拉全量历史。
 * 供图表页前台和服务端账户 delta 补拉共用，避免每个入口都重复拉 all 历史。
 */
package com.binance.monitor.ui.account;

public final class AccountHistoryRefreshPolicyHelper {

    private AccountHistoryRefreshPolicyHelper() {
    }

    // 仅当服务端历史修订号变化时，才补拉全量历史，避免按数量猜测导致的误刷新。
    public static boolean shouldRefreshAllHistory(String remoteHistoryRevision,
                                                  String cachedHistoryRevision,
                                                  boolean hasStoredTradeHistory) {
        String remote = remoteHistoryRevision == null ? "" : remoteHistoryRevision.trim();
        String cached = cachedHistoryRevision == null ? "" : cachedHistoryRevision.trim();
        if (!hasStoredTradeHistory) {
            return true;
        }
        if (remote.isEmpty()) {
            return cached.isEmpty();
        }
        if (cached.isEmpty()) {
            return true;
        }
        return !remote.equals(cached);
    }
}

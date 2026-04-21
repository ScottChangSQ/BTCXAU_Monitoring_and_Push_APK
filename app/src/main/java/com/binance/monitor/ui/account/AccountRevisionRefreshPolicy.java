/*
 * 账户页 revision 刷新策略，负责把“是否需要继续走远端快照”收口成统一 gate。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.runtime.state.model.AccountRuntimeSnapshot;

public final class AccountRevisionRefreshPolicy {

    private AccountRevisionRefreshPolicy() {
    }

    // 只有“页面落后于当前运行态”或“当前结果已经过期”时，才允许定时器触发远端快照。
    public static boolean shouldRequestSnapshot(@Nullable AccountRuntimeSnapshot runtimeSnapshot,
                                                @Nullable String appliedHistoryRevision,
                                                long appliedUpdatedAt,
                                                long nowMs,
                                                long staleAfterMs) {
        if (runtimeSnapshot == null) {
            return true;
        }
        String runtimeHistoryRevision = trimToEmpty(runtimeSnapshot.getHistoryRevision());
        String appliedRevision = trimToEmpty(appliedHistoryRevision);
        if (!runtimeHistoryRevision.isEmpty() && !runtimeHistoryRevision.equals(appliedRevision)) {
            return true;
        }
        long safeAppliedUpdatedAt = Math.max(appliedUpdatedAt, runtimeSnapshot.getUpdatedAt());
        if (safeAppliedUpdatedAt <= 0L) {
            return true;
        }
        long safeStaleAfterMs = Math.max(1_000L, staleAfterMs);
        return nowMs - safeAppliedUpdatedAt >= safeStaleAfterMs;
    }

    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

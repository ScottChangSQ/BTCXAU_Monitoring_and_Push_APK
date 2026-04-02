/*
 * 账户预加载策略辅助，负责根据前后台状态和快照模式统一计算刷新节奏。
 * 供 AccountStatsPreloadManager 复用，避免页面层和预加载层各自写死时间。
 */
package com.binance.monitor.ui.account;

import com.binance.monitor.constants.AppConstants;

public final class AccountPreloadPolicyHelper {
    private static final long FOREGROUND_FULL_SNAPSHOT_DELAY_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS;
    private static final long FOREGROUND_LIVE_SNAPSHOT_DELAY_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS * 2L;

    private AccountPreloadPolicyHelper() {
    }

    // 根据当前是否在前台和是否需要全量快照，返回下一次预加载的等待时间。
    public static long resolveRefreshDelayMs(boolean foreground, boolean fullSnapshotActive) {
        if (!foreground) {
            return AppConstants.ACCOUNT_REFRESH_MAX_INTERVAL_MS;
        }
        return fullSnapshotActive
                ? FOREGROUND_FULL_SNAPSHOT_DELAY_MS
                : FOREGROUND_LIVE_SNAPSHOT_DELAY_MS;
    }
}

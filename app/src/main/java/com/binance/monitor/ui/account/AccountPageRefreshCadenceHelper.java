/*
 * 账户页刷新节奏辅助，统一收口分析页显式快照循环的最小频率与退避策略。
 * 目标是在不影响交易确认主链的前提下，减少前台页面的重复全量快照。
 */
package com.binance.monitor.ui.account;

import com.binance.monitor.constants.AppConstants;

final class AccountPageRefreshCadenceHelper {
    private static final long CONNECTED_BASE_DELAY_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS * 2L;
    private static final long UNCHANGED_STEP_DELAY_MS = 4_000L;

    private AccountPageRefreshCadenceHelper() {
    }

    static long resolveDelayMs(boolean connected, int unchangedRefreshStreak) {
        if (!connected) {
            return Math.min(AppConstants.ACCOUNT_REFRESH_MAX_INTERVAL_MS, CONNECTED_BASE_DELAY_MS);
        }
        int safeStreak = Math.max(0, unchangedRefreshStreak);
        return Math.min(
                AppConstants.ACCOUNT_REFRESH_MAX_INTERVAL_MS,
                CONNECTED_BASE_DELAY_MS + safeStreak * UNCHANGED_STEP_DELAY_MS
        );
    }
}

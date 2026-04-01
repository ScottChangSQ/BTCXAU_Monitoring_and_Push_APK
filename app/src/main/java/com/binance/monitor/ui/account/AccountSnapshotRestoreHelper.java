/*
 * 账户快照恢复辅助类，用于把预加载快照缺失但本地已留存的数据先补回页面。
 * 当前主要用于首屏回填交易记录与本地留存快照，避免页面先显示空白。
 */
package com.binance.monitor.ui.account;

import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.ui.account.model.AccountSnapshot;

import java.util.ArrayList;

final class AccountSnapshotRestoreHelper {

    private AccountSnapshotRestoreHelper() {
    }

    // 当预加载快照未携带历史交易时，优先回填本地已留存交易，避免列表先空白再重新加载。
    static AccountSnapshot mergeMissingTrades(AccountSnapshot preloadSnapshot,
                                             AccountStorageRepository.StoredSnapshot storedSnapshot) {
        if (preloadSnapshot == null) {
            return null;
        }
        boolean shouldUseStoredTrades = (preloadSnapshot.getTrades() == null || preloadSnapshot.getTrades().isEmpty())
                && storedSnapshot != null
                && storedSnapshot.getTrades() != null
                && !storedSnapshot.getTrades().isEmpty();
        if (!shouldUseStoredTrades) {
            return preloadSnapshot;
        }
        return new AccountSnapshot(
                preloadSnapshot.getOverviewMetrics() == null ? new ArrayList<>() : preloadSnapshot.getOverviewMetrics(),
                preloadSnapshot.getCurvePoints() == null ? new ArrayList<>() : preloadSnapshot.getCurvePoints(),
                preloadSnapshot.getCurveIndicators() == null ? new ArrayList<>() : preloadSnapshot.getCurveIndicators(),
                preloadSnapshot.getPositions() == null ? new ArrayList<>() : preloadSnapshot.getPositions(),
                preloadSnapshot.getPendingOrders() == null ? new ArrayList<>() : preloadSnapshot.getPendingOrders(),
                storedSnapshot.getTrades(),
                preloadSnapshot.getStatsMetrics() == null ? new ArrayList<>() : preloadSnapshot.getStatsMetrics()
        );
    }

    // 把本地持久化快照转换成页面可直接使用的账户快照。
    static AccountSnapshot restoreStoredSnapshot(AccountStorageRepository.StoredSnapshot storedSnapshot) {
        if (!hasStoredSnapshotData(storedSnapshot)) {
            return null;
        }
        return new AccountSnapshot(
                storedSnapshot.getOverviewMetrics(),
                storedSnapshot.getCurvePoints(),
                storedSnapshot.getCurveIndicators(),
                storedSnapshot.getPositions(),
                storedSnapshot.getPendingOrders(),
                storedSnapshot.getTrades(),
                storedSnapshot.getStatsMetrics()
        );
    }

    // 判断本地持久化快照是否带有可展示的数据，避免空快照误触发回填。
    static boolean hasStoredSnapshotData(AccountStorageRepository.StoredSnapshot storedSnapshot) {
        if (storedSnapshot == null) {
            return false;
        }
        return !storedSnapshot.getOverviewMetrics().isEmpty()
                || !storedSnapshot.getCurvePoints().isEmpty()
                || !storedSnapshot.getCurveIndicators().isEmpty()
                || !storedSnapshot.getPositions().isEmpty()
                || !storedSnapshot.getPendingOrders().isEmpty()
                || !storedSnapshot.getTrades().isEmpty()
                || !storedSnapshot.getStatsMetrics().isEmpty();
    }
}

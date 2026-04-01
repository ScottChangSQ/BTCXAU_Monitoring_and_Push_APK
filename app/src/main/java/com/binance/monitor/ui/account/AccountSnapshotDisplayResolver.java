/*
 * 账户快照展示解析器，用于在预加载缓存与本地持久化快照之间选择首屏展示数据。
 * 供账户页、图表页等需要快速回填账户信息的界面复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.ui.account.model.AccountSnapshot;

public final class AccountSnapshotDisplayResolver {
    private static final long MAX_CACHE_AGE_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS * 3L;

    private AccountSnapshotDisplayResolver() {
    }

    // 按优先级选择页面首屏要展示的快照，优先使用新鲜预加载，再回退到本地持久化数据。
    @Nullable
    public static AccountSnapshot resolve(@Nullable AccountStatsPreloadManager.Cache cache,
                                          @Nullable AccountStorageRepository.StoredSnapshot storedSnapshot,
                                          long nowMs) {
        return resolve(cache, storedSnapshot, nowMs, true);
    }

    // 账户会话失效时不允许再用任何旧缓存回填页面，避免登出后仍显示旧数据。
    @Nullable
    public static AccountSnapshot resolve(@Nullable AccountStatsPreloadManager.Cache cache,
                                          @Nullable AccountStorageRepository.StoredSnapshot storedSnapshot,
                                          long nowMs,
                                          boolean sessionActive) {
        if (!sessionActive) {
            return null;
        }
        if (isFreshCache(cache, nowMs)) {
            return AccountSnapshotRestoreHelper.mergeMissingTrades(cache.getSnapshot(), storedSnapshot);
        }
        return AccountSnapshotRestoreHelper.restoreStoredSnapshot(storedSnapshot);
    }

    // 判断预加载缓存是否仍然新鲜，避免把过期缓存误当成首屏实时数据。
    static boolean isFreshCache(@Nullable AccountStatsPreloadManager.Cache cache, long nowMs) {
        if (cache == null || cache.getSnapshot() == null) {
            return false;
        }
        long fetchedAt = cache.getFetchedAt();
        if (fetchedAt <= 0L) {
            return true;
        }
        return nowMs - fetchedAt <= MAX_CACHE_AGE_MS;
    }
}

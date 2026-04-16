/*
 * 账户持仓运行态仓，从总账户缓存裁出当前页真正需要的运行态。
 */
package com.binance.monitor.ui.account.runtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccountRuntimeSnapshotStore {

    @NonNull
    public AccountRuntimePayload build(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null || cache.getSnapshot() == null) {
            return AccountRuntimePayload.empty();
        }
        AccountSnapshot snapshot = cache.getSnapshot();
        return new AccountRuntimePayload(
                copyMetrics(snapshot.getOverviewMetrics()),
                copyPositions(snapshot.getPositions()),
                copyPositions(snapshot.getPendingOrders()),
                Collections.emptyList(),
                Collections.emptyList(),
                safe(cache.getAccount()),
                safe(cache.getServer()),
                cache.isConnected(),
                cache.getUpdatedAt(),
                cache.getFetchedAt()
        );
    }

    @NonNull
    private List<AccountMetric> copyMetrics(@Nullable List<AccountMetric> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(source);
    }

    @NonNull
    private List<PositionItem> copyPositions(@Nullable List<PositionItem> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(source);
    }

    @NonNull
    private String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}

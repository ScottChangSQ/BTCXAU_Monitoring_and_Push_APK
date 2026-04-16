/*
 * 账户统计历史态仓，从总账户缓存裁出历史页真正需要的数据。
 */
package com.binance.monitor.ui.account.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccountHistorySnapshotStore {

    @NonNull
    public AccountHistoryPayload build(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null) {
            return AccountHistoryPayload.empty();
        }
        return build(cache.getSnapshot(), cache.getHistoryRevision());
    }

    @NonNull
    public AccountHistoryPayload build(@Nullable AccountSnapshot snapshot,
                                       @Nullable String historyRevision) {
        if (snapshot == null) {
            return AccountHistoryPayload.empty();
        }
        return new AccountHistoryPayload(
                safe(historyRevision),
                copyTrades(snapshot.getTrades()),
                copyCurves(snapshot.getCurvePoints()),
                copyMetrics(snapshot.getStatsMetrics()),
                copyMetrics(snapshot.getCurveIndicators())
        );
    }

    @NonNull
    private List<TradeRecordItem> copyTrades(@Nullable List<TradeRecordItem> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(source);
    }

    @NonNull
    private List<CurvePoint> copyCurves(@Nullable List<CurvePoint> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(source);
    }

    @NonNull
    private List<AccountMetric> copyMetrics(@Nullable List<AccountMetric> source) {
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

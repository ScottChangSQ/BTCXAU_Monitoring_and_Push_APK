/*
 * 账户统计页历史态载荷，只承载历史成交、净值曲线和统计指标。
 */
package com.binance.monitor.ui.account.history;

import androidx.annotation.NonNull;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import java.util.Collections;
import java.util.List;

public final class AccountHistoryPayload {
    private final String historyRevision;
    private final List<TradeRecordItem> trades;
    private final List<CurvePoint> curvePoints;
    private final List<AccountMetric> statsMetrics;
    private final List<AccountMetric> curveIndicators;

    public AccountHistoryPayload(@NonNull String historyRevision,
                                 @NonNull List<TradeRecordItem> trades,
                                 @NonNull List<CurvePoint> curvePoints,
                                 @NonNull List<AccountMetric> statsMetrics,
                                 @NonNull List<AccountMetric> curveIndicators) {
        this.historyRevision = historyRevision;
        this.trades = trades;
        this.curvePoints = curvePoints;
        this.statsMetrics = statsMetrics;
        this.curveIndicators = curveIndicators;
    }

    public static AccountHistoryPayload empty() {
        return new AccountHistoryPayload("", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    public String getHistoryRevision() {
        return historyRevision;
    }

    public List<TradeRecordItem> getTrades() {
        return trades;
    }

    public List<CurvePoint> getCurvePoints() {
        return curvePoints;
    }

    public List<AccountMetric> getStatsMetrics() {
        return statsMetrics;
    }

    public List<AccountMetric> getCurveIndicators() {
        return curveIndicators;
    }
}

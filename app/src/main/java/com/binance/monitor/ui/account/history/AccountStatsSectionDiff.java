/*
 * 账户统计页分段刷新结果，用于决定哪些区块需要重绘。
 */
package com.binance.monitor.ui.account.history;

import androidx.annotation.Nullable;

public final class AccountStatsSectionDiff {
    public final boolean refreshCurveSection;
    public final boolean refreshReturnSection;
    public final boolean refreshTradeStatsSection;
    public final boolean refreshTradeRecordsSection;

    public AccountStatsSectionDiff(boolean refreshCurveSection,
                                   boolean refreshReturnSection,
                                   boolean refreshTradeStatsSection,
                                   boolean refreshTradeRecordsSection) {
        this.refreshCurveSection = refreshCurveSection;
        this.refreshReturnSection = refreshReturnSection;
        this.refreshTradeStatsSection = refreshTradeStatsSection;
        this.refreshTradeRecordsSection = refreshTradeRecordsSection;
    }

    public static AccountStatsSectionDiff between(@Nullable AccountStatsRenderSignature previous,
                                                  @Nullable AccountStatsRenderSignature current) {
        if (current == null) {
            return new AccountStatsSectionDiff(false, false, false, false);
        }
        if (previous == null) {
            return new AccountStatsSectionDiff(true, true, true, true);
        }
        return new AccountStatsSectionDiff(
                !previous.getCurveSectionKey().equals(current.getCurveSectionKey()),
                !previous.getReturnSectionKey().equals(current.getReturnSectionKey()),
                !previous.getTradeStatsSectionKey().equals(current.getTradeStatsSectionKey()),
                !previous.getTradeRecordsSectionKey().equals(current.getTradeRecordsSectionKey())
        );
    }

    public boolean isEmpty() {
        return !refreshCurveSection
                && !refreshReturnSection
                && !refreshTradeStatsSection
                && !refreshTradeRecordsSection;
    }
}

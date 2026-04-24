/*
 * 账户统计页重渲染协调器，统一承接快照落地、次级区块补渲染、交易统计和交易记录筛选主链。
 * 宿主只提供页面状态和最终 UI 绑定能力，避免重业务主链继续堆在旧 Activity 里。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.ui.account.history.AccountStatsRenderSignature;
import com.binance.monitor.ui.account.history.AccountStatsSectionDiff;

import java.util.ArrayList;
import java.util.List;

final class AccountStatsRenderCoordinator {

    interface Host {
        void replaceTradeHistory(@Nullable List<TradeRecordItem> source);

        void replaceCurveHistory(@Nullable List<CurvePoint> source);

        void setLatestCurveIndicators(@Nullable List<AccountMetric> indicators);

        void setLatestStatsMetrics(@Nullable List<AccountMetric> metrics);

        void setBasePositions(@Nullable List<PositionItem> positions);

        void setBasePendingOrders(@Nullable List<PositionItem> pendingOrders);

        @NonNull
        List<TradeRecordItem> getTradeHistory();

        @NonNull
        List<CurvePoint> getCurveHistory();

        @NonNull
        String buildDataQualitySummary(@Nullable List<TradeRecordItem> trades,
                                       @Nullable List<CurvePoint> curves,
                                       @Nullable List<PositionItem> positions);

        void setDataQualitySummary(@NonNull String summary);

        long resolveCloseTime(@Nullable TradeRecordItem item);

        void setBaseTrades(@Nullable List<TradeRecordItem> trades);

        @NonNull
        List<TradeRecordItem> getBaseTrades();

        @NonNull
        List<PositionItem> getBasePositions();

        @NonNull
        List<CurvePoint> normalizeCurvePoints(@Nullable List<CurvePoint> source);

        void setAllCurvePoints(@Nullable List<CurvePoint> points);

        @NonNull
        List<CurvePoint> getAllCurvePoints();

        void setLatestCumulativePnl(double cumulativePnl);

        @NonNull
        List<CurvePoint> resolveImmediateCurvePoints();

        void renderCurveWithIndicators(@NonNull List<CurvePoint> points);

        void logTradeVisibilitySnapshot(@Nullable List<TradeRecordItem> snapshotTrades,
                                        @Nullable List<TradeRecordItem> effectiveTrades,
                                        @Nullable List<TradeRecordItem> baseTrades);

        void logAccountSnapshotEvents(@Nullable List<PositionItem> positions,
                                      @Nullable List<PositionItem> pendingOrders,
                                      @Nullable List<TradeRecordItem> trades,
                                      boolean remoteConnected);

        void ensureReturnStatsAnchor();

        void updateOverviewHeader();

        void setDeferredSecondaryRenderPending(boolean pending);

        boolean isSecondarySectionsAttached();

        void scheduleDeferredSecondarySectionAttach();

        void traceAccountRenderPhase(@NonNull String phase,
                                     long stageStartedAt,
                                     int tradeCount,
                                     int positionCount,
                                     int curveCount);

        @NonNull
        AccountStatsRenderSignature buildCurrentHistoryRenderSignature();

        boolean isForceDeferredSectionRender();

        void setForceDeferredSectionRender(boolean value);

        @Nullable
        AccountStatsRenderSignature getLastHistoryRenderSignature();

        void setPendingHistoryRenderSignature(@Nullable AccountStatsRenderSignature signature);

        void setPendingSectionDiff(@Nullable AccountStatsSectionDiff diff);

        void setLastHistoryRenderSignature(@Nullable AccountStatsRenderSignature signature);

        void hideTradeStatsSectionUntilFreshContentReady();

        int nextDeferredSecondaryRenderRevision();

        boolean canExecuteDeferredSecondarySectionRender();

        void executeDeferredSecondaryRender(@NonNull Runnable action);

        void runOnUiThread(@NonNull Runnable action);

        boolean shouldIgnoreDeferredSecondaryRenderResult(int renderRevision);

        void logRenderWarning(@NonNull String message);

        @Nullable
        AccountStatsSectionDiff getPendingSectionDiff();

        @Nullable
        AccountStatsRenderSignature getPendingHistoryRenderSignature();

        @NonNull
        List<AccountMetric> getLatestStatsMetrics();

        @NonNull
        AccountTimeRange getSelectedRange();

        boolean isManualCurveRangeEnabled();

        long getManualCurveRangeStartMs();

        long getManualCurveRangeEndMs();

        long getAppliedAccountUpdatedAt();

        @NonNull
        AccountDeferredSnapshotRenderHelper.TradePnlSideMode getTradePnlSideMode();

        @NonNull
        AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis getTradeWeekdayBasis();

        void bindTradeAnalytics(@Nullable List<AccountMetric> tradeStatsMetrics,
                                @Nullable List<TradePnlBarChartView.Entry> entries,
                                @Nullable List<CurveAnalyticsHelper.TradeScatterPoint> tradeScatterPoints,
                                @Nullable List<CurveAnalyticsHelper.DurationBucket> holdingDurationBuckets,
                                @Nullable List<TradeWeekdayBarChartHelper.Entry> weekdayEntries,
                                double totalPnl);

        void collapseAllExpandedRows();

        @NonNull
        String readTradeProductFilter();

        @NonNull
        String readTradeSideFilter();

        @NonNull
        String readTradeSortFilter();

        void setSelectedTradeProductFilter(@NonNull String filter);

        void setSelectedTradeSideFilter(@NonNull String filter);

        void setSelectedTradeSortFilter(@NonNull String filter);

        @NonNull
        String getTradeProductFilterLabel();

        @NonNull
        String getTradeSideFilterLabel();

        @NonNull
        String getTradeSortFilterLabel();

        @NonNull
        String getSelectedTradeProductFilterValue();

        @NonNull
        String getSelectedTradeSideFilterValue();

        @NonNull
        String getSelectedTradeSortFilterValue();

        void updateTradeFilterDisplayTexts(@NonNull String product,
                                           @NonNull String side,
                                           @NonNull String sort);

        void updateTradeProductOptions(@Nullable List<String> products,
                                       @NonNull String selectedProduct);

        void renderReturnStatsTable(@NonNull List<CurvePoint> curvePoints);

        void setManualCurveRangeEnabled(boolean enabled);

        void syncRangeInputsWithDisplayedCurve(@Nullable List<CurvePoint> displayedPoints);

        void applyPreparedCurveProjection(@NonNull AccountDeferredSnapshotRenderHelper.CurveProjection curveProjection);

        int getDisplayedCurvePointCount();

        @NonNull
        String getLastExplicitTradeSortMode();

        @NonNull
        String normalizeSortValue(@Nullable String rawSort);

        boolean isTradeSortDescending();

        @NonNull
        AccountDeferredSnapshotRenderHelper.SortMode toHelperSortMode(@NonNull String normalizedSort);

        void bindFilteredTrades(@Nullable List<TradeRecordItem> filtered,
                                @NonNull AccountDeferredSnapshotRenderHelper.TradeSummary tradeSummary,
                                boolean scrollToTop,
                                @NonNull String product,
                                @NonNull String side,
                                @NonNull String normalizedSort);
    }

    private final Host host;

    AccountStatsRenderCoordinator(@NonNull Host host) {
        this.host = host;
    }

    // 统一落地账户快照，把历史态与次级区块刷新编排收口到协调器。
    void applySnapshot(@NonNull AccountSnapshot snapshot, boolean remoteConnected) {
        long applySnapshotStartedAt = System.currentTimeMillis();
        List<PositionItem> snapshotPositions = snapshot.getPositions() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getPositions());
        List<PositionItem> snapshotPending = snapshot.getPendingOrders() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getPendingOrders());
        List<TradeRecordItem> snapshotTrades = snapshot.getTrades() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getTrades());
        List<CurvePoint> snapshotCurves = snapshot.getCurvePoints() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getCurvePoints());

        host.replaceTradeHistory(snapshotTrades);
        host.replaceCurveHistory(snapshotCurves);
        host.setLatestCurveIndicators(snapshot.getCurveIndicators());
        host.setLatestStatsMetrics(snapshot.getStatsMetrics());
        host.setBasePositions(snapshotPositions);
        host.setBasePendingOrders(snapshotPending);

        List<TradeRecordItem> effectiveTrades = new ArrayList<>(host.getTradeHistory());
        List<CurvePoint> effectiveCurves = new ArrayList<>(host.getCurveHistory());
        host.setDataQualitySummary(host.buildDataQualitySummary(
                effectiveTrades,
                effectiveCurves,
                host.getBasePositions()
        ));

        List<TradeRecordItem> sortedTrades = new ArrayList<>(effectiveTrades);
        sortedTrades.sort((left, right) -> Long.compare(
                host.resolveCloseTime(right),
                host.resolveCloseTime(left)
        ));
        host.setBaseTrades(sortedTrades);

        List<CurvePoint> normalizedCurves = host.normalizeCurvePoints(effectiveCurves);
        host.setAllCurvePoints(normalizedCurves);
        AccountOverviewCumulativeMetricsCalculator.OverviewCumulativeValues cumulativeValues =
                AccountOverviewCumulativeMetricsCalculator.calculate(
                        host.getBaseTrades(),
                        host.getBasePositions(),
                        normalizedCurves
                );
        host.setLatestCumulativePnl(cumulativeValues.hasCumulativePnlTruth()
                ? cumulativeValues.getCumulativePnl()
                : 0d);
        host.renderCurveWithIndicators(host.resolveImmediateCurvePoints());
        host.logTradeVisibilitySnapshot(snapshotTrades, effectiveTrades, host.getBaseTrades());
        host.logAccountSnapshotEvents(
                host.getBasePositions(),
                snapshotPending,
                host.getBaseTrades(),
                remoteConnected
        );
        host.ensureReturnStatsAnchor();
        host.updateOverviewHeader();

        host.setDeferredSecondaryRenderPending(true);
        if (host.isSecondarySectionsAttached()) {
            renderDeferredSnapshotSections();
        } else {
            host.scheduleDeferredSecondarySectionAttach();
        }
        host.traceAccountRenderPhase(
                "apply_snapshot_total",
                applySnapshotStartedAt,
                host.getBaseTrades().size(),
                host.getBasePositions().size(),
                normalizedCurves.size()
        );
    }

    // 次级区块渲染是否真的需要刷新，由协调器统一判断并调度。
    void renderDeferredSnapshotSections() {
        AccountStatsRenderSignature nextRenderSignature = host.buildCurrentHistoryRenderSignature();
        AccountStatsSectionDiff sectionDiff = host.isForceDeferredSectionRender()
                ? new AccountStatsSectionDiff(true, true, true, true)
                : AccountStatsSectionDiff.between(host.getLastHistoryRenderSignature(), nextRenderSignature);
        host.setForceDeferredSectionRender(false);
        if (sectionDiff.isEmpty()) {
            host.setDeferredSecondaryRenderPending(false);
            host.setPendingHistoryRenderSignature(nextRenderSignature);
            host.setPendingSectionDiff(sectionDiff);
            host.setLastHistoryRenderSignature(nextRenderSignature);
            return;
        }
        host.setPendingHistoryRenderSignature(nextRenderSignature);
        host.setPendingSectionDiff(sectionDiff);
        if (sectionDiff.refreshTradeStatsSection) {
            host.hideTradeStatsSectionUntilFreshContentReady();
        }
        host.setDeferredSecondaryRenderPending(false);
        scheduleDeferredSecondarySectionRender();
    }

    // 次级区块后台准备与主线程落地统一收口，旧 Activity 只保留最终宿主能力。
    void scheduleDeferredSecondarySectionRender() {
        if (!host.canExecuteDeferredSecondarySectionRender()) {
            return;
        }
        final AccountStatsSectionDiff sectionDiff = host.getPendingSectionDiff();
        if (sectionDiff == null || sectionDiff.isEmpty()) {
            return;
        }
        DeferredSecondaryRenderRequest request = buildDeferredSecondaryRenderRequest();
        int renderRevision = host.nextDeferredSecondaryRenderRevision();
        host.executeDeferredSecondaryRender(() -> {
            try {
                AccountDeferredSnapshotRenderHelper.PreparedSnapshotSections prepared =
                        AccountDeferredSnapshotRenderHelper.prepare(request.toHelperRequest());
                host.runOnUiThread(() -> {
                    if (host.shouldIgnoreDeferredSecondaryRenderResult(renderRevision)) {
                        return;
                    }
                    applyDeferredSecondaryRenderResult(request, prepared);
                });
            } catch (Exception exception) {
                host.logRenderWarning("Deferred secondary render failed: " + exception.getMessage());
            }
        });
    }

    // 交易统计大区块统一走后台 helper + 主线程绑定的同一条入口。
    void refreshTradeStats() {
        AccountDeferredSnapshotRenderHelper.TradeAnalytics tradeAnalytics =
                AccountDeferredSnapshotRenderHelper.buildTradeAnalytics(
                        host.getLatestStatsMetrics(),
                        host.getBaseTrades(),
                        host.getTradePnlSideMode(),
                        host.getTradeWeekdayBasis(),
                        host.getAllCurvePoints()
                );
        host.bindTradeAnalytics(
                tradeAnalytics.getTradeStatsMetrics(),
                tradeAnalytics.getTradePnlEntries(),
                tradeAnalytics.getTradeScatterPoints(),
                tradeAnalytics.getHoldingDurationBuckets(),
                tradeAnalytics.getTradeWeekdayEntries(),
                tradeAnalytics.getTradePnlTotal()
        );
    }

    // 收益统计模式或口径切换时，统一通过协调器刷新收益表，避免旧 Activity 自己重建。
    void refreshReturnStats() {
        host.renderReturnStatsTable(host.getAllCurvePoints());
    }

    // 时间区间或手动日期区间变化时，统一通过协调器刷新曲线投影，避免旧 Activity 自己拼重算。
    void refreshCurveProjection() {
        AccountDeferredSnapshotRenderHelper.CurveProjection curveProjection =
                AccountDeferredSnapshotRenderHelper.buildCurveProjection(
                        host.getAllCurvePoints(),
                        host.getSelectedRange(),
                        host.isManualCurveRangeEnabled(),
                        host.getManualCurveRangeStartMs(),
                        host.getManualCurveRangeEndMs(),
                        host.getAppliedAccountUpdatedAt()
                );
        host.setManualCurveRangeEnabled(curveProjection.isManualRangeApplied());
        host.syncRangeInputsWithDisplayedCurve(curveProjection.getDisplayedCurvePoints());
        host.applyPreparedCurveProjection(curveProjection);
    }

    // 交易记录筛选和排序统一收口，避免旧 Activity 自己再持有一套主链。
    void refreshTrades(boolean scrollToTop, boolean collapseExpanded) {
        if (collapseExpanded) {
            host.collapseAllExpandedRows();
        }
        String product = host.readTradeProductFilter();
        String side = host.readTradeSideFilter();
        String sort = host.readTradeSortFilter();
        host.setSelectedTradeProductFilter(product);
        host.setSelectedTradeSideFilter(side);
        host.setSelectedTradeSortFilter(sort);
        host.updateTradeFilterDisplayTexts(product, side, sort);

        String normalizedSort = "排序方式".equals(sort)
                ? host.normalizeSortValue(host.getLastExplicitTradeSortMode())
                : host.normalizeSortValue(sort);
        List<TradeRecordItem> filteredTrades = AccountDeferredSnapshotRenderHelper.buildFilteredTrades(
                host.getBaseTrades(),
                new AccountDeferredSnapshotRenderHelper.TradeFilterRequest(
                        product,
                        "全部产品".equals(product),
                        side,
                        "全部方向".equals(side),
                        host.toHelperSortMode(normalizedSort),
                        host.isTradeSortDescending()
                )
        );
        host.bindFilteredTrades(
                filteredTrades,
                AccountDeferredSnapshotRenderHelper.buildTradeSummary(filteredTrades),
                scrollToTop,
                product,
                side,
                normalizedSort
        );
    }

    // 冻结一次次级区块刷新所需的状态，避免后台线程读取到中途变化的 UI 选择。
    private DeferredSecondaryRenderRequest buildDeferredSecondaryRenderRequest() {
        String product = host.getSelectedTradeProductFilterValue();
        if (product.trim().isEmpty()) {
            product = host.getTradeProductFilterLabel();
        }
        String side = host.getSelectedTradeSideFilterValue();
        if (side.trim().isEmpty()) {
            side = host.getTradeSideFilterLabel();
        }
        String sort = host.getSelectedTradeSortFilterValue();
        if (sort.trim().isEmpty()) {
            sort = host.getTradeSortFilterLabel();
        }
        String normalizedSort = host.getTradeSortFilterLabel().equals(sort)
                ? host.normalizeSortValue(host.getLastExplicitTradeSortMode())
                : host.normalizeSortValue(sort);
        return new DeferredSecondaryRenderRequest(
                new ArrayList<>(host.getLatestStatsMetrics()),
                new ArrayList<>(host.getBaseTrades()),
                new ArrayList<>(host.getAllCurvePoints()),
                host.getSelectedRange(),
                host.isManualCurveRangeEnabled(),
                host.getManualCurveRangeStartMs(),
                host.getManualCurveRangeEndMs(),
                host.getAppliedAccountUpdatedAt(),
                host.getTradePnlSideMode(),
                host.getTradeWeekdayBasis(),
                product,
                host.getTradeProductFilterLabel().equals(product),
                side,
                host.getTradeSideFilterLabel().equals(side),
                sort,
                normalizedSort,
                host.isTradeSortDescending()
        );
    }

    // 把后台准备好的结果一次性绑定到次级区块，避免主线程重复计算。
    private void applyDeferredSecondaryRenderResult(DeferredSecondaryRenderRequest request,
                                                    AccountDeferredSnapshotRenderHelper.PreparedSnapshotSections prepared) {
        AccountStatsSectionDiff sectionDiff = host.getPendingSectionDiff();
        if (sectionDiff == null || sectionDiff.isEmpty()) {
            host.setLastHistoryRenderSignature(host.getPendingHistoryRenderSignature());
            return;
        }
        long stageStartedAt = System.currentTimeMillis();
        host.updateTradeProductOptions(prepared.getTradeProducts(), request.tradeProductFilter);
        host.updateTradeFilterDisplayTexts(request.tradeProductFilter, request.tradeSideFilter, request.rawSortSelection);
        host.traceAccountRenderPhase("bind_overview_and_filters",
                stageStartedAt,
                host.getBaseTrades().size(),
                host.getBasePositions().size(),
                host.getAllCurvePoints().size());

        if (sectionDiff.refreshReturnSection) {
            stageStartedAt = System.currentTimeMillis();
            host.renderReturnStatsTable(host.getAllCurvePoints());
            host.traceAccountRenderPhase("render_returns_table",
                    stageStartedAt,
                    host.getBaseTrades().size(),
                    host.getBasePositions().size(),
                    host.getAllCurvePoints().size());
        }

        if (sectionDiff.refreshCurveSection) {
            stageStartedAt = System.currentTimeMillis();
            host.setManualCurveRangeEnabled(prepared.getCurveProjection().isManualRangeApplied());
            host.syncRangeInputsWithDisplayedCurve(prepared.getCurveProjection().getDisplayedCurvePoints());
            host.applyPreparedCurveProjection(prepared.getCurveProjection());
            host.traceAccountRenderPhase("apply_curve_range",
                    stageStartedAt,
                    host.getBaseTrades().size(),
                    host.getBasePositions().size(),
                    host.getDisplayedCurvePointCount());
        }

        if (sectionDiff.refreshTradeStatsSection) {
            stageStartedAt = System.currentTimeMillis();
            host.bindTradeAnalytics(prepared.getTradeStatsMetrics(),
                    prepared.getTradePnlEntries(),
                    prepared.getTradeScatterPoints(),
                    prepared.getHoldingDurationBuckets(),
                    prepared.getTradeWeekdayEntries(),
                    prepared.getTradePnlTotal());
            host.traceAccountRenderPhase("refresh_trade_stats",
                    stageStartedAt,
                    host.getBaseTrades().size(),
                    host.getBasePositions().size(),
                    host.getDisplayedCurvePointCount());
        }

        if (sectionDiff.refreshTradeRecordsSection) {
            stageStartedAt = System.currentTimeMillis();
            host.bindFilteredTrades(prepared.getFilteredTrades(),
                    prepared.getTradeSummary(),
                    false,
                    request.tradeProductFilter,
                    request.tradeSideFilter,
                    request.normalizedSort);
            host.traceAccountRenderPhase("refresh_trades",
                    stageStartedAt,
                    host.getBaseTrades().size(),
                    host.getBasePositions().size(),
                    host.getDisplayedCurvePointCount());
        }
        host.setLastHistoryRenderSignature(host.getPendingHistoryRenderSignature());
    }

    // 次级区块后台计算请求，冻结一次渲染所需的筛选条件和数据快照。
    private static final class DeferredSecondaryRenderRequest {
        private final List<AccountMetric> latestStatsMetrics;
        private final List<TradeRecordItem> baseTrades;
        private final List<CurvePoint> allCurvePoints;
        private final AccountTimeRange selectedRange;
        private final boolean manualCurveRangeEnabled;
        private final long manualCurveRangeStartMs;
        private final long manualCurveRangeEndMs;
        private final long appliedAccountUpdatedAt;
        private final AccountDeferredSnapshotRenderHelper.TradePnlSideMode tradePnlSideMode;
        private final AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis tradeWeekdayBasis;
        private final String tradeProductFilter;
        private final boolean allProducts;
        private final String tradeSideFilter;
        private final boolean allSides;
        private final String rawSortSelection;
        private final String normalizedSort;
        private final boolean tradeSortDescending;

        private DeferredSecondaryRenderRequest(List<AccountMetric> latestStatsMetrics,
                                               List<TradeRecordItem> baseTrades,
                                               List<CurvePoint> allCurvePoints,
                                               AccountTimeRange selectedRange,
                                               boolean manualCurveRangeEnabled,
                                               long manualCurveRangeStartMs,
                                               long manualCurveRangeEndMs,
                                               long appliedAccountUpdatedAt,
                                               AccountDeferredSnapshotRenderHelper.TradePnlSideMode tradePnlSideMode,
                                               AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis tradeWeekdayBasis,
                                               String tradeProductFilter,
                                               boolean allProducts,
                                               String tradeSideFilter,
                                               boolean allSides,
                                               String rawSortSelection,
                                               String normalizedSort,
                                               boolean tradeSortDescending) {
            this.latestStatsMetrics = latestStatsMetrics;
            this.baseTrades = baseTrades;
            this.allCurvePoints = allCurvePoints;
            this.selectedRange = selectedRange;
            this.manualCurveRangeEnabled = manualCurveRangeEnabled;
            this.manualCurveRangeStartMs = manualCurveRangeStartMs;
            this.manualCurveRangeEndMs = manualCurveRangeEndMs;
            this.appliedAccountUpdatedAt = appliedAccountUpdatedAt;
            this.tradePnlSideMode = tradePnlSideMode;
            this.tradeWeekdayBasis = tradeWeekdayBasis;
            this.tradeProductFilter = tradeProductFilter;
            this.allProducts = allProducts;
            this.tradeSideFilter = tradeSideFilter;
            this.allSides = allSides;
            this.rawSortSelection = rawSortSelection;
            this.normalizedSort = normalizedSort;
            this.tradeSortDescending = tradeSortDescending;
        }

        private AccountDeferredSnapshotRenderHelper.PrepareRequest toHelperRequest() {
            return new AccountDeferredSnapshotRenderHelper.PrepareRequest(
                    latestStatsMetrics,
                    baseTrades,
                    allCurvePoints,
                    selectedRange,
                    manualCurveRangeEnabled,
                    manualCurveRangeStartMs,
                    manualCurveRangeEndMs,
                    appliedAccountUpdatedAt,
                    tradePnlSideMode,
                    tradeWeekdayBasis,
                    new AccountDeferredSnapshotRenderHelper.TradeFilterRequest(
                            tradeProductFilter,
                            allProducts,
                            tradeSideFilter,
                            allSides,
                            toHelperSortMode(normalizedSort),
                            tradeSortDescending
                    )
            );
        }

        private static AccountDeferredSnapshotRenderHelper.SortMode toHelperSortMode(String normalizedSort) {
            if ("开仓时间".equals(normalizedSort)) {
                return AccountDeferredSnapshotRenderHelper.SortMode.OPEN_TIME;
            }
            if ("盈亏金额".equals(normalizedSort)) {
                return AccountDeferredSnapshotRenderHelper.SortMode.PROFIT;
            }
            return AccountDeferredSnapshotRenderHelper.SortMode.CLOSE_TIME;
        }
    }
}

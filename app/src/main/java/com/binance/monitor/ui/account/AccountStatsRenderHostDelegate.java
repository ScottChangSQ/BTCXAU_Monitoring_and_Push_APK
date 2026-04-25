/*
 * 账户统计渲染宿主委托，统一适配渲染协调器宿主能力。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.ui.account.history.AccountStatsRenderSignature;
import com.binance.monitor.ui.account.history.AccountStatsSectionDiff;

import java.util.List;

public final class AccountStatsRenderHostDelegate implements AccountStatsRenderCoordinator.Host {

    private final Owner owner;

    public AccountStatsRenderHostDelegate(@NonNull Owner owner) {
        this.owner = owner;
    }

    @Override public void replaceTradeHistory(@Nullable List<TradeRecordItem> source) { owner.replaceTradeHistory(source); }
    @Override public void replaceCurveHistory(@Nullable List<CurvePoint> source) { owner.replaceCurveHistory(source); }
    @Override public void setLatestCurveIndicators(@Nullable List<AccountMetric> indicators) { owner.setLatestCurveIndicators(indicators); }
    @Override public void setLatestStatsMetrics(@Nullable List<AccountMetric> metrics) { owner.setLatestStatsMetrics(metrics); }
    @Override public void setBasePositions(@Nullable List<PositionItem> positions) { owner.setBasePositions(positions); }
    @Override public void setBasePendingOrders(@Nullable List<PositionItem> pendingOrders) { owner.setBasePendingOrders(pendingOrders); }
    @NonNull @Override public List<TradeRecordItem> getTradeHistory() { return owner.getTradeHistory(); }
    @NonNull @Override public List<CurvePoint> getCurveHistory() { return owner.getCurveHistory(); }
    @NonNull @Override public String buildDataQualitySummary(@Nullable List<TradeRecordItem> trades, @Nullable List<CurvePoint> curves, @Nullable List<PositionItem> positions) { return owner.buildDataQualitySummary(trades, curves, positions); }
    @Override public void setDataQualitySummary(@NonNull String summary) { owner.setDataQualitySummary(summary); }
    @Override public long resolveCloseTime(@Nullable TradeRecordItem item) { return owner.resolveCloseTime(item); }
    @Override public void setBaseTrades(@Nullable List<TradeRecordItem> trades) { owner.setBaseTrades(trades); }
    @NonNull @Override public List<TradeRecordItem> getBaseTrades() { return owner.getBaseTrades(); }
    @NonNull @Override public List<PositionItem> getBasePositions() { return owner.getBasePositions(); }
    @NonNull @Override public List<CurvePoint> normalizeCurvePoints(@Nullable List<CurvePoint> source) { return owner.normalizeCurvePoints(source); }
    @Override public void setAllCurvePoints(@Nullable List<CurvePoint> points) { owner.setAllCurvePoints(points); }
    @NonNull @Override public List<CurvePoint> getAllCurvePoints() { return owner.getAllCurvePoints(); }
    @Override public void setLatestCumulativePnl(double cumulativePnl) { owner.setLatestCumulativePnl(cumulativePnl); }
    @NonNull @Override public List<CurvePoint> resolveImmediateCurvePoints() { return owner.resolveImmediateCurvePoints(); }
    @Override public void renderCurveWithIndicators(@NonNull List<CurvePoint> points) { owner.renderCurveWithIndicators(points); }
    @Override public void logTradeVisibilitySnapshot(@Nullable List<TradeRecordItem> snapshotTrades, @Nullable List<TradeRecordItem> effectiveTrades, @Nullable List<TradeRecordItem> baseTrades) { owner.logTradeVisibilitySnapshot(snapshotTrades, effectiveTrades, baseTrades); }
    @Override public void logAccountSnapshotEvents(@Nullable List<PositionItem> positions, @Nullable List<PositionItem> pendingOrders, @Nullable List<TradeRecordItem> trades, boolean remoteConnected) { owner.logAccountSnapshotEvents(positions, pendingOrders, trades, remoteConnected); }
    @Override public void ensureReturnStatsAnchor() { owner.ensureReturnStatsAnchor(); }
    @Override public void updateOverviewHeader() { owner.updateOverviewHeader(); }
    @Override public void setDeferredSecondaryRenderPending(boolean pending) { owner.setDeferredSecondaryRenderPending(pending); }
    @Override public boolean isSecondarySectionsAttached() { return owner.isSecondarySectionsAttached(); }
    @Override public void scheduleDeferredSecondarySectionAttach() { owner.scheduleDeferredSecondarySectionAttach(); }
    @Override public void traceAccountRenderPhase(@NonNull String phase, long stageStartedAt, int tradeCount, int positionCount, int curveCount) { owner.traceAccountRenderPhase(phase, stageStartedAt, tradeCount, positionCount, curveCount); }
    @NonNull @Override public AccountStatsRenderSignature buildCurrentHistoryRenderSignature() { return owner.buildCurrentHistoryRenderSignature(); }
    @Override public boolean isForceDeferredSectionRender() { return owner.isForceDeferredSectionRender(); }
    @Override public void setForceDeferredSectionRender(boolean value) { owner.setForceDeferredSectionRender(value); }
    @Nullable @Override public AccountStatsRenderSignature getLastHistoryRenderSignature() { return owner.getLastHistoryRenderSignature(); }
    @Override public void setPendingHistoryRenderSignature(@Nullable AccountStatsRenderSignature signature) { owner.setPendingHistoryRenderSignature(signature); }
    @Override public void setPendingSectionDiff(@Nullable AccountStatsSectionDiff diff) { owner.setPendingSectionDiff(diff); }
    @Override public void setLastHistoryRenderSignature(@Nullable AccountStatsRenderSignature signature) { owner.setLastHistoryRenderSignature(signature); }
    @Override public void hideTradeStatsSectionUntilFreshContentReady() { owner.hideTradeStatsSectionUntilFreshContentReady(); }
    @Override public int nextDeferredSecondaryRenderRevision() { return owner.nextDeferredSecondaryRenderRevision(); }
    @Override public boolean canExecuteDeferredSecondarySectionRender() { return owner.canExecuteDeferredSecondarySectionRender(); }
    @Override public void executeDeferredSecondaryRender(@NonNull Runnable action) { owner.executeDeferredSecondaryRender(action); }
    @Override public void runOnUiThread(@NonNull Runnable action) { owner.runOnUiThread(action); }
    @Override public boolean shouldIgnoreDeferredSecondaryRenderResult(int renderRevision) { return owner.shouldIgnoreDeferredSecondaryRenderResult(renderRevision); }
    @Override public void logRenderWarning(@NonNull String message) { owner.logRenderWarning(message); }
    @Nullable @Override public AccountStatsSectionDiff getPendingSectionDiff() { return owner.getPendingSectionDiff(); }
    @Nullable @Override public AccountStatsRenderSignature getPendingHistoryRenderSignature() { return owner.getPendingHistoryRenderSignature(); }
    @Override public boolean isCurveSectionVisible() { return owner.isCurveSectionVisible(); }
    @Override public boolean isReturnSectionVisible() { return owner.isReturnSectionVisible(); }
    @Override public boolean isTradeStatsSectionVisible() { return owner.isTradeStatsSectionVisible(); }
    @Override public boolean isTradeRecordsSectionVisible() { return owner.isTradeRecordsSectionVisible(); }
    @NonNull @Override public List<AccountMetric> getLatestStatsMetrics() { return owner.getLatestStatsMetrics(); }
    @NonNull @Override public AccountTimeRange getSelectedRange() { return owner.getSelectedRange(); }
    @Override public boolean isManualCurveRangeEnabled() { return owner.isManualCurveRangeEnabled(); }
    @Override public long getManualCurveRangeStartMs() { return owner.getManualCurveRangeStartMs(); }
    @Override public long getManualCurveRangeEndMs() { return owner.getManualCurveRangeEndMs(); }
    @Override public long getAppliedAccountUpdatedAt() { return owner.getAppliedAccountUpdatedAt(); }
    @NonNull @Override public AccountDeferredSnapshotRenderHelper.TradePnlSideMode getTradePnlSideMode() { return owner.getTradePnlSideMode(); }
    @NonNull @Override public AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis getTradeWeekdayBasis() { return owner.getTradeWeekdayBasis(); }
    @Override public void bindTradeAnalytics(@Nullable List<AccountMetric> tradeStatsMetrics, @Nullable List<TradePnlBarChartView.Entry> entries, @Nullable List<CurveAnalyticsHelper.TradeScatterPoint> tradeScatterPoints, @Nullable List<CurveAnalyticsHelper.DurationBucket> holdingDurationBuckets, @Nullable List<TradeWeekdayBarChartHelper.Entry> weekdayEntries, double totalPnl) { owner.bindTradeAnalytics(tradeStatsMetrics, entries, tradeScatterPoints, holdingDurationBuckets, weekdayEntries, totalPnl); }
    @Override public void collapseAllExpandedRows() { owner.collapseAllExpandedRows(); }
    @NonNull @Override public String readTradeProductFilter() { return owner.readTradeProductFilter(); }
    @NonNull @Override public String readTradeSideFilter() { return owner.readTradeSideFilter(); }
    @NonNull @Override public String readTradeSortFilter() { return owner.readTradeSortFilter(); }
    @Override public void setSelectedTradeProductFilter(@NonNull String filter) { owner.setSelectedTradeProductFilter(filter); }
    @Override public void setSelectedTradeSideFilter(@NonNull String filter) { owner.setSelectedTradeSideFilter(filter); }
    @Override public void setSelectedTradeSortFilter(@NonNull String filter) { owner.setSelectedTradeSortFilter(filter); }
    @NonNull @Override public String getTradeProductFilterLabel() { return owner.getTradeProductFilterLabel(); }
    @NonNull @Override public String getTradeSideFilterLabel() { return owner.getTradeSideFilterLabel(); }
    @NonNull @Override public String getTradeSortFilterLabel() { return owner.getTradeSortFilterLabel(); }
    @NonNull @Override public String getSelectedTradeProductFilterValue() { return owner.getSelectedTradeProductFilterValue(); }
    @NonNull @Override public String getSelectedTradeSideFilterValue() { return owner.getSelectedTradeSideFilterValue(); }
    @NonNull @Override public String getSelectedTradeSortFilterValue() { return owner.getSelectedTradeSortFilterValue(); }
    @Override public void updateTradeFilterDisplayTexts(@NonNull String product, @NonNull String side, @NonNull String sort) { owner.updateTradeFilterDisplayTexts(product, side, sort); }
    @Override public void updateTradeProductOptions(@Nullable List<String> products, @NonNull String selectedProduct) { owner.updateTradeProductOptions(products, selectedProduct); }
    @Override public void renderReturnStatsTable(@NonNull List<CurvePoint> curvePoints) { owner.renderReturnStatsTable(curvePoints); }
    @Override public void setManualCurveRangeEnabled(boolean enabled) { owner.setManualCurveRangeEnabled(enabled); }
    @Override public void syncRangeInputsWithDisplayedCurve(@Nullable List<CurvePoint> displayedPoints) { owner.syncRangeInputsWithDisplayedCurve(displayedPoints); }
    @Override public void applyPreparedCurveProjection(@NonNull AccountDeferredSnapshotRenderHelper.CurveProjection curveProjection) { owner.applyPreparedCurveProjection(curveProjection); }
    @Override public int getDisplayedCurvePointCount() { return owner.getDisplayedCurvePointCount(); }
    @NonNull @Override public String getLastExplicitTradeSortMode() { return owner.getLastExplicitTradeSortMode(); }
    @NonNull @Override public String normalizeSortValue(@Nullable String rawSort) { return owner.normalizeSortValue(rawSort); }
    @Override public boolean isTradeSortDescending() { return owner.isTradeSortDescending(); }
    @NonNull @Override public AccountDeferredSnapshotRenderHelper.SortMode toHelperSortMode(@NonNull String normalizedSort) { return owner.toHelperSortMode(normalizedSort); }
    @Override public void bindFilteredTrades(@Nullable List<TradeRecordItem> filtered, @NonNull AccountDeferredSnapshotRenderHelper.TradeSummary tradeSummary, boolean scrollToTop, @NonNull String product, @NonNull String side, @NonNull String normalizedSort) { owner.bindFilteredTrades(filtered, tradeSummary, scrollToTop, product, side, normalizedSort); }

    public interface Owner extends AccountStatsRenderCoordinator.Host {}
}

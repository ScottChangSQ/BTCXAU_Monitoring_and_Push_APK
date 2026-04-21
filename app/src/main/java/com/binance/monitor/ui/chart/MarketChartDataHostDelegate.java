/*
 * 行情持仓页数据宿主委托，统一适配图表数据协调器宿主能力。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

import java.util.List;

public final class MarketChartDataHostDelegate implements MarketChartDataCoordinator.Host {

    private final Owner owner;

    public MarketChartDataHostDelegate(@NonNull Owner owner) {
        this.owner = owner;
    }

    @Nullable @Override public MonitorRepository getMonitorRepository() { return owner.getMonitorRepository(); }
    @NonNull @Override public LifecycleOwner getLifecycleOwner() { return owner.getLifecycleOwner(); }
    @NonNull @Override public String getSelectedSymbol() { return owner.getSelectedSymbol(); }
    @NonNull @Override public MarketChartDataCoordinator.IntervalSelection getSelectedInterval() { return owner.getSelectedInterval(); }
    @NonNull @Override public String buildCacheKey(@NonNull String symbol, @NonNull MarketChartDataCoordinator.IntervalSelection interval) { return owner.buildCacheKey(symbol, interval); }
    @Override public boolean cancelRunningRequestIfNeeded(boolean allowCancelRunning, boolean autoRefresh) { return owner.cancelRunningRequestIfNeeded(allowCancelRunning, autoRefresh); }
    @Nullable @Override public List<CandleEntry> getCachedCandles(@NonNull String key) { return owner.getCachedCandles(key); }
    @Override public void schedulePersistedCacheRestore(@NonNull String key, boolean applyWhenLoaded) { owner.schedulePersistedCacheRestore(key, applyWhenLoaded); }
    @NonNull @Override public List<CandleEntry> buildWarmDisplayCandles(@NonNull String symbol, @NonNull MarketChartDataCoordinator.IntervalSelection targetInterval) { return owner.buildWarmDisplayCandles(symbol, targetInterval); }
    @Override public void applyLocalDisplayCandles(@NonNull String key, @NonNull List<CandleEntry> candles) { owner.applyLocalDisplayCandles(key, candles); }
    @NonNull @Override public String getActiveDataKey() { return owner.getActiveDataKey(); }
    @NonNull @Override public List<CandleEntry> getLoadedCandles() { return owner.getLoadedCandles(); }
    @Override public long resolveLatestVisibleCandleTime(@Nullable List<CandleEntry> visible) { return owner.resolveLatestVisibleCandleTime(visible); }
    @Override public long intervalToMs(@NonNull String key) { return owner.intervalToMs(key); }
    @Override public boolean hasRealtimeTailSourceForChart() { return owner.hasRealtimeTailSourceForChart(); }
    @Override public void applyRequestSkipState(@NonNull MarketChartRefreshHelper.SyncPlan refreshPlan) { owner.applyRequestSkipState(refreshPlan); }
    @Override public int nextRequestVersion() { return owner.nextRequestVersion(); }
    @Override public void applyRequestStartState(boolean autoRefresh) { owner.applyRequestStartState(autoRefresh); }
    @Override public void setRunningTaskStartMs(long startedAtMs) { owner.setRunningTaskStartMs(startedAtMs); }
    @Override public void cancelProgressiveGapFillTask() { owner.cancelProgressiveGapFillTask(); }
    @Override public void submitRunningTask(@NonNull Runnable action) { owner.submitRunningTask(action); }
    @NonNull @Override public List<CandleEntry> loadCandlesForRequest(@NonNull MarketChartRefreshHelper.SyncPlan plan, @Nullable List<CandleEntry> seed, long previousLatestOpenTime, long previousOldestOpenTime, int previousWindowSize, @NonNull String symbol, @NonNull MarketChartDataCoordinator.IntervalSelection interval) throws Exception { return owner.loadCandlesForRequest(plan, seed, previousLatestOpenTime, previousOldestOpenTime, previousWindowSize, symbol, interval); }
    @Override public void postToMainThread(@NonNull Runnable action) { owner.postToMainThread(action); }
    @Override public boolean shouldIgnoreRequestResult(int requestVersion) { return owner.shouldIgnoreRequestResult(requestVersion); }
    @Override public boolean shouldFollowLatestViewportOnRefresh() { return owner.shouldFollowLatestViewportOnRefresh(); }
    @Override public void setActiveDataKey(@NonNull String key) { owner.setActiveDataKey(key); }
    @Override public void applyDisplayCandles(@NonNull String key, @NonNull List<CandleEntry> candles, boolean keepViewport, boolean shouldFollowLatest, boolean updateMemoryCache) { owner.applyDisplayCandles(key, candles, keepViewport, shouldFollowLatest, updateMemoryCache); }
    @Override public void persistClosedCandles(@NonNull String key, @NonNull List<CandleEntry> finalProcessed, @NonNull String symbol, @NonNull MarketChartDataCoordinator.IntervalSelection interval) { owner.persistClosedCandles(key, finalProcessed, symbol, interval); }
    @Override public void startProgressiveGapFill(@NonNull String reqSymbol, @NonNull MarketChartDataCoordinator.IntervalSelection reqInterval, int current, @Nullable List<CandleEntry> visibleWindow, long previousOldestOpenTime, int previousWindowSize) { owner.startProgressiveGapFill(reqSymbol, reqInterval, current, visibleWindow, previousOldestOpenTime, previousWindowSize); }
    @Override public void applyRequestSuccessState(boolean autoRefresh, long requestStartedAtMs) { owner.applyRequestSuccessState(autoRefresh, requestStartedAtMs); }
    @Override public void applyRequestFailureState(boolean autoRefresh, boolean deferTrueEmptyUntilStorageRestore, @NonNull String message) { owner.applyRequestFailureState(autoRefresh, deferTrueEmptyUntilStorageRestore, message); }
    @Override public boolean beginLoadMore() { return owner.beginLoadMore(); }
    @Override public void notifyLoadMoreFinished() { owner.notifyLoadMoreFinished(); }
    @Override public void cancelLoadMoreTask() { owner.cancelLoadMoreTask(); }
    @Override public void submitLoadMoreTask(@NonNull Runnable action) { owner.submitLoadMoreTask(action); }
    @NonNull @Override public List<CandleEntry> fetchV2SeriesBefore(@NonNull String symbol, @NonNull MarketChartDataCoordinator.IntervalSelection interval, int limit, long endTimeInclusive) throws Exception { return owner.fetchV2SeriesBefore(symbol, interval, limit, endTimeInclusive); }
    @NonNull @Override public List<CandleEntry> aggregateToYear(@Nullable List<CandleEntry> source, @NonNull String symbol) { return owner.aggregateToYear(source, symbol); }
    @Override public boolean shouldIgnoreLoadMoreResult(@NonNull String reqSymbol, @NonNull MarketChartDataCoordinator.IntervalSelection reqInterval) { return owner.shouldIgnoreLoadMoreResult(reqSymbol, reqInterval); }
    @Override public void applyLoadMoreSuccessState(@NonNull String reqSymbol, @NonNull MarketChartDataCoordinator.IntervalSelection reqInterval, @NonNull List<CandleEntry> older) { owner.applyLoadMoreSuccessState(reqSymbol, reqInterval, older); }
    @Override public void finishLoadMoreState() { owner.finishLoadMoreState(); }
    @Override public int getRestoreWindowLimit() { return owner.getRestoreWindowLimit(); }
    @Override public int getHistoryPageLimit() { return owner.getHistoryPageLimit(); }
    @Override public void applyRealtimeChartTail(@Nullable KlineData latestKline) { owner.applyRealtimeChartTail(latestKline); }
    @Override public void updateVolumeThresholdOverlay() { owner.updateVolumeThresholdOverlay(); }
    @Override public void updateAccountAnnotationsOverlay() { owner.updateAccountAnnotationsOverlay(); }
    @Override public void updateAbnormalAnnotationsOverlay() { owner.updateAbnormalAnnotationsOverlay(); }
    @Override public boolean isChartViewReady() { return owner.isChartViewReady(); }
    @Override public boolean isAccountSessionActive() { return owner.isAccountSessionActive(); }
    @Nullable @Override public AccountStatsPreloadManager.Cache getLatestAccountCache() { return owner.getLatestAccountCache(); }
    @Nullable @Override public AccountSnapshot resolveChartOverlaySnapshot(boolean sessionActive, @Nullable AccountStatsPreloadManager.Cache cache) { return owner.resolveChartOverlaySnapshot(sessionActive, cache); }
    @Override public boolean isCurrentOverlayBoundToActiveSession() { return owner.isCurrentOverlayBoundToActiveSession(); }
    @Override public void clearAccountAnnotationsOverlay() { owner.clearAccountAnnotationsOverlay(); }
    @NonNull @Override public String buildCurrentCacheKey() { return owner.buildCurrentCacheKey(); }
    @Override public boolean shouldDeferOverlayUntilPrimaryDisplay(@NonNull String key) { return owner.shouldDeferOverlayUntilPrimaryDisplay(key); }
    @Override public void replacePendingOverlay(@NonNull String key, @NonNull Runnable action) { owner.replacePendingOverlay(key, action); }
    @Override public void applyChartOverlaySnapshot(@NonNull AccountSnapshot snapshot, @Nullable AccountStatsPreloadManager.Cache cache) { owner.applyChartOverlaySnapshot(snapshot, cache); }

    public interface Owner extends MarketChartDataCoordinator.Host {}
}

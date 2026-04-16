/*
 * 行情持仓页数据协调器，统一承接图表请求入口、实时观察和图上叠加层主链。
 * 宿主只提供底层数据访问和最终落图能力，避免重业务链继续堆在旧 Activity 里。
 */
package com.binance.monitor.ui.chart;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.util.ChainLatencyTracer;

import java.util.ArrayList;
import java.util.List;

final class MarketChartDataCoordinator {

    static final class IntervalSelection {
        private final String key;
        private final String apiInterval;
        private final int limit;
        private final boolean yearAggregate;

        IntervalSelection(@NonNull String key,
                          @NonNull String apiInterval,
                          int limit,
                          boolean yearAggregate) {
            this.key = key;
            this.apiInterval = apiInterval;
            this.limit = limit;
            this.yearAggregate = yearAggregate;
        }

        @NonNull
        String getKey() {
            return key;
        }

        @NonNull
        String getApiInterval() {
            return apiInterval;
        }

        int getLimit() {
            return limit;
        }

        boolean isYearAggregate() {
            return yearAggregate;
        }
    }

    interface Host {
        @Nullable
        MonitorRepository getMonitorRepository();

        @NonNull
        LifecycleOwner getLifecycleOwner();

        @NonNull
        String getSelectedSymbol();

        @NonNull
        IntervalSelection getSelectedInterval();

        @NonNull
        String buildCacheKey(@NonNull String symbol, @NonNull IntervalSelection interval);

        boolean cancelRunningRequestIfNeeded(boolean allowCancelRunning, boolean autoRefresh);

        @Nullable
        List<CandleEntry> getCachedCandles(@NonNull String key);

        void schedulePersistedCacheRestore(@NonNull String key, boolean applyWhenLoaded);

        @NonNull
        List<CandleEntry> buildWarmDisplayCandles(@NonNull String symbol, @NonNull IntervalSelection targetInterval);

        void applyLocalDisplayCandles(@NonNull String key, @NonNull List<CandleEntry> candles);

        @NonNull
        String getActiveDataKey();

        @NonNull
        List<CandleEntry> getLoadedCandles();

        long resolveLatestVisibleCandleTime(@Nullable List<CandleEntry> visible);

        long intervalToMs(@NonNull String key);

        boolean hasRealtimeTailSourceForChart();

        void applyRequestSkipState(@NonNull MarketChartRefreshHelper.SyncPlan refreshPlan);

        int nextRequestVersion();

        void applyRequestStartState(boolean autoRefresh);

        void setRunningTaskStartMs(long startedAtMs);

        void cancelProgressiveGapFillTask();

        void submitRunningTask(@NonNull Runnable action);

        @NonNull
        List<CandleEntry> loadCandlesForRequest(@NonNull MarketChartRefreshHelper.SyncPlan plan,
                                                @Nullable List<CandleEntry> seed,
                                                long previousLatestOpenTime,
                                                long previousOldestOpenTime,
                                                int previousWindowSize,
                                                @NonNull String symbol,
                                                @NonNull IntervalSelection interval) throws Exception;

        void postToMainThread(@NonNull Runnable action);

        boolean shouldIgnoreRequestResult(int requestVersion);

        boolean shouldFollowLatestViewportOnRefresh();

        void setActiveDataKey(@NonNull String key);

        void applyDisplayCandles(@NonNull String key,
                                 @NonNull List<CandleEntry> candles,
                                 boolean keepViewport,
                                 boolean shouldFollowLatest,
                                 boolean updateMemoryCache);

        void persistClosedCandles(@NonNull String key,
                                  @NonNull List<CandleEntry> finalProcessed,
                                  @NonNull String symbol,
                                  @NonNull IntervalSelection interval);

        void startProgressiveGapFill(@NonNull String reqSymbol,
                                     @NonNull IntervalSelection reqInterval,
                                     int current,
                                     @Nullable List<CandleEntry> visibleWindow,
                                     long previousOldestOpenTime,
                                     int previousWindowSize);

        void applyRequestSuccessState(boolean autoRefresh, long requestStartedAtMs);

        void applyRequestFailureState(boolean autoRefresh, @NonNull String message);

        boolean beginLoadMore();

        void notifyLoadMoreFinished();

        void cancelLoadMoreTask();

        void submitLoadMoreTask(@NonNull Runnable action);

        @NonNull
        List<CandleEntry> fetchV2SeriesBefore(@NonNull String symbol,
                                              @NonNull IntervalSelection interval,
                                              int limit,
                                              long endTimeInclusive) throws Exception;

        @NonNull
        List<CandleEntry> aggregateToYear(@Nullable List<CandleEntry> source, @NonNull String symbol);

        boolean shouldIgnoreLoadMoreResult(@NonNull String reqSymbol, @NonNull IntervalSelection reqInterval);

        void applyLoadMoreSuccessState(@NonNull String reqSymbol,
                                       @NonNull IntervalSelection reqInterval,
                                       @NonNull List<CandleEntry> older);

        void finishLoadMoreState();

        int getRestoreWindowLimit();

        int getHistoryPageLimit();

        void applyRealtimeChartTail(@Nullable KlineData latestKline);

        void updateVolumeThresholdOverlay();

        void updateAccountAnnotationsOverlay();

        void updateAbnormalAnnotationsOverlay();

        boolean isChartViewReady();

        boolean isAccountSessionActive();

        @Nullable
        AccountStatsPreloadManager.Cache getLatestAccountCache();

        @Nullable
        AccountSnapshot resolveChartOverlaySnapshot(boolean sessionActive,
                                                   @Nullable AccountStatsPreloadManager.Cache cache);

        boolean isCurrentOverlayBoundToActiveSession();

        void clearAccountAnnotationsOverlay();

        @NonNull
        String buildCurrentCacheKey();

        boolean shouldDeferOverlayUntilPrimaryDisplay(@NonNull String key);

        void replacePendingOverlay(@NonNull String key, @NonNull Runnable action);

        void applyChartOverlaySnapshot(@NonNull AccountSnapshot snapshot,
                                       @Nullable AccountStatsPreloadManager.Cache cache);
    }

    private final Host host;

    MarketChartDataCoordinator(@NonNull Host host) {
        this.host = host;
    }

    // 图表请求入口统一经由协调器，旧 Activity 只保留底层 helper。
    void requestKlines(boolean allowCancelRunning, boolean autoRefresh) {
        requestKlinesCore(allowCancelRunning, autoRefresh);
    }

    // 历史补页入口统一经由协调器，保持和主请求同一层调度。
    void requestMoreHistory(long beforeOpenTime) {
        requestMoreHistoryCore(beforeOpenTime);
    }

    // 统一订阅监控服务的实时 K 线尾部，让宿主只负责具体落图。
    void observeRealtimeDisplayKlines() {
        MonitorRepository repository = host.getMonitorRepository();
        if (repository == null) {
            return;
        }
        repository.getDisplayKlines().observe(host.getLifecycleOwner(), snapshot -> {
            if (snapshot == null || snapshot.isEmpty()) {
                return;
            }
            host.applyRealtimeChartTail(snapshot.get(host.getSelectedSymbol()));
        });
    }

    // 图表上所有叠加层刷新统一从这里收口。
    void refreshChartOverlays() {
        host.updateVolumeThresholdOverlay();
        host.updateAccountAnnotationsOverlay();
        host.updateAbnormalAnnotationsOverlay();
    }

    // 图表页恢复时优先直接消费最近账户缓存，必要时再清空叠加层。
    void restoreChartOverlayFromLatestCacheOrEmpty() {
        if (!host.isChartViewReady()) {
            return;
        }
        boolean sessionActive = host.isAccountSessionActive();
        AccountStatsPreloadManager.Cache cache = host.getLatestAccountCache();
        AccountSnapshot snapshot = host.resolveChartOverlaySnapshot(sessionActive, cache);
        if (!sessionActive) {
            host.clearAccountAnnotationsOverlay();
            return;
        }
        if (snapshot == null) {
            if (!host.isCurrentOverlayBoundToActiveSession()) {
                host.clearAccountAnnotationsOverlay();
            }
            return;
        }
        String key = host.buildCurrentCacheKey();
        if (host.shouldDeferOverlayUntilPrimaryDisplay(key)) {
            host.replacePendingOverlay(key, () -> host.applyChartOverlaySnapshot(snapshot, cache));
            return;
        }
        host.applyChartOverlaySnapshot(snapshot, cache);
    }

    // 主请求编排从旧 Activity 下沉到协调器。
    private void requestKlinesCore(boolean allowCancelRunning, boolean autoRefresh) {
        final String traceSymbol = host.getSelectedSymbol();
        final IntervalSelection reqInterval = host.getSelectedInterval();
        final String key = host.buildCacheKey(traceSymbol, reqInterval);
        if (!host.cancelRunningRequestIfNeeded(allowCancelRunning, autoRefresh)) {
            return;
        }

        boolean shouldWarmDisplay = ChartWarmDisplayPolicyHelper.shouldWarmDisplay(
                host.getLoadedCandles().isEmpty(),
                !key.equals(host.getActiveDataKey())
        );
        List<CandleEntry> memoryCached = host.getCachedCandles(key);
        if (!MarketChartDisplayHelper.isSeriesCompatibleForInterval(reqInterval.getKey(), memoryCached)) {
            memoryCached = null;
        }
        if (memoryCached == null || memoryCached.isEmpty()) {
            host.schedulePersistedCacheRestore(key, shouldWarmDisplay);
        }
        if (shouldWarmDisplay) {
            List<CandleEntry> cached = memoryCached;
            if (cached == null || cached.isEmpty()) {
                cached = host.buildWarmDisplayCandles(traceSymbol, reqInterval);
            }
            if (cached != null && !cached.isEmpty()) {
                host.applyLocalDisplayCandles(key, cached);
            }
        }

        List<CandleEntry> localForPlan = key.equals(host.getActiveDataKey())
                ? host.getLoadedCandles()
                : memoryCached;
        if (!MarketChartDisplayHelper.isSeriesCompatibleForInterval(reqInterval.getKey(), localForPlan)) {
            localForPlan = null;
        }
        long latestVisibleTime = host.resolveLatestVisibleCandleTime(localForPlan);
        MarketChartRefreshHelper.SyncPlan refreshPlan = MarketChartRefreshHelper.resolvePlan(
                localForPlan,
                reqInterval.getLimit(),
                host.getRestoreWindowLimit(),
                System.currentTimeMillis(),
                latestVisibleTime,
                host.intervalToMs(reqInterval.getKey()),
                reqInterval.isYearAggregate(),
                host.hasRealtimeTailSourceForChart()
        );
        if (refreshPlan.mode == MarketChartRefreshHelper.SyncMode.SKIP) {
            host.applyRequestSkipState(refreshPlan);
            return;
        }

        final int current = host.nextRequestVersion();
        final String traceIntervalKey = reqInterval.getKey();
        final List<CandleEntry> loadedCandles = host.getLoadedCandles();
        final long previousLatestOpenTime = loadedCandles.isEmpty()
                ? -1L
                : loadedCandles.get(loadedCandles.size() - 1).getOpenTime();
        final long previousOldestOpenTime = loadedCandles.isEmpty()
                ? -1L
                : loadedCandles.get(0).getOpenTime();
        final int previousWindowSize = loadedCandles.size();
        final long requestStartedAtMs = SystemClock.elapsedRealtime();
        host.applyRequestStartState(autoRefresh);
        host.setRunningTaskStartMs(System.currentTimeMillis());
        host.cancelProgressiveGapFillTask();
        final List<CandleEntry> refreshSeed = localForPlan == null ? new ArrayList<>() : new ArrayList<>(localForPlan);
        ChainLatencyTracer.markChartPullPhase(
                traceSymbol,
                traceIntervalKey,
                current,
                "start",
                0L,
                refreshSeed.size()
        );
        host.submitRunningTask(() -> {
            try {
                long loadStartedAtMs = SystemClock.elapsedRealtime();
                List<CandleEntry> processed = host.loadCandlesForRequest(
                        refreshPlan,
                        refreshSeed,
                        previousLatestOpenTime,
                        previousOldestOpenTime,
                        previousWindowSize,
                        traceSymbol,
                        reqInterval
                );
                long loadDurationMs = Math.max(0L, SystemClock.elapsedRealtime() - loadStartedAtMs);
                ChainLatencyTracer.markChartPullPhase(
                        traceSymbol,
                        traceIntervalKey,
                        current,
                        "load_done",
                        loadDurationMs,
                        processed.size()
                );
                final List<CandleEntry> finalProcessed = processed;
                if (finalProcessed.isEmpty()) {
                    throw new IllegalStateException("币安未返回可用K线数据");
                }
                host.postToMainThread(() -> {
                    if (host.shouldIgnoreRequestResult(current)) {
                        return;
                    }
                    boolean followingLatestViewport = host.shouldFollowLatestViewportOnRefresh();
                    MarketChartDisplayHelper.DisplayUpdate displayUpdate = MarketChartDisplayHelper.buildDisplayUpdate(
                            traceSymbol,
                            reqInterval.getKey(),
                            refreshSeed,
                            finalProcessed,
                            reqInterval.getLimit(),
                            host.getLoadedCandles(),
                            autoRefresh,
                            followingLatestViewport
                    );
                    host.setActiveDataKey(key);
                    if (displayUpdate.candlesChanged) {
                        host.applyDisplayCandles(key, displayUpdate.toDisplay, autoRefresh, displayUpdate.shouldFollowLatest, true);
                        host.persistClosedCandles(key, finalProcessed, traceSymbol, reqInterval);
                    }
                    host.startProgressiveGapFill(
                            traceSymbol,
                            reqInterval,
                            current,
                            displayUpdate.toDisplay,
                            previousOldestOpenTime,
                            previousWindowSize
                    );
                    host.applyRequestSuccessState(autoRefresh, requestStartedAtMs);
                    long totalDurationMs = Math.max(0L, SystemClock.elapsedRealtime() - requestStartedAtMs);
                    ChainLatencyTracer.markChartPullPhase(
                            traceSymbol,
                            traceIntervalKey,
                            current,
                            "ui_applied",
                            totalDurationMs,
                            finalProcessed.size()
                    );
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                final String message = e.getMessage() == null ? "K线请求失败" : e.getMessage();
                host.postToMainThread(() -> {
                    if (host.shouldIgnoreRequestResult(current)) {
                        return;
                    }
                    host.applyRequestFailureState(autoRefresh, message);
                });
            }
        });
    }

    // 历史补页编排从旧 Activity 下沉到协调器。
    private void requestMoreHistoryCore(long beforeOpenTime) {
        if (!host.beginLoadMore()) {
            host.notifyLoadMoreFinished();
            return;
        }
        final String reqSymbol = host.getSelectedSymbol();
        final IntervalSelection reqInterval = host.getSelectedInterval();
        host.cancelProgressiveGapFillTask();
        host.cancelLoadMoreTask();
        host.submitLoadMoreTask(() -> {
            try {
                List<CandleEntry> fetched = host.fetchV2SeriesBefore(
                        reqSymbol,
                        reqInterval,
                        host.getHistoryPageLimit(),
                        beforeOpenTime - 1L
                );
                List<CandleEntry> processed = reqInterval.isYearAggregate()
                        ? host.aggregateToYear(fetched, reqSymbol)
                        : fetched;
                if (processed.isEmpty()) {
                    host.postToMainThread(host::finishLoadMoreState);
                    return;
                }
                host.postToMainThread(() -> {
                    try {
                        if (host.shouldIgnoreLoadMoreResult(reqSymbol, reqInterval)) {
                            return;
                        }
                        List<CandleEntry> older = ChartHistoryPagingHelper.resolveOlderCandles(host.getLoadedCandles(), processed);
                        if (older.isEmpty()) {
                            return;
                        }
                        host.applyLoadMoreSuccessState(reqSymbol, reqInterval, older);
                    } finally {
                        host.finishLoadMoreState();
                    }
                });
            } catch (Exception ignored) {
                host.postToMainThread(host::finishLoadMoreState);
            }
        });
    }
}

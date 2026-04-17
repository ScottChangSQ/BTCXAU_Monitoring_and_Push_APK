/*
 * 监控服务悬浮窗协调器，统一处理配置应用、刷新节流、快照拼装和最终更新。
 */
package com.binance.monitor.service;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.runtime.AppForegroundTracker;
import com.binance.monitor.runtime.ConnectionStage;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStore;
import com.binance.monitor.runtime.state.model.FloatingCardRuntimeModel;
import com.binance.monitor.ui.floating.FloatingPositionAggregator;
import com.binance.monitor.ui.floating.FloatingSymbolCardData;
import com.binance.monitor.ui.floating.FloatingWindowManager;
import com.binance.monitor.ui.floating.FloatingWindowSnapshot;
import com.binance.monitor.util.ChainLatencyTracer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class MonitorFloatingCoordinator {

    interface DataSource {
        @Nullable
        AccountStatsPreloadManager.Cache getLatestAccountCache();

        @NonNull
        ConnectionStage getCurrentConnectionStage();

        @NonNull
        String getCurrentConnectionStatus();
    }

    private final Handler mainHandler;
    private final FloatingWindowManager floatingWindowManager;
    private final ConfigManager configManager;
    private final MonitorRepository repository;
    private final DataSource dataSource;
    private final UnifiedRuntimeSnapshotStore runtimeSnapshotStore;
    private boolean floatingRefreshScheduled;
    private long lastFloatingRefreshAt;
    private final Runnable floatingRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            floatingRefreshScheduled = false;
            lastFloatingRefreshAt = System.currentTimeMillis();
            refreshFloatingWindow();
        }
    };

    // 创建悬浮窗协调器。
    MonitorFloatingCoordinator(@NonNull Handler mainHandler,
                               @Nullable FloatingWindowManager floatingWindowManager,
                               @Nullable ConfigManager configManager,
                               @Nullable MonitorRepository repository,
                               @NonNull DataSource dataSource) {
        this.mainHandler = mainHandler;
        this.floatingWindowManager = floatingWindowManager;
        this.configManager = configManager;
        this.repository = repository;
        this.dataSource = dataSource;
        this.runtimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();
    }

    // 应用悬浮窗显示偏好，并触发一次立即刷新。
    void applyPreferences() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::applyPreferences);
            return;
        }
        if (floatingWindowManager == null || configManager == null) {
            return;
        }
        floatingWindowManager.applyPreferences(
                configManager.isFloatingEnabled(),
                configManager.getFloatingAlpha(),
                configManager.isShowBtc(),
                configManager.isShowXau()
        );
        requestRefresh(true);
    }

    // 统一收口悬浮窗刷新节流逻辑。
    void requestRefresh(boolean immediate) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> requestRefresh(immediate));
            return;
        }
        if (floatingWindowManager == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastFloatingRefreshAt;
        long throttleMs = resolveFloatingRefreshThrottleMs();
        if (immediate || elapsed >= throttleMs) {
            mainHandler.removeCallbacks(floatingRefreshRunnable);
            floatingRefreshScheduled = false;
            lastFloatingRefreshAt = now;
            refreshFloatingWindow();
            return;
        }
        if (floatingRefreshScheduled) {
            return;
        }
        long delay = Math.max(120L, throttleMs - elapsed);
        floatingRefreshScheduled = true;
        mainHandler.postDelayed(floatingRefreshRunnable, delay);
    }

    // 销毁时取消悬浮窗排队刷新并隐藏窗口。
    void onDestroy() {
        mainHandler.removeCallbacks(floatingRefreshRunnable);
        floatingRefreshScheduled = false;
        lastFloatingRefreshAt = 0L;
        if (floatingWindowManager != null) {
            floatingWindowManager.destroy();
        }
    }

    // 立即拼装并更新悬浮窗快照。
    private void refreshFloatingWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::refreshFloatingWindow);
            return;
        }
        if (floatingWindowManager == null) {
            return;
        }
        FloatingWindowSnapshot snapshot = buildFloatingSnapshot();
        for (FloatingSymbolCardData card : snapshot.getCards()) {
            if (card == null) {
                continue;
            }
            ChainLatencyTracer.markFloatingUpdate(card.getCode(), card.getUpdatedAt());
        }
        floatingWindowManager.update(snapshot);
    }

    // 组装一份统一悬浮窗快照，确保所有字段在同一次 UI 刷新中一起变化。
    private FloatingWindowSnapshot buildFloatingSnapshot() {
        AccountStatsPreloadManager.Cache cache = dataSource.getLatestAccountCache();
        List<FloatingSymbolCardData> cards = buildFloatingCards(cache);
        return new FloatingWindowSnapshot(
                dataSource.getCurrentConnectionStage(),
                dataSource.getCurrentConnectionStatus(),
                resolveFloatingUpdatedAt(cards),
                cards
        );
    }

    @NonNull
    private List<FloatingSymbolCardData> buildFloatingCards(@Nullable AccountStatsPreloadManager.Cache cache) {
        boolean showBtc = configManager != null && configManager.isShowBtc();
        boolean showXau = configManager != null && configManager.isShowXau();
        List<String> visibleSymbols = FloatingPositionAggregator.filterMarketSymbols(
                Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                showBtc,
                showXau
        );
        if (cache != null && cache.getSnapshot() != null) {
            List<FloatingCardRuntimeModel> runtimeCards = new ArrayList<>();
            for (String symbol : visibleSymbols) {
                runtimeCards.add(runtimeSnapshotStore.selectFloatingCard(symbol));
            }
            return FloatingPositionAggregator.buildSymbolCardsFromRuntime(
                    visibleSymbols,
                    runtimeCards,
                    repository == null ? null : repository.getDisplayOverviewKlineSnapshot(),
                    repository == null ? null : repository.getDisplayPriceSnapshot()
            );
        }
        List<com.binance.monitor.domain.account.model.PositionItem> positions = copyCachePositions(cache);
        return FloatingPositionAggregator.buildSymbolCards(
                positions,
                repository == null ? null : repository.getDisplayOverviewKlineSnapshot(),
                repository == null ? null : repository.getDisplayPriceSnapshot(),
                showBtc,
                showXau
        );
    }

    // 复制账户正式缓存里的持仓列表，避免悬浮窗直接持有可变对象引用。
    private List<com.binance.monitor.domain.account.model.PositionItem> copyCachePositions(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null || cache.getSnapshot() == null || cache.getSnapshot().getPositions() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(cache.getSnapshot().getPositions());
    }

    // 从产品卡片里挑出本轮悬浮窗的统一刷新时间。
    private long resolveFloatingUpdatedAt(@Nullable List<FloatingSymbolCardData> cards) {
        long updatedAt = 0L;
        if (cards == null || cards.isEmpty()) {
            return updatedAt;
        }
        for (FloatingSymbolCardData card : cards) {
            if (card != null) {
                updatedAt = Math.max(updatedAt, card.getUpdatedAt());
            }
        }
        return updatedAt;
    }

    // 后台时放慢悬浮窗刷新，减少不必要的主线程绘制。
    private long resolveFloatingRefreshThrottleMs() {
        return MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(
                AppForegroundTracker.getInstance().isForeground()
        );
    }
}

/*
 * 行情持仓页运行时，统一承接页面宿主回调，供旧 Activity 与主壳 Fragment 复用。
 * 当前先收口页面宿主编排，后续再继续把更重的图表业务状态迁入这里。
 */
package com.binance.monitor.ui.chart;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ActivityMarketChartBinding;
import com.binance.monitor.service.MonitorServiceController;

public final class MarketChartPageRuntime implements MarketChartPageHostDelegate.Owner {
    public static final String INDICATOR_VOLUME = "volume";
    public static final String INDICATOR_MACD = "macd";
    public static final String INDICATOR_STOCH_RSI = "stoch_rsi";
    public static final String INDICATOR_BOLL = "boll";
    public static final String INDICATOR_MA = "ma";
    public static final String INDICATOR_EMA = "ema";
    public static final String INDICATOR_SRA = "sra";
    public static final String INDICATOR_AVL = "avl";
    public static final String INDICATOR_RSI = "rsi";
    public static final String INDICATOR_KDJ = "kdj";

    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean hasEnteredScreen;
    private boolean accountOverlayRefreshPending;
    private long nextAutoRefreshAtMs;
    private final Runnable chartOverlayRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            accountOverlayRefreshPending = false;
            host.updateAccountAnnotationsOverlay();
        }
    };
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (MarketChartRevisionRefreshPolicy.shouldRequestKlines(
                    host.getCurrentMarketWindowSignature(),
                    host.getAppliedMarketWindowSignature(),
                    host.getAppliedMarketWindowUpdatedAt(),
                    System.currentTimeMillis(),
                    host.getAutoRefreshStaleAfterMs())) {
                host.requestAutoRefreshKlines();
            }
            scheduleNextAutoRefresh();
        }
    };
    private final Runnable refreshCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            refreshRefreshCountdownDisplay();
            if (nextAutoRefreshAtMs > 0L) {
                mainHandler.postDelayed(this, 1_000L);
            }
        }
    };

    public MarketChartPageRuntime(@NonNull Host host) {
        this.host = host;
    }

    @NonNull
    @Override
    public AppCompatActivity requireActivity() {
        return host.requireActivity();
    }

    @Override
    public void bindPageContent(@NonNull ActivityMarketChartBinding binding) {
        host.bindPageContent(binding);
    }

    @Override
    public void applyPagePalette() {
        host.applyPagePalette();
    }

    @Override
    public void applyPrivacyMaskState() {
        host.applyPrivacyMaskState();
    }

    @Override
    public void attachAccountCacheListener() {
        host.attachAccountCacheListener();
    }

    @Override
    public void restoreChartOverlayFromLatestCache() {
        host.restoreChartOverlayFromLatestCache();
    }

    @Override
    public void consumePendingTradeActionIfNeeded() {
        host.consumePendingTradeActionIfNeeded();
    }

    @Override
    public boolean shouldRequestKlinesOnResume() {
        return host.shouldRequestKlinesOnResume();
    }

    @Override
    public void requestKlines() {
        host.requestKlines();
    }

    public void requestColdStartKlines() {
        host.requestColdStartKlines();
    }

    public void requestResumeKlines() {
        host.requestResumeKlines();
    }

    public void requestSelectionChangeKlines() {
        host.requestSelectionChangeKlines();
    }

    @Override
    public void refreshChartOverlays() {
        host.refreshChartOverlays();
    }

    @Override
    public void restorePersistedCache() {
        host.restorePersistedCache();
    }

    @Override
    public void beginChartBootstrap() {
        host.beginChartBootstrap();
    }

    @Override
    public void updateStateCount() {
        host.updateStateCount();
    }

    @Override
    public void cancelChartBackgroundTasks() {
        host.cancelChartBackgroundTasks();
    }

    @Override
    public void cancelTradeTasks() {
        host.cancelTradeTasks();
    }

    @Override
    public void detachAccountCacheListener() {
        host.detachAccountCacheListener();
    }

    @Override
    public void clearStartupPrimaryDrawObserver() {
        host.clearStartupPrimaryDrawObserver();
    }

    @Override
    public void shutdownIoExecutor() {
        host.shutdownIoExecutor();
    }

    @Override
    public void onColdStart() {
        host.applyPagePalette();
        host.applyPrivacyMaskState();
        host.beginChartBootstrap();
        host.restorePersistedCache();
        host.updateStateCount();
        refreshRefreshCountdownDisplay();
    }

    @Override
    public void onPageShown() {
        MonitorServiceController.ensureStarted(host.requireActivity());
        host.applyPagePalette();
        host.attachAccountCacheListener();
        host.restoreChartOverlayFromLatestCache();
        host.consumePendingTradeActionIfNeeded();
        host.applyPrivacyMaskState();
        enterChartScreen(!hasEnteredScreen);
        hasEnteredScreen = true;
    }

    @Override
    public void onPageHidden() {
        stopAutoRefresh();
        host.cancelChartBackgroundTasks();
        host.cancelTradeTasks();
        removeOverlayRefreshCallbacks();
        host.detachAccountCacheListener();
    }

    @Override
    public void onPageDestroyed() {
        stopAutoRefresh();
        removeOverlayRefreshCallbacks();
        host.clearStartupPrimaryDrawObserver();
        host.detachAccountCacheListener();
        host.shutdownIoExecutor();
    }

    // 把账户侧高频刷新折叠成短延迟任务，避免持仓区连续重建导致卡顿。
    public void scheduleChartOverlayRefresh() {
        if (accountOverlayRefreshPending) {
            return;
        }
        accountOverlayRefreshPending = true;
        mainHandler.removeCallbacks(chartOverlayRefreshRunnable);
        mainHandler.postDelayed(chartOverlayRefreshRunnable, host.getChartOverlayRefreshDebounceMs());
    }

    // 页面离场时统一移除图上叠加刷新回调，避免离屏后仍触发图层重算。
    private void removeOverlayRefreshCallbacks() {
        mainHandler.removeCallbacks(chartOverlayRefreshRunnable);
        accountOverlayRefreshPending = false;
    }

    // 统一处理图表页进入前台：冷启动只发起一次初始请求，普通切页返回只恢复消费节奏。
    private void enterChartScreen(boolean coldStart) {
        if (coldStart) {
            host.requestColdStartKlines();
        } else if (host.shouldRequestKlinesOnResume()) {
            host.requestResumeKlines();
        }
        host.refreshChartOverlays();
        startAutoRefresh();
    }

    // 重启自动刷新节奏，并统一刷新倒计时展示。
    public void startAutoRefresh() {
        stopAutoRefresh();
        scheduleNextAutoRefresh();
        if (host.shouldShowRefreshCountdown()) {
            mainHandler.post(refreshCountdownRunnable);
        } else {
            refreshRefreshCountdownDisplay();
        }
    }

    // 页面离开或切换窗口时清理自动刷新调度，避免离屏仍继续排队请求。
    public void stopAutoRefresh() {
        mainHandler.removeCallbacks(autoRefreshRunnable);
        mainHandler.removeCallbacks(refreshCountdownRunnable);
        nextAutoRefreshAtMs = 0L;
        refreshRefreshCountdownDisplay();
    }

    // 对外暴露“立即重排下一次自动刷新”，供切产品、切周期后复用同一调度入口。
    public void scheduleNextAutoRefresh() {
        long delayMs = host.resolveAutoRefreshDelayMs();
        nextAutoRefreshAtMs = System.currentTimeMillis() + delayMs;
        mainHandler.removeCallbacks(autoRefreshRunnable);
        mainHandler.postDelayed(autoRefreshRunnable, delayMs);
        mainHandler.removeCallbacks(refreshCountdownRunnable);
        if (host.shouldShowRefreshCountdown()) {
            mainHandler.post(refreshCountdownRunnable);
        } else {
            refreshRefreshCountdownDisplay();
        }
    }

    // 切产品、切周期或外部指定 symbol 后，统一走“重拉取 + 重排自动刷新”这一条页面编排。
    public void requestChartSelectionReload() {
        host.requestSelectionChangeKlines();
        scheduleNextAutoRefresh();
    }

    // 品种切换统一走运行时入口，避免旧 Activity 自己拼状态切换和刷新编排。
    public void requestSymbolSelection(@NonNull String symbol) {
        if (!host.canApplySelectedSymbol(symbol)) {
            return;
        }
        host.commitSelectedSymbol(symbol);
        host.invalidateChartDisplayContext();
        requestChartSelectionReload();
    }

    // 周期切换统一走运行时入口，避免旧 Activity 自己拼状态切换和刷新编排。
    public void requestIntervalSelection(@NonNull String intervalKey) {
        if (!host.canApplySelectedInterval(intervalKey)) {
            return;
        }
        host.commitSelectedInterval(intervalKey);
        host.invalidateChartDisplayContext();
        requestChartSelectionReload();
    }

    // 图表叠加层显隐开关统一走运行时入口，便于后续 Fragment 直接复用。
    public void toggleHistoryTradeVisibility() {
        host.toggleHistoryTradeVisibilityState();
        host.applyPrivacyMaskState();
    }

    // 图上持仓标注显隐开关统一走运行时入口，避免旧 Activity 自己拼点击编排。
    public void togglePositionOverlayVisibility() {
        host.togglePositionOverlayVisibilityState();
        host.applyPrivacyMaskState();
    }

    // 指标显隐切换统一走运行时入口，避免旧 Activity 自己拼状态切换和图层刷新。
    public void toggleIndicator(@NonNull String indicatorKey) {
        host.toggleIndicatorState(indicatorKey);
        host.notifyIndicatorSelectionChanged();
    }

    // 指标参数修改后统一走同一条刷新入口，避免旧 Activity 自己重复收尾。
    public void notifyIndicatorSelectionChanged() {
        host.notifyIndicatorSelectionChanged();
    }

    // 当请求延迟或缓存新鲜度变化时，复用当前调度状态刷新右上角元信息。
    public void refreshRefreshCountdownDisplay() {
        host.renderRefreshCountdown(nextAutoRefreshAtMs);
    }

    @Override
    public boolean isEmbeddedInHostShell() {
        return host.isEmbeddedInHostShell();
    }

    @Override
    public void openMarketMonitor() {
        host.openMarketMonitor();
    }

    @Override
    public void openAccountStats() {
        host.openAccountStats();
    }

    @Override
    public void openAccountPosition() {
        host.openAccountPosition();
    }

    @Override
    public void openSettings() {
        host.openSettings();
    }

    public interface Host {
        @NonNull
        AppCompatActivity requireActivity();

        void bindPageContent(@NonNull ActivityMarketChartBinding binding);

        void applyPagePalette();

        void applyPrivacyMaskState();

        void attachAccountCacheListener();

        void restoreChartOverlayFromLatestCache();

        void consumePendingTradeActionIfNeeded();

        boolean shouldRequestKlinesOnResume();

        void requestKlines();

        void requestColdStartKlines();

        void requestResumeKlines();

        void requestSelectionChangeKlines();

        void refreshChartOverlays();

        void beginChartBootstrap();

        void restorePersistedCache();

        void updateStateCount();

        void cancelChartBackgroundTasks();

        void cancelTradeTasks();

        void detachAccountCacheListener();

        void clearStartupPrimaryDrawObserver();

        void shutdownIoExecutor();

        void updateAccountAnnotationsOverlay();

        long getChartOverlayRefreshDebounceMs();

        void requestAutoRefreshKlines();

        @NonNull
        String getCurrentMarketWindowSignature();

        @NonNull
        String getSelectedChartSymbol();

        @NonNull
        String getAppliedMarketWindowSignature();

        long getAppliedMarketWindowUpdatedAt();

        long getAutoRefreshStaleAfterMs();

        boolean shouldShowRefreshCountdown();

        long resolveAutoRefreshDelayMs();

        void renderRefreshCountdown(long nextAutoRefreshAtMs);

        void toggleHistoryTradeVisibilityState();

        void togglePositionOverlayVisibilityState();

        void toggleIndicatorState(@NonNull String indicatorKey);

        void notifyIndicatorSelectionChanged();

        boolean canApplySelectedSymbol(@NonNull String symbol);

        void commitSelectedSymbol(@NonNull String symbol);

        boolean canApplySelectedInterval(@NonNull String intervalKey);

        void commitSelectedInterval(@NonNull String intervalKey);

        void invalidateChartDisplayContext();

        boolean isEmbeddedInHostShell();

        void openMarketMonitor();

        void openAccountStats();

        void openAccountPosition();

        void openSettings();
    }
}

/*
 * 账户统计页运行时，统一承接页面宿主回调，供旧 Activity 与主壳 Fragment 复用。
 * 当前先收口页面宿主编排，后续再继续把更重的统计页状态迁入这里。
 */
package com.binance.monitor.ui.account;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ContentAccountStatsBinding;
import com.binance.monitor.service.MonitorServiceController;

public final class AccountStatsPageRuntime implements AccountStatsPageHostDelegate.Owner {

    private final Host host;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private boolean snapshotLoopEnabled;
    private long nextRefreshAtMs;
    private long scheduledRefreshDelayMs;
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            clearScheduledRefresh();
            host.requestScheduledSnapshot();
        }
    };

    public AccountStatsPageRuntime(@NonNull Host host) {
        this.host = host;
    }

    @NonNull
    @Override
    public AppCompatActivity requireActivity() {
        return host.requireActivity();
    }

    @Override
    public void bindPageContent(@NonNull ContentAccountStatsBinding binding) {
        host.bindPageContent(binding);
    }

    @Override
    public void placeCurveSectionToBottom() {
        host.placeCurveSectionToBottom();
    }

    @Override
    public void bindLocalMeta() {
        host.bindLocalMeta();
    }

    @Override
    public void onColdStart() {
        host.placeCurveSectionToBottom();
        host.bindLocalMeta();
        host.applyPagePalette();
        host.applyPrivacyMaskState();
        enableSnapshotLoop();
        host.enterAccountScreen(true);
    }

    @Override
    public void onPageShown() {
        MonitorServiceController.ensureStarted(host.requireActivity());
        host.attachForegroundRefresh();
        host.applyPagePalette();
        host.applyPrivacyMaskState();
        enableSnapshotLoop();
        host.enterAccountScreen(false);
        host.openLoginDialogIfRequested();
    }

    @Override
    public void onPageHidden() {
        disableSnapshotLoop();
        clearScheduledRefresh();
        host.dismissActiveLoginDialog();
        host.clearTransientUiCallbacks();
        host.detachForegroundRefresh();
        host.persistUiState();
    }

    @Override
    public void onPageDestroyed() {
        disableSnapshotLoop();
        clearScheduledRefresh();
        host.dismissActiveLoginDialog();
        host.clearDestroyCallbacks();
        host.shutdownExecutors();
    }

    @Override
    public void attachForegroundRefresh() {
        host.attachForegroundRefresh();
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
    public void enterAccountScreen(boolean coldStart) {
        host.enterAccountScreen(coldStart);
    }

    @Override
    public void openLoginDialogIfRequested() {
        host.openLoginDialogIfRequested();
    }

    @Override
    public void dismissActiveLoginDialog() {
        host.dismissActiveLoginDialog();
    }

    @Override
    public void clearTransientUiCallbacks() {
        host.clearTransientUiCallbacks();
    }

    @Override
    public void detachForegroundRefresh() {
        host.detachForegroundRefresh();
    }

    @Override
    public void persistUiState() {
        host.persistUiState();
    }

    @Override
    public void clearDestroyCallbacks() {
        host.clearDestroyCallbacks();
    }

    @Override
    public void shutdownExecutors() {
        host.shutdownExecutors();
    }

    @Override
    public void requestScheduledSnapshot() {
        host.requestScheduledSnapshot();
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
    public void openMarketChart() {
        host.openMarketChart();
    }

    @Override
    public void openAccountPosition() {
        host.openAccountPosition();
    }

    @Override
    public void openSettings() {
        host.openSettings();
    }

    // 运行时自己持有页面活跃态，避免旧 Activity 再保留页面循环状态。
    public boolean shouldKeepRefreshLoop(boolean userLoggedIn, boolean finishing, boolean destroyed) {
        return snapshotLoopEnabled && userLoggedIn && !finishing && !destroyed;
    }

    private void enableSnapshotLoop() {
        snapshotLoopEnabled = true;
    }

    private void disableSnapshotLoop() {
        snapshotLoopEnabled = false;
    }

    // 由运行时统一承接下一次账户快照调度，避免旧 Activity 再持有页面刷新回调。
    public void scheduleNextSnapshot(long delayMs) {
        long safeDelay = AccountRefreshMetaHelper.normalizeDelayMs(delayMs);
        refreshHandler.removeCallbacks(refreshRunnable);
        scheduledRefreshDelayMs = safeDelay;
        nextRefreshAtMs = System.currentTimeMillis() + safeDelay;
        refreshHandler.postDelayed(refreshRunnable, safeDelay);
    }

    // 清理已经排队的下一次账户刷新，保证页面离场后不再误触发请求。
    public void clearScheduledRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        nextRefreshAtMs = 0L;
        scheduledRefreshDelayMs = 0L;
    }

    public interface Host {
        @NonNull
        AppCompatActivity requireActivity();

        void bindPageContent(@NonNull ContentAccountStatsBinding binding);

        void placeCurveSectionToBottom();

        void bindLocalMeta();

        void attachForegroundRefresh();

        void applyPagePalette();

        void applyPrivacyMaskState();

        void enterAccountScreen(boolean coldStart);

        void openLoginDialogIfRequested();

        void dismissActiveLoginDialog();

        void clearTransientUiCallbacks();

        void detachForegroundRefresh();

        void persistUiState();

        void clearDestroyCallbacks();

        void shutdownExecutors();

        void requestScheduledSnapshot();

        boolean isEmbeddedInHostShell();

        void openMarketMonitor();

        void openMarketChart();

        void openAccountPosition();

        void openSettings();
    }
}

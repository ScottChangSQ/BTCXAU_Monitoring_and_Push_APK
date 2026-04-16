/*
 * 行情持仓页宿主委托，统一适配 Activity/Fragment 的页面宿主能力。
 * 当前先收口 PageController.Host 实现，后续 Fragment 真正承接业务时继续补齐 Owner 能力。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ActivityMarketChartBinding;

public final class MarketChartPageHostDelegate implements MarketChartPageController.Host {

    private final Owner owner;

    public MarketChartPageHostDelegate(@NonNull Owner owner) {
        this.owner = owner;
    }

    @NonNull
    @Override
    public AppCompatActivity requireActivity() {
        return owner.requireActivity();
    }

    @Override
    public void bindPageContent(@NonNull ActivityMarketChartBinding binding) {
        owner.bindPageContent(binding);
    }

    @Override
    public void applyPagePalette() {
        owner.applyPagePalette();
    }

    @Override
    public void applyPrivacyMaskState() {
        owner.applyPrivacyMaskState();
    }

    @Override
    public void attachAccountCacheListener() {
        owner.attachAccountCacheListener();
    }

    @Override
    public void restoreChartOverlayFromLatestCacheOrEmpty() {
        owner.restoreChartOverlayFromLatestCacheOrEmpty();
    }

    @Override
    public void consumePendingTradeActionIfNeeded() {
        owner.consumePendingTradeActionIfNeeded();
    }

    @Override
    public boolean shouldRequestKlinesOnResume() {
        return owner.shouldRequestKlinesOnResume();
    }

    @Override
    public void requestKlines() {
        owner.requestKlines();
    }

    @Override
    public void refreshChartOverlays() {
        owner.refreshChartOverlays();
    }

    @Override
    public void restorePersistedCache() {
        owner.restorePersistedCache();
    }

    @Override
    public void updateStateCount() {
        owner.updateStateCount();
    }

    @Override
    public void cancelChartBackgroundTasks() {
        owner.cancelChartBackgroundTasks();
    }

    @Override
    public void cancelTradeTasks() {
        owner.cancelTradeTasks();
    }

    @Override
    public void detachAccountCacheListener() {
        owner.detachAccountCacheListener();
    }

    @Override
    public void clearStartupPrimaryDrawObserver() {
        owner.clearStartupPrimaryDrawObserver();
    }

    @Override
    public void shutdownIoExecutor() {
        owner.shutdownIoExecutor();
    }

    @Override
    public void onColdStart() {
        owner.onColdStart();
    }

    @Override
    public void onPageShown() {
        owner.onPageShown();
    }

    @Override
    public void onPageHidden() {
        owner.onPageHidden();
    }

    @Override
    public void onPageDestroyed() {
        owner.onPageDestroyed();
    }

    @Override
    public boolean isEmbeddedInHostShell() {
        return owner.isEmbeddedInHostShell();
    }

    @Override
    public void openMarketMonitor() {
        owner.openMarketMonitor();
    }

    @Override
    public void openAccountStats() {
        owner.openAccountStats();
    }

    @Override
    public void openAccountPosition() {
        owner.openAccountPosition();
    }

    @Override
    public void openSettings() {
        owner.openSettings();
    }

    public interface Owner {
        @NonNull
        AppCompatActivity requireActivity();

        void bindPageContent(@NonNull ActivityMarketChartBinding binding);

        void applyPagePalette();

        void applyPrivacyMaskState();

        void attachAccountCacheListener();

        void restoreChartOverlayFromLatestCacheOrEmpty();

        void consumePendingTradeActionIfNeeded();

        boolean shouldRequestKlinesOnResume();

        void requestKlines();

        void refreshChartOverlays();

        void restorePersistedCache();

        void updateStateCount();

        void cancelChartBackgroundTasks();

        void cancelTradeTasks();

        void detachAccountCacheListener();

        void clearStartupPrimaryDrawObserver();

        void shutdownIoExecutor();

        void onColdStart();

        void onPageShown();

        void onPageHidden();

        void onPageDestroyed();

        boolean isEmbeddedInHostShell();

        void openMarketMonitor();

        void openAccountStats();

        void openAccountPosition();

        void openSettings();
    }
}

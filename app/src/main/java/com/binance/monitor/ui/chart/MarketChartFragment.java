/*
 * 行情持仓主壳页骨架。
 */
package com.binance.monitor.ui.chart;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ActivityMarketChartBinding;
import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;
import com.binance.monitor.ui.host.HostTabPage;
import com.binance.monitor.ui.settings.SettingsActivity;

public class MarketChartFragment extends Fragment implements HostTabPage {
    private MarketChartPageController pageController;
    private MarketChartPageRuntime pageRuntime;
    private MarketChartScreen screen;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_market_chart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View chartContentView = ((ViewGroup) view).getChildAt(0);
        ActivityMarketChartBinding chartBinding = ActivityMarketChartBinding.bind(chartContentView);
        screen = new MarketChartScreen(
                (AppCompatActivity) requireActivity(),
                getViewLifecycleOwner(),
                chartBinding
        );
        screen.initialize();
        screen.onNewIntent(requireActivity().getIntent());
        pageRuntime = new MarketChartPageRuntime(new MarketChartPageRuntime.Host() {
                    @NonNull
                    @Override
                    public AppCompatActivity requireActivity() {
                        return (AppCompatActivity) MarketChartFragment.this.requireActivity();
                    }

                    @Override
                    public void bindPageContent(@NonNull ActivityMarketChartBinding binding) {
                        if (screen != null) {
                            screen.bindPageContent();
                        }
                    }

                    @Override
                    public void applyPagePalette() {
                        if (screen != null) {
                            screen.applyPagePalette();
                        }
                    }

                    @Override
                    public void applyPrivacyMaskState() {
                        if (screen != null) {
                            screen.applyPrivacyMaskState();
                        }
                    }

                    @Override
                    public void attachAccountCacheListener() {
                        if (screen != null) {
                            screen.attachAccountCacheListener();
                        }
                    }

                    @Override
                    public void restoreChartOverlayFromLatestCacheOrEmpty() {
                        if (screen != null) {
                            screen.restoreChartOverlayFromLatestCacheOrEmpty();
                        }
                    }

                    @Override
                    public void consumePendingTradeActionIfNeeded() {
                        if (screen != null) {
                            screen.consumePendingTradeActionIfNeeded();
                        }
                    }

                    @Override
                    public boolean shouldRequestKlinesOnResume() {
                        return screen != null && screen.shouldRequestKlinesOnResume();
                    }

                    @Override
                    public void requestKlines() {
                        if (screen != null) {
                            screen.requestKlines();
                        }
                    }

                    @Override
                    public void refreshChartOverlays() {
                        if (screen != null) {
                            screen.refreshChartOverlays();
                        }
                    }

                    @Override
                    public void restorePersistedCache() {
                        if (screen != null) {
                            screen.restorePersistedCache();
                        }
                    }

                    @Override
                    public void updateStateCount() {
                        if (screen != null) {
                            screen.updateStateCount();
                        }
                    }

                    @Override
                    public void cancelChartBackgroundTasks() {
                        if (screen != null) {
                            screen.cancelChartBackgroundTasks();
                        }
                    }

                    @Override
                    public void cancelTradeTasks() {
                        if (screen != null) {
                            screen.cancelTradeTasks();
                        }
                    }

                    @Override
                    public void detachAccountCacheListener() {
                        if (screen != null) {
                            screen.detachAccountCacheListener();
                        }
                    }

                    @Override
                    public void clearStartupPrimaryDrawObserver() {
                        if (screen != null) {
                            screen.clearStartupPrimaryDrawObserver();
                        }
                    }

                    @Override
                    public void shutdownIoExecutor() {
                        if (screen != null) {
                            screen.shutdownIoExecutor();
                        }
                    }

                    @Override
                    public void updateAccountAnnotationsOverlay() {
                        if (screen != null) {
                            screen.updateAccountAnnotationsOverlay();
                        }
                    }

                    @Override
                    public long getChartOverlayRefreshDebounceMs() {
                        return 120L;
                    }

                    @Override
                    public void requestAutoRefreshKlines() {
                        if (screen != null) {
                            screen.requestAutoRefreshKlines();
                        }
                    }

                    @Override
                    public boolean shouldShowRefreshCountdown() {
                        return screen != null && screen.shouldShowRefreshCountdown();
                    }

                    @Override
                    public long resolveAutoRefreshDelayMs() {
                        return screen == null ? 0L : screen.resolveAutoRefreshDelayMs();
                    }

                    @Override
                    public void renderRefreshCountdown(long nextAutoRefreshAtMs) {
                        if (screen != null) {
                            screen.renderRefreshCountdown(nextAutoRefreshAtMs);
                        }
                    }

                    @Override
                    public void toggleHistoryTradeVisibilityState() {
                        if (screen != null) {
                            screen.toggleHistoryTradeVisibilityState();
                        }
                    }

                    @Override
                    public void togglePositionOverlayVisibilityState() {
                        if (screen != null) {
                            screen.togglePositionOverlayVisibilityState();
                        }
                    }

                    @Override
                    public void toggleIndicatorState(@NonNull String indicatorKey) {
                        if (screen != null) {
                            screen.toggleIndicatorState(indicatorKey);
                        }
                    }

                    @Override
                    public void notifyIndicatorSelectionChanged() {
                        if (screen != null) {
                            screen.notifyIndicatorSelectionChanged();
                        }
                    }

                    @Override
                    public boolean canApplySelectedSymbol(@NonNull String symbol) {
                        return screen != null && screen.canApplySelectedSymbol(symbol);
                    }

                    @Override
                    public void commitSelectedSymbol(@NonNull String symbol) {
                        if (screen != null) {
                            screen.commitSelectedSymbol(symbol);
                        }
                    }

                    @Override
                    public boolean canApplySelectedInterval(@NonNull String intervalKey) {
                        return screen != null && screen.canApplySelectedInterval(intervalKey);
                    }

                    @Override
                    public void commitSelectedInterval(@NonNull String intervalKey) {
                        if (screen != null) {
                            screen.commitSelectedInterval(intervalKey);
                        }
                    }

                    @Override
                    public void invalidateChartDisplayContext() {
                        if (screen != null) {
                            screen.invalidateChartDisplayContext();
                        }
                    }

                    @Override
                    public boolean isEmbeddedInHostShell() {
                        return true;
                    }

                    @Override
                    public void openMarketMonitor() {
                        startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.MARKET_MONITOR));
                    }

                    @Override
                    public void openAccountStats() {
                        startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.ACCOUNT_STATS));
                    }

                    @Override
                    public void openAccountPosition() {
                        startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.ACCOUNT_POSITION));
                    }

                    @Override
                    public void openSettings() {
                        startActivity(new Intent(requireContext(), SettingsActivity.class));
                    }
                });
        screen.attachPageRuntime(pageRuntime);
        pageController = new MarketChartPageController(new MarketChartPageHostDelegate(
                pageRuntime),
                chartBinding,
                new MarketChartPageController.BottomNavBinding(
                        chartBinding.tabBar,
                        chartBinding.tabMarketMonitor,
                        chartBinding.tabMarketChart,
                        chartBinding.tabAccountPosition,
                        chartBinding.tabAccountStats
                ));
        pageController.bind();
        pageController.onColdStart();
    }

    @Override
    public void onHostPageShown() {
        if (screen != null) {
            screen.onNewIntent(requireActivity().getIntent());
        }
        if (pageController != null) {
            pageController.onPageShown();
        }
    }

    @Override
    public void onHostPageHidden() {
        if (pageController != null) {
            pageController.onPageHidden();
        }
    }

    @Override
    public void onDestroyView() {
        if (pageController != null) {
            pageController.onDestroy();
            pageController = null;
        }
        pageRuntime = null;
        screen = null;
        super.onDestroyView();
    }
}

/*
 * 行情持仓页控制器，统一承接底部导航与页面宿主边界。
 * 当前阶段先收口 Activity/Fragment 的公共入口，图表数据主链仍由宿主承接。
 */
package com.binance.monitor.ui.chart;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ActivityMarketChartBinding;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.theme.UiPaletteManager;

public final class MarketChartPageController {

    private final Host host;
    private final ActivityMarketChartBinding binding;
    @Nullable
    private final BottomNavBinding bottomNavBinding;

    public MarketChartPageController(@NonNull Host host,
                                     @NonNull ActivityMarketChartBinding binding,
                                     @Nullable BottomNavBinding bottomNavBinding) {
        this.host = host;
        this.binding = binding;
        this.bottomNavBinding = bottomNavBinding;
    }

    public void bind() {
        setupBottomNav();
        host.bindPageContent(binding);
    }

    public void onColdStart() {
        host.onColdStart();
    }

    public void onPageShown() {
        if (!host.isEmbeddedInHostShell()) {
            updateBottomTabs(false, true, false, false, false);
        }
        host.onPageShown();
    }

    public void onPageHidden() {
        host.onPageHidden();
    }

    public void onDestroy() {
        host.onPageDestroyed();
    }

    private void setupBottomNav() {
        if (bottomNavBinding == null) {
            return;
        }
        if (host.isEmbeddedInHostShell()) {
            bottomNavBinding.tabBar.setVisibility(View.GONE);
            return;
        }
        bottomNavBinding.tabBar.setVisibility(View.VISIBLE);
        updateBottomTabs(false, true, false, false, false);
        bottomNavBinding.tabMarketMonitor.setOnClickListener(v -> host.openMarketMonitor());
        bottomNavBinding.tabMarketChart.setOnClickListener(v -> updateBottomTabs(false, true, false, false, false));
        bottomNavBinding.tabAccountStats.setOnClickListener(v -> host.openAccountStats());
        bottomNavBinding.tabAccountPosition.setOnClickListener(v -> host.openAccountPosition());
        bottomNavBinding.tabSettings.setOnClickListener(v -> host.openSettings());
    }

    private void updateBottomTabs(boolean marketSelected,
                                  boolean chartSelected,
                                  boolean accountSelected,
                                  boolean accountPositionSelected,
                                  boolean settingsSelected) {
        if (bottomNavBinding == null) {
            return;
        }
        AppCompatActivity activity = host.requireActivity();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        BottomTabVisibilityManager.apply(activity,
                bottomNavBinding.tabMarketMonitor,
                bottomNavBinding.tabMarketChart,
                bottomNavBinding.tabAccountStats,
                bottomNavBinding.tabAccountPosition,
                bottomNavBinding.tabSettings);
        bottomNavBinding.tabBar.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.surfaceEnd, palette.stroke));
        styleNavTab(bottomNavBinding.tabMarketMonitor, marketSelected);
        styleNavTab(bottomNavBinding.tabMarketChart, chartSelected);
        styleNavTab(bottomNavBinding.tabAccountStats, accountSelected);
        styleNavTab(bottomNavBinding.tabAccountPosition, accountPositionSelected);
        styleNavTab(bottomNavBinding.tabSettings, settingsSelected);
    }

    private void styleNavTab(@Nullable TextView tab, boolean selected) {
        UiPaletteManager.styleBottomNavTab(tab, selected, UiPaletteManager.resolve(host.requireActivity()));
    }

    public interface Host {
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

    public static final class BottomNavBinding {
        final View tabBar;
        final TextView tabMarketMonitor;
        final TextView tabMarketChart;
        final TextView tabAccountPosition;
        final TextView tabAccountStats;
        final TextView tabSettings;

        public BottomNavBinding(@NonNull View tabBar,
                                @NonNull TextView tabMarketMonitor,
                                @NonNull TextView tabMarketChart,
                                @NonNull TextView tabAccountPosition,
                                @NonNull TextView tabAccountStats,
                                @NonNull TextView tabSettings) {
            this.tabBar = tabBar;
            this.tabMarketMonitor = tabMarketMonitor;
            this.tabMarketChart = tabMarketChart;
            this.tabAccountPosition = tabAccountPosition;
            this.tabAccountStats = tabAccountStats;
            this.tabSettings = tabSettings;
        }
    }
}

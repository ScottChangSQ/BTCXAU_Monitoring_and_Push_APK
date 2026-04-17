/*
 * 行情监控页控制器，统一承接底部导航与页面可见性宿主边界。
 * 当前阶段先把 Activity/Fragment 的公共入口收口，行情数据主链仍由宿主承接。
 */
package com.binance.monitor.ui.market;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ActivityMainBinding;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.theme.UiPaletteManager;

public final class MarketMonitorPageController {

    private final Host host;
    private final ActivityMainBinding binding;
    @Nullable
    private final BottomNavBinding bottomNavBinding;

    public MarketMonitorPageController(@NonNull Host host,
                                       @NonNull ActivityMainBinding binding,
                                       @Nullable BottomNavBinding bottomNavBinding) {
        this.host = host;
        this.binding = binding;
        this.bottomNavBinding = bottomNavBinding;
    }

    public void bind() {
        setupBottomNav();
        host.bindPageContent(binding);
    }

    public void onPageShown() {
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
        updateBottomTabs(true, false, false, false);
        bottomNavBinding.tabMarketMonitor.setOnClickListener(v -> updateBottomTabs(true, false, false, false));
        bottomNavBinding.tabMarketChart.setOnClickListener(v -> host.openMarketChart());
        bottomNavBinding.tabAccountStats.setOnClickListener(v -> host.openAccountStats());
        bottomNavBinding.tabAccountPosition.setOnClickListener(v -> host.openAccountPosition());
    }

    private void updateBottomTabs(boolean marketSelected,
                                  boolean chartSelected,
                                  boolean accountSelected,
                                  boolean accountPositionSelected) {
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
                null);
        bottomNavBinding.tabBar.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.surfaceEnd, palette.stroke));
        styleNavTab(bottomNavBinding.tabMarketMonitor, marketSelected);
        styleNavTab(bottomNavBinding.tabMarketChart, chartSelected);
        styleNavTab(bottomNavBinding.tabAccountStats, accountSelected);
        styleNavTab(bottomNavBinding.tabAccountPosition, accountPositionSelected);
    }

    private void styleNavTab(@Nullable TextView tab, boolean selected) {
        UiPaletteManager.styleBottomNavTab(tab, selected, UiPaletteManager.resolve(host.requireActivity()));
    }

    public interface Host {
        @NonNull
        AppCompatActivity requireActivity();

        void bindPageContent(@NonNull ActivityMainBinding binding);

        void onPageShown();

        void onPageHidden();

        void onPageDestroyed();

        boolean isEmbeddedInHostShell();

        void openMarketChart();

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

        public BottomNavBinding(@NonNull View tabBar,
                                @NonNull TextView tabMarketMonitor,
                                @NonNull TextView tabMarketChart,
                                @NonNull TextView tabAccountPosition,
                                @NonNull TextView tabAccountStats) {
            this.tabBar = tabBar;
            this.tabMarketMonitor = tabMarketMonitor;
            this.tabMarketChart = tabMarketChart;
            this.tabAccountPosition = tabAccountPosition;
            this.tabAccountStats = tabAccountStats;
        }
    }
}

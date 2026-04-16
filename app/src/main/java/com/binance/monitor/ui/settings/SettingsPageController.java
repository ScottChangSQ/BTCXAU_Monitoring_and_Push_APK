/*
 * 设置页共享控制器，统一承接设置首页目录、主题和底部导航。
 * 旧 Activity 与主壳 Fragment 共用这一套页面实现。
 */
package com.binance.monitor.ui.settings;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ContentSettingsBinding;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.theme.UiPaletteManager;

public final class SettingsPageController {

    private final Host host;
    private final ContentSettingsBinding binding;
    @Nullable
    private final BottomNavBinding bottomNavBinding;

    public SettingsPageController(@NonNull Host host,
                                  @NonNull ContentSettingsBinding binding,
                                  @Nullable BottomNavBinding bottomNavBinding) {
        this.host = host;
        this.binding = binding;
        this.bottomNavBinding = bottomNavBinding;
    }

    // 绑定设置首页入口和底部导航。
    public void bind() {
        setupEntries();
        setupBottomNav();
    }

    // 页面进入可见态时刷新主题和底部选中态。
    public void onPageShown() {
        applyPaletteStyles();
        updateBottomTabs();
    }

    public void onPageHidden() {
    }

    public void onDestroy() {
    }

    // 绑定首页分类入口。
    private void setupEntries() {
        binding.itemDisplay.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_DISPLAY, "悬浮窗与显示"));
        binding.itemGateway.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_GATEWAY, "正式入口"));
        binding.itemTheme.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_THEME, "主题设置"));
        binding.itemTab.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_TAB, "Tab 页管理"));
        binding.itemCache.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_CACHE, "缓存管理"));
        binding.itemLogs.setOnClickListener(v -> host.openLogPage());
    }

    // 绑定底部导航。
    private void setupBottomNav() {
        if (bottomNavBinding == null) {
            return;
        }
        if (host.isEmbeddedInHostShell()) {
            bottomNavBinding.tabBar.setVisibility(View.GONE);
            return;
        }
        bottomNavBinding.tabBar.setVisibility(View.VISIBLE);
        updateBottomTabs();
        bottomNavBinding.tabMarketMonitor.setOnClickListener(v -> host.openMarketMonitor());
        bottomNavBinding.tabMarketChart.setOnClickListener(v -> host.openMarketChart());
        bottomNavBinding.tabAccountStats.setOnClickListener(v -> host.openAccountStats());
        bottomNavBinding.tabAccountPosition.setOnClickListener(v -> host.openAccountPosition());
        bottomNavBinding.tabSettings.setOnClickListener(v -> updateBottomTabs());
    }

    // 刷新底部导航状态。
    private void updateBottomTabs() {
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
        styleNavTab(bottomNavBinding.tabMarketMonitor, false);
        styleNavTab(bottomNavBinding.tabMarketChart, false);
        styleNavTab(bottomNavBinding.tabAccountStats, false);
        styleNavTab(bottomNavBinding.tabAccountPosition, false);
        styleNavTab(bottomNavBinding.tabSettings, true);
    }

    private void styleNavTab(@Nullable TextView tab, boolean selected) {
        UiPaletteManager.styleBottomNavTab(tab, selected, UiPaletteManager.resolve(host.requireActivity()));
    }

    // 应用当前主题色到首页目录和底部导航。
    private void applyPaletteStyles() {
        AppCompatActivity activity = host.requireActivity();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(activity, palette);
        styleEntry(binding.itemDisplay, palette);
        styleEntry(binding.itemGateway, palette);
        styleEntry(binding.itemTheme, palette);
        styleEntry(binding.itemTab, palette);
        styleEntry(binding.itemCache, palette);
        styleEntry(binding.itemLogs, palette);
    }

    // 统一绘制设置入口样式。
    private void styleEntry(@Nullable View view, @NonNull UiPaletteManager.Palette palette) {
        if (view == null) {
            return;
        }
        view.setBackground(UiPaletteManager.createListRowBackground(host.requireActivity(), palette.surfaceEnd, palette.stroke));
    }

    public interface Host {
        @NonNull
        AppCompatActivity requireActivity();

        boolean isEmbeddedInHostShell();

        void openMarketMonitor();

        void openMarketChart();

        void openAccountStats();

        void openAccountPosition();

        void openSettingsSection(@NonNull String section, @NonNull String title);

        void openLogPage();
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

/*
 * 账户统计页面控制器，统一承接页面绑定、生命周期和底部导航宿主边界。
 * 当前阶段先把 Activity/Fragment 的公共入口收口，统计主链仍由宿主回调承接。
 */
package com.binance.monitor.ui.account;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ContentAccountStatsBinding;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.theme.UiPaletteManager;

public final class AccountStatsPageController {

    private final Host host;
    private final ContentAccountStatsBinding binding;
    @Nullable
    private final BottomNavBinding bottomNavBinding;

    public AccountStatsPageController(@NonNull Host host,
                                      @NonNull ContentAccountStatsBinding binding,
                                      @Nullable BottomNavBinding bottomNavBinding) {
        this.host = host;
        this.binding = binding;
        this.bottomNavBinding = bottomNavBinding;
    }

    // 绑定页面内容，后续 Fragment 与 Activity 复用同一入口。
    public void bind() {
        setupBottomNav();
        host.bindPageContent(binding);
    }

    public void onColdStart() {
        host.onColdStart();
    }

    // 页面进入可见态时触发。
    public void onPageShown() {
        host.onPageShown();
    }

    // 页面离开可见态时触发。
    public void onPageHidden() {
        host.onPageHidden();
    }

    // 页面销毁时通知宿主释放资源。
    public void onDestroy() {
        host.onPageDestroyed();
    }

    // 统一装配底部导航，主壳承载时隐藏页内 Tab。
    private void setupBottomNav() {
        if (bottomNavBinding == null) {
            return;
        }
        if (host.isEmbeddedInHostShell()) {
            bottomNavBinding.tabBar.setVisibility(View.GONE);
            return;
        }
        bottomNavBinding.tabBar.setVisibility(View.VISIBLE);
        updateBottomTabs(false, false, true, false, false);
        bottomNavBinding.tabMarketMonitor.setOnClickListener(v -> host.openMarketMonitor());
        bottomNavBinding.tabMarketChart.setOnClickListener(v -> host.openMarketChart());
        bottomNavBinding.tabAccountStats.setOnClickListener(v -> updateBottomTabs(false, false, true, false, false));
        bottomNavBinding.tabAccountPosition.setOnClickListener(v -> host.openAccountPosition());
        bottomNavBinding.tabSettings.setOnClickListener(v -> host.openSettings());
    }

    // 刷新底部导航状态。
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

        void bindPageContent(@NonNull ContentAccountStatsBinding binding);

        void placeCurveSectionToBottom();

        void bindLocalMeta();

        void onColdStart();

        void onPageShown();

        void onPageHidden();

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

        void onPageDestroyed();

        boolean isEmbeddedInHostShell();

        void openMarketMonitor();

        void openMarketChart();

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

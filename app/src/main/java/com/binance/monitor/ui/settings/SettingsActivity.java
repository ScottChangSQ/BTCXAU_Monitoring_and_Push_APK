/*
 * 设置首页，只负责按微信式目录展示设置分类入口。
 * 具体设置内容下沉到 SettingsSectionActivity，避免首页过长。
 */
package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ActivitySettingsBinding;
import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.log.LogActivity;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;

public class SettingsActivity extends AppCompatActivity {

    public static final String SECTION_DISPLAY = "display";
    public static final String SECTION_GATEWAY = "gateway";
    public static final String SECTION_THEME = "theme";
    public static final String SECTION_TAB = "tab";
    public static final String SECTION_CACHE = "cache";

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupEntries();
        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
        updateBottomTabs();
    }

    // 绑定首页分类入口。
    private void setupEntries() {
        binding.itemDisplay.setOnClickListener(v -> openSection(SECTION_DISPLAY, "悬浮窗与显示"));
        binding.itemGateway.setOnClickListener(v -> openSection(SECTION_GATEWAY, "正式入口"));
        binding.itemTheme.setOnClickListener(v -> openSection(SECTION_THEME, "主题设置"));
        binding.itemTab.setOnClickListener(v -> openSection(SECTION_TAB, "Tab 页管理"));
        binding.itemCache.setOnClickListener(v -> openSection(SECTION_CACHE, "缓存管理"));
        binding.itemLogs.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
    }

    // 绑定底部导航。
    private void setupBottomNav() {
        updateBottomTabs();
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabMarketChart.setOnClickListener(v -> openMarketChart());
        binding.tabAccountStats.setOnClickListener(v -> openAccountStats());
        binding.tabSettings.setOnClickListener(v -> updateBottomTabs());
    }

    // 刷新底部导航状态。
    private void updateBottomTabs() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        BottomTabVisibilityManager.apply(this,
                binding.tabMarketMonitor,
                binding.tabMarketChart,
                binding.tabAccountStats,
                binding.tabSettings);
        binding.tabBar.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.surfaceEnd, palette.stroke));
        styleNavTab(binding.tabMarketMonitor, false);
        styleNavTab(binding.tabMarketChart, false);
        styleNavTab(binding.tabAccountStats, false);
        styleNavTab(binding.tabSettings, true);
    }

    // 绘制单个底部导航按钮。
    private void styleNavTab(TextView tab, boolean selected) {
        UiPaletteManager.styleBottomNavTab(tab, selected, UiPaletteManager.resolve(this));
    }

    // 应用当前主题色到首页目录和底部导航。
    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        styleEntry(binding.itemDisplay, palette);
        styleEntry(binding.itemGateway, palette);
        styleEntry(binding.itemTheme, palette);
        styleEntry(binding.itemTab, palette);
        styleEntry(binding.itemCache, palette);
        styleEntry(binding.itemLogs, palette);
    }

    // 统一绘制设置入口样式。
    private void styleEntry(android.view.View view, UiPaletteManager.Palette palette) {
        if (view == null) {
            return;
        }
        view.setBackground(UiPaletteManager.createListRowBackground(this, palette.surfaceEnd, palette.stroke));
    }

    // 打开指定设置分组页。
    private void openSection(String section, String title) {
        Intent intent = new Intent(this, SettingsSectionActivity.class);
        intent.putExtra(SettingsSectionActivity.EXTRA_SECTION, section);
        intent.putExtra(SettingsSectionActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }

    private void openMarketMonitor() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private void openAccountStats() {
        Intent intent = new Intent(this, AccountStatsBridgeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private void openMarketChart() {
        Intent intent = new Intent(this, MarketChartActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}

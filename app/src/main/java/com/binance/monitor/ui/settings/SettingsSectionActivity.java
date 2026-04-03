/*
 * 设置二级页，负责展示某一个设置分组的具体内容。
 * 复用原有设置逻辑，但按分类只显示当前需要操作的模块。
 */
package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.local.db.repository.ChartHistoryRepository;
import com.binance.monitor.databinding.ActivitySettingsDetailBinding;
import com.binance.monitor.service.MonitorService;
import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.ui.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.main.MainViewModel;
import com.binance.monitor.ui.theme.ThemeLauncherIconManager;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class SettingsSectionActivity extends AppCompatActivity {

    public static final String EXTRA_SECTION = "extra_section";
    public static final String EXTRA_TITLE = "extra_title";

    private ActivitySettingsDetailBinding binding;
    private MainViewModel viewModel;
    private boolean applying;
    private String sectionKey = SettingsActivity.SECTION_DISPLAY;
    private String sectionTitle = "设置";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sectionKey = readExtra(EXTRA_SECTION, SettingsActivity.SECTION_DISPLAY);
        sectionTitle = readExtra(EXTRA_TITLE, "设置");
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setupBottomNav();
        setupThemeItems();
        setupActions();
        applyVisibleSection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
        updateBottomTabs();
        applySettings();
        applyVisibleSection();
    }

    // 读取页面参数，缺失时使用默认值。
    private String readExtra(String key, String fallback) {
        String value = getIntent().getStringExtra(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    // 绑定顶部返回和底部导航。
    private void setupBottomNav() {
        updateBottomTabs();
        binding.btnBack.setOnClickListener(v -> finish());
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabMarketChart.setOnClickListener(v -> openMarketChart());
        binding.tabAccountStats.setOnClickListener(v -> openAccountStats());
        binding.tabSettings.setOnClickListener(v -> openSettingsHome());
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

    // 绑定各个设置控件行为。
    private void setupActions() {
        binding.btnClearCache.setOnClickListener(v -> confirmAndClearCache());
        binding.btnSaveMt5GatewayUrl.setOnClickListener(v -> saveMt5GatewayAddress());
        binding.switchFloatingEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            if (isChecked && !PermissionHelper.canDrawOverlays(this)) {
                PermissionHelper.openOverlaySettings(this);
                return;
            }
            viewModel.setFloatingEnabled(isChecked);
            sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.seekFloatingAlpha.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int safeValue = Math.max(20, progress);
                binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, safeValue));
                if (fromUser && !applying) {
                    viewModel.setFloatingAlpha(safeValue);
                    sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
            }
        });
        binding.switchShowBtc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            viewModel.setShowBtc(isChecked);
            sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.switchShowXau.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            viewModel.setShowXau(isChecked);
            sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.switchTabMarketMonitor.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            if (!canApplyTabVisibility("market", isChecked)) {
                resetTabSwitches();
                return;
            }
            viewModel.setTabMarketMonitorVisible(isChecked);
            updateBottomTabs();
        });
        binding.switchTabMarketChart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            if (!canApplyTabVisibility("chart", isChecked)) {
                resetTabSwitches();
                return;
            }
            viewModel.setTabMarketChartVisible(isChecked);
            updateBottomTabs();
        });
        binding.switchTabAccountStats.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            if (!canApplyTabVisibility("account", isChecked)) {
                resetTabSwitches();
                return;
            }
            viewModel.setTabAccountStatsVisible(isChecked);
            updateBottomTabs();
        });
    }

    // 绑定主题选项。
    private void setupThemeItems() {
        binding.cardThemeFinancial.setOnClickListener(v -> selectTheme(0));
        binding.cardThemeVintage.setOnClickListener(v -> selectTheme(1));
        binding.cardThemeBinance.setOnClickListener(v -> selectTheme(2));
        binding.cardThemeTradingView.setOnClickListener(v -> selectTheme(3));
        binding.cardThemeLight.setOnClickListener(v -> selectTheme(4));
    }

    // 防止用户把三个业务页签都关掉。
    private boolean canApplyTabVisibility(String tabKey, boolean targetVisible) {
        boolean marketVisible = "market".equals(tabKey) ? targetVisible : viewModel.isTabMarketMonitorVisible();
        boolean chartVisible = "chart".equals(tabKey) ? targetVisible : viewModel.isTabMarketChartVisible();
        boolean accountVisible = "account".equals(tabKey) ? targetVisible : viewModel.isTabAccountStatsVisible();
        if (marketVisible || chartVisible || accountVisible) {
            return true;
        }
        Toast.makeText(this, "至少保留一个业务 Tab 页", Toast.LENGTH_SHORT).show();
        return false;
    }

    // 设置非法时回滚开关状态。
    private void resetTabSwitches() {
        applying = true;
        binding.switchTabMarketMonitor.setChecked(viewModel.isTabMarketMonitorVisible());
        binding.switchTabMarketChart.setChecked(viewModel.isTabMarketChartVisible());
        binding.switchTabAccountStats.setChecked(viewModel.isTabAccountStatsVisible());
        applying = false;
    }

    // 弹出缓存清理分类选择。
    private void confirmAndClearCache() {
        final boolean[] checked = new boolean[]{true, false, true};
        final String[] items = new String[]{"历史行情数据", "历史交易数据", "运行时缓存"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("清理缓存")
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("清理", (dialog, which) -> clearCacheData(
                        CacheSectionClassifier.fromSelection(checked[0], checked[1], checked[2])))
                .show();
    }

    // 按勾选分类执行缓存清理。
    private void clearCacheData(CacheSectionClassifier.CacheSelection selection) {
        if (selection == null) {
            return;
        }
        int marketDeleted = 0;
        int tradeDeleted = 0;
        int cacheDeleted = 0;
        if (selection.shouldClearHistoryMarket()) {
            marketDeleted += new ChartHistoryRepository(this).clearAllHistory();
        }
        if (selection.shouldClearHistoryTrade()) {
            tradeDeleted = new AccountStorageRepository(this).clearTradeHistory();
        }
        if (selection.shouldClearRuntime()) {
            new AccountStorageRepository(this).clearRuntimeSnapshot();
            AccountStatsPreloadManager.getInstance(getApplicationContext()).clearLatestCache();
            getSharedPreferences(MarketChartActivity.PREF_RUNTIME_NAME, MODE_PRIVATE).edit().clear().apply();
            cacheDeleted = deleteDirectoryChildren(getCacheDir());
        }
        Toast.makeText(this,
                "已清理：历史行情 " + marketDeleted + "，历史交易 " + tradeDeleted + "，运行时文件 " + cacheDeleted,
                Toast.LENGTH_SHORT).show();
    }

    // 清理目录下所有子文件。
    private int deleteDirectoryChildren(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return 0;
        }
        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return 0;
        }
        int deleted = 0;
        for (File child : children) {
            deleted += deleteRecursively(child);
        }
        return deleted;
    }

    // 递归删除单个文件或目录。
    private int deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        int deleted = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleted += deleteRecursively(child);
                }
            }
        }
        if (file.delete()) {
            deleted++;
        }
        return deleted;
    }

    // 把当前持久化设置回显到页面。
    private void applySettings() {
        applying = true;
        binding.switchFloatingEnabled.setChecked(viewModel.isFloatingEnabled());
        int alpha = viewModel.getFloatingAlpha();
        binding.seekFloatingAlpha.setProgress(alpha);
        binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, alpha));
        binding.switchShowBtc.setChecked(viewModel.isShowBtc());
        binding.switchShowXau.setChecked(viewModel.isShowXau());
        binding.switchTabMarketMonitor.setChecked(viewModel.isTabMarketMonitorVisible());
        binding.switchTabMarketChart.setChecked(viewModel.isTabMarketChartVisible());
        binding.switchTabAccountStats.setChecked(viewModel.isTabAccountStatsVisible());
        binding.etMt5GatewayUrl.setText(viewModel.getMt5GatewayBaseUrl());
        applying = false;
    }

    // 应用当前主题色。
    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        binding.btnBack.setTextColor(palette.textPrimary);
        binding.tvSettingsDetailTitle.setText(sectionTitle);
        binding.tvSettingsDetailTitle.setTextColor(palette.textPrimary);
        binding.btnClearCache.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.btnClearCache.setTextColor(palette.textPrimary);
        binding.btnSaveMt5GatewayUrl.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
        binding.btnSaveMt5GatewayUrl.setTextColor(ContextCompat.getColor(this, R.color.white));
        binding.cardFloatingSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardGatewaySection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardThemeSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardTabSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardCacheSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        applyThemeItems(palette.id);
    }

    // 绘制主题预览卡片。
    private void applyThemeItems(int selectedId) {
        styleThemeItem(binding.cardThemeFinancial, binding.tvThemeFinancialTitle, binding.tvThemeFinancialDesc,
                binding.viewThemeFinancialA, binding.viewThemeFinancialB, binding.viewThemeFinancialC,
                UiPaletteManager.findById(0), selectedId == 0);
        styleThemeItem(binding.cardThemeVintage, binding.tvThemeVintageTitle, binding.tvThemeVintageDesc,
                binding.viewThemeVintageA, binding.viewThemeVintageB, binding.viewThemeVintageC,
                UiPaletteManager.findById(1), selectedId == 1);
        styleThemeItem(binding.cardThemeBinance, binding.tvThemeBinanceTitle, binding.tvThemeBinanceDesc,
                binding.viewThemeBinanceA, binding.viewThemeBinanceB, binding.viewThemeBinanceC,
                UiPaletteManager.findById(2), selectedId == 2);
        styleThemeItem(binding.cardThemeTradingView, binding.tvThemeTradingViewTitle, binding.tvThemeTradingViewDesc,
                binding.viewThemeTradingViewA, binding.viewThemeTradingViewB, binding.viewThemeTradingViewC,
                UiPaletteManager.findById(3), selectedId == 3);
        styleThemeItem(binding.cardThemeLight, binding.tvThemeLightTitle, binding.tvThemeLightDesc,
                binding.viewThemeLightA, binding.viewThemeLightB, binding.viewThemeLightC,
                UiPaletteManager.findById(4), selectedId == 4);
    }

    // 绘制单个主题条目。
    private void styleThemeItem(View item,
                                TextView titleView,
                                TextView descView,
                                View previewA,
                                View previewB,
                                View previewC,
                                UiPaletteManager.Palette palette,
                                boolean selected) {
        if (item != null) {
            int border = selected ? palette.primary : palette.stroke;
            int fill = selected ? palette.control : palette.surfaceEnd;
            item.setBackground(UiPaletteManager.createThemeItemDrawable(this, fill, border));
        }
        if (titleView != null) {
            titleView.setTextColor(palette.textPrimary);
        }
        if (descView != null) {
            descView.setTextColor(palette.textSecondary);
        }
        if (previewA != null) {
            previewA.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
        }
        if (previewB != null) {
            previewB.setBackground(UiPaletteManager.createFilledDrawable(this, palette.rise));
        }
        if (previewC != null) {
            previewC.setBackground(UiPaletteManager.createFilledDrawable(this, palette.fall));
        }
    }

    // 切换主题并通知服务刷新悬浮窗。
    private void selectTheme(int paletteId) {
        viewModel.setColorPalette(paletteId);
        ThemeLauncherIconManager.apply(this, paletteId);
        applyPaletteStyles();
        sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        Toast.makeText(this, "主题已切换为 " + UiPaletteManager.findById(paletteId).label, Toast.LENGTH_SHORT).show();
    }

    // 保存网关地址设置。
    private void saveMt5GatewayAddress() {
        String input = binding.etMt5GatewayUrl.getText() == null
                ? ""
                : binding.etMt5GatewayUrl.getText().toString();
        viewModel.setMt5GatewayBaseUrl(input);
        String normalized = viewModel.getMt5GatewayBaseUrl();
        binding.etMt5GatewayUrl.setText(normalized);
        binding.etMt5GatewayUrl.setSelection(normalized.length());
        AccountStatsPreloadManager.getInstance(getApplicationContext()).clearLatestCache();
        sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        Toast.makeText(this, getString(R.string.mt5_gateway_saved, normalized), Toast.LENGTH_SHORT).show();
    }

    // 让前台服务立即应用新配置。
    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, MonitorService.class);
        intent.setAction(action);
        ContextCompat.startForegroundService(this, intent);
    }

    // 根据分类控制可见模块。
    private void applyVisibleSection() {
        binding.cardFloatingSection.setVisibility(SettingsActivity.SECTION_DISPLAY.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardGatewaySection.setVisibility(SettingsActivity.SECTION_GATEWAY.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardThemeSection.setVisibility(SettingsActivity.SECTION_THEME.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardTabSection.setVisibility(SettingsActivity.SECTION_TAB.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardCacheSection.setVisibility(SettingsActivity.SECTION_CACHE.equals(sectionKey) ? View.VISIBLE : View.GONE);
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

    private void openSettingsHome() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

}

/*
 * 设置二级页，负责展示某一个设置分组的具体内容。
 * 复用原有设置逻辑，但按分类只显示当前需要操作的模块。
 */
package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.local.db.repository.ChartHistoryRepository;
import com.binance.monitor.databinding.ActivitySettingsDetailBinding;
import com.binance.monitor.service.MonitorServiceController;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.log.LogActivity;
import com.binance.monitor.ui.main.MainViewModel;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.ui.trade.TradeAuditActivity;
import com.binance.monitor.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Locale;

public class SettingsSectionActivity extends AppCompatActivity {
    public static final String EXTRA_SECTION = "extra_section";
    public static final String EXTRA_TITLE = "extra_title";

    private ActivitySettingsDetailBinding binding;
    private MainViewModel viewModel;
    private boolean applying;
    private String sectionKey = SettingsActivity.SECTION_DISPLAY;
    private String sectionTitle = "设置";
    private java.util.concurrent.ExecutorService cacheExecutor;
    private ConfigManager configManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sectionKey = readExtra(EXTRA_SECTION, SettingsActivity.SECTION_DISPLAY);
        sectionTitle = readExtra(EXTRA_TITLE, "设置");
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        configManager = ConfigManager.getInstance(getApplicationContext());
        cacheExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        binding.btnBack.setOnClickListener(v -> finish());
        setupActions();
        lockGatewayEntrySection();
        applyVisibleSection();
    }

    @Override
    protected void onDestroy() {
        if (cacheExecutor != null) {
            cacheExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
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

    // 绑定各个设置控件行为。
    private void setupActions() {
        binding.btnClearCache.setOnClickListener(v -> confirmAndClearCache());
        binding.btnOpenTradeAudit.setOnClickListener(v -> TradeAuditActivity.open(this, ""));
        binding.btnSaveTradeSettings.setOnClickListener(v -> saveTradeSettings());
        binding.switchFloatingEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            if (isChecked && !PermissionHelper.canDrawOverlays(this)) {
                PermissionHelper.openOverlaySettings(this);
                return;
            }
            viewModel.setFloatingEnabled(isChecked);
            MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.seekFloatingAlpha.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int safeValue = Math.max(20, progress);
                binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, safeValue));
                if (fromUser && !applying) {
                    viewModel.setFloatingAlpha(safeValue);
                    MonitorServiceController.dispatch(SettingsSectionActivity.this, AppConstants.ACTION_REFRESH_CONFIG);
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
            MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.switchShowXau.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            viewModel.setShowXau(isChecked);
            MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        });
    }

    // 弹出缓存清理分类选择。
    private void confirmAndClearCache() {
        final boolean[] checked = new boolean[]{true, false, true};
        final String[] items = new String[]{"历史行情数据", "历史交易数据", "运行时缓存"};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("清理缓存")
                .setMultiChoiceItems(items, checked, (dialogInterface, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("清理", (dialogInterface, which) -> clearCacheDataAsync(
                        CacheSectionClassifier.fromSelection(checked[0], checked[1], checked[2])))
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(this));
    }

    private void clearCacheDataAsync(CacheSectionClassifier.CacheSelection selection) {
        if (selection == null || cacheExecutor == null) {
            return;
        }
        cacheExecutor.execute(() -> {
            CacheClearResult result = clearCacheData(selection);
            runOnUiThread(() -> Toast.makeText(this,
                    "已清理：历史行情 " + result.marketDeleted
                            + "，历史交易 " + result.tradeDeleted
                            + "，运行时文件 " + result.cacheDeleted,
                    Toast.LENGTH_SHORT).show());
        });
    }

    // 按勾选分类执行缓存清理。
    private CacheClearResult clearCacheData(CacheSectionClassifier.CacheSelection selection) {
        if (selection == null) {
            return new CacheClearResult(0, 0, 0);
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
        return new CacheClearResult(marketDeleted, tradeDeleted, cacheDeleted);
    }

    private static final class CacheClearResult {
        private final int marketDeleted;
        private final int tradeDeleted;
        private final int cacheDeleted;

        private CacheClearResult(int marketDeleted, int tradeDeleted, int cacheDeleted) {
            this.marketDeleted = marketDeleted;
            this.tradeDeleted = tradeDeleted;
            this.cacheDeleted = cacheDeleted;
        }
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
        applyTradeSettings();
        lockGatewayEntrySection();
        applying = false;
    }

    // 把交易默认参数、模板和风控阈值回显到设置页。
    private void applyTradeSettings() {
        binding.switchTradeOneClickMode.setChecked(configManager.isTradeOneClickModeEnabled());
    }

    // 保存交易低频设置，并刷新交易页共享配置真值。
    private void saveTradeSettings() {
        configManager.setTradeOneClickModeEnabled(binding.switchTradeOneClickMode.isChecked());
        applyTradeSettings();
        MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        Toast.makeText(this, getString(R.string.settings_trade_saved), Toast.LENGTH_SHORT).show();
    }

    // 第 1 步入口唯一化后，设置页只展示固定公网入口，不再允许本地改写主链。
    private void lockGatewayEntrySection() {
        binding.etMt5GatewayUrl.setText(AppConstants.MT5_GATEWAY_BASE_URL);
        binding.etMt5GatewayUrl.setSelection(AppConstants.MT5_GATEWAY_BASE_URL.length());
        binding.etMt5GatewayUrl.setEnabled(false);
        binding.etMt5GatewayUrl.setFocusable(false);
        binding.etMt5GatewayUrl.setFocusableInTouchMode(false);
        binding.etMt5GatewayUrl.setClickable(false);
        binding.btnSaveMt5GatewayUrl.setVisibility(View.GONE);
    }

    // 设置页里的低频布尔项、输入项和动作按钮统一走标准主体。
    private void applyStandardSubjectStyles(UiPaletteManager.Palette palette) {
        UiPaletteManager.styleToggleChoice(binding.switchFloatingEnabled, palette);
        UiPaletteManager.styleToggleChoice(binding.switchShowBtc, palette);
        UiPaletteManager.styleToggleChoice(binding.switchShowXau, palette);
        UiPaletteManager.styleToggleChoice(binding.switchTradeOneClickMode, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutMt5GatewayUrl, palette);
        UiPaletteManager.styleActionButton(
                binding.btnClearCache,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnOpenTradeAudit,
                palette,
                palette.primarySoft,
                UiPaletteManager.controlSelectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnSaveMt5GatewayUrl,
                palette,
                palette.primarySoft,
                UiPaletteManager.controlSelectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnSaveTradeSettings,
                palette,
                palette.primarySoft,
                UiPaletteManager.controlSelectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
    }

    // 应用当前主题色。
    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        binding.btnBack.setTextColor(palette.textPrimary);
        binding.tvSettingsDetailTitle.setText(sectionTitle);
        binding.tvSettingsDetailTitle.setTextColor(palette.textPrimary);
        applyStandardSubjectStyles(palette);
        binding.cardFloatingSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardGatewaySection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardTradeSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardCacheSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.tvTradeSectionSummary.setTextColor(palette.textSecondary);
        binding.tvTradeSessionVolumeSummary.setTextColor(palette.textPrimary);
    }

    // 根据分类控制可见模块。
    private void applyVisibleSection() {
        binding.cardFloatingSection.setVisibility(SettingsActivity.SECTION_DISPLAY.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardGatewaySection.setVisibility(SettingsActivity.SECTION_GATEWAY.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardTradeSection.setVisibility(SettingsActivity.SECTION_TRADE.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardCacheSection.setVisibility(SettingsActivity.SECTION_CACHE.equals(sectionKey) ? View.VISIBLE : View.GONE);
    }

    // 统一格式化设置页小数展示，避免无意义的尾零噪音。
    private String formatDecimal(double value) {
        if (Math.abs(value) < 0.0000001d) {
            return "0";
        }
        String text = String.format(Locale.US, "%.4f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    // 解析小数输入，失败时回退到当前值，并保证不低于给定下限。
    private double parseDouble(@Nullable CharSequence input, double fallback, double minValue) {
        try {
            double parsed = Double.parseDouble(input == null ? "" : input.toString().trim());
            return Math.max(minValue, parsed);
        } catch (Exception ignored) {
            return Math.max(minValue, fallback);
        }
    }

    // 解析整数输入，失败时回退到当前值，并保证不低于给定下限。
    private int parseInt(@Nullable CharSequence input, int fallback, int minValue) {
        try {
            int parsed = Integer.parseInt(input == null ? "" : input.toString().trim());
            return Math.max(minValue, parsed);
        } catch (Exception ignored) {
            return Math.max(minValue, fallback);
        }
    }

}

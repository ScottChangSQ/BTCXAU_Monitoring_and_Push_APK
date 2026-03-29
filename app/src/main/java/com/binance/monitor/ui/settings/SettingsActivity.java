package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.KlineCacheStore;
import com.binance.monitor.databinding.ActivitySettingsBinding;
import com.binance.monitor.ui.account.AccountStatsPreloadManager;
import com.binance.monitor.service.MonitorService;
import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.log.LogActivity;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.main.MainViewModel;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private MainViewModel viewModel;
    private boolean applying;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setupPaletteSelector();
        setupBottomNav();
        setupActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
        updateBottomTabs();
        applySettings();
    }

    private void setupBottomNav() {
        updateBottomTabs();
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabMarketChart.setOnClickListener(v -> openMarketChart());
        binding.tabAccountStats.setOnClickListener(v -> openAccountStats());
        binding.tabSettings.setOnClickListener(v -> updateBottomTabs());
    }

    private void updateBottomTabs() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        BottomTabVisibilityManager.apply(this,
                binding.tabMarketMonitor,
                binding.tabMarketChart,
                binding.tabAccountStats,
                binding.tabSettings);
        binding.tabMarketMonitor.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.tabMarketChart.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.tabAccountStats.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.tabSettings.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));

        binding.tabMarketMonitor.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        binding.tabMarketChart.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        binding.tabAccountStats.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        binding.tabSettings.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private void setupPaletteSelector() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.item_spinner_filter,
                android.R.id.text1,
                UiPaletteManager.labels());
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        binding.spinnerColorPalette.setAdapter(adapter);
        binding.tvThemePaletteLabel.setOnClickListener(v -> binding.spinnerColorPalette.performClick());
        binding.spinnerColorPalette.setOnItemSelectedListener(new com.binance.monitor.ui.account.SimpleSelectionListener(() -> {
            if (applying) {
                return;
            }
            int selected = binding.spinnerColorPalette.getSelectedItemPosition();
            if (selected == viewModel.getColorPalette()) {
                return;
            }
            viewModel.setColorPalette(selected);
            binding.tvThemePaletteLabel.setText(UiPaletteManager.labels()[selected]);
            sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
            applyPaletteStyles();
            updateBottomTabs();
        }));
    }

    private void setupActions() {
        binding.btnViewLogs.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        binding.btnClearCache.setOnClickListener(v -> confirmAndClearCache());
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

    private void resetTabSwitches() {
        applying = true;
        binding.switchTabMarketMonitor.setChecked(viewModel.isTabMarketMonitorVisible());
        binding.switchTabMarketChart.setChecked(viewModel.isTabMarketChartVisible());
        binding.switchTabAccountStats.setChecked(viewModel.isTabAccountStatsVisible());
        applying = false;
    }

    private void confirmAndClearCache() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("清理缓存")
                .setMessage("将清理行情缓存、异常记录缓存、账户预加载缓存和运行时临时缓存，是否继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清理", (dialog, which) -> clearCacheData())
                .show();
    }

    private void clearCacheData() {
        int klineDeleted = new KlineCacheStore(this).clearAll();
        AbnormalRecordManager.getInstance(getApplicationContext()).clearAll();
        AccountStatsPreloadManager.getInstance(getApplicationContext()).clearLatestCache();
        getSharedPreferences(MarketChartActivity.PREF_RUNTIME_NAME, MODE_PRIVATE).edit().clear().apply();
        int cacheDeleted = deleteDirectoryChildren(getCacheDir());
        Toast.makeText(this,
                "缓存已清理（K线文件 " + klineDeleted + "，临时文件 " + cacheDeleted + "）",
                Toast.LENGTH_SHORT).show();
    }

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

    private void applySettings() {
        applying = true;
        binding.spinnerColorPalette.setSelection(viewModel.getColorPalette(), false);
        binding.tvThemePaletteLabel.setText(UiPaletteManager.labels()[viewModel.getColorPalette()]);
        binding.switchFloatingEnabled.setChecked(viewModel.isFloatingEnabled());
        int alpha = viewModel.getFloatingAlpha();
        binding.seekFloatingAlpha.setProgress(alpha);
        binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, alpha));
        binding.switchShowBtc.setChecked(viewModel.isShowBtc());
        binding.switchShowXau.setChecked(viewModel.isShowXau());
        binding.switchTabMarketMonitor.setChecked(viewModel.isTabMarketMonitorVisible());
        binding.switchTabMarketChart.setChecked(viewModel.isTabMarketChartVisible());
        binding.switchTabAccountStats.setChecked(viewModel.isTabAccountStatsVisible());
        applying = false;
    }

    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        binding.btnViewLogs.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.btnViewLogs.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        binding.btnClearCache.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.btnClearCache.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        binding.spinnerColorPalette.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.tvThemePaletteLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, MonitorService.class);
        intent.setAction(action);
        ContextCompat.startForegroundService(this, intent);
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

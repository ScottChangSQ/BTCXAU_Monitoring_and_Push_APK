package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.databinding.ActivitySettingsBinding;
import com.binance.monitor.service.MonitorService;
import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.ui.log.LogActivity;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.main.MainViewModel;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.PermissionHelper;

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
        binding.tabAccountStats.setOnClickListener(v -> openAccountStats());
        binding.tabSettings.setOnClickListener(v -> updateBottomTabs());
    }

    private void updateBottomTabs() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        binding.tabMarketMonitor.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.tabAccountStats.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.tabSettings.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));

        binding.tabMarketMonitor.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
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
        applying = false;
    }

    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        binding.btnViewLogs.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.btnViewLogs.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
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
}

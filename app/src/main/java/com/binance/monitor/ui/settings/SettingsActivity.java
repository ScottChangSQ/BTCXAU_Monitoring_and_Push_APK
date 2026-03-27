package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
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
        setupBottomNav();
        setupActions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySettings();
    }

    private void setupBottomNav() {
        updateBottomTabs();
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabAccountStats.setOnClickListener(v -> openAccountStats());
        binding.tabSettings.setOnClickListener(v -> updateBottomTabs());
    }

    private void updateBottomTabs() {
        binding.tabMarketMonitor.setBackground(AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
        binding.tabAccountStats.setBackground(AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
        binding.tabSettings.setBackground(AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected));

        binding.tabMarketMonitor.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        binding.tabAccountStats.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        binding.tabSettings.setTextColor(ContextCompat.getColor(this, R.color.bg_primary));
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
        binding.switchFloatingEnabled.setChecked(viewModel.isFloatingEnabled());
        int alpha = viewModel.getFloatingAlpha();
        binding.seekFloatingAlpha.setProgress(alpha);
        binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, alpha));
        binding.switchShowBtc.setChecked(viewModel.isShowBtc());
        binding.switchShowXau.setChecked(viewModel.isShowXau());
        applying = false;
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

package com.binance.monitor.ui.main;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.databinding.ActivityMainBinding;
import com.binance.monitor.databinding.ItemMetricBinding;
import com.binance.monitor.service.MonitorService;
import com.binance.monitor.ui.adapter.AbnormalRecordAdapter;
import com.binance.monitor.ui.log.LogActivity;
import com.binance.monitor.util.AppLaunchHelper;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_NOTIFICATION = 100;

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private AbnormalRecordAdapter recordAdapter;
    private ItemMetricBinding metricOpenBinding;
    private ItemMetricBinding metricCloseBinding;
    private ItemMetricBinding metricVolumeBinding;
    private ItemMetricBinding metricAmountBinding;
    private ItemMetricBinding metricChangeBinding;
    private ItemMetricBinding metricPercentBinding;
    private String selectedSymbol = AppConstants.SYMBOL_BTC;
    private boolean applyingConfig;
    private boolean monitoringEnabled;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        recordAdapter = new AbnormalRecordAdapter();
        metricOpenBinding = binding.layoutMetricOpen;
        metricCloseBinding = binding.layoutMetricClose;
        metricVolumeBinding = binding.layoutMetricVolume;
        metricAmountBinding = binding.layoutMetricAmount;
        metricChangeBinding = binding.layoutMetricChange;
        metricPercentBinding = binding.layoutMetricPercent;

        setupMetrics();
        setupRecycler();
        setupActions();
        setupObservers();
        applyGlobalPreferences();
        loadSymbolConfig(selectedSymbol);
        renderSymbolTab();
        sendServiceAction(AppConstants.ACTION_BOOTSTRAP);
        promptNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyGlobalPreferences();
        loadSymbolConfig(selectedSymbol);
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistCurrentSymbolConfig();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View focus = getCurrentFocus();
            if (focus instanceof EditText) {
                int[] location = new int[2];
                focus.getLocationOnScreen(location);
                float x = event.getRawX();
                float y = event.getRawY();
                boolean outside = x < location[0]
                        || x > location[0] + focus.getWidth()
                        || y < location[1]
                        || y > location[1] + focus.getHeight();
                if (outside) {
                    focus.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void setupMetrics() {
        metricOpenBinding.tvMetricLabel.setText(getString(R.string.open_price));
        metricCloseBinding.tvMetricLabel.setText(getString(R.string.close_price));
        metricVolumeBinding.tvMetricLabel.setText(getString(R.string.volume));
        metricAmountBinding.tvMetricLabel.setText(getString(R.string.amount));
        metricChangeBinding.tvMetricLabel.setText(getString(R.string.price_change));
        metricPercentBinding.tvMetricLabel.setText(getString(R.string.percent_change));
    }

    private void setupRecycler() {
        binding.recyclerRecords.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerRecords.setNestedScrollingEnabled(true);
        binding.recyclerRecords.setAdapter(recordAdapter);
    }

    private void setupActions() {
        binding.btnSymbolBtc.setOnClickListener(v -> switchSymbol(AppConstants.SYMBOL_BTC));
        binding.btnSymbolXau.setOnClickListener(v -> switchSymbol(AppConstants.SYMBOL_XAU));
        binding.btnRestoreDefault.setOnClickListener(v -> {
            SymbolConfig config = viewModel.resetSymbolConfig(selectedSymbol);
            applySymbolConfig(config);
            sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.btnToggleMonitoring.setOnClickListener(v -> {
            persistCurrentSymbolConfig();
            if (monitoringEnabled) {
                sendServiceAction(AppConstants.ACTION_STOP_MONITORING);
            } else {
                sendServiceAction(AppConstants.ACTION_START_MONITORING);
                promptNotificationPermissionIfNeeded();
            }
        });
        binding.btnViewLogs.setOnClickListener(v -> startActivity(new android.content.Intent(this, LogActivity.class)));
        binding.btnOpenBinance.setOnClickListener(v -> {
            if (!AppLaunchHelper.openBinance(this)) {
                Toast.makeText(this, R.string.open_app_failed, Toast.LENGTH_SHORT).show();
            }
        });
        binding.btnOpenMt5.setOnClickListener(v -> {
            if (!AppLaunchHelper.openMt5(this)) {
                Toast.makeText(this, R.string.open_app_failed, Toast.LENGTH_SHORT).show();
            }
        });
        binding.radioLogicGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (applyingConfig) {
                return;
            }
            viewModel.setUseAndMode(checkedId == R.id.radioLogicAnd);
            sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.switchVolume.setOnCheckedChangeListener((buttonView, isChecked) -> persistIfReady());
        binding.switchAmount.setOnCheckedChangeListener((buttonView, isChecked) -> persistIfReady());
        binding.switchPriceChange.setOnCheckedChangeListener((buttonView, isChecked) -> persistIfReady());
        binding.etVolumeThreshold.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                persistIfReady();
            }
        });
        binding.etAmountThreshold.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                persistIfReady();
            }
        });
        binding.etPriceChangeThreshold.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                persistIfReady();
            }
        });
        binding.switchFloatingEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applyingConfig) {
                return;
            }
            if (isChecked && !PermissionHelper.canDrawOverlays(this)) {
                showOverlayPermissionDialog();
            }
            viewModel.setFloatingEnabled(isChecked);
            sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.seekFloatingAlpha.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int safeValue = Math.max(20, progress);
                binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, safeValue));
                if (fromUser && !applyingConfig) {
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
            if (!applyingConfig) {
                viewModel.setShowBtc(isChecked);
                sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
            }
        });
        binding.switchShowXau.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!applyingConfig) {
                viewModel.setShowXau(isChecked);
                sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
            }
        });
    }

    private void setupObservers() {
        viewModel.getConnectionStatus().observe(this, status -> binding.tvConnectionStatus.setText(status));
        viewModel.getMonitoringEnabled().observe(this, enabled -> {
            monitoringEnabled = Boolean.TRUE.equals(enabled);
            binding.tvMonitoringStatus.setText(monitoringEnabled
                    ? R.string.monitoring_running
                    : R.string.monitoring_stopped);
            binding.tvMonitoringStatus.setBackground(monitoringEnabled
                    ? AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected)
                    : AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
            binding.tvMonitoringStatus.setTextColor(ContextCompat.getColor(this,
                    monitoringEnabled ? R.color.bg_primary : R.color.text_primary));
            binding.btnToggleMonitoring.setText(monitoringEnabled
                    ? R.string.toggle_stop
                    : R.string.toggle_start);
            binding.btnToggleMonitoring.setBackground(monitoringEnabled
                    ? AppCompatResources.getDrawable(this, R.drawable.bg_inline_button)
                    : AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected));
            binding.btnToggleMonitoring.setTextColor(ContextCompat.getColor(this,
                    monitoringEnabled ? R.color.text_primary : R.color.bg_primary));
        });
        viewModel.getLastUpdateTime().observe(this,
                time -> binding.tvLastUpdate.setText(FormatUtils.formatDateTime(time == null ? 0L : time)));
        viewModel.getLatestPrices().observe(this,
                prices -> renderMarket(prices, viewModel.getLatestClosedKlines().getValue()));
        viewModel.getLatestClosedKlines().observe(this,
                klines -> renderMarket(viewModel.getLatestPrices().getValue(), klines));
        viewModel.getRecords().observe(this, records -> {
            List<AbnormalRecord> display = mergeDisplayRecords(records);
            recordAdapter.submitList(display);
            binding.tvRecordsEmpty.setVisibility(display.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        });
    }

    private List<AbnormalRecord> mergeDisplayRecords(List<AbnormalRecord> source) {
        List<AbnormalRecord> output = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return output;
        }
        long oneHourAgo = System.currentTimeMillis() - 60L * 60L * 1000L;
        Map<Long, Map<String, AbnormalRecord>> grouped = new LinkedHashMap<>();
        for (AbnormalRecord item : source) {
            if (item.getTimestamp() < oneHourAgo) {
                continue;
            }
            Map<String, AbnormalRecord> map = grouped.get(item.getCloseTime());
            if (map == null) {
                map = new LinkedHashMap<>();
                grouped.put(item.getCloseTime(), map);
            }
            map.put(item.getSymbol(), item);
        }
        for (Map<String, AbnormalRecord> bySymbol : grouped.values()) {
            AbnormalRecord btc = bySymbol.get(AppConstants.SYMBOL_BTC);
            AbnormalRecord xau = bySymbol.get(AppConstants.SYMBOL_XAU);
            if (btc != null && xau != null) {
                String summary = getString(R.string.record_line_both, "BTC", btc.getTriggerSummary(), "XAU", xau.getTriggerSummary());
                AbnormalRecord merged = new AbnormalRecord(
                        UUID.randomUUID().toString(),
                        "BOTH",
                        Math.max(btc.getTimestamp(), xau.getTimestamp()),
                        btc.getCloseTime(),
                        btc.getOpenPrice(),
                        btc.getClosePrice(),
                        btc.getVolume(),
                        btc.getAmount(),
                        btc.getPriceChange(),
                        btc.getPercentChange(),
                        summary
                );
                output.add(merged);
            } else if (btc != null) {
                String summary = getString(R.string.record_line_single, "BTC", btc.getTriggerSummary());
                output.add(new AbnormalRecord(
                        btc.getId(),
                        "BTC",
                        btc.getTimestamp(),
                        btc.getCloseTime(),
                        btc.getOpenPrice(),
                        btc.getClosePrice(),
                        btc.getVolume(),
                        btc.getAmount(),
                        btc.getPriceChange(),
                        btc.getPercentChange(),
                        summary
                ));
            } else if (xau != null) {
                String summary = getString(R.string.record_line_single, "XAU", xau.getTriggerSummary());
                output.add(new AbnormalRecord(
                        xau.getId(),
                        "XAU",
                        xau.getTimestamp(),
                        xau.getCloseTime(),
                        xau.getOpenPrice(),
                        xau.getClosePrice(),
                        xau.getVolume(),
                        xau.getAmount(),
                        xau.getPriceChange(),
                        xau.getPercentChange(),
                        summary
                ));
            }
            if (output.size() >= 10) {
                break;
            }
        }
        return output;
    }

    private void renderMarket(Map<String, Double> prices, Map<String, KlineData> klines) {
        Double price = prices != null ? prices.get(selectedSymbol) : null;
        KlineData data = klines != null ? klines.get(selectedSymbol) : null;
        String unit = AppConstants.symbolToAsset(selectedSymbol);
        binding.tvCurrentPrice.setText(price == null ? "--" : FormatUtils.formatPriceWithUnit(price));
        if (data == null) {
            setMetric(metricOpenBinding, "--");
            setMetric(metricCloseBinding, "--");
            setMetric(metricVolumeBinding, "--");
            setMetric(metricAmountBinding, "--");
            setMetric(metricChangeBinding, "--");
            setMetric(metricPercentBinding, "--");
            return;
        }
        setMetric(metricOpenBinding, FormatUtils.formatPriceWithUnit(data.getOpenPrice()));
        setMetric(metricCloseBinding, FormatUtils.formatPriceWithUnit(data.getClosePrice()));
        setMetric(metricVolumeBinding, FormatUtils.formatVolumeWithUnit(data.getVolume(), unit));
        setMetric(metricAmountBinding, FormatUtils.formatAmount(data.getQuoteAssetVolume()).replace("M$", " M$"));
        setMetric(metricChangeBinding, FormatUtils.formatSignedPriceWithUnit(data.getPriceChange()));
        setMetric(metricPercentBinding, FormatUtils.formatPercent(data.getPercentChange()));
        int changeColor = data.getPriceChange() >= 0
                ? ContextCompat.getColor(this, R.color.accent_green)
                : ContextCompat.getColor(this, R.color.accent_red);
        metricChangeBinding.tvMetricValue.setTextColor(changeColor);
        metricPercentBinding.tvMetricValue.setTextColor(changeColor);
    }

    private void setMetric(ItemMetricBinding bindingItem, String value) {
        bindingItem.tvMetricValue.setText(value);
        if (bindingItem == metricChangeBinding || bindingItem == metricPercentBinding) {
            bindingItem.tvMetricValue.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        }
    }

    private void applyGlobalPreferences() {
        applyingConfig = true;
        binding.radioLogicOr.setChecked(!viewModel.isUseAndMode());
        binding.radioLogicAnd.setChecked(viewModel.isUseAndMode());
        binding.switchFloatingEnabled.setChecked(viewModel.isFloatingEnabled());
        int alpha = viewModel.getFloatingAlpha();
        binding.seekFloatingAlpha.setProgress(alpha);
        binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, alpha));
        binding.switchShowBtc.setChecked(viewModel.isShowBtc());
        binding.switchShowXau.setChecked(viewModel.isShowXau());
        applyingConfig = false;
    }

    private void switchSymbol(String symbol) {
        if (TextUtils.equals(symbol, selectedSymbol)) {
            return;
        }
        persistCurrentSymbolConfig();
        selectedSymbol = symbol;
        renderSymbolTab();
        loadSymbolConfig(symbol);
        renderMarket(viewModel.getLatestPrices().getValue(), viewModel.getLatestClosedKlines().getValue());
    }

    private void loadSymbolConfig(String symbol) {
        applySymbolConfig(viewModel.getSymbolConfig(symbol));
        updateUnitViews(symbol);
    }

    private void applySymbolConfig(SymbolConfig config) {
        applyingConfig = true;
        binding.etVolumeThreshold.setText(String.valueOf(config.getVolumeThreshold()));
        binding.etAmountThreshold.setText(String.valueOf(config.getAmountThreshold() / 1_000_000d));
        binding.etPriceChangeThreshold.setText(String.valueOf(config.getPriceChangeThreshold()));
        binding.switchVolume.setChecked(config.isVolumeEnabled());
        binding.switchAmount.setChecked(config.isAmountEnabled());
        binding.switchPriceChange.setChecked(config.isPriceChangeEnabled());
        applyingConfig = false;
    }

    private void updateUnitViews(String symbol) {
        String volumeUnit = AppConstants.symbolToAsset(symbol);
        binding.tvVolumeUnit.setText(volumeUnit);
        binding.inputLayoutVolumeThreshold.setHint(getString(R.string.volume_threshold_hint) + " (" + volumeUnit + ")");
        metricVolumeBinding.tvMetricLabel.setText(getString(R.string.volume) + " (" + volumeUnit + ")");
    }

    private void renderSymbolTab() {
        styleSymbolButton(binding.btnSymbolBtc, AppConstants.SYMBOL_BTC.equals(selectedSymbol));
        styleSymbolButton(binding.btnSymbolXau, AppConstants.SYMBOL_XAU.equals(selectedSymbol));
    }

    private void styleSymbolButton(Button button, boolean selected) {
        button.setBackground(selected
                ? AppCompatResources.getDrawable(this, R.drawable.bg_symbol_selected)
                : AppCompatResources.getDrawable(this, R.drawable.bg_symbol_unselected));
        button.setTextColor(ContextCompat.getColor(this, selected ? R.color.bg_primary : R.color.white));
    }

    private void persistIfReady() {
        if (!applyingConfig) {
            persistCurrentSymbolConfig();
        }
    }

    private void persistCurrentSymbolConfig() {
        if (applyingConfig) {
            return;
        }
        SymbolConfig current = viewModel.getSymbolConfig(selectedSymbol);
        current.setVolumeThreshold(parseDouble(
                binding.etVolumeThreshold.getText() == null ? null : binding.etVolumeThreshold.getText().toString(),
                current.getVolumeThreshold()));
        current.setAmountThreshold(parseDouble(
                binding.etAmountThreshold.getText() == null ? null : binding.etAmountThreshold.getText().toString(),
                current.getAmountThreshold() / 1_000_000d) * 1_000_000d);
        current.setPriceChangeThreshold(parseDouble(
                binding.etPriceChangeThreshold.getText() == null ? null : binding.etPriceChangeThreshold.getText().toString(),
                current.getPriceChangeThreshold()));
        current.setVolumeEnabled(binding.switchVolume.isChecked());
        current.setAmountEnabled(binding.switchAmount.isChecked());
        current.setPriceChangeEnabled(binding.switchPriceChange.isChecked());
        viewModel.saveSymbolConfig(current);
        sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
    }

    private double parseDouble(String value, double fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void sendServiceAction(String action) {
        android.content.Intent intent = new android.content.Intent(this, MonitorService.class);
        intent.setAction(action);
        ContextCompat.startForegroundService(this, intent);
    }

    private void showOverlayPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.overlay_permission_required)
                .setPositiveButton(R.string.permission_settings, (dialog, which) -> PermissionHelper.openOverlaySettings(this))
                .setNegativeButton(R.string.dismiss, null)
                .show();
    }

    private void promptNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !PermissionHelper.hasNotificationPermission(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.notification_permission_required)
                    .setPositiveButton(R.string.permission_settings, (dialog, which) ->
                            PermissionHelper.requestNotificationPermission(this, REQUEST_CODE_NOTIFICATION))
                    .setNegativeButton(R.string.dismiss, null)
                    .show();
        }
    }
}

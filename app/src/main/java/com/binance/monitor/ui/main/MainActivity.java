package com.binance.monitor.ui.main;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
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
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.AppLaunchHelper;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
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
    private int tabActiveColor = 0xFF07C160;
    private int tabInactiveColor = 0xFF7F8EA9;
    private long lastMarketUpdateMs;
    private ArrayAdapter<String> symbolAdapter;
    private List<AbnormalRecord> recentRecordsSource = Collections.emptyList();
    private final Handler recentRecordsHandler = new Handler(Looper.getMainLooper());
    private final Runnable recentRecordsRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            renderRecentRecords();
            recentRecordsHandler.postDelayed(this, 30_000L);
        }
    };
    private final Runnable updateTimeTickerRunnable = new Runnable() {
        @Override
        public void run() {
            binding.tvLastUpdate.setText(formatMarketUpdateText(lastMarketUpdateMs));
            recentRecordsHandler.postDelayed(this, 1_000L);
        }
    };

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
        setupBottomNav();
        applyGlobalPreferences();
        loadSymbolConfig(selectedSymbol);
        applyPaletteStyles();
        sendServiceAction(AppConstants.ACTION_BOOTSTRAP);
        promptNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
        applyGlobalPreferences();
        loadSymbolConfig(selectedSymbol);
        renderMarket(viewModel.getLatestPrices().getValue(), viewModel.getLatestClosedKlines().getValue());
        startRecentRecordsAutoRefresh();
        startUpdateTimeTicker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRecentRecordsAutoRefresh();
        stopUpdateTimeTicker();
        persistCurrentSymbolConfig();
    }

    @Override
    protected void onDestroy() {
        stopRecentRecordsAutoRefresh();
        stopUpdateTimeTicker();
        super.onDestroy();
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

    private void setupBottomNav() {
        updateBottomTabs(true, false, false, false);
        binding.tabMarketMonitor.setOnClickListener(v -> updateBottomTabs(true, false, false, false));
        binding.tabMarketChart.setOnClickListener(v -> openMarketChart());
        binding.tabAccountStats.setOnClickListener(v -> openAccountStats());
        binding.tabSettings.setOnClickListener(v -> openSettings());
    }

    private void updateBottomTabs(boolean marketSelected,
                                  boolean chartSelected,
                                  boolean accountSelected,
                                  boolean settingsSelected) {
        BottomTabVisibilityManager.apply(this,
                binding.tabMarketMonitor,
                binding.tabMarketChart,
                binding.tabAccountStats,
                binding.tabSettings);
        styleNavTab(binding.tabMarketMonitor, marketSelected);
        styleNavTab(binding.tabMarketChart, chartSelected);
        styleNavTab(binding.tabAccountStats, accountSelected);
        styleNavTab(binding.tabSettings, settingsSelected);
    }

    private void styleNavTab(TextView tab, boolean selected) {
        if (tab == null) {
            return;
        }
        tab.setBackgroundResource(selected ? R.drawable.bg_tab_wechat_selected : R.drawable.bg_tab_wechat_unselected);
        tab.setTextColor(selected ? tabActiveColor : tabInactiveColor);
        tab.setTypeface(null, Typeface.NORMAL);
        tab.setTextSize(13f);
    }

    private void setupActions() {
        setupSymbolSelector();
        binding.btnRestoreDefault.setOnClickListener(v -> {
            SymbolConfig config = viewModel.resetSymbolConfig(selectedSymbol);
            applySymbolConfig(config);
            sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        });
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
            applyLogicModeStyles();
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
    }

    private void setupSymbolSelector() {
        List<String> symbols = new ArrayList<>(AppConstants.MONITOR_SYMBOLS);
        if (symbols.isEmpty()) {
            symbols.add(AppConstants.SYMBOL_BTC);
        }
        symbolAdapter = createSymbolAdapter(symbols);
        binding.spinnerSymbolPicker.setAdapter(symbolAdapter);
        binding.tvMainSymbolPickerLabel.setOnClickListener(v -> binding.spinnerSymbolPicker.performClick());
        binding.spinnerSymbolPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                if (item == null) {
                    return;
                }
                String symbol = String.valueOf(item).trim();
                if (symbol.isEmpty()) {
                    return;
                }
                updateMainSymbolPickerLabel(symbol);
                switchSymbol(symbol);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        syncSymbolSelector();
    }

    // 统一产品下拉项文字样式，避免在不同主题下文字不可见。
    private ArrayAdapter<String> createSymbolAdapter(List<String> symbols) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.item_spinner_filter,
                android.R.id.text1,
                symbols
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSymbolSpinnerItem(view);
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSymbolSpinnerItem(view);
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    // 强制设置颜色与字号，确保产品选项稳定显示。
    private void styleSymbolSpinnerItem(@Nullable View view) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        textView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        textView.setTextSize(14f);
        textView.setTypeface(null, Typeface.NORMAL);
    }

    private void syncSymbolSelector() {
        SpinnerAdapter adapter = binding.spinnerSymbolPicker.getAdapter();
        if (adapter == null) {
            return;
        }
        String target = selectedSymbol == null ? "" : selectedSymbol.trim().toUpperCase(java.util.Locale.ROOT);
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item == null) {
                continue;
            }
            String symbol = String.valueOf(item).trim().toUpperCase(java.util.Locale.ROOT);
            if (!symbol.equals(target)) {
                continue;
            }
            if (binding.spinnerSymbolPicker.getSelectedItemPosition() != i) {
                binding.spinnerSymbolPicker.setSelection(i, false);
            }
            updateMainSymbolPickerLabel(symbol);
            return;
        }
        updateMainSymbolPickerLabel(target);
    }

    private void updateMainSymbolPickerLabel(String symbol) {
        if (binding == null || binding.tvMainSymbolPickerLabel == null) {
            return;
        }
        String text = symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT);
        if (text.isEmpty()) {
            text = AppConstants.SYMBOL_BTC;
        }
        binding.tvMainSymbolPickerLabel.setText(text);
        applyMainSymbolPickerIndicator();
    }

    // 给产品选择标签补上下拉箭头，明确提示该区域可点击展开。
    private void applyMainSymbolPickerIndicator() {
        if (binding == null || binding.tvMainSymbolPickerLabel == null) {
            return;
        }
        Drawable arrow = ContextCompat.getDrawable(this, R.drawable.ic_spinner_arrow);
        if (arrow == null) {
            return;
        }
        Drawable tintedArrow = DrawableCompat.wrap(arrow.mutate());
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        DrawableCompat.setTint(tintedArrow, palette.textSecondary);
        binding.tvMainSymbolPickerLabel.setCompoundDrawablePadding(Math.round(6 * getResources().getDisplayMetrics().density));
        binding.tvMainSymbolPickerLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, tintedArrow, null);
    }

    private void setupObservers() {
        viewModel.getConnectionStatus().observe(this, status -> {
            binding.tvConnectionStatus.setText(status);
            applyConnectionChipStyle();
        });
        viewModel.getLastUpdateTime().observe(this, time -> {
            lastMarketUpdateMs = time == null ? 0L : time;
            binding.tvLastUpdate.setText(formatMarketUpdateText(lastMarketUpdateMs));
        });
        viewModel.getLatestPrices().observe(this,
                prices -> renderMarket(prices, viewModel.getLatestClosedKlines().getValue()));
        viewModel.getLatestClosedKlines().observe(this,
                klines -> renderMarket(viewModel.getLatestPrices().getValue(), klines));
        viewModel.getRecords().observe(this, records -> {
            recentRecordsSource = records == null ? Collections.emptyList() : records;
            renderRecentRecords();
        });
    }

    private void renderRecentRecords() {
        List<AbnormalRecord> display = mergeDisplayRecords(recentRecordsSource);
        recordAdapter.submitList(display);
        binding.tvRecordsEmpty.setVisibility(display.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void startRecentRecordsAutoRefresh() {
        recentRecordsHandler.removeCallbacks(recentRecordsRefreshRunnable);
        recentRecordsHandler.post(recentRecordsRefreshRunnable);
    }

    private void stopRecentRecordsAutoRefresh() {
        recentRecordsHandler.removeCallbacks(recentRecordsRefreshRunnable);
    }

    private void startUpdateTimeTicker() {
        recentRecordsHandler.removeCallbacks(updateTimeTickerRunnable);
        recentRecordsHandler.post(updateTimeTickerRunnable);
    }

    private void stopUpdateTimeTicker() {
        recentRecordsHandler.removeCallbacks(updateTimeTickerRunnable);
    }

    private List<AbnormalRecord> mergeDisplayRecords(List<AbnormalRecord> source) {
        List<AbnormalRecord> output = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return output;
        }
        long oneHourAgo = System.currentTimeMillis() - 60L * 60L * 1000L;
        Map<Long, Map<String, AbnormalRecord>> grouped = new LinkedHashMap<>();
        for (AbnormalRecord item : source) {
            if (item.getCloseTime() < oneHourAgo) {
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
                        Math.max(btc.getCloseTime(), xau.getCloseTime()),
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
                        btc.getCloseTime(),
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
                        xau.getCloseTime(),
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
        String priceText = price == null ? "--" : FormatUtils.formatPriceWithUnit(price);
        binding.tvCurrentPrice.setText(priceText);
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
        applyingConfig = false;
        applyLogicModeStyles();
    }

    // 用纯文字高亮展示 OR / AND，去掉按钮底框和圆形选中标记。
    private void applyLogicModeStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        styleLogicOption(binding.radioLogicOr, !viewModel.isUseAndMode(), palette);
        styleLogicOption(binding.radioLogicAnd, viewModel.isUseAndMode(), palette);
    }

    // 统一逻辑选项的颜色和下划线，选中项更醒目。
    private void styleLogicOption(TextView optionView,
                                  boolean selected,
                                  UiPaletteManager.Palette palette) {
        if (optionView == null || palette == null) {
            return;
        }
        optionView.setTextColor(selected ? palette.primary : palette.textSecondary);
        optionView.setTypeface(null, Typeface.NORMAL);
        optionView.setPaintFlags(selected
                ? (optionView.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG)
                : (optionView.getPaintFlags() & ~android.graphics.Paint.UNDERLINE_TEXT_FLAG));
    }

    private void switchSymbol(String symbol) {
        if (TextUtils.isEmpty(symbol)) {
            return;
        }
        String normalized = symbol.trim().toUpperCase(java.util.Locale.ROOT);
        if (TextUtils.equals(normalized, selectedSymbol)) {
            return;
        }
        persistCurrentSymbolConfig();
        selectedSymbol = normalized;
        syncSymbolSelector();
        loadSymbolConfig(normalized);
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

    private void applyConnectionChipStyle() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        binding.tvConnectionStatus.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
        binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        tabActiveColor = palette.primary;
        tabInactiveColor = palette.textSecondary;
        binding.spinnerSymbolPicker.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.tvMainSymbolPickerLabel.setTextColor(palette.textPrimary);
        applyMainSymbolPickerIndicator();
        applyLogicModeStyles();
        if (binding.spinnerSymbolPicker.getAdapter() instanceof BaseAdapter) {
            ((BaseAdapter) binding.spinnerSymbolPicker.getAdapter()).notifyDataSetChanged();
        }
        updateBottomTabs(true, false, false, false);
        applyConnectionChipStyle();
        syncSymbolSelector();
    }

    private String formatMarketUpdateText(long timestampMs) {
        if (timestampMs <= 0L) {
            return "--";
        }
        long intervalMs = Math.max(1_000L, AppConstants.PRICE_UPDATE_THROTTLE_MS);
        long intervalSeconds = Math.max(1L, intervalMs / 1_000L);
        long elapsed = Math.max(0L, System.currentTimeMillis() - timestampMs);
        long remainMs = intervalMs - (elapsed % intervalMs);
        if (remainMs == intervalMs && elapsed > 0L) {
            remainMs = 0L;
        }
        long remainSeconds = remainMs <= 0L
                ? intervalSeconds
                : Math.max(1L, (remainMs + 999L) / 1_000L);
        return FormatUtils.formatDateTime(timestampMs)
                + "（" + remainSeconds + "秒/" + intervalSeconds + "秒）";
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

    private void openAccountStats() {
        android.content.Intent intent = new android.content.Intent(this, com.binance.monitor.ui.account.AccountStatsBridgeActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openMarketChart() {
        android.content.Intent intent = new android.content.Intent(this, MarketChartActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openSettings() {
        android.content.Intent intent = new android.content.Intent(this, SettingsActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
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

    private void promptBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || PermissionHelper.isIgnoringBatteryOptimizations(this)) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.battery_optimization_required)
                .setPositiveButton(R.string.battery_optimization_allow, (dialog, which) ->
                        PermissionHelper.requestIgnoreBatteryOptimizations(this))
                .setNegativeButton(R.string.permission_settings, (dialog, which) ->
                        PermissionHelper.openBatteryOptimizationSettings(this))
                .setNeutralButton(R.string.dismiss, null)
                .show();
    }
}

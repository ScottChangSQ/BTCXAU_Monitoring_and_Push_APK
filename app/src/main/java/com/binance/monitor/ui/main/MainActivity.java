package com.binance.monitor.ui.main;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.graphics.Typeface;
import android.text.TextWatcher;
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
import com.binance.monitor.databinding.DialogAbnormalRecordsBinding;
import com.binance.monitor.service.MonitorService;
import com.binance.monitor.ui.adapter.AbnormalRecordAdapter;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.AppLaunchHelper;
import com.binance.monitor.util.ChainLatencyTracer;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.GatewayUrlResolver;
import com.binance.monitor.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_NOTIFICATION = 100;

    private static final class ConnectionDetailRowHolder {
        private final View row;
        private final TextView valueView;

        private ConnectionDetailRowHolder(View row, TextView valueView) {
            this.row = row;
            this.valueView = valueView;
        }
    }

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
    private long lastMarketUpdateMs;
    private String lastMarketRenderSignature = "";
    private ArrayAdapter<String> symbolAdapter;
    private List<AbnormalRecord> recentRecordsSource = Collections.emptyList();
    private Map<String, Double> latestPricesSnapshot = Collections.emptyMap();
    private Map<String, KlineData> latestKlinesSnapshot = Collections.emptyMap();
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
        ensureMonitorServiceStarted();

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
        promptNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureMonitorServiceStarted();
        applyPaletteStyles();
        applyGlobalPreferences();
        loadSymbolConfig(selectedSymbol);
        latestPricesSnapshot = safePriceSnapshot(viewModel.getDisplayPrices().getValue());
        latestKlinesSnapshot = safeKlineSnapshot(viewModel.getDisplayOverviewKlines().getValue());
        renderMarketIfNeeded(latestPricesSnapshot, latestKlinesSnapshot);
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
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        BottomTabVisibilityManager.apply(this,
                binding.tabMarketMonitor,
                binding.tabMarketChart,
                binding.tabAccountStats,
                binding.tabSettings);
        binding.tabBar.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.surfaceEnd, palette.stroke));
        styleNavTab(binding.tabMarketMonitor, marketSelected);
        styleNavTab(binding.tabMarketChart, chartSelected);
        styleNavTab(binding.tabAccountStats, accountSelected);
        styleNavTab(binding.tabSettings, settingsSelected);
    }

    private void styleNavTab(TextView tab, boolean selected) {
        UiPaletteManager.styleBottomNavTab(tab, selected, UiPaletteManager.resolve(this));
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
        binding.tvConnectionStatus.setOnClickListener(v -> showConnectionDetailsDialog());
        binding.btnViewAllRecords.setOnClickListener(v -> showAllAbnormalRecordsDialog());
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
        UiPaletteManager.styleSpinnerItemText(textView, UiPaletteManager.resolve(this), 14f);
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
        viewModel.getDisplayPrices().observe(this, prices -> {
            latestPricesSnapshot = safePriceSnapshot(prices);
            renderMarketIfNeeded(latestPricesSnapshot, latestKlinesSnapshot);
        });
        viewModel.getDisplayOverviewKlines().observe(this, klines -> {
            latestKlinesSnapshot = safeKlineSnapshot(klines);
            renderMarketIfNeeded(latestPricesSnapshot, latestKlinesSnapshot);
        });
        viewModel.getRecords().observe(this, records -> {
            recentRecordsSource = records == null ? Collections.emptyList() : records;
            renderRecentRecords();
        });
    }

    private void renderRecentRecords() {
        List<AbnormalRecord> display = RecentAbnormalRecordHelper.buildRecentDisplay(
                recentRecordsSource,
                System.currentTimeMillis(),
                10
        );
        recordAdapter.submitList(display);
        binding.tvRecordsEmpty.setVisibility(display.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void startRecentRecordsAutoRefresh() {
        recentRecordsHandler.removeCallbacks(recentRecordsRefreshRunnable);
        recentRecordsHandler.postDelayed(recentRecordsRefreshRunnable, 30_000L);
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

    private void showAllAbnormalRecordsDialog() {
        DialogAbnormalRecordsBinding dialogBinding = DialogAbnormalRecordsBinding.inflate(getLayoutInflater());
        AbnormalRecordAdapter dialogAdapter = new AbnormalRecordAdapter(true);
        dialogBinding.recyclerAbnormalRecords.setLayoutManager(new LinearLayoutManager(this));
        dialogBinding.recyclerAbnormalRecords.setAdapter(dialogAdapter);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setPositiveButton("关闭", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(dialogBinding.getRoot(), palette);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        bindAllAbnormalRecords(dialogBinding, dialogAdapter, "");
        dialogBinding.etAbnormalSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                bindAllAbnormalRecords(dialogBinding, dialogAdapter, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        dialog.show();
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
        }
    }

    private void bindAllAbnormalRecords(DialogAbnormalRecordsBinding dialogBinding,
                                        AbnormalRecordAdapter adapter,
                                        String keyword) {
        List<AbnormalRecord> filtered = filterAllAbnormalRecords(recentRecordsSource, keyword);
        adapter.submitList(filtered);
        dialogBinding.tvAbnormalRecordsEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<AbnormalRecord> filterAllAbnormalRecords(List<AbnormalRecord> source, String keyword) {
        List<AbnormalRecord> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        String query = keyword == null ? "" : keyword.trim().toUpperCase();
        for (AbnormalRecord item : source) {
            if (item == null) {
                continue;
            }
            if (query.isEmpty()) {
                result.add(item);
                if (result.size() >= 500) {
                    break;
                }
                continue;
            }
            String haystack = (item.getSymbol() + " "
                    + FormatUtils.formatDateTime(item.getTimestamp()) + " "
                    + item.getTriggerSummary()).toUpperCase();
            if (haystack.contains(query)) {
                result.add(item);
            }
            if (result.size() >= 500) {
                break;
            }
        }
        return result;
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
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        int changeColor = data.getPriceChange() >= 0 ? palette.rise : palette.fall;
        metricChangeBinding.tvMetricValue.setTextColor(changeColor);
        metricPercentBinding.tvMetricValue.setTextColor(changeColor);
    }

    // 仅在当前产品行情真正变化时重绘，减少切页恢复时的重复渲染。
    private void renderMarketIfNeeded(@Nullable Map<String, Double> prices,
                                      @Nullable Map<String, KlineData> klines) {
        Double latestPrice = prices == null ? null : prices.get(selectedSymbol);
        KlineData latestKline = klines == null ? null : klines.get(selectedSymbol);
        String nextSignature = MainMarketRenderHelper.buildRenderSignature(
                selectedSymbol,
                latestPrice,
                latestKline
        );
        if (!MainMarketRenderHelper.shouldRender(lastMarketRenderSignature, nextSignature)) {
            return;
        }
        lastMarketRenderSignature = nextSignature;
        if (latestKline != null) {
            ChainLatencyTracer.markMainRender(selectedSymbol, latestKline.getCloseTime());
        }
        renderMarket(prices, klines);
    }

    private void setMetric(ItemMetricBinding bindingItem, String value) {
        bindingItem.tvMetricValue.setText(value);
        if (bindingItem == metricChangeBinding || bindingItem == metricPercentBinding) {
            bindingItem.tvMetricValue.setTextColor(UiPaletteManager.resolve(this).textPrimary);
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
        lastMarketRenderSignature = "";
        syncSymbolSelector();
        loadSymbolConfig(normalized);
        renderMarketIfNeeded(latestPricesSnapshot, latestKlinesSnapshot);
    }

    // 把最新价格快照转成不可空 map，避免恢复页面时重复判空分支。
    private Map<String, Double> safePriceSnapshot(@Nullable Map<String, Double> prices) {
        return prices == null ? Collections.emptyMap() : prices;
    }

    // 把最新 K 线快照转成不可空 map，供恢复页面时直接复用。
    private Map<String, KlineData> safeKlineSnapshot(@Nullable Map<String, KlineData> klines) {
        return klines == null ? Collections.emptyMap() : klines;
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
        binding.spinnerSymbolPicker.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.tvMainSymbolPickerLabel.setTextColor(palette.textPrimary);
        UiPaletteManager.styleInlineTextButton(binding.btnViewAllRecords, false, palette, 11f);
        applyMainSymbolPickerIndicator();
        applyLogicModeStyles();
        if (binding.spinnerSymbolPicker.getAdapter() instanceof BaseAdapter) {
            ((BaseAdapter) binding.spinnerSymbolPicker.getAdapter()).notifyDataSetChanged();
        }
        updateBottomTabs(true, false, false, false);
        applyConnectionChipStyle();
        syncSymbolSelector();
    }

    // 点击主界面连接状态时展示当前网络与网关入口信息。
    private void showConnectionDetailsDialog() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        String configuredBase = viewModel.getMt5GatewayBaseUrl();
        String gatewayRoot = GatewayUrlResolver.resolveGatewayRootBaseUrl(configuredBase, AppConstants.MT5_GATEWAY_BASE_URL);
        String binanceRest = viewModel.getBinanceRestBaseUrl();
        String binanceWs = viewModel.getBinanceWebSocketBaseUrl();

        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setBackground(UiPaletteManager.createFilledDrawable(this, palette.surfaceEnd));
        content.setPadding(dp(18), dp(14), dp(18), dp(6));

        android.widget.TextView titleView = new android.widget.TextView(this);
        titleView.setText("网络连接详情");
        titleView.setTextColor(palette.textPrimary);
        titleView.setTextSize(18f);
        content.addView(titleView);

        android.widget.TextView subtitleView = new android.widget.TextView(this);
        subtitleView.setText("监控工作状态与访问入口");
        subtitleView.setTextColor(palette.textSecondary);
        subtitleView.setTextSize(12f);
        android.widget.LinearLayout.LayoutParams subtitleParams =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.topMargin = dp(4);
        content.addView(subtitleView, subtitleParams);

        content.addView(createConnectionDetailRow("连接状态", binding.tvConnectionStatus.getText().toString(), palette));
        content.addView(createConnectionDetailRow("服务器入口", gatewayRoot, palette));
        content.addView(createConnectionDetailRow("Binance REST", binanceRest, palette));
        content.addView(createConnectionDetailRow("Binance WS", binanceWs, palette));
        content.addView(createConnectionDetailRow("本机内网", resolveLocalIpv4Address(), palette));
        content.addView(createConnectionDetailRow("服务器主机", resolveHostLabel(gatewayRoot), palette));
        ConnectionDetailRowHolder locationRow = createConnectionDetailRowHolder("服务器地理位置", "检测中...", palette);
        ConnectionDetailRowHolder latencyRow = createConnectionDetailRowHolder("服务器延迟", "检测中...", palette);
        content.addView(locationRow.row);
        content.addView(latencyRow.row);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setPositiveButton("确定", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(UiPaletteManager.createFilledDrawable(this, palette.surfaceEnd));
        }
        dialog.show();
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
        }
        loadConnectionDiagnosticsAsync(gatewayRoot, dialog, locationRow.valueView, latencyRow.valueView);
    }

    // 统一渲染连接详情行，避免弹窗里信息长短不一时样式散掉。
    private View createConnectionDetailRow(String label, String value, UiPaletteManager.Palette palette) {
        return createConnectionDetailRowHolder(label, value, palette).row;
    }

    // 创建可回填内容的详情行，供异步检测服务器信息时更新。
    private ConnectionDetailRowHolder createConnectionDetailRowHolder(String label,
                                                                      String value,
                                                                      UiPaletteManager.Palette palette) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.topMargin = dp(10);
        row.setLayoutParams(params);

        android.widget.TextView labelView = new android.widget.TextView(this);
        labelView.setText(label);
        labelView.setTextColor(palette.textSecondary);
        labelView.setTextSize(12f);
        row.addView(labelView);

        android.widget.TextView valueView = new android.widget.TextView(this);
        valueView.setText((value == null || value.trim().isEmpty()) ? "--" : value.trim());
        valueView.setTextColor(palette.textPrimary);
        valueView.setTextSize(13f);
        android.widget.LinearLayout.LayoutParams valueParams =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        valueParams.topMargin = dp(4);
        row.addView(valueView, valueParams);
        return new ConnectionDetailRowHolder(row, valueView);
    }

    // 异步探测服务器地理位置和延迟，避免主线程卡顿。
    private void loadConnectionDiagnosticsAsync(String gatewayRoot,
                                                androidx.appcompat.app.AlertDialog dialog,
                                                TextView locationView,
                                                TextView latencyView) {
        new Thread(() -> {
            ConnectionDetailNetworkHelper.ServerDiagnostics diagnostics =
                    ConnectionDetailNetworkHelper.load(gatewayRoot);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || dialog == null || !dialog.isShowing()) {
                    return;
                }
                if (locationView != null) {
                    locationView.setText(diagnostics.location);
                }
                if (latencyView != null) {
                    latencyView.setText(diagnostics.latencyText);
                }
            });
        }, "connection-diagnostics").start();
    }

    // 解析当前设备可用的 IPv4 内网地址。
    private String resolveLocalIpv4Address() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address
                            && !address.isLoopbackAddress()
                            && !address.getHostAddress().startsWith("169.254.")) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "--";
    }

    // 从地址里提取主机名，方便快速确认当前连接的是哪台服务器。
    private String resolveHostLabel(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "--" : uri.getHost();
        } catch (Exception ignored) {
            return "--";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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

    // 首次创建页面时确保监控服务已启动，避免直达入口时服务尚未建立主链。
    private void ensureMonitorServiceStarted() {
        if (MonitorService.isServiceRunning()) {
            return;
        }
        sendServiceAction(AppConstants.ACTION_BOOTSTRAP);
    }

    private void openAccountStats() {
        android.content.Intent intent = new android.content.Intent(this, com.binance.monitor.ui.account.AccountStatsBridgeActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openMarketChart() {
        android.content.Intent intent = new android.content.Intent(this, MarketChartActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openSettings() {
        android.content.Intent intent = new android.content.Intent(this, SettingsActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
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

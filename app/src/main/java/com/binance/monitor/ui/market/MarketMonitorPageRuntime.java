/*
 * 行情监控页运行时，统一承接主界面真实页面状态、数据订阅和交互逻辑。
 * MainActivity 与 MarketMonitorFragment 通过这一运行时共用同一套页面实现。
 */
package com.binance.monitor.ui.market;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.databinding.ActivityMainBinding;
import com.binance.monitor.databinding.DialogAbnormalRecordsBinding;
import com.binance.monitor.databinding.ItemMetricBinding;
import com.binance.monitor.service.MonitorServiceController;
import com.binance.monitor.ui.adapter.AbnormalRecordAdapter;
import com.binance.monitor.ui.main.ConnectionDetailNetworkHelper;
import com.binance.monitor.ui.main.MainMarketRenderHelper;
import com.binance.monitor.ui.main.MainViewModel;
import com.binance.monitor.ui.main.RecentAbnormalRecordHelper;
import com.binance.monitor.ui.theme.SpacingTokenResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.AppLaunchHelper;
import com.binance.monitor.util.ChainLatencyTracer;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.GatewayUrlResolver;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarketMonitorPageRuntime implements MarketMonitorPageHostDelegate.Owner {

    private static final String STATE_SELECTED_SYMBOL = "state_selected_symbol";

    private static final class ConnectionDetailRowHolder {
        private final View row;
        private final TextView valueView;

        private ConnectionDetailRowHolder(View row, TextView valueView) {
            this.row = row;
            this.valueView = valueView;
        }
    }

    private final Host host;
    private final ActivityMainBinding binding;
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

    private MainViewModel viewModel;
    private AbnormalRecordAdapter recordAdapter;
    private ItemMetricBinding metricOpenBinding;
    private ItemMetricBinding metricCloseBinding;
    private ItemMetricBinding metricChangeBinding;
    private ItemMetricBinding metricPercentBinding;
    private String selectedSymbol = AppConstants.SYMBOL_BTC;
    private boolean applyingConfig;
    private boolean bound;
    private long lastMarketUpdateMs;
    private String lastMarketRenderSignature = "";
    private ArrayAdapter<String> symbolAdapter;
    private List<AbnormalRecord> recentRecordsSource = Collections.emptyList();

    public MarketMonitorPageRuntime(@NonNull Host host,
                                    @NonNull ActivityMainBinding binding,
                                    @Nullable Bundle savedInstanceState) {
        this.host = host;
        this.binding = binding;
        restoreSelectedSymbol(savedInstanceState);
    }

    // 保存当前页面状态，供宿主重建时恢复。
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(STATE_SELECTED_SYMBOL, selectedSymbol);
    }

    @NonNull
    @Override
    public AppCompatActivity requireActivity() {
        return host.requireActivity();
    }

    // 首次绑定页面内容，装配真实页面主链。
    @Override
    public void bindPageContent(@NonNull ActivityMainBinding ignored) {
        if (bound) {
            return;
        }
        ensureMonitorServiceStarted();
        viewModel = new ViewModelProvider(host.getViewModelStoreOwner()).get(MainViewModel.class);
        recordAdapter = new AbnormalRecordAdapter();
        metricOpenBinding = binding.layoutMetricOpen;
        metricCloseBinding = binding.layoutMetricClose;
        metricChangeBinding = binding.layoutMetricChange;
        metricPercentBinding = binding.layoutMetricPercent;
        setupMetrics();
        setupRecycler();
        setupActions();
        setupObservers();
        bound = true;
    }

    // 页面进入前台时恢复主题、配置和定时刷新链路。
    @Override
    public void onPageShown() {
        ensureMonitorServiceStarted();
        applyPaletteStyles();
        applyGlobalPreferences();
        loadSymbolConfig(selectedSymbol);
        renderMarketIfNeeded();
        startRecentRecordsAutoRefresh();
        startUpdateTimeTicker();
    }

    // 页面离开前台时停止循环刷新，并落盘当前页面配置。
    @Override
    public void onPageHidden() {
        stopRecentRecordsAutoRefresh();
        stopUpdateTimeTicker();
        persistCurrentSymbolConfig();
    }

    // 页面销毁时清理定时回调，避免访问已销毁视图。
    @Override
    public void onPageDestroyed() {
        stopRecentRecordsAutoRefresh();
        stopUpdateTimeTicker();
    }

    @Override
    public boolean isEmbeddedInHostShell() {
        return host.isEmbeddedInHostShell();
    }

    @Override
    public void openMarketChart() {
        host.openMarketChart();
    }

    @Override
    public void openAccountStats() {
        host.openAccountStats();
    }

    @Override
    public void openAccountPosition() {
        host.openAccountPosition();
    }

    @Override
    public void openSettings() {
        host.openSettings();
    }

    private void setupMetrics() {
        metricOpenBinding.tvMetricLabel.setText(getString(R.string.open_price));
        metricCloseBinding.tvMetricLabel.setText(getString(R.string.close_price));
        metricChangeBinding.tvMetricLabel.setText(getString(R.string.price_change));
        metricPercentBinding.tvMetricLabel.setText(getString(R.string.percent_change));
    }

    private void setupRecycler() {
        binding.recyclerRecords.setLayoutManager(new LinearLayoutManager(requireActivity()));
        binding.recyclerRecords.setNestedScrollingEnabled(true);
        binding.recyclerRecords.setAdapter(recordAdapter);
    }

    private void setupActions() {
        setupSymbolSelector();
        binding.btnRestoreDefault.setOnClickListener(v -> {
            if (viewModel == null) {
                return;
            }
            SymbolConfig config = viewModel.resetSymbolConfig(selectedSymbol);
            applySymbolConfig(config);
            MonitorServiceController.dispatch(requireActivity(), AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.btnOpenBinance.setOnClickListener(v -> {
            if (!AppLaunchHelper.openBinance(requireActivity())) {
                Toast.makeText(requireActivity(), R.string.open_app_failed, Toast.LENGTH_SHORT).show();
            }
        });
        binding.btnOpenMt5.setOnClickListener(v -> {
            if (!AppLaunchHelper.openMt5(requireActivity())) {
                Toast.makeText(requireActivity(), R.string.open_app_failed, Toast.LENGTH_SHORT).show();
            }
        });
        binding.tvConnectionStatus.setOnClickListener(v -> showConnectionDetailsDialog());
        binding.btnViewAllRecords.setOnClickListener(v -> showAllAbnormalRecordsDialog());
        binding.radioLogicGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (applyingConfig || viewModel == null) {
                return;
            }
            viewModel.setUseAndMode(checkedId == R.id.radioLogicAnd);
            applyLogicModeStyles();
            MonitorServiceController.dispatch(requireActivity(), AppConstants.ACTION_REFRESH_CONFIG);
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

    private ArrayAdapter<String> createSymbolAdapter(List<String> symbols) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                requireActivity(),
                R.layout.item_spinner_filter_anchor,
                android.R.id.text1,
                symbols
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                return super.getView(position, convertView, parent);
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSymbolSpinnerDropdownItem(view);
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    private void styleSymbolSpinnerDropdownItem(@Nullable View view) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        UiPaletteManager.styleSelectFieldLabel(
                textView,
                UiPaletteManager.resolve(requireActivity()),
                R.style.TextAppearance_BinanceMonitor_Body
        );
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
        if (binding.tvMainSymbolPickerLabel == null) {
            return;
        }
        String text = symbol == null ? "" : symbol.trim().toUpperCase(java.util.Locale.ROOT);
        if (text.isEmpty()) {
            text = AppConstants.SYMBOL_BTC;
        }
        binding.tvMainSymbolPickerLabel.setText(text);
        applyMainSymbolPickerIndicator();
    }

    private void applyMainSymbolPickerIndicator() {
        if (binding.tvMainSymbolPickerLabel == null) {
            return;
        }
        Drawable arrow = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_spinner_arrow);
        if (arrow == null) {
            return;
        }
        Drawable tintedArrow = DrawableCompat.wrap(arrow.mutate());
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(requireActivity());
        DrawableCompat.setTint(tintedArrow, palette.textSecondary);
        binding.tvMainSymbolPickerLabel.setCompoundDrawablePadding(Math.round(6 * requireActivity().getResources().getDisplayMetrics().density));
        binding.tvMainSymbolPickerLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, tintedArrow, null);
    }

    private void setupObservers() {
        if (viewModel == null) {
            return;
        }
        LifecycleOwner lifecycleOwner = host.getLifecycleOwner();
        viewModel.getConnectionStatus().observe(lifecycleOwner, status -> {
            binding.tvConnectionStatus.setText(status);
            applyConnectionChipStyle();
        });
        viewModel.getLastUpdateTime().observe(lifecycleOwner, time -> {
            lastMarketUpdateMs = time == null ? 0L : time;
            binding.tvLastUpdate.setText(formatMarketUpdateText(lastMarketUpdateMs));
        });
        viewModel.getMarketRuntimeSnapshotLiveData().observe(lifecycleOwner, snapshot -> {
            renderMarketIfNeeded();
        });
        viewModel.getRecords().observe(lifecycleOwner, records -> {
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
        DialogAbnormalRecordsBinding dialogBinding = DialogAbnormalRecordsBinding.inflate(requireActivity().getLayoutInflater());
        AbnormalRecordAdapter dialogAdapter = new AbnormalRecordAdapter(true);
        dialogBinding.recyclerAbnormalRecords.setLayoutManager(new LinearLayoutManager(requireActivity()));
        dialogBinding.recyclerAbnormalRecords.setAdapter(dialogAdapter);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity())
                .setView(dialogBinding.getRoot())
                .setPositiveButton("关闭", null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(requireActivity());
        UiPaletteManager.applyPageTheme(dialogBinding.getRoot(), palette);
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
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
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

    private void renderMarket() {
        if (viewModel == null) {
            return;
        }
        Double price = null;
        String marketWindowSignature = viewModel.selectMarketWindowSignature(selectedSymbol);
        if (!marketWindowSignature.isEmpty()) {
            price = viewModel.selectLatestPrice(selectedSymbol);
        }
        KlineData data = viewModel.selectClosedMinute(selectedSymbol);
        String unit = AppConstants.symbolToAsset(selectedSymbol);
        String priceText = price == null ? "--" : FormatUtils.formatPriceWithUnit(price);
        binding.tvCurrentPrice.setText(priceText);
        if (data == null) {
            setMetric(metricOpenBinding, "--");
            setMetric(metricCloseBinding, "--");
            setMetric(metricChangeBinding, "--");
            setMetric(metricPercentBinding, "--");
            return;
        }
        setMetric(metricOpenBinding, FormatUtils.formatPriceWithUnit(data.getOpenPrice()));
        setMetric(metricCloseBinding, FormatUtils.formatPriceWithUnit(data.getClosePrice()));
        setMetric(metricChangeBinding, FormatUtils.formatSignedPriceWithUnit(data.getPriceChange()));
        setMetric(metricPercentBinding, FormatUtils.formatPercent(data.getPercentChange()));
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(requireActivity());
        int changeColor = data.getPriceChange() >= 0 ? palette.rise : palette.fall;
        metricChangeBinding.tvMetricValue.setTextColor(changeColor);
        metricPercentBinding.tvMetricValue.setTextColor(changeColor);
    }

    private void renderMarketIfNeeded() {
        if (viewModel == null) {
            return;
        }
        Double latestPrice = null;
        String marketWindowSignature = viewModel.selectMarketWindowSignature(selectedSymbol);
        if (!marketWindowSignature.isEmpty()) {
            latestPrice = viewModel.selectLatestPrice(selectedSymbol);
        }
        KlineData latestKline = viewModel.selectClosedMinute(selectedSymbol);
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
        renderMarket();
    }

    private void setMetric(ItemMetricBinding bindingItem, String value) {
        bindingItem.tvMetricValue.setText(value);
        if (bindingItem == metricChangeBinding || bindingItem == metricPercentBinding) {
            bindingItem.tvMetricValue.setTextColor(UiPaletteManager.resolve(requireActivity()).textPrimary);
        }
    }

    private void applyGlobalPreferences() {
        if (viewModel == null) {
            return;
        }
        applyingConfig = true;
        binding.radioLogicOr.setChecked(!viewModel.isUseAndMode());
        binding.radioLogicAnd.setChecked(viewModel.isUseAndMode());
        applyingConfig = false;
        applyLogicModeStyles();
    }

    private void applyLogicModeStyles() {
        if (viewModel == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(requireActivity());
        styleLogicOption(binding.radioLogicOr, !viewModel.isUseAndMode(), palette);
        styleLogicOption(binding.radioLogicAnd, viewModel.isUseAndMode(), palette);
    }

    private void styleLogicOption(TextView optionView,
                                  boolean selected,
                                  UiPaletteManager.Palette palette) {
        if (optionView == null || palette == null) {
            return;
        }
        optionView.setTextColor(selected
                ? UiPaletteManager.controlSelectedText(optionView.getContext())
                : UiPaletteManager.controlUnselectedText(optionView.getContext()));
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
        renderMarketIfNeeded();
    }

    private void restoreSelectedSymbol(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String restored = savedInstanceState.getString(STATE_SELECTED_SYMBOL, AppConstants.SYMBOL_BTC);
        if (!TextUtils.isEmpty(restored)) {
            selectedSymbol = restored.trim().toUpperCase(java.util.Locale.ROOT);
        }
    }

    private void loadSymbolConfig(String symbol) {
        if (viewModel == null) {
            return;
        }
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
    }

    private void applyConnectionChipStyle() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(requireActivity());
        binding.tvConnectionStatus.setBackground(UiPaletteManager.createFilledDrawable(requireActivity(), palette.primary));
        binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireActivity(), R.color.text_inverse));
    }

    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(requireActivity());
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(requireActivity(), palette);
        binding.spinnerSymbolPicker.setBackground(UiPaletteManager.createOutlinedDrawable(requireActivity(), palette.control, palette.stroke));
        UiPaletteManager.styleSelectFieldLabel(
                binding.tvMainSymbolPickerLabel,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleTextTrigger(
                binding.btnViewAllRecords,
                palette,
                palette.surfaceStart,
                UiPaletteManager.controlUnselectedText(binding.btnViewAllRecords.getContext()),
                R.style.TextAppearance_BinanceMonitor_ControlCompact
        );
        applyMainSymbolPickerIndicator();
        applyLogicModeStyles();
        if (binding.spinnerSymbolPicker.getAdapter() instanceof BaseAdapter) {
            ((BaseAdapter) binding.spinnerSymbolPicker.getAdapter()).notifyDataSetChanged();
        }
        applyConnectionChipStyle();
        syncSymbolSelector();
    }

    private void showConnectionDetailsDialog() {
        if (viewModel == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(requireActivity());
        String configuredBase = viewModel.getMt5GatewayBaseUrl();
        String gatewayRoot = GatewayUrlResolver.resolveGatewayRootBaseUrl(configuredBase, AppConstants.MT5_GATEWAY_BASE_URL);
        String binanceRest = viewModel.getBinanceRestBaseUrl();
        String binanceWs = viewModel.getBinanceWebSocketBaseUrl();

        android.widget.LinearLayout content = new android.widget.LinearLayout(requireActivity());
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setBackground(UiPaletteManager.createSurfaceDrawable(requireActivity(), palette.card, palette.stroke));
        int horizontal = SpacingTokenResolver.px(requireActivity(), R.dimen.dialog_content_padding);
        int top = SpacingTokenResolver.rowGapPx(requireActivity());
        int bottom = SpacingTokenResolver.rowGapCompactPx(requireActivity());
        content.setPadding(horizontal, top, horizontal, bottom);

        android.widget.TextView titleView = new android.widget.TextView(requireActivity());
        titleView.setText("网络连接详情");
        titleView.setTextColor(palette.textPrimary);
        UiPaletteManager.applyTextAppearance(titleView, R.style.TextAppearance_BinanceMonitor_Value);
        content.addView(titleView);

        android.widget.TextView subtitleView = new android.widget.TextView(requireActivity());
        subtitleView.setText("监控工作状态与访问入口");
        subtitleView.setTextColor(palette.textSecondary);
        UiPaletteManager.applyTextAppearance(subtitleView, R.style.TextAppearance_BinanceMonitor_Meta);
        android.widget.LinearLayout.LayoutParams subtitleParams =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        subtitleParams.topMargin = SpacingTokenResolver.rowGapCompactPx(requireActivity());
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

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireActivity())
                .setView(content)
                .setPositiveButton("确定", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
        }
        loadConnectionDiagnosticsAsync(gatewayRoot, dialog, locationRow.valueView, latencyRow.valueView);
    }

    private View createConnectionDetailRow(String label, String value, UiPaletteManager.Palette palette) {
        return createConnectionDetailRowHolder(label, value, palette).row;
    }

    private ConnectionDetailRowHolder createConnectionDetailRowHolder(String label,
                                                                      String value,
                                                                      UiPaletteManager.Palette palette) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(requireActivity());
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.setBackground(UiPaletteManager.createOutlinedDrawable(requireActivity(), palette.card, palette.stroke));
        row.setPadding(
                SpacingTokenResolver.px(requireActivity(), R.dimen.list_item_padding_x),
                SpacingTokenResolver.px(requireActivity(), R.dimen.list_item_padding_y),
                SpacingTokenResolver.px(requireActivity(), R.dimen.list_item_padding_x),
                SpacingTokenResolver.px(requireActivity(), R.dimen.list_item_padding_y)
        );
        android.widget.LinearLayout.LayoutParams params =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.topMargin = SpacingTokenResolver.rowGapPx(requireActivity());
        row.setLayoutParams(params);

        android.widget.TextView labelView = new android.widget.TextView(requireActivity());
        labelView.setText(label);
        labelView.setTextColor(palette.textSecondary);
        UiPaletteManager.applyTextAppearance(labelView, R.style.TextAppearance_BinanceMonitor_Meta);
        row.addView(labelView);

        android.widget.TextView valueView = new android.widget.TextView(requireActivity());
        valueView.setText((value == null || value.trim().isEmpty()) ? "--" : value.trim());
        valueView.setTextColor(palette.textPrimary);
        UiPaletteManager.applyTextAppearance(valueView, R.style.TextAppearance_BinanceMonitor_Body);
        android.widget.LinearLayout.LayoutParams valueParams =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        valueParams.topMargin = SpacingTokenResolver.rowGapCompactPx(requireActivity());
        row.addView(valueView, valueParams);
        return new ConnectionDetailRowHolder(row, valueView);
    }

    private void loadConnectionDiagnosticsAsync(String gatewayRoot,
                                                androidx.appcompat.app.AlertDialog dialog,
                                                TextView locationView,
                                                TextView latencyView) {
        new Thread(() -> {
            ConnectionDetailNetworkHelper.ServerDiagnostics diagnostics =
                    ConnectionDetailNetworkHelper.load(gatewayRoot);
            requireActivity().runOnUiThread(() -> {
                if (requireActivity().isFinishing() || requireActivity().isDestroyed() || dialog == null || !dialog.isShowing()) {
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

    private String resolveHostLabel(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "--" : uri.getHost();
        } catch (Exception ignored) {
            return "--";
        }
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
        if (applyingConfig || viewModel == null) {
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
        MonitorServiceController.dispatch(requireActivity(), AppConstants.ACTION_REFRESH_CONFIG);
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

    private void ensureMonitorServiceStarted() {
        MonitorServiceController.ensureStarted(requireActivity());
    }

    private String getString(int resId) {
        return requireActivity().getString(resId);
    }

    public interface Host {
        @NonNull
        AppCompatActivity requireActivity();

        @NonNull
        LifecycleOwner getLifecycleOwner();

        @NonNull
        ViewModelStoreOwner getViewModelStoreOwner();

        boolean isEmbeddedInHostShell();

        void openMarketChart();

        void openAccountStats();

        void openAccountPosition();

        void openSettings();
    }
}

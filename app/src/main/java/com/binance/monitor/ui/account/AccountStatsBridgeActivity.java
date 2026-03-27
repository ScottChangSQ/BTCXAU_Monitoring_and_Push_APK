package com.binance.monitor.ui.account;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.databinding.ActivityAccountStatsBinding;
import com.binance.monitor.ui.account.adapter.AccountMetricAdapter;
import com.binance.monitor.ui.account.adapter.PendingOrderAdapter;
import com.binance.monitor.ui.account.adapter.PositionAdapterV2;
import com.binance.monitor.ui.account.adapter.PositionAggregateAdapter;
import com.binance.monitor.ui.account.adapter.StatsMetricAdapter;
import com.binance.monitor.ui.account.adapter.TradeRecordAdapterV2;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.util.FormatUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountStatsBridgeActivity extends AppCompatActivity {
    private static final double ACCOUNT_INITIAL_BALANCE = 15_019.45d;
    private static final int RETURNS_CELL_MARGIN_DP = 0;
    private static final int RETURNS_HEADER_HEIGHT_DP = 36;
    private static final int RETURNS_BODY_HEIGHT_DP = 56;

    private static final String ACCOUNT = "7400048";
    private static final String SERVER = "ICMarketsSC-MT5-6";

    private static final String FILTER_PRODUCT = "全部产品";
    private static final String FILTER_SIDE = "全部方向";
    private static final String FILTER_DATE = "全部日期";
    private static final String FILTER_LAST_1D = "近1日";
    private static final String FILTER_LAST_7D = "近7日";
    private static final String FILTER_LAST_30D = "近30日";
    private static final String FILTER_SORT = "排序方式";
    private static final String SORT_CLOSE_TIME = "平仓时间";
    private static final String SORT_OPEN_TIME = "开仓时间";
    private static final String SORT_PROFIT = "盈利水平";

    private static final String PREFS_UI_STATE = "account_stats_ui_state";
    private static final String PREF_RANGE = "pref_range";
    private static final String PREF_RETURN_MODE = "pref_return_mode";
    private static final String PREF_RETURN_VALUE_MODE = "pref_return_value_mode";
    private static final String PREF_TRADE_PNL_SIDE = "pref_trade_pnl_side";
    private static final String PREF_RETURN_ANCHOR = "pref_return_anchor";
    private static final String PREF_MANUAL_ENABLED = "pref_manual_enabled";
    private static final String PREF_MANUAL_START_MS = "pref_manual_start_ms";
    private static final String PREF_MANUAL_END_MS = "pref_manual_end_ms";
    private static final String PREF_MANUAL_START_TEXT = "pref_manual_start_text";
    private static final String PREF_MANUAL_END_TEXT = "pref_manual_end_text";
    private static final String PREF_FILTER_PRODUCT = "pref_filter_product";
    private static final String PREF_FILTER_SIDE = "pref_filter_side";
    private static final String PREF_FILTER_DATE = "pref_filter_date";
    private static final String PREF_FILTER_SORT = "pref_filter_sort";
    private static final String PREF_FILTER_SORT_DESC = "pref_filter_sort_desc";

    private enum ReturnStatsMode {
        DAY,
        MONTH,
        YEAR,
        STAGE
    }

    private enum ReturnValueMode {
        RATE,
        AMOUNT
    }

    private enum TradePnlSideMode {
        ALL,
        BUY,
        SELL
    }

    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat monthTitleFormat = new SimpleDateFormat("yyyy年M月", Locale.getDefault());

    private ActivityAccountStatsBinding binding;
    private AccountStatsFallbackDataSource fallbackDataSource;
    private AccountStatsPreloadManager preloadManager;
    private Mt5BridgeGatewayClient gatewayClient;
    private AccountMetricAdapter overviewAdapter;
    private StatsMetricAdapter indicatorAdapter;
    private PositionAggregateAdapter positionAggregateAdapter;
    private PositionAdapterV2 positionAdapter;
    private PendingOrderAdapter pendingOrderAdapter;
    private TradeRecordAdapterV2 tradeAdapter;
    private StatsMetricAdapter statsAdapter;
    private ExecutorService ioExecutor;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private volatile boolean loading;

    private AccountTimeRange selectedRange = AccountTimeRange.D7;
    private ReturnStatsMode returnStatsMode = ReturnStatsMode.MONTH;
    private ReturnValueMode returnValueMode = ReturnValueMode.RATE;
    private TradePnlSideMode tradePnlSideMode = TradePnlSideMode.ALL;
    private long returnStatsAnchorDateMs;
    private String selectedTradeProductFilter = FILTER_PRODUCT;
    private String selectedTradeSideFilter = FILTER_SIDE;
    private String selectedTradeDateFilter = FILTER_DATE;
    private String selectedTradeSortFilter = FILTER_SORT;
    private String lastExplicitTradeSortMode = "";
    private boolean tradeSortDescending = false;
    private boolean sortSpinnerUserAction;
    private double latestCumulativePnl;

    private boolean manualCurveRangeEnabled;
    private long manualCurveRangeStartMs;
    private long manualCurveRangeEndMs;

    private List<PositionItem> basePositions = new ArrayList<>();
    private List<PositionItem> basePendingOrders = new ArrayList<>();
    private List<TradeRecordItem> baseTrades = new ArrayList<>();
    private List<CurvePoint> allCurvePoints = new ArrayList<>();
    private List<CurvePoint> displayedCurvePoints = new ArrayList<>();
    private List<AccountMetric> latestOverviewMetrics = new ArrayList<>();
    private String defaultCurveMeta = "--";
    private double curveBaseBalance = ACCOUNT_INITIAL_BALANCE;

    private String connectedAccount = ACCOUNT;
    private String connectedServer = SERVER;
    private String connectedSource = "历史数据（网关离线）";
    private String connectedGateway = "--";
    private String connectedUpdate = "--";
    private String connectedLeverageText = "";
    private String connectedError = "";
    private String dataQualitySummary = "";
    private final Map<Long, CurvePoint> curveHistory = new TreeMap<>();
    private final Map<String, TradeRecordItem> tradeHistory = new LinkedHashMap<>();
    private List<PositionItem> connectedPositionCache = new ArrayList<>();
    private List<PositionItem> connectedPendingCache = new ArrayList<>();
    private List<AccountMetric> connectedOverviewCache = new ArrayList<>();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            requestSnapshot();
            refreshHandler.postDelayed(this, AppConstants.ACCOUNT_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountStatsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fallbackDataSource = new AccountStatsFallbackDataSource();
        preloadManager = AccountStatsPreloadManager.getInstance(getApplicationContext());
        gatewayClient = new Mt5BridgeGatewayClient();
        ioExecutor = Executors.newSingleThreadExecutor();

        overviewAdapter = new AccountMetricAdapter();
        indicatorAdapter = new StatsMetricAdapter();
        positionAggregateAdapter = new PositionAggregateAdapter();
        positionAdapter = new PositionAdapterV2();
        pendingOrderAdapter = new PendingOrderAdapter();
        tradeAdapter = new TradeRecordAdapterV2();
        statsAdapter = new StatsMetricAdapter();

        restoreUiState();
        setupBottomNav();
        placeCurveSectionToBottom();
        setupRecyclers();
        setupFilters();
        configureToggleButtonsV2();
        setupRangeToggle();
        setupReturnStatsModeToggle();
        setupReturnStatsValueToggle();
        setupTradePnlSideToggle();
        setupDatePickers();
        setupCurveInteraction();
        setupOverviewHeader();
        bindLocalMeta();
        applyPreloadedCacheIfAvailable();
        requestSnapshot();

        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, AppConstants.ACCOUNT_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, AppConstants.ACCOUNT_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        persistUiState();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable);
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
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
                boolean outside = x < location[0] || x > location[0] + focus.getWidth()
                        || y < location[1] || y > location[1] + focus.getHeight();
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

    private void setupOverviewHeader() {
        binding.tvAccountConnectionStatus.setOnClickListener(v -> showConnectionDialog());
    }

    private void restoreUiState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_UI_STATE, MODE_PRIVATE);
        selectedRange = safeEnumValue(AccountTimeRange.class,
                prefs.getString(PREF_RANGE, selectedRange.name()),
                AccountTimeRange.D7);
        returnStatsMode = safeEnumValue(ReturnStatsMode.class,
                prefs.getString(PREF_RETURN_MODE, returnStatsMode.name()),
                ReturnStatsMode.MONTH);
        returnValueMode = safeEnumValue(ReturnValueMode.class,
                prefs.getString(PREF_RETURN_VALUE_MODE, returnValueMode.name()),
                ReturnValueMode.RATE);
        tradePnlSideMode = safeEnumValue(TradePnlSideMode.class,
                prefs.getString(PREF_TRADE_PNL_SIDE, tradePnlSideMode.name()),
                TradePnlSideMode.ALL);
        returnStatsAnchorDateMs = prefs.getLong(PREF_RETURN_ANCHOR, 0L);

        manualCurveRangeEnabled = prefs.getBoolean(PREF_MANUAL_ENABLED, false);
        manualCurveRangeStartMs = prefs.getLong(PREF_MANUAL_START_MS, 0L);
        manualCurveRangeEndMs = prefs.getLong(PREF_MANUAL_END_MS, 0L);
        String manualStartText = prefs.getString(PREF_MANUAL_START_TEXT, "");
        String manualEndText = prefs.getString(PREF_MANUAL_END_TEXT, "");
        if (binding != null) {
            if (manualStartText == null || manualStartText.trim().isEmpty()) {
                if (manualCurveRangeStartMs > 0L) {
                    binding.etRangeStart.setText(dateOnlyFormat.format(new Date(manualCurveRangeStartMs)));
                }
            } else {
                binding.etRangeStart.setText(manualStartText);
            }
            if (manualEndText == null || manualEndText.trim().isEmpty()) {
                if (manualCurveRangeEndMs > 0L) {
                    binding.etRangeEnd.setText(dateOnlyFormat.format(new Date(manualCurveRangeEndMs)));
                }
            } else {
                binding.etRangeEnd.setText(manualEndText);
            }
        }
        if (manualCurveRangeEnabled && (manualCurveRangeStartMs <= 0L || manualCurveRangeEndMs <= 0L)) {
            try {
                Date start = manualStartText == null ? null : dateOnlyFormat.parse(manualStartText);
                Date end = manualEndText == null ? null : dateOnlyFormat.parse(manualEndText);
                if (start != null && end != null) {
                    manualCurveRangeStartMs = start.getTime();
                    manualCurveRangeEndMs = end.getTime() + 24L * 60L * 60L * 1000L - 1L;
                }
            } catch (Exception ignored) {
            }
        }

        selectedTradeProductFilter = prefs.getString(PREF_FILTER_PRODUCT, FILTER_PRODUCT);
        selectedTradeSideFilter = prefs.getString(PREF_FILTER_SIDE, FILTER_SIDE);
        selectedTradeDateFilter = prefs.getString(PREF_FILTER_DATE, FILTER_DATE);
        selectedTradeSortFilter = prefs.getString(PREF_FILTER_SORT, FILTER_SORT);
        tradeSortDescending = prefs.getBoolean(PREF_FILTER_SORT_DESC, false);

        if (selectedTradeProductFilter == null || selectedTradeProductFilter.trim().isEmpty()) {
            selectedTradeProductFilter = FILTER_PRODUCT;
        }
        if (selectedTradeSideFilter == null || selectedTradeSideFilter.trim().isEmpty()) {
            selectedTradeSideFilter = FILTER_SIDE;
        }
        if (selectedTradeDateFilter == null || selectedTradeDateFilter.trim().isEmpty()) {
            selectedTradeDateFilter = FILTER_DATE;
        }
        if (selectedTradeSortFilter == null || selectedTradeSortFilter.trim().isEmpty()) {
            selectedTradeSortFilter = FILTER_SORT;
        }
        lastExplicitTradeSortMode = FILTER_SORT.equals(selectedTradeSortFilter)
                ? ""
                : normalizeSortValue(selectedTradeSortFilter);
    }

    private void persistUiState() {
        if (binding == null) {
            return;
        }
        selectedTradeProductFilter = safeSpinnerValue(binding.spinnerTradeProduct, selectedTradeProductFilter, FILTER_PRODUCT);
        selectedTradeSideFilter = safeSpinnerValue(binding.spinnerTradeSide, selectedTradeSideFilter, FILTER_SIDE);
        selectedTradeDateFilter = safeSpinnerValue(binding.spinnerTradeTime, selectedTradeDateFilter, FILTER_DATE);
        selectedTradeSortFilter = safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT);

        String manualStartText = trim(binding.etRangeStart.getText() == null
                ? ""
                : binding.etRangeStart.getText().toString());
        String manualEndText = trim(binding.etRangeEnd.getText() == null
                ? ""
                : binding.etRangeEnd.getText().toString());

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_UI_STATE, MODE_PRIVATE).edit();
        editor.putString(PREF_RANGE, selectedRange.name());
        editor.putString(PREF_RETURN_MODE, returnStatsMode.name());
        editor.putString(PREF_RETURN_VALUE_MODE, returnValueMode.name());
        editor.putString(PREF_TRADE_PNL_SIDE, tradePnlSideMode.name());
        editor.putLong(PREF_RETURN_ANCHOR, returnStatsAnchorDateMs);
        editor.putBoolean(PREF_MANUAL_ENABLED, manualCurveRangeEnabled);
        editor.putLong(PREF_MANUAL_START_MS, manualCurveRangeStartMs);
        editor.putLong(PREF_MANUAL_END_MS, manualCurveRangeEndMs);
        editor.putString(PREF_MANUAL_START_TEXT, manualStartText);
        editor.putString(PREF_MANUAL_END_TEXT, manualEndText);
        editor.putString(PREF_FILTER_PRODUCT, selectedTradeProductFilter);
        editor.putString(PREF_FILTER_SIDE, selectedTradeSideFilter);
        editor.putString(PREF_FILTER_DATE, selectedTradeDateFilter);
        editor.putString(PREF_FILTER_SORT, selectedTradeSortFilter);
        editor.putBoolean(PREF_FILTER_SORT_DESC, tradeSortDescending);
        editor.apply();
    }

    private String safeSpinnerValue(Spinner spinner, String fallback, String defaultValue) {
        if (spinner == null) {
            return fallback == null || fallback.trim().isEmpty() ? defaultValue : fallback;
        }
        Object selected = spinner.getSelectedItem();
        String value = selected == null ? "" : selected.toString();
        if (value == null || value.trim().isEmpty()) {
            value = fallback;
        }
        if (value == null || value.trim().isEmpty()) {
            value = defaultValue;
        }
        return value;
    }

    private <T extends Enum<T>> T safeEnumValue(Class<T> enumClass, @Nullable String stored, T fallback) {
        if (stored == null || stored.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, stored);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private void showConnectionDialog() {
        String message = "账号信息：" + connectedAccount + "\n"
                + "服务器信息：" + connectedServer + "\n"
                + "数据源信息：" + connectedSource + "\n"
                + "网关信息：" + connectedGateway + "\n"
                + "更新时间信息：" + connectedUpdate
                + (connectedError.isEmpty() ? "" : "\n失败原因：" + connectedError)
                + (dataQualitySummary.isEmpty() ? "" : "\n数据校对：" + dataQualitySummary);
        new AlertDialog.Builder(this)
                .setTitle("账户连接详情")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void setupBottomNav() {
        updateBottomTabs(false, true, false);
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabAccountStats.setOnClickListener(v -> updateBottomTabs(false, true, false));
        binding.tabSettings.setOnClickListener(v -> openSettings());
    }

    private void updateBottomTabs(boolean marketSelected, boolean accountSelected, boolean settingsSelected) {
        binding.tabMarketMonitor.setBackground(marketSelected
                ? AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected)
                : AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
        binding.tabMarketMonitor.setTextColor(ContextCompat.getColor(this,
                marketSelected ? R.color.bg_primary : R.color.text_secondary));

        binding.tabAccountStats.setBackground(accountSelected
                ? AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected)
                : AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
        binding.tabAccountStats.setTextColor(ContextCompat.getColor(this,
                accountSelected ? R.color.bg_primary : R.color.text_secondary));

        binding.tabSettings.setBackground(settingsSelected
                ? AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected)
                : AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
        binding.tabSettings.setTextColor(ContextCompat.getColor(this,
                settingsSelected ? R.color.bg_primary : R.color.text_secondary));
    }

    private void placeCurveSectionToBottom() {
        if (binding.cardCurveSection == null) {
            return;
        }
        android.view.ViewParent parent = binding.cardCurveSection.getParent();
        if (!(parent instanceof android.widget.LinearLayout)) {
            return;
        }
        android.widget.LinearLayout container = (android.widget.LinearLayout) parent;
        container.removeView(binding.cardCurveSection);
        container.addView(binding.cardCurveSection);
    }

    private void setupRecyclers() {
        binding.recyclerOverview.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerOverview.setAdapter(overviewAdapter);

        binding.recyclerCurveIndicators.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerCurveIndicators.setAdapter(indicatorAdapter);

        binding.recyclerPositionByProduct.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositionByProduct.setAdapter(positionAggregateAdapter);

        binding.recyclerPositions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositions.setAdapter(positionAdapter);

        binding.recyclerPendingOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPendingOrders.setAdapter(pendingOrderAdapter);

        binding.recyclerTrades.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTrades.setAdapter(tradeAdapter);

        binding.recyclerStats.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerStats.setAdapter(statsAdapter);
    }

    private void setupFilters() {
        ArrayAdapter<String> productAdapter = createTradeFilterAdapter(new String[]{FILTER_PRODUCT});
        binding.spinnerTradeProduct.setAdapter(productAdapter);
        binding.spinnerTradeProduct.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        ArrayAdapter<String> sideAdapter = createTradeFilterAdapter(new String[]{FILTER_SIDE, "\u4e70\u5165", "\u5356\u51fa"});
        binding.spinnerTradeSide.setAdapter(sideAdapter);
        binding.spinnerTradeSide.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        ArrayAdapter<String> timeAdapter = createTradeFilterAdapter(
                new String[]{FILTER_DATE, FILTER_LAST_1D, FILTER_LAST_7D, FILTER_LAST_30D});
        binding.spinnerTradeTime.setAdapter(timeAdapter);
        binding.spinnerTradeTime.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        ArrayAdapter<String> sortAdapter = createTradeFilterAdapter(
                new String[]{FILTER_SORT, SORT_CLOSE_TIME, SORT_OPEN_TIME, SORT_PROFIT});
        binding.spinnerTradeSort.setAdapter(sortAdapter);
        binding.spinnerTradeSort.setOnTouchListener((v, event) -> {
            if (event != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                sortSpinnerUserAction = true;
            }
            return false;
        });
        binding.spinnerTradeSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String raw = parent.getItemAtPosition(position) == null
                        ? FILTER_SORT
                        : String.valueOf(parent.getItemAtPosition(position));
                handleSortSelection(raw, sortSpinnerUserAction);
                sortSpinnerUserAction = false;
                refreshTrades();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                sortSpinnerUserAction = false;
            }
        });

        setSpinnerSelectionByValue(binding.spinnerTradeProduct, selectedTradeProductFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeSide, selectedTradeSideFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeTime, selectedTradeDateFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeSort, selectedTradeSortFilter);
        selectedTradeProductFilter = safeSpinnerValue(binding.spinnerTradeProduct, selectedTradeProductFilter, FILTER_PRODUCT);
        selectedTradeSideFilter = safeSpinnerValue(binding.spinnerTradeSide, selectedTradeSideFilter, FILTER_SIDE);
        selectedTradeDateFilter = safeSpinnerValue(binding.spinnerTradeTime, selectedTradeDateFilter, FILTER_DATE);
        selectedTradeSortFilter = safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT);
        forceRenderTradeFilterSpinners();

        binding.btnApplyManualRange.setOnClickListener(v -> applyManualCurveRange());
    }

    private ArrayAdapter<String> createTradeFilterAdapter(String[] options) {
        final List<String> items = new ArrayList<>();
        if (options != null) {
            for (String option : options) {
                items.add(option == null ? "" : option);
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.item_spinner_filter,
                android.R.id.text1,
                items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = resolveTradeFilterTextView(view);
                bindTradeFilterTextView(textView, getItem(position), true);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = resolveTradeFilterTextView(view);
                bindTradeFilterTextView(textView, getItem(position), false);
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    @Nullable
    private TextView resolveTradeFilterTextView(View view) {
        if (view instanceof TextView) {
            return (TextView) view;
        }
        if (view == null) {
            return null;
        }
        View text = view.findViewById(android.R.id.text1);
        return text instanceof TextView ? (TextView) text : null;
    }

    private void bindTradeFilterTextView(@Nullable TextView textView, @Nullable String value, boolean collapsed) {
        if (textView == null) {
            return;
        }
        textView.setText(value == null ? "" : value);
        textView.setSingleLine(true);
        textView.setMaxLines(1);
        textView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textView.setIncludeFontPadding(false);
        textView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        textView.setTextColor(0xFFFFFFFF);
        textView.setShadowLayer(1.2f, 0f, 0f, 0x99000000);
        textView.setVisibility(View.VISIBLE);
        textView.setAlpha(1f);
        textView.setPadding(dpToPx(10), dpToPx(7), collapsed ? dpToPx(24) : dpToPx(10), dpToPx(7));
        textView.setMinHeight(dpToPx(36));
        if (!collapsed) {
            textView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_surface));
        } else {
            textView.setBackgroundColor(0x00000000);
        }
    }

    private void handleSortSelection(String rawSortValue, boolean userAction) {
        String raw = (rawSortValue == null || rawSortValue.trim().isEmpty()) ? FILTER_SORT : rawSortValue;
        String normalized = normalizeSortValue(raw);
        if (userAction && !FILTER_SORT.equals(raw)) {
            if (normalized.equals(lastExplicitTradeSortMode)) {
                tradeSortDescending = !tradeSortDescending;
            } else {
                tradeSortDescending = false;
            }
            lastExplicitTradeSortMode = normalized;
        } else if (!FILTER_SORT.equals(raw)) {
            lastExplicitTradeSortMode = normalized;
        }
        selectedTradeSortFilter = raw;
    }

    private String normalizeSortValue(String rawSort) {
        if (rawSort == null || rawSort.trim().isEmpty() || FILTER_SORT.equals(rawSort)) {
            return SORT_CLOSE_TIME;
        }
        return rawSort;
    }

    private void setSpinnerSelectionByValue(Spinner spinner, String value) {
        if (spinner == null || value == null) {
            return;
        }
        SpinnerAdapter adapter = spinner.getAdapter();
        if (adapter == null) {
            return;
        }
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item != null && value.equals(item.toString())) {
                spinner.setSelection(i, false);
                return;
            }
        }
        if (adapter.getCount() > 0) {
            spinner.setSelection(0, false);
        }
    }

    private void forceRenderTradeFilterSpinners() {
        forceRenderSpinnerSelectedText(binding.spinnerTradeProduct);
        forceRenderSpinnerSelectedText(binding.spinnerTradeSide);
        forceRenderSpinnerSelectedText(binding.spinnerTradeTime);
        forceRenderSpinnerSelectedText(binding.spinnerTradeSort);
    }

    private void forceRenderSpinnerSelectedText(Spinner spinner) {
        if (spinner == null) {
            return;
        }
        spinner.post(() -> {
            View selectedView = spinner.getSelectedView();
            TextView textView = resolveTradeFilterTextView(selectedView);
            if (textView != null) {
                Object selected = spinner.getSelectedItem();
                bindTradeFilterTextView(textView, selected == null ? "" : String.valueOf(selected), true);
            }
        });
    }

    private void configureToggleButtonsV2() {
        binding.tvCurveTitle.setText("\u51c0\u503c/\u7ed3\u4f59\u66f2\u7ebf");

        styleSegmentButton(binding.btnRange1d, "1D", 10f);
        styleSegmentButton(binding.btnRange7d, "7D", 10f);
        styleSegmentButton(binding.btnRange1m, "1M", 10f);
        styleSegmentButton(binding.btnRange3m, "3M", 10f);
        styleSegmentButton(binding.btnRange1y, "1Y", 10f);
        styleSegmentButton(binding.btnRangeAll, "ALL", 10f);

        styleSegmentButton(binding.btnReturnDay, "\u65e5\u6536\u76ca", 11f);
        styleSegmentButton(binding.btnReturnMonth, "\u6708\u6536\u76ca", 11f);
        styleSegmentButton(binding.btnReturnYear, "\u5e74\u6536\u76ca", 11f);
        styleSegmentButton(binding.btnReturnStage, "\u9636\u6bb5\u6536\u76ca", 11f);
        styleSegmentButton(binding.btnReturnsRate, "\u6536\u76ca\u7387", 10f);
        styleSegmentButton(binding.btnReturnsAmount, "\u6536\u76ca\u989d", 10f);
        binding.tvReturnsPeriod.setText("\u7edf\u8ba1\u6708\u4efd");

        styleSegmentButton(binding.btnTradePnlAll, "\u5168\u90e8", 11f);
        styleSegmentButton(binding.btnTradePnlBuy, "\u4e70\u5165", 11f);
        styleSegmentButton(binding.btnTradePnlSell, "\u5356\u51fa", 11f);

        binding.toggleTimeRange.post(() -> autoFitSegmentButtons(
                binding.toggleTimeRange,
                new MaterialButton[]{
                        binding.btnRange1d, binding.btnRange7d, binding.btnRange1m,
                        binding.btnRange3m, binding.btnRange1y, binding.btnRangeAll
                },
                10f,
                8f));
        binding.toggleReturnStatsMode.post(() -> autoFitSegmentButtons(
                binding.toggleReturnStatsMode,
                new MaterialButton[]{
                        binding.btnReturnDay, binding.btnReturnMonth, binding.btnReturnYear, binding.btnReturnStage
                },
                11f,
                8f));
        binding.toggleTradePnlSide.post(() -> autoFitSegmentButtons(
                binding.toggleTradePnlSide,
                new MaterialButton[]{
                        binding.btnTradePnlAll, binding.btnTradePnlBuy, binding.btnTradePnlSell
                },
                11f,
                9f));
    }

    private void configureToggleButtons() {
        binding.tvCurveTitle.setText("净值/结余曲线");
        styleSegmentButton(binding.btnRange1d, "1D", 10f);
        styleSegmentButton(binding.btnRange7d, "7D", 10f);
        styleSegmentButton(binding.btnRange1m, "1M", 10f);
        styleSegmentButton(binding.btnRange3m, "3M", 10f);
        styleSegmentButton(binding.btnRange1y, "1Y", 10f);
        styleSegmentButton(binding.btnRangeAll, "ALL", 10f);

        styleSegmentButton(binding.btnReturnDay, "日收益", 11f);
        styleSegmentButton(binding.btnReturnMonth, "月收益", 11f);
        styleSegmentButton(binding.btnReturnYear, "年收益", 11f);
        styleSegmentButton(binding.btnReturnStage, "阶段收益", 11f);

        styleSegmentButton(binding.btnTradePnlAll, "全部", 11f);
        styleSegmentButton(binding.btnTradePnlBuy, "买入", 11f);
        styleSegmentButton(binding.btnTradePnlSell, "卖出", 11f);
        binding.tvCurveTitle.setText("净值/结余曲线");
        styleSegmentButton(binding.btnReturnDay, "日收益", 11f);
        styleSegmentButton(binding.btnReturnMonth, "月收益", 11f);
        styleSegmentButton(binding.btnReturnYear, "年收益", 11f);
        styleSegmentButton(binding.btnReturnStage, "阶段收益", 11f);
        styleSegmentButton(binding.btnTradePnlAll, "全部", 11f);
        styleSegmentButton(binding.btnTradePnlBuy, "买入", 11f);
        styleSegmentButton(binding.btnTradePnlSell, "卖出", 11f);

        binding.toggleTimeRange.post(() -> autoFitSegmentButtons(
                binding.toggleTimeRange,
                new MaterialButton[]{
                        binding.btnRange1d, binding.btnRange7d, binding.btnRange1m,
                        binding.btnRange3m, binding.btnRange1y, binding.btnRangeAll
                },
                10f,
                8f));
        binding.toggleReturnStatsMode.post(() -> autoFitSegmentButtons(
                binding.toggleReturnStatsMode,
                new MaterialButton[]{
                        binding.btnReturnDay, binding.btnReturnMonth, binding.btnReturnYear, binding.btnReturnStage
                },
                11f,
                8f));
    }

    private void styleSegmentButton(MaterialButton button, String text, float sizeSp) {
        if (button == null) {
            return;
        }
        button.setText(text);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setEllipsize(null);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setGravity(Gravity.CENTER);
        button.setIncludeFontPadding(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
    }

    private void autoFitSegmentButtons(MaterialButtonToggleGroup group,
                                       MaterialButton[] buttons,
                                       float maxTextSizeSp,
                                       float minTextSizeSp) {
        if (group == null || buttons == null || buttons.length == 0) {
            return;
        }
        int availableWidth = group.getWidth() - group.getPaddingLeft() - group.getPaddingRight();
        if (availableWidth <= 0) {
            return;
        }

        float eachButtonWidth = availableWidth / (float) buttons.length;
        float resolvedSizeSp = maxTextSizeSp;
        while (resolvedSizeSp > minTextSizeSp) {
            boolean fits = true;
            for (MaterialButton button : buttons) {
                if (button == null) {
                    continue;
                }
                CharSequence text = button.getText();
                String label = text == null ? "" : text.toString();
                if (label.isEmpty()) {
                    continue;
                }
                TextPaint probe = new TextPaint(button.getPaint());
                probe.setTextSize(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP,
                        resolvedSizeSp,
                        getResources().getDisplayMetrics()));
                float textWidth = probe.measureText(label);
                if (textWidth + dpToPx(8) > eachButtonWidth) {
                    fits = false;
                    break;
                }
            }
            if (fits) {
                break;
            }
            resolvedSizeSp -= 0.5f;
        }

        float finalSizeSp = Math.max(minTextSizeSp, resolvedSizeSp);
        for (MaterialButton button : buttons) {
            if (button == null) {
                continue;
            }
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, finalSizeSp);
            button.setPadding(0, 0, 0, 0);
        }
    }

    private void setupDatePickers() {
        binding.etRangeStart.setOnClickListener(v -> showDatePicker(binding.etRangeStart));
        binding.etRangeEnd.setOnClickListener(v -> showDatePicker(binding.etRangeEnd));
        binding.tvReturnsPeriod.setOnClickListener(v -> showReturnStatsDatePicker());
    }

    private void setupCurveInteraction() {
        binding.equityCurveView.setOnPointHighlightListener(point -> {
            if (point == null) {
                binding.tvCurveMeta.setText(defaultCurveMeta);
                return;
            }
            double pct = (point.getBalance() - curveBaseBalance) / Math.max(1e-9, curveBaseBalance) * 100d;
            String detail = String.format(Locale.getDefault(),
                    "时间 %s | 净值 $%s | 结余 $%s | 收益 %+.2f%% | 差额 %s",
                    FormatUtils.formatDateTime(point.getTimestamp()),
                    FormatUtils.formatPrice(point.getEquity()),
                    FormatUtils.formatPrice(point.getBalance()),
                    pct,
                    signedMoney(point.getEquity() - point.getBalance()));
            binding.tvCurveMeta.setText(detail);
        });
    }

    private void showDatePicker(EditText target) {
        long selection = MaterialDatePicker.todayInUtcMilliseconds();
        String text = trim(target.getText() == null ? "" : target.getText().toString());
        if (!text.isEmpty()) {
            try {
                Date parsed = dateOnlyFormat.parse(text);
                if (parsed != null) {
                    Calendar local = Calendar.getInstance();
                    local.setTime(parsed);
                    Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    utc.clear();
                    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
                    selection = utc.getTimeInMillis();
                }
            } catch (Exception ignored) {
            }
        }

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日期")
                .setSelection(selection)
                .build();
        picker.addOnPositiveButtonClickListener(value -> {
            if (value == null) {
                return;
            }
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(value);
            Calendar local = Calendar.getInstance();
            local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
            local.set(Calendar.MILLISECOND, 0);
            target.setText(dateOnlyFormat.format(local.getTime()));
        });
        picker.show(getSupportFragmentManager(), "date_picker_" + target.getId());
    }

    private void showReturnStatsDatePicker() {
        if (returnStatsMode != ReturnStatsMode.DAY) {
            return;
        }

        Map<Integer, boolean[]> yearMonthMap = buildYearMonthAvailability(allCurvePoints);
        if (yearMonthMap.isEmpty()) {
            return;
        }

        List<Integer> years = new ArrayList<>(yearMonthMap.keySet());
        years.sort(Integer::compareTo);
        String[] yearLabels = new String[years.size()];
        for (int i = 0; i < years.size(); i++) {
            yearLabels[i] = years.get(i) + "年";
        }

        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(returnStatsAnchorDateMs > 0L ? returnStatsAnchorDateMs : System.currentTimeMillis());
        int anchorYear = anchor.get(Calendar.YEAR);
        int anchorMonth = anchor.get(Calendar.MONTH) + 1;

        int yearIndex = years.indexOf(anchorYear);
        if (yearIndex < 0) {
            yearIndex = years.size() - 1;
            anchorYear = years.get(yearIndex);
        }

        NumberPicker yearPicker = new NumberPicker(this);
        yearPicker.setMinValue(0);
        yearPicker.setMaxValue(yearLabels.length - 1);
        yearPicker.setDisplayedValues(yearLabels);
        yearPicker.setWrapSelectorWheel(false);
        yearPicker.setValue(yearIndex);

        NumberPicker monthPicker = new NumberPicker(this);
        monthPicker.setWrapSelectorWheel(false);
        applyMonthPickerRange(monthPicker, yearMonthMap.get(anchorYear), anchorMonth);

        TextView yearLabel = new TextView(this);
        yearLabel.setText("年份");
        yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        yearLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        yearLabel.setGravity(Gravity.CENTER);

        TextView monthLabel = new TextView(this);
        monthLabel.setText("月份");
        monthLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        monthLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        monthLabel.setGravity(Gravity.CENTER);

        android.widget.LinearLayout yearColumn = new android.widget.LinearLayout(this);
        yearColumn.setOrientation(android.widget.LinearLayout.VERTICAL);
        yearColumn.addView(yearLabel, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        yearColumn.addView(yearPicker, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.LinearLayout monthColumn = new android.widget.LinearLayout(this);
        monthColumn.setOrientation(android.widget.LinearLayout.VERTICAL);
        monthColumn.addView(monthLabel, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        monthColumn.addView(monthPicker, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        int sidePadding = dpToPx(12);
        content.setPadding(sidePadding, sidePadding, sidePadding, sidePadding);
        content.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_surface));
        content.addView(yearColumn, new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(monthColumn, new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        yearPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            int selectedYear = years.get(newVal);
            applyMonthPickerRange(monthPicker, yearMonthMap.get(selectedYear), monthPicker.getValue());
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle("选择统计月份")
                .setView(content)
                .setPositiveButton("确定", (dialog, which) -> {
                    int selectedYear = years.get(yearPicker.getValue());
                    int selectedMonth = monthPicker.getValue();
                    Calendar selected = Calendar.getInstance();
                    selected.set(selectedYear, selectedMonth - 1, 1, 0, 0, 0);
                    selected.set(Calendar.MILLISECOND, 0);
                    returnStatsAnchorDateMs = selected.getTimeInMillis();
                    renderReturnStatsTable(allCurvePoints);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private Map<Integer, boolean[]> buildYearMonthAvailability(List<CurvePoint> points) {
        Map<Integer, boolean[]> yearMonthMap = new TreeMap<>();
        if (points == null) {
            return yearMonthMap;
        }
        for (CurvePoint point : points) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(point.getTimestamp());
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            boolean[] months = yearMonthMap.get(year);
            if (months == null) {
                months = new boolean[13];
                yearMonthMap.put(year, months);
            }
            months[month] = true;
        }
        return yearMonthMap;
    }

    private void applyMonthPickerRange(NumberPicker picker, @Nullable boolean[] months, int preferredMonth) {
        int minMonth = 1;
        int maxMonth = 12;
        if (months != null) {
            minMonth = 13;
            maxMonth = 0;
            for (int m = 1; m <= 12; m++) {
                if (months[m]) {
                    minMonth = Math.min(minMonth, m);
                    maxMonth = Math.max(maxMonth, m);
                }
            }
            if (minMonth > maxMonth) {
                minMonth = 1;
                maxMonth = 12;
            }
        }
        picker.setMinValue(minMonth);
        picker.setMaxValue(maxMonth);
        int month = preferredMonth;
        if (month < minMonth || month > maxMonth) {
            month = minMonth;
        }
        if (months != null && !months[month]) {
            month = minMonth;
            for (int m = minMonth; m <= maxMonth; m++) {
                if (months[m]) {
                    month = m;
                    break;
                }
            }
        }
        picker.setValue(month);
    }

    private void setupRangeToggle() {
        binding.toggleTimeRange.check(mapRangeButtonId(selectedRange));
        binding.toggleTimeRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnRange1d) {
                selectedRange = AccountTimeRange.D1;
            } else if (checkedId == R.id.btnRange7d) {
                selectedRange = AccountTimeRange.D7;
            } else if (checkedId == R.id.btnRange1m) {
                selectedRange = AccountTimeRange.M1;
            } else if (checkedId == R.id.btnRange3m) {
                selectedRange = AccountTimeRange.M3;
            } else if (checkedId == R.id.btnRange1y) {
                selectedRange = AccountTimeRange.Y1;
            } else {
                selectedRange = AccountTimeRange.ALL;
            }
            clearManualCurveRange(true);
            applyCurrentCurveRangeFromAllPoints();
        });
    }

    private void setupReturnStatsModeToggle() {
        binding.toggleReturnStatsMode.check(mapReturnModeButtonId(returnStatsMode));
        binding.toggleReturnStatsMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnReturnDay) {
                returnStatsMode = ReturnStatsMode.DAY;
            } else if (checkedId == R.id.btnReturnMonth) {
                returnStatsMode = ReturnStatsMode.MONTH;
            } else if (checkedId == R.id.btnReturnYear) {
                returnStatsMode = ReturnStatsMode.YEAR;
            } else {
                returnStatsMode = ReturnStatsMode.STAGE;
            }
            renderReturnStatsTable(allCurvePoints);
        });
    }

    private void setupReturnStatsValueToggle() {
        binding.toggleReturnsValueMode.check(returnValueMode == ReturnValueMode.AMOUNT
                ? R.id.btnReturnsAmount
                : R.id.btnReturnsRate);
        binding.toggleReturnsValueMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            returnValueMode = checkedId == R.id.btnReturnsAmount
                    ? ReturnValueMode.AMOUNT
                    : ReturnValueMode.RATE;
            renderReturnStatsTable(allCurvePoints);
        });
    }

    private void setupTradePnlSideToggle() {
        binding.toggleTradePnlSide.check(mapTradePnlSideButtonId(tradePnlSideMode));
        binding.toggleTradePnlSide.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnTradePnlBuy) {
                tradePnlSideMode = TradePnlSideMode.BUY;
            } else if (checkedId == R.id.btnTradePnlSell) {
                tradePnlSideMode = TradePnlSideMode.SELL;
            } else {
                tradePnlSideMode = TradePnlSideMode.ALL;
            }
            refreshTradeStats();
        });
    }

    private int mapRangeButtonId(AccountTimeRange range) {
        if (range == null) {
            return R.id.btnRange7d;
        }
        switch (range) {
            case D1:
                return R.id.btnRange1d;
            case D7:
                return R.id.btnRange7d;
            case M1:
                return R.id.btnRange1m;
            case M3:
                return R.id.btnRange3m;
            case Y1:
                return R.id.btnRange1y;
            case ALL:
            default:
                return R.id.btnRangeAll;
        }
    }

    private int mapReturnModeButtonId(ReturnStatsMode mode) {
        if (mode == ReturnStatsMode.DAY) {
            return R.id.btnReturnDay;
        }
        if (mode == ReturnStatsMode.YEAR) {
            return R.id.btnReturnYear;
        }
        if (mode == ReturnStatsMode.STAGE) {
            return R.id.btnReturnStage;
        }
        return R.id.btnReturnMonth;
    }

    private int mapTradePnlSideButtonId(TradePnlSideMode mode) {
        if (mode == TradePnlSideMode.BUY) {
            return R.id.btnTradePnlBuy;
        }
        if (mode == TradePnlSideMode.SELL) {
            return R.id.btnTradePnlSell;
        }
        return R.id.btnTradePnlAll;
    }

    private void bindLocalMeta() {
        connectedAccount = ACCOUNT;
        connectedServer = SERVER;
        connectedSource = "历史数据（网关离线）";
        connectedGateway = "--";
        connectedUpdate = FormatUtils.formatTime(System.currentTimeMillis());
        connectedLeverageText = "";
        connectedError = "";
        dataQualitySummary = "";
        setConnectionStatus(false);
        updateOverviewHeader();
    }

    private void applyPreloadedCacheIfAvailable() {
        if (preloadManager == null) {
            return;
        }
        AccountStatsPreloadManager.Cache cache = preloadManager.getLatestCache();
        if (cache == null || cache.getSnapshot() == null) {
            return;
        }
        if (System.currentTimeMillis() - cache.getFetchedAt() > AppConstants.ACCOUNT_REFRESH_INTERVAL_MS * 3L) {
            return;
        }
        connectedAccount = cache.getAccount().isEmpty() ? ACCOUNT : cache.getAccount();
        connectedServer = cache.getServer().isEmpty() ? SERVER : cache.getServer();
        connectedSource = normalizeSource(cache.getSource());
        connectedGateway = cache.getGateway().isEmpty() ? "--" : cache.getGateway();
        long updateAt = cache.getUpdatedAt() > 0L ? cache.getUpdatedAt() : cache.getFetchedAt();
        connectedUpdate = FormatUtils.formatTime(updateAt);
        connectedError = cache.getError();

        setConnectionStatus(cache.isConnected());
        updateOverviewHeader();
        applySnapshot(cache.getSnapshot(), cache.isConnected());
    }

    private void updateOverviewHeader() {
        String title = "账户总览-" + connectedAccount;
        if (!connectedLeverageText.isEmpty()) {
            title = title + "-" + connectedLeverageText;
        }
        binding.tvAccountOverviewTitle.setText(title);
        binding.tvAccountMeta.setText("更新时间 " + connectedUpdate);
    }

    private void requestSnapshot() {
        if (loading) {
            return;
        }
        loading = true;
        AccountTimeRange fetchRange = AccountTimeRange.ALL;

        ioExecutor.execute(() -> {
            Mt5BridgeGatewayClient.SnapshotResult remote = gatewayClient.fetch(fetchRange);
            AccountSnapshot snapshot;
            boolean connected;
            String account;
            String server;
            String source;
            String gateway;
            long updatedAt;
            String error;

            if (remote.isSuccess()) {
                snapshot = remote.getSnapshot();
                connected = true;
                account = remote.getAccount(ACCOUNT);
                server = remote.getServer(SERVER);
                source = normalizeSource(remote.getLocalizedSource());
                gateway = remote.getGatewayEndpoint();
                updatedAt = remote.getUpdatedAt() > 0L ? remote.getUpdatedAt() : System.currentTimeMillis();
                error = "";
            } else {
                snapshot = fallbackDataSource.load(fetchRange);
                connected = false;
                account = ACCOUNT;
                server = SERVER;
                source = "历史数据（网关离线）";
                gateway = "Gateway offline";
                updatedAt = System.currentTimeMillis();
                error = remote.getError();
            }

            final AccountSnapshot finalSnapshot = snapshot;
            final boolean finalConnected = connected;
            final String finalAccount = account;
            final String finalServer = server;
            final String finalSource = source;
            final String finalGateway = gateway;
            final String finalUpdate = FormatUtils.formatTime(updatedAt);
            final String finalError = error;

            runOnUiThread(() -> {
                connectedAccount = finalAccount;
                connectedServer = finalServer;
                connectedSource = finalSource;
                connectedGateway = finalGateway;
                connectedUpdate = finalUpdate;
                connectedError = finalError;

                setConnectionStatus(finalConnected);
                updateOverviewHeader();
                applySnapshot(finalSnapshot, finalConnected);
                loading = false;
            });
        });
    }

    private String normalizeSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return "MT5网关";
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("fallback") || normalized.contains("offline")) {
            return "历史数据（网关离线）";
        }
        if (source.contains("缃戝叧") || source.contains("网关")) {
            return "MT5网关";
        }
        if (source.contains("鍘嗗彶") || source.contains("历史")) {
            return "历史数据（网关离线）";
        }
        return source;
    }

    private void setConnectionStatus(boolean connected) {
        binding.tvAccountConnectionStatus.setText(connected ? "已连接账户" : "未连接账户");
        binding.tvAccountConnectionStatus.setBackground(connected
                ? AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected)
                : AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
        binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(this,
                connected ? R.color.bg_primary : R.color.text_secondary));
    }

    private void applySnapshot(AccountSnapshot snapshot, boolean remoteConnected) {
        List<PositionItem> snapshotPositions = snapshot.getPositions() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getPositions());
        List<PositionItem> snapshotPending = snapshot.getPendingOrders() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getPendingOrders());
        List<TradeRecordItem> snapshotTrades = snapshot.getTrades() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getTrades());
        List<CurvePoint> snapshotCurves = snapshot.getCurvePoints() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getCurvePoints());

        if (remoteConnected) {
            mergeCurveHistory(snapshotCurves);
            mergeTradeHistory(snapshotTrades);
            connectedPositionCache = new ArrayList<>(snapshotPositions);
            connectedPendingCache = new ArrayList<>(snapshotPending);
            connectedOverviewCache = snapshot.getOverviewMetrics() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(snapshot.getOverviewMetrics());
        }

        if (!remoteConnected && !connectedPositionCache.isEmpty()) {
            basePositions = new ArrayList<>(connectedPositionCache);
        } else {
            basePositions = snapshotPositions;
        }
        if (snapshotPending.isEmpty()) {
            if (!remoteConnected && !connectedPendingCache.isEmpty()) {
                basePendingOrders = new ArrayList<>(connectedPendingCache);
            } else {
                basePendingOrders = buildPendingOrders(basePositions);
            }
        } else {
            basePendingOrders = snapshotPending;
        }

        List<TradeRecordItem> effectiveTrades = tradeHistory.isEmpty()
                ? snapshotTrades
                : new ArrayList<>(tradeHistory.values());
        List<CurvePoint> effectiveCurves = curveHistory.isEmpty()
                ? snapshotCurves
                : new ArrayList<>(curveHistory.values());
        dataQualitySummary = buildDataQualitySummary(effectiveTrades, effectiveCurves);

        baseTrades = pruneZeroProfitTrades(mergeOpenCloseTrades(effectiveTrades));
        allCurvePoints = normalizeCurvePoints(effectiveCurves);
        ensureReturnStatsAnchor();
        if (!remoteConnected && !connectedOverviewCache.isEmpty()) {
            latestOverviewMetrics = new ArrayList<>(connectedOverviewCache);
        } else {
            latestOverviewMetrics = snapshot.getOverviewMetrics() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(snapshot.getOverviewMetrics());
        }
        connectedLeverageText = extractLeverageText(latestOverviewMetrics);
        updateOverviewHeader();

        List<AccountMetric> overview = buildOverviewMetrics(latestOverviewMetrics);

        overviewAdapter.submitList(overview);
        updateTradeProductOptions();
        renderReturnStatsTable(allCurvePoints);
        applyCurrentCurveRangeFromAllPoints();
        refreshTradeStats();
        refreshPositions();
        refreshTrades();
    }

    private void mergeCurveHistory(List<CurvePoint> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (CurvePoint point : source) {
            if (point == null) {
                continue;
            }
            long ts = point.getTimestamp();
            if (ts <= 0L) {
                continue;
            }
            curveHistory.put(ts, point);
        }
        while (curveHistory.size() > 100_000) {
            Long firstKey = curveHistory.keySet().iterator().next();
            curveHistory.remove(firstKey);
        }
    }

    private void mergeTradeHistory(List<TradeRecordItem> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (TradeRecordItem item : source) {
            if (item == null) {
                continue;
            }
            String key = buildTradeDedupeKey(item);
            tradeHistory.put(key, item);
        }
        while (tradeHistory.size() > 20_000) {
            String firstKey = tradeHistory.keySet().iterator().next();
            tradeHistory.remove(firstKey);
        }
    }

    private String buildDataQualitySummary(List<TradeRecordItem> trades, List<CurvePoint> curves) {
        int tradeCount = trades == null ? 0 : trades.size();
        int curveCount = curves == null ? 0 : curves.size();
        int missingOpen = 0;
        int missingClose = 0;
        if (trades != null) {
            for (TradeRecordItem item : trades) {
                if (item == null) {
                    continue;
                }
                if (resolveOpenTime(item) <= 0L) {
                    missingOpen++;
                }
                if (resolveCloseTime(item) <= 0L) {
                    missingClose++;
                }
            }
        }
        return "交易" + tradeCount
                + "条, 曲线" + curveCount
                + "点, 开仓时间缺失" + missingOpen
                + "条, 平仓时间缺失" + missingClose + "条";
    }

    private List<CurvePoint> normalizeCurvePoints(List<CurvePoint> source) {
        List<CurvePoint> normalized = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            long now = System.currentTimeMillis();
            normalized.add(new CurvePoint(now - 60_000L, ACCOUNT_INITIAL_BALANCE, ACCOUNT_INITIAL_BALANCE));
            normalized.add(new CurvePoint(now, ACCOUNT_INITIAL_BALANCE, ACCOUNT_INITIAL_BALANCE));
            return normalized;
        }

        List<CurvePoint> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));

        double lastEquity = ACCOUNT_INITIAL_BALANCE;
        double lastBalance = ACCOUNT_INITIAL_BALANCE;
        boolean first = true;
        for (CurvePoint point : sorted) {
            long ts = point.getTimestamp() > 0L ? point.getTimestamp() : System.currentTimeMillis();
            double equity = point.getEquity();
            double balance = point.getBalance();

            if (first) {
                if (equity <= 0d && balance <= 0d) {
                    equity = ACCOUNT_INITIAL_BALANCE;
                    balance = ACCOUNT_INITIAL_BALANCE;
                } else {
                    if (equity <= 0d) {
                        equity = balance;
                    }
                    if (balance <= 0d) {
                        balance = equity;
                    }
                }
                first = false;
            } else {
                if (equity <= 0d) {
                    equity = lastEquity;
                }
                if (balance <= 0d) {
                    balance = lastBalance;
                }
            }

            if (equity <= 0d) {
                equity = ACCOUNT_INITIAL_BALANCE;
            }
            if (balance <= 0d) {
                balance = ACCOUNT_INITIAL_BALANCE;
            }
            lastEquity = equity;
            lastBalance = balance;
            normalized.add(new CurvePoint(ts, equity, balance));
        }

        if (normalized.size() == 1) {
            CurvePoint only = normalized.get(0);
            normalized.add(new CurvePoint(only.getTimestamp() + 60_000L, only.getEquity(), only.getBalance()));
        }
        return normalized;
    }

    private List<AccountMetric> buildOverviewMetrics(List<AccountMetric> snapshotOverview) {
        List<AccountMetric> result = new ArrayList<>();
        if (allCurvePoints.isEmpty()) {
            latestCumulativePnl = 0d;
            result.add(new AccountMetric("总资产", "$" + FormatUtils.formatPrice(ACCOUNT_INITIAL_BALANCE)));
            result.add(new AccountMetric("净资产", "$" + FormatUtils.formatPrice(ACCOUNT_INITIAL_BALANCE)));
            result.add(new AccountMetric("可用预付款", "$0.00"));
            result.add(new AccountMetric("保证金", "$0.00"));
            result.add(new AccountMetric("仓位占比", "0.00%"));
            result.add(new AccountMetric("持仓市值", "$0.00"));
            result.add(new AccountMetric("持仓盈亏", "$0.00"));
            result.add(new AccountMetric("持仓收益率", "0.00%"));
            result.add(new AccountMetric("当日盈亏", "$0.00"));
            result.add(new AccountMetric("当日收益率", "0.00%"));
            result.add(new AccountMetric("累计盈亏", "$0.00"));
            result.add(new AccountMetric("累计收益率", "0.00%"));
            return result;
        }

        CurvePoint last = allCurvePoints.get(allCurvePoints.size() - 1);
        double totalAsset = Math.max(0d, last.getBalance());
        double netAsset = Math.max(0d, last.getEquity());

        double margin = metricValue(snapshotOverview, "保证金", "保证金金额", "Margin", "Margin Amount");
        double free = metricValue(snapshotOverview, "可用预付款", "可用资金", "Free Fund", "Available Funds", "Available");
        if (margin <= 0d) {
            margin = Math.max(0d, netAsset * 0.35d);
        }
        if (free <= 0d) {
            free = Math.max(0d, netAsset - margin);
        }

        double marketValue = 0d;
        double positionPnl = 0d;
        for (PositionItem item : basePositions) {
            marketValue += item.getMarketValue();
            positionPnl += item.getTotalPnL();
        }
        double positionRatio = safeDivide(marketValue, Math.max(1d, netAsset));
        double positionPnlRate = safeDivide(positionPnl, Math.max(1d, totalAsset));

        double dayClosedPnl = calcTodayClosedPnl(baseTrades);
        double dayStartAsset = calcTodayStartAsset(totalAsset, dayClosedPnl, allCurvePoints);
        double dayReturn = safeDivide(dayClosedPnl, Math.max(1d, dayStartAsset));

        double initialAsset = Math.max(1d, allCurvePoints.get(0).getBalance());
        double cumulativePnl = totalAsset - initialAsset;
        double cumulativeRate = safeDivide(cumulativePnl, initialAsset);
        latestCumulativePnl = cumulativePnl;

        result.add(new AccountMetric("总资产", "$" + FormatUtils.formatPrice(totalAsset)));
        result.add(new AccountMetric("净资产", "$" + FormatUtils.formatPrice(netAsset)));
        result.add(new AccountMetric("可用预付款", "$" + FormatUtils.formatPrice(free)));
        result.add(new AccountMetric("保证金", "$" + FormatUtils.formatPrice(margin)));
        result.add(new AccountMetric("仓位占比", percent(positionRatio)));
        result.add(new AccountMetric("持仓市值", "$" + FormatUtils.formatPrice(marketValue)));
        result.add(new AccountMetric("持仓盈亏", signedMoney(positionPnl)));
        result.add(new AccountMetric("持仓收益率", percent(positionPnlRate)));
        result.add(new AccountMetric("当日盈亏", signedMoney(dayClosedPnl)));
        result.add(new AccountMetric("当日收益率", percent(dayReturn)));
        result.add(new AccountMetric("累计盈亏", signedMoney(cumulativePnl)));
        result.add(new AccountMetric("累计收益率", percent(cumulativeRate)));
        return result;
    }

    private double calcTodayClosedPnl(List<TradeRecordItem> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0d;
        }
        long start = startOfToday();
        long end = endOfToday();
        double sum = 0d;
        for (TradeRecordItem item : trades) {
            long closeTime = item.getCloseTime() > 0L ? item.getCloseTime() : item.getTimestamp();
            if (closeTime >= start && closeTime <= end) {
                sum += item.getProfit();
            }
        }
        return sum;
    }

    private double calcTodayStartAsset(double currentBalance, double todayClosedPnl, List<CurvePoint> points) {
        if (points != null && !points.isEmpty()) {
            long start = startOfToday();
            for (CurvePoint point : points) {
                if (point.getTimestamp() >= start) {
                    return Math.max(1d, point.getBalance());
                }
            }
        }
        return Math.max(1d, currentBalance - todayClosedPnl);
    }

    private long startOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long endOfToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private double metricValue(List<AccountMetric> metrics, String... names) {
        if (metrics == null || metrics.isEmpty() || names == null || names.length == 0) {
            return 0d;
        }
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String name = trim(MetricNameTranslator.toChinese(metric.getName()));
            for (String candidate : names) {
                if (candidate == null) {
                    continue;
                }
                String query = trim(candidate);
                if (name.equalsIgnoreCase(query) || name.contains(query) || query.contains(name)) {
                    return parseNumber(metric.getValue());
                }
            }
        }
        return 0d;
    }

    private String extractLeverageText(List<AccountMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "";
        }
        double best = 0d;
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String rawName = trim(metric.getName());
            String name = trim(MetricNameTranslator.toChinese(rawName));
            String normalizedName = (name + " " + rawName).toLowerCase(Locale.ROOT);
            boolean leverageField = normalizedName.contains("杠杆")
                    || normalizedName.contains("lever");
            if (!leverageField) {
                continue;
            }
            double parsed = parseLeverageNumber(metric.getValue());
            if (parsed > best) {
                best = parsed;
            }
        }
        if (best <= 0d) {
            return "";
        }
        return String.format(Locale.getDefault(), "%.0fx", best);
    }

    private double parseLeverageNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0d;
        }
        String value = raw.replace(",", "");
        double max = 0d;
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                token.append(c);
            } else if (token.length() > 0) {
                try {
                    max = Math.max(max, Double.parseDouble(token.toString()));
                } catch (Exception ignored) {
                    // ignore parse failure for noisy fragments
                }
                token.setLength(0);
            }
        }
        if (token.length() > 0) {
            try {
                max = Math.max(max, Double.parseDouble(token.toString()));
            } catch (Exception ignored) {
                // ignore parse failure for tail fragment
            }
        }
        return max;
    }

    private double parseNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0d;
        }
        StringBuilder builder = new StringBuilder();
        boolean dot = false;
        boolean sign = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '+' || c == '-') && !sign && builder.length() == 0) {
                builder.append(c);
                sign = true;
                continue;
            }
            if (Character.isDigit(c)) {
                builder.append(c);
                continue;
            }
            if (c == '.' && !dot) {
                builder.append(c);
                dot = true;
            }
        }
        try {
            return Double.parseDouble(builder.toString());
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private List<AccountMetric> buildStatsFallbackMetrics() {
        List<AccountMetric> result = new ArrayList<>();

        double totalPnl = 0d;
        double maxPos = 0d;
        for (PositionItem item : basePositions) {
            totalPnl += item.getTotalPnL();
            maxPos = Math.max(maxPos, item.getPositionRatio());
        }

        int buy = 0;
        int sell = 0;
        int win = 0;
        int loss = 0;
        for (TradeRecordItem item : baseTrades) {
            if ("BUY".equalsIgnoreCase(item.getSide()) || "Buy".equalsIgnoreCase(item.getSide())) {
                buy++;
            } else {
                sell++;
            }
            if (item.getProfit() >= 0d) {
                win++;
            } else {
                loss++;
            }
        }

        double maxDd = 0d;
        if (displayedCurvePoints.size() > 1) {
            double peak = displayedCurvePoints.get(0).getEquity();
            for (CurvePoint point : displayedCurvePoints) {
                peak = Math.max(peak, point.getEquity());
                maxDd = Math.max(maxDd, safeDivide(peak - point.getEquity(), peak));
            }
        }

        result.add(new AccountMetric("累计收益额", signedMoney(totalPnl)));
        result.add(new AccountMetric("总交易次数", String.valueOf(baseTrades.size())));
        result.add(new AccountMetric("买入次数", String.valueOf(buy)));
        result.add(new AccountMetric("卖出次数", String.valueOf(sell)));
        result.add(new AccountMetric("胜率", percent(safeDivide(win, Math.max(1d, win + loss)))));
        result.add(new AccountMetric("盈利交易数/亏损交易数", win + " / " + loss));
        result.add(new AccountMetric("最大回撤", percent(maxDd)));
        result.add(new AccountMetric("单一持仓最大占比", percent(maxPos)));
        return result;
    }

    private void refreshTradeStats() {
        statsAdapter.submitList(buildTradeStatsMetrics(baseTrades, allCurvePoints));
        List<TradePnlBarChartView.Entry> entries = buildTradePnlChartEntries(baseTrades, tradePnlSideMode);
        binding.tradePnlBarChart.setEntries(entries);

        double totalPnl = 0d;
        for (TradePnlBarChartView.Entry entry : entries) {
            totalPnl += entry.pnl;
        }
        String sideLabel;
        if (tradePnlSideMode == TradePnlSideMode.BUY) {
            sideLabel = "买入";
        } else if (tradePnlSideMode == TradePnlSideMode.SELL) {
            sideLabel = "卖出";
        } else {
            sideLabel = "全部";
        }
        String pnlText = signedMoney(totalPnl);
        String summaryText = String.format(
                Locale.getDefault(),
                "全周期总计盈亏（%s）: %s",
                sideLabel,
                pnlText);
        SpannableString summarySpan = new SpannableString(summaryText);
        int pnlStart = summaryText.lastIndexOf(pnlText);
        if (pnlStart >= 0) {
            int pnlColor = ContextCompat.getColor(this,
                    totalPnl >= 0d ? R.color.accent_green : R.color.accent_red);
            summarySpan.setSpan(new ForegroundColorSpan(pnlColor),
                    pnlStart, pnlStart + pnlText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        binding.tvTradePnlSummary.setText(summarySpan);
        binding.tvTradePnlLegend.setVisibility(View.GONE);
    }

    private List<AccountMetric> buildTradeStatsMetrics(List<TradeRecordItem> trades, List<CurvePoint> curvePoints) {
        List<AccountMetric> result = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            result.add(new AccountMetric("最近交易", "--"));
            result.add(new AccountMetric("交易次数", "0"));
            result.add(new AccountMetric("盈利交易", "0次 (0.00%)"));
            result.add(new AccountMetric("亏损交易", "0次 (0.00%)"));
            result.add(new AccountMetric("最好交易", "--"));
            result.add(new AccountMetric("最差交易", "--"));
            result.add(new AccountMetric("毛利", "--"));
            result.add(new AccountMetric("毛损", "--"));
            result.add(new AccountMetric("最大连续盈利", "--"));
            result.add(new AccountMetric("最大连续亏损", "--"));
            result.add(new AccountMetric("利润因子", "--"));
            result.add(new AccountMetric("夏普比率", "--"));
            result.add(new AccountMetric("每周交易次数", "--"));
            result.add(new AccountMetric("平均持仓时间", "--"));
            return result;
        }

        List<TradeRecordItem> ordered = new ArrayList<>(trades);
        ordered.sort(Comparator.comparingLong(this::resolveCloseTime));

        int winCount = 0;
        int lossCount = 0;
        double grossProfit = 0d;
        double grossLoss = 0d;
        double bestTrade = -Double.MAX_VALUE;
        double worstTrade = Double.MAX_VALUE;
        boolean hasBestTrade = false;
        boolean hasWorstTrade = false;

        long durationSumMs = 0L;
        int durationCount = 0;
        long firstClose = Long.MAX_VALUE;
        long lastClose = 0L;

        int currentWinStreak = 0;
        int maxWinStreak = 0;
        double currentWinAmount = 0d;
        double maxWinAmount = 0d;

        int currentLossStreak = 0;
        int maxLossStreak = 0;
        double currentLossAmount = 0d;
        double maxLossAmount = 0d;

        for (TradeRecordItem item : ordered) {
            double profit = item.getProfit();
            long openTime = resolveOpenTime(item);
            long closeTime = resolveCloseTime(item);

            firstClose = Math.min(firstClose, closeTime);
            lastClose = Math.max(lastClose, closeTime);

            if (closeTime >= openTime) {
                durationSumMs += (closeTime - openTime);
                durationCount++;
            }

            if (profit > 0d) {
                winCount++;
                grossProfit += profit;
                currentWinStreak++;
                currentWinAmount += profit;
                if (currentWinStreak > maxWinStreak
                        || (currentWinStreak == maxWinStreak && currentWinAmount > maxWinAmount)) {
                    maxWinStreak = currentWinStreak;
                    maxWinAmount = currentWinAmount;
                }
                currentLossStreak = 0;
                currentLossAmount = 0d;
                bestTrade = Math.max(bestTrade, profit);
                worstTrade = Math.min(worstTrade, profit);
                hasBestTrade = true;
                hasWorstTrade = true;
            } else if (profit < 0d) {
                lossCount++;
                grossLoss += profit;
                currentLossStreak++;
                currentLossAmount += profit;
                if (currentLossStreak > maxLossStreak
                        || (currentLossStreak == maxLossStreak && currentLossAmount < maxLossAmount)) {
                    maxLossStreak = currentLossStreak;
                    maxLossAmount = currentLossAmount;
                }
                currentWinStreak = 0;
                currentWinAmount = 0d;
                bestTrade = Math.max(bestTrade, profit);
                worstTrade = Math.min(worstTrade, profit);
                hasBestTrade = true;
                hasWorstTrade = true;
            } else {
                currentWinStreak = 0;
                currentWinAmount = 0d;
                currentLossStreak = 0;
                currentLossAmount = 0d;
            }
        }

        int totalTrades = winCount + lossCount;
        double winRate = safeDivide(winCount, Math.max(1d, totalTrades));
        double lossRate = safeDivide(lossCount, Math.max(1d, totalTrades));
        double profitFactor = Math.abs(grossLoss) < 1e-9
                ? (grossProfit > 0d ? Double.POSITIVE_INFINITY : 0d)
                : grossProfit / Math.abs(grossLoss);

        String sharpe = calcBalanceSharpe(curvePoints);
        long avgDurationMs = durationCount <= 0 ? 0L : durationSumMs / durationCount;
        String avgDurationText = durationCount <= 0 ? "--" : formatDuration(avgDurationMs);

        double weeks = 1d;
        if (firstClose < Long.MAX_VALUE && lastClose > firstClose) {
            weeks = Math.max(1d, (lastClose - firstClose) / (7d * 24d * 60d * 60d * 1000d));
        }
        String perWeek = Math.round(totalTrades / weeks) + " 次/周";

        result.add(new AccountMetric("最近交易", formatRelativeTime(lastClose)));
        result.add(new AccountMetric("交易次数", totalTrades + " 次"));
        result.add(new AccountMetric("盈利交易", winCount + "次 (" + percentRaw(winRate) + ")"));
        result.add(new AccountMetric("亏损交易", lossCount + "次 (" + percentRaw(lossRate) + ")"));
        result.add(new AccountMetric("最好交易", hasBestTrade ? signedMoney(bestTrade) : "--"));
        result.add(new AccountMetric("最差交易", hasWorstTrade ? signedMoney(worstTrade) : "--"));
        result.add(new AccountMetric("毛利", signedMoney(grossProfit)));
        result.add(new AccountMetric("毛损", signedMoney(grossLoss)));
        result.add(new AccountMetric("最大连续盈利", formatStreak(maxWinStreak, maxWinAmount)));
        result.add(new AccountMetric("最大连续亏损", formatStreak(maxLossStreak, maxLossAmount)));
        result.add(new AccountMetric("利润因子", Double.isInfinite(profitFactor)
                ? "∞"
                : String.format(Locale.getDefault(), "%.2f", profitFactor)));
        result.add(new AccountMetric("夏普比率", sharpe));
        result.add(new AccountMetric("每周交易次数", perWeek));
        result.add(new AccountMetric("平均持仓时间", avgDurationText));
        return result;
    }

    private List<TradePnlBarChartView.Entry> buildTradePnlChartEntries(List<TradeRecordItem> trades,
                                                                        TradePnlSideMode sideMode) {
        List<TradePnlBarChartView.Entry> result = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            return result;
        }

        Map<String, Double> byCode = new LinkedHashMap<>();
        for (TradeRecordItem item : trades) {
            if (!matchesSideMode(item, sideMode)) {
                continue;
            }
            String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                code = trim(item.getProductName());
            }
            if (code.isEmpty()) {
                code = "UNKNOWN";
            }
            byCode.put(code, byCode.getOrDefault(code, 0d) + item.getProfit());
        }

        List<Map.Entry<String, Double>> ordered = new ArrayList<>(byCode.entrySet());
        ordered.sort((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));
        for (Map.Entry<String, Double> entry : ordered) {
            result.add(new TradePnlBarChartView.Entry(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private boolean matchesSideMode(TradeRecordItem item, TradePnlSideMode sideMode) {
        if (sideMode == TradePnlSideMode.ALL) {
            return true;
        }
        String side = trim(item.getSide()).toLowerCase(Locale.ROOT);
        if (sideMode == TradePnlSideMode.BUY) {
            return side.contains("buy") || side.contains("多") || side.contains("买");
        }
        return side.contains("sell") || side.contains("空") || side.contains("卖");
    }

    private long resolveOpenTime(TradeRecordItem item) {
        long open = item.getOpenTime();
        return open > 0L ? open : item.getTimestamp();
    }

    private long resolveCloseTime(TradeRecordItem item) {
        long close = item.getCloseTime();
        return close > 0L ? close : item.getTimestamp();
    }

    private String calcBalanceSharpe(List<CurvePoint> points) {
        if (points == null || points.size() < 3) {
            return "--";
        }
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            double prev = points.get(i - 1).getBalance();
            double current = points.get(i).getBalance();
            if (prev <= 0d) {
                continue;
            }
            returns.add((current - prev) / prev);
        }
        if (returns.size() < 2) {
            return "--";
        }
        double mean = returns.stream().mapToDouble(v -> v).average().orElse(0d);
        double vol = calcStd(returns);
        if (vol <= 1e-9) {
            return "--";
        }
        double sharpe = (mean * Math.sqrt(365d)) / vol;
        return String.format(Locale.getDefault(), "%.2f", sharpe);
    }

    private String formatRelativeTime(long timestampMs) {
        if (timestampMs <= 0L) {
            return "--";
        }
        long diff = Math.max(0L, System.currentTimeMillis() - timestampMs);
        long minuteMs = 60L * 1000L;
        long hourMs = 60L * minuteMs;
        long dayMs = 24L * hourMs;
        long monthMs = 30L * dayMs;
        if (diff < minuteMs) {
            return "刚刚";
        }
        if (diff < hourMs) {
            return (diff / minuteMs) + " 分前";
        }
        if (diff < dayMs) {
            return (diff / hourMs) + " 小时前";
        }
        if (diff < monthMs) {
            return (diff / dayMs) + " 天前";
        }
        return (diff / monthMs) + " 月前";
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0L) {
            return "--";
        }
        long minuteMs = 60L * 1000L;
        long hourMs = 60L * minuteMs;
        long dayMs = 24L * hourMs;

        long days = durationMs / dayMs;
        long hours = (durationMs % dayMs) / hourMs;
        long minutes = (durationMs % hourMs) / minuteMs;

        if (days > 0) {
            return String.format(Locale.getDefault(), "%d天%d小时", days, hours);
        }
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d小时%d分", hours, minutes);
        }
        return Math.max(1L, minutes) + " 分钟";
    }

    private String percentRaw(double ratio) {
        return String.format(Locale.getDefault(), "%.2f%%", ratio * 100d);
    }

    private String formatStreak(int count, double amount) {
        if (count <= 0) {
            return "--";
        }
        return String.format(Locale.getDefault(), "%d次 (%s)", count, signedMoney(amount));
    }

    private void applyCurrentCurveRangeFromAllPoints() {
        if (manualCurveRangeEnabled) {
            List<CurvePoint> manual = filterCurveByManualRange(
                    allCurvePoints,
                    manualCurveRangeStartMs,
                    manualCurveRangeEndMs
            );
            if (manual.size() >= 2) {
                displayedCurvePoints = manual;
                renderCurveWithIndicators(displayedCurvePoints);
                return;
            }
            manualCurveRangeEnabled = false;
        }
        displayedCurvePoints = filterCurveByRange(allCurvePoints, selectedRange);
        renderCurveWithIndicators(displayedCurvePoints);
    }

    private List<CurvePoint> filterCurveByManualRange(List<CurvePoint> source, long startInclusive, long endInclusive) {
        List<CurvePoint> filtered = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return filtered;
        }
        for (CurvePoint point : source) {
            long ts = point.getTimestamp();
            if (ts >= startInclusive && ts <= endInclusive) {
                filtered.add(point);
            }
        }
        return filtered;
    }

    private List<CurvePoint> filterCurveByRange(List<CurvePoint> source, AccountTimeRange range) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        if (range == null || range == AccountTimeRange.ALL) {
            return new ArrayList<>(source);
        }

        long durationMs;
        switch (range) {
            case D1:
                durationMs = 24L * 60L * 60L * 1000L;
                break;
            case D7:
                durationMs = 7L * 24L * 60L * 60L * 1000L;
                break;
            case M1:
                durationMs = 30L * 24L * 60L * 60L * 1000L;
                break;
            case M3:
                durationMs = 90L * 24L * 60L * 60L * 1000L;
                break;
            case Y1:
                durationMs = 365L * 24L * 60L * 60L * 1000L;
                break;
            case ALL:
            default:
                return new ArrayList<>(source);
        }

        long end = source.get(source.size() - 1).getTimestamp();
        long start = end - durationMs;
        List<CurvePoint> filtered = new ArrayList<>();
        for (CurvePoint point : source) {
            if (point.getTimestamp() >= start) {
                filtered.add(point);
            }
        }
        if (filtered.size() >= 2) {
            return filtered;
        }
        if (source.size() >= 2) {
            return new ArrayList<>(source.subList(source.size() - 2, source.size()));
        }
        return new ArrayList<>(source);
    }

    private void renderCurveWithIndicators(List<CurvePoint> points) {
        curveBaseBalance = resolveCurvePercentBase(points);
        binding.equityCurveView.setBaseBalance(curveBaseBalance);
        binding.equityCurveView.setPoints(points);
        defaultCurveMeta = buildCurveMeta(points);
        binding.tvCurveMeta.setText(defaultCurveMeta);
        indicatorAdapter.submitList(buildCurveIndicators(points));
    }

    private double resolveCurvePercentBase(List<CurvePoint> points) {
        if (points != null && !points.isEmpty()) {
            double firstBalance = points.get(0).getBalance();
            if (firstBalance > 0d) {
                return Math.max(1e-9, firstBalance);
            }
        }
        if (allCurvePoints != null && !allCurvePoints.isEmpty()) {
            double allFirstBalance = allCurvePoints.get(0).getBalance();
            if (allFirstBalance > 0d) {
                return Math.max(1e-9, allFirstBalance);
            }
        }
        return Math.max(1e-9, ACCOUNT_INITIAL_BALANCE);
    }

    private String buildCurveMeta(List<CurvePoint> points) {
        if (points == null || points.isEmpty()) {
            return "--";
        }
        double start = points.get(0).getBalance();
        double current = points.get(points.size() - 1).getBalance();
        double peak = start;
        double valley = start;
        double currentEquity = points.get(points.size() - 1).getEquity();
        int peakIndex = 0;
        int valleyIndex = 0;
        for (int i = 1; i < points.size(); i++) {
            double value = points.get(i).getBalance();
            if (value >= peak) {
                peak = value;
                peakIndex = i;
            }
            if (value <= valley) {
                valley = value;
                valleyIndex = i;
            }
        }
        double drawdown = safeDivide(peak - valley, peak);
        String summary = String.format(Locale.getDefault(),
                "起点净值 $%s | 当前净值 $%s | 峰值 %s | 谷值 %s | 最大回撤 %.2f%% | 收益率 %+.2f%%",
                FormatUtils.formatPrice(start),
                FormatUtils.formatPrice(current),
                FormatUtils.formatTime(points.get(peakIndex).getTimestamp()),
                FormatUtils.formatTime(points.get(valleyIndex).getTimestamp()),
                drawdown * 100d,
                safeDivide(current - start, Math.max(1d, start)) * 100d);
        summary = summary + " | 当前净值 $" + FormatUtils.formatPrice(currentEquity);
        return summary;
    }

    private List<AccountMetric> buildCurveIndicators(List<CurvePoint> points) {
        List<AccountMetric> result = new ArrayList<>();
        if (points == null || points.size() < 2) {
            result.add(new AccountMetric("近1日收益", "--"));
            result.add(new AccountMetric("近7日收益", "--"));
            result.add(new AccountMetric("近30日收益", "--"));
            result.add(new AccountMetric("累计收益", "--"));
            result.add(new AccountMetric("最大回撤", "--"));
            result.add(new AccountMetric("夏普比率", "--"));
            return result;
        }

        List<CurvePoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));

        CurvePoint latest = sorted.get(sorted.size() - 1);
        double latestBalance = Math.max(1e-9, latest.getBalance());
        long latestTs = latest.getTimestamp();

        double r1 = calcLookbackReturn(sorted, latestTs, latestBalance, 1);
        double r7 = calcLookbackReturn(sorted, latestTs, latestBalance, 7);
        double r30 = calcLookbackReturn(sorted, latestTs, latestBalance, 30);

        double startBalance = Math.max(1e-9, sorted.get(0).getBalance());
        double cumulative = safeDivide(latestBalance - startBalance, startBalance);

        double maxDrawdownRate = calcMaxDrawdownRate(sorted);
        String maxDrawdownText = String.format(Locale.getDefault(), "%.2f%%", maxDrawdownRate * 100d);

        String sharpe = calcCurveSharpe(sorted);

        result.add(new AccountMetric("近1日收益", percent(r1)));
        result.add(new AccountMetric("近7日收益", percent(r7)));
        result.add(new AccountMetric("近30日收益", percent(r30)));
        result.add(new AccountMetric("累计收益", percent(cumulative)));
        result.add(new AccountMetric("最大回撤", maxDrawdownText));
        result.add(new AccountMetric("夏普比率", sharpe));
        return result;
    }

    private double calcLookbackReturn(List<CurvePoint> points, long latestTs, double latestBalance, int days) {
        long lookbackMs = days * 24L * 60L * 60L * 1000L;
        long targetTs = latestTs - lookbackMs;
        CurvePoint startPoint = findNearestPointAtOrBefore(points, targetTs);
        if (startPoint == null) {
            startPoint = points.get(0);
        }
        double startBalance = Math.max(1e-9, startPoint.getBalance());
        return safeDivide(latestBalance - startBalance, startBalance);
    }

    @Nullable
    private CurvePoint findNearestPointAtOrBefore(List<CurvePoint> points, long targetTs) {
        CurvePoint candidate = null;
        for (CurvePoint point : points) {
            if (point.getTimestamp() <= targetTs) {
                candidate = point;
            } else {
                break;
            }
        }
        return candidate;
    }

    private double calcMaxDrawdownRate(List<CurvePoint> points) {
        double peak = Math.max(1e-9, points.get(0).getBalance());
        double maxDrawdown = 0d;
        for (CurvePoint point : points) {
            double balance = Math.max(1e-9, point.getBalance());
            if (balance > peak) {
                peak = balance;
            }
            double drawdown = safeDivide(balance - peak, peak);
            maxDrawdown = Math.min(maxDrawdown, drawdown);
        }
        return maxDrawdown;
    }

    private String calcCurveSharpe(List<CurvePoint> points) {
        Map<Integer, Double> dailyClose = new TreeMap<>();
        Calendar calendar = Calendar.getInstance();
        for (CurvePoint point : points) {
            calendar.setTimeInMillis(point.getTimestamp());
            int key = calendar.get(Calendar.YEAR) * 10_000
                    + (calendar.get(Calendar.MONTH) + 1) * 100
                    + calendar.get(Calendar.DAY_OF_MONTH);
            dailyClose.put(key, point.getBalance());
        }
        if (dailyClose.size() < 2) {
            return "--";
        }
        List<Double> closes = new ArrayList<>(dailyClose.values());
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            double prev = Math.max(1e-9, closes.get(i - 1));
            returns.add(safeDivide(closes.get(i) - prev, prev));
        }
        if (returns.size() < 2) {
            return "--";
        }
        double mean = returns.stream().mapToDouble(v -> v).average().orElse(0d);
        double std = calcStd(returns);
        if (std <= 1e-12) {
            return "0.00";
        }
        double sharpe = mean / std * Math.sqrt(365d);
        return String.format(Locale.getDefault(), "%.2f", sharpe);
    }

    private void renderReturnStatsTable(List<CurvePoint> source) {
        binding.tableMonthlyReturns.removeAllViews();
        if (source == null || source.size() < 2) {
            binding.tvMonthlyReturnsTitle.setText("收益率统计");
            binding.tvReturnsPeriod.setText("--");
            binding.tvMonthlyReturnsHint.setText("暂无收益统计数据");
            return;
        }
        long referenceTime = resolveReturnStatsReferenceTime(source);
        binding.tvReturnsPeriod.setText(formatMonthLabel(referenceTime));
        List<CurvePoint> scoped = source;
        if (scoped.size() < 2) {
            scoped = new ArrayList<>();
            scoped.add(source.get(0));
            scoped.add(source.get(Math.min(1, source.size() - 1)));
        }
        switch (returnStatsMode) {
            case DAY:
                binding.tvReturnsPeriod.setVisibility(View.VISIBLE);
                binding.tvReturnsPeriod.setClickable(true);
                binding.tvReturnsPeriod.setText(formatMonthLabel(referenceTime));
                renderDailyReturnsTable(scoped, referenceTime);
                break;
            case YEAR:
                binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                binding.tvReturnsPeriod.setClickable(false);
                renderYearlyReturnsTable(scoped);
                break;
            case STAGE:
                binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                binding.tvReturnsPeriod.setClickable(false);
                renderStageReturnsTable(scoped, scoped.get(scoped.size() - 1).getTimestamp());
                break;
            case MONTH:
            default:
                binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                binding.tvReturnsPeriod.setClickable(false);
                renderMonthlyReturnsTable(scoped);
                break;
        }
    }

    private String formatMonthLabel(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        return String.format(Locale.getDefault(), "%d年%d月",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1);
    }

    private void ensureReturnStatsAnchor() {
        if (allCurvePoints == null || allCurvePoints.isEmpty()) {
            returnStatsAnchorDateMs = 0L;
            return;
        }
        long latest = allCurvePoints.get(allCurvePoints.size() - 1).getTimestamp();
        if (returnStatsAnchorDateMs <= 0L || returnStatsAnchorDateMs > latest) {
            returnStatsAnchorDateMs = latest;
        }
    }

    private long resolveReturnStatsReferenceTime(List<CurvePoint> source) {
        long latest = source.get(source.size() - 1).getTimestamp();
        if (returnStatsAnchorDateMs <= 0L) {
            returnStatsAnchorDateMs = latest;
            return latest;
        }
        return Math.min(returnStatsAnchorDateMs, latest);
    }

    private long endOfDay(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private void renderDailyReturnsTable(List<CurvePoint> source, long referenceTime) {
        List<CurvePoint> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));
        if (sorted.size() < 2) {
            binding.tvMonthlyReturnsHint.setText("暂无日收益统计数据");
            return;
        }

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(referenceTime);
        int year = target.get(Calendar.YEAR);
        int month = target.get(Calendar.MONTH);

        binding.tvMonthlyReturnsTitle.setText("日收益统计");
        binding.tvMonthlyReturnsHint.setText("点击具体日期可查看该日净值曲线区间");

        Map<Integer, DayBucket> dayBuckets = new LinkedHashMap<>();
        Map<Integer, Double> closeByDay = new LinkedHashMap<>();
        List<Integer> dayOrder = new ArrayList<>();
        for (CurvePoint point : sorted) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(point.getTimestamp());
            int y = calendar.get(Calendar.YEAR);
            int m = calendar.get(Calendar.MONTH);
            int d = calendar.get(Calendar.DAY_OF_MONTH);
            int dayKey = y * 10_000 + (m + 1) * 100 + d;

            closeByDay.put(dayKey, point.getBalance());
            if (dayOrder.isEmpty() || dayOrder.get(dayOrder.size() - 1) != dayKey) {
                dayOrder.add(dayKey);
            }

            if (y == year && m == month) {
                DayBucket bucket = dayBuckets.get(d);
                if (bucket == null) {
                    bucket = new DayBucket(d);
                    dayBuckets.put(d, bucket);
                }
                if (bucket.startMs == 0L || point.getTimestamp() < bucket.startMs) {
                    bucket.startMs = point.getTimestamp();
                }
                if (point.getTimestamp() >= bucket.endMs) {
                    bucket.endMs = point.getTimestamp();
                    bucket.closeEquity = point.getBalance();
                }
            }
        }

        rebuildDailyTableV3(binding.tableMonthlyReturns, year, month, dayBuckets, closeByDay, dayOrder);
    }

    private void renderMonthlyReturnsTable(List<CurvePoint> source) {
        binding.tvMonthlyReturnsTitle.setText("月收益统计");
        binding.tvMonthlyReturnsHint.setText("点击月份可查看对应区间净值曲线");

        List<YearlyReturnRow> rows = buildMonthlyReturnRows(source);
        if (rows.isEmpty()) {
            binding.tvMonthlyReturnsHint.setText("暂无月收益统计数据");
            return;
        }
        rebuildMonthlyTableTwoRowsV3(binding.tableMonthlyReturns, rows);
    }

    private void rebuildDailyTable(TableLayout table,
                                   int year,
                                   int month,
                                   Map<Integer, DayBucket> dayBuckets,
                                   Map<Integer, Double> closeByDay,
                                   List<Integer> dayOrder) {
        table.removeAllViews();
        table.addView(createSimpleHeaderRow(new String[]{"一", "二", "三", "四", "五", "六", "日"}, 46));

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(year, month, 1, 0, 0, 0);
        firstDay.set(Calendar.MILLISECOND, 0);
        int firstWeek = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);

        int totalCells = firstWeek + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7d);
        int day = 1;
        for (int row = 0; row < rows; row++) {
            TableRow tableRow = new TableRow(this);
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || day > daysInMonth) {
                    tableRow.addView(createReturnsCell("", 46, false, null, null));
                    continue;
                }

                DayBucket bucket = dayBuckets.get(day);
                if (bucket == null) {
                    CharSequence plainLabel = buildLabelValueText(day + "日", "--", null);
                    tableRow.addView(createReturnsCell(plainLabel, 46, false, null, null));
                } else {
                    int key = year * 10_000 + (month + 1) * 100 + day;
                    double prevClose = bucket.closeEquity;
                    int indexOfDay = dayOrder.indexOf(key);
                    if (indexOfDay > 0) {
                        Integer prevKey = dayOrder.get(indexOfDay - 1);
                        Double prev = closeByDay.get(prevKey);
                        if (prev != null && prev > 0d) {
                            prevClose = prev;
                        }
                    }
                    double dayAmount = bucket.closeEquity - prevClose;
                    double dayReturn = safeDivide(bucket.closeEquity - prevClose, prevClose);
                    int color = ContextCompat.getColor(this, dayReturn >= 0d ? R.color.accent_green : R.color.accent_red);
                    String valueText = formatReturnValue(dayReturn, dayAmount, true);
                    long startMs = bucket.startMs;
                    long endMs = bucket.endMs;
                    tableRow.addView(createReturnsCell(
                            buildLabelValueText(day + "日", valueText, color),
                            46,
                            false,
                            null,
                            v -> applyCurveRangeFromTableSelection(startMs, endMs)));
                }
                day++;
            }
            table.addView(tableRow);
        }
    }

    private void rebuildMonthlyTableTwoRows(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();

        for (YearlyReturnRow row : rows) {
            View.OnClickListener yearClick = null;
            if (row.startMs > 0L && row.endMs > row.startMs) {
                long startMs = row.startMs;
                long endMs = row.endMs;
                yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
            }

            TableRow firstRow = new TableRow(this);
            int yearColor = ContextCompat.getColor(this, row.yearReturnRate >= 0d ? R.color.accent_green : R.color.accent_red);
            String yearValueText = formatReturnValue(row.yearReturnRate, row.yearReturnAmount);
            TextView yearCell = createReturnsCell(
                    buildLabelValueText(row.year + "年", yearValueText, yearColor),
                    72,
                    false,
                    null,
                    yearClick);
            TableRow.LayoutParams yearParams = (TableRow.LayoutParams) yearCell.getLayoutParams();
            yearParams.height = dpToPx(102);
            yearCell.setLayoutParams(yearParams);
            firstRow.addView(yearCell);
            for (int month = 1; month <= 6; month++) {
                firstRow.addView(createMonthReturnCell(month, row.monthly.get(month), 46));
            }
            table.addView(firstRow);

            TableRow secondRow = new TableRow(this);
            TextView placeholder = createReturnsCell("", 72, false, null, null);
            placeholder.setVisibility(View.INVISIBLE);
            placeholder.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            secondRow.addView(placeholder);
            for (int month = 7; month <= 12; month++) {
                secondRow.addView(createMonthReturnCell(month, row.monthly.get(month), 46));
            }
            table.addView(secondRow);
        }
    }

    private TextView createMonthReturnCell(int month, @Nullable MonthReturnInfo info, int widthDp) {
        if (info == null || !info.hasData) {
            return createReturnsCell(buildLabelValueText(month + "月", "--", null), widthDp, false, null, null);
        }
        int textColor = ContextCompat.getColor(this, info.returnRate >= 0d ? R.color.accent_green : R.color.accent_red);
        String text = formatReturnValue(info.returnRate, info.returnAmount);
        long startMs = info.startMs;
        long endMs = info.endMs;
        return createReturnsCell(
                buildLabelValueText(month + "月", text, textColor),
                widthDp,
                false,
                null,
                v -> applyCurveRangeFromTableSelection(startMs, endMs));
    }

    private void renderYearlyReturnsTable(List<CurvePoint> source) {
        binding.tvMonthlyReturnsTitle.setText("年收益统计");
        binding.tvMonthlyReturnsHint.setText("点击年份可查看该年净值曲线");

        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();

        List<CurvePoint> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));
        Map<Integer, PeriodBucket> yearBuckets = new TreeMap<>();
        for (CurvePoint point : sorted) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(point.getTimestamp());
            int year = calendar.get(Calendar.YEAR);
            PeriodBucket bucket = yearBuckets.get(year);
            if (bucket == null) {
                bucket = new PeriodBucket();
                yearBuckets.put(year, bucket);
            }
            if (bucket.startMs == 0L || point.getTimestamp() < bucket.startMs) {
                bucket.startMs = point.getTimestamp();
            }
            if (point.getTimestamp() >= bucket.endMs) {
                bucket.endMs = point.getTimestamp();
                bucket.closeEquity = point.getBalance();
            }
        }

        if (yearBuckets.isEmpty()) {
            binding.tvMonthlyReturnsHint.setText("暂无年收益统计数据");
            return;
        }

        double previousClose = Math.max(1e-9, sorted.get(0).getBalance());
        TableRow row = null;
        int colCount = 0;
        for (Map.Entry<Integer, PeriodBucket> entry : yearBuckets.entrySet()) {
            if (row == null || colCount == 3) {
                row = new TableRow(this);
                table.addView(row);
                colCount = 0;
            }
            PeriodBucket bucket = entry.getValue();
            double yearAmount = bucket.closeEquity - previousClose;
            double yearReturn = safeDivide(yearAmount, previousClose);
            previousClose = bucket.closeEquity;

            int color = ContextCompat.getColor(this, yearReturn >= 0d ? R.color.accent_green : R.color.accent_red);
            String valueText = formatReturnValue(yearReturn, yearAmount);
            long startMs = bucket.startMs;
            long endMs = bucket.endMs;
            row.addView(createReturnsCell(
                    buildLabelValueText(entry.getKey() + "年", valueText, color),
                    96,
                    false,
                    null,
                    v -> applyCurveRangeFromTableSelection(startMs, endMs)));
            colCount++;
        }
    }

    private void renderStageReturnsTable(List<CurvePoint> source, long referenceTime) {
        binding.tvMonthlyReturnsTitle.setText("阶段收益统计");
        binding.tvMonthlyReturnsHint.setText("支持近1日/近7日/近30日/近3月/近1年/今年以来/全部/自定义");

        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        String valueHeader = returnValueMode == ReturnValueMode.RATE ? "收益率" : "收益额";
        table.addView(createSimpleHeaderRow(new String[]{"阶段", valueHeader}, 110));

        long endMs = Math.min(source.get(source.size() - 1).getTimestamp(), endOfDay(referenceTime));
        long allStart = source.get(0).getTimestamp();

        List<StageRange> stageRanges = new ArrayList<>();
        stageRanges.add(new StageRange("近1日", endMs - 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近7日", endMs - 7L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近30日", endMs - 30L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近3月", endMs - 90L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近1年", endMs - 365L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("今年以来", startOfYear(endMs), endMs));
        stageRanges.add(new StageRange("全部", allStart, endMs));
        if (manualCurveRangeEnabled) {
            stageRanges.add(new StageRange("自定义区间", manualCurveRangeStartMs, manualCurveRangeEndMs));
        }

        for (StageRange stage : stageRanges) {
            List<CurvePoint> range = filterCurveByManualRange(source, stage.startMs, stage.endMs);
            TableRow row = new TableRow(this);
            if (range.size() < 2) {
                row.addView(createReturnsCell(stage.label, 110, false, null, null));
                row.addView(createReturnsCell("--", 110, false, null, null));
                table.addView(row);
                continue;
            }

            double startEquity = range.get(0).getBalance();
            double endEquity = range.get(range.size() - 1).getBalance();
            double profit = endEquity - startEquity;
            double rate = safeDivide(profit, startEquity);
            int color = ContextCompat.getColor(this, rate >= 0d ? R.color.accent_green : R.color.accent_red);
            String valueText = formatReturnValue(rate, profit);

            row.addView(createReturnsCell(stage.label, 110, false, null,
                    v -> applyCurveRangeFromTableSelection(stage.startMs, stage.endMs)));
            row.addView(createReturnsCell(valueText, 110, false, color,
                    v -> applyCurveRangeFromTableSelection(stage.startMs, stage.endMs)));
            table.addView(row);
        }
    }

    private void rebuildDailyTableV2(TableLayout table,
                                     int year,
                                     int month,
                                     Map<Integer, DayBucket> dayBuckets,
                                     Map<Integer, Double> closeByDay,
                                     List<Integer> dayOrder) {
        table.removeAllViews();
        table.addView(createSimpleHeaderRow(new String[]{"一", "二", "三", "四", "五", "六", "日"}, 48));

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(year, month, 1, 0, 0, 0);
        firstDay.set(Calendar.MILLISECOND, 0);
        int firstWeek = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);

        int totalCells = firstWeek + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7d);
        int day = 1;
        for (int row = 0; row < rows; row++) {
            TableRow tableRow = new TableRow(this);
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || day > daysInMonth) {
                    tableRow.addView(createReturnsCell("", 48, false, null, null));
                    continue;
                }

                DayBucket bucket = dayBuckets.get(day);
                if (bucket == null) {
                    tableRow.addView(createReturnsCell(
                            buildLabelValueText(day + "日", "--", null),
                            48,
                            false,
                            null,
                            null));
                } else {
                    int key = year * 10_000 + (month + 1) * 100 + day;
                    double prevClose = bucket.closeEquity;
                    int indexOfDay = dayOrder.indexOf(key);
                    if (indexOfDay > 0) {
                        Integer prevKey = dayOrder.get(indexOfDay - 1);
                        Double prev = closeByDay.get(prevKey);
                        if (prev != null && prev > 0d) {
                            prevClose = prev;
                        }
                    }
                    double dayAmount = bucket.closeEquity - prevClose;
                    double dayReturn = safeDivide(dayAmount, prevClose);
                    int color = ContextCompat.getColor(this, dayReturn >= 0d ? R.color.accent_green : R.color.accent_red);
                    String valueText = formatReturnValue(dayReturn, dayAmount, true);
                    long startMs = bucket.startMs;
                    long endMs = bucket.endMs;
                    tableRow.addView(createReturnsCell(
                            buildLabelValueText(day + "日", valueText, color),
                            48,
                            false,
                            null,
                            v -> applyCurveRangeFromTableSelection(startMs, endMs)));
                }
                day++;
            }
            table.addView(tableRow);
        }
    }

    private void rebuildMonthlyTableTwoRowsV2(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();
        for (YearlyReturnRow row : rows) {
            View.OnClickListener yearClick = null;
            if (row.startMs > 0L && row.endMs > row.startMs) {
                long startMs = row.startMs;
                long endMs = row.endMs;
                yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
            }

            int yearColor = ContextCompat.getColor(this, row.yearReturnRate >= 0d ? R.color.accent_green : R.color.accent_red);
            String yearValueText = formatReturnValue(row.yearReturnRate, row.yearReturnAmount);

            TableRow firstRow = new TableRow(this);
            TextView yearCell = createReturnsCell(
                    buildLabelValueText(row.year + "年", yearValueText, yearColor),
                    74,
                    false,
                    null,
                    yearClick);
            TableRow.LayoutParams yearParams = (TableRow.LayoutParams) yearCell.getLayoutParams();
            yearParams.height = dpToPx(106);
            yearCell.setLayoutParams(yearParams);
            firstRow.addView(yearCell);
            for (int month = 1; month <= 6; month++) {
                firstRow.addView(createMonthReturnCellV2(month, row.monthly.get(month), 50));
            }
            table.addView(firstRow);

            TableRow secondRow = new TableRow(this);
            TextView placeholder = createReturnsCell("", 74, false, null, null);
            placeholder.setVisibility(View.INVISIBLE);
            placeholder.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            secondRow.addView(placeholder);
            for (int month = 7; month <= 12; month++) {
                secondRow.addView(createMonthReturnCellV2(month, row.monthly.get(month), 50));
            }
            table.addView(secondRow);
        }
    }

    private TextView createMonthReturnCellV2(int month, @Nullable MonthReturnInfo info, int widthDp) {
        if (info == null || !info.hasData) {
            return createReturnsCell(buildLabelValueText(month + "月", "--", null), widthDp, false, null, null);
        }
        int textColor = ContextCompat.getColor(this, info.returnRate >= 0d ? R.color.accent_green : R.color.accent_red);
        String text = formatReturnValue(info.returnRate, info.returnAmount);
        long startMs = info.startMs;
        long endMs = info.endMs;
        return createReturnsCell(
                buildLabelValueText(month + "月", text, textColor),
                widthDp,
                false,
                null,
                v -> applyCurveRangeFromTableSelection(startMs, endMs));
    }

    private void rebuildDailyTableV3(TableLayout table,
                                     int year,
                                     int month,
                                     Map<Integer, DayBucket> dayBuckets,
                                     Map<Integer, Double> closeByDay,
                                     List<Integer> dayOrder) {
        table.removeAllViews();
        table.setShrinkAllColumns(true);
        table.setStretchAllColumns(true);

        TableRow headerRow = new TableRow(this);
        String[] weekHeaders = new String[]{"一", "二", "三", "四", "五", "六", "日"};
        for (String header : weekHeaders) {
            TextView headerCell = createReturnsCell(header, 0, true, null, null);
            applyReturnsCellLayout(headerCell, 0, 1f, RETURNS_HEADER_HEIGHT_DP,
                    RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
            headerCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            headerRow.addView(headerCell);
        }
        table.addView(headerRow);

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(year, month, 1, 0, 0, 0);
        firstDay.set(Calendar.MILLISECOND, 0);
        int firstWeek = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar prevMonth = Calendar.getInstance();
        prevMonth.set(year, month, 1, 0, 0, 0);
        prevMonth.add(Calendar.MONTH, -1);
        int prevDaysInMonth = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        int nextDayValue = 1;

        int totalCells = firstWeek + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7d);
        int day = 1;
        for (int row = 0; row < rows; row++) {
            TableRow tableRow = new TableRow(this);
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || day > daysInMonth) {
                    String ghostLabel;
                    if (index < firstWeek) {
                        int prevDay = prevDaysInMonth - (firstWeek - index) + 1;
                        ghostLabel = String.valueOf(prevDay);
                    } else {
                        ghostLabel = String.valueOf(nextDayValue++);
                    }
                    TextView emptyCell = createReturnsCell(
                            buildLabelValueText(ghostLabel, "--",
                                    ContextCompat.getColor(this, R.color.text_secondary)),
                            0,
                            false,
                            null,
                            null);
                    applyReturnsCellLayout(emptyCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                    emptyCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
                    tableRow.addView(emptyCell);
                    continue;
                }

                DayBucket bucket = dayBuckets.get(day);
                if (bucket == null) {
                    TextView dayCell = createReturnsCell(
                            buildLabelValueText(String.valueOf(day), "--", null),
                            0,
                            false,
                            null,
                            null);
                    applyReturnsCellLayout(dayCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                    dayCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
                    tableRow.addView(dayCell);
                } else {
                    int key = year * 10_000 + (month + 1) * 100 + day;
                    double prevClose = bucket.closeEquity;
                    int indexOfDay = dayOrder.indexOf(key);
                    if (indexOfDay > 0) {
                        Integer prevKey = dayOrder.get(indexOfDay - 1);
                        Double prev = closeByDay.get(prevKey);
                        if (prev != null && prev > 0d) {
                            prevClose = prev;
                        }
                    }
                    double dayAmount = bucket.closeEquity - prevClose;
                    double dayReturn = safeDivide(dayAmount, prevClose);
                    int color = ContextCompat.getColor(this, dayReturn >= 0d ? R.color.accent_green : R.color.accent_red);
                    String valueText = formatReturnValue(dayReturn, dayAmount, true);
                    long startMs = bucket.startMs;
                    long endMs = bucket.endMs;
                    TextView dayCell = createReturnsCell(
                            buildLabelValueText(String.valueOf(day), valueText, color),
                            0,
                            false,
                            null,
                            v -> applyCurveRangeFromTableSelection(startMs, endMs));
                    applyReturnsCellLayout(dayCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                    dayCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
                    tableRow.addView(dayCell);
                }
                day++;
            }
            table.addView(tableRow);
        }
    }

    private void rebuildMonthlyTableTwoRowsV3(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();
        table.setShrinkAllColumns(true);
        table.setStretchAllColumns(true);

        for (YearlyReturnRow row : rows) {
            View.OnClickListener yearClick = null;
            if (row.startMs > 0L && row.endMs > row.startMs) {
                long startMs = row.startMs;
                long endMs = row.endMs;
                yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
            }

            int yearColor = ContextCompat.getColor(this, row.yearReturnRate >= 0d ? R.color.accent_green : R.color.accent_red);
            String yearValueText = formatReturnValue(row.yearReturnRate, row.yearReturnAmount);

            TableRow firstRow = new TableRow(this);
            firstRow.setBaselineAligned(false);
            TableLayout.LayoutParams firstRowParams = new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            firstRowParams.setMargins(0, 0, 0, 0);
            firstRow.setLayoutParams(firstRowParams);
            TextView yearCell = createReturnsCell(
                    buildLabelValueText(row.year + "年", yearValueText, yearColor),
                    0,
                    false,
                    null,
                    yearClick);
            applyReturnsCellLayout(yearCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                    0, 0, 0, 0);
            yearCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            firstRow.addView(yearCell);
            for (int month = 1; month <= 6; month++) {
                firstRow.addView(createMonthReturnCellV3(month, row.monthly.get(month), 0, 1f, 0));
            }
            table.addView(firstRow);

            TableRow secondRow = new TableRow(this);
            secondRow.setBaselineAligned(false);
            TableLayout.LayoutParams secondRowParams = new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            int overlapPx = -dpToPx(1);
            secondRowParams.setMargins(0, overlapPx, 0, 0);
            secondRow.setLayoutParams(secondRowParams);
            TextView placeholder = createReturnsCell("", 0, false, null, null);
            applyReturnsCellLayout(placeholder, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                    0, overlapPx, 0, 0);
            placeholder.setText("");
            secondRow.addView(placeholder);
            for (int month = 7; month <= 12; month++) {
                secondRow.addView(createMonthReturnCellV3(month, row.monthly.get(month), 0, 1f, overlapPx));
            }
            table.addView(secondRow);
        }
    }

    private TextView createMonthReturnCellV3(int month,
                                             @Nullable MonthReturnInfo info,
                                             int widthDp,
                                             float weight,
                                             int marginTopDp) {
        TextView cell;
        if (info == null || !info.hasData) {
            cell = createReturnsCell(buildLabelValueText(month + "月", "--", null), widthDp, false, null, null);
        } else {
            int textColor = ContextCompat.getColor(this, info.returnRate >= 0d ? R.color.accent_green : R.color.accent_red);
            String text = formatReturnValue(info.returnRate, info.returnAmount);
            long startMs = info.startMs;
            long endMs = info.endMs;
            cell = createReturnsCell(
                    buildLabelValueText(month + "月", text, textColor),
                    widthDp,
                    false,
                    null,
                    v -> applyCurveRangeFromTableSelection(startMs, endMs));
        }
        applyReturnsCellLayout(cell, widthDp, weight, RETURNS_BODY_HEIGHT_DP,
                RETURNS_CELL_MARGIN_DP, marginTopDp, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        return cell;
    }

    private void applyReturnsCellLayout(TextView cell,
                                        int widthDp,
                                        float weight,
                                        int heightDp,
                                        int marginLeftDp,
                                        int marginTopDp,
                                        int marginRightDp,
                                        int marginBottomDp) {
        int widthPx = widthDp <= 0 ? 0 : dpToPx(widthDp);
        TableRow.LayoutParams params = new TableRow.LayoutParams(widthPx, dpToPx(heightDp), weight);
        params.setMargins(
                toMarginPx(marginLeftDp),
                toMarginPx(marginTopDp),
                toMarginPx(marginRightDp),
                toMarginPx(marginBottomDp));
        cell.setLayoutParams(params);
    }

    private int toMarginPx(int value) {
        return value < 0 ? value : dpToPx(value);
    }

    private long startOfYear(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private TableRow createSimpleHeaderRow(String[] headers) {
        return createSimpleHeaderRow(headers, 80);
    }

    private TableRow createSimpleHeaderRow(String[] headers, int widthDp) {
        TableRow row = new TableRow(this);
        for (String text : headers) {
            row.addView(createReturnsCell(text, widthDp, true, null, null));
        }
        return row;
    }

    private CharSequence buildLabelValueText(String label, String value, @Nullable Integer valueColor) {
        String safeLabel = trim(label);
        String safeValue = trim(value);
        if (safeValue.isEmpty()) {
            safeValue = "--";
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (!safeLabel.isEmpty()) {
            builder.append(safeLabel);
            builder.append('\n');
        }
        int valueStart = builder.length();
        builder.append(safeValue);
        if (valueColor != null) {
            builder.setSpan(new ForegroundColorSpan(valueColor),
                    valueStart,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    private String formatReturnValue(double rate, double amount) {
        return formatReturnValue(rate, amount, false);
    }

    private String formatReturnValue(double rate, double amount, boolean dayMode) {
        if (returnValueMode == ReturnValueMode.AMOUNT) {
            return String.format(Locale.getDefault(), "%+,.0f", amount);
        }
        return String.format(Locale.getDefault(), dayMode ? "%+.1f%%" : "%+.2f%%", rate * 100d);
    }

    private TextView createReturnsCell(CharSequence text,
                                       int minWidthDp,
                                       boolean header,
                                       @Nullable Integer textColor,
                                       @Nullable View.OnClickListener clickListener) {
        TextView cell = new TextView(this);
        int baseHeightDp = header ? 36 : 52;
        TableRow.LayoutParams params = new TableRow.LayoutParams(dpToPx(minWidthDp), dpToPx(baseHeightDp));
        int marginPx = dpToPx(RETURNS_CELL_MARGIN_DP);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);
        cell.setLayoutParams(params);
        cell.setPadding(0, 0, 0, 0);
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        cell.setIncludeFontPadding(false);
        cell.setText(text);
        cell.setTextSize(header ? 10.5f : 8.8f);
        String plain = text == null ? "" : text.toString();
        cell.setEllipsize(null);
        if (plain.contains("\n")) {
            cell.setLines(2);
            cell.setMaxLines(2);
            cell.setLineSpacing(0f, 1.0f);
        } else {
            cell.setLines(1);
            cell.setMaxLines(1);
        }
        cell.setBackgroundResource(header ? R.drawable.bg_returns_table_header_cell : R.drawable.bg_returns_table_cell);
        int defaultColor = ContextCompat.getColor(this, header ? R.color.text_primary : R.color.text_secondary);
        cell.setTextColor(textColor != null ? textColor : defaultColor);
        if (clickListener != null) {
            cell.setOnClickListener(clickListener);
            cell.setClickable(true);
        }
        return cell;
    }

    private List<YearlyReturnRow> buildMonthlyReturnRows(List<CurvePoint> source) {
        List<YearlyReturnRow> rows = new ArrayList<>();
        if (source == null || source.size() < 2) {
            return rows;
        }

        List<CurvePoint> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));

        Map<Integer, PeriodBucket> monthBuckets = new TreeMap<>();
        for (CurvePoint point : sorted) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(point.getTimestamp());
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            int key = year * 100 + month;
            PeriodBucket bucket = monthBuckets.get(key);
            if (bucket == null) {
                bucket = new PeriodBucket();
                monthBuckets.put(key, bucket);
            }
            if (bucket.startMs == 0L || point.getTimestamp() < bucket.startMs) {
                bucket.startMs = point.getTimestamp();
            }
            if (point.getTimestamp() >= bucket.endMs) {
                bucket.endMs = point.getTimestamp();
                bucket.closeEquity = point.getBalance();
            }
        }

        if (monthBuckets.isEmpty()) {
            return rows;
        }

        Map<Integer, MonthReturnInfo> monthReturnMap = new LinkedHashMap<>();
        double previousClose = Math.max(1e-9, sorted.get(0).getBalance());
        for (Map.Entry<Integer, PeriodBucket> entry : monthBuckets.entrySet()) {
            PeriodBucket bucket = entry.getValue();
            MonthReturnInfo info = new MonthReturnInfo();
            info.startMs = bucket.startMs;
            info.endMs = bucket.endMs;
            info.hasData = true;
            info.startEquity = previousClose;
            info.endEquity = bucket.closeEquity;
            info.returnAmount = bucket.closeEquity - previousClose;
            info.returnRate = safeDivide(info.returnAmount, previousClose);
            previousClose = bucket.closeEquity;
            monthReturnMap.put(entry.getKey(), info);
        }

        int firstKey = monthBuckets.keySet().iterator().next();
        int lastKey = firstKey;
        for (Integer key : monthBuckets.keySet()) {
            lastKey = key;
        }

        int firstYear = firstKey / 100;
        int lastYear = lastKey / 100;
        for (int year = firstYear; year <= lastYear; year++) {
            YearlyReturnRow yearly = new YearlyReturnRow(year);
            double yearStart = 0d;
            double yearEnd = 0d;
            for (int month = 1; month <= 12; month++) {
                int key = year * 100 + month;
                MonthReturnInfo info = monthReturnMap.get(key);
                yearly.monthly.put(month, info);
                if (info != null && info.hasData) {
                    yearly.yearReturnAmount += info.returnAmount;
                    if (yearStart <= 0d) {
                        yearStart = info.startEquity;
                    }
                    yearEnd = info.endEquity;
                    if (yearly.startMs == 0L || info.startMs < yearly.startMs) {
                        yearly.startMs = info.startMs;
                    }
                    if (info.endMs > yearly.endMs) {
                        yearly.endMs = info.endMs;
                    }
                }
            }
            yearly.yearReturnRate = yearStart <= 0d ? 0d : safeDivide(yearEnd - yearStart, yearStart);
            rows.add(yearly);
        }
        return rows;
    }

    private void applyCurveRangeFromTableSelection(long startMs, long endMs) {
        if (startMs <= 0L || endMs <= startMs) {
            return;
        }
        List<CurvePoint> filtered = filterCurveByManualRange(allCurvePoints, startMs, endMs);
        if (filtered.size() < 2) {
            return;
        }
        manualCurveRangeEnabled = true;
        manualCurveRangeStartMs = startMs;
        manualCurveRangeEndMs = endMs;
        binding.etRangeStart.setText(dateOnlyFormat.format(new Date(startMs)));
        binding.etRangeEnd.setText(dateOnlyFormat.format(new Date(endMs)));
        displayedCurvePoints = filtered;
        renderCurveWithIndicators(displayedCurvePoints);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void refreshPositions() {
        List<PositionItem> list = new ArrayList<>(basePositions);
        list.sort(Comparator.comparing(PositionItem::getProductName));

        positionAggregateAdapter.submitList(buildPositionAggregates(list));
        positionAdapter.submitList(list);

        List<PositionItem> pendingOrders = new ArrayList<>(basePendingOrders);
        pendingOrders.sort((a, b) -> Double.compare(b.getPendingLots(), a.getPendingLots()));
        pendingOrderAdapter.submitList(pendingOrders);

        int pendingVisibility = pendingOrders.isEmpty() ? View.GONE : View.VISIBLE;
        binding.tvPendingOrdersTitle.setVisibility(pendingVisibility);
        binding.recyclerPendingOrders.setVisibility(pendingVisibility);

        double totalPnl = 0d;
        for (PositionItem item : list) {
            totalPnl += item.getTotalPnL();
        }
        double totalAsset = allCurvePoints.isEmpty()
                ? ACCOUNT_INITIAL_BALANCE
                : Math.max(1d, allCurvePoints.get(allCurvePoints.size() - 1).getBalance());
        double ratio = safeDivide(totalPnl, totalAsset);
        binding.tvPositionPnlSummary.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        binding.tvPositionPnlSummary.setText(buildPositionPnlSummary(totalPnl, ratio));
    }

    private List<PositionAggregateAdapter.AggregateItem> buildPositionAggregates(List<PositionItem> list) {
        Map<String, PositionAggregate> grouped = new LinkedHashMap<>();
        for (PositionItem item : list) {
            String side = normalizeSideCn(item.getSide());
            String key = item.getCode() + "|" + side;
            PositionAggregate aggregate = grouped.get(key);
            if (aggregate == null) {
                aggregate = new PositionAggregate(item.getProductName(), side);
                grouped.put(key, aggregate);
            }
            double quantity = Math.max(0d, item.getQuantity());
            aggregate.totalQuantity += quantity;
            aggregate.weightedCostAmount += quantity * item.getCostPrice();
            aggregate.weightedQuantity += quantity;
            aggregate.totalPnl += item.getTotalPnL();
        }

        List<PositionAggregateAdapter.AggregateItem> result = new ArrayList<>();
        for (PositionAggregate aggregate : grouped.values()) {
            double avgCost = aggregate.weightedQuantity <= 0d
                    ? 0d
                    : aggregate.weightedCostAmount / aggregate.weightedQuantity;
            result.add(new PositionAggregateAdapter.AggregateItem(
                    aggregate.productName,
                    aggregate.side,
                    aggregate.totalQuantity,
                    avgCost,
                    aggregate.totalPnl
            ));
        }
        return result;
    }

    private CharSequence buildPositionPnlSummary(double totalPnl, double ratio) {
        String pnlText = signedMoney(totalPnl);
        String ratioText = percent(ratio);
        String summary = String.format(Locale.getDefault(),
                "持仓盈亏: %s | 持仓收益率: %s",
                pnlText,
                ratioText);
        SpannableString spannable = new SpannableString(summary);

        int pnlColor = ContextCompat.getColor(this, totalPnl >= 0d ? R.color.accent_green : R.color.accent_red);
        int ratioColor = ContextCompat.getColor(this, ratio >= 0d ? R.color.accent_green : R.color.accent_red);

        int pnlStart = summary.indexOf(pnlText);
        if (pnlStart >= 0) {
            spannable.setSpan(new ForegroundColorSpan(pnlColor),
                    pnlStart, pnlStart + pnlText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int ratioStart = summary.lastIndexOf(ratioText);
        if (ratioStart >= 0) {
            spannable.setSpan(new ForegroundColorSpan(ratioColor),
                    ratioStart, ratioStart + ratioText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private List<PositionItem> buildPendingOrders(List<PositionItem> list) {
        List<PositionItem> pending = new ArrayList<>();
        for (PositionItem item : list) {
            if (item.getPendingCount() > 0 || item.getPendingLots() > 0d) {
                pending.add(item);
            }
        }
        pending.sort((a, b) -> Double.compare(b.getPendingLots(), a.getPendingLots()));
        return pending;
    }

    private List<TradeRecordItem> mergeOpenCloseTrades(List<TradeRecordItem> source) {
        List<TradeRecordItem> merged = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return merged;
        }

        java.util.Set<String> dedupeSet = new java.util.LinkedHashSet<>();
        Map<String, List<TradeRecordItem>> grouped = new LinkedHashMap<>();
        for (TradeRecordItem item : source) {
            if (item == null) {
                continue;
            }
            String dedupeKey = buildTradeDedupeKey(item);
            if (dedupeSet.contains(dedupeKey)) {
                continue;
            }
            dedupeSet.add(dedupeKey);

            String key = buildTradeMergeKey(item);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        for (List<TradeRecordItem> group : grouped.values()) {
            if (group == null || group.isEmpty()) {
                continue;
            }

            group.sort(Comparator.comparingLong(this::resolveCloseTime));
            List<TradeRecordItem> nonZero = new ArrayList<>();
            for (TradeRecordItem item : group) {
                if (Math.abs(item.getProfit()) > 1e-9) {
                    nonZero.add(item);
                }
            }

            if (nonZero.size() == 1) {
                merged.add(buildMergedTradeFromGroup(nonZero.get(0), group));
                continue;
            }
            if (nonZero.size() > 1) {
                merged.addAll(nonZero);
                continue;
            }

            TradeRecordItem latest = chooseLatestTradeRecord(group);
            if (latest == null) {
                continue;
            }
            long openTime = resolveOpenTime(latest);
            long closeTime = resolveCloseTime(latest);
            boolean likelyNoise = Math.abs(latest.getProfit()) <= 1e-9
                    && closeTime <= openTime + 1_000L;
            if (!likelyNoise) {
                merged.add(latest);
            }
        }

        merged.sort((a, b) -> Long.compare(resolveCloseTime(b), resolveCloseTime(a)));
        return merged;
    }

    private String buildTradeDedupeKey(TradeRecordItem item) {
        String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
        long open = resolveOpenTime(item);
        long close = resolveCloseTime(item);
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long price = Math.round(Math.abs(item.getPrice()) * 100d);
        long profit = Math.round(item.getProfit() * 100d);
        String side = normalizeSide(trim(item.getSide())).toLowerCase(Locale.ROOT);
        return code + "|" + side + "|" + open + "|" + close + "|" + qty + "|" + price + "|" + profit;
    }

    private String buildTradeMergeKey(TradeRecordItem item) {
        String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
        long openBucket = resolveOpenTime(item) / 60_000L;
        long quantityKey = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        return code + "|" + openBucket + "|" + quantityKey;
    }

    private List<TradeRecordItem> pruneZeroProfitTrades(List<TradeRecordItem> source) {
        List<TradeRecordItem> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        for (TradeRecordItem item : source) {
            if (Math.abs(item.getProfit()) > 1e-9) {
                result.add(item);
            }
        }

        result.sort((a, b) -> Long.compare(resolveCloseTime(b), resolveCloseTime(a)));
        return result;
    }

    private boolean isMeaningfulZeroProfitTrade(TradeRecordItem item) {
        if (item == null) {
            return false;
        }
        long open = resolveOpenTime(item);
        long close = resolveCloseTime(item);
        if (close <= open + 60_000L) {
            return false;
        }
        return Math.abs(item.getQuantity()) > 1e-9;
    }

    private boolean hasMatchingNonZeroTrade(TradeRecordItem zero, List<TradeRecordItem> nonZero) {
        if (zero == null || nonZero == null || nonZero.isEmpty()) {
            return false;
        }
        String code = trim(zero.getCode()).toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            code = trim(zero.getProductName()).toUpperCase(Locale.ROOT);
        }
        long open = resolveOpenTime(zero);
        double qty = Math.abs(zero.getQuantity());
        for (TradeRecordItem item : nonZero) {
            String otherCode = trim(item.getCode()).toUpperCase(Locale.ROOT);
            if (otherCode.isEmpty()) {
                otherCode = trim(item.getProductName()).toUpperCase(Locale.ROOT);
            }
            if (!code.equals(otherCode)) {
                continue;
            }
            double otherQty = Math.abs(item.getQuantity());
            if (qty > 1e-9 && otherQty > 1e-9) {
                double diff = Math.abs(qty - otherQty);
                if (diff > Math.max(0.01d, qty * 0.25d)) {
                    continue;
                }
            }
            long otherClose = resolveCloseTime(item);
            if (otherClose >= open - 12L * 60L * 60L * 1000L
                    && otherClose <= open + 30L * 24L * 60L * 60L * 1000L) {
                return true;
            }
        }
        return false;
    }

    private TradeRecordItem chooseLatestTradeRecord(List<TradeRecordItem> group) {
        TradeRecordItem latest = null;
        for (TradeRecordItem item : group) {
            if (latest == null || resolveCloseTime(item) > resolveCloseTime(latest)) {
                latest = item;
            }
        }
        return latest;
    }

    private TradeRecordItem buildMergedTradeFromGroup(TradeRecordItem closeRecord, List<TradeRecordItem> group) {
        double feeSum = 0d;
        double storageFeeSum = 0d;
        double maxQuantity = Math.max(0d, closeRecord.getQuantity());
        double maxAmount = Math.max(0d, closeRecord.getAmount());
        long minOpenTime = Long.MAX_VALUE;
        long maxCloseTime = 0L;

        for (TradeRecordItem item : group) {
            feeSum += item.getFee();
            storageFeeSum += item.getStorageFee();
            maxQuantity = Math.max(maxQuantity, item.getQuantity());
            maxAmount = Math.max(maxAmount, item.getAmount());
            long openTime = resolveOpenTime(item);
            long closeTime = resolveCloseTime(item);
            if (openTime > 0L) {
                minOpenTime = Math.min(minOpenTime, openTime);
            }
            if (closeTime > 0L) {
                maxCloseTime = Math.max(maxCloseTime, closeTime);
            }
        }
        if (minOpenTime == Long.MAX_VALUE) {
            minOpenTime = resolveOpenTime(closeRecord);
        }
        if (maxCloseTime <= 0L) {
            maxCloseTime = resolveCloseTime(closeRecord);
        }

        String remark = collectMergedRemarks(group, closeRecord.getRemark());
        String side = resolveMergedSide(closeRecord, group);
        if (side.isEmpty()) {
            side = closeRecord.getSide();
        }
        return new TradeRecordItem(
                maxCloseTime,
                closeRecord.getProductName(),
                closeRecord.getCode(),
                side,
                closeRecord.getPrice(),
                maxQuantity > 0d ? maxQuantity : closeRecord.getQuantity(),
                maxAmount > 0d ? maxAmount : closeRecord.getAmount(),
                feeSum,
                remark,
                closeRecord.getProfit(),
                minOpenTime,
                maxCloseTime,
                storageFeeSum
        );
    }

    private String resolveMergedSide(TradeRecordItem closeRecord, List<TradeRecordItem> group) {
        if (group != null) {
            for (TradeRecordItem item : group) {
                if (Math.abs(item.getProfit()) <= 1e-9) {
                    String side = normalizeSide(trim(item.getSide()));
                    if (!side.isEmpty()) {
                        return side;
                    }
                }
            }
        }
        return normalizeSide(trim(closeRecord.getSide()));
    }

    private String collectMergedRemarks(List<TradeRecordItem> group, String fallback) {
        StringBuilder builder = new StringBuilder();
        for (TradeRecordItem item : group) {
            String remark = trim(item.getRemark());
            if (remark.isEmpty()) {
                continue;
            }
            String current = builder.toString();
            if (current.contains(remark)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(remark);
        }
        if (builder.length() > 0) {
            return builder.toString();
        }
        return fallback == null ? "" : fallback;
    }

    private void refreshTrades() {
        List<TradeRecordItem> filtered = new ArrayList<>();
        String product = (String) binding.spinnerTradeProduct.getSelectedItem();
        String side = (String) binding.spinnerTradeSide.getSelectedItem();
        String time = (String) binding.spinnerTradeTime.getSelectedItem();
        String sort = (String) binding.spinnerTradeSort.getSelectedItem();
        if (product == null || product.trim().isEmpty()) {
            product = FILTER_PRODUCT;
        }
        if (side == null || side.trim().isEmpty()) {
            side = FILTER_SIDE;
        }
        if (time == null || time.trim().isEmpty()) {
            time = FILTER_DATE;
        }
        if (sort == null || sort.trim().isEmpty()) {
            sort = FILTER_SORT;
        }
        selectedTradeProductFilter = product;
        selectedTradeSideFilter = side;
        selectedTradeDateFilter = time;
        selectedTradeSortFilter = sort;
        String normalizedSort = normalizeSortValue(sort);

        long now = System.currentTimeMillis();
        long limit;
        if (FILTER_LAST_1D.equals(time)) {
            limit = now - 24L * 60L * 60L * 1000L;
        } else if (FILTER_LAST_7D.equals(time)) {
            limit = now - 7L * 24L * 60L * 60L * 1000L;
        } else if (FILTER_LAST_30D.equals(time)) {
            limit = now - 30L * 24L * 60L * 60L * 1000L;
        } else {
            limit = 0L;
        }

        for (TradeRecordItem item : baseTrades) {
            long closeTime = item.getCloseTime() > 0L ? item.getCloseTime() : item.getTimestamp();
            if (limit > 0L && closeTime < limit) {
                continue;
            }
            if (!FILTER_PRODUCT.equals(product) && !item.getCode().equalsIgnoreCase(product)) {
                continue;
            }
            if (!FILTER_SIDE.equals(side) && !item.getSide().equalsIgnoreCase(normalizeSide(side))) {
                continue;
            }
            filtered.add(item);
        }
        filtered = collapseZeroProfitForDisplay(filtered);

        if (SORT_OPEN_TIME.equals(normalizedSort)) {
            filtered.sort((a, b) -> Long.compare(
                    a.getOpenTime() > 0L ? a.getOpenTime() : a.getTimestamp(),
                    b.getOpenTime() > 0L ? b.getOpenTime() : b.getTimestamp()));
        } else if (SORT_PROFIT.equals(normalizedSort)) {
            filtered.sort((a, b) -> Double.compare(a.getProfit(), b.getProfit()));
        } else {
            filtered.sort((a, b) -> Long.compare(
                    a.getCloseTime() > 0L ? a.getCloseTime() : a.getTimestamp(),
                    b.getCloseTime() > 0L ? b.getCloseTime() : b.getTimestamp()));
        }
        if (tradeSortDescending) {
            java.util.Collections.reverse(filtered);
        }
        tradeAdapter.submitList(filtered);
        updateTradePnlSummary(filtered, product, side, time);
        forceRenderTradeFilterSpinners();
    }

    private List<TradeRecordItem> collapseZeroProfitForDisplay(List<TradeRecordItem> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<TradeRecordItem> result = new ArrayList<>();
        for (TradeRecordItem item : source) {
            if (Math.abs(item.getProfit()) > 1e-9) {
                result.add(item);
            }
        }
        return result;
    }

    private void updateTradePnlSummary(List<TradeRecordItem> trades,
                                       String productFilter,
                                       String sideFilter,
                                       String dateFilter) {
        double total = 0d;
        int tradeCount = trades == null ? 0 : trades.size();
        if (trades != null) {
            for (TradeRecordItem item : trades) {
                total += item.getProfit();
            }
        }
        if (isDefaultTradeFilters(productFilter, sideFilter, dateFilter)) {
            total = latestCumulativePnl;
        }
        String pnlText = String.format(Locale.getDefault(), "%,.2f", total);
        String summary = "盈亏合计：" + pnlText + "    交易次数：" + tradeCount + "次";
        SpannableStringBuilder span = new SpannableStringBuilder(summary);
        int pnlStart = summary.indexOf(pnlText);
        if (pnlStart >= 0) {
            int color = ContextCompat.getColor(this,
                    total > 0d ? R.color.accent_green : (total < 0d ? R.color.accent_red : R.color.text_primary));
            span.setSpan(new ForegroundColorSpan(color),
                    pnlStart,
                    pnlStart + pnlText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        binding.tvTradeRecordsPnlSummary.setText(span);
        binding.tvTradeRecordsPnlSummary.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
    }

    private boolean isDefaultTradeFilters(String productFilter,
                                          String sideFilter,
                                          String dateFilter) {
        return isDefaultOrBlank(productFilter, FILTER_PRODUCT)
                && isDefaultOrBlank(sideFilter, FILTER_SIDE)
                && isDefaultOrBlank(dateFilter, FILTER_DATE);
    }

    private boolean isDefaultOrBlank(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        return defaultValue.equals(value);
    }

    private void updateTradeProductOptions() {
        String current = selectedTradeProductFilter;
        if (current == null || current.trim().isEmpty()) {
            current = FILTER_PRODUCT;
        }

        List<String> products = new ArrayList<>();
        for (TradeRecordItem item : baseTrades) {
            String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
            if (code.isEmpty() || products.contains(code)) {
                continue;
            }
            products.add(code);
        }
        products.sort(String::compareToIgnoreCase);

        List<String> options = new ArrayList<>();
        options.add(FILTER_PRODUCT);
        options.addAll(products);
        ArrayAdapter<String> adapter = createTradeFilterAdapter(options.toArray(new String[0]));
        binding.spinnerTradeProduct.setAdapter(adapter);
        setSpinnerSelectionByValue(binding.spinnerTradeProduct, current);
        selectedTradeProductFilter = safeSpinnerValue(binding.spinnerTradeProduct, current, FILTER_PRODUCT);
        forceRenderSpinnerSelectedText(binding.spinnerTradeProduct);
    }

    private void applyManualCurveRange() {
        String startText = trim(binding.etRangeStart.getText() == null ? "" : binding.etRangeStart.getText().toString());
        String endText = trim(binding.etRangeEnd.getText() == null ? "" : binding.etRangeEnd.getText().toString());
        if (startText.isEmpty() || endText.isEmpty()) {
            clearManualCurveRange(false);
            applyCurrentCurveRangeFromAllPoints();
            Toast.makeText(this, "已恢复当前时间维度默认区间", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Date startDate = dateOnlyFormat.parse(startText);
            Date endDate = dateOnlyFormat.parse(endText);
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException();
            }
            long start = startDate.getTime();
            long end = endDate.getTime() + 24L * 60L * 60L * 1000L - 1L;
            if (start > end) {
                long tmp = start;
                start = end;
                end = tmp;
            }

            List<CurvePoint> filtered = filterCurveByManualRange(allCurvePoints, start, end);
            if (filtered.size() < 2) {
                Toast.makeText(this, "该区间数据不足，请调整日期", Toast.LENGTH_SHORT).show();
                return;
            }
            manualCurveRangeEnabled = true;
            manualCurveRangeStartMs = start;
            manualCurveRangeEndMs = end;
            applyCurrentCurveRangeFromAllPoints();
        } catch (Exception e) {
            Toast.makeText(this, "日期格式应为 yyyy-MM-dd", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearManualCurveRange(boolean clearInputText) {
        manualCurveRangeEnabled = false;
        manualCurveRangeStartMs = 0L;
        manualCurveRangeEndMs = 0L;
        if (clearInputText) {
            binding.etRangeStart.setText("");
            binding.etRangeEnd.setText("");
        }
    }

    private String trim(String source) {
        return source == null ? "" : source.trim();
    }

    private double safeDivide(double a, double b) {
        return Math.abs(b) < 1e-9 ? 0d : a / b;
    }

    private double returnN(double[] values, int n) {
        if (values.length < 2) {
            return 0d;
        }
        int from = Math.max(0, values.length - 1 - n);
        return safeDivide(values[values.length - 1] - values[from], values[from]);
    }

    private double calcStd(List<Double> values) {
        if (values.isEmpty()) {
            return 0d;
        }
        double avg = values.stream().mapToDouble(v -> v).average().orElse(0d);
        double sum = 0d;
        for (double value : values) {
            double diff = value - avg;
            sum += diff * diff;
        }
        return Math.sqrt(sum / values.size());
    }

    private String signedMoney(double value) {
        return (value >= 0d ? "+" : "-") + "$" + FormatUtils.formatPrice(Math.abs(value));
    }

    private String percent(double ratio) {
        return String.format(Locale.getDefault(), "%+.2f%%", ratio * 100d);
    }

    private String normalizeSide(String side) {
        if ("买入".equals(side)) {
            return "Buy";
        }
        if ("卖出".equals(side)) {
            return "Sell";
        }
        return side;
    }

    private String normalizeSideCn(String side) {
        if ("buy".equalsIgnoreCase(side) || "买入".equals(side)) {
            return "买入";
        }
        if ("sell".equalsIgnoreCase(side) || "卖出".equals(side)) {
            return "卖出";
        }
        return side;
    }

    private void openMarketMonitor() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private static class DayBucket {
        private final int day;
        private long startMs;
        private long endMs;
        private double closeEquity;

        private DayBucket(int day) {
            this.day = day;
        }
    }

    private static class StageRange {
        private final String label;
        private final long startMs;
        private final long endMs;

        private StageRange(String label, long startMs, long endMs) {
            this.label = label;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static class PeriodBucket {
        private long startMs;
        private long endMs;
        private double closeEquity;
    }

    private static class MonthReturnInfo {
        private long startMs;
        private long endMs;
        private double startEquity;
        private double endEquity;
        private double returnAmount;
        private double returnRate;
        private boolean hasData;
    }

    private static class YearlyReturnRow {
        private final int year;
        private final Map<Integer, MonthReturnInfo> monthly = new LinkedHashMap<>();
        private long startMs;
        private long endMs;
        private double yearReturnAmount;
        private double yearReturnRate;

        private YearlyReturnRow(int year) {
            this.year = year;
        }
    }

    private static class PositionAggregate {
        private final String productName;
        private final String side;
        private double totalQuantity;
        private double weightedCostAmount;
        private double weightedQuantity;
        private double totalPnl;

        private PositionAggregate(String productName, String side) {
            this.productName = productName;
            this.side = side;
        }
    }
}

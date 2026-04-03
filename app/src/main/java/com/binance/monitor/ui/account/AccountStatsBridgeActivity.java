/*
 * 账户统计桥接页，负责账户总览、持仓、交易记录与刷新状态的统一展示。
 * 该页面同时对接网关快照、历史缓存和本地筛选交互。
 */
package com.binance.monitor.ui.account;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.databinding.ActivityAccountStatsBinding;
import com.binance.monitor.service.MonitorService;
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
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;
import com.binance.monitor.ui.widget.TradeScrollBarView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountStatsBridgeActivity extends AppCompatActivity {
    private static final double ACCOUNT_INITIAL_BALANCE = 15_019.45d;
    private static final double TRADE_PNL_ZERO_THRESHOLD = 0.01d;
    private static final int RETURNS_CELL_MARGIN_DP = 1;
    private static final int RETURNS_HEADER_HEIGHT_DP = 28;
    private static final int RETURNS_BODY_HEIGHT_DP = 42;
    private static final int RETURNS_MONTH_GROUP_HEIGHT_DP = 44;
    private static final int RETURNS_STAGE_HEIGHT_DP = 38;
    private static final String ACCOUNT = "7400048";
    private static final String SERVER = "ICMarketsSC-MT5-6";

    private static final String FILTER_PRODUCT = "全部产品";
    private static final String FILTER_SIDE = "全部方向";
    private static final String FILTER_DATE = "全部日期";
    private static final String FILTER_SORT = "排序方式";
    private static final String SORT_CLOSE_TIME = "平仓时间";
    private static final String SORT_OPEN_TIME = "开仓时间";
    private static final String SORT_PROFIT = "盈利水平";
    private static final long ACCOUNT_REFRESH_MIN_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS;
    private static final long ACCOUNT_REFRESH_MAX_MS = AppConstants.ACCOUNT_REFRESH_MAX_INTERVAL_MS;
    private static final TimeZone BEIJING_TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");

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
    private static final String PREF_FILTER_SORT = "pref_filter_sort";
    private static final String PREF_FILTER_SORT_DESC = "pref_filter_sort_desc";
    private static final String PREF_LOGIN_ENABLED = "pref_login_enabled";
    private static final String PREF_LOGIN_ACCOUNT = "pref_login_account";
    private static final String PREF_LOGIN_PASSWORD = "pref_login_password";
    private static final String PREF_LOGIN_SERVER = "pref_login_server";
    private static final int DATE_TARGET_START = 0;
    private static final int DATE_TARGET_END = 1;

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

    private enum TradeWeekdayBasis {
        CLOSE_TIME,
        OPEN_TIME
    }

    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat monthTitleFormat = new SimpleDateFormat("yyyy年M月", Locale.getDefault());

    private ActivityAccountStatsBinding binding;
    private AccountStatsPreloadManager preloadManager;
    private Mt5BridgeGatewayClient gatewayClient;
    private AccountStorageRepository accountStorageRepository;
    private AccountMetricAdapter overviewAdapter;
    private AccountMetricAdapter indicatorAdapter;
    private PositionAggregateAdapter positionAggregateAdapter;
    private PositionAdapterV2 positionAdapter;
    private PendingOrderAdapter pendingOrderAdapter;
    private TradeRecordAdapterV2 tradeAdapter;
    private StatsMetricAdapter statsAdapter;
    private LogManager logManager;
    private ExecutorService ioExecutor;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final AccountSnapshotRequestGuard snapshotRequestGuard = new AccountSnapshotRequestGuard();
    private volatile boolean loading;
    private boolean snapshotLoopEnabled;
    private long connectedUpdateAtMs;
    private long nextRefreshAtMs;

    private AccountTimeRange selectedRange = AccountTimeRange.D7;
    private ReturnStatsMode returnStatsMode = ReturnStatsMode.MONTH;
    private ReturnValueMode returnValueMode = ReturnValueMode.RATE;
    private TradePnlSideMode tradePnlSideMode = TradePnlSideMode.ALL;
    private TradeWeekdayBasis tradeWeekdayBasis = TradeWeekdayBasis.CLOSE_TIME;
    private long returnStatsAnchorDateMs;
    private String selectedTradeProductFilter = FILTER_PRODUCT;
    private String selectedTradeSideFilter = FILTER_SIDE;
    private String selectedTradeSortFilter = FILTER_SORT;
    private String lastExplicitTradeSortMode = SORT_CLOSE_TIME;
    private boolean tradeSortDescending = true;
    private double latestCumulativePnl;

    private boolean manualCurveRangeEnabled;
    private long manualCurveRangeStartMs;
    private long manualCurveRangeEndMs;
    private int manualDateTarget = DATE_TARGET_START;
    private final List<Integer> returnPickerYears = new ArrayList<>();
    private final List<Integer> returnPickerVisibleMonths = new ArrayList<>();
    private final Map<Integer, boolean[]> returnPickerYearMonthMap = new TreeMap<>();

    private List<PositionItem> basePositions = new ArrayList<>();
    private List<PositionItem> basePendingOrders = new ArrayList<>();
    private List<TradeRecordItem> baseTrades = new ArrayList<>();
    private List<CurvePoint> allCurvePoints = new ArrayList<>();
    private List<CurvePoint> displayedCurvePoints = new ArrayList<>();
    private List<CurveAnalyticsHelper.DrawdownPoint> displayedDrawdownPoints = new ArrayList<>();
    private List<CurveAnalyticsHelper.DailyReturnPoint> displayedDailyReturnPoints = new ArrayList<>();
    private List<AccountMetric> latestOverviewMetrics = new ArrayList<>();
    private String defaultCurveMeta = "--";
    private double curveBaseBalance = ACCOUNT_INITIAL_BALANCE;
    private boolean syncingCurveHighlight;

    private String connectedAccount = ACCOUNT;
    private String connectedAccountName = ACCOUNT;
    private String connectedServer = SERVER;
    private String connectedSource = "历史数据（网关离线）";
    private String connectedGateway = "--";
    private String connectedUpdate = "--";
    private String connectedLeverageText = "";
    private String connectedError = "";
    private String dataQualitySummary = "";
    private boolean userLoggedIn;
    private boolean gatewayConnected;
    private String loginAccountInput = ACCOUNT;
    private String loginPasswordInput = "";
    private String loginServerInput = SERVER;
    private final Map<Long, CurvePoint> curveHistory = new TreeMap<>();
    private final Map<String, TradeRecordItem> tradeHistory = new LinkedHashMap<>();
    private List<PositionItem> connectedPositionCache = new ArrayList<>();
    private List<PositionItem> connectedPendingCache = new ArrayList<>();
    private List<AccountMetric> connectedOverviewCache = new ArrayList<>();
    private final Map<String, PositionLogState> lastPositionLogStates = new HashMap<>();
    private final Map<String, PendingLogState> lastPendingLogStates = new HashMap<>();
    private final Set<String> knownTradeOpenLogKeys = new HashSet<>();
    private final Set<String> knownTradeCloseLogKeys = new HashSet<>();
    private final Map<String, String> knownTradeStateByKey = new HashMap<>();
    private int lastHistoryRecordCount = 0;
    private Boolean lastTradePnlMismatchState;
    private boolean accountLogBaselineReady;
    private Boolean lastLoggedConnectedState;
    private String lastLoggedSource = "";
    private String lastLoggedGateway = "";
    private String lastLoggedError = "";
    private long dynamicRefreshDelayMs = ACCOUNT_REFRESH_MIN_MS;
    private long scheduledRefreshDelayMs = ACCOUNT_REFRESH_MIN_MS;
    private int unchangedRefreshStreak = 0;
    private boolean draggingTradeScrollBar;
    private final Runnable hideLoginSuccessBannerRunnable = this::hideLoginSuccessBannerNow;
    private final AccountStatsPreloadManager.CacheListener preloadCacheListener = cache -> {
        if (cache == null || isFinishing() || isDestroyed()) {
            return;
        }
        applyPreloadedCacheIfAvailable();
    };

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            clearScheduledRefresh();
            requestSnapshot();
        }
    };

    private final Runnable overviewHeaderTicker = new Runnable() {
        @Override
        public void run() {
            updateOverviewHeader();
            refreshHandler.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountStatsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preloadManager = AccountStatsPreloadManager.getInstance(getApplicationContext());
        gatewayClient = new Mt5BridgeGatewayClient(getApplicationContext());
        accountStorageRepository = new AccountStorageRepository(getApplicationContext());
        logManager = LogManager.getInstance(getApplicationContext());
        ioExecutor = Executors.newSingleThreadExecutor();

        overviewAdapter = new AccountMetricAdapter();
        indicatorAdapter = new AccountMetricAdapter();
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
        setupTradeWeekdayBasisToggle();
        setupDatePickers();
        setupCurveInteraction();
        setupOverviewHeader();
        bindLocalMeta();
        applyPaletteStyles();
        applyPrivacyMaskState();
        applyPreloadedCacheIfAvailable();
        snapshotLoopEnabled = true;
        if (userLoggedIn) {
            requestSnapshot();
        } else {
            clearScheduledRefresh();
            setConnectionStatus(false);
        }
        startOverviewHeaderTicker();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
        applyPrivacyMaskState();
        if (preloadManager != null) {
            preloadManager.addCacheListener(preloadCacheListener);
            preloadManager.setLiveScreenActive(true);
        }
        snapshotLoopEnabled = true;
        if (userLoggedIn) {
            if (!loading && nextRefreshAtMs <= 0L) {
                requestSnapshot();
            }
        } else {
            clearScheduledRefresh();
        }
        startOverviewHeaderTicker();
    }

    @Override
    protected void onPause() {
        snapshotLoopEnabled = false;
        clearScheduledRefresh();
        stopOverviewHeaderTicker();
        binding.tvLoginSuccessBanner.removeCallbacks(hideLoginSuccessBannerRunnable);
        hideLoginSuccessBannerNow();
        if (preloadManager != null) {
            preloadManager.removeCacheListener(preloadCacheListener);
            preloadManager.setLiveScreenActive(false);
        }
        persistUiState();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        snapshotLoopEnabled = false;
        clearScheduledRefresh();
        stopOverviewHeaderTicker();
        if (binding != null && binding.tvLoginSuccessBanner != null) {
            binding.tvLoginSuccessBanner.removeCallbacks(hideLoginSuccessBannerRunnable);
        }
        if (preloadManager != null) {
            preloadManager.removeCacheListener(preloadCacheListener);
            preloadManager.setLiveScreenActive(false);
        }
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
        binding.ivAccountPrivacyToggle.setOnClickListener(v -> togglePrivacyMaskState());
        binding.tvAccountConnectionStatus.setOnClickListener(v -> {
            if (!userLoggedIn) {
                showLoginDialog();
            } else {
                showConnectionDialog();
            }
        });
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
        selectedTradeSortFilter = prefs.getString(PREF_FILTER_SORT, FILTER_SORT);
        tradeSortDescending = prefs.getBoolean(PREF_FILTER_SORT_DESC, true);
        boolean persistedLoginEnabled = prefs.getBoolean(PREF_LOGIN_ENABLED, false);
        boolean sessionActive = ConfigManager.getInstance(getApplicationContext()).isAccountSessionActive();
        userLoggedIn = persistedLoginEnabled && sessionActive;
        loginAccountInput = trim(prefs.getString(PREF_LOGIN_ACCOUNT, ACCOUNT));
        if (loginAccountInput.isEmpty()) {
            loginAccountInput = ACCOUNT;
        }
        loginPasswordInput = trim(prefs.getString(PREF_LOGIN_PASSWORD, ""));
        loginServerInput = trim(prefs.getString(PREF_LOGIN_SERVER, SERVER));
        if (loginServerInput.isEmpty()) {
            loginServerInput = SERVER;
        }

        if (selectedTradeProductFilter == null || selectedTradeProductFilter.trim().isEmpty()) {
            selectedTradeProductFilter = FILTER_PRODUCT;
        }
        if (selectedTradeSideFilter == null || selectedTradeSideFilter.trim().isEmpty()) {
            selectedTradeSideFilter = FILTER_SIDE;
        }
        if (selectedTradeSortFilter == null || selectedTradeSortFilter.trim().isEmpty()) {
            selectedTradeSortFilter = FILTER_SORT;
        }
        lastExplicitTradeSortMode = FILTER_SORT.equals(selectedTradeSortFilter)
                ? SORT_CLOSE_TIME
                : normalizeSortValue(selectedTradeSortFilter);
        ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(userLoggedIn);
    }

    private void persistUiState() {
        if (binding == null) {
            return;
        }
        selectedTradeProductFilter = safeSpinnerValue(binding.spinnerTradeProduct, selectedTradeProductFilter, FILTER_PRODUCT);
        selectedTradeSideFilter = safeSpinnerValue(binding.spinnerTradeSide, selectedTradeSideFilter, FILTER_SIDE);
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
        editor.putString(PREF_FILTER_SORT, selectedTradeSortFilter);
        editor.putBoolean(PREF_FILTER_SORT_DESC, tradeSortDescending);
        editor.putBoolean(PREF_LOGIN_ENABLED, userLoggedIn);
        editor.putString(PREF_LOGIN_ACCOUNT, trim(loginAccountInput).isEmpty() ? ACCOUNT : trim(loginAccountInput));
        editor.putString(PREF_LOGIN_PASSWORD, trim(loginPasswordInput));
        editor.putString(PREF_LOGIN_SERVER, trim(loginServerInput).isEmpty() ? SERVER : trim(loginServerInput));
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
        boolean masked = isPrivacyMasked();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(UiPaletteManager.createFilledDrawable(this, palette.surfaceEnd));
        content.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(6));

        TextView titleView = new TextView(this);
        titleView.setText("账户连接详情");
        titleView.setTextColor(palette.textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        content.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText("当前账户连接状态");
        subtitleView.setTextColor(palette.textSecondary);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dpToPx(4);
        content.addView(subtitleView, subtitleParams);

        content.addView(createConnectionDetailRow("账号信息",
                AccountStatsPrivacyFormatter.maskValue(connectedAccount, masked), palette));
        content.addView(createConnectionDetailRow("服务器信息",
                AccountStatsPrivacyFormatter.maskValue(connectedServer, masked), palette));
        content.addView(createConnectionDetailRow("数据源信息",
                AccountStatsPrivacyFormatter.maskValue(connectedSource, masked), palette));
        content.addView(createConnectionDetailRow("网关信息",
                AccountStatsPrivacyFormatter.maskValue(connectedGateway, masked), palette));
        content.addView(createConnectionDetailRow("更新时间信息",
                AccountStatsPrivacyFormatter.maskValue(connectedUpdate, masked), palette));
        if (!connectedError.isEmpty()) {
            content.addView(createConnectionDetailRow("失败原因",
                    AccountStatsPrivacyFormatter.maskValue(connectedError, masked), palette));
        }
        if (!dataQualitySummary.isEmpty()) {
            content.addView(createConnectionDetailRow("数据校对",
                    AccountStatsPrivacyFormatter.maskValue(dataQualitySummary, masked), palette));
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setPositiveButton("确定", null);
        if (userLoggedIn) {
            builder.setNegativeButton("退出登录", (dialog, which) -> logoutAccount());
        }
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(UiPaletteManager.createFilledDrawable(this, palette.surfaceEnd));
        }
        dialog.show();
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
        }
        if (userLoggedIn && dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(palette.fall);
        }
    }

    // 用当前主题渲染连接详情行，保证文字可读性和整体风格一致。
    private View createConnectionDetailRow(String label,
                                           String value,
                                           UiPaletteManager.Palette palette) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dpToPx(8);
        row.setLayoutParams(params);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(palette.textSecondary);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(trim(value).isEmpty() ? "--" : value);
        valueView.setTextColor(palette.textPrimary);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dpToPx(4);
        row.addView(valueView, valueParams);
        return row;
    }

    private void showLoginDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dpToPx(16);
        int top = dpToPx(8);
        int bottom = dpToPx(4);
        container.setPadding(horizontal, top, horizontal, bottom);

        EditText accountInput = createLoginField("账户名称", false);
        accountInput.setText(trim(loginAccountInput).isEmpty() ? ACCOUNT : trim(loginAccountInput));
        container.addView(accountInput);

        EditText passwordInput = createLoginField("账户密码", true);
        if (!trim(loginPasswordInput).isEmpty()) {
            passwordInput.setText(loginPasswordInput);
        }
        container.addView(passwordInput);

        EditText serverInput = createLoginField("服务器信息", false);
        serverInput.setText(trim(loginServerInput).isEmpty() ? SERVER : trim(loginServerInput));
        container.addView(serverInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle("账户登录")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("登录", (dialog, which) -> {
                    String account = trim(accountInput.getText() == null ? "" : accountInput.getText().toString());
                    String password = trim(passwordInput.getText() == null ? "" : passwordInput.getText().toString());
                    String server = trim(serverInput.getText() == null ? "" : serverInput.getText().toString());
                    if (account.isEmpty() || password.isEmpty() || server.isEmpty()) {
                        Toast.makeText(this, "请完整填写账户、密码和服务器信息", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    userLoggedIn = true;
                    loginAccountInput = account;
                    loginPasswordInput = password;
                    loginServerInput = server;
                    connectedAccount = account;
                    connectedAccountName = account;
                    connectedServer = server;
                    connectedSource = "登录后同步中";
                    connectedGateway = "--";
                    connectedUpdateAtMs = System.currentTimeMillis();
                    connectedUpdate = FormatUtils.formatDateTime(connectedUpdateAtMs);
                    connectedError = "";
                    gatewayConnected = false;
                    ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(true);
                    if (preloadManager != null) {
                        preloadManager.clearLatestCache();
                        preloadManager.setFullSnapshotActive(true);
                    }
                    setConnectionStatus(gatewayConnected);
                    updateOverviewHeader();
                    persistUiState();
                    requestSnapshot();
                })
                .show();
    }

    private EditText createLoginField(String hint, boolean passwordMode) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        input.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(8);
        input.setLayoutParams(params);
        if (passwordMode) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        return input;
    }

    private void logoutAccount() {
        snapshotRequestGuard.invalidateSession();
        userLoggedIn = false;
        gatewayConnected = false;
        loading = false;
        loginPasswordInput = "";
        connectedAccount = "";
        clearScheduledRefresh();
        ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(false);
        if (preloadManager != null) {
            preloadManager.setFullSnapshotActive(false);
            preloadManager.clearLatestCache();
        }
        clearRuntimeAccountState();
        clearPersistedAccountState();
        connectedError = "";
        connectedSource = "未登录";
        connectedGateway = "--";
        connectedAccountName = "";
        connectedLeverageText = "";
        connectedUpdateAtMs = 0L;
        connectedUpdate = "--";
        setConnectionStatus(false);
        updateOverviewHeader();
        applyLoggedOutEmptyState();
        persistUiState();
    }

    // 退出登录时清空页面持有的账户内存态，避免旧数据继续展示。
    private void clearRuntimeAccountState() {
        basePositions = new ArrayList<>();
        basePendingOrders = new ArrayList<>();
        baseTrades = new ArrayList<>();
        allCurvePoints = new ArrayList<>();
        displayedCurvePoints = new ArrayList<>();
        latestOverviewMetrics = new ArrayList<>();
        connectedPositionCache = new ArrayList<>();
        connectedPendingCache = new ArrayList<>();
        connectedOverviewCache = new ArrayList<>();
        curveHistory.clear();
        tradeHistory.clear();
        lastPositionLogStates.clear();
        lastPendingLogStates.clear();
        knownTradeOpenLogKeys.clear();
        knownTradeCloseLogKeys.clear();
        knownTradeStateByKey.clear();
        lastHistoryRecordCount = 0;
        lastTradePnlMismatchState = null;
        accountLogBaselineReady = false;
        latestCumulativePnl = 0d;
        dataQualitySummary = "";
    }

    // 退出登录后顺手清掉本地账户缓存，避免其他页面继续读到旧快照。
    private void clearPersistedAccountState() {
        if (accountStorageRepository == null) {
            return;
        }
        accountStorageRepository.clearRuntimeSnapshot();
        accountStorageRepository.clearTradeHistory();
    }

    // 构造一个空账户快照，复用现有页面渲染链路统一清空界面。
    private AccountSnapshot buildEmptyAccountSnapshot() {
        return new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    // 登出后立即切回彻底空态，避免列表或图表继续残留上一账户数据。
    private void applyLoggedOutEmptyState() {
        latestOverviewMetrics = new ArrayList<>();
        connectedOverviewCache = new ArrayList<>();
        applySnapshot(buildEmptyAccountSnapshot(), false);
        overviewAdapter.submitList(buildDisconnectedOverviewMetrics());
    }

    private void setupBottomNav() {
        updateBottomTabs(false, false, true, false);
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabMarketChart.setOnClickListener(v -> openMarketChart());
        binding.tabAccountStats.setOnClickListener(v -> updateBottomTabs(false, false, true, false));
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

    private void placeCurveSectionToBottom() {
        if (binding.cardCurveSection == null) {
            return;
        }
        android.view.ViewParent parent = binding.cardCurveSection.getParent();
        if (!(parent instanceof android.widget.LinearLayout)) {
            return;
        }
        android.widget.LinearLayout container = (android.widget.LinearLayout) parent;
        android.view.View returnStatsSection = binding.cardReturnStatsSection;
        container.removeView(binding.cardCurveSection);
        if (returnStatsSection != null && returnStatsSection.getParent() == container) {
            container.removeView(returnStatsSection);
        }
        container.addView(binding.cardCurveSection);
        if (returnStatsSection != null) {
            container.addView(returnStatsSection);
        }
    }

    private void setupRecyclers() {
        binding.recyclerOverview.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerOverview.setAdapter(overviewAdapter);

        binding.recyclerCurveIndicators.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerCurveIndicators.setAdapter(indicatorAdapter);

        binding.recyclerPositionByProduct.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositionByProduct.setAdapter(positionAggregateAdapter);

        binding.recyclerPositions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositions.setItemAnimator(null);
        binding.recyclerPositions.setAdapter(positionAdapter);

        binding.recyclerPendingOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPendingOrders.setItemAnimator(null);
        binding.recyclerPendingOrders.setAdapter(pendingOrderAdapter);

        binding.recyclerTrades.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTrades.setItemAnimator(null);
        binding.recyclerTrades.setAdapter(tradeAdapter);
        configureTradeScrollHandle();

        binding.recyclerStats.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerStats.setAdapter(statsAdapter);

    }

    // 账户统计页不再用整页遮罩，而是统一切到局部打码 + 图表占位态。
    private void applyPrivacyMaskState() {
        if (binding == null) {
            return;
        }
        boolean masked = isPrivacyMasked();
        overviewAdapter.setMasked(masked);
        indicatorAdapter.setMasked(masked);
        positionAggregateAdapter.setMasked(masked);
        positionAdapter.setMasked(masked);
        pendingOrderAdapter.setMasked(masked);
        tradeAdapter.setMasked(masked);
        statsAdapter.setMasked(masked);
        binding.tradeWeekdayBarChart.setMasked(masked);
        binding.equityCurveView.setMasked(masked);
        binding.positionRatioChartView.setMasked(masked);
        binding.drawdownChartView.setMasked(masked);
        binding.dailyReturnChartView.setMasked(masked);
        binding.tradePnlBarChart.setMasked(masked);
        binding.tradeDistributionScatterView.setMasked(masked);
        binding.holdingDurationDistributionView.setMasked(masked);
        updateAccountPrivacyToggle(masked);
        updateOverviewHeader();
        renderReturnStatsTable(allCurvePoints);
        applyCurrentCurveRangeFromAllPoints();
        refreshTradeStats();
        refreshPositions();
        refreshTrades(false);
    }

    private boolean isPrivacyMasked() {
        return SensitiveDisplayMasker.isEnabled(this);
    }

    private void togglePrivacyMaskState() {
        boolean masked = !isPrivacyMasked();
        ConfigManager.getInstance(getApplicationContext()).setDataMasked(masked);
        sendServiceAction(AppConstants.ACTION_REFRESH_CONFIG);
        applyPrivacyMaskState();
    }

    private void updateAccountPrivacyToggle(boolean masked) {
        if (binding == null || binding.ivAccountPrivacyToggle == null) {
            return;
        }
        binding.ivAccountPrivacyToggle.setImageResource(masked
                ? R.drawable.ic_privacy_hidden
                : R.drawable.ic_privacy_visible);
        binding.ivAccountPrivacyToggle.setContentDescription(masked ? "显示隐私数据" : "隐藏隐私数据");
        binding.ivAccountPrivacyToggle.setAlpha(masked ? 0.9f : 1f);
    }

    private void setupFilters() {
        ArrayAdapter<String> productAdapter = createTradeFilterAdapter(new String[]{FILTER_PRODUCT});
        binding.spinnerTradeProduct.setAdapter(productAdapter);
        binding.spinnerTradeProduct.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            updateTradeFilterDisplayTexts();
            refreshTrades(true);
        }));

        ArrayAdapter<String> sideAdapter = createTradeFilterAdapter(new String[]{FILTER_SIDE, "\u4e70\u5165", "\u5356\u51fa"});
        binding.spinnerTradeSide.setAdapter(sideAdapter);
        binding.spinnerTradeSide.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            updateTradeFilterDisplayTexts();
            refreshTrades(true);
        }));

        ArrayAdapter<String> sortAdapter = createTradeFilterAdapter(createTradeSortOptions());
        binding.spinnerTradeSort.setAdapter(sortAdapter);
        binding.spinnerTradeSort.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            handleSortSelection(safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT), true);
            updateTradeFilterDisplayTexts();
            refreshTrades(true, true);
        }));

        setSpinnerSelectionByValue(binding.spinnerTradeProduct, selectedTradeProductFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeSide, selectedTradeSideFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeSort, resolveSortSelectionValue(selectedTradeSortFilter));
        selectedTradeProductFilter = safeSpinnerValue(binding.spinnerTradeProduct, selectedTradeProductFilter, FILTER_PRODUCT);
        selectedTradeSideFilter = safeSpinnerValue(binding.spinnerTradeSide, selectedTradeSideFilter, FILTER_SIDE);
        selectedTradeSortFilter = safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT);
        handleSortSelection(selectedTradeSortFilter, false);
        updateTradeFilterDisplayTexts();
        binding.tvTradeProductLabel.setOnClickListener(v -> binding.spinnerTradeProduct.performClick());
        binding.tvTradeSideLabel.setOnClickListener(v -> binding.spinnerTradeSide.performClick());
        binding.tvTradeSortLabel.setOnClickListener(v -> binding.spinnerTradeSort.performClick());

        binding.btnApplyManualRange.setOnClickListener(v -> applyManualCurveRange());
    }

    private ArrayAdapter<String> createTradeFilterAdapter(String[] options) {
        final List<String> items = new ArrayList<>();
        if (options != null) {
            for (String option : options) {
                String value = option == null ? "" : option.trim();
                if (!value.isEmpty()) {
                    items.add(value);
                }
            }
        }
        if (items.isEmpty()) {
            items.add("--");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.item_spinner_filter,
                android.R.id.text1,
                items
        );
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    private void updateTradeFilterDisplayTexts() {
        updateTradeFilterDisplayTexts(
                safeSpinnerValue(binding.spinnerTradeProduct, selectedTradeProductFilter, FILTER_PRODUCT),
                safeSpinnerValue(binding.spinnerTradeSide, selectedTradeSideFilter, FILTER_SIDE),
                safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT)
        );
    }

    private void updateTradeFilterDisplayTexts(String product, String side, String sort) {
        setTradeFilterLabel(binding.tvTradeProductLabel, product, FILTER_PRODUCT);
        setTradeFilterLabel(binding.tvTradeSideLabel, side, FILTER_SIDE);
        String sortText = trim(sort);
        if (sortText.isEmpty()) {
            sortText = FILTER_SORT;
        }
        binding.tvTradeSortLabel.setText(sortText);
    }

    private void setTradeFilterLabel(TextView labelView, String value, String fallback) {
        if (labelView == null) {
            return;
        }
        String text = trim(value);
        if (text.isEmpty()) {
            text = fallback;
        }
        labelView.setText(text);
    }

    private void handleSortSelection(String rawSortValue, boolean userAction) {
        String raw = (rawSortValue == null || rawSortValue.trim().isEmpty()) ? FILTER_SORT : rawSortValue;
        String normalized = normalizeSortValue(raw);
        if (FILTER_SORT.equals(raw)) {
            selectedTradeSortFilter = raw;
            return;
        }
        tradeSortDescending = isDescendingSortLabel(raw);
        lastExplicitTradeSortMode = normalized;
        selectedTradeSortFilter = raw;
    }

    private String normalizeSortValue(String rawSort) {
        if (rawSort == null || rawSort.trim().isEmpty() || FILTER_SORT.equals(rawSort)) {
            return SORT_CLOSE_TIME;
        }
        return stripSortDirection(rawSort);
    }

    private String[] createTradeSortOptions() {
        return new String[]{
                FILTER_SORT,
                buildSortOption(SORT_CLOSE_TIME, true),
                buildSortOption(SORT_CLOSE_TIME, false),
                buildSortOption(SORT_OPEN_TIME, true),
                buildSortOption(SORT_OPEN_TIME, false),
                buildSortOption(SORT_PROFIT, true),
                buildSortOption(SORT_PROFIT, false)
        };
    }

    private String buildSortOption(String base, boolean descending) {
        return base + (descending ? " ↓" : " ↑");
    }

    private String resolveSortSelectionValue(String rawValue) {
        String safeValue = trim(rawValue);
        if (safeValue.isEmpty() || FILTER_SORT.equals(safeValue)) {
            return FILTER_SORT;
        }
        if (safeValue.endsWith("↓") || safeValue.endsWith("↑")) {
            return safeValue;
        }
        return buildSortOption(normalizeSortValue(safeValue), tradeSortDescending);
    }

    private String stripSortDirection(String value) {
        return trim(value).replace(" ↓", "").replace(" ↑", "");
    }

    private boolean isDescendingSortLabel(String value) {
        return !trim(value).endsWith("↑");
    }

    private void showTradeSortPopupMenu(View anchor) {
        View menuAnchor = anchor == null ? binding.tvTradeSortLabel : anchor;
        PopupMenu popup = new PopupMenu(this, menuAnchor);
        String[] sortOptions = new String[]{SORT_CLOSE_TIME, SORT_OPEN_TIME, SORT_PROFIT};
        String current = normalizeSortValue(selectedTradeSortFilter);
        for (int i = 0; i < sortOptions.length; i++) {
            String option = sortOptions[i];
            String label = option;
            if (option.equals(current)) {
                label = option + (tradeSortDescending ? " ↓" : " ↑");
            }
            popup.getMenu().add(0, i, i, label);
        }
        popup.setOnMenuItemClickListener(item -> {
            int which = item.getItemId();
            if (which < 0 || which >= sortOptions.length) {
                return false;
            }
            String chosen = sortOptions[which];
            handleSortSelection(chosen, true);
            setSpinnerSelectionByValue(binding.spinnerTradeSort, chosen);
            updateTradeFilterDisplayTexts();
            refreshTrades(true, true);
            return true;
        });
        popup.show();
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

        styleSegmentButton(binding.btnTradePnlAll, "\u5168\u90e8", 11f);
        styleSegmentButton(binding.btnTradePnlBuy, "\u4e70\u5165", 11f);
        styleSegmentButton(binding.btnTradePnlSell, "\u5356\u51fa", 11f);
        styleSegmentButton(binding.btnTradeWeekdayCloseTime, "\u6309\u5e73\u4ed3\u65f6\u95f4", 11f);
        styleSegmentButton(binding.btnTradeWeekdayOpenTime, "\u6309\u5f00\u4ed3\u65f6\u95f4", 11f);

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
        binding.toggleReturnsValueMode.post(() -> autoFitSegmentButtons(
                binding.toggleReturnsValueMode,
                new MaterialButton[]{
                        binding.btnReturnsRate, binding.btnReturnsAmount
                },
                11f,
                9f));
        binding.toggleTradePnlSide.post(() -> autoFitSegmentButtons(
                binding.toggleTradePnlSide,
                new MaterialButton[]{
                        binding.btnTradePnlAll, binding.btnTradePnlBuy, binding.btnTradePnlSell
                },
                11f,
                9f));
        binding.toggleTradeWeekdayBasis.post(() -> autoFitSegmentButtons(
                binding.toggleTradeWeekdayBasis,
                new MaterialButton[]{
                        binding.btnTradeWeekdayCloseTime, binding.btnTradeWeekdayOpenTime
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
        button.setCornerRadius(0);
        button.setShapeAppearanceModel(button.getShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(0f)
                .build());

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        int checkedBg = palette.primary;
        int uncheckedBg = ContextCompat.getColor(this, R.color.bg_input);
        int checkedText = ContextCompat.getColor(this, R.color.white);
        int uncheckedText = ContextCompat.getColor(this, R.color.text_primary);
        button.setBackgroundTintList(new ColorStateList(states, new int[]{checkedBg, uncheckedBg}));
        button.setTextColor(new ColorStateList(states, new int[]{checkedText, uncheckedText}));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.stroke_card)));
        button.setStrokeWidth(dpToPx(1));
        button.setRippleColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_gold)));
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
        setupManualDatePickerPanel();
        setupReturnPeriodPickerPanel();
    }

    private void setupCurveInteraction() {
        binding.equityCurveView.setShowBottomTimeLabels(false);
        binding.equityCurveView.setMergeWithPreviousPane(false);
        binding.equityCurveView.setMergeWithNextPane(true);
        binding.positionRatioChartView.setMergeWithPreviousPane(true);
        binding.positionRatioChartView.setMergeWithNextPane(true);
        binding.drawdownChartView.setMergeWithPreviousPane(true);
        binding.drawdownChartView.setMergeWithNextPane(true);
        binding.dailyReturnChartView.setShowBottomTimeLabels(true);
        binding.dailyReturnChartView.setMergeWithPreviousPane(true);
        binding.dailyReturnChartView.setMergeWithNextPane(false);
        binding.equityCurveView.setOnPointHighlightListener((point, xRatio) -> {
            if (syncingCurveHighlight) {
                return;
            }
            if (point == null) {
                clearSharedCurveHighlight();
                return;
            }
            applySharedCurveHighlight(point.getTimestamp(), xRatio, false);
        });
        binding.drawdownChartView.setOnTimeHighlightListener((timestamp, xRatio) -> {
            if (syncingCurveHighlight) {
                return;
            }
            if (timestamp == null) {
                clearSharedCurveHighlight();
                return;
            }
            applySharedCurveHighlight(timestamp, xRatio, true);
        });
        binding.positionRatioChartView.setOnTimeHighlightListener((timestamp, xRatio) -> {
            if (syncingCurveHighlight) {
                return;
            }
            if (timestamp == null) {
                clearSharedCurveHighlight();
                return;
            }
            applySharedCurveHighlight(timestamp, xRatio, true);
        });
        binding.dailyReturnChartView.setOnTimeHighlightListener((timestamp, xRatio) -> {
            if (syncingCurveHighlight) {
                return;
            }
            if (timestamp == null) {
                clearSharedCurveHighlight();
                return;
            }
            applySharedCurveHighlight(timestamp, xRatio, true);
        });
    }

    private void setupManualDatePickerPanel() {
        binding.etRangeStart.setOnClickListener(v -> showManualDatePickerPanel(DATE_TARGET_START));
        binding.etRangeEnd.setOnClickListener(v -> showManualDatePickerPanel(DATE_TARGET_END));
        binding.btnManualDateCancel.setOnClickListener(v -> hideManualDatePickerPanel());
        binding.btnManualDateConfirm.setOnClickListener(v -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.YEAR, binding.npManualYear.getValue());
            chosen.set(Calendar.MONTH, binding.npManualMonth.getValue() - 1);
            chosen.set(Calendar.DAY_OF_MONTH, binding.npManualDay.getValue());
            chosen.set(Calendar.HOUR_OF_DAY, 0);
            chosen.set(Calendar.MINUTE, 0);
            chosen.set(Calendar.SECOND, 0);
            chosen.set(Calendar.MILLISECOND, 0);

            EditText target = manualDateTarget == DATE_TARGET_END ? binding.etRangeEnd : binding.etRangeStart;
            target.setText(dateOnlyFormat.format(chosen.getTime()));
            hideManualDatePickerPanel();
        });
        binding.npManualYear.setOnValueChangedListener((picker, oldVal, newVal) -> updateManualPickerDayRange());
        binding.npManualMonth.setOnValueChangedListener((picker, oldVal, newVal) -> updateManualPickerDayRange());
    }

    private void showManualDatePickerPanel(int target) {
        manualDateTarget = target;
        binding.tvManualDatePickerTitle.setText(target == DATE_TARGET_START ? "选择开始日期" : "选择结束日期");

        Calendar floor = Calendar.getInstance();
        floor.add(Calendar.YEAR, -8);
        int minYear = floor.get(Calendar.YEAR);
        Calendar ceil = Calendar.getInstance();
        ceil.add(Calendar.YEAR, 2);
        int maxYear = ceil.get(Calendar.YEAR);
        binding.npManualYear.setMinValue(minYear);
        binding.npManualYear.setMaxValue(maxYear);
        binding.npManualYear.setWrapSelectorWheel(false);

        binding.npManualMonth.setMinValue(1);
        binding.npManualMonth.setMaxValue(12);
        binding.npManualMonth.setWrapSelectorWheel(true);

        EditText targetView = target == DATE_TARGET_END ? binding.etRangeEnd : binding.etRangeStart;
        Calendar chosen = Calendar.getInstance();
        String text = trim(targetView.getText() == null ? "" : targetView.getText().toString());
        if (!text.isEmpty()) {
            try {
                Date parsed = dateOnlyFormat.parse(text);
                if (parsed != null) {
                    chosen.setTime(parsed);
                }
            } catch (Exception ignored) {
            }
        }

        int year = chosen.get(Calendar.YEAR);
        if (year < minYear) {
            year = minYear;
        } else if (year > maxYear) {
            year = maxYear;
        }
        binding.npManualYear.setValue(year);
        binding.npManualMonth.setValue(chosen.get(Calendar.MONTH) + 1);
        updateManualPickerDayRange();
        binding.npManualDay.setValue(chosen.get(Calendar.DAY_OF_MONTH));
        binding.layoutManualDatePickerPanel.setVisibility(View.VISIBLE);
        binding.layoutManualDatePickerPanel.post(() ->
                applyPickerPanelStyle(binding.npManualYear, binding.npManualMonth, binding.npManualDay));
    }

    private void updateManualPickerDayRange() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, binding.npManualYear.getValue());
        calendar.set(Calendar.MONTH, Math.max(0, binding.npManualMonth.getValue() - 1));
        int maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        binding.npManualDay.setMinValue(1);
        binding.npManualDay.setMaxValue(maxDay);
        binding.npManualDay.setWrapSelectorWheel(true);
    }

    private void hideManualDatePickerPanel() {
        binding.layoutManualDatePickerPanel.setVisibility(View.GONE);
    }

    private void setupReturnPeriodPickerPanel() {
        binding.tvReturnsPeriod.setOnClickListener(v -> showReturnPeriodPickerPanel());
        binding.btnReturnPeriodCancel.setOnClickListener(v -> hideReturnPeriodPickerPanel());
        binding.btnReturnPeriodConfirm.setOnClickListener(v -> {
            if (returnPickerYears.isEmpty()) {
                hideReturnPeriodPickerPanel();
                return;
            }
            int selectedYear = returnPickerYears.get(binding.npReturnYear.getValue());
            int monthIndex = binding.npReturnMonth.getValue();
            int selectedMonth = monthIndex >= 0 && monthIndex < returnPickerVisibleMonths.size()
                    ? returnPickerVisibleMonths.get(monthIndex)
                    : 1;
            Calendar selected = Calendar.getInstance();
            selected.set(selectedYear, selectedMonth - 1, 1, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);
            returnStatsAnchorDateMs = selected.getTimeInMillis();
            hideReturnPeriodPickerPanel();
            renderReturnStatsTable(allCurvePoints);
        });
        binding.npReturnYear.setOnValueChangedListener((picker, oldVal, newVal) ->
                syncReturnPickerMonthForYearIndex(newVal));
    }

    private void showReturnPeriodPickerPanel() {
        if (returnStatsMode != ReturnStatsMode.DAY) {
            binding.layoutReturnPeriodPickerPanel.setVisibility(View.GONE);
            return;
        }

        returnPickerYearMonthMap.clear();
        returnPickerYearMonthMap.putAll(buildYearMonthAvailability(allCurvePoints));
        if (returnPickerYearMonthMap.isEmpty()) {
            binding.layoutReturnPeriodPickerPanel.setVisibility(View.GONE);
            return;
        }

        returnPickerYears.clear();
        returnPickerYears.addAll(returnPickerYearMonthMap.keySet());
        returnPickerYears.sort(Integer::compareTo);

        String[] yearLabels = AccountDatePickerValueHelper.buildYearLabels(returnPickerYears);

        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(returnStatsAnchorDateMs > 0L ? returnStatsAnchorDateMs : System.currentTimeMillis());
        int year = anchor.get(Calendar.YEAR);
        int month = anchor.get(Calendar.MONTH) + 1;
        int yearIndex = returnPickerYears.indexOf(year);
        if (yearIndex < 0) {
            yearIndex = returnPickerYears.size() - 1;
            year = returnPickerYears.get(yearIndex);
        }

        binding.npReturnYear.setMinValue(0);
        binding.npReturnYear.setMaxValue(yearLabels.length - 1);
        binding.npReturnYear.setDisplayedValues(null);
        binding.npReturnYear.setDisplayedValues(yearLabels);
        binding.npReturnYear.setWrapSelectorWheel(false);
        binding.npReturnYear.setValue(yearIndex);

        binding.npReturnMonth.setWrapSelectorWheel(false);
        applyMonthPickerRange(binding.npReturnMonth, returnPickerYearMonthMap.get(year), month);
        binding.layoutReturnPeriodPickerPanel.setVisibility(View.VISIBLE);
        binding.layoutReturnPeriodPickerPanel.post(() ->
                applyPickerPanelStyle(binding.npReturnYear, binding.npReturnMonth));
    }

    private void syncReturnPickerMonthForYearIndex(int yearIndex) {
        if (returnPickerYears.isEmpty() || yearIndex < 0 || yearIndex >= returnPickerYears.size()) {
            return;
        }
        int selectedYear = returnPickerYears.get(yearIndex);
        applyMonthPickerRange(binding.npReturnMonth,
                returnPickerYearMonthMap.get(selectedYear),
                resolveSelectedReturnMonth());
        binding.layoutReturnPeriodPickerPanel.post(() ->
                applyPickerPanelStyle(binding.npReturnYear, binding.npReturnMonth));
    }

    // 统一提升日期选择器的文字和分隔线可见性。
    private void applyPickerPanelStyle(NumberPicker... pickers) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        int textColor = palette.textPrimary;
        int dividerColor = palette.stroke;
        applyPickerPanelTheme(palette);
        for (NumberPicker picker : pickers) {
            applyNumberPickerStyle(picker, textColor, dividerColor);
        }
    }

    // 把日期选择面板的容器、标题和按钮统一切到当前主题。
    private void applyPickerPanelTheme(UiPaletteManager.Palette palette) {
        if (binding == null || palette == null) {
            return;
        }
        binding.layoutManualDatePickerPanel.setBackground(
                UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.layoutReturnPeriodPickerPanel.setBackground(
                UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.tvManualDatePickerTitle.setTextColor(palette.textPrimary);
        binding.tvReturnPeriodPickerTitle.setTextColor(palette.textPrimary);
        stylePickerPanelButton(binding.btnManualDateCancel, false, palette);
        stylePickerPanelButton(binding.btnReturnPeriodCancel, false, palette);
        stylePickerPanelButton(binding.btnManualDateConfirm, true, palette);
        stylePickerPanelButton(binding.btnReturnPeriodConfirm, true, palette);
    }

    // 通过子控件和反射同步 NumberPicker 颜色，解决深色界面数字发灰的问题。
    private void applyNumberPickerStyle(@Nullable NumberPicker picker, int textColor, int dividerColor) {
        if (picker == null) {
            return;
        }
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setVerticalFadingEdgeEnabled(false);
        picker.setFadingEdgeLength(0);
        picker.setAlpha(1f);
        for (int i = 0; i < picker.getChildCount(); i++) {
            View child = picker.getChildAt(i);
            if (child instanceof EditText) {
                EditText editText = (EditText) child;
                editText.setTextColor(textColor);
                editText.setHintTextColor(textColor);
                editText.setHighlightColor(dividerColor);
                editText.setAlpha(1f);
                editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            }
        }
        try {
            java.lang.reflect.Field selectorWheelPaintField =
                    NumberPicker.class.getDeclaredField("mSelectorWheelPaint");
            selectorWheelPaintField.setAccessible(true);
            Paint paint = (Paint) selectorWheelPaintField.get(picker);
            if (paint != null) {
                paint.setColor(textColor);
                paint.setAlpha(255);
                paint.setTextSize(dpToPx(18));
            }
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field selectionDividerField =
                    NumberPicker.class.getDeclaredField("mSelectionDivider");
            selectionDividerField.setAccessible(true);
            selectionDividerField.set(picker, new android.graphics.drawable.ColorDrawable(dividerColor));
        } catch (Exception ignored) {
        }
        picker.setWillNotDraw(false);
        picker.invalidate();
        picker.postInvalidate();
        picker.requestLayout();
    }

    // 统一日期选择面板按钮风格，避免 XML 固定颜色压过主题。
    private void stylePickerPanelButton(@Nullable Button button,
                                        boolean primary,
                                        UiPaletteManager.Palette palette) {
        if (button == null || palette == null) {
            return;
        }
        if (primary) {
            button.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
            button.setTextColor(palette.surfaceStart);
        } else {
            button.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
            button.setTextColor(palette.textPrimary);
        }
    }

    private void hideReturnPeriodPickerPanel() {
        binding.layoutReturnPeriodPickerPanel.setVisibility(View.GONE);
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
        AccountDatePickerValueHelper.MonthState state =
                AccountDatePickerValueHelper.buildMonthState(months, preferredMonth);
        returnPickerVisibleMonths.clear();
        returnPickerVisibleMonths.addAll(state.getMonths());
        picker.setDisplayedValues(null);
        picker.setMinValue(0);
        picker.setMaxValue(Math.max(0, state.getLabels().length - 1));
        picker.setDisplayedValues(state.getLabels());
        picker.setValue(state.getSelectedIndex());
    }

    // 把月份选择器当前索引还原成真实月份。
    private int resolveSelectedReturnMonth() {
        int monthIndex = binding.npReturnMonth.getValue();
        if (monthIndex >= 0 && monthIndex < returnPickerVisibleMonths.size()) {
            return returnPickerVisibleMonths.get(monthIndex);
        }
        return 1;
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
        binding.tvReturnsPeriod.setEnabled(returnStatsMode == ReturnStatsMode.DAY);
        binding.tvReturnsPeriod.setAlpha(returnStatsMode == ReturnStatsMode.DAY ? 1f : 0.65f);
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
            if (returnStatsMode != ReturnStatsMode.DAY) {
                hideReturnPeriodPickerPanel();
            }
            binding.tvReturnsPeriod.setEnabled(returnStatsMode == ReturnStatsMode.DAY);
            binding.tvReturnsPeriod.setAlpha(returnStatsMode == ReturnStatsMode.DAY ? 1f : 0.65f);
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

    private void setupTradeWeekdayBasisToggle() {
        binding.toggleTradeWeekdayBasis.check(mapTradeWeekdayBasisButtonId(tradeWeekdayBasis));
        binding.toggleTradeWeekdayBasis.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            tradeWeekdayBasis = checkedId == R.id.btnTradeWeekdayOpenTime
                    ? TradeWeekdayBasis.OPEN_TIME
                    : TradeWeekdayBasis.CLOSE_TIME;
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

    private int mapTradeWeekdayBasisButtonId(TradeWeekdayBasis basis) {
        return basis == TradeWeekdayBasis.OPEN_TIME
                ? R.id.btnTradeWeekdayOpenTime
                : R.id.btnTradeWeekdayCloseTime;
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
        connectedAccount = userLoggedIn ? loginAccountInput : ACCOUNT;
        connectedAccountName = connectedAccount;
        connectedServer = userLoggedIn ? loginServerInput : SERVER;
        connectedSource = userLoggedIn ? "历史数据（网关离线）" : "未登录";
        connectedGateway = "--";
        connectedUpdateAtMs = System.currentTimeMillis();
        connectedUpdate = FormatUtils.formatDateTime(connectedUpdateAtMs);
        connectedLeverageText = "";
        connectedError = "";
        dataQualitySummary = "";
        gatewayConnected = false;
        setConnectionStatus(false);
        updateOverviewHeader();
    }

    private void applyPreloadedCacheIfAvailable() {
        if (!isAccountSessionReady()) {
            applyLoggedOutEmptyState();
            return;
        }
        AccountStorageRepository.StoredSnapshot storedSnapshot = accountStorageRepository == null
                ? null
                : accountStorageRepository.loadStoredSnapshot();
        if (preloadManager == null) {
            applyStoredSnapshotIfAvailable();
            return;
        }
        AccountStatsPreloadManager.Cache cache = preloadManager.getLatestCache();
        if (cache == null || cache.getSnapshot() == null) {
            applyStoredSnapshotIfAvailable();
            return;
        }
        if (System.currentTimeMillis() - cache.getFetchedAt() > AppConstants.ACCOUNT_REFRESH_INTERVAL_MS * 3L) {
            applyStoredSnapshotIfAvailable();
            return;
        }
        connectedAccount = cache.getAccount().isEmpty() ? ACCOUNT : cache.getAccount();
        connectedAccountName = connectedAccount;
        connectedServer = cache.getServer().isEmpty() ? SERVER : cache.getServer();
        connectedSource = normalizeSource(cache.getSource());
        connectedGateway = cache.getGateway().isEmpty() ? "--" : cache.getGateway();
        long updateAt = cache.getUpdatedAt() > 0L ? cache.getUpdatedAt() : cache.getFetchedAt();
        connectedUpdateAtMs = updateAt;
        connectedUpdate = FormatUtils.formatDateTime(updateAt);
        connectedError = cache.getError();

        setConnectionStatus(cache.isConnected());
        updateOverviewHeader();
        logConnectionEvent(cache.isConnected());
        AccountSnapshot mergedSnapshot = AccountSnapshotRestoreHelper.mergeMissingTrades(
                cache.getSnapshot(),
                storedSnapshot
        );
        applySnapshot(mergedSnapshot, cache.isConnected());
        persistSnapshotToStorage(
                mergedSnapshot,
                cache.isConnected(),
                connectedAccount,
                connectedServer,
                connectedSource,
                connectedGateway,
                connectedUpdateAtMs,
                connectedError,
                cache.getFetchedAt()
        );
    }

    private void applyStoredSnapshotIfAvailable() {
        if (accountStorageRepository == null || !isAccountSessionReady()) {
            return;
        }
        AccountStorageRepository.StoredSnapshot snapshot = accountStorageRepository.loadStoredSnapshot();
        if (snapshot.getPositions().isEmpty()
                && snapshot.getPendingOrders().isEmpty()
                && snapshot.getTrades().isEmpty()
                && snapshot.getCurvePoints().isEmpty()) {
            return;
        }
        connectedAccount = snapshot.getAccount().isEmpty() ? ACCOUNT : snapshot.getAccount();
        connectedAccountName = connectedAccount;
        connectedServer = snapshot.getServer().isEmpty() ? SERVER : snapshot.getServer();
        connectedSource = snapshot.getSource().isEmpty() ? "历史数据（本地恢复）" : snapshot.getSource();
        connectedGateway = snapshot.getGateway().isEmpty() ? "--" : snapshot.getGateway();
        long updateAt = snapshot.getUpdatedAt() > 0L ? snapshot.getUpdatedAt() : snapshot.getFetchedAt();
        connectedUpdateAtMs = updateAt > 0L ? updateAt : System.currentTimeMillis();
        connectedUpdate = FormatUtils.formatDateTime(connectedUpdateAtMs);
        connectedError = snapshot.getError();
        setConnectionStatus(snapshot.isConnected());
        updateOverviewHeader();
        applySnapshot(buildAccountSnapshot(snapshot), snapshot.isConnected());
    }

    private AccountSnapshot buildAccountSnapshot(AccountStorageRepository.StoredSnapshot snapshot) {
        return new AccountSnapshot(
                snapshot.getOverviewMetrics(),
                snapshot.getCurvePoints(),
                snapshot.getCurveIndicators(),
                snapshot.getPositions(),
                snapshot.getPendingOrders(),
                snapshot.getTrades(),
                snapshot.getStatsMetrics()
        );
    }

    private void updateOverviewHeader() {
        boolean masked = isPrivacyMasked();
        boolean disconnected = !userLoggedIn && !gatewayConnected;
        String title = disconnected
                ? "账户总览（未连接）"
                : AccountStatsPrivacyFormatter.formatOverviewTitle(
                AccountOverviewTitleHelper.resolveDisplayAccount(
                        connectedAccount,
                        connectedAccountName,
                        ACCOUNT
                ),
                masked);
        if (!masked && !connectedLeverageText.isEmpty()) {
            String leverageText = "（" + connectedLeverageText + "）";
            SpannableStringBuilder builder = new SpannableStringBuilder(title).append(leverageText);
            int leverageStart = builder.length() - leverageText.length();
            builder.setSpan(new AbsoluteSizeSpan(12, true),
                    leverageStart,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_secondary)),
                    leverageStart,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            binding.tvAccountOverviewTitle.setText(builder);
        } else {
            binding.tvAccountOverviewTitle.setText(title);
        }
        binding.tvAccountMeta.setText(disconnected
                ? "更新时间 --"
                : AccountStatsPrivacyFormatter.formatRefreshMeta(formatRefreshMetaText(), masked));
    }

    private boolean isAccountSessionReady() {
        return userLoggedIn && ConfigManager.getInstance(getApplicationContext()).isAccountSessionActive();
    }

    private void scheduleNextSnapshot(long delayMs) {
        long safeDelay = AccountRefreshMetaHelper.normalizeDelayMs(delayMs);
        refreshHandler.removeCallbacks(refreshRunnable);
        scheduledRefreshDelayMs = safeDelay;
        nextRefreshAtMs = System.currentTimeMillis() + safeDelay;
        refreshHandler.postDelayed(refreshRunnable, safeDelay);
    }

    // 清空已排队的下一次刷新，保证页面显示与真实调度保持一致。
    private void clearScheduledRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        nextRefreshAtMs = 0L;
        scheduledRefreshDelayMs = 0L;
    }

    private void startOverviewHeaderTicker() {
        refreshHandler.removeCallbacks(overviewHeaderTicker);
        refreshHandler.post(overviewHeaderTicker);
    }

    private void stopOverviewHeaderTicker() {
        refreshHandler.removeCallbacks(overviewHeaderTicker);
    }

    private String formatRefreshMetaText() {
        long nowMs = System.currentTimeMillis();
        long updateAt = connectedUpdateAtMs > 0L ? connectedUpdateAtMs : nowMs;
        long intervalSeconds = AccountRefreshMetaHelper.resolveIntervalSeconds(
                scheduledRefreshDelayMs,
                dynamicRefreshDelayMs
        );
        long remainSeconds = AccountRefreshMetaHelper.resolveRemainingSeconds(
                nextRefreshAtMs,
                nowMs,
                intervalSeconds
        );
        return FormatUtils.formatDateTime(updateAt) + "（" + remainSeconds + "秒/" + intervalSeconds + "秒）";
    }

    private void requestSnapshot() {
        if (!userLoggedIn) {
            loading = false;
            gatewayConnected = false;
            setConnectionStatus(false);
            clearScheduledRefresh();
            updateOverviewHeader();
            return;
        }
        if (loading) {
            return;
        }
        loading = true;
        final AccountSnapshotRequestGuard.RequestToken requestToken = snapshotRequestGuard.openRequest();
        AccountTimeRange fetchRange = AccountTimeRange.ALL;

        ioExecutor.execute(() -> {
            Mt5BridgeGatewayClient.SnapshotResult remote = gatewayClient.fetch(fetchRange);
            AccountSnapshot snapshot;
            boolean connected;
            String account;
            String accountName;
            String server;
            String source;
            String gateway;
            long updatedAt;
            String error;

            if (remote.isSuccess()) {
                boolean loginMatched = isLoginCredentialMatched(remote);
                if (loginMatched) {
                    snapshot = remote.getSnapshot();
                    connected = true;
                    account = remote.getAccount(trim(loginAccountInput).isEmpty() ? ACCOUNT : loginAccountInput);
                    accountName = remote.getAccountName(account);
                    server = remote.getServer(trim(loginServerInput).isEmpty() ? SERVER : loginServerInput);
                    source = normalizeSource(remote.getLocalizedSource());
                    gateway = remote.getGatewayEndpoint();
                    updatedAt = remote.getUpdatedAt() > 0L ? remote.getUpdatedAt() : System.currentTimeMillis();
                    error = "";
                } else {
                    snapshot = null;
                    connected = false;
                    account = trim(loginAccountInput).isEmpty() ? ACCOUNT : loginAccountInput;
                    accountName = account;
                    server = trim(loginServerInput).isEmpty() ? SERVER : loginServerInput;
                    source = "登录校验失败";
                    gateway = remote.getGatewayEndpoint();
                    updatedAt = System.currentTimeMillis();
                    error = "登录账户或服务器与网关返回不一致";
                }
            } else {
                // 断线时保持当前界面数据，不回落到演示/备用数据。
                snapshot = null;
                connected = false;
                account = trim(loginAccountInput).isEmpty() ? ACCOUNT : loginAccountInput;
                accountName = account;
                server = trim(loginServerInput).isEmpty() ? SERVER : loginServerInput;
                source = "历史数据（网关离线）";
                gateway = "Gateway offline";
                updatedAt = System.currentTimeMillis();
                error = remote.getError();
            }

            final AccountSnapshot finalSnapshot = snapshot;
            final boolean finalConnected = connected;
            final String finalAccount = account;
            final String finalAccountName = accountName;
            final String finalServer = server;
            final String finalSource = source;
            final String finalGateway = gateway;
            final long finalUpdatedAt = updatedAt;
            final String finalError = error;
            final boolean finalUnchanged = remote.isUnchanged();

            runOnUiThread(() -> {
                if (!snapshotRequestGuard.shouldApply(requestToken)) {
                    return;
                }
                boolean previousConnected = gatewayConnected;
                connectedAccount = finalAccount;
                connectedAccountName = finalAccountName;
                connectedServer = finalServer;
                connectedSource = finalSource;
                connectedGateway = finalGateway;
                connectedUpdateAtMs = finalUpdatedAt;
                connectedUpdate = FormatUtils.formatDateTime(finalUpdatedAt);
                connectedError = finalError;

                setConnectionStatus(finalConnected);
                if (AccountConnectionTransitionHelper.shouldShowLoginSuccess(previousConnected, finalConnected, userLoggedIn)) {
                    showLoginSuccessBanner();
                }
                updateOverviewHeader();
                logConnectionEvent(finalConnected);
                if (finalSnapshot != null) {
                    applySnapshot(finalSnapshot, finalConnected);
                    persistSnapshotToStorage(
                            finalSnapshot,
                            finalConnected,
                            finalAccount,
                            finalServer,
                            finalSource,
                            finalGateway,
                            finalUpdatedAt,
                            finalError,
                            System.currentTimeMillis()
                    );
                }
                adjustRefreshCadence(finalConnected, finalUnchanged);
                loading = false;
                if (shouldKeepRefreshLoop()) {
                    scheduleNextSnapshot(dynamicRefreshDelayMs);
                } else {
                    clearScheduledRefresh();
                }
                updateOverviewHeader();
            });
        });
    }

    private void persistSnapshotToStorage(AccountSnapshot snapshot,
                                          boolean connected,
                                          String account,
                                          String server,
                                          String source,
                                          String gateway,
                                          long updatedAt,
                                          String error,
                                          long fetchedAt) {
        if (accountStorageRepository == null || snapshot == null) {
            return;
        }
        accountStorageRepository.persistSnapshot(new AccountStorageRepository.StoredSnapshot(
                connected,
                account,
                server,
                source,
                gateway,
                updatedAt,
                error,
                fetchedAt,
                snapshot.getOverviewMetrics(),
                snapshot.getCurvePoints(),
                snapshot.getCurveIndicators(),
                snapshot.getPositions(),
                snapshot.getPendingOrders(),
                snapshot.getTrades(),
                snapshot.getStatsMetrics()
        ));
    }

    private boolean isLoginCredentialMatched(Mt5BridgeGatewayClient.SnapshotResult remote) {
        String expectedAccount = trim(loginAccountInput);
        String expectedServer = trim(loginServerInput);
        String remoteAccount = trim(remote.getAccount(""));
        String remoteServer = trim(remote.getServer(""));
        boolean accountMatched = expectedAccount.isEmpty() || remoteAccount.equalsIgnoreCase(expectedAccount);
        boolean serverMatched = expectedServer.isEmpty() || remoteServer.equalsIgnoreCase(expectedServer);
        return accountMatched && serverMatched;
    }

    private void adjustRefreshCadence(boolean connected, boolean unchanged) {
        if (!connected) {
            unchangedRefreshStreak = 0;
            dynamicRefreshDelayMs = Math.min(ACCOUNT_REFRESH_MAX_MS, ACCOUNT_REFRESH_MIN_MS * 2L);
            return;
        }
        if (unchanged) {
            unchangedRefreshStreak = Math.min(12, unchangedRefreshStreak + 1);
        } else {
            unchangedRefreshStreak = 0;
        }
        dynamicRefreshDelayMs = Math.min(
                ACCOUNT_REFRESH_MAX_MS,
                ACCOUNT_REFRESH_MIN_MS + unchangedRefreshStreak * 2_000L);
    }

    // 仅在页面处于活跃状态且用户保持登录时，才继续排队下一次刷新。
    private boolean shouldKeepRefreshLoop() {
        return snapshotLoopEnabled && userLoggedIn && !isFinishing() && !isDestroyed();
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
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        gatewayConnected = connected;
        if (!userLoggedIn) {
            binding.tvAccountConnectionStatus.setText("未登录账户");
            binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createOutlinedDrawable(this,
                    UiPaletteManager.neutralFill(this),
                    UiPaletteManager.neutralStroke(this)));
            binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            return;
        }
        binding.tvAccountConnectionStatus.setText(connected ? "已连接账户" : "已登录账户");
        if (connected) {
            binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
            binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createOutlinedDrawable(this,
                    UiPaletteManager.neutralFill(this),
                    UiPaletteManager.neutralStroke(this)));
            binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void logConnectionEvent(boolean connected) {
        if (logManager == null) {
            return;
        }
        boolean stateChanged = lastLoggedConnectedState == null || lastLoggedConnectedState != connected;
        if (stateChanged) {
            if (connected) {
                logManager.info("AccountStats connected: account=" + connectedAccount
                        + ", server=" + connectedServer
                        + ", source=" + trim(connectedSource));
            } else {
                String message = "AccountStats disconnected: fallback source=" + trim(connectedSource);
                String error = trim(connectedError);
                if (!error.isEmpty()) {
                    message = message + ", error=" + error;
                }
                logManager.warn(message);
            }
        }

        String source = trim(connectedSource);
        if (!source.equals(lastLoggedSource)) {
            logManager.info("AccountStats data source -> " + source);
            lastLoggedSource = source;
        }

        String gateway = trim(connectedGateway);
        if (!gateway.equals(lastLoggedGateway) && !gateway.isEmpty()) {
            logManager.info("AccountStats gateway -> " + gateway);
            lastLoggedGateway = gateway;
        }

        String error = trim(connectedError);
        if (!connected && !error.isEmpty() && !error.equals(lastLoggedError)) {
            logManager.warn("AccountStats gateway error: " + error);
        }
        lastLoggedError = error;
        lastLoggedConnectedState = connected;
    }

    private void logAccountSnapshotEvents(List<PositionItem> positions,
                                          List<PositionItem> pendingOrders,
                                          List<TradeRecordItem> trades,
                                          boolean remoteConnected) {
        if (logManager == null || !remoteConnected) {
            return;
        }

        Map<String, PositionLogState> currentPositions = buildPositionLogStateMap(positions);
        Map<String, PendingLogState> currentPending = buildPendingLogStateMap(pendingOrders);

        if (!accountLogBaselineReady) {
            lastPositionLogStates.clear();
            lastPositionLogStates.putAll(currentPositions);
            lastPendingLogStates.clear();
            lastPendingLogStates.putAll(currentPending);
            knownTradeOpenLogKeys.clear();
            knownTradeCloseLogKeys.clear();
            knownTradeStateByKey.clear();
            if (trades != null) {
                for (TradeRecordItem item : trades) {
                    knownTradeOpenLogKeys.add(buildTradeOpenLogKey(item));
                    knownTradeCloseLogKeys.add(buildTradeCloseLogKey(item));
                    knownTradeStateByKey.put(buildTradeDedupeKey(item), buildTradeStateSignature(item));
                }
            }
            lastHistoryRecordCount = trades == null ? 0 : trades.size();
            accountLogBaselineReady = true;
            return;
        }

        final double qtyEps = 1e-6;
        Set<String> positionKeys = new HashSet<>();
        positionKeys.addAll(lastPositionLogStates.keySet());
        positionKeys.addAll(currentPositions.keySet());
        for (String key : positionKeys) {
            PositionLogState previous = lastPositionLogStates.get(key);
            PositionLogState current = currentPositions.get(key);
            if (previous == null && current != null && Math.abs(current.quantity) > qtyEps) {
                logManager.info("Position opened: " + current.code + " " + current.side
                        + " qty=" + shortQty(current.quantity)
                        + " avgCost=$" + FormatUtils.formatPrice(current.avgCostPrice));
                continue;
            }
            if (previous != null && (current == null || Math.abs(current.quantity) <= qtyEps)) {
                logManager.info("Position closed: " + previous.code + " " + previous.side
                        + " qty=" + shortQty(previous.quantity));
                continue;
            }
            if (previous == null || current == null) {
                continue;
            }
            double delta = current.quantity - previous.quantity;
            double threshold = Math.max(1e-4, Math.max(Math.abs(previous.quantity), Math.abs(current.quantity)) * 0.001d);
            if (Math.abs(delta) >= threshold) {
                if (delta > 0d) {
                    logManager.info("Position increased: " + current.code + " " + current.side
                            + " +" + shortQty(delta) + " -> " + shortQty(current.quantity));
                } else {
                    logManager.info("Position reduced: " + current.code + " " + current.side
                            + " " + shortQty(delta) + " -> " + shortQty(current.quantity));
                }
            }
        }

        Set<String> pendingKeys = new HashSet<>();
        pendingKeys.addAll(lastPendingLogStates.keySet());
        pendingKeys.addAll(currentPending.keySet());
        for (String key : pendingKeys) {
            PendingLogState previous = lastPendingLogStates.get(key);
            PendingLogState current = currentPending.get(key);
            if (previous == null && current != null && current.count > 0) {
                logManager.info("Pending added: " + current.code + " " + current.side
                        + " lots=" + shortQty(current.lots) + ", count=" + current.count);
                continue;
            }
            if (previous != null && (current == null || current.count <= 0 || Math.abs(current.lots) <= qtyEps)) {
                logManager.info("Pending removed: " + previous.code + " " + previous.side
                        + " lots=" + shortQty(previous.lots) + ", count=" + previous.count);
                continue;
            }
            if (previous == null || current == null) {
                continue;
            }
            boolean countChanged = previous.count != current.count;
            boolean lotsChanged = Math.abs(current.lots - previous.lots) >= Math.max(1e-4, Math.abs(previous.lots) * 0.01d);
            if (countChanged || lotsChanged) {
                logManager.info("Pending changed: " + current.code + " " + current.side
                        + " lots " + shortQty(previous.lots) + " -> " + shortQty(current.lots)
                        + ", count " + previous.count + " -> " + current.count);
            }
        }

        if (trades != null && !trades.isEmpty()) {
            List<TradeRecordItem> orderedTrades = new ArrayList<>(trades);
            orderedTrades.sort(Comparator.comparingLong(this::resolveCloseTime));
            Map<String, String> currentTradeStates = new HashMap<>();
            int budget = 80;
            for (TradeRecordItem item : orderedTrades) {
                String tradeKey = buildTradeDedupeKey(item);
                String tradeState = buildTradeStateSignature(item);
                currentTradeStates.put(tradeKey, tradeState);
                String previousState = knownTradeStateByKey.get(tradeKey);
                if (previousState != null && !previousState.equals(tradeState) && budget > 0) {
                    logManager.info("Trade changed: " + symbolForLog(item.getCode(), item.getProductName())
                            + " " + sideForLog(item.getSide())
                            + " qty=" + shortQty(item.getQuantity())
                            + " pnl=" + signedMoney(item.getProfit())
                            + " close=" + FormatUtils.formatDateTime(resolveCloseTime(item)));
                    budget--;
                }

                String openKey = buildTradeOpenLogKey(item);
                boolean newOpenKey = previousState == null && knownTradeOpenLogKeys.add(openKey);
                if (newOpenKey && budget > 0) {
                    logManager.info("Trade opened: " + symbolForLog(item.getCode(), item.getProductName())
                            + " " + sideForLog(item.getSide())
                            + " qty=" + shortQty(item.getQuantity())
                            + " open=" + FormatUtils.formatDateTime(resolveOpenTime(item)));
                    budget--;
                }

                String closeKey = buildTradeCloseLogKey(item);
                boolean newCloseKey = previousState == null && knownTradeCloseLogKeys.add(closeKey);
                if (newCloseKey && budget > 0) {
                    logManager.info("Trade closed: " + symbolForLog(item.getCode(), item.getProductName())
                            + " " + sideForLog(item.getSide())
                            + " qty=" + shortQty(item.getQuantity())
                            + " pnl=" + signedMoney(item.getProfit())
                            + " close=" + FormatUtils.formatDateTime(resolveCloseTime(item)));
                    budget--;
                }
            }

            int historyRecordCount = currentTradeStates.size();
            if (historyRecordCount != lastHistoryRecordCount) {
                int delta = historyRecordCount - lastHistoryRecordCount;
                if (delta > 0) {
                    logManager.info("History records added: +" + delta + " -> " + historyRecordCount);
                } else if (lastHistoryRecordCount > 0) {
                    logManager.warn("History records reduced: " + delta + " -> " + historyRecordCount);
                }
                lastHistoryRecordCount = historyRecordCount;
            }

            int removedTrades = 0;
            for (String key : new HashSet<>(knownTradeStateByKey.keySet())) {
                if (!currentTradeStates.containsKey(key)) {
                    removedTrades++;
                }
            }
            if (removedTrades > 0) {
                logManager.warn("Trade records removed from snapshot: " + removedTrades);
            }

            knownTradeStateByKey.clear();
            knownTradeStateByKey.putAll(currentTradeStates);
        }

        if (knownTradeOpenLogKeys.size() > 50_000 || knownTradeCloseLogKeys.size() > 50_000) {
            knownTradeOpenLogKeys.clear();
            knownTradeCloseLogKeys.clear();
            if (trades != null) {
                for (TradeRecordItem item : trades) {
                    knownTradeOpenLogKeys.add(buildTradeOpenLogKey(item));
                    knownTradeCloseLogKeys.add(buildTradeCloseLogKey(item));
                }
            }
        }

        lastPositionLogStates.clear();
        lastPositionLogStates.putAll(currentPositions);
        lastPendingLogStates.clear();
        lastPendingLogStates.putAll(currentPending);
    }

    private Map<String, PositionLogState> buildPositionLogStateMap(List<PositionItem> positions) {
        Map<String, PositionLogState> result = new HashMap<>();
        if (positions == null || positions.isEmpty()) {
            return result;
        }
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            double qty = Math.max(0d, Math.abs(item.getQuantity()));
            if (qty <= 1e-9) {
                continue;
            }
            String code = symbolForLog(item.getCode(), item.getProductName());
            String side = sideForLog(item.getSide());
            String key = code + "|" + side;
            PositionLogState state = result.get(key);
            if (state == null) {
                state = new PositionLogState();
                state.productName = trim(item.getProductName());
                state.code = code;
                state.side = side;
                result.put(key, state);
            }
            state.quantity += qty;
            state.costAmount += qty * Math.max(0d, item.getCostPrice());
            state.latestPrice = Math.max(state.latestPrice, Math.max(0d, item.getLatestPrice()));
        }
        for (PositionLogState state : result.values()) {
            state.avgCostPrice = state.quantity > 0d ? state.costAmount / state.quantity : 0d;
        }
        return result;
    }

    private Map<String, PendingLogState> buildPendingLogStateMap(List<PositionItem> pendingOrders) {
        Map<String, PendingLogState> result = new HashMap<>();
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return result;
        }
        for (PositionItem item : pendingOrders) {
            if (item == null) {
                continue;
            }
            double lots = Math.max(0d, item.getPendingLots());
            int count = Math.max(0, item.getPendingCount());
            if (lots <= 1e-9 && count <= 0) {
                continue;
            }
            String code = symbolForLog(item.getCode(), item.getProductName());
            String side = sideForLog(item.getSide());
            String key = code + "|" + side;
            PendingLogState state = result.get(key);
            if (state == null) {
                state = new PendingLogState();
                state.productName = trim(item.getProductName());
                state.code = code;
                state.side = side;
                result.put(key, state);
            }
            state.lots += lots;
            state.count += count;
            double pendingPrice = item.getPendingPrice() > 0d ? item.getPendingPrice() : item.getLatestPrice();
            if (pendingPrice > 0d) {
                state.priceAmount += pendingPrice * Math.max(1d, lots);
                state.priceWeight += Math.max(1d, lots);
            }
        }
        for (PendingLogState state : result.values()) {
            state.price = state.priceWeight > 0d ? state.priceAmount / state.priceWeight : 0d;
        }
        return result;
    }

    private String buildTradeOpenLogKey(TradeRecordItem item) {
        String code = symbolForLog(item == null ? "" : item.getCode(), item == null ? "" : item.getProductName());
        String side = sideForLog(item == null ? "" : item.getSide());
        long open = item == null ? 0L : resolveOpenTime(item);
        long qty = item == null ? 0L : Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long price = item == null ? 0L : Math.round(Math.abs(item.getPrice()) * 100d);
        return code + "|" + side + "|" + open + "|" + qty + "|" + price;
    }

    private String buildTradeCloseLogKey(TradeRecordItem item) {
        return buildTradeDedupeKey(item);
    }

    private String buildTradeStateSignature(TradeRecordItem item) {
        if (item == null) {
            return "";
        }
        long open = resolveOpenTime(item);
        long close = resolveCloseTime(item);
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long price = Math.round(Math.abs(item.getPrice()) * 100d);
        long profit = Math.round(item.getProfit() * 100d);
        long storage = Math.round(item.getStorageFee() * 100d);
        String remark = trim(item.getRemark());
        String side = normalizeSide(trim(item.getSide())).toLowerCase(Locale.ROOT);
        return open + "|" + close + "|" + qty + "|" + price + "|" + profit + "|" + storage
                + "|" + side + "|" + remark;
    }

    private String symbolForLog(String code, String productName) {
        String normalizedCode = trim(code).toUpperCase(Locale.ROOT);
        if (!normalizedCode.isEmpty()) {
            return normalizedCode;
        }
        return trim(productName).toUpperCase(Locale.ROOT);
    }

    private String sideForLog(String side) {
        String normalized = normalizeSide(trim(side));
        if ("buy".equalsIgnoreCase(normalized)) {
            return "Buy";
        }
        if ("sell".equalsIgnoreCase(normalized)) {
            return "Sell";
        }
        return normalized;
    }

    private String shortQty(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private static final class PositionLogState {
        private String productName;
        private String code;
        private String side;
        private double quantity;
        private double costAmount;
        private double avgCostPrice;
        private double latestPrice;
    }

    private static final class PendingLogState {
        private String productName;
        private String code;
        private String side;
        private double lots;
        private int count;
        private double priceAmount;
        private double priceWeight;
        private double price;
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
        dataQualitySummary = buildDataQualitySummary(effectiveTrades, effectiveCurves, basePositions);

        baseTrades = new ArrayList<>(effectiveTrades);
        baseTrades.sort((a, b) -> Long.compare(resolveCloseTime(b), resolveCloseTime(a)));
        allCurvePoints = normalizeCurvePoints(effectiveCurves, baseTrades);
        logAccountSnapshotEvents(basePositions, basePendingOrders, baseTrades, remoteConnected);
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

        List<AccountMetric> overview = buildOverviewMetrics(
                latestOverviewMetrics,
                buildPositionListWithNaturalDayPnl(basePositions, baseTrades)
        );

        overviewAdapter.submitList(overview);
        updateTradeProductOptions();
        renderReturnStatsTable(allCurvePoints);
        applyCurrentCurveRangeFromAllPoints();
        refreshTradeStats();
        refreshPositions();
        refreshTrades(false);
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
            String key = buildTradeHistoryKey(item);
            tradeHistory.put(key, item);
        }
        while (tradeHistory.size() > 20_000) {
            String firstKey = tradeHistory.keySet().iterator().next();
            tradeHistory.remove(firstKey);
        }
    }

    private String buildTradeHistoryKey(TradeRecordItem item) {
        if (item.getDealTicket() > 0L) {
            return "deal|" + item.getDealTicket();
        }
        if (item.getOrderId() > 0L || item.getPositionId() > 0L) {
            return "trade|" + item.getOrderId() + "|" + item.getPositionId()
                    + "|" + item.getEntryType()
                    + "|" + resolveOpenTime(item)
                    + "|" + resolveCloseTime(item)
                    + "|" + Math.round(Math.abs(item.getQuantity()) * 10_000d);
        }
        return buildTradeDedupeKey(item);
    }

    private String buildDataQualitySummary(List<TradeRecordItem> trades,
                                           List<CurvePoint> curves,
                                           List<PositionItem> positions) {
        int tradeCount = trades == null ? 0 : trades.size();
        int curveCount = curves == null ? 0 : curves.size();
        int missingOpen = 0;
        int missingClose = 0;
        int nearZeroProfit = 0;
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
                if (Math.abs(item.getProfit()) < TRADE_PNL_ZERO_THRESHOLD) {
                    nearZeroProfit++;
                }
            }
        }
        int missingTp = 0;
        int missingSl = 0;
        if (positions != null) {
            for (PositionItem item : positions) {
                if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                    continue;
                }
                if (item.getTakeProfit() <= 0d) {
                    missingTp++;
                }
                if (item.getStopLoss() <= 0d) {
                    missingSl++;
                }
            }
        }
        String capHint = tradeCount >= 500 ? ", 疑似存在上限截断" : "";
        return "交易" + tradeCount
                + "条, 曲线" + curveCount
                + "点, 开仓时间缺失" + missingOpen
                + "条, 平仓时间缺失" + missingClose
                + "条, 近零盈亏" + nearZeroProfit
                + "条, 持仓缺止盈" + missingTp
                + "条, 持仓缺止损" + missingSl + "条" + capHint;
    }

    private List<CurvePoint> normalizeCurvePoints(List<CurvePoint> source, List<TradeRecordItem> trades) {
        return AccountCurveRebuildHelper.rebuild(source, trades, ACCOUNT_INITIAL_BALANCE);
    }

    private List<AccountMetric> buildOverviewMetrics(List<AccountMetric> snapshotOverview,
                                                     List<PositionItem> currentPositions) {
        List<AccountMetric> result = new ArrayList<>();
        if (allCurvePoints.isEmpty()) {
            latestCumulativePnl = 0d;
            if (!userLoggedIn && !gatewayConnected) {
                return buildDisconnectedOverviewMetrics();
            }
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
        List<PositionItem> metricsPositions = currentPositions == null ? new ArrayList<>() : currentPositions;
        for (PositionItem item : metricsPositions) {
            marketValue += item.getMarketValue();
            positionPnl += item.getTotalPnL() + item.getStorageFee();
        }
        double positionRatio = safeDivide(marketValue, Math.max(1d, netAsset));
        double positionPnlRate = safeDivide(positionPnl, Math.max(1d, marketValue));

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

    // 退出登录后账户概览统一显示为空值，避免误把默认值当成真实账户数据。
    private List<AccountMetric> buildDisconnectedOverviewMetrics() {
        List<AccountMetric> result = new ArrayList<>();
        result.add(new AccountMetric("总资产", "--"));
        result.add(new AccountMetric("净资产", "--"));
        result.add(new AccountMetric("可用预付款", "--"));
        result.add(new AccountMetric("保证金", "--"));
        result.add(new AccountMetric("仓位占比", "--"));
        result.add(new AccountMetric("持仓市值", "--"));
        result.add(new AccountMetric("持仓盈亏", "--"));
        result.add(new AccountMetric("持仓收益率", "--"));
        result.add(new AccountMetric("当日盈亏", "--"));
        result.add(new AccountMetric("当日收益率", "--"));
        result.add(new AccountMetric("累计盈亏", "--"));
        result.add(new AccountMetric("累计收益率", "--"));
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
                sum += item.getProfit() + item.getStorageFee();
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
        Calendar calendar = Calendar.getInstance(BEIJING_TIME_ZONE);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long endOfToday() {
        Calendar calendar = Calendar.getInstance(BEIJING_TIME_ZONE);
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
        boolean masked = isPrivacyMasked();
        statsAdapter.submitList(buildTradeStatsMetrics(baseTrades, allCurvePoints));
        List<TradePnlBarChartView.Entry> entries = buildTradePnlChartEntries(baseTrades, tradePnlSideMode);
        binding.tradePnlBarChart.setEntries(entries);
        List<TradeRecordItem> scopedTrades = filterTradesBySideMode(baseTrades, tradePnlSideMode);
        binding.tradeDistributionScatterView.setPoints(
                CurveAnalyticsHelper.buildTradeScatterPoints(scopedTrades, allCurvePoints));
        binding.holdingDurationDistributionView.setBuckets(
                CurveAnalyticsHelper.buildHoldingDurationDistribution(scopedTrades));
        refreshTradeWeekdayStats(scopedTrades);

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
        if (masked) {
            binding.tvTradePnlSummary.setText(String.format(
                    Locale.getDefault(),
                    "全周期总计盈亏（%s）: %s",
                    sideLabel,
                    SensitiveDisplayMasker.MASK_TEXT));
            binding.tvTradePnlSummary.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            binding.tvTradePnlLegend.setVisibility(View.GONE);
            return;
        }
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

    private void refreshTradeWeekdayStats(List<TradeRecordItem> trades) {
        List<TradeWeekdayStatsHelper.Row> rows = TradeWeekdayStatsHelper.buildRows(
                trades,
                tradeWeekdayBasis == TradeWeekdayBasis.OPEN_TIME
                        ? TradeWeekdayStatsHelper.TimeBasis.OPEN_TIME
                        : TradeWeekdayStatsHelper.TimeBasis.CLOSE_TIME
        );
        binding.tradeWeekdayBarChart.setEntries(TradeWeekdayBarChartHelper.buildEntries(rows));
    }

    private List<AccountMetric> buildTradeStatsMetrics(List<TradeRecordItem> trades, List<CurvePoint> curvePoints) {
        List<AccountMetric> result = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            result.add(new AccountMetric("最近交易", "--"));
            result.add(new AccountMetric("交易次数", "0"));
            result.add(new AccountMetric("盈利交易", "0次\n0.00%"));
            result.add(new AccountMetric("亏损交易", "0次\n0.00%"));
            result.add(new AccountMetric("最好交易", "--"));
            result.add(new AccountMetric("最差交易", "--"));
            result.add(new AccountMetric("毛利", "--"));
            result.add(new AccountMetric("毛损", "--"));
            result.add(new AccountMetric("最大连续盈利", "0次\n--"));
            result.add(new AccountMetric("最大连续亏损", "0次\n--"));
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
            double profitWithStorage = profit + item.getStorageFee();
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

            if (profitWithStorage > 0d) {
                grossProfit += profitWithStorage;
            } else if (profitWithStorage < 0d) {
                grossLoss += profitWithStorage;
            }
        }

        int totalTrades = ordered.size();
        double winRate = safeDivide(winCount, Math.max(1d, totalTrades));
        double lossRate = safeDivide(lossCount, Math.max(1d, totalTrades));
        double profitFactor = Math.abs(grossLoss) < 1e-9
                ? (grossProfit > 0d ? Double.POSITIVE_INFINITY : 0d)
                : grossProfit / Math.abs(grossLoss);

        String sharpe = calcEquityCurveSharpe(curvePoints);
        long avgDurationMs = durationCount <= 0 ? 0L : durationSumMs / durationCount;
        String avgDurationText = durationCount <= 0 ? "--" : formatDuration(avgDurationMs);

        double weeks = 1d;
        if (firstClose < Long.MAX_VALUE && lastClose > firstClose) {
            weeks = Math.max(1d, (lastClose - firstClose) / (7d * 24d * 60d * 60d * 1000d));
        }
        String perWeek = Math.round(totalTrades / weeks) + " 次/周";

        result.add(new AccountMetric("最近交易", formatRelativeTime(lastClose)));
        result.add(new AccountMetric("交易次数", totalTrades + " 次"));
        result.add(new AccountMetric("盈利交易", formatTradeRatioMetric(winCount, winRate)));
        result.add(new AccountMetric("亏损交易", formatTradeRatioMetric(lossCount, lossRate)));
        result.add(new AccountMetric("最好交易", hasBestTrade ? signedMoney(bestTrade) : "--"));
        result.add(new AccountMetric("最差交易", hasWorstTrade ? signedMoney(worstTrade) : "--"));
        result.add(new AccountMetric("毛利/毛损", TradeStatsTextFormatter.formatGrossPair(grossProfit, grossLoss)));
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
            byCode.put(code, byCode.getOrDefault(code, 0d) + item.getProfit() + item.getStorageFee());
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

    private List<TradeRecordItem> filterTradesBySideMode(List<TradeRecordItem> trades, TradePnlSideMode sideMode) {
        List<TradeRecordItem> result = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            return result;
        }
        for (TradeRecordItem item : trades) {
            if (item != null && matchesSideMode(item, sideMode)) {
                result.add(item);
            }
        }
        return result;
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
        return TradeStatsTextFormatter.formatStreakMetric(count, amount);
    }

    private String formatTradeRatioMetric(int count, double ratio) {
        return TradeStatsTextFormatter.formatTradeRatioMetric(count, ratio);
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
                syncRangeInputsWithDisplayedCurve(displayedCurvePoints);
                renderCurveWithIndicators(displayedCurvePoints);
                return;
            }
            manualCurveRangeEnabled = false;
        }
        displayedCurvePoints = filterCurveByRange(allCurvePoints, selectedRange);
        syncRangeInputsWithDisplayedCurve(displayedCurvePoints);
        renderCurveWithIndicators(displayedCurvePoints);
    }

    private void syncRangeInputsWithDisplayedCurve(List<CurvePoint> points) {
        if (binding == null) {
            return;
        }
        if (points == null || points.isEmpty()) {
            binding.etRangeStart.setText("");
            binding.etRangeEnd.setText("");
            return;
        }
        CurvePoint start = points.get(0);
        CurvePoint end = points.get(points.size() - 1);
        binding.etRangeStart.setText(dateOnlyFormat.format(new Date(start.getTimestamp())));
        binding.etRangeEnd.setText(dateOnlyFormat.format(new Date(end.getTimestamp())));
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
        List<CurvePoint> effectivePoints = AccountCurvePositionRatioHelper.ensureVisibleRatios(
                points,
                basePositions,
                baseTrades,
                parseLeverageNumber(connectedLeverageText)
        );
        displayedCurvePoints = effectivePoints;
        CurveAnalyticsHelper.DrawdownSegment drawdownSegment = CurveAnalyticsHelper.resolveMaxDrawdownSegment(effectivePoints);
        curveBaseBalance = resolveCurvePercentBase(effectivePoints);
        binding.equityCurveView.setBaseBalance(curveBaseBalance);
        long viewportStartTs = effectivePoints != null && !effectivePoints.isEmpty() ? effectivePoints.get(0).getTimestamp() : 0L;
        long viewportEndTs = effectivePoints != null && effectivePoints.size() > 1
                ? effectivePoints.get(effectivePoints.size() - 1).getTimestamp()
                : viewportStartTs + 1L;
        if (drawdownSegment == null) {
            binding.equityCurveView.setDrawdownHighlight(0L, 0L, 0d, 0d);
        } else {
            binding.equityCurveView.setDrawdownHighlight(
                    drawdownSegment.getPeakTimestamp(),
                    drawdownSegment.getValleyTimestamp(),
                    drawdownSegment.getPeakEquity(),
                    drawdownSegment.getValleyEquity()
            );
        }
        binding.equityCurveView.setPoints(effectivePoints);
        binding.positionRatioChartView.setViewport(viewportStartTs, viewportEndTs);
        binding.positionRatioChartView.setPoints(effectivePoints);
        displayedDrawdownPoints = CurveAnalyticsHelper.buildDrawdownSeries(effectivePoints);
        displayedDailyReturnPoints = CurveAnalyticsHelper.buildDailyReturnSeries(effectivePoints);
        binding.drawdownChartView.setViewport(viewportStartTs, viewportEndTs);
        binding.drawdownChartView.setPoints(displayedDrawdownPoints);
        binding.dailyReturnChartView.setViewport(viewportStartTs, viewportEndTs);
        binding.dailyReturnChartView.setPoints(displayedDailyReturnPoints);
        defaultCurveMeta = buildCurveMeta(effectivePoints, drawdownSegment);
        binding.tvCurveMeta.setText(isPrivacyMasked()
                ? AccountStatsPrivacyFormatter.maskValue(defaultCurveMeta, true)
                : defaultCurveMeta);
        clearSharedCurveHighlight();
        indicatorAdapter.submitList(buildCurveIndicators(effectivePoints));
    }

    // 把任一子图的时间点同步成多联图共享十字光标。
    private void applySharedCurveHighlight(long timestamp, float xRatio, boolean preferExactTimestamp) {
        if (isPrivacyMasked()) {
            clearSharedCurveHighlight();
            return;
        }
        AccountCurveHighlightHelper.HighlightSnapshot snapshot =
                AccountCurveHighlightHelper.resolveSharedHighlight(
                        displayedCurvePoints,
                        displayedDrawdownPoints,
                        displayedDailyReturnPoints,
                        timestamp,
                        xRatio,
                        preferExactTimestamp
                );
        if (snapshot == null) {
            clearSharedCurveHighlight();
            return;
        }
        CurvePoint point = snapshot.getCurvePoint();
        CurveAnalyticsHelper.DrawdownPoint drawdownPoint = snapshot.getDrawdownPoint();
        CurveAnalyticsHelper.DailyReturnPoint dailyReturnPoint = snapshot.getDailyReturnPoint();
        List<String> extraLines = new ArrayList<>();
        extraLines.add("仓位 " + formatPercentValue(point.getPositionRatio(), false));
        extraLines.add("回撤 " + formatPercentValue(drawdownPoint == null ? null : drawdownPoint.getDrawdownRate(), false));
        extraLines.add("日收益 " + formatPercentValue(dailyReturnPoint == null ? null : dailyReturnPoint.getReturnRate(), true));
        syncingCurveHighlight = true;
        binding.tvCurveMeta.setText(isPrivacyMasked()
                ? AccountStatsPrivacyFormatter.maskValue(defaultCurveMeta, true)
                : buildHighlightCurveMeta(snapshot));
        binding.equityCurveView.setTooltipExtraLines(extraLines);
        binding.equityCurveView.syncHighlightPoint(point, snapshot.getTargetTimestamp(), xRatio);
        binding.positionRatioChartView.syncHighlightTimestamp(snapshot.getTargetTimestamp(), xRatio);
        binding.drawdownChartView.syncHighlightTimestamp(snapshot.getTargetTimestamp(), xRatio);
        binding.dailyReturnChartView.syncHighlightTimestamp(snapshot.getTargetTimestamp(), xRatio);
        syncingCurveHighlight = false;
    }

    // 清除多联图共享十字光标和附带弹窗。
    private void clearSharedCurveHighlight() {
        syncingCurveHighlight = true;
        binding.equityCurveView.setTooltipExtraLines(null);
        binding.equityCurveView.setTooltipPointOverride(null);
        binding.equityCurveView.clearSyncedHighlight();
        binding.positionRatioChartView.clearSyncedHighlight();
        binding.drawdownChartView.clearSyncedHighlight();
        binding.dailyReturnChartView.clearSyncedHighlight();
        binding.tvCurveMeta.setText(isPrivacyMasked()
                ? AccountStatsPrivacyFormatter.maskValue(defaultCurveMeta, true)
                : defaultCurveMeta);
        syncingCurveHighlight = false;
    }

    // 生成长按时的区间曲线摘要，让图外信息栏也随手指位置实时变化。
    private String buildHighlightCurveMeta(AccountCurveHighlightHelper.HighlightSnapshot snapshot) {
        if (snapshot == null) {
            return defaultCurveMeta;
        }
        CurvePoint point = snapshot.getCurvePoint();
        CurveAnalyticsHelper.DrawdownPoint drawdownPoint = snapshot.getDrawdownPoint();
        CurveAnalyticsHelper.DailyReturnPoint dailyReturnPoint = snapshot.getDailyReturnPoint();
        double rangeReturn = safeDivide(point.getEquity() - curveBaseBalance, Math.max(1d, curveBaseBalance)) * 100d;
        return String.format(Locale.getDefault(),
                "时间 %s | 当前净值 $%s | 当前结余 $%s | 仓位 %s | 回撤 %s | 日收益 %s | 区间收益 %+.2f%%",
                FormatUtils.formatTime(snapshot.getTargetTimestamp()),
                FormatUtils.formatPrice(point.getEquity()),
                FormatUtils.formatPrice(point.getBalance()),
                formatPercentValue(point.getPositionRatio(), false),
                formatPercentValue(drawdownPoint == null ? null : drawdownPoint.getDrawdownRate(), false),
                formatPercentValue(dailyReturnPoint == null ? null : dailyReturnPoint.getReturnRate(), true),
                rangeReturn);
    }

    // 找到当前主图里离目标时间最近的点。
    @Nullable
    private CurvePoint findNearestCurvePoint(long timestamp) {
        if (displayedCurvePoints == null || displayedCurvePoints.isEmpty()) {
            return null;
        }
        CurvePoint bestPoint = displayedCurvePoints.get(0);
        long bestDistance = Math.abs(bestPoint.getTimestamp() - timestamp);
        for (CurvePoint point : displayedCurvePoints) {
            long distance = Math.abs(point.getTimestamp() - timestamp);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = point;
            }
        }
        return bestPoint;
    }

    // 找到离目标时间最近的回撤点。
    @Nullable
    private CurveAnalyticsHelper.DrawdownPoint findNearestDrawdownPoint(long timestamp) {
        if (displayedDrawdownPoints == null || displayedDrawdownPoints.isEmpty()) {
            return null;
        }
        CurveAnalyticsHelper.DrawdownPoint bestPoint = displayedDrawdownPoints.get(0);
        long bestDistance = Math.abs(bestPoint.getTimestamp() - timestamp);
        for (CurveAnalyticsHelper.DrawdownPoint point : displayedDrawdownPoints) {
            long distance = Math.abs(point.getTimestamp() - timestamp);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = point;
            }
        }
        return bestPoint;
    }

    // 找到离目标时间最近的日收益点。
    @Nullable
    private CurveAnalyticsHelper.DailyReturnPoint findNearestDailyReturnPoint(long timestamp) {
        if (displayedDailyReturnPoints == null || displayedDailyReturnPoints.isEmpty()) {
            return null;
        }
        CurveAnalyticsHelper.DailyReturnPoint bestPoint = displayedDailyReturnPoints.get(0);
        long bestDistance = Math.abs(bestPoint.getTimestamp() - timestamp);
        for (CurveAnalyticsHelper.DailyReturnPoint point : displayedDailyReturnPoints) {
            long distance = Math.abs(point.getTimestamp() - timestamp);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = point;
            }
        }
        return bestPoint;
    }

    // 统一格式化附图百分比，缺数据时用 --。
    private String formatPercentValue(@Nullable Double value, boolean alwaysShowSign) {
        if (value == null) {
            return "--";
        }
        if (alwaysShowSign) {
            return String.format(Locale.getDefault(), "%+.2f%%", value * 100d);
        }
        return String.format(Locale.getDefault(), "%.2f%%", value * 100d);
    }

    private double resolveCurvePercentBase(List<CurvePoint> points) {
        if (points != null && !points.isEmpty()) {
            double firstEquity = points.get(0).getEquity();
            if (firstEquity > 0d) {
                return Math.max(1e-9, firstEquity);
            }
        }
        if (allCurvePoints != null && !allCurvePoints.isEmpty()) {
            double firstEquity = allCurvePoints.get(0).getEquity();
            if (firstEquity > 0d) {
                return Math.max(1e-9, firstEquity);
            }
        }
        return Math.max(1e-9, ACCOUNT_INITIAL_BALANCE);
    }

    private String buildCurveMeta(List<CurvePoint> points,
                                  @Nullable CurveAnalyticsHelper.DrawdownSegment drawdownSegment) {
        if (points == null || points.isEmpty()) {
            return "--";
        }
        double start = points.get(0).getEquity();
        double currentEquity = points.get(points.size() - 1).getEquity();
        double currentBalance = points.get(points.size() - 1).getBalance();
        double rangeReturn = safeDivide(currentEquity - start, Math.max(1d, start)) * 100d;
        String gapText = signedMoney(currentEquity - currentBalance);
        if (drawdownSegment == null) {
            return String.format(Locale.getDefault(),
                    "区间净值 $%s | 当前净值 $%s | 当前结余 $%s | 区间收益 %+.2f%% | 浮盈差额 %s",
                    FormatUtils.formatPrice(start),
                    FormatUtils.formatPrice(currentEquity),
                    FormatUtils.formatPrice(currentBalance),
                    rangeReturn,
                    gapText);
        }
        return String.format(Locale.getDefault(),
                "区间净值 $%s | 当前净值 $%s | 当前结余 $%s | 最大回撤区间 %s -> %s | 最大回撤 %.2f%% | 区间收益 %+.2f%% | 浮盈差额 %s",
                FormatUtils.formatPrice(start),
                FormatUtils.formatPrice(currentEquity),
                FormatUtils.formatPrice(currentBalance),
                FormatUtils.formatTime(drawdownSegment.getPeakTimestamp()),
                FormatUtils.formatTime(drawdownSegment.getValleyTimestamp()),
                drawdownSegment.getDrawdownRate() * 100d,
                rangeReturn,
                gapText);
    }

    @Nullable
    private DrawdownSegment resolveMaxDrawdownSegment(List<CurvePoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        List<CurvePoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));
        CurvePoint peakPoint = sorted.get(0);
        CurvePoint maxPeakPoint = null;
        CurvePoint maxValleyPoint = null;
        double maxDrawdown = 0d;
        for (CurvePoint point : sorted) {
            if (point.getBalance() >= peakPoint.getBalance()) {
                peakPoint = point;
            }
            double drawdown = safeDivide(peakPoint.getBalance() - point.getBalance(), peakPoint.getBalance());
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
                maxPeakPoint = peakPoint;
                maxValleyPoint = point;
            }
        }
        if (maxPeakPoint == null || maxValleyPoint == null || maxDrawdown <= 0d) {
            return null;
        }
        return new DrawdownSegment(
                maxPeakPoint.getTimestamp(),
                maxValleyPoint.getTimestamp(),
                maxPeakPoint.getBalance(),
                maxValleyPoint.getBalance(),
                maxDrawdown
        );
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
        double latestEquity = Math.max(1e-9, latest.getEquity());
        long latestTs = latest.getTimestamp();

        double r1 = calcLookbackEquityReturn(sorted, latestTs, latestEquity, 1);
        double r7 = calcLookbackEquityReturn(sorted, latestTs, latestEquity, 7);
        double r30 = calcLookbackEquityReturn(sorted, latestTs, latestEquity, 30);

        double startEquity = Math.max(1e-9, sorted.get(0).getEquity());
        double cumulative = safeDivide(latestEquity - startEquity, startEquity);

        CurveAnalyticsHelper.DrawdownSegment drawdownSegment = CurveAnalyticsHelper.resolveMaxDrawdownSegment(sorted);
        String maxDrawdownText = drawdownSegment == null
                ? "--"
                : String.format(Locale.getDefault(), "%.2f%%", drawdownSegment.getDrawdownRate() * 100d);
        String sharpe = calcEquityCurveSharpe(sorted);

        result.add(new AccountMetric("近1日收益", percent(r1)));
        result.add(new AccountMetric("近7日收益", percent(r7)));
        result.add(new AccountMetric("近30日收益", percent(r30)));
        result.add(new AccountMetric("累计收益", percent(cumulative)));
        result.add(new AccountMetric("最大回撤", maxDrawdownText));
        result.add(new AccountMetric("夏普比率", sharpe));
        return result;
    }

    private double calcLookbackEquityReturn(List<CurvePoint> points, long latestTs, double latestEquity, int days) {
        long lookbackMs = days * 24L * 60L * 60L * 1000L;
        long targetTs = latestTs - lookbackMs;
        CurvePoint startPoint = findNearestPointAtOrBefore(points, targetTs);
        if (startPoint == null) {
            startPoint = points.get(0);
        }
        double startEquity = Math.max(1e-9, startPoint.getEquity());
        return safeDivide(latestEquity - startEquity, startEquity);
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
        binding.tvMonthlyReturnsHint.setVisibility(View.GONE);
        binding.tvMonthlyReturnsHint.setText("");
        if (source == null || source.size() < 2) {
            binding.tvReturnsPeriod.setText("--");
            return;
        }
        long referenceTime = resolveReturnStatsReferenceTime(source);
        String periodText = isPrivacyMasked()
                ? SensitiveDisplayMasker.MASK_TEXT
                : formatMonthLabel(referenceTime);
        binding.tvReturnsPeriod.setText(periodText);
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
                binding.tvReturnsPeriod.setText(periodText);
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
            return;
        }

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(referenceTime);
        int year = target.get(Calendar.YEAR);
        int month = target.get(Calendar.MONTH);

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
        List<YearlyReturnRow> rows = buildMonthlyReturnRows(source);
        if (rows.isEmpty()) {
            return;
        }
        rebuildMonthlyTableThreeRowsV4(binding.tableMonthlyReturns, rows);
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
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);

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
            return;
        }

        String valueHeader = returnValueMode == ReturnValueMode.RATE ? "收益率" : "收益额";
        table.addView(createAlignedReturnsRow("年份", valueHeader, true, null, null));

        double previousClose = Math.max(1e-9, sorted.get(0).getBalance());
        for (Map.Entry<Integer, PeriodBucket> entry : yearBuckets.entrySet()) {
            PeriodBucket bucket = entry.getValue();
            double yearAmount = bucket.closeEquity - previousClose;
            double yearReturn = safeDivide(yearAmount, previousClose);
            previousClose = bucket.closeEquity;

            int color = ContextCompat.getColor(this, yearReturn >= 0d ? R.color.accent_green : R.color.accent_red);
            String valueText = formatReturnValue(yearReturn, yearAmount);
            long startMs = bucket.startMs;
            long endMs = bucket.endMs;
            table.addView(createAlignedReturnsRow(
                    entry.getKey() + "年",
                    valueText,
                    false,
                    color,
                    yearReturn,
                    v -> applyCurveRangeFromTableSelection(startMs, endMs)));
        }
    }

    private void renderStageReturnsTable(List<CurvePoint> source, long referenceTime) {
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        String valueHeader = returnValueMode == ReturnValueMode.RATE ? "收益率" : "收益额";
        table.addView(createAlignedReturnsRow("阶段", valueHeader, true, null, null));

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
            if (range.size() < 2) {
                table.addView(createAlignedReturnsRow(stage.label, "--", false, null, null));
                continue;
            }

            double startEquity = range.get(0).getBalance();
            double endEquity = range.get(range.size() - 1).getBalance();
            double profit = endEquity - startEquity;
            double rate = safeDivide(profit, startEquity);
            int color = ContextCompat.getColor(this, rate >= 0d ? R.color.accent_green : R.color.accent_red);
            String valueText = formatReturnValue(rate, profit);

            table.addView(createAlignedReturnsRow(
                    stage.label,
                    valueText,
                    false,
                    color,
                    rate,
                    v -> applyCurveRangeFromTableSelection(stage.startMs, stage.endMs)));
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
                    View emptyCell = createDailyReturnsCell(
                            ghostLabel,
                            null,
                            ContextCompat.getColor(this, R.color.text_secondary),
                            null,
                            null,
                            null);
                    applyReturnsCellLayout(emptyCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                    tableRow.addView(emptyCell);
                    continue;
                }

                DayBucket bucket = dayBuckets.get(day);
                if (bucket == null) {
                    View dayCell = createDailyReturnsCell(
                            String.valueOf(day),
                            null,
                            ContextCompat.getColor(this, R.color.text_primary),
                            null,
                            null,
                            null);
                    applyReturnsCellLayout(dayCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
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
                    View dayCell = createDailyReturnsCell(
                            String.valueOf(day),
                            valueText,
                            ContextCompat.getColor(this, R.color.text_primary),
                            color,
                            v -> applyCurveRangeFromTableSelection(startMs, endMs),
                            dayReturn);
                    applyReturnsCellLayout(dayCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                    tableRow.addView(dayCell);
                }
                day++;
            }
            table.addView(tableRow);
        }
    }

    private void rebuildMonthlyTableTwoRowsV3(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();
        table.setShrinkAllColumns(false);
        table.setStretchAllColumns(false);
        table.addView(createMonthlyReturnsHeaderRow());

        for (YearlyReturnRow row : rows) {
            table.addView(createMonthlyReturnsYearRow(row));
        }
    }

    private void rebuildMonthlyTableThreeRowsV4(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();
        table.setShrinkAllColumns(false);
        table.setStretchAllColumns(false);
        for (YearlyReturnRow row : rows) {
            table.addView(createMonthlyGroupedBlock(row));
        }
    }

    private LinearLayout createMonthlyGroupedBlock(YearlyReturnRow rowData) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setGravity(Gravity.TOP);
        TableLayout.LayoutParams blockParams = new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT);
        blockParams.topMargin = dpToPx(RETURNS_CELL_MARGIN_DP);
        block.setLayoutParams(blockParams);

        TextView yearCell = createMonthlyYearSummaryCell(rowData);
        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(
                0,
                dpToPx(RETURNS_MONTH_GROUP_HEIGHT_DP * 3 + RETURNS_CELL_MARGIN_DP * 2),
                0.94f);
        yearParams.rightMargin = dpToPx(RETURNS_CELL_MARGIN_DP);
        yearCell.setLayoutParams(yearParams);
        block.addView(yearCell);

        LinearLayout monthColumn = new LinearLayout(this);
        monthColumn.setOrientation(LinearLayout.VERTICAL);
        monthColumn.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                4f));
        monthColumn.addView(createMonthlyGroupedLine(rowData, 1, 4));
        monthColumn.addView(createMonthlyGroupedLine(rowData, 5, 8));
        monthColumn.addView(createMonthlyGroupedLine(rowData, 9, 12));
        block.addView(monthColumn);
        return block;
    }

    private LinearLayout createMonthlyGroupedLine(YearlyReturnRow rowData,
                                                  int startMonth,
                                                  int endMonth) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int month = startMonth; month <= endMonth; month++) {
            TextView cell = createMonthlyGroupedCell(month, rowData.monthly.get(month));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    dpToPx(RETURNS_MONTH_GROUP_HEIGHT_DP),
                    1f);
            if (month < endMonth) {
                params.rightMargin = dpToPx(RETURNS_CELL_MARGIN_DP);
            }
            if (endMonth < 12) {
                params.bottomMargin = dpToPx(RETURNS_CELL_MARGIN_DP);
            }
            cell.setLayoutParams(params);
            row.addView(cell);
        }
        return row;
    }

    private TextView createMonthlyYearSummaryCell(YearlyReturnRow rowData) {
        View.OnClickListener yearClick = null;
        if (rowData.startMs > 0L && rowData.endMs > rowData.startMs) {
            long startMs = rowData.startMs;
            long endMs = rowData.endMs;
            yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
        }
        int valueColor = ContextCompat.getColor(this, rowData.yearReturnRate >= 0d ? R.color.accent_green : R.color.accent_red);
        TextView cell = createReturnsCell(
                buildLabelValueText(rowData.year + "年",
                        formatReturnValue(rowData.yearReturnRate, rowData.yearReturnAmount),
                        valueColor),
                0,
                false,
                null,
                yearClick);
        cell.setGravity(Gravity.CENTER);
        cell.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.8f);
        cell.setPadding(dpToPx(4), dpToPx(3), dpToPx(4), dpToPx(3));
        return cell;
    }

    private TextView createMonthlyGroupedCell(int month, @Nullable MonthReturnInfo info) {
        Integer valueColor = info != null && info.hasData
                ? ContextCompat.getColor(this, info.returnRate >= 0d ? R.color.accent_green : R.color.accent_red)
                : null;
        TextView cell = createReturnsCell(
                buildLabelValueText(month + "月", formatMonthlyGroupedValue(info), valueColor),
                0,
                false,
                null,
                resolveMonthlyHeatCellClickListener(info));
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.2f);
        cell.setPadding(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
        cell.setBackground(buildReturnsCellBackground(resolveMonthlyHeatCellRate(info), false));
        return cell;
    }

    private String formatMonthlyGroupedValue(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData) {
            return "--";
        }
        if (isPrivacyMasked()) {
            return SensitiveDisplayMasker.MASK_TEXT;
        }
        return formatReturnValue(info.returnRate, info.returnAmount);
    }

    private TableRow createMonthlyReturnsHeaderRow() {
        TableRow row = new TableRow(this);
        row.addView(createMonthlyHeatCell("年份", 1.18f, true, null, null, null));
        for (int month = 1; month <= 12; month++) {
            row.addView(createMonthlyHeatCell(month + "月", 1f, true, null, null, null));
        }
        return row;
    }

    private TableRow createMonthlyReturnsYearRow(YearlyReturnRow rowData) {
        TableRow row = new TableRow(this);
        View.OnClickListener yearClick = null;
        if (rowData.startMs > 0L && rowData.endMs > rowData.startMs) {
            long startMs = rowData.startMs;
            long endMs = rowData.endMs;
            yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
        }
        row.addView(createMonthlyHeatCell(rowData.year + "年", 1.18f, false, null, null, yearClick));
        for (int month = 1; month <= 12; month++) {
            row.addView(createMonthlyHeatCell(
                    formatMonthlyHeatCellValue(rowData.monthly.get(month)),
                    1f,
                    false,
                    resolveMonthlyHeatCellTextColor(rowData.monthly.get(month)),
                    resolveMonthlyHeatCellRate(rowData.monthly.get(month)),
                    resolveMonthlyHeatCellClickListener(rowData.monthly.get(month))));
        }
        return row;
    }

    private String formatMonthlyHeatCellValue(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData) {
            return "--";
        }
        if (isPrivacyMasked()) {
            return SensitiveDisplayMasker.MASK_TEXT;
        }
        if (returnValueMode == ReturnValueMode.AMOUNT) {
            return formatCompactSignedAmount(info.returnAmount);
        }
        return String.format(Locale.getDefault(), "%+.2f", info.returnRate * 100d);
    }

    private String formatCompactSignedAmount(double amount) {
        double absAmount = Math.abs(amount);
        if (absAmount >= 10_000d) {
            return String.format(Locale.getDefault(), "%+.1fk", amount / 1_000d);
        }
        return String.format(Locale.getDefault(), "%+,.0f", amount);
    }

    @Nullable
    private Integer resolveMonthlyHeatCellTextColor(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData) {
            return null;
        }
        return ContextCompat.getColor(this, info.returnRate >= 0d ? R.color.accent_green : R.color.accent_red);
    }

    @Nullable
    private Double resolveMonthlyHeatCellRate(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData) {
            return null;
        }
        return info.returnRate;
    }

    @Nullable
    private View.OnClickListener resolveMonthlyHeatCellClickListener(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData || info.startMs <= 0L || info.endMs <= info.startMs) {
            return null;
        }
        long startMs = info.startMs;
        long endMs = info.endMs;
        return v -> applyCurveRangeFromTableSelection(startMs, endMs);
    }

    private TextView createMonthlyHeatCell(CharSequence text,
                                           float weight,
                                           boolean header,
                                           @Nullable Integer textColor,
                                           @Nullable Double heatRate,
                                           @Nullable View.OnClickListener clickListener) {
        TextView cell = createReturnsCell(makeNonBreakingText(text == null ? "" : text.toString()), 0, header, textColor, clickListener);
        applyReturnsCellLayout(cell, 0, weight, header ? RETURNS_HEADER_HEIGHT_DP : RETURNS_BODY_HEIGHT_DP,
                0, RETURNS_CELL_MARGIN_DP, 0, RETURNS_CELL_MARGIN_DP);
        cell.setGravity(Gravity.CENTER);
        cell.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, header ? 8.6f : 7.2f);
        cell.setPadding(dpToPx(header ? 2 : 1), 0, dpToPx(header ? 2 : 1), 0);
        cell.setSingleLine(true);
        cell.setMaxLines(1);
        cell.setMinLines(1);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setBackground(buildReturnsCellBackground(heatRate, header));
        return cell;
    }

    private TableRow createAlignedReturnsRow(CharSequence label,
                                             CharSequence value,
                                             boolean header,
                                             @Nullable Integer valueColor,
                                             @Nullable Double heatRate) {
        return createAlignedReturnsRow(label, value, header, valueColor, heatRate, null);
    }

    private TableRow createAlignedReturnsRow(CharSequence label,
                                             CharSequence value,
                                             boolean header,
                                             @Nullable Integer valueColor,
                                             @Nullable Double heatRate,
                                             @Nullable View.OnClickListener clickListener) {
        TableRow row = new TableRow(this);
        row.addView(createAlignedReturnsCell(label, header, false, 1f, null, heatRate, clickListener));
        row.addView(createAlignedReturnsCell(value, header, true, 1f, valueColor, heatRate, clickListener));
        return row;
    }

    private TextView createAlignedReturnsCell(CharSequence text,
                                              boolean header,
                                              boolean alignEnd,
                                              float weight,
                                              @Nullable Integer textColor,
                                              @Nullable Double heatRate,
                                              @Nullable View.OnClickListener clickListener) {
        TextView cell = new TextView(this);
        applyReturnsCellLayout(cell, 0, weight, header ? RETURNS_HEADER_HEIGHT_DP : RETURNS_STAGE_HEIGHT_DP,
                0, RETURNS_CELL_MARGIN_DP, 0, RETURNS_CELL_MARGIN_DP);
        int horizontalPadding = dpToPx(header ? 8 : 10);
        cell.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        cell.setGravity((alignEnd ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        cell.setTextAlignment(alignEnd ? View.TEXT_ALIGNMENT_VIEW_END : View.TEXT_ALIGNMENT_VIEW_START);
        cell.setIncludeFontPadding(false);
        cell.setSingleLine(true);
        cell.setMaxLines(1);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        String displayText = text == null ? "--" : text.toString();
        boolean maskedValue = !header && alignEnd && isPrivacyMasked() && !"--".equals(displayText.trim());
        if (maskedValue) {
            displayText = SensitiveDisplayMasker.MASK_TEXT;
        }
        cell.setText(makeNonBreakingText(displayText));
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, header ? 10f : 9.6f);
        int neutralColor = ContextCompat.getColor(this, header ? R.color.text_primary : R.color.text_secondary);
        cell.setTextColor(AccountStatsPrivacyFormatter.resolveValueColor(textColor, neutralColor, maskedValue));
        cell.setBackground(buildReturnsCellBackground(heatRate, header));
        if (clickListener != null) {
            cell.setOnClickListener(clickListener);
            cell.setClickable(true);
        }
        return cell;
    }

    private void applyReturnsCellLayout(View cell,
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
        boolean maskedValue = isPrivacyMasked() && !"--".equals(safeValue);
        if (maskedValue) {
            safeValue = SensitiveDisplayMasker.MASK_TEXT;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (!safeLabel.isEmpty()) {
            builder.append(safeLabel);
            builder.append('\n');
        }
        int valueStart = builder.length();
        builder.append(makeNonBreakingText(safeValue));
        if (valueColor != null && !maskedValue) {
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
        int baseHeightDp = header ? RETURNS_HEADER_HEIGHT_DP : RETURNS_BODY_HEIGHT_DP;
        TableRow.LayoutParams params = new TableRow.LayoutParams(dpToPx(minWidthDp), dpToPx(baseHeightDp));
        int marginPx = dpToPx(RETURNS_CELL_MARGIN_DP);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);
        cell.setLayoutParams(params);
        int horizontalPadding = dpToPx(header ? 6 : 2);
        int verticalPadding = dpToPx(header ? 4 : 6);
        cell.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        cell.setIncludeFontPadding(false);
        cell.setText(text);
        cell.setTextSize(header ? 11f : 8.6f);
        String plain = text == null ? "" : text.toString();
        cell.setEllipsize(null);
        if (plain.contains("\n")) {
            cell.setLines(2);
            cell.setMaxLines(2);
            cell.setLineSpacing(0f, 1.02f);
        } else {
            cell.setLines(1);
            cell.setMaxLines(1);
        }
        cell.setBackground(buildReturnsCellBackground(null, header));
        int defaultColor = ContextCompat.getColor(this, header ? R.color.text_primary : R.color.text_secondary);
        cell.setTextColor(textColor != null ? textColor : defaultColor);
        if (clickListener != null) {
            cell.setOnClickListener(clickListener);
            cell.setClickable(true);
        }
        return cell;
    }

    private View createDailyReturnsCell(String label,
                                        @Nullable String value,
                                        int labelColor,
                                        @Nullable Integer valueColor,
                                        @Nullable View.OnClickListener clickListener,
                                        @Nullable Double heatRate) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        container.setBackground(buildReturnsCellBackground(heatRate, false));
        container.setBaselineAligned(false);

        TextView labelView = new TextView(this);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        labelView.setIncludeFontPadding(false);
        labelView.setText(trim(label));
        labelView.setTextColor(labelColor);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        container.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(this);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        valueView.setIncludeFontPadding(false);
        String displayValue = value == null ? "00.0%" : value;
        boolean masked = isPrivacyMasked() && value != null;
        if (masked) {
            displayValue = SensitiveDisplayMasker.MASK_TEXT;
        }
        valueView.setText(makeNonBreakingText(displayValue));
        valueView.setTextColor(masked
                ? ContextCompat.getColor(this, R.color.text_secondary)
                : (valueColor != null ? valueColor : ContextCompat.getColor(this, R.color.text_secondary)));
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.0f);
        valueView.setSingleLine(true);
        valueView.setMaxLines(1);
        valueView.setMinLines(1);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dpToPx(2);
        container.addView(valueView, valueParams);

        if (value == null) {
            valueView.setAlpha(0f);
        }
        if (clickListener != null) {
            container.setOnClickListener(clickListener);
            container.setClickable(true);
        }
        return container;
    }

    private GradientDrawable buildReturnsCellBackground(@Nullable Double rate, boolean header) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(0f);
        drawable.setStroke(dpToPx(1), palette.stroke);
        int fill = header
                ? blendColor(palette.surfaceEnd, palette.textPrimary, 0.10f)
                : palette.surfaceEnd;
        if (!header && rate != null && !isPrivacyMasked()) {
            fill = AccountReturnsHeatStyleHelper.resolveFillColor(
                    palette.surfaceEnd,
                    palette.rise,
                    palette.fall,
                    rate);
        }
        drawable.setColor(fill);
        return drawable;
    }

    private int blendColor(int startColor, int endColor, float ratio) {
        float safeRatio = Math.max(0f, Math.min(1f, ratio));
        int startA = Color.alpha(startColor);
        int startR = Color.red(startColor);
        int startG = Color.green(startColor);
        int startB = Color.blue(startColor);
        int endA = Color.alpha(endColor);
        int endR = Color.red(endColor);
        int endG = Color.green(endColor);
        int endB = Color.blue(endColor);
        return Color.argb(
                Math.round(startA + (endA - startA) * safeRatio),
                Math.round(startR + (endR - startR) * safeRatio),
                Math.round(startG + (endG - startG) * safeRatio),
                Math.round(startB + (endB - startB) * safeRatio));
    }

    private CharSequence makeNonBreakingText(String value) {
        String safe = trim(value);
        if (safe.isEmpty()) {
            return "--";
        }
        return safe.replace(" ", "\u00A0");
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
        boolean masked = isPrivacyMasked();
        List<PositionItem> list = buildPositionListWithNaturalDayPnl(basePositions, baseTrades);
        list.sort(Comparator.comparing(PositionItem::getProductName));

        positionAggregateAdapter.setMasked(masked);
        positionAdapter.setMasked(masked);
        pendingOrderAdapter.setMasked(masked);
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
            totalPnl += item.getTotalPnL() + item.getStorageFee();
        }
        double totalMarketValue = 0d;
        for (PositionItem item : list) {
            totalMarketValue += Math.max(0d, Math.abs(item.getMarketValue()));
        }
        double ratio = safeDivide(totalPnl, Math.max(1d, totalMarketValue));
        overviewAdapter.submitList(buildOverviewMetrics(latestOverviewMetrics, list));
        binding.tvPositionPnlSummary.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        if (masked) {
            binding.tvPositionPnlSummary.setText("全周期总计盈亏（持仓）: **** | 持仓收益率: ****");
        } else {
            binding.tvPositionPnlSummary.setText(buildPositionPnlSummary(totalPnl, ratio));
        }
    }

    private List<PositionItem> buildPositionListWithNaturalDayPnl(List<PositionItem> source,
                                                                   List<TradeRecordItem> trades) {
        List<PositionItem> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        List<PositionItem> normalizedSource = deduplicatePositionItems(source);
        long start = startOfToday();
        long end = endOfToday();
        Map<String, Double> todayClosedByKey = new HashMap<>();
        if (trades != null) {
            for (TradeRecordItem trade : trades) {
                if (trade == null) {
                    continue;
                }
                long closeTime = resolveCloseTime(trade);
                if (closeTime < start || closeTime > end) {
                    continue;
                }
                String key = buildPositionSideKey(trade.getCode(), trade.getProductName(), trade.getSide());
                double sum = todayClosedByKey.getOrDefault(key, 0d);
                todayClosedByKey.put(key, sum + trade.getProfit() + trade.getStorageFee());
            }
        }

        Map<String, Double> totalQtyByKey = new HashMap<>();
        for (PositionItem item : normalizedSource) {
            if (item == null) {
                continue;
            }
            String key = buildPositionSideKey(item.getCode(), item.getProductName(), item.getSide());
            double qty = Math.max(0d, Math.abs(item.getQuantity()));
            totalQtyByKey.put(key, totalQtyByKey.getOrDefault(key, 0d) + qty);
        }

        for (PositionItem item : normalizedSource) {
            if (item == null) {
                continue;
            }
            String key = buildPositionSideKey(item.getCode(), item.getProductName(), item.getSide());
            double todayClosed = todayClosedByKey.getOrDefault(key, 0d);
            double totalQty = totalQtyByKey.getOrDefault(key, 0d);
            double qty = Math.max(0d, Math.abs(item.getQuantity()));
            double shareClosed = totalQty > 1e-9 ? todayClosed * (qty / totalQty) : todayClosed;
            double naturalDayPnL = item.getDayPnL() + shareClosed;

            result.add(new PositionItem(
                    item.getProductName(),
                    item.getCode(),
                    item.getSide(),
                    item.getPositionTicket(),
                    item.getOrderId(),
                    item.getQuantity(),
                    item.getSellableQuantity(),
                    item.getCostPrice(),
                    item.getLatestPrice(),
                    item.getMarketValue(),
                    item.getPositionRatio(),
                    naturalDayPnL,
                    item.getTotalPnL(),
                    item.getReturnRate(),
                    item.getPendingLots(),
                    item.getPendingCount(),
                    item.getPendingPrice(),
                    item.getTakeProfit(),
                    item.getStopLoss(),
                    item.getStorageFee()
            ));
        }
        return result;
    }

    private List<PositionItem> deduplicatePositionItems(List<PositionItem> source) {
        LinkedHashMap<String, PositionItem> unique = new LinkedHashMap<>();
        for (PositionItem item : source) {
            if (item == null) {
                continue;
            }
            unique.put(buildPositionUniqueKey(item), item);
        }
        return new ArrayList<>(unique.values());
    }

    private String buildPositionUniqueKey(PositionItem item) {
        if (item.getPositionTicket() > 0L) {
            return "position|" + item.getPositionTicket();
        }
        if (item.getOrderId() > 0L) {
            return "order|" + item.getOrderId();
        }
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long cost = Math.round(item.getCostPrice() * 10000d);
        long latest = Math.round(item.getLatestPrice() * 10000d);
        long total = Math.round(item.getTotalPnL() * 100d);
        long day = Math.round(item.getDayPnL() * 100d);
        long storage = Math.round(item.getStorageFee() * 100d);
        return trim(item.getCode()).toUpperCase(Locale.ROOT)
                + "|" + normalizeSide(trim(item.getSide())).toUpperCase(Locale.ROOT)
                + "|" + qty + "|" + cost + "|" + latest
                + "|" + total + "|" + day + "|" + storage;
    }

    private String buildPositionSideKey(String code, String productName, String side) {
        String symbol = symbolForLog(code, productName);
        String normalizedSide = sideForLog(side);
        return symbol + "|" + normalizedSide;
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
            double quantity = Math.max(0d, Math.abs(item.getQuantity()));
            aggregate.totalQuantity += quantity;
            aggregate.weightedCostAmount += quantity * item.getCostPrice();
            aggregate.weightedQuantity += quantity;
            aggregate.totalPnl += item.getTotalPnL() + item.getStorageFee();
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
                "全周期总计盈亏（持仓）: %s | 持仓收益率: %s",
                pnlText,
                ratioText);
        SpannableStringBuilder spannable = new SpannableStringBuilder(summary);

        int pnlColor = ContextCompat.getColor(this, totalPnl >= 0d ? R.color.accent_green : R.color.accent_red);
        int ratioColor = ContextCompat.getColor(this, ratio >= 0d ? R.color.accent_green : R.color.accent_red);
        int pnlStart = summary.indexOf(pnlText);
        if (pnlStart >= 0) {
            spannable.setSpan(new ForegroundColorSpan(pnlColor),
                    pnlStart, pnlStart + pnlText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new AbsoluteSizeSpan(18, true),
                    pnlStart, pnlStart + pnlText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int ratioStart = summary.lastIndexOf(ratioText);
        if (ratioStart >= 0) {
            spannable.setSpan(new ForegroundColorSpan(ratioColor),
                    ratioStart, ratioStart + ratioText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            spannable.setSpan(new AbsoluteSizeSpan(16, true),
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

        int nearZeroCount = 0;
        for (TradeRecordItem item : source) {
            if (item != null && Math.abs(item.getProfit()) < TRADE_PNL_ZERO_THRESHOLD) {
                nearZeroCount++;
            }
        }
        // If upstream has already returned lifecycle-level records, skip aggressive regrouping.
        if (nearZeroCount == 0) {
            for (TradeRecordItem item : source) {
                if (item != null) {
                    merged.add(item);
                }
            }
            merged.sort((a, b) -> Long.compare(resolveCloseTime(b), resolveCloseTime(a)));
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
                if (Math.abs(item.getProfit()) >= TRADE_PNL_ZERO_THRESHOLD) {
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
            boolean likelyNoise = Math.abs(latest.getProfit()) < TRADE_PNL_ZERO_THRESHOLD
                    && closeTime <= openTime + 1_000L;
            if (!likelyNoise) {
                merged.add(latest);
            }
        }

        merged.sort((a, b) -> Long.compare(resolveCloseTime(b), resolveCloseTime(a)));
        return merged;
    }

    private String buildTradeDedupeKey(TradeRecordItem item) {
        if (item.getDealTicket() > 0L) {
            return "deal|" + item.getDealTicket();
        }
        if (item.getOrderId() > 0L || item.getPositionId() > 0L) {
            return "trade|" + item.getOrderId() + "|" + item.getPositionId()
                    + "|" + item.getEntryType()
                    + "|" + resolveOpenTime(item)
                    + "|" + resolveCloseTime(item)
                    + "|" + Math.round(Math.abs(item.getQuantity()) * 10_000d);
        }
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
        long closeBucket = resolveCloseTime(item) / 60_000L;
        long quantityKey = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long openPriceKey = Math.round(Math.abs(item.getOpenPrice()) * 100d);
        long closePriceKey = Math.round(Math.abs(item.getClosePrice()) * 100d);
        String side = normalizeSide(trim(item.getSide())).toLowerCase(Locale.ROOT);
        return code + "|" + side + "|" + openBucket + "|" + closeBucket + "|"
                + quantityKey + "|" + openPriceKey + "|" + closePriceKey;
    }

    private List<TradeRecordItem> pruneZeroProfitTrades(List<TradeRecordItem> source) {
        List<TradeRecordItem> result = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return result;
        }
        for (TradeRecordItem item : source) {
            if (Math.abs(item.getProfit()) >= TRADE_PNL_ZERO_THRESHOLD) {
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
        double mergedOpenPrice = closeRecord.getOpenPrice() > 0d ? closeRecord.getOpenPrice() : closeRecord.getPrice();
        double mergedClosePrice = closeRecord.getClosePrice() > 0d ? closeRecord.getClosePrice() : closeRecord.getPrice();
        long minOpenTime = Long.MAX_VALUE;
        long maxCloseTime = 0L;

        for (TradeRecordItem item : group) {
            feeSum += item.getFee();
            storageFeeSum += item.getStorageFee();
            maxQuantity = Math.max(maxQuantity, item.getQuantity());
            maxAmount = Math.max(maxAmount, item.getAmount());
            long openTime = resolveOpenTime(item);
            long closeTime = resolveCloseTime(item);
            double openPrice = item.getOpenPrice() > 0d ? item.getOpenPrice() : item.getPrice();
            double closePrice = item.getClosePrice() > 0d ? item.getClosePrice() : item.getPrice();
            if (openTime > 0L) {
                if (openTime < minOpenTime) {
                    minOpenTime = openTime;
                    mergedOpenPrice = openPrice;
                }
            }
            if (closeTime > 0L) {
                if (closeTime >= maxCloseTime) {
                    maxCloseTime = closeTime;
                    mergedClosePrice = closePrice;
                }
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
                storageFeeSum,
                mergedOpenPrice,
                mergedClosePrice
        );
    }

    private String resolveMergedSide(TradeRecordItem closeRecord, List<TradeRecordItem> group) {
        if (group != null) {
            for (TradeRecordItem item : group) {
                if (Math.abs(item.getProfit()) < TRADE_PNL_ZERO_THRESHOLD) {
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
        refreshTrades(false);
    }

    private void refreshTrades(boolean scrollToTop) {
        refreshTrades(scrollToTop, false);
    }

    private void refreshTrades(boolean scrollToTop, boolean collapseExpanded) {
        if (collapseExpanded && tradeAdapter != null) {
            tradeAdapter.collapseAllExpandedRows();
        }
        List<TradeRecordItem> filtered = new ArrayList<>();
        String product = (String) binding.spinnerTradeProduct.getSelectedItem();
        String side = (String) binding.spinnerTradeSide.getSelectedItem();
        String sort = (String) binding.spinnerTradeSort.getSelectedItem();
        if (product == null || product.trim().isEmpty()) {
            product = FILTER_PRODUCT;
        }
        if (side == null || side.trim().isEmpty()) {
            side = FILTER_SIDE;
        }
        if (sort == null || sort.trim().isEmpty()) {
            sort = FILTER_SORT;
        }
        selectedTradeProductFilter = product;
        selectedTradeSideFilter = side;
        selectedTradeSortFilter = sort;
        updateTradeFilterDisplayTexts(product, side, sort);
        String normalizedSort = FILTER_SORT.equals(sort)
                ? normalizeSortValue(lastExplicitTradeSortMode)
                : normalizeSortValue(sort);

        for (TradeRecordItem item : baseTrades) {
            if (!FILTER_PRODUCT.equals(product) && !item.getCode().equalsIgnoreCase(product)) {
                continue;
            }
            if (!FILTER_SIDE.equals(side) && !item.getSide().equalsIgnoreCase(normalizeSide(side))) {
                continue;
            }
            filtered.add(item);
        }
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
        binding.recyclerTrades.post(this::updateTradeScrollHandle);
        if (scrollToTop) {
            scrollTradesToTop();
        }
        updateTradePnlSummary(filtered, product, side, FILTER_DATE);
    }

    private void scrollTradesToTop() {
        RecyclerView.LayoutManager layoutManager = binding.recyclerTrades.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            binding.recyclerTrades.scrollToPosition(0);
            return;
        }
        binding.recyclerTrades.post(() ->
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(0, 0));
    }
    private void configureTradeScrollHandle() {
        binding.recyclerTrades.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateTradeScrollHandle();
            }
        });
        binding.viewTradeScrollBar.setOnThumbDragListener(new TradeScrollBarView.OnThumbDragListener() {
            @Override
            public void onDragFractionChanged(float fraction) {
                int range = binding.recyclerTrades.computeVerticalScrollRange();
                int extent = binding.recyclerTrades.computeVerticalScrollExtent();
                int currentOffset = binding.recyclerTrades.computeVerticalScrollOffset();
                int scrollable = Math.max(0, range - extent);
                if (scrollable <= 0) {
                    return;
                }
                int targetOffset = Math.round(fraction * scrollable);
                binding.recyclerTrades.scrollBy(0, targetOffset - currentOffset);
            }

            @Override
            public void onDragStateChanged(boolean dragging) {
                draggingTradeScrollBar = dragging;
                if (!dragging) {
                    updateTradeScrollHandle();
                }
            }
        });
        binding.recyclerTrades.post(this::updateTradeScrollHandle);
    }

    private void updateTradeScrollHandle() {
        int range = binding.recyclerTrades.computeVerticalScrollRange();
        int extent = binding.recyclerTrades.computeVerticalScrollExtent();
        int offset = binding.recyclerTrades.computeVerticalScrollOffset();
        int scrollable = Math.max(0, range - extent);
        if (scrollable <= 0) {
            binding.viewTradeScrollBar.setVisibility(View.INVISIBLE);
            return;
        }
        binding.viewTradeScrollBar.setVisibility(View.VISIBLE);
        if (!draggingTradeScrollBar) {
            binding.viewTradeScrollBar.setScrollMetrics(offset, extent, range);
        }
    }


    private List<TradeRecordItem> collapseZeroProfitForDisplay(List<TradeRecordItem> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(source);
    }

    private void updateTradePnlSummary(List<TradeRecordItem> trades,
                                       String productFilter,
                                       String sideFilter,
                                       String dateFilter) {
        boolean masked = isPrivacyMasked();
        double total = 0d;
        double storageTotal = 0d;
        int tradeCount = trades == null ? 0 : trades.size();
        if (trades != null) {
            for (TradeRecordItem item : trades) {
                total += item.getProfit();
                storageTotal += item.getStorageFee();
            }
        }
        boolean mismatch = Math.abs(total - latestCumulativePnl) >= TRADE_PNL_ZERO_THRESHOLD;
        if (isDefaultTradeFilters(productFilter, sideFilter, dateFilter)
                && logManager != null
                && mismatch
                && !Boolean.TRUE.equals(lastTradePnlMismatchState)) {
            logManager.warn("Trade pnl summary mismatch: tradeSum="
                    + FormatUtils.formatPrice(total)
                    + ", cumulative=" + FormatUtils.formatPrice(latestCumulativePnl)
                    + ", rawTrades=" + baseTrades.size());
        }
        lastTradePnlMismatchState = mismatch;
        String pnlText = signedMoney(total);
        String storageText = signedMoney(storageTotal);
        double balanceTotal = total + storageTotal;
        String balanceText = signedMoney(balanceTotal);
        if (masked) {
            binding.tvTradeSummaryCountValue.setText(SensitiveDisplayMasker.MASK_TEXT);
            binding.tvTradeSummaryBalanceValue.setText(SensitiveDisplayMasker.MASK_TEXT);
            binding.tvTradeSummaryPnlValue.setText(SensitiveDisplayMasker.MASK_TEXT);
            binding.tvTradeSummaryStorageValue.setText(SensitiveDisplayMasker.MASK_TEXT);
            int defaultColor = ContextCompat.getColor(this, R.color.text_primary);
            binding.tvTradeSummaryCountValue.setTextColor(defaultColor);
            binding.tvTradeSummaryBalanceValue.setTextColor(defaultColor);
            binding.tvTradeSummaryPnlValue.setTextColor(defaultColor);
            binding.tvTradeSummaryStorageValue.setTextColor(defaultColor);
            return;
        }
        binding.tvTradeSummaryCountValue.setText(tradeCount + "次");
        binding.tvTradeSummaryBalanceValue.setText(storageText);
        binding.tvTradeSummaryPnlValue.setText(pnlText);
        binding.tvTradeSummaryStorageValue.setText(balanceText);
        binding.tvTradeSummaryCountValue.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        binding.tvTradeSummaryBalanceValue.setTextColor(resolveSignedValueColor(storageTotal));
        binding.tvTradeSummaryPnlValue.setTextColor(resolveSignedValueColor(total));
        binding.tvTradeSummaryStorageValue.setTextColor(resolveSignedValueColor(balanceTotal));
    }

    // 统一给盈亏数字着色，0 值保持主文字色。
    private int resolveSignedValueColor(double value) {
        if (value > 0d) {
            return ContextCompat.getColor(this, R.color.accent_green);
        }
        if (value < 0d) {
            return ContextCompat.getColor(this, R.color.accent_red);
        }
        return ContextCompat.getColor(this, R.color.text_primary);
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
        updateTradeFilterDisplayTexts();
    }

    private void applyManualCurveRange() {
        hideManualDatePickerPanel();
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
        return FormatUtils.formatSignedMoney(value);
    }

    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        updateBottomTabs(false, false, true, false);
        configureToggleButtonsV2();
        flattenCardSections(binding.scrollAccountStats, palette);
        binding.equityCurveView.refreshPalette();
        binding.positionRatioChartView.refreshPalette();
        binding.drawdownChartView.refreshPalette();
        binding.dailyReturnChartView.refreshPalette();
        binding.tradeDistributionScatterView.refreshPalette();
        binding.holdingDurationDistributionView.refreshPalette();
        if (binding.ivAccountPrivacyToggle != null) {
            binding.ivAccountPrivacyToggle.setImageTintList(ColorStateList.valueOf(palette.textSecondary));
        }
        updateLoginSuccessBannerStyle();
        setConnectionStatus(gatewayConnected);
        applyPrivacyMaskState();
    }

    // 登录真正连上网关后，在界面中央显示一条不会拦截点击的短提示。
    private void showLoginSuccessBanner() {
        if (binding == null || binding.tvLoginSuccessBanner == null) {
            return;
        }
        updateLoginSuccessBannerStyle();
        TextView banner = binding.tvLoginSuccessBanner;
        banner.removeCallbacks(hideLoginSuccessBannerRunnable);
        banner.animate().cancel();
        banner.setVisibility(View.VISIBLE);
        banner.setAlpha(0f);
        banner.setScaleX(0.92f);
        banner.setScaleY(0.92f);
        banner.setTranslationY(dpToPx(8));
        banner.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180L)
                .start();
        banner.postDelayed(hideLoginSuccessBannerRunnable, 900L);
    }

    // 统一刷新登录成功提示的颜色，保证主题切换时样式同步。
    private void updateLoginSuccessBannerStyle() {
        if (binding == null || binding.tvLoginSuccessBanner == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        binding.tvLoginSuccessBanner.setText(R.string.account_login_success_banner);
        binding.tvLoginSuccessBanner.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
        binding.tvLoginSuccessBanner.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    // 让成功提示快速淡出，避免遮挡中间操作区。
    private void hideLoginSuccessBannerNow() {
        if (binding == null || binding.tvLoginSuccessBanner == null) {
            return;
        }
        TextView banner = binding.tvLoginSuccessBanner;
        banner.removeCallbacks(hideLoginSuccessBannerRunnable);
        if (banner.getVisibility() != View.VISIBLE) {
            banner.setAlpha(0f);
            banner.setTranslationY(dpToPx(8));
            banner.setVisibility(View.GONE);
            return;
        }
        banner.animate().cancel();
        banner.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .translationY(-dpToPx(4))
                .setDuration(160L)
                .withEndAction(() -> {
                    if (binding == null || binding.tvLoginSuccessBanner == null) {
                        return;
                    }
                    binding.tvLoginSuccessBanner.setVisibility(View.GONE);
                    binding.tvLoginSuccessBanner.setAlpha(0f);
                    binding.tvLoginSuccessBanner.setScaleX(1f);
                    binding.tvLoginSuccessBanner.setScaleY(1f);
                    binding.tvLoginSuccessBanner.setTranslationY(dpToPx(8));
                })
                .start();
    }

    private void flattenCardSections(View view, UiPaletteManager.Palette palette) {
        if (view instanceof MaterialCardView) {
            MaterialCardView cardView = (MaterialCardView) view;
            cardView.setCardElevation(0f);
            cardView.setRadius(0f);
            cardView.setStrokeWidth(0);
            cardView.setCardBackgroundColor(palette.surfaceEnd);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                flattenCardSections(group.getChildAt(i), palette);
            }
        }
    }

    private String calcEquityCurveSharpe(List<CurvePoint> points) {
        List<CurveAnalyticsHelper.DailyReturnPoint> dailyReturns = CurveAnalyticsHelper.buildDailyReturnSeries(points);
        if (dailyReturns.size() < 2) {
            return "--";
        }
        List<Double> returns = new ArrayList<>();
        for (CurveAnalyticsHelper.DailyReturnPoint point : dailyReturns) {
            returns.add(point.getReturnRate());
        }
        double mean = returns.stream().mapToDouble(v -> v).average().orElse(0d);
        double std = calcStd(returns);
        if (std <= 1e-12) {
            return "0.00";
        }
        double sharpe = mean / std * Math.sqrt(365d);
        return String.format(Locale.getDefault(), "%.2f", sharpe);
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

    private void openMarketChart() {
        Intent intent = new Intent(this, MarketChartActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, MonitorService.class);
        intent.setAction(action);
        ContextCompat.startForegroundService(this, intent);
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

    private static class DrawdownSegment {
        private final long peakTimestamp;
        private final long valleyTimestamp;
        private final double peakBalance;
        private final double valleyBalance;
        private final double drawdownRate;

        private DrawdownSegment(long peakTimestamp,
                                long valleyTimestamp,
                                double peakBalance,
                                double valleyBalance,
                                double drawdownRate) {
            this.peakTimestamp = peakTimestamp;
            this.valleyTimestamp = valleyTimestamp;
            this.peakBalance = peakBalance;
            this.valleyBalance = valleyBalance;
            this.drawdownRate = drawdownRate;
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

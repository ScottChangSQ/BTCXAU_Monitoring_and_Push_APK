/*
 * 行情图表页，负责展示 K 线、指标、异常交易标注以及当前持仓相关信息。
 * 与行情接口、账户快照预加载、主题系统和异常记录模块协同工作。
 */
package com.binance.monitor.ui.chart;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.local.db.repository.ChartHistoryRepository;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.data.model.v2.MarketSeriesPayload;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.data.remote.v2.GatewayV2Client;
import com.binance.monitor.data.remote.v2.GatewayV2TradeClient;
import com.binance.monitor.databinding.ActivityMarketChartBinding;
import com.binance.monitor.databinding.DialogTradeCommandBinding;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.ui.account.adapter.PendingOrderAdapter;
import com.binance.monitor.ui.account.adapter.PositionAdapterV2;
import com.binance.monitor.ui.account.adapter.PositionAggregateAdapter;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.ui.trade.TradeCommandFactory;
import com.binance.monitor.ui.trade.TradeCommandStateMachine;
import com.binance.monitor.ui.trade.TradeConfirmDialogController;
import com.binance.monitor.ui.trade.TradeExecutionCoordinator;
import com.binance.monitor.service.MonitorService;
import com.binance.monitor.util.ChainLatencyTracer;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MarketChartActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET_SYMBOL = "extra_target_symbol";
    public static final String PREF_RUNTIME_NAME = "market_chart_runtime";
    private static final String PREF_KEY_SELECTED_INTERVAL = "selected_interval";
    private static final String PREF_KEY_SHOW_HISTORY_TRADES = "show_history_trades";
    private static final String PREF_KEY_SHOW_POSITION_OVERLAYS = "show_position_overlays";
    private static final String PREF_KEY_POSITION_SORT = "position_sort";
    private static final String PREF_KEY_CHART_CACHE_SCHEMA_VERSION = "chart_cache_schema_version";
    private static final int CHART_CACHE_SCHEMA_VERSION = 2;

    private static final int HISTORY_PERSIST_LIMIT = 5_000;
    private static final int RESTORE_WINDOW_LIMIT = 240;
    private static final int HISTORY_PAGE_LIMIT = 300;
    private static final int GAP_FILL_MAX_ROUNDS = 8;
    private static final long CHART_OVERLAY_REFRESH_DEBOUNCE_MS = 120L;

    private static final class IntervalOption {
        private final String key;
        private final String label;
        private final String apiInterval;
        private final int limit;
        private final boolean yearAggregate;

        private IntervalOption(String key, String label, String apiInterval, int limit, boolean yearAggregate) {
            this.key = key;
            this.label = label;
            this.apiInterval = apiInterval;
            this.limit = limit;
            this.yearAggregate = yearAggregate;
        }
    }

    private interface IndicatorParamApplyCallback {
        void onApply(int[] values);
    }

    private static final IntervalOption[] INTERVALS = new IntervalOption[]{
            new IntervalOption("1m", "1分", "1m", 1500, false),
            new IntervalOption("5m", "5分", "5m", 1500, false),
            new IntervalOption("15m", "15分", "15m", 1500, false),
            new IntervalOption("30m", "30分", "30m", 1500, false),
            new IntervalOption("1h", "1小时", "1h", 1500, false),
            new IntervalOption("4h", "4小时", "4h", 1500, false),
            new IntervalOption("1d", "日线", "1d", 1500, false),
            new IntervalOption("1w", "周线", "1w", 1500, false),
            new IntervalOption("1M", "月线", "1M", 1500, false),
            new IntervalOption("1y", "年线", "1M", 1500, true)
    };

    private ActivityMarketChartBinding binding;
    private GatewayV2Client gatewayV2Client;
    private GatewayV2TradeClient gatewayV2TradeClient;
    private MonitorRepository monitorRepository;
    private TradeExecutionCoordinator tradeExecutionCoordinator;
    private MarketChartTradeDialogCoordinator tradeDialogCoordinator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService ioExecutor;
    private Future<?> runningTask;
    private Future<?> loadMoreTask;
    private Future<?> progressiveGapFillTask;
    private long runningTaskStartMs;
    private int requestVersion = 0;
    private final Map<String, List<CandleEntry>> klineCache = new ConcurrentHashMap<>();
    private final Map<String, String> latestPersistedSignatures = new ConcurrentHashMap<>();
    private final Set<String> pendingPersistedCacheKeys = ConcurrentHashMap.newKeySet();
    private ChartHistoryRepository chartHistoryRepository;
    private AccountStorageRepository accountStorageRepository;
    private AccountStatsPreloadManager accountStatsPreloadManager;
    private AbnormalRecordManager abnormalRecordManager;
    private SecureSessionPrefs secureSessionPrefs;
    private Future<?> storedChartOverlayRestoreTask;
    private AccountSnapshot storedChartOverlaySnapshot;
    private String storedChartOverlayAccount = "";
    private String storedChartOverlayServer = "";

    private String selectedSymbol = AppConstants.SYMBOL_BTC;
    private ArrayAdapter<String> symbolAdapter;
    private IntervalOption selectedInterval = INTERVALS[2];
    private String activeDataKey = "";
    private boolean showVolume = true;
    private boolean showMacd = true;
    private boolean showStochRsi = true;
    private boolean showBoll = true;
    private boolean showMa = false;
    private boolean showEma = false;
    private boolean showSra;
    private boolean showAvl;
    private boolean showRsi;
    private boolean showKdj;
    private boolean showHistoryTrades = true;
    private boolean showPositionOverlays = true;
    private int maPeriod = 20;
    private int emaPeriod = 12;
    private int sraPeriod = 14;
    private int avlPeriod = 20;
    private int rsiPeriod = 14;
    private int kdjPeriod = 9;
    private int kdjSmoothK = 3;
    private int kdjSmoothD = 3;
    private int bollPeriod = 20;
    private int bollStdMultiplier = 2;
    private int macdFastPeriod = 12;
    private int macdSlowPeriod = 26;
    private int macdSignalPeriod = 9;
    private int stochRsiLookback = 14;
    private int stochRsiSmoothK = 3;
    private int stochRsiSmoothD = 3;
    private final List<CandleEntry> loadedCandles = new ArrayList<>();
    private final SimpleDateFormat infoTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final SimpleDateFormat stateTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final DecimalFormat qtyFormat = new DecimalFormat("0.####");
    private final DecimalFormat pnlFormat = new DecimalFormat("0.##");
    private PositionAggregateAdapter chartPositionAggregateAdapter;
    private PositionAdapterV2 chartPositionAdapter;
    private PendingOrderAdapter chartPendingOrderAdapter;
    private final List<PositionItem> lastChartPositions = new ArrayList<>();
    private final List<PositionItem> lastChartPendingOrders = new ArrayList<>();
    private double lastChartTotalAsset;
    private MarketChartPositionSortHelper.SortOption selectedPositionSort =
            MarketChartPositionSortHelper.SortOption.OPEN_TIME_DESC;
    private int pricePaneLeftPx;
    private int pricePaneTopPx;
    private int pricePaneRightPx;
    private int pricePaneBottomPx;
    private int volumePaneLeftPx;
    private int volumePaneTopPx;
    private int volumePaneRightPx;
    private int volumePaneBottomPx;
    private long nextAutoRefreshAtMs;
    private volatile boolean loadingMore;
    private long lastSuccessUpdateMs;
    private long lastSuccessfulRequestLatencyMs = -1L;
    private List<AbnormalRecord> abnormalRecords = new ArrayList<>();
    private boolean chartScreenEntered;
    private boolean accountOverlayRefreshPending;
    private String lastAccountOverlaySignature = "";
    private String lastAbnormalOverlaySignature = "";
    private final AccountStatsPreloadManager.CacheListener accountCacheListener = cache -> scheduleChartOverlayRefresh();
    private final Runnable chartOverlayRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            accountOverlayRefreshPending = false;
            updateAccountAnnotationsOverlay();
        }
    };
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            requestKlines(false, true);
            scheduleNextAutoRefresh();
        }
    };
    private final Runnable refreshCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            updateRefreshCountdownText();
            if (nextAutoRefreshAtMs > 0L) {
                mainHandler.postDelayed(this, 1_000L);
            }
        }
    };
    private final MarketChartStartupGate startupGate = new MarketChartStartupGate();
    @Nullable
    private ViewTreeObserver.OnDrawListener pendingStartupDrawListener;
    private String pendingStartupDrawKey = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMarketChartBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ensureMonitorServiceStarted();
        gatewayV2Client = new GatewayV2Client(this);
        gatewayV2TradeClient = new GatewayV2TradeClient(this);
        monitorRepository = MonitorRepository.getInstance(getApplicationContext());
        ioExecutor = Executors.newFixedThreadPool(2);
        chartHistoryRepository = new ChartHistoryRepository(this);
        accountStorageRepository = new AccountStorageRepository(getApplicationContext());
        accountStatsPreloadManager = AccountStatsPreloadManager.getInstance(getApplicationContext());
        abnormalRecordManager = AbnormalRecordManager.getInstance(getApplicationContext());
        secureSessionPrefs = new SecureSessionPrefs(getApplicationContext());
        tradeExecutionCoordinator = createTradeExecutionCoordinator();
        tradeDialogCoordinator = new MarketChartTradeDialogCoordinator(
                this,
                binding,
                mainHandler,
                ioExecutor,
                accountStatsPreloadManager,
                tradeExecutionCoordinator,
                () -> selectedSymbol,
                () -> loadedCandles,
                this::refreshChartOverlays
        );
        ensureChartCacheSchemaCurrent();
        applyIntentSymbol(getIntent(), false);
        restoreSelectedInterval();
        restoreHistoryTradeVisibility();
        restorePositionOverlayVisibility();
        restoreChartPositionSort();
        startupGate.resetForDataKey(buildCacheKey(selectedSymbol, selectedInterval));
        if (abnormalRecordManager != null) {
            abnormalRecordManager.getRecordsLiveData().observe(this, records -> {
                abnormalRecords = records == null ? new ArrayList<>() : new ArrayList<>(records);
                updateAbnormalAnnotationsOverlay();
            });
        }
        observeRealtimeDisplayKlines();

        setupChart();
        setupSymbolSelector();
        setupIntervalButtons();
        setupIndicatorButtons();
        setupChartPositionPanel();
        setupBottomNav();
        normalizeOptionButtons();
        binding.btnRetryLoad.setOnClickListener(v -> requestKlines());
        applyPaletteStyles();
        applyPrivacyMaskState();
        restorePersistedCache(buildCacheKey(selectedSymbol, selectedInterval));
        updateStateCount();
        updateRefreshCountdownText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureMonitorServiceStarted();
        applyPaletteStyles();
        if (accountStatsPreloadManager != null) {
            accountStatsPreloadManager.addCacheListener(accountCacheListener);
        }
        restoreChartOverlayFromLatestCacheOrEmpty();
        applyPrivacyMaskState();
        enterChartScreen(!chartScreenEntered);
        chartScreenEntered = true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntentSymbol(intent, true);
    }

    @Override
    protected void onPause() {
        stopAutoRefresh();
        if (tradeDialogCoordinator != null) {
            tradeDialogCoordinator.cancelTradeTasks();
        }
        mainHandler.removeCallbacks(chartOverlayRefreshRunnable);
        accountOverlayRefreshPending = false;
        if (accountStatsPreloadManager != null) {
            accountStatsPreloadManager.removeCacheListener(accountCacheListener);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopAutoRefresh();
        mainHandler.removeCallbacks(chartOverlayRefreshRunnable);
        accountOverlayRefreshPending = false;
        clearStartupPrimaryDrawObserver();
        if (accountStatsPreloadManager != null) {
            accountStatsPreloadManager.removeCacheListener(accountCacheListener);
        }
        if (runningTask != null) {
            runningTask.cancel(true);
            runningTask = null;
        }
        if (loadMoreTask != null) {
            loadMoreTask.cancel(true);
            loadMoreTask = null;
        }
        cancelProgressiveGapFillTask();
        if (tradeDialogCoordinator != null) {
            tradeDialogCoordinator.cancelTradeTasks();
        }
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private void setupChart() {
        binding.klineChartView.setIndicatorsVisible(showVolume, showMacd, showStochRsi, showBoll);
        binding.klineChartView.setExtendedIndicatorsVisible(showMa, showEma, showSra, showAvl, showRsi, showKdj);
        applyCoreIndicatorParamsToChart();
        applyAdvancedIndicatorParamsToChart();
        binding.klineChartView.setOnCrosshairListener(value -> {
            if (value == null) {
                renderInfoWithLatest();
            } else {
                renderInfo(value.candle, value.macdDif, value.macdDea, value.macdHist, value.stochK, value.stochD);
            }
        });
        binding.klineChartView.setOnRequestMoreListener(this::requestMoreHistory);
        binding.klineChartView.setOnPricePaneLayoutListener((left, top, right, bottom) -> {
            pricePaneLeftPx = left;
            pricePaneTopPx = top;
            pricePaneRightPx = right;
            pricePaneBottomPx = bottom;
            updateRefreshCountdownPosition();
            boolean positioned = updateScrollToLatestButtonPosition();
            updateHistoryTradeButtonPosition();
            if (positioned && binding.klineChartView.isLatestCandleOutOfBounds()) {
                binding.btnScrollToLatest.setVisibility(android.view.View.VISIBLE);
            }
        });
        binding.klineChartView.setOnVolumePaneLayoutListener((left, top, right, bottom) -> {
            volumePaneLeftPx = left;
            volumePaneTopPx = top;
            volumePaneRightPx = right;
            volumePaneBottomPx = bottom;
            updateHistoryTradeButtonPosition();
        });
        binding.klineChartView.setOnViewportStateListener(outOfBounds ->
        {
            if (!outOfBounds) {
                binding.btnScrollToLatest.setVisibility(android.view.View.INVISIBLE);
                return;
            }
            boolean positioned = updateScrollToLatestButtonPosition();
            if (positioned) {
                binding.btnScrollToLatest.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.btnScrollToLatest.setVisibility(android.view.View.INVISIBLE);
                binding.btnScrollToLatest.post(() -> {
                    if (updateScrollToLatestButtonPosition()) {
                        binding.btnScrollToLatest.setVisibility(android.view.View.VISIBLE);
                    }
                });
            }
        });
        binding.btnScrollToLatest.setOnClickListener(v -> {
            binding.klineChartView.scrollToLatest();
            updateScrollToLatestButtonPosition();
        });
        binding.btnTogglePositionOverlays.setOnClickListener(v -> togglePositionOverlayVisibility());
        binding.btnToggleHistoryTrades.setOnClickListener(v -> toggleHistoryTradeVisibility());
        binding.btnScrollToLatest.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateScrollToLatestButtonPosition());
        binding.tvChartRefreshCountdown.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateRefreshCountdownPosition());
        binding.btnTogglePositionOverlays.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updatePositionOverlayButtonPosition());
        binding.btnToggleHistoryTrades.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateHistoryTradeButtonPosition());
        binding.klineChartView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
        {
            updateRefreshCountdownPosition();
            updateScrollToLatestButtonPosition();
            updatePositionOverlayButtonPosition();
            updateHistoryTradeButtonPosition();
        });
        binding.btnScrollToLatest.setVisibility(android.view.View.INVISIBLE);
        updatePositionOverlayToggleButton();
        updateHistoryTradeToggleButton();
        updatePositionOverlayButtonPosition();
        updateHistoryTradeButtonPosition();
        refreshChartOverlays();
    }

    // 根据隐私状态控制敏感叠加层和当前持仓模块，不再使用整页遮罩。
    private void applyPrivacyMaskState() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        boolean masked = SensitiveDisplayMasker.isEnabled(this);
        binding.klineChartView.setOverlayVisibility(
                !masked && showPositionOverlays,
                !masked && showPositionOverlays,
                showHistoryTrades,
                !masked && showPositionOverlays);
        updatePositionOverlayToggleButton();
        updateHistoryTradeToggleButton();
        updateChartPositionPanel(lastChartPositions, lastChartPendingOrders, lastChartTotalAsset);
    }

    private void setupSymbolSelector() {
        List<String> symbols = getSupportedSymbols();
        symbolAdapter = createSymbolAdapter(symbols);
        binding.spinnerSymbolPicker.setAdapter(symbolAdapter);
        binding.tvChartSymbolPickerLabel.setOnClickListener(v -> binding.spinnerSymbolPicker.performClick());
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
                updateChartSymbolPickerLabel(symbol);
                switchSymbol(symbol);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        syncSymbolSelector();
    }

    // 统一产品下拉项文字样式，避免主题切换后下拉项不可见。
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

    // 强制设置颜色与字号，保证产品选项可读。
    private void styleSymbolSpinnerItem(@Nullable View view) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        UiPaletteManager.styleSpinnerItemText(textView, UiPaletteManager.resolve(this), 14f);
    }

    private void applyIntentSymbol(@Nullable Intent intent, boolean triggerReload) {
        String symbol = resolveTargetSymbol(intent);
        if (!isSupportedSymbol(symbol)) {
            return;
        }
        if (symbol.equalsIgnoreCase(selectedSymbol)) {
            return;
        }
        selectedSymbol = symbol;
        if (binding == null) {
            return;
        }
        syncSymbolSelector();
        if (triggerReload) {
            invalidateChartDisplayContext();
            requestKlines();
            scheduleNextAutoRefresh();
        }
    }

    private String resolveTargetSymbol(@Nullable Intent intent) {
        if (intent == null) {
            return "";
        }
        String raw = intent.getStringExtra(EXTRA_TARGET_SYMBOL);
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    // 统一处理图表页进入前台：冷启动只发起一次初始请求，普通切页返回只恢复消费节奏。
    private void enterChartScreen(boolean coldStart) {
        if (coldStart) {
            requestKlines();
        } else if (shouldRequestKlinesOnResume()) {
            requestKlines();
        }
        refreshChartOverlays();
        startAutoRefresh();
    }

    private boolean isSupportedSymbol(@Nullable String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        String target = symbol.trim().toUpperCase(Locale.ROOT);
        for (String item : getSupportedSymbols()) {
            if (target.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    private void setupIntervalButtons() {
        binding.btnInterval1m.setOnClickListener(v -> switchInterval(INTERVALS[0]));
        binding.btnInterval5m.setOnClickListener(v -> switchInterval(INTERVALS[1]));
        binding.btnInterval15m.setOnClickListener(v -> switchInterval(INTERVALS[2]));
        binding.btnInterval30m.setOnClickListener(v -> switchInterval(INTERVALS[3]));
        binding.btnInterval1h.setOnClickListener(v -> switchInterval(INTERVALS[4]));
        binding.btnInterval4h.setOnClickListener(v -> switchInterval(INTERVALS[5]));
        binding.btnInterval1d.setOnClickListener(v -> switchInterval(INTERVALS[6]));
        binding.btnInterval1w.setOnClickListener(v -> switchInterval(INTERVALS[7]));
        binding.btnInterval1mo.setOnClickListener(v -> switchInterval(INTERVALS[8]));
        binding.btnInterval1y.setOnClickListener(v -> switchInterval(INTERVALS[9]));
        updateIntervalButtons();
    }

    private void setupIndicatorButtons() {
        binding.btnIndicatorVolume.setOnClickListener(v -> toggleIndicator(() -> showVolume = !showVolume));
        binding.btnIndicatorMacd.setOnClickListener(v -> toggleIndicator(() -> showMacd = !showMacd));
        binding.btnIndicatorStochRsi.setOnClickListener(v -> toggleIndicator(() -> showStochRsi = !showStochRsi));
        binding.btnIndicatorBoll.setOnClickListener(v -> toggleIndicator(() -> showBoll = !showBoll));
        binding.btnIndicatorMa.setOnClickListener(v -> toggleIndicator(() -> showMa = !showMa));
        binding.btnIndicatorEma.setOnClickListener(v -> toggleIndicator(() -> showEma = !showEma));
        binding.btnIndicatorSra.setOnClickListener(v -> toggleIndicator(() -> showSra = !showSra));
        binding.btnIndicatorAvl.setOnClickListener(v -> toggleIndicator(() -> showAvl = !showAvl));
        binding.btnIndicatorRsi.setOnClickListener(v -> toggleIndicator(() -> showRsi = !showRsi));
        binding.btnIndicatorKdj.setOnClickListener(v -> toggleIndicator(() -> showKdj = !showKdj));

        binding.btnIndicatorVolume.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "VOL 参数设置",
                    "成交量主图当前无额外参数",
                    new String[]{},
                    new int[]{},
                    null);
            return true;
        });

        binding.btnIndicatorMacd.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "MACD 参数设置",
                    "指数平滑异同移动平均线",
                    new String[]{"快线", "慢线", "信号线"},
                    new int[]{macdFastPeriod, macdSlowPeriod, macdSignalPeriod},
                    values -> {
                        macdFastPeriod = values[0];
                        macdSlowPeriod = Math.max(macdFastPeriod + 1, values[1]);
                        macdSignalPeriod = values[2];
                        applyCoreIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });
        binding.btnIndicatorBoll.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "BOLL 参数设置",
                    "布林带",
                    new String[]{"周期", "标准差倍数"},
                    new int[]{bollPeriod, bollStdMultiplier},
                    values -> {
                        bollPeriod = values[0];
                        bollStdMultiplier = values[1];
                        applyCoreIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });
        binding.btnIndicatorStochRsi.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "STOCHRSI 参数设置",
                    "随机相对强弱指标",
                    new String[]{"随机周期", "平滑K", "平滑D"},
                    new int[]{stochRsiLookback, stochRsiSmoothK, stochRsiSmoothD},
                    values -> {
                        stochRsiLookback = values[0];
                        stochRsiSmoothK = values[1];
                        stochRsiSmoothD = values[2];
                        applyCoreIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });

        binding.btnIndicatorMa.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "MA 参数设置",
                    "移动平均线",
                    new String[]{"周期"},
                    new int[]{maPeriod},
                    values -> {
                        maPeriod = values[0];
                        applyAdvancedIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });
        binding.btnIndicatorEma.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "EMA 参数设置",
                    "指数移动平均线",
                    new String[]{"周期"},
                    new int[]{emaPeriod},
                    values -> {
                        emaPeriod = values[0];
                        applyAdvancedIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });
        binding.btnIndicatorSra.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "SRA 参数设置",
                    "平滑移动均线",
                    new String[]{"平滑周期"},
                    new int[]{sraPeriod},
                    values -> {
                        sraPeriod = values[0];
                        applyAdvancedIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });
        binding.btnIndicatorAvl.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "AVL 参数设置",
                    "成交量均线",
                    new String[]{"周期"},
                    new int[]{avlPeriod},
                    values -> {
                        avlPeriod = values[0];
                        applyAdvancedIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });
        binding.btnIndicatorRsi.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "RSI 参数设置",
                    "相对强弱指标",
                    new String[]{"周期"},
                    new int[]{rsiPeriod},
                    values -> {
                        rsiPeriod = values[0];
                        applyAdvancedIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });
        binding.btnIndicatorKdj.setOnLongClickListener(v -> {
            showIndicatorParamDialog(
                    "KDJ 参数设置",
                    "随机指标",
                    new String[]{"周期N", "平滑K", "平滑D"},
                    new int[]{kdjPeriod, kdjSmoothK, kdjSmoothD},
                    values -> {
                        kdjPeriod = values[0];
                        kdjSmoothK = values[1];
                        kdjSmoothD = values[2];
                        applyAdvancedIndicatorParamsToChart();
                        onIndicatorChanged();
                    });
            return true;
        });
        updateIndicatorButtons();
    }

    private void toggleIndicator(Runnable action) {
        if (action == null) {
            return;
        }
        action.run();
        onIndicatorChanged();
    }

    // 创建图表页复用的交易执行协调器，统一复用检查、确认和强刷逻辑。
    private TradeExecutionCoordinator createTradeExecutionCoordinator() {
        return new TradeExecutionCoordinator(
                new TradeExecutionCoordinator.TradeGateway() {
                    @Override
                    public com.binance.monitor.data.model.v2.trade.TradeCheckResult check(TradeCommand command) throws Exception {
                        return gatewayV2TradeClient.check(command);
                    }

                    @Override
                    public com.binance.monitor.data.model.v2.trade.TradeReceipt submit(TradeCommand command) throws Exception {
                        return gatewayV2TradeClient.submit(command);
                    }

                    @Override
                    public com.binance.monitor.data.model.v2.trade.TradeReceipt result(String requestId) throws Exception {
                        return gatewayV2TradeClient.result(requestId);
                    }
                },
                range -> accountStatsPreloadManager == null ? null : accountStatsPreloadManager.fetchForUi(range),
                new TradeConfirmDialogController(),
                3
        );
    }

    private void setupChartPositionPanel() {
        chartPositionAggregateAdapter = new PositionAggregateAdapter();
        chartPositionAdapter = new PositionAdapterV2();
        chartPendingOrderAdapter = new PendingOrderAdapter();
        ArrayAdapter<String> positionSortAdapter = createPositionSortAdapter();
        binding.recyclerChartPositionByProduct.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerChartPositionByProduct.setAdapter(chartPositionAggregateAdapter);
        binding.recyclerChartPositions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerChartPositions.setItemAnimator(null);
        binding.recyclerChartPositions.setAdapter(chartPositionAdapter);
        binding.recyclerChartPendingOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerChartPendingOrders.setItemAnimator(null);
        binding.recyclerChartPendingOrders.setAdapter(chartPendingOrderAdapter);
        binding.spinnerChartPositionSort.setAdapter(positionSortAdapter);
        binding.spinnerChartPositionSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                handleChartPositionSortSelection(MarketChartPositionSortHelper.fromLabel(
                        item == null ? "" : String.valueOf(item)), true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        binding.tvChartPositionSortLabel.setOnClickListener(v -> binding.spinnerChartPositionSort.performClick());
        chartPositionAdapter.setActionListener(item -> tradeDialogCoordinator.showPositionActionMenu(item));
        chartPendingOrderAdapter.setActionListener(item -> tradeDialogCoordinator.showPendingOrderActionMenu(item));
        binding.btnChartTradeBuy.setOnClickListener(
                v -> tradeDialogCoordinator.showTradeCommandDialog(
                        MarketChartTradeDialogCoordinator.ChartTradeAction.OPEN_BUY,
                        null
                )
        );
        binding.btnChartTradeSell.setOnClickListener(
                v -> tradeDialogCoordinator.showTradeCommandDialog(
                        MarketChartTradeDialogCoordinator.ChartTradeAction.OPEN_SELL,
                        null
                )
        );
        binding.btnChartTradePending.setOnClickListener(
                v -> tradeDialogCoordinator.showTradeCommandDialog(
                        MarketChartTradeDialogCoordinator.ChartTradeAction.PENDING_ADD,
                        null
                )
        );
        syncChartPositionSortSelection();
        restoreChartOverlayFromLatestCacheOrEmpty();
    }

    // 统一创建持仓明细排序下拉项，保证主题切换后选项仍可见。
    private ArrayAdapter<String> createPositionSortAdapter() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.item_spinner_filter,
                android.R.id.text1,
                MarketChartPositionSortHelper.buildOptionLabels()
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                stylePositionSortSpinnerItem(view);
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                stylePositionSortSpinnerItem(view);
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    // 排序标签和下拉选项统一使用当前主题文字色。
    private void stylePositionSortSpinnerItem(@Nullable View view) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        UiPaletteManager.styleSpinnerItemText(textView, UiPaletteManager.resolve(this), 12f);
    }

    // 点击右侧排序入口后统一更新状态、文案和持久化，再用最近一份持仓快照重绘列表。
    private void handleChartPositionSortSelection(@Nullable MarketChartPositionSortHelper.SortOption option,
                                                  boolean persist) {
        MarketChartPositionSortHelper.SortOption resolved = option == null
                ? MarketChartPositionSortHelper.SortOption.OPEN_TIME_DESC
                : option;
        if (resolved == selectedPositionSort && binding != null && binding.tvChartPositionSortLabel != null) {
            binding.tvChartPositionSortLabel.setText(resolved.getLabel());
            return;
        }
        selectedPositionSort = resolved;
        if (binding != null && binding.tvChartPositionSortLabel != null) {
            binding.tvChartPositionSortLabel.setText(resolved.getLabel());
        }
        if (persist) {
            persistChartPositionSort();
        }
        updateChartPositionPanel(lastChartPositions, lastChartPendingOrders, lastChartTotalAsset);
    }

    // 进入页面时同步 Spinner 选中项和右侧标签，避免控件状态与真实排序规则脱节。
    private void syncChartPositionSortSelection() {
        if (binding == null) {
            return;
        }
        binding.tvChartPositionSortLabel.setText(selectedPositionSort.getLabel());
        SpinnerAdapter adapter = binding.spinnerChartPositionSort.getAdapter();
        if (adapter == null) {
            return;
        }
        String[] labels = MarketChartPositionSortHelper.buildOptionLabels();
        for (int i = 0; i < labels.length; i++) {
            if (selectedPositionSort.getLabel().equals(labels[i])) {
                binding.spinnerChartPositionSort.setSelection(i, false);
                return;
            }
        }
    }

    private void updateChartPositionPanel(List<PositionItem> positions,
                                          List<PositionItem> pendingOrders,
                                          double totalAsset) {
        if (binding == null
                || chartPositionAggregateAdapter == null
                || chartPositionAdapter == null
                || chartPendingOrderAdapter == null) {
            return;
        }
        List<PositionItem> positionSnapshot = positions == null
                ? new ArrayList<>()
                : new ArrayList<>(positions);
        List<PositionItem> pendingSnapshot = pendingOrders == null
                ? new ArrayList<>()
                : new ArrayList<>(pendingOrders);
        lastChartPositions.clear();
        lastChartPositions.addAll(positionSnapshot);
        lastChartPendingOrders.clear();
        lastChartPendingOrders.addAll(pendingSnapshot);
        lastChartTotalAsset = Math.max(0d, totalAsset);
        boolean masked = SensitiveDisplayMasker.isEnabled(this);
        List<PositionItem> filteredPositions = new ArrayList<>();
        for (PositionItem item : positionSnapshot) {
            if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                continue;
            }
            if (!matchesSelectedSymbol(item.getCode(), item.getProductName())) {
                continue;
            }
            filteredPositions.add(item);
        }
        List<PositionItem> aggregateSource = new ArrayList<>(filteredPositions);
        aggregateSource.sort(Comparator.comparing(PositionItem::getProductName));
        filteredPositions = MarketChartPositionSortHelper.sortPositions(filteredPositions, selectedPositionSort);

        List<PositionItem> filteredPendingOrders = new ArrayList<>();
        for (PositionItem item : pendingSnapshot) {
            if (item == null) {
                continue;
            }
            if (!matchesSelectedSymbol(item.getCode(), item.getProductName())) {
                continue;
            }
            double pendingLots = resolvePendingLots(item);
            if (pendingLots <= 1e-9 && item.getPendingCount() <= 0) {
                continue;
            }
            filteredPendingOrders.add(item);
        }
        filteredPendingOrders.sort((a, b) -> Double.compare(resolvePendingSortWeight(b), resolvePendingSortWeight(a)));

        chartPositionAggregateAdapter.setMasked(masked);
        chartPositionAdapter.setMasked(masked);
        chartPendingOrderAdapter.setMasked(masked);
        List<PositionAggregateAdapter.AggregateItem> aggregateItems = buildPositionAggregatesForChart(aggregateSource);
        chartPositionAggregateAdapter.submitList(aggregateItems);
        binding.recyclerChartPositionByProduct.setVisibility(aggregateItems.isEmpty() ? View.GONE : View.VISIBLE);
        if (binding.tvChartPositionAggregateEmpty != null) {
            binding.tvChartPositionAggregateEmpty.setVisibility(aggregateItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
        chartPositionAdapter.submitList(filteredPositions);
        chartPendingOrderAdapter.submitList(filteredPendingOrders);
        binding.recyclerChartPositions.setVisibility(filteredPositions.isEmpty() ? View.GONE : View.VISIBLE);
        if (binding.tvChartPositionsEmpty != null) {
            binding.tvChartPositionsEmpty.setVisibility(filteredPositions.isEmpty() ? View.VISIBLE : View.GONE);
        }
        binding.tvChartPendingOrdersTitle.setVisibility(View.VISIBLE);
        binding.recyclerChartPendingOrders.setVisibility(filteredPendingOrders.isEmpty() ? View.GONE : View.VISIBLE);
        if (binding.tvChartPendingOrdersEmpty != null) {
            binding.tvChartPendingOrdersEmpty.setVisibility(filteredPendingOrders.isEmpty() ? View.VISIBLE : View.GONE);
        }

        double totalPnl = 0d;
        for (PositionItem item : filteredPositions) {
            totalPnl += item.getTotalPnL() + item.getStorageFee();
        }
        double ratio = totalAsset <= 1e-9 ? 0d : totalPnl / totalAsset;
        if (filteredPositions.isEmpty()) {
            if (masked) {
                binding.tvChartPositionSummary.setText("当前持仓：**** | 当前挂单：****");
            } else {
                binding.tvChartPositionSummary.setText("当前暂无持仓，挂单 " + filteredPendingOrders.size() + " 笔");
            }
            return;
        }
        if (masked) {
            binding.tvChartPositionSummary.setText("持仓盈亏: **** | 持仓收益率: ****");
        } else {
            binding.tvChartPositionSummary.setText(buildPositionPnlSummaryForChart(totalPnl, ratio));
        }
    }

    // 图表页当前持仓收益率统一按“持仓盈亏 / 当前总资产（总结余）”计算。
    private double resolveChartTotalAsset(@Nullable AccountSnapshot snapshot) {
        if (snapshot == null) {
            return 0d;
        }
        double balance = resolveMetricNumber(snapshot.getOverviewMetrics(), "总结余", "结余", "Balance", "Current Balance");
        if (balance > 1e-9) {
            return balance;
        }
        double curveBalance = resolveLatestCurveBalance(snapshot);
        if (curveBalance > 1e-9) {
            return curveBalance;
        }
        return Math.max(0d, resolveMetricNumber(snapshot.getOverviewMetrics(), "总资产", "Total Asset", "Equity", "Net Asset"));
    }

    private double resolveLatestCurveBalance(@Nullable AccountSnapshot snapshot) {
        if (snapshot == null || snapshot.getCurvePoints() == null || snapshot.getCurvePoints().isEmpty()) {
            return 0d;
        }
        for (int i = snapshot.getCurvePoints().size() - 1; i >= 0; i--) {
            CurvePoint point = snapshot.getCurvePoints().get(i);
            if (point == null) {
                continue;
            }
            double balance = Math.max(0d, point.getBalance());
            if (balance > 1e-9) {
                return balance;
            }
        }
        return 0d;
    }

    private double resolveMetricNumber(List<AccountMetric> metrics, String... names) {
        if (metrics == null || metrics.isEmpty() || names == null || names.length == 0) {
            return 0d;
        }
        for (AccountMetric metric : metrics) {
            if (metric == null) {
                continue;
            }
            String metricName = metric.getName() == null ? "" : metric.getName().trim().toLowerCase(Locale.ROOT);
            for (String name : names) {
                String candidate = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
                if (candidate.isEmpty()) {
                    continue;
                }
                if (metricName.contains(candidate)) {
                    return parseMetricNumber(metric.getValue());
                }
            }
        }
        return 0d;
    }

    private double parseMetricNumber(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0d;
        }
        StringBuilder builder = new StringBuilder();
        boolean hasDecimal = false;
        boolean hasSign = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c == '+' || c == '-') && !hasSign && builder.length() == 0) {
                builder.append(c);
                hasSign = true;
            } else if (Character.isDigit(c)) {
                builder.append(c);
            } else if (c == '.' && !hasDecimal) {
                builder.append(c);
                hasDecimal = true;
            }
        }
        if (builder.length() == 0 || "+".contentEquals(builder) || "-".contentEquals(builder)) {
            return 0d;
        }
        try {
            return Double.parseDouble(builder.toString());
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private double resolvePendingLots(@Nullable PositionItem item) {
        if (item == null) {
            return 0d;
        }
        double pendingLots = Math.abs(item.getPendingLots());
        if (pendingLots > 1e-9) {
            return pendingLots;
        }
        return Math.abs(item.getQuantity());
    }

    private double resolvePendingSortWeight(@Nullable PositionItem item) {
        if (item == null) {
            return 0d;
        }
        double lots = resolvePendingLots(item);
        if (lots > 1e-9) {
            return lots;
        }
        return Math.max(0d, item.getPendingCount());
    }

    private List<PositionAggregateAdapter.AggregateItem> buildPositionAggregatesForChart(List<PositionItem> list) {
        Map<String, PositionAggregateAdapter.AggregateItem> merged = new LinkedHashMap<>();
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        for (PositionItem item : list) {
            if (item == null) {
                continue;
            }
            String side = "SELL".equals(normalizeTradeSideLabel(item.getSide())) ? "卖出" : "买入";
            String key = item.getCode() + "|" + side;
            PositionAggregateAdapter.AggregateItem current = merged.get(key);
            double quantity = Math.max(0d, Math.abs(item.getQuantity()));
            if (current == null) {
                merged.put(key, new PositionAggregateAdapter.AggregateItem(
                        item.getProductName(),
                        side,
                        quantity,
                        quantity <= 0d ? 0d : item.getCostPrice(),
                        item.getTotalPnL() + item.getStorageFee()));
                continue;
            }
            double mergedQuantity = current.quantity + quantity;
            double weightedCost = current.avgCostPrice * current.quantity + item.getCostPrice() * quantity;
            double avgCost = mergedQuantity <= 1e-9 ? 0d : weightedCost / mergedQuantity;
            merged.put(key, new PositionAggregateAdapter.AggregateItem(
                    current.productName,
                    side,
                    mergedQuantity,
                    avgCost,
                    current.totalPnl + item.getTotalPnL() + item.getStorageFee()));
        }
        return new ArrayList<>(merged.values());
    }

    private CharSequence buildPositionPnlSummaryForChart(double totalPnl, double ratio) {
        String pnlText = formatSignedUsd(totalPnl);
        String ratioText = String.format(Locale.getDefault(), "%+.2f%%", ratio * 100d);
        String summary = "持仓盈亏: " + pnlText + " | 持仓收益率: " + ratioText;
        SpannableStringBuilder spannable = new SpannableStringBuilder(summary);
        int pnlColor = ContextCompat.getColor(this, totalPnl >= 0d ? R.color.accent_green : R.color.accent_red);
        int ratioColor = ContextCompat.getColor(this, ratio >= 0d ? R.color.accent_green : R.color.accent_red);

        int pnlStart = summary.indexOf(pnlText);
        if (pnlStart >= 0) {
            spannable.setSpan(new ForegroundColorSpan(pnlColor),
                    pnlStart,
                    pnlStart + pnlText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int ratioStart = summary.lastIndexOf(ratioText);
        if (ratioStart >= 0) {
            spannable.setSpan(new ForegroundColorSpan(ratioColor),
                    ratioStart,
                    ratioStart + ratioText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private void setupBottomNav() {
        updateBottomTabs(false, true, false, false);
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabMarketChart.setOnClickListener(v -> updateBottomTabs(false, true, false, false));
        binding.tabAccountStats.setOnClickListener(v -> openAccountStats());
        binding.tabSettings.setOnClickListener(v -> openSettings());
    }

    private void updateBottomTabs(boolean market, boolean chart, boolean account, boolean settings) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        BottomTabVisibilityManager.apply(this,
                binding.tabMarketMonitor,
                binding.tabMarketChart,
                binding.tabAccountStats,
                binding.tabSettings);
        binding.tabBar.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.surfaceEnd, palette.stroke));
        styleNavTab(binding.tabMarketMonitor, market);
        styleNavTab(binding.tabMarketChart, chart);
        styleNavTab(binding.tabAccountStats, account);
        styleNavTab(binding.tabSettings, settings);
    }

    private void styleNavTab(TextView button, boolean selected) {
        UiPaletteManager.styleBottomNavTab(button, selected, UiPaletteManager.resolve(this));
    }

    private void switchSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty() || symbol.equalsIgnoreCase(selectedSymbol)) {
            return;
        }
        selectedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        syncSymbolSelector();
        invalidateChartDisplayContext();
        requestKlines();
        scheduleNextAutoRefresh();
    }

    private void switchInterval(IntervalOption option) {
        if (option == null || option.key.equals(selectedInterval.key)) {
            return;
        }
        selectedInterval = option;
        persistSelectedInterval();
        updateIntervalButtons();
        invalidateChartDisplayContext();
        requestKlines();
        scheduleNextAutoRefresh();
    }

    private void onIndicatorChanged() {
        binding.klineChartView.setIndicatorsVisible(showVolume, showMacd, showStochRsi, showBoll);
        binding.klineChartView.setExtendedIndicatorsVisible(showMa, showEma, showSra, showAvl, showRsi, showKdj);
        updateIndicatorButtons();
        refreshChartOverlays();
        binding.klineChartView.post(() -> updateScrollToLatestButtonPosition());
    }

    private void applyAdvancedIndicatorParamsToChart() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        binding.klineChartView.setAdvancedIndicatorParams(
                maPeriod,
                emaPeriod,
                sraPeriod,
                avlPeriod,
                rsiPeriod,
                kdjPeriod,
                kdjSmoothK,
                kdjSmoothD
        );
    }

    private void applyCoreIndicatorParamsToChart() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        binding.klineChartView.setCoreIndicatorParams(
                bollPeriod,
                bollStdMultiplier,
                macdFastPeriod,
                macdSlowPeriod,
                macdSignalPeriod,
                stochRsiLookback,
                stochRsiSmoothK,
                stochRsiSmoothD
        );
    }

    private void showIndicatorParamDialog(String title,
                                          String hint,
                                          String[] labels,
                                          int[] defaults,
                                          IndicatorParamApplyCallback callback) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_indicator_params, null, false);
        TextView tvHint = content.findViewById(R.id.tvParamHint);
        TextView tvLabel1 = content.findViewById(R.id.tvParamLabel1);
        TextView tvLabel2 = content.findViewById(R.id.tvParamLabel2);
        TextView tvLabel3 = content.findViewById(R.id.tvParamLabel3);
        EditText etValue1 = content.findViewById(R.id.etParamValue1);
        EditText etValue2 = content.findViewById(R.id.etParamValue2);
        EditText etValue3 = content.findViewById(R.id.etParamValue3);
        tvHint.setText("长按后可调整该指标参数，修改后立即生效。");

        List<TextView> labelViews = new ArrayList<>();
        labelViews.add(tvLabel1);
        labelViews.add(tvLabel2);
        labelViews.add(tvLabel3);
        List<EditText> valueViews = new ArrayList<>();
        valueViews.add(etValue1);
        valueViews.add(etValue2);
        valueViews.add(etValue3);

        int count = Math.min(Math.min(labels == null ? 0 : labels.length, defaults == null ? 0 : defaults.length), 3);
        for (int i = 0; i < 3; i++) {
            TextView labelView = labelViews.get(i);
            EditText valueView = valueViews.get(i);
            if (i < count) {
                labelView.setVisibility(View.VISIBLE);
                valueView.setVisibility(View.VISIBLE);
                labelView.setText(labels[i]);
                valueView.setText(String.valueOf(defaults[i]));
            } else {
                labelView.setVisibility(View.GONE);
                valueView.setVisibility(View.GONE);
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(hint)
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", (dialog, which) -> {
                    int[] result = new int[count];
                    for (int i = 0; i < count; i++) {
                        int parsed = parsePositiveInt(valueViews.get(i).getText() == null
                                ? ""
                                : valueViews.get(i).getText().toString(), defaults[i]);
                        result[i] = parsed;
                    }
                    if (callback != null) {
                        callback.onApply(result);
                    }
                })
                .show();
    }

    private int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            return value > 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void requestKlines() {
        requestKlines(true, false);
    }

    private void requestKlines(boolean allowCancelRunning, boolean autoRefresh) {
        final String key = buildCacheKey(selectedSymbol, selectedInterval);

        if (runningTask != null && !runningTask.isDone()) {
            if (!allowCancelRunning) {
                boolean taskStale = autoRefresh && (System.currentTimeMillis() - runningTaskStartMs > 25_000L);
                if (!taskStale) {
                    return;
                }
            }
            runningTask.cancel(true);
            runningTask = null;
        }

        boolean shouldWarmDisplay = ChartWarmDisplayPolicyHelper.shouldWarmDisplay(
                loadedCandles.isEmpty(),
                !key.equals(activeDataKey)
        );
        List<CandleEntry> memoryCached = getCachedCandles(key);
        if (!MarketChartDisplayHelper.isSeriesCompatibleForInterval(selectedInterval.key, memoryCached)) {
            memoryCached = null;
        }
        if (memoryCached == null || memoryCached.isEmpty()) {
            schedulePersistedCacheRestore(key, shouldWarmDisplay);
        }
        if (shouldWarmDisplay) {
            List<CandleEntry> cached = memoryCached;
            if (cached == null || cached.isEmpty()) {
                cached = buildWarmDisplayCandles(selectedSymbol, selectedInterval);
            }
            if (cached != null && !cached.isEmpty()) {
                applyLocalDisplayCandles(key, cached);
            }
        }

        List<CandleEntry> localForPlan = key.equals(activeDataKey)
                ? loadedCandles
                : memoryCached;
        if (!MarketChartDisplayHelper.isSeriesCompatibleForInterval(selectedInterval.key, localForPlan)) {
            localForPlan = null;
        }
        long latestVisibleTime = resolveLatestVisibleCandleTime(localForPlan);
        MarketChartRefreshHelper.SyncPlan refreshPlan = MarketChartRefreshHelper.resolvePlan(
                localForPlan,
                selectedInterval.limit,
                RESTORE_WINDOW_LIMIT,
                System.currentTimeMillis(),
                latestVisibleTime,
                intervalToMs(selectedInterval.key),
                selectedInterval.yearAggregate,
                hasRealtimeTailSourceForChart()
        );
        if (refreshPlan.mode == MarketChartRefreshHelper.SyncMode.SKIP) {
            applyRequestSkipState(refreshPlan);
            return;
        }

        final int current = ++requestVersion;
        final String traceSymbol = selectedSymbol;
        final IntervalOption reqInterval = selectedInterval;
        final String traceIntervalKey = reqInterval == null ? "" : reqInterval.key;
        final long previousLatestOpenTime = loadedCandles.isEmpty()
                ? -1L
                : loadedCandles.get(loadedCandles.size() - 1).getOpenTime();
        final long previousOldestOpenTime = loadedCandles.isEmpty()
                ? -1L
                : loadedCandles.get(0).getOpenTime();
        final int previousWindowSize = loadedCandles.size();
        final long requestStartedAtMs = SystemClock.elapsedRealtime();
        applyRequestStartState(autoRefresh);
        runningTaskStartMs = System.currentTimeMillis();
        cancelProgressiveGapFillTask();
        final List<CandleEntry> refreshSeed = localForPlan == null ? new ArrayList<>() : new ArrayList<>(localForPlan);
        ChainLatencyTracer.markChartPullPhase(
                traceSymbol,
                traceIntervalKey,
                current,
                "start",
                0L,
                refreshSeed.size()
        );
        runningTask = ioExecutor.submit(() -> {
            try {
                long loadStartedAtMs = SystemClock.elapsedRealtime();
                List<CandleEntry> processed = loadCandlesForRequest(
                        refreshPlan,
                        refreshSeed,
                        previousLatestOpenTime,
                        previousOldestOpenTime,
                        previousWindowSize);
                long loadDurationMs = Math.max(0L, SystemClock.elapsedRealtime() - loadStartedAtMs);
                ChainLatencyTracer.markChartPullPhase(
                        traceSymbol,
                        traceIntervalKey,
                        current,
                        "load_done",
                        loadDurationMs,
                        processed.size()
                );
                final List<CandleEntry> finalProcessed = processed;
                if (finalProcessed.isEmpty()) {
                    throw new IllegalStateException("币安未返回可用K线数据");
                }
                mainHandler.post(() -> {
                    if (current != requestVersion || isFinishing() || isDestroyed()) {
                        return;
                    }
                    boolean followingLatestViewport = shouldFollowLatestViewportOnRefresh();
                    MarketChartDisplayHelper.DisplayUpdate displayUpdate = MarketChartDisplayHelper.buildDisplayUpdate(
                            selectedSymbol,
                            reqInterval == null ? "" : reqInterval.key,
                            refreshSeed,
                            finalProcessed,
                            reqInterval == null ? RESTORE_WINDOW_LIMIT : reqInterval.limit,
                            loadedCandles,
                            autoRefresh,
                            followingLatestViewport
                    );
                    activeDataKey = key;
                    if (displayUpdate.candlesChanged) {
                        applyDisplayCandles(key, displayUpdate.toDisplay, autoRefresh, displayUpdate.shouldFollowLatest, true);
                        List<CandleEntry> closedPersistenceWindow =
                                ChartPersistenceWindowHelper.retainClosedCandles(finalProcessed, System.currentTimeMillis());
                        persistCandlesAsync(key, closedPersistenceWindow, traceSymbol, reqInterval, true);
                    }
                    startProgressiveGapFill(
                            traceSymbol,
                            reqInterval,
                            current,
                            displayUpdate.toDisplay,
                            previousOldestOpenTime,
                            previousWindowSize
                    );
                    applyRequestSuccessState(autoRefresh, requestStartedAtMs);
                    long totalDurationMs = Math.max(0L, SystemClock.elapsedRealtime() - requestStartedAtMs);
                    ChainLatencyTracer.markChartPullPhase(
                            traceSymbol,
                            traceIntervalKey,
                            current,
                            "ui_applied",
                            totalDurationMs,
                            finalProcessed.size()
                    );
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                final String message = e.getMessage() == null ? "K线请求失败" : e.getMessage();
                mainHandler.post(() -> {
                    if (current != requestVersion || isFinishing() || isDestroyed()) {
                        return;
                    }
                    applyRequestFailureState(autoRefresh, message);
                });
            }
        });
    }

    private void requestMoreHistory(long beforeOpenTime) {
        if (loadingMore || loadedCandles.isEmpty()) {
            binding.klineChartView.notifyLoadMoreFinished();
            return;
        }
        loadingMore = true;
        final String reqSymbol = selectedSymbol;
        final IntervalOption reqInterval = selectedInterval;
        cancelProgressiveGapFillTask();
        if (loadMoreTask != null) {
            loadMoreTask.cancel(true);
        }
        loadMoreTask = ioExecutor.submit(() -> {
            try {
                List<CandleEntry> fetched = fetchV2SeriesBefore(
                        reqSymbol,
                        reqInterval,
                        HISTORY_PAGE_LIMIT,
                        beforeOpenTime - 1L
                );
                List<CandleEntry> processed = reqInterval.yearAggregate
                        ? aggregateToYear(fetched, reqSymbol)
                        : fetched;
                if (processed.isEmpty()) {
                    mainHandler.post(() -> {
                        finishLoadMoreState();
                    });
                    return;
                }
                mainHandler.post(() -> {
                    try {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        if (!reqSymbol.equals(selectedSymbol) || reqInterval != selectedInterval) {
                            return;
                        }
                        if (loadedCandles.isEmpty()) {
                            return;
                        }
                        List<CandleEntry> older = ChartHistoryPagingHelper.resolveOlderCandles(loadedCandles, processed);
                        if (older.isEmpty()) {
                            return;
                        }
                        applyLoadMoreSuccessState(reqSymbol, reqInterval, older);
                    } finally {
                        finishLoadMoreState();
                    }
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    finishLoadMoreState();
                });
            }
        });
    }

    private long intervalToMs(String key) {
        if ("1M".equals(key)) return 30L * 24L * 60L * 60_000L;
        if ("1y".equalsIgnoreCase(key)) return 365L * 24L * 60L * 60_000L;
        if ("1m".equalsIgnoreCase(key)) return 60_000L;
        if ("5m".equalsIgnoreCase(key)) return 5L * 60_000L;
        if ("15m".equalsIgnoreCase(key)) return 15L * 60_000L;
        if ("30m".equalsIgnoreCase(key)) return 30L * 60_000L;
        if ("1h".equalsIgnoreCase(key)) return 60L * 60_000L;
        if ("4h".equalsIgnoreCase(key)) return 4L * 60L * 60_000L;
        if ("1d".equalsIgnoreCase(key)) return 24L * 60L * 60_000L;
        if ("1w".equalsIgnoreCase(key)) return 7L * 24L * 60L * 60_000L;
        return -1L;
    }

    private long resolveLatestVisibleCandleTime(@Nullable List<CandleEntry> visible) {
        if (visible == null || visible.isEmpty()) {
            return 0L;
        }
        CandleEntry latest = visible.get(visible.size() - 1);
        return Math.max(latest.getCloseTime(), latest.getOpenTime());
    }

    // 切周期时，优先尝试用本地已有小周期缓存快速聚合出目标周期，减少首次等待网络的空窗。
    private List<CandleEntry> buildWarmDisplayCandles(String symbol, IntervalOption targetInterval) {
        if (symbol == null || symbol.trim().isEmpty() || targetInterval == null) {
            return new ArrayList<>();
        }
        for (IntervalOption candidate : INTERVALS) {
            if (!canWarmDisplayFrom(candidate, targetInterval)) {
                continue;
            }
            List<CandleEntry> source = getCachedCandles(buildCacheKey(symbol, candidate));
            if (source == null || source.isEmpty()) {
                continue;
            }
            String targetKey = targetInterval.yearAggregate ? targetInterval.key : targetInterval.apiInterval;
            List<CandleEntry> aggregated = CandleAggregationHelper.aggregate(
                    source,
                    symbol,
                    targetKey,
                    targetInterval.limit
            );
            List<CandleEntry> closedAggregated = CandleAggregationHelper.retainClosedTargetCandles(
                    aggregated,
                    targetKey,
                    System.currentTimeMillis()
            );
            if (closedAggregated.isEmpty()) {
                continue;
            }
            return closedAggregated;
        }
        return new ArrayList<>();
    }

    private boolean canWarmDisplayFrom(@Nullable IntervalOption source, @Nullable IntervalOption target) {
        return source != null
                && target != null
                && source != target
                && ChartWarmDisplayPolicyHelper.canWarmDisplayFrom(
                source.key,
                source.yearAggregate,
                target.key,
                target.yearAggregate
        );
    }

    // 用于持久化去重，避免同一窗口反复写盘。
    private String buildSeriesSignature(List<CandleEntry> candles) {
        return ChartSeriesSignatureHelper.build(candles);
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        scheduleNextAutoRefresh();
        if (shouldShowRefreshCountdown()) {
            mainHandler.post(refreshCountdownRunnable);
        } else {
            updateRefreshCountdownText();
        }
    }

    private void stopAutoRefresh() {
        mainHandler.removeCallbacks(autoRefreshRunnable);
        mainHandler.removeCallbacks(refreshCountdownRunnable);
        nextAutoRefreshAtMs = 0L;
        updateRefreshCountdownText();
    }

    private void scheduleNextAutoRefresh() {
        long delayMs = resolveAutoRefreshDelayMs();
        nextAutoRefreshAtMs = System.currentTimeMillis() + delayMs;
        mainHandler.removeCallbacks(autoRefreshRunnable);
        mainHandler.postDelayed(autoRefreshRunnable, delayMs);
        mainHandler.removeCallbacks(refreshCountdownRunnable);
        if (shouldShowRefreshCountdown()) {
            mainHandler.post(refreshCountdownRunnable);
        } else {
            updateRefreshCountdownText();
        }
    }

    private void updateRefreshCountdownText() {
        if (binding == null || binding.tvChartRefreshCountdown == null) {
            return;
        }
        binding.tvChartRefreshCountdown.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);
        binding.tvChartRefreshCountdown.setText(shouldShowRefreshCountdown()
                ? ChartRefreshMetaFormatter.buildCountdownText(
                        nextAutoRefreshAtMs,
                        System.currentTimeMillis(),
                        resolveAutoRefreshDelayMs(),
                        lastSuccessfulRequestLatencyMs
                )
                : ChartRefreshMetaFormatter.buildLatencyOnlyText(lastSuccessfulRequestLatencyMs));
        updateRefreshCountdownPosition();
    }

    // 当前图表已切到推送优先链路，不再展示固定秒级刷新倒计时。
    private boolean shouldShowRefreshCountdown() {
        return false;
    }

    private long resolveAutoRefreshDelayMs() {
        String key = buildCacheKey(selectedSymbol, selectedInterval);
        List<CandleEntry> visible = key.equals(activeDataKey)
                ? loadedCandles
                : getCachedCandles(key);
        boolean realtimeFresh = MarketChartRefreshHelper.isRealtimeFresh(
                System.currentTimeMillis(),
                resolveLatestVisibleCandleTime(visible)
        );
        return MarketChartRefreshHelper.resolveAutoRefreshDelayMs(
                realtimeFresh,
                AppConstants.CHART_AUTO_REFRESH_INTERVAL_MS,
                hasRealtimeTailSourceForChart(),
                System.currentTimeMillis()
        );
    }

    // 页面从其他 Tab 返回时，若已有可用窗口且当前窗口仍处于分钟源新鲜范围内，则不立即重拉。
    private boolean shouldRequestKlinesOnResume() {
        String key = buildCacheKey(selectedSymbol, selectedInterval);
        List<CandleEntry> visible = loadedCandles;
        if (visible.isEmpty()) {
            List<CandleEntry> cached = getCachedCandles(key);
            if (cached != null && !cached.isEmpty()) {
                visible = cached;
            } else {
                schedulePersistedCacheRestore(key, true);
            }
        }
        boolean compatibleVisible = !visible.isEmpty()
                && MarketChartDisplayHelper.isSeriesCompatibleForInterval(selectedInterval.key, visible);
        boolean freshVisibleWindow = compatibleVisible
                && MarketChartRefreshHelper.isRealtimeFresh(
                System.currentTimeMillis(),
                resolveLatestVisibleCandleTime(visible));
        boolean visibleSeriesNeedsRepair = compatibleVisible
                && MarketChartRefreshHelper.shouldForceRequestForSeriesRepair(
                visible,
                intervalToMs(selectedInterval.key),
                selectedInterval.yearAggregate,
                hasRealtimeTailSourceForChart()
        );
        return !MarketChartRefreshHelper.shouldSkipRequestOnResume(
                compatibleVisible,
                freshVisibleWindow,
                visibleSeriesNeedsRepair
        );
    }

    // 图表页实时分钟尾部只允许覆盖 1d 及以下周期，周/月/年线继续等待正式快照。
    private boolean hasRealtimeTailSourceForChart() {
        return monitorRepository != null
                && ChartWarmDisplayPolicyHelper.canRefreshFromMinuteTail(selectedInterval.key, selectedInterval.yearAggregate)
                && AppConstants.MONITOR_SYMBOLS.contains(selectedSymbol);
    }

    // 直接监听监控服务的实时 K 线尾部，让图表页与悬浮窗共用同一套最新行情源。
    private void observeRealtimeDisplayKlines() {
        if (monitorRepository == null) {
            return;
        }
        monitorRepository.getDisplayKlines().observe(this, snapshot -> {
            if (snapshot == null || snapshot.isEmpty()) {
                return;
            }
            applyRealtimeChartTail(snapshot.get(selectedSymbol));
        });
    }

    // 用最新分钟 K 线修正当前图表的末尾，避免只能每 5 秒靠轮询回填。
    private void applyRealtimeChartTail(@Nullable KlineData latestKline) {
        if (latestKline == null || binding == null || !hasRealtimeTailSourceForChart()) {
            return;
        }
        if (!matchesSelectedSymbol(latestKline.getSymbol())) {
            return;
        }
        String key = buildCacheKey(selectedSymbol, selectedInterval);
        if (startupGate.shouldDeferUntilPrimaryDisplay(key)) {
            startupGate.replacePendingRealtime(key, () -> applyRealtimeChartTail(latestKline));
            return;
        }
        CandleEntry realtimeBaseCandle = toRealtimeCandleEntry(latestKline);
        List<CandleEntry> minuteCandles = mergeRealtimeMinuteCache(realtimeBaseCandle);
        List<CandleEntry> realtimeDisplay = buildRealtimeDisplayCandles(realtimeBaseCandle, minuteCandles);
        if (realtimeDisplay.isEmpty()) {
            return;
        }
        List<CandleEntry> mergedDisplay = MarketChartDisplayHelper.mergeRealtimeTail(loadedCandles, realtimeDisplay);
        if (mergedDisplay.isEmpty()) {
            return;
        }
        boolean followingLatestViewport = shouldFollowLatestViewportOnRefresh();
        applyDisplayCandles(key, mergedDisplay, true, followingLatestViewport, true);
        ChainLatencyTracer.markChartRealtimeRender(
                selectedSymbol,
                latestKline.getCloseTime(),
                selectedInterval == null ? "" : selectedInterval.key
        );
        lastSuccessUpdateMs = System.currentTimeMillis();
        updateStateCount();
        refreshChartOverlays();
        if (!binding.klineChartView.hasActiveCrosshair()) {
            renderInfoWithLatest();
        }
        updateRefreshCountdownText();
    }

    // 统一把服务层实时 K 线转成图表层 CandleEntry，避免两边字段口径再次分叉。
    private CandleEntry toRealtimeCandleEntry(KlineData latestKline) {
        return new CandleEntry(
                selectedSymbol,
                latestKline.getOpenTime(),
                latestKline.getCloseTime(),
                latestKline.getOpenPrice(),
                latestKline.getHighPrice(),
                latestKline.getLowPrice(),
                latestKline.getClosePrice(),
                latestKline.getVolume(),
                latestKline.getQuoteAssetVolume()
        );
    }

    // 分钟底稿先写回本地缓存，供切周期、异常标注和后续聚合统一复用。
    private List<CandleEntry> mergeRealtimeMinuteCache(CandleEntry realtimeBaseCandle) {
        IntervalOption minuteOption = INTERVALS[0];
        String minuteKey = buildCacheKey(selectedSymbol, minuteOption);
        List<CandleEntry> minuteSource = getCachedCandles(minuteKey);
        List<CandleEntry> mergedMinute = CandleAggregationHelper.mergeRealtimeBaseCandle(
                minuteSource,
                realtimeBaseCandle,
                selectedSymbol,
                Math.max(minuteOption.limit, RESTORE_WINDOW_LIMIT)
        );
        if (!mergedMinute.isEmpty()) {
            klineCache.put(minuteKey, new ArrayList<>(mergedMinute));
        }
        return mergedMinute;
    }

    // 当前周期显示序列统一从分钟底稿派生，保证图表尾部与服务端实时价格口径一致。
    private List<CandleEntry> buildRealtimeDisplayCandles(CandleEntry realtimeBaseCandle,
                                                          List<CandleEntry> minuteCandles) {
        if (selectedInterval.yearAggregate) {
            return new ArrayList<>();
        }
        if ("1m".equalsIgnoreCase(selectedInterval.key)) {
            return CandleAggregationHelper.mergeRealtimeBaseCandle(
                    loadedCandles,
                    realtimeBaseCandle,
                    selectedSymbol,
                    selectedInterval.limit
            );
        }
        if (minuteCandles == null || minuteCandles.isEmpty()) {
            return new ArrayList<>();
        }
        return CandleAggregationHelper.aggregate(
                minuteCandles,
                selectedSymbol,
                selectedInterval.apiInterval,
                selectedInterval.limit
        );
    }

    private boolean matchesSelectedSymbol(@Nullable String symbol) {
        return symbol != null && symbol.trim().equalsIgnoreCase(selectedSymbol);
    }

    // 给周期/指标横向按钮条同步主题背景。
    private void applyStripBackground(Button anchorButton, UiPaletteManager.Palette palette) {
        if (anchorButton == null || palette == null) {
            return;
        }
        android.view.ViewParent parent = anchorButton.getParent();
        if (parent instanceof View) {
            ((View) parent).setBackground(UiPaletteManager.createFilledDrawable(this, palette.surfaceEnd));
        }
    }

    // 区分周期按钮和指标按钮，保持原有字号层级。
    private boolean isIntervalButton(Button button) {
        return button == binding.btnInterval1m
                || button == binding.btnInterval5m
                || button == binding.btnInterval15m
                || button == binding.btnInterval30m
                || button == binding.btnInterval1h
                || button == binding.btnInterval4h
                || button == binding.btnInterval1d
                || button == binding.btnInterval1w
                || button == binding.btnInterval1mo
                || button == binding.btnInterval1y;
    }

    private String buildCacheKey(String symbol, IntervalOption interval) {
        if (interval == null) {
            return MarketChartCacheKeyHelper.build(symbol, "default", "default", false);
        }
        return MarketChartCacheKeyHelper.build(symbol, interval.key, interval.apiInterval, interval.yearAggregate);
    }

    private List<String> getSupportedSymbols() {
        if (AppConstants.MONITOR_SYMBOLS == null || AppConstants.MONITOR_SYMBOLS.isEmpty()) {
            List<String> fallback = new ArrayList<>();
            fallback.add(AppConstants.SYMBOL_BTC);
            return fallback;
        }
        List<String> symbols = new ArrayList<>();
        for (String symbol : AppConstants.MONITOR_SYMBOLS) {
            if (symbol == null) {
                continue;
            }
            String normalized = symbol.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty() || symbols.contains(normalized)) {
                continue;
            }
            symbols.add(normalized);
        }
        if (symbols.isEmpty()) {
            symbols.add(AppConstants.SYMBOL_BTC);
        }
        return symbols;
    }

    // 图表数据口径升级后，一次性清掉旧缓存，避免历史脏数据继续污染最新图表。
    private void ensureChartCacheSchemaCurrent() {
        SharedPreferences preferences = getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE);
        int storedVersion = preferences.getInt(PREF_KEY_CHART_CACHE_SCHEMA_VERSION, 0);
        if (!ChartCacheInvalidationHelper.shouldInvalidate(storedVersion, CHART_CACHE_SCHEMA_VERSION)) {
            return;
        }
        if (chartHistoryRepository != null) {
            chartHistoryRepository.clearAllHistory();
        }
        klineCache.clear();
        latestPersistedSignatures.clear();
        loadedCandles.clear();
        activeDataKey = "";
        preferences.edit().putInt(PREF_KEY_CHART_CACHE_SCHEMA_VERSION, CHART_CACHE_SCHEMA_VERSION).apply();
    }

    private void restorePersistedCache(String key) {
        List<CandleEntry> persisted = getCachedCandles(key);
        if (persisted == null || persisted.isEmpty()) {
            schedulePersistedCacheRestore(key, true);
            return;
        }
        applyLocalDisplayCandles(key, persisted);
    }

    // 本地缓存恢复和切周期预显示共用同一套落图入口，避免状态更新规则再次分叉。
    private void applyLocalDisplayCandles(String key, List<CandleEntry> candles) {
        if (key == null || key.trim().isEmpty() || candles == null || candles.isEmpty() || binding == null) {
            return;
        }
        applyDisplayCandles(key, candles, false, false, false);
        renderInfoWithLatest();
        updateStateCount();
        refreshChartOverlays();
    }

    // 统一替换当前图表显示序列，区分是否保留视口与是否同步刷新内存缓存。
    private void applyDisplayCandles(String key,
                                     List<CandleEntry> candles,
                                     boolean keepViewport,
                                     boolean shouldFollowLatest,
                                     boolean updateMemoryCache) {
        if (key == null || key.trim().isEmpty() || candles == null || binding == null) {
            return;
        }
        loadedCandles.clear();
        loadedCandles.addAll(candles);
        activeDataKey = key;
        if (updateMemoryCache) {
            klineCache.put(key, new ArrayList<>(candles));
        }
        scheduleStartupPrimaryDrawObserver(key);
        if (keepViewport) {
            binding.klineChartView.setCandlesKeepingViewport(loadedCandles);
            if (shouldFollowLatest && !binding.klineChartView.hasActiveCrosshair()) {
                binding.klineChartView.scrollToLatest();
            }
            flushStartupDeferredWorkAfterPrimaryCommit(key);
            return;
        }
        binding.klineChartView.setCandles(loadedCandles);
        flushStartupDeferredWorkAfterPrimaryCommit(key);
    }

    // 十字线激活时说明用户正在查看某根K线，自动刷新此时不应再抢焦点跟随最新值。
    private boolean shouldFollowLatestViewportOnRefresh() {
        return binding != null
                && binding.klineChartView != null
                && binding.klineChartView.isFollowingLatestViewport()
                && !binding.klineChartView.hasActiveCrosshair();
    }

    // 统一处理成功回包后的页面状态更新，避免成功路径再散落多套收尾逻辑。
    private void applyRequestSuccessState(boolean autoRefresh, long requestStartedAtMs) {
        refreshChartOverlays();
        if (!(autoRefresh && binding.klineChartView.hasActiveCrosshair())) {
            renderInfoWithLatest();
        }
        long measuredLatencyMs = Math.max(0L, SystemClock.elapsedRealtime() - requestStartedAtMs);
        lastSuccessfulRequestLatencyMs = MarketChartRefreshHelper.smoothDisplayedLatencyMs(
                lastSuccessfulRequestLatencyMs,
                measuredLatencyMs
        );
        lastSuccessUpdateMs = System.currentTimeMillis();
        updateStateCount();
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.btnRetryLoad.setVisibility(android.view.View.GONE);
        showLoading(false);
    }

    // 统一处理跳过网络请求后的页面状态，避免 SKIP 分支继续散写 UI 重置。
    private void applyRequestSkipState(MarketChartRefreshHelper.SyncPlan refreshPlan) {
        lastSuccessfulRequestLatencyMs =
                MarketChartRefreshHelper.resolveDisplayedLatencyMs(refreshPlan, lastSuccessfulRequestLatencyMs);
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.btnRetryLoad.setVisibility(android.view.View.GONE);
        showLoading(false);
        updateRefreshCountdownText();
    }

    // 统一处理发起网络请求前的页面状态，避免 loading / error reset 再次分叉。
    private void applyRequestStartState(boolean autoRefresh) {
        showLoading(MarketChartDisplayHelper.shouldShowBlockingLoading(autoRefresh, loadedCandles));
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.btnRetryLoad.setVisibility(android.view.View.GONE);
    }

    // 统一处理失败后的页面状态，避免空图与错误提示规则继续散落在请求回调里。
    private void applyRequestFailureState(boolean autoRefresh, String message) {
        if (loadedCandles.isEmpty()) {
            clearChartDisplayForEmptyState();
        }
        updateStateCount();
        binding.tvError.setText(getString(R.string.chart_error_prefix, message));
        binding.tvError.setVisibility(android.view.View.VISIBLE);
        binding.btnRetryLoad.setVisibility(autoRefresh ? android.view.View.GONE : android.view.View.VISIBLE);
        showLoading(false);
    }

    // 没有任何可显示 K 线时，统一清空图表并恢复空态文案。
    private void clearChartDisplayForEmptyState() {
        binding.klineChartView.setCandles(new ArrayList<>());
        refreshChartOverlays();
        binding.tvChartInfo.setText(R.string.chart_info_empty);
    }

    // 切产品或切周期时，先失效上一组图表上下文，避免旧数据挂在新选择下继续显示。
    private void invalidateChartDisplayContext() {
        activeDataKey = "";
        loadedCandles.clear();
        lastAbnormalOverlaySignature = "";
        lastAccountOverlaySignature = "";
        startupGate.resetForDataKey(buildCacheKey(selectedSymbol, selectedInterval));
        clearStartupPrimaryDrawObserver();
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        binding.klineChartView.setCandles(new ArrayList<>());
        binding.klineChartView.setPositionAnnotations(new ArrayList<>());
        binding.klineChartView.setPendingAnnotations(new ArrayList<>());
        binding.klineChartView.setHistoryTradeAnnotations(new ArrayList<>());
        binding.klineChartView.setAggregateCostAnnotation(null);
        updateChartPositionPanel(new ArrayList<>(), new ArrayList<>(), 0d);
        binding.tvChartInfo.setText(R.string.chart_info_empty);
        updateStateCount();
    }

    // 左滑分页成功后统一更新内存、缓存、图表和叠加层，避免分页成功分支继续散写状态更新。
    private void applyLoadMoreSuccessState(String reqSymbol,
                                           IntervalOption reqInterval,
                                           List<CandleEntry> older) {
        if (reqSymbol == null || reqInterval == null || older == null || older.isEmpty()) {
            return;
        }
        loadedCandles.addAll(0, older);
        String cacheKey = buildCacheKey(reqSymbol, reqInterval);
        klineCache.put(cacheKey, new ArrayList<>(loadedCandles));
        persistCandlesAsync(cacheKey, older, reqSymbol, reqInterval, false);
        binding.klineChartView.prependCandles(older);
        refreshChartOverlays();
        updateStateCount();
    }

    // 分页结束统一收尾，避免成功/空结果/异常三条路径重复写 loadingMore 与通知回调。
    private void finishLoadMoreState() {
        loadingMore = false;
        binding.klineChartView.notifyLoadMoreFinished();
    }

    // 自动缺口补拉和主请求分开执行，避免重新进入图表时必须等整轮历史补完才第一次落图。
    private void startProgressiveGapFill(String reqSymbol,
                                         IntervalOption reqInterval,
                                         int current,
                                         @Nullable List<CandleEntry> visibleWindow,
                                         long previousOldestOpenTime,
                                         int previousWindowSize) {
        cancelProgressiveGapFillTask();
        if (reqSymbol == null
                || reqInterval == null
                || visibleWindow == null
                || visibleWindow.isEmpty()
                || ioExecutor == null
                || ioExecutor.isShutdown()) {
            return;
        }
        long latestWindowOldestOpenTime = resolveOldestOpenTime(visibleWindow);
        boolean visibleWindowHasInternalGap = hasVisibleWindowInternalGap(reqInterval, visibleWindow);
        if (!ChartGapFillHelper.shouldBackfillOlderHistory(
                previousWindowSize,
                RESTORE_WINDOW_LIMIT,
                previousOldestOpenTime,
                latestWindowOldestOpenTime,
                visibleWindowHasInternalGap)) {
            return;
        }
        List<CandleEntry> workingWindow = new ArrayList<>(visibleWindow);
        progressiveGapFillTask = ioExecutor.submit(() -> {
            try {
                if (reqInterval.yearAggregate) {
                    runYearGapFill(reqSymbol, reqInterval, current, workingWindow, previousOldestOpenTime, previousWindowSize);
                    return;
                }
                runProgressiveGapFill(reqSymbol, reqInterval, current, workingWindow, previousOldestOpenTime, previousWindowSize);
            } catch (Exception ignored) {
            }
        });
    }

    // 自动缺口补拉按批次向左 prepend，和手动左滑共用同一套落图路径。
    private void runProgressiveGapFill(String reqSymbol,
                                       IntervalOption reqInterval,
                                       int current,
                                       List<CandleEntry> workingWindow,
                                       long previousOldestOpenTime,
                                       int previousWindowSize) throws Exception {
        long latestOldest = resolveOldestOpenTime(workingWindow);
        int rounds = 0;
        while (!Thread.currentThread().isInterrupted()
                && ChartGapFillHelper.shouldBackfillOlderHistory(
                previousWindowSize,
                RESTORE_WINDOW_LIMIT,
                previousOldestOpenTime,
                latestOldest,
                hasVisibleWindowInternalGap(reqInterval, workingWindow))
                && rounds < GAP_FILL_MAX_ROUNDS) {
            List<CandleEntry> fetched = fetchV2SeriesBefore(
                    reqSymbol,
                    reqInterval,
                    HISTORY_PAGE_LIMIT,
                    Math.max(0L, latestOldest - 1L)
            );
            List<CandleEntry> older = ChartHistoryPagingHelper.resolveOlderCandles(workingWindow, fetched);
            if (older.isEmpty()) {
                break;
            }
            workingWindow.addAll(0, older);
            latestOldest = resolveOldestOpenTime(workingWindow);
            applyGapFillBatch(reqSymbol, reqInterval, current, older);
            rounds++;
        }
    }

    // 年线自动缺口补拉仍走月线底稿，再聚合成年桶，保持时间桶语义和手动翻页一致。
    private void runYearGapFill(String reqSymbol,
                                IntervalOption reqInterval,
                                int current,
                                List<CandleEntry> workingWindow,
                                long previousOldestOpenTime,
                                int previousWindowSize) throws Exception {
        long latestOldest = resolveOldestOpenTime(workingWindow);
        int rounds = 0;
        while (!Thread.currentThread().isInterrupted()
                && ChartGapFillHelper.shouldBackfillOlderHistory(
                previousWindowSize,
                RESTORE_WINDOW_LIMIT,
                previousOldestOpenTime,
                latestOldest,
                hasVisibleWindowInternalGap(reqInterval, workingWindow))
                && rounds < GAP_FILL_MAX_ROUNDS) {
            List<CandleEntry> olderMonthly = fetchV2SeriesBefore(
                    reqSymbol,
                    reqInterval,
                    HISTORY_PAGE_LIMIT,
                    Math.max(0L, latestOldest - 1L)
            );
            List<CandleEntry> olderYear = aggregateToYear(olderMonthly, reqSymbol);
            List<CandleEntry> older = ChartHistoryPagingHelper.resolveOlderCandles(workingWindow, olderYear);
            if (older.isEmpty()) {
                break;
            }
            workingWindow.addAll(0, older);
            latestOldest = resolveOldestOpenTime(workingWindow);
            applyGapFillBatch(reqSymbol, reqInterval, current, older);
            rounds++;
        }
    }

    // 后台补拉回包统一切回主线程并复用 prepend 更新，避免自动补拉和手动左滑两套 UI 逻辑分叉。
    private void applyGapFillBatch(String reqSymbol,
                                   IntervalOption reqInterval,
                                   int current,
                                   List<CandleEntry> older) {
        if (older == null || older.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            if (current != requestVersion || isFinishing() || isDestroyed()) {
                return;
            }
            if (!reqSymbol.equals(selectedSymbol) || reqInterval != selectedInterval) {
                return;
            }
            if (loadedCandles.isEmpty()) {
                return;
            }
            List<CandleEntry> appendable = ChartHistoryPagingHelper.resolveOlderCandles(loadedCandles, older);
            if (appendable.isEmpty()) {
                return;
            }
            applyLoadMoreSuccessState(reqSymbol, reqInterval, appendable);
        });
    }

    // 新请求、手动左滑或页面销毁时都要中断自动缺口补拉，避免旧任务继续占用网络与 UI。
    private void cancelProgressiveGapFillTask() {
        if (progressiveGapFillTask != null) {
            progressiveGapFillTask.cancel(true);
            progressiveGapFillTask = null;
        }
    }

    private long resolveOldestOpenTime(@Nullable List<CandleEntry> candles) {
        if (candles == null || candles.isEmpty()) {
            return -1L;
        }
        CandleEntry oldest = candles.get(0);
        return oldest == null ? -1L : oldest.getOpenTime();
    }

    private List<CandleEntry> getCachedCandles(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        List<CandleEntry> cached = klineCache.get(key);
        return cached == null || cached.isEmpty() ? null : cached;
    }

    // 图表页启动只允许消费内存缓存；Room 回读改为后台执行，避免切页时阻塞主线程导致 ANR。
    private void schedulePersistedCacheRestore(String key, boolean applyWhenLoaded) {
        if (chartHistoryRepository == null
                || ioExecutor == null
                || ioExecutor.isShutdown()
                || key == null
                || key.trim().isEmpty()) {
            return;
        }
        if (!pendingPersistedCacheKeys.add(key)) {
            return;
        }
        ioExecutor.submit(() -> {
            try {
                List<CandleEntry> persisted = chartHistoryRepository.loadCandles(key);
                if (persisted == null || persisted.isEmpty()) {
                    return;
                }
                List<CandleEntry> snapshot = new ArrayList<>(persisted);
                klineCache.put(key, snapshot);
                if (!applyWhenLoaded) {
                    return;
                }
                mainHandler.post(() -> applyPersistedCacheRestoreResult(key, snapshot));
            } finally {
                pendingPersistedCacheKeys.remove(key);
            }
        });
    }

    private void applyPersistedCacheRestoreResult(String key, List<CandleEntry> persisted) {
        if (isFinishing() || isDestroyed() || persisted == null || persisted.isEmpty()) {
            return;
        }
        if (!key.equals(buildCacheKey(selectedSymbol, selectedInterval))) {
            return;
        }
        if (!loadedCandles.isEmpty()) {
            return;
        }
        applyLocalDisplayCandles(key, persisted);
    }

    // 避免主线程频繁写大文件，持久化统一异步执行且按签名去重。
    private void persistCandlesAsync(String key,
                                     List<CandleEntry> candles,
                                     String symbol,
                                     @Nullable IntervalOption interval,
                                     boolean latestWindowWrite) {
        if (key == null || key.trim().isEmpty() || candles == null || candles.isEmpty() || interval == null) {
            return;
        }
        List<CandleEntry> snapshot = new ArrayList<>(candles);
        String signature = null;
        if (latestWindowWrite) {
            signature = buildSeriesSignature(snapshot);
            String persisted = latestPersistedSignatures.get(key);
            if (signature.equals(persisted)) {
                return;
            }
        }
        final String persistedSignature = signature;
        Runnable task = () -> {
            persistCandles(key, snapshot, symbol, interval);
            if (latestWindowWrite && persistedSignature != null) {
                latestPersistedSignatures.put(key, persistedSignature);
            }
        };
        if (ioExecutor == null || ioExecutor.isShutdown()) {
            task.run();
            return;
        }
        try {
            ioExecutor.submit(task);
        } catch (Exception ignored) {
            task.run();
        }
    }

    private void persistCandles(String key,
                                List<CandleEntry> candles,
                                String symbol,
                                @Nullable IntervalOption interval) {
        if (chartHistoryRepository == null
                || key == null
                || key.trim().isEmpty()
                || candles == null
                || candles.isEmpty()
                || interval == null) {
            return;
        }
        chartHistoryRepository.saveCandles(
                key,
                symbol,
                interval.key,
                interval.apiInterval,
                interval.yearAggregate,
                candles
        );
    }

    private List<CandleEntry> loadCandlesForRequest(MarketChartRefreshHelper.SyncPlan plan,
                                                    @Nullable List<CandleEntry> seed,
                                                    long previousLatestOpenTime,
                                                    long previousOldestOpenTime,
                                                    int previousWindowSize) throws Exception {
        if (selectedInterval != null && selectedInterval.yearAggregate) {
            return loadYearAggregateCandlesForRequest(
                    plan,
                    seed,
                    previousLatestOpenTime,
                    previousOldestOpenTime,
                    previousWindowSize
            );
        }
        List<CandleEntry> result;
        if (plan != null && plan.mode == MarketChartRefreshHelper.SyncMode.INCREMENTAL) {
            List<CandleEntry> base = ChartWindowSliceHelper.takeLatest(seed, RESTORE_WINDOW_LIMIT);
            List<CandleEntry> tail = fetchV2SeriesAfter(
                    selectedSymbol,
                    selectedInterval,
                    RESTORE_WINDOW_LIMIT,
                    plan.startTimeInclusive
            );
            result = MarketChartDisplayHelper.mergeSeriesByOpenTime(base, tail);
        } else {
            result = fetchFullHistoryAndMark(selectedSymbol, RESTORE_WINDOW_LIMIT);
        }
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("币安未返回可用K线数据");
        }
        return result;
    }

    // 年线显示仍使用月线接口增量拉取，但先聚合成年桶再与本地年线窗口合并，避免跨粒度直接拼接导致重复统计。
    private List<CandleEntry> loadYearAggregateCandlesForRequest(MarketChartRefreshHelper.SyncPlan plan,
                                                                 @Nullable List<CandleEntry> seed,
                                                                 long previousLatestOpenTime,
                                                                 long previousOldestOpenTime,
                                                                 int previousWindowSize) throws Exception {
        List<CandleEntry> mergedYear;
        if (plan != null && plan.mode == MarketChartRefreshHelper.SyncMode.INCREMENTAL) {
            List<CandleEntry> baseYear = ChartWindowSliceHelper.takeLatest(seed, RESTORE_WINDOW_LIMIT);
            List<CandleEntry> tailMonthly = fetchV2SeriesAfter(
                    selectedSymbol,
                    selectedInterval,
                    RESTORE_WINDOW_LIMIT,
                    plan.startTimeInclusive
            );
            List<CandleEntry> tailYear = aggregateToYear(tailMonthly, selectedSymbol);
            mergedYear = MarketChartDisplayHelper.mergeSeriesByOpenTime(baseYear, tailYear);
        } else {
            List<CandleEntry> fullMonthly = fetchFullHistoryAndMark(selectedSymbol, RESTORE_WINDOW_LIMIT);
            mergedYear = aggregateToYear(fullMonthly, selectedSymbol);
        }
        if (mergedYear == null || mergedYear.isEmpty()) {
            throw new IllegalStateException("币安未返回可用K线数据");
        }
        return mergedYear;
    }

    private List<CandleEntry> fetchFullHistoryAndMark(String symbol, int limit) throws Exception {
        return fetchV2FullSeries(symbol, selectedInterval, limit);
    }

    // v2 行情链路下，图表真值直接来自服务端的闭合 candles + latestPatch。
    private List<CandleEntry> fetchV2FullSeries(String symbol,
                                                @Nullable IntervalOption interval,
                                                int limit) throws Exception {
        if (gatewayV2Client == null || interval == null) {
            return new ArrayList<>();
        }
        MarketSeriesPayload payload = gatewayV2Client.fetchMarketSeries(symbol, interval.apiInterval, limit);
        return mergeMarketSeriesPayload(payload);
    }

    // v2 分页链路：按 endTime 拉窗口并复用同一套 candles + patch 合并逻辑。
    private List<CandleEntry> fetchV2SeriesBefore(String symbol,
                                                  @Nullable IntervalOption interval,
                                                  int limit,
                                                  long endTimeInclusive) throws Exception {
        if (gatewayV2Client == null || interval == null) {
            return new ArrayList<>();
        }
        MarketSeriesPayload payload = gatewayV2Client.fetchMarketSeriesBefore(
                symbol,
                interval.apiInterval,
                limit,
                endTimeInclusive
        );
        return mergeMarketSeriesPayload(payload);
    }

    // v2 增量链路：按 startTime 拉窗口，减少切周期后的补尾等待。
    private List<CandleEntry> fetchV2SeriesAfter(String symbol,
                                                 @Nullable IntervalOption interval,
                                                 int limit,
                                                 long startTimeInclusive) throws Exception {
        if (gatewayV2Client == null || interval == null) {
            return new ArrayList<>();
        }
        MarketSeriesPayload payload = gatewayV2Client.fetchMarketSeriesAfter(
                symbol,
                interval.apiInterval,
                limit,
                startTimeInclusive
        );
        return mergeMarketSeriesPayload(payload);
    }

    // 把 v2 返回的闭合 candles 与 latestPatch 合并成图表当前要显示的完整序列。
    private List<CandleEntry> mergeMarketSeriesPayload(@Nullable MarketSeriesPayload payload) {
        if (payload == null) {
            return new ArrayList<>();
        }
        return MarketChartDisplayHelper.mergeSeriesWithLatestPatch(
                payload.getCandles(),
                payload.getLatestPatch()
        );
    }

    private List<CandleEntry> aggregateToYear(List<CandleEntry> source, String symbol) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Integer, List<CandleEntry>> grouped = new LinkedHashMap<>();
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        for (CandleEntry item : source) {
            calendar.setTimeInMillis(item.getOpenTime());
            int year = calendar.get(Calendar.YEAR);
            List<CandleEntry> bucket = grouped.get(year);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(year, bucket);
            }
            bucket.add(item);
        }
        List<CandleEntry> out = new ArrayList<>();
        for (Map.Entry<Integer, List<CandleEntry>> entry : grouped.entrySet()) {
            List<CandleEntry> bucket = entry.getValue();
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            Collections.sort(bucket, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));
            CandleEntry first = bucket.get(0);
            CandleEntry last = bucket.get(bucket.size() - 1);
            double high = first.getHigh();
            double low = first.getLow();
            double vol = 0d;
            double quoteVol = 0d;
            for (CandleEntry item : bucket) {
                high = Math.max(high, item.getHigh());
                low = Math.min(low, item.getLow());
                vol += item.getVolume();
                quoteVol += item.getQuoteVolume();
            }
            out.add(new CandleEntry(
                    symbol,
                    first.getOpenTime(),
                    last.getCloseTime(),
                    first.getOpen(),
                    high,
                    low,
                    last.getClose(),
                    vol,
                    quoteVol
            ));
        }
        Collections.sort(out, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));
        return out;
    }

    private void renderInfoWithLatest() {
        if (loadedCandles.isEmpty()) {
            binding.tvChartInfo.setText(R.string.chart_info_empty);
            return;
        }
        CandleEntry latest = loadedCandles.get(loadedCandles.size() - 1);
        binding.tvChartInfo.setText(buildInfo(latest,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN));
    }

    private void renderInfo(CandleEntry candle, double dif, double dea, double macd, double k, double d) {
        binding.tvChartInfo.setText(buildInfo(candle, dif, dea, macd, k, d));
    }

    private String buildInfo(CandleEntry candle,
                             double dif,
                             double dea,
                             double macd,
                             double k,
                             double d) {
        if (candle == null) {
            return getString(R.string.chart_info_empty);
        }
        String time = infoTimeFormat.format(new java.util.Date(candle.getOpenTime()));
        String difText = Double.isNaN(dif) ? "--" : String.format(Locale.getDefault(), "%.3f", dif);
        String deaText = Double.isNaN(dea) ? "--" : String.format(Locale.getDefault(), "%.3f", dea);
        String macdText = Double.isNaN(macd) ? "--" : String.format(Locale.getDefault(), "%.3f", macd);
        String kText = Double.isNaN(k) ? "--" : String.format(Locale.getDefault(), "%.2f", k);
        String dText = Double.isNaN(d) ? "--" : String.format(Locale.getDefault(), "%.2f", d);
        return time
                + " | O:" + FormatUtils.formatPrice(candle.getOpen())
                + " H:" + FormatUtils.formatPrice(candle.getHigh())
                + " L:" + FormatUtils.formatPrice(candle.getLow())
                + " C:" + FormatUtils.formatPrice(candle.getClose())
                + " | VOL:" + shortVolume(candle.getVolume())
                + " | DIF:" + difText
                + " DEA:" + deaText
                + " MACD:" + macdText
                + " | K:" + kText
                + " D:" + dText;
    }

    private void updateStateCount() {
        String base = "共" + loadedCandles.size() + "根K线";
        if (lastSuccessUpdateMs > 0L) {
            base += "，更新时间：" + stateTimeFormat.format(lastSuccessUpdateMs);
        }
        binding.tvChartState.setText(base);
    }

    private String shortVolume(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000d) {
            return String.format(Locale.getDefault(), "%.2fB", value / 1_000_000_000d);
        }
        if (abs >= 1_000_000d) {
            return String.format(Locale.getDefault(), "%.2fM", value / 1_000_000d);
        }
        if (abs >= 1_000d) {
            return String.format(Locale.getDefault(), "%.2fK", value / 1_000d);
        }
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private void showLoading(boolean loading) {
        binding.progressChart.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.tvChartLoading.setVisibility(loading ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void refreshChartOverlays() {
        updateVolumeThresholdOverlay();
        updateAccountAnnotationsOverlay();
        updateAbnormalAnnotationsOverlay();
    }

    // 把账户侧高频刷新折叠成短延迟任务，避免持仓区连续重建导致卡顿。
    private void scheduleChartOverlayRefresh() {
        if (accountOverlayRefreshPending) {
            return;
        }
        accountOverlayRefreshPending = true;
        mainHandler.removeCallbacks(chartOverlayRefreshRunnable);
        mainHandler.postDelayed(chartOverlayRefreshRunnable, CHART_OVERLAY_REFRESH_DEBOUNCE_MS);
    }

    private void updateVolumeThresholdOverlay() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        SymbolConfig config = ConfigManager.getInstance(this).getSymbolConfig(selectedSymbol);
        boolean minuteInterval = "1m".equalsIgnoreCase(selectedInterval.apiInterval) && !selectedInterval.yearAggregate;
        double threshold = config == null ? Double.NaN : config.getVolumeThreshold();
        boolean visible = config != null
                && showVolume
                && minuteInterval
                && config.isVolumeEnabled()
                && threshold > 0d;
        binding.klineChartView.setVolumeThreshold(threshold, visible);
    }

    private void updateAbnormalAnnotationsOverlay() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        List<AbnormalRecord> filteredRecords = filterAbnormalRecordsForSelectedSymbol();
        String abnormalOverlaySignature = buildAbnormalOverlaySignature(filteredRecords);
        if (abnormalOverlaySignature.equals(lastAbnormalOverlaySignature)) {
            return;
        }
        List<KlineChartView.PriceAnnotation> abnormalAnnotations = new ArrayList<>();
        List<AbnormalAnnotationOverlayBuilder.BucketAnnotation> groupedAnnotations =
                AbnormalAnnotationOverlayBuilder.build(filteredRecords, loadedCandles);
        for (AbnormalAnnotationOverlayBuilder.BucketAnnotation item : groupedAnnotations) {
            abnormalAnnotations.add(new KlineChartView.PriceAnnotation(
                    item.anchorTimeMs,
                    item.price,
                    item.label,
                    item.color,
                    item.groupId,
                    item.count,
                    item.intensity
            ));
        }
        lastAbnormalOverlaySignature = abnormalOverlaySignature;
        binding.klineChartView.setAbnormalAnnotations(abnormalAnnotations);
    }

    // 先按当前选中标的筛掉无关异常记录，避免聚合器承担页面状态判断。
    private List<AbnormalRecord> filterAbnormalRecordsForSelectedSymbol() {
        List<AbnormalRecord> filtered = new ArrayList<>();
        if (abnormalRecords == null || abnormalRecords.isEmpty()) {
            return filtered;
        }
        for (AbnormalRecord record : abnormalRecords) {
            if (record == null || !matchesSelectedSymbol(record.getSymbol(), record.getSymbol())) {
                continue;
            }
            filtered.add(record);
        }
        return filtered;
    }

    // 用输入真值签名识别异常标注是否真的变化，避免切 tab 时重复重建同一层标注。
    private String buildAbnormalOverlaySignature(List<AbnormalRecord> filteredRecords) {
        long firstOpenTime = loadedCandles.isEmpty() ? 0L : loadedCandles.get(0).getOpenTime();
        long lastOpenTime = loadedCandles.isEmpty() ? 0L : loadedCandles.get(loadedCandles.size() - 1).getOpenTime();
        StringBuilder builder = new StringBuilder();
        builder.append(selectedSymbol == null ? "" : selectedSymbol.trim())
                .append('|')
                .append(loadedCandles.size())
                .append('|')
                .append(firstOpenTime)
                .append('|')
                .append(lastOpenTime);
        if (filteredRecords == null || filteredRecords.isEmpty()) {
            return builder.toString();
        }
        for (AbnormalRecord record : filteredRecords) {
            if (record == null) {
                builder.append("|null");
                continue;
            }
            builder.append('|')
                    .append(record.getId() == null ? "" : record.getId().trim())
                    .append('|')
                    .append(record.getTimestamp())
                    .append('|')
                    .append(record.getCloseTime())
                    .append('|')
                    .append(record.getOpenPrice())
                    .append('|')
                    .append(record.getClosePrice())
                    .append('|')
                    .append(record.getVolume())
                    .append('|')
                    .append(record.getAmount())
                    .append('|')
                    .append(record.getPriceChange())
                    .append('|')
                    .append(record.getPercentChange())
                    .append('|')
                    .append(record.getTriggerSummary() == null ? "" : record.getTriggerSummary().trim());
        }
        return builder.toString();
    }

    private void updateAccountAnnotationsOverlay() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        boolean sessionActive = ConfigManager.getInstance(this).isAccountSessionActive();
        AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager == null
                ? null
                : accountStatsPreloadManager.getLatestCache();
        AccountSnapshot snapshot = (!sessionActive || cache == null) ? null : cache.getSnapshot();
        if (!sessionActive) {
            clearStoredChartOverlaySnapshot();
            clearAccountAnnotationsOverlay();
            return;
        }
        if (sessionActive && (cache == null || snapshot == null)) {
            return;
        }
        String overlaySignature = buildAccountOverlaySignature(cache);
        if (overlaySignature.equals(lastAccountOverlaySignature)) {
            return;
        }
        if (snapshot == null) {
            lastAccountOverlaySignature = overlaySignature;
            clearAccountAnnotationsOverlay();
            return;
        }
        String key = buildCacheKey(selectedSymbol, selectedInterval);
        if (startupGate.shouldDeferUntilPrimaryDisplay(key)) {
            startupGate.replacePendingOverlay(key, () -> applyChartOverlaySnapshot(snapshot, cache));
            return;
        }
        applyChartOverlaySnapshot(snapshot, cache);
    }

    // 图表页初次创建时优先直接消费最近一份账户缓存，避免“当前持仓”先闪成空白再恢复。
    private void restoreChartOverlayFromLatestCacheOrEmpty() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        boolean sessionActive = ConfigManager.getInstance(this).isAccountSessionActive();
        AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager == null
                ? null
                : accountStatsPreloadManager.getLatestCache();
        AccountSnapshot snapshot = resolveChartOverlaySnapshot(sessionActive, cache);
        if (!sessionActive) {
            clearAccountAnnotationsOverlay();
            return;
        }
        // 会话仍有效但缓存尚未回填时，保留当前页面状态，避免首帧先闪成空白。
        if (snapshot == null) {
            return;
        }
        String key = buildCacheKey(selectedSymbol, selectedInterval);
        if (startupGate.shouldDeferUntilPrimaryDisplay(key)) {
            startupGate.replacePendingOverlay(key, () -> applyChartOverlaySnapshot(snapshot, cache));
            return;
        }
        applyChartOverlaySnapshot(snapshot, cache);
    }

    // 图表页恢复时优先消费内存缓存；缓存还没回填时回退到本地已持久化快照。
    @Nullable
    private AccountSnapshot resolveChartOverlaySnapshot(boolean sessionActive,
                                                        @Nullable AccountStatsPreloadManager.Cache cache) {
        if (!sessionActive) {
            return null;
        }
        if (cache != null && cache.getSnapshot() != null) {
            syncStoredChartOverlayIdentity(cache.getAccount(), cache.getServer());
            return cache.getSnapshot();
        }
        if (storedChartOverlaySnapshot != null && matchesStoredChartOverlayIdentity(resolveActiveSessionAccount(),
                resolveActiveSessionServer())) {
            return storedChartOverlaySnapshot;
        }
        clearStoredChartOverlaySnapshot();
        scheduleStoredChartOverlayRestore();
        return null;
    }

    // 图表页恢复时如需回退本地账户快照，也必须走后台线程，不能在首帧阶段同步读 Room。
    private void scheduleStoredChartOverlayRestore() {
        if (accountStorageRepository == null
                || ioExecutor == null
                || ioExecutor.isShutdown()
                || (storedChartOverlayRestoreTask != null && !storedChartOverlayRestoreTask.isDone())) {
            return;
        }
        storedChartOverlayRestoreTask = ioExecutor.submit(() -> {
            try {
                AccountStorageRepository.StoredSnapshot storedSnapshot = accountStorageRepository.loadStoredSnapshot();
                if (!hasStoredChartOverlaySnapshot(storedSnapshot)) {
                    return;
                }
                if (!matchesActiveSessionIdentity(storedSnapshot.getAccount(), storedSnapshot.getServer())) {
                    return;
                }
                AccountSnapshot snapshot = toAccountSnapshot(storedSnapshot);
                mainHandler.post(() -> applyStoredChartOverlaySnapshot(snapshot));
            } finally {
                storedChartOverlayRestoreTask = null;
            }
        });
    }

    private void applyStoredChartOverlaySnapshot(@Nullable AccountSnapshot snapshot) {
        if (snapshot == null || isFinishing() || isDestroyed() || binding == null || binding.klineChartView == null) {
            return;
        }
        if (!ConfigManager.getInstance(this).isAccountSessionActive()) {
            return;
        }
        AccountStatsPreloadManager.Cache latestCache = accountStatsPreloadManager == null
                ? null
                : accountStatsPreloadManager.getLatestCache();
        if (latestCache != null && latestCache.getSnapshot() != null) {
            return;
        }
        syncStoredChartOverlaySnapshot(snapshot, resolveActiveSessionAccount(), resolveActiveSessionServer());
        applyChartOverlaySnapshot(snapshot, null);
    }

    private AccountSnapshot toAccountSnapshot(AccountStorageRepository.StoredSnapshot storedSnapshot) {
        return new AccountSnapshot(
                storedSnapshot.getOverviewMetrics(),
                storedSnapshot.getCurvePoints(),
                storedSnapshot.getCurveIndicators(),
                storedSnapshot.getPositions(),
                storedSnapshot.getPendingOrders(),
                storedSnapshot.getTrades(),
                storedSnapshot.getStatsMetrics()
        );
    }

    private void applyChartOverlaySnapshot(@Nullable AccountSnapshot snapshot,
                                           @Nullable AccountStatsPreloadManager.Cache cache) {
        if (snapshot == null || binding == null || binding.klineChartView == null) {
            return;
        }
        List<TradeRecordItem> trades = snapshot.getTrades() == null
                ? Collections.emptyList()
                : snapshot.getTrades();
        List<PositionItem> positions = snapshot.getPositions() == null
                ? Collections.emptyList()
                : snapshot.getPositions();
        List<PositionItem> pendingOrders = snapshot.getPendingOrders() == null
                ? Collections.emptyList()
                : snapshot.getPendingOrders();
        double totalAsset = resolveChartTotalAsset(snapshot);
        binding.klineChartView.setPositionAnnotations(buildPositionAnnotations(positions, trades));
        binding.klineChartView.setPendingAnnotations(buildPendingAnnotations(pendingOrders, trades));
        binding.klineChartView.setHistoryTradeAnnotations(buildHistoricalTradeAnnotations(trades));
        binding.klineChartView.setAggregateCostAnnotation(buildAggregateCostAnnotation(positions));
        lastAccountOverlaySignature = buildAccountOverlaySignature(cache);
        updateChartPositionPanel(positions, pendingOrders, totalAsset);
    }

    // 启动阶段先确认主序列已经提交到图表视图，但仍要等到首帧真正绘制后才能放开增量更新。
    private void flushStartupDeferredWorkAfterPrimaryCommit(@Nullable String key) {
        List<Runnable> pending = startupGate.onPrimaryDisplayCommitted(key);
        runStartupDeferredWork(pending);
    }

    // 只有主图首帧真正画出来后，实时尾部和账户叠加层才允许开始消费。
    private void flushStartupDeferredWorkAfterPrimaryDraw(@Nullable String key) {
        List<Runnable> pending = startupGate.onPrimaryDisplayDrawn(key);
        runStartupDeferredWork(pending);
    }

    private void runStartupDeferredWork(@Nullable List<Runnable> pending) {
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (Runnable item : pending) {
            if (item != null) {
                item.run();
            }
        }
    }

    // 为当前 key 注册一次性首帧绘制监听，确保启动期的增量更新只会在主图真正出现在屏幕上后释放。
    private void scheduleStartupPrimaryDrawObserver(@Nullable String key) {
        if (binding == null || binding.klineChartView == null || key == null || key.trim().isEmpty()) {
            return;
        }
        if (!startupGate.shouldDeferUntilPrimaryDisplay(key)) {
            clearStartupPrimaryDrawObserver();
            return;
        }
        if (key.equals(pendingStartupDrawKey) && pendingStartupDrawListener != null) {
            return;
        }
        clearStartupPrimaryDrawObserver();
        pendingStartupDrawKey = key;
        pendingStartupDrawListener = new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                if (binding == null || binding.klineChartView == null) {
                    clearStartupPrimaryDrawObserver();
                    return;
                }
                String drawKey = pendingStartupDrawKey;
                binding.klineChartView.post(() -> {
                    clearStartupPrimaryDrawObserver();
                    flushStartupDeferredWorkAfterPrimaryDraw(drawKey);
                });
            }
        };
        binding.klineChartView.getViewTreeObserver().addOnDrawListener(pendingStartupDrawListener);
    }

    private void clearStartupPrimaryDrawObserver() {
        if (binding != null && binding.klineChartView != null && pendingStartupDrawListener != null) {
            ViewTreeObserver observer = binding.klineChartView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnDrawListener(pendingStartupDrawListener);
            }
        }
        pendingStartupDrawListener = null;
        pendingStartupDrawKey = "";
    }

    // 只要本地已经落过账户叠加层必需数据，就允许首帧直接恢复，避免当前持仓先空白。
    private boolean hasStoredChartOverlaySnapshot(@Nullable AccountStorageRepository.StoredSnapshot storedSnapshot) {
        if (storedSnapshot == null) {
            return false;
        }
        return !storedSnapshot.getPositions().isEmpty()
                || !storedSnapshot.getPendingOrders().isEmpty()
                || !storedSnapshot.getTrades().isEmpty()
                || !storedSnapshot.getOverviewMetrics().isEmpty()
                || !storedSnapshot.getCurvePoints().isEmpty();
    }

    // 当没有任何可用账户快照时，统一清空图表上的持仓与挂单标注。
    private void clearAccountAnnotationsOverlay() {
        clearStoredChartOverlaySnapshot();
        lastAccountOverlaySignature = buildAccountOverlaySignature(
                accountStatsPreloadManager == null ? null : accountStatsPreloadManager.getLatestCache()
        );
        binding.klineChartView.setPositionAnnotations(new ArrayList<>());
        binding.klineChartView.setPendingAnnotations(new ArrayList<>());
        binding.klineChartView.setHistoryTradeAnnotations(new ArrayList<>());
        binding.klineChartView.setAggregateCostAnnotation(null);
        updateChartPositionPanel(new ArrayList<>(), new ArrayList<>(), 0d);
    }

    // 用轻量签名识别账户叠加层是否真的变化，避免无效重算。
    private String buildAccountOverlaySignature(@Nullable AccountStatsPreloadManager.Cache cache) {
        String chartDataKey = buildCacheKey(selectedSymbol, selectedInterval);
        long firstOpenTime = loadedCandles.isEmpty() ? 0L : loadedCandles.get(0).getOpenTime();
        long lastOpenTime = loadedCandles.isEmpty() ? 0L : loadedCandles.get(loadedCandles.size() - 1).getOpenTime();
        long cacheUpdatedAt = cache == null ? 0L : cache.getUpdatedAt();
        String historyRevision = cache == null ? "" : cache.getHistoryRevision();
        String account = cache == null ? resolveActiveSessionAccount() : trimToEmpty(cache.getAccount());
        String server = cache == null ? resolveActiveSessionServer() : trimToEmpty(cache.getServer());
        return chartDataKey + "|" + loadedCandles.size() + "|" + firstOpenTime + "|" + lastOpenTime
                + "|" + cacheUpdatedAt + "|" + historyRevision + "|" + account + "|" + server;
    }

    private void syncStoredChartOverlaySnapshot(@Nullable AccountSnapshot snapshot,
                                                @Nullable String account,
                                                @Nullable String server) {
        storedChartOverlaySnapshot = snapshot;
        storedChartOverlayAccount = trimToEmpty(account);
        storedChartOverlayServer = trimToEmpty(server);
    }

    private void clearStoredChartOverlaySnapshot() {
        storedChartOverlaySnapshot = null;
        storedChartOverlayAccount = "";
        storedChartOverlayServer = "";
    }

    private void syncStoredChartOverlayIdentity(@Nullable String account, @Nullable String server) {
        storedChartOverlayAccount = trimToEmpty(account);
        storedChartOverlayServer = trimToEmpty(server);
    }

    private boolean matchesStoredChartOverlayIdentity(@Nullable String account, @Nullable String server) {
        String candidateAccount = trimToEmpty(account);
        String candidateServer = trimToEmpty(server);
        if (candidateAccount.isEmpty() || candidateServer.isEmpty()) {
            return false;
        }
        return candidateAccount.equalsIgnoreCase(storedChartOverlayAccount)
                && candidateServer.equalsIgnoreCase(storedChartOverlayServer);
    }

    private boolean matchesActiveSessionIdentity(@Nullable String account, @Nullable String server) {
        String expectedAccount = resolveActiveSessionAccount();
        String expectedServer = resolveActiveSessionServer();
        if (expectedAccount.isEmpty() || expectedServer.isEmpty()) {
            return false;
        }
        return expectedAccount.equalsIgnoreCase(trimToEmpty(account))
                && expectedServer.equalsIgnoreCase(trimToEmpty(server));
    }

    private String resolveActiveSessionAccount() {
        if (secureSessionPrefs == null || !ConfigManager.getInstance(this).isAccountSessionActive()) {
            return "";
        }
        SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();
        return sessionSummary.getActiveAccount() == null
                ? ""
                : trimToEmpty(sessionSummary.getActiveAccount().getLogin());
    }

    private String resolveActiveSessionServer() {
        if (secureSessionPrefs == null || !ConfigManager.getInstance(this).isAccountSessionActive()) {
            return "";
        }
        SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();
        return sessionSummary.getActiveAccount() == null
                ? ""
                : trimToEmpty(sessionSummary.getActiveAccount().getServer());
    }

    private String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    // 图表页可见窗口仍有固定时间粒度缺口时，需要继续向左补历史，不能只看窗口长度。
    private boolean hasVisibleWindowInternalGap(@Nullable IntervalOption interval,
                                                @Nullable List<CandleEntry> visibleWindow) {
        long expectedGapMs = resolveExpectedGapMs(interval);
        return expectedGapMs > 0L && MarketChartRefreshHelper.hasInternalGap(visibleWindow, expectedGapMs);
    }

    // 固定周期直接使用标准时间步长；月线和年线属于变长桶，不走这里的定长缺口判定。
    private long resolveExpectedGapMs(@Nullable IntervalOption interval) {
        if (interval == null || interval.yearAggregate) {
            return 0L;
        }
        String apiInterval = interval.apiInterval == null ? "" : interval.apiInterval.trim().toLowerCase(Locale.ROOT);
        switch (apiInterval) {
            case "1m":
                return 60_000L;
            case "5m":
                return 5L * 60_000L;
            case "15m":
                return 15L * 60_000L;
            case "30m":
                return 30L * 60_000L;
            case "1h":
                return 60L * 60_000L;
            case "4h":
                return 4L * 60L * 60_000L;
            case "1d":
                return 24L * 60L * 60_000L;
            case "1w":
                return 7L * 24L * 60L * 60_000L;
            default:
                return 0L;
        }
    }

    private List<KlineChartView.PriceAnnotation> buildHistoricalTradeAnnotations(List<TradeRecordItem> trades) {
        List<KlineChartView.PriceAnnotation> result = new ArrayList<>();
        List<HistoricalTradeAnnotationBuilder.TradeAnnotation> source =
                HistoricalTradeAnnotationBuilder.build(selectedSymbol, trades, loadedCandles);
        for (HistoricalTradeAnnotationBuilder.TradeAnnotation item : source) {
            if (item == null) {
                continue;
            }
            String sideLabel = "SELL".equalsIgnoreCase(item.side) ? "卖出" : "买入";
            int entryColor = "SELL".equalsIgnoreCase(item.side)
                    ? Color.parseColor("#F6465D")
                    : Color.parseColor("#4D8BFF");
            int connectorColor = item.totalPnl >= 0d
                    ? Color.parseColor("#16C784")
                    : Color.parseColor("#F6465D");
            int exitColor = Color.parseColor("#E7EEF7");
            String pnlLabel = formatSignedUsd(item.totalPnl);
            String[] detailLines = new String[]{
                    safeTradePopupValue(item.productName, item.code),
                    "方向 " + sideLabel,
                    "开仓 " + FormatUtils.formatDateTime(item.openTimeMs) + " $" + FormatUtils.formatPrice(item.entryPrice),
                    "平仓 " + FormatUtils.formatDateTime(item.closeTimeMs) + " $" + FormatUtils.formatPrice(item.exitPrice),
                    "数量 " + formatQuantity(item.quantity),
                    "盈亏 " + pnlLabel
            };
            result.add(new KlineChartView.PriceAnnotation(
                    item.entryAnchorTimeMs,
                    item.entryPrice,
                    sideLabel,
                    entryColor,
                    item.groupId,
                    1,
                    0f,
                    0L,
                    Double.NaN,
                    "SELL".equalsIgnoreCase(item.side)
                            ? KlineChartView.ANNOTATION_KIND_HISTORY_ENTRY_SELL
                            : KlineChartView.ANNOTATION_KIND_HISTORY_ENTRY_BUY,
                    detailLines
            ));
            result.add(new KlineChartView.PriceAnnotation(
                    item.entryAnchorTimeMs,
                    item.entryPrice,
                    pnlLabel,
                    connectorColor,
                    item.groupId,
                    1,
                    0f,
                    item.exitAnchorTimeMs,
                    item.exitPrice,
                    KlineChartView.ANNOTATION_KIND_HISTORY_CONNECTOR,
                    detailLines
            ));
            result.add(new KlineChartView.PriceAnnotation(
                    item.exitAnchorTimeMs,
                    item.exitPrice,
                    "平仓",
                    exitColor,
                    item.groupId,
                    1,
                    0f,
                    0L,
                    Double.NaN,
                    KlineChartView.ANNOTATION_KIND_HISTORY_EXIT,
                    detailLines
            ));
        }
        return result;
    }

    private String safeTradePopupValue(String productName, String code) {
        String name = productName == null ? "" : productName.trim();
        if (!name.isEmpty()) {
            return name;
        }
        return code == null ? "--" : code.trim();
    }

    private List<KlineChartView.PriceAnnotation> buildPositionAnnotations(List<PositionItem> positions,
                                                                          List<TradeRecordItem> trades) {
        List<KlineChartView.PriceAnnotation> result = new ArrayList<>();
        if (positions == null || positions.isEmpty()) {
            return result;
        }
        int gainColor = Color.parseColor("#16C784");
        int lossColor = Color.parseColor("#F6465D");
        int tpColor = Color.parseColor("#16C784");
        int slColor = Color.parseColor("#F6465D");
        for (PositionItem item : positions) {
            if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                continue;
            }
            if (!matchesSelectedSymbol(item.getCode(), item.getProductName())) {
                continue;
            }
            double price = item.getCostPrice() > 0d ? item.getCostPrice() : item.getLatestPrice();
            if (price <= 0d) {
                continue;
            }
            long anchorTime = resolvePositionAnchorTime(item, trades);
            if (anchorTime <= 0L) {
                continue;
            }
            String side = normalizeTradeSideLabel(item.getSide());
            String label = side + " " + formatQuantity(Math.abs(item.getQuantity()))
                    + ", " + formatSignedUsd(item.getTotalPnL());
            int color = item.getTotalPnL() >= 0d ? gainColor : lossColor;
            String groupId = buildAnnotationGroupId("position", item, anchorTime, price);
            String[] detailLines = new String[]{
                    safeTradePopupValue(item.getProductName(), item.getCode()),
                    "方向 " + side,
                    "开仓 " + formatPositionOpenTime(anchorTime) + " $" + FormatUtils.formatPrice(price),
                    "数量 " + formatQuantity(Math.abs(item.getQuantity())),
                    "浮盈亏 " + formatSignedUsd(item.getTotalPnL()),
                    "止盈 " + formatOptionalPrice(item.getTakeProfit()),
                    "止损 " + formatOptionalPrice(item.getStopLoss())
            };
            result.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    price,
                    label,
                    color,
                    groupId,
                    1,
                    0f,
                    0L,
                    Double.NaN,
                    KlineChartView.ANNOTATION_KIND_DEFAULT,
                    detailLines
            ));
            appendTpSlAnnotations(result, anchorTime, item.getTakeProfit(), item.getStopLoss(), tpColor, slColor, groupId);
        }
        result.sort(Comparator.comparingDouble(annotation -> annotation.price));
        return result;
    }

    private List<KlineChartView.PriceAnnotation> buildPendingAnnotations(List<PositionItem> pendingOrders,
                                                                         List<TradeRecordItem> trades) {
        List<KlineChartView.PriceAnnotation> result = new ArrayList<>();
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return result;
        }
        int buyColor = Color.parseColor("#16C784");
        int sellColor = Color.parseColor("#F6465D");
        int tpColor = Color.parseColor("#16C784");
        int slColor = Color.parseColor("#F6465D");
        for (PositionItem item : pendingOrders) {
            if (item == null) {
                continue;
            }
            if (!matchesSelectedSymbol(item.getCode(), item.getProductName())) {
                continue;
            }
            double lots = resolvePendingLots(item);
            if (lots <= 1e-9 && item.getPendingCount() <= 0) {
                continue;
            }
            double price = item.getPendingPrice() > 0d
                    ? item.getPendingPrice()
                    : (item.getCostPrice() > 0d ? item.getCostPrice() : item.getLatestPrice());
            if (price <= 0d) {
                continue;
            }
            String side = normalizeTradeSideLabel(item.getSide());
            int color = isSellSide(item.getSide()) ? sellColor : buyColor;
            long anchorTime = resolvePendingAnchorTime(item, trades);
            if (anchorTime <= 0L) {
                continue;
            }
            String qtyLabel = lots > 1e-9
                    ? formatQuantity(lots)
                    : (item.getPendingCount() > 0 ? (item.getPendingCount() + "单") : "--");
            String label = "PENDING " + side + " " + qtyLabel
                    + ", @ $" + FormatUtils.formatPrice(price);
            String groupId = buildAnnotationGroupId("pending", item, anchorTime, price);
            String[] detailLines = new String[]{
                    safeTradePopupValue(item.getProductName(), item.getCode()),
                    "方向 " + side,
                    "挂单 " + formatPositionOpenTime(anchorTime) + " $" + FormatUtils.formatPrice(price),
                    "数量 " + qtyLabel,
                    "止盈 " + formatOptionalPrice(item.getTakeProfit()),
                    "止损 " + formatOptionalPrice(item.getStopLoss())
            };
            result.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    price,
                    label,
                    color,
                    groupId,
                    1,
                    0f,
                    0L,
                    Double.NaN,
                    KlineChartView.ANNOTATION_KIND_DEFAULT,
                    detailLines
            ));
            appendTpSlAnnotations(result, anchorTime, item.getTakeProfit(), item.getStopLoss(), tpColor, slColor, groupId);
        }
        result.sort(Comparator.comparingDouble(annotation -> annotation.price));
        return result;
    }

    @Nullable
    private KlineChartView.AggregateCostAnnotation buildAggregateCostAnnotation(List<PositionItem> positions) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }
        double weightedCost = 0d;
        double qty = 0d;
        for (PositionItem item : positions) {
            if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                continue;
            }
            if (!matchesSelectedSymbol(item.getCode(), item.getProductName())) {
                continue;
            }
            if (item.getCostPrice() <= 0d) {
                continue;
            }
            double absQty = Math.abs(item.getQuantity());
            weightedCost += item.getCostPrice() * absQty;
            qty += absQty;
        }
        if (qty <= 1e-9) {
            return null;
        }
        double avgCost = weightedCost / qty;
        return new KlineChartView.AggregateCostAnnotation(avgCost, FormatUtils.formatPrice(avgCost), selectedSymbol);
    }

    private long resolvePositionAnchorTime(PositionItem position, List<TradeRecordItem> trades) {
        long directOpenTime = position.getOpenTime();
        if (directOpenTime > 0L) {
            return directOpenTime;
        }
        if (trades != null && !trades.isEmpty()) {
            long byPositionId = findTradeOpenTimeByPositionId(position.getPositionTicket(), position.getCostPrice(), trades);
            if (byPositionId > 0L) {
                return byPositionId;
            }
            long byOrderId = findTradeOpenTimeByOrderId(position.getOrderId(), position.getCostPrice(), trades);
            if (byOrderId > 0L) {
                return byOrderId;
            }
        }
        return 0L;
    }

    private long resolvePendingAnchorTime(PositionItem pendingOrder, List<TradeRecordItem> trades) {
        long directOpenTime = pendingOrder.getOpenTime();
        if (directOpenTime > 0L) {
            return directOpenTime;
        }
        if (trades != null && !trades.isEmpty()) {
            long byOrderId = findTradeOpenTimeByOrderId(pendingOrder.getOrderId(), pendingOrder.getPendingPrice(), trades);
            if (byOrderId > 0L) {
                return byOrderId;
            }
            long byPositionId = findTradeOpenTimeByPositionId(pendingOrder.getPositionTicket(), pendingOrder.getPendingPrice(), trades);
            if (byPositionId > 0L) {
                return byPositionId;
            }
        }
        return 0L;
    }

    private long findTradeOpenTimeByPositionId(long positionId, double targetPrice, List<TradeRecordItem> trades) {
        if (positionId <= 0L || trades == null || trades.isEmpty()) {
            return 0L;
        }
        double bestScore = Double.MAX_VALUE;
        long bestTime = 0L;
        for (TradeRecordItem trade : trades) {
            if (trade == null || trade.getPositionId() != positionId) {
                continue;
            }
            if (!matchesSelectedSymbol(trade.getCode(), trade.getProductName())) {
                continue;
            }
            long openTime = resolveTradeOpenTime(trade);
            if (openTime <= 0L) {
                continue;
            }
            double score = priceDistance(resolveTradeOpenPrice(trade), targetPrice);
            if (score < bestScore || (Math.abs(score - bestScore) < 1e-9 && openTime > bestTime)) {
                bestScore = score;
                bestTime = openTime;
            }
        }
        return bestTime;
    }

    private long findTradeOpenTimeByOrderId(long orderId, double targetPrice, List<TradeRecordItem> trades) {
        if (orderId <= 0L || trades == null || trades.isEmpty()) {
            return 0L;
        }
        double bestScore = Double.MAX_VALUE;
        long bestTime = 0L;
        for (TradeRecordItem trade : trades) {
            if (trade == null || trade.getOrderId() != orderId) {
                continue;
            }
            if (!matchesSelectedSymbol(trade.getCode(), trade.getProductName())) {
                continue;
            }
            long openTime = resolveTradeOpenTime(trade);
            if (openTime <= 0L) {
                continue;
            }
            double score = priceDistance(resolveTradeOpenPrice(trade), targetPrice);
            if (score < bestScore || (Math.abs(score - bestScore) < 1e-9 && openTime > bestTime)) {
                bestScore = score;
                bestTime = openTime;
            }
        }
        return bestTime;
    }

    private long resolveTradeOpenTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        return trade.getOpenTime();
    }

    private double resolveTradeOpenPrice(TradeRecordItem trade) {
        if (trade == null) {
            return 0d;
        }
        return trade.getOpenPrice();
    }

    private double priceDistance(double left, double right) {
        if (left <= 0d || right <= 0d) {
            return Double.MAX_VALUE / 4d;
        }
        return Math.abs(left - right) / Math.max(1d, Math.abs(right));
    }

    private boolean matchesSelectedSymbol(String code, String productName) {
        String normalizedSelected = MarketChartTradeSupport.toTradeSymbol(selectedSymbol);
        if (normalizedSelected == null || normalizedSelected.trim().isEmpty()) {
            return false;
        }
        String selected = normalizedSelected.trim().toUpperCase(Locale.ROOT);
        String normalizedCode = MarketChartTradeSupport.toTradeSymbol(code);
        return normalizedCode != null && selected.equals(normalizedCode.trim().toUpperCase(Locale.ROOT));
    }

    private String normalizeTradeSideLabel(String side) {
        if (side == null) {
            return "BUY";
        }
        String normalized = side.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("sell") || normalized.contains("卖")) {
            return "SELL";
        }
        return "BUY";
    }

    private boolean isSellSide(String side) {
        return "SELL".equals(normalizeTradeSideLabel(side));
    }

    private String formatQuantity(double quantity) {
        return qtyFormat.format(Math.max(0d, quantity));
    }

    private String formatSignedUsd(double value) {
        String sign = value >= 0d ? "+" : "-";
        return sign + "$" + pnlFormat.format(Math.abs(value));
    }

    // 当前持仓/挂单没有真实开仓时间时只显示占位，避免伪造时间。
    private String formatPositionOpenTime(long openTimeMs) {
        if (openTimeMs <= 0L) {
            return "--";
        }
        return FormatUtils.formatDateTime(openTimeMs);
    }

    // 当前持仓/挂单的 TP/SL 为空时统一显示占位。
    private String formatOptionalPrice(double value) {
        if (value <= 0d) {
            return "--";
        }
        return "$" + FormatUtils.formatPrice(value);
    }

    private String buildAnnotationGroupId(String type, PositionItem item, long anchorTime, double price) {
        String safeType = type == null ? "order" : type;
        if (item == null) {
            return "";
        }
        if (item.getPositionTicket() > 0L) {
            return safeType + "|position|" + item.getPositionTicket();
        }
        if (item.getOrderId() > 0L) {
            return safeType + "|order|" + item.getOrderId();
        }
        return "";
    }

    private void appendTpSlAnnotations(List<KlineChartView.PriceAnnotation> output,
                                       long anchorTime,
                                       double takeProfit,
                                       double stopLoss,
                                       int tpColor,
                                       int slColor,
                                       String groupId) {
        if (output == null) {
            return;
        }
        if (takeProfit > 0d) {
            output.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    takeProfit,
                    "TP $" + FormatUtils.formatPrice(takeProfit),
                    tpColor,
                    groupId));
        }
        if (stopLoss > 0d) {
            output.add(new KlineChartView.PriceAnnotation(
                    anchorTime,
                    stopLoss,
                    "SL $" + FormatUtils.formatPrice(stopLoss),
                    slColor,
                    groupId));
        }
    }

    private boolean updateScrollToLatestButtonPosition() {
        if (binding == null || binding.btnScrollToLatest == null || binding.klineChartView == null) {
            return false;
        }
        if (pricePaneRightPx <= 0 || pricePaneBottomPx <= 0) {
            return false;
        }
        int buttonWidth = binding.btnScrollToLatest.getWidth();
        int buttonHeight = binding.btnScrollToLatest.getHeight();
        if (buttonWidth <= 0 || buttonHeight <= 0) {
            android.view.ViewGroup.LayoutParams rawParams = binding.btnScrollToLatest.getLayoutParams();
            if (rawParams != null) {
                if (buttonWidth <= 0 && rawParams.width > 0) {
                    buttonWidth = rawParams.width;
                }
                if (buttonHeight <= 0 && rawParams.height > 0) {
                    buttonHeight = rawParams.height;
                }
            }
            if (buttonWidth <= 0 || buttonHeight <= 0) {
                binding.btnScrollToLatest.post(() -> updateScrollToLatestButtonPosition());
                return false;
            }
        }
        int inset = dpToPx(2f);
        int targetLeft = Math.max(0, pricePaneRightPx - buttonWidth - inset);
        int targetTop = Math.max(0, pricePaneBottomPx - buttonHeight - inset);
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) binding.btnScrollToLatest.getLayoutParams();
        int targetGravity = Gravity.TOP | Gravity.START;
        if (params.gravity == targetGravity
                && params.leftMargin == targetLeft
                && params.topMargin == targetTop
                && params.rightMargin == 0
                && params.bottomMargin == 0) {
            return true;
        }
        params.gravity = targetGravity;
        params.leftMargin = targetLeft;
        params.topMargin = targetTop;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        binding.btnScrollToLatest.setLayoutParams(params);
        return true;
    }

    // 将倒计时固定在K线价格绘图区内右上角，不受外层容器留白影响。
    private boolean updateRefreshCountdownPosition() {
        if (binding == null || binding.tvChartRefreshCountdown == null || binding.klineChartView == null) {
            return false;
        }
        if (pricePaneRightPx <= 0 || pricePaneBottomPx <= 0) {
            return false;
        }
        int viewWidth = binding.tvChartRefreshCountdown.getWidth();
        int viewHeight = binding.tvChartRefreshCountdown.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            binding.tvChartRefreshCountdown.post(this::updateRefreshCountdownPosition);
            return false;
        }
        int inset = dpToPx(2f);
        int targetLeft = Math.max(pricePaneLeftPx, pricePaneRightPx - viewWidth - inset);
        int targetTop = Math.max(0, pricePaneTopPx + inset);
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) binding.tvChartRefreshCountdown.getLayoutParams();
        int targetGravity = Gravity.TOP | Gravity.START;
        if (params.gravity == targetGravity
                && params.leftMargin == targetLeft
                && params.topMargin == targetTop
                && params.rightMargin == 0
                && params.bottomMargin == 0) {
            return true;
        }
        params.gravity = targetGravity;
        params.leftMargin = targetLeft;
        params.topMargin = targetTop;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        binding.tvChartRefreshCountdown.setLayoutParams(params);
        return true;
    }

    private int dpToPx(float dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void syncSymbolSelector() {
        SpinnerAdapter adapter = binding.spinnerSymbolPicker.getAdapter();
        if (adapter == null) {
            return;
        }
        String target = selectedSymbol == null ? "" : selectedSymbol.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item == null) {
                continue;
            }
            String symbol = String.valueOf(item).trim().toUpperCase(Locale.ROOT);
            if (!symbol.equals(target)) {
                continue;
            }
            if (binding.spinnerSymbolPicker.getSelectedItemPosition() != i) {
                binding.spinnerSymbolPicker.setSelection(i, false);
            }
            updateChartSymbolPickerLabel(symbol);
            return;
        }
        updateChartSymbolPickerLabel(target);
    }

    private void updateChartSymbolPickerLabel(String symbol) {
        if (binding == null || binding.tvChartSymbolPickerLabel == null) {
            return;
        }
        String text = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty()) {
            text = AppConstants.SYMBOL_BTC;
        }
        binding.tvChartSymbolPickerLabel.setText(text);
        applyChartSymbolPickerIndicator();
    }

    // 给顶部产品标签补上下拉箭头，避免像普通文本一样不明显。
    private void applyChartSymbolPickerIndicator() {
        if (binding == null || binding.tvChartSymbolPickerLabel == null) {
            return;
        }
        Drawable arrow = ContextCompat.getDrawable(this, R.drawable.ic_spinner_arrow);
        if (arrow == null) {
            return;
        }
        Drawable tintedArrow = DrawableCompat.wrap(arrow.mutate());
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        DrawableCompat.setTint(tintedArrow, palette.textSecondary);
        binding.tvChartSymbolPickerLabel.setCompoundDrawablePadding(dpToPx(6f));
        binding.tvChartSymbolPickerLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, tintedArrow, null);
    }

    private void updateIntervalButtons() {
        styleTabButton(binding.btnInterval1m, selectedInterval == INTERVALS[0]);
        styleTabButton(binding.btnInterval5m, selectedInterval == INTERVALS[1]);
        styleTabButton(binding.btnInterval15m, selectedInterval == INTERVALS[2]);
        styleTabButton(binding.btnInterval30m, selectedInterval == INTERVALS[3]);
        styleTabButton(binding.btnInterval1h, selectedInterval == INTERVALS[4]);
        styleTabButton(binding.btnInterval4h, selectedInterval == INTERVALS[5]);
        styleTabButton(binding.btnInterval1d, selectedInterval == INTERVALS[6]);
        styleTabButton(binding.btnInterval1w, selectedInterval == INTERVALS[7]);
        styleTabButton(binding.btnInterval1mo, selectedInterval == INTERVALS[8]);
        styleTabButton(binding.btnInterval1y, selectedInterval == INTERVALS[9]);
    }

    // 恢复上次退出前的K线周期，避免每次重进都回到默认值。
    private void restoreSelectedInterval() {
        SharedPreferences preferences = getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE);
        String resolvedKey = MarketChartRuntimeHelper.resolveStoredIntervalKey(
                preferences.getString(PREF_KEY_SELECTED_INTERVAL, selectedInterval.key),
                selectedInterval.key,
                resolveSupportedIntervalKeys()
        );
        IntervalOption option = findIntervalOptionByKey(resolvedKey);
        if (option != null) {
            selectedInterval = option;
        }
    }

    // 恢复历史成交叠加层显示开关，避免用户每次重进都重新设置。
    private void restoreHistoryTradeVisibility() {
        SharedPreferences preferences = getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE);
        showHistoryTrades = preferences.getBoolean(PREF_KEY_SHOW_HISTORY_TRADES, true);
    }

    // 恢复图上持仓相关标注显示开关，避免用户每次重进都重新设置。
    private void restorePositionOverlayVisibility() {
        SharedPreferences preferences = getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE);
        showPositionOverlays = preferences.getBoolean(PREF_KEY_SHOW_POSITION_OVERLAYS, true);
    }

    // 恢复持仓明细排序选项，避免每次进入都回到默认排序。
    private void restoreChartPositionSort() {
        SharedPreferences preferences = getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE);
        selectedPositionSort = MarketChartPositionSortHelper.fromStoredValue(
                preferences.getString(PREF_KEY_POSITION_SORT, MarketChartPositionSortHelper.SortOption.OPEN_TIME_DESC.name()));
    }

    // 持久化当前选中的K线周期，供下次进入时恢复。
    private void persistSelectedInterval() {
        getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_SELECTED_INTERVAL, selectedInterval.key)
                .apply();
    }

    // 持久化历史成交叠加层显示开关。
    private void persistHistoryTradeVisibility() {
        getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_SHOW_HISTORY_TRADES, showHistoryTrades)
                .apply();
    }

    // 持久化图上持仓相关标注显示开关。
    private void persistPositionOverlayVisibility() {
        getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_SHOW_POSITION_OVERLAYS, showPositionOverlays)
                .apply();
    }

    // 持久化持仓明细排序规则，保证下次进入仍沿用用户上一次选择。
    private void persistChartPositionSort() {
        getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_POSITION_SORT, selectedPositionSort.name())
                .apply();
    }

    // 切换 K 线图历史成交叠加层显示状态。
    private void toggleHistoryTradeVisibility() {
        showHistoryTrades = !showHistoryTrades;
        persistHistoryTradeVisibility();
        applyPrivacyMaskState();
    }

    // 切换 K 线图当前仓位相关标注显示状态，不影响历史成交和下方持仓面板。
    private void togglePositionOverlayVisibility() {
        showPositionOverlays = !showPositionOverlays;
        persistPositionOverlayVisibility();
        applyPrivacyMaskState();
    }

    private void updatePositionOverlayToggleButton() {
        if (binding == null || binding.btnTogglePositionOverlays == null) {
            return;
        }
        binding.btnTogglePositionOverlays.setText(showPositionOverlays
                ? R.string.chart_position_overlays_on
                : R.string.chart_position_overlays_off);
        UiPaletteManager.styleInlineTextButton(
                binding.btnTogglePositionOverlays,
                showPositionOverlays,
                UiPaletteManager.resolve(this),
                10f
        );
        updatePositionOverlayButtonPosition();
    }

    private void updateHistoryTradeToggleButton() {
        if (binding == null || binding.btnToggleHistoryTrades == null) {
            return;
        }
        binding.btnToggleHistoryTrades.setText(showHistoryTrades
                ? R.string.chart_history_trades_on
                : R.string.chart_history_trades_off);
        UiPaletteManager.styleInlineTextButton(
                binding.btnToggleHistoryTrades,
                showHistoryTrades,
                UiPaletteManager.resolve(this),
                10f
        );
        updateHistoryTradeButtonPosition();
    }

    private boolean updateHistoryTradeButtonPosition() {
        if (binding == null || binding.btnToggleHistoryTrades == null) {
            return false;
        }
        int buttonWidth = binding.btnToggleHistoryTrades.getWidth();
        int buttonHeight = binding.btnToggleHistoryTrades.getHeight();
        if (buttonWidth <= 0 || buttonHeight <= 0) {
            ViewGroup.LayoutParams rawParams = binding.btnToggleHistoryTrades.getLayoutParams();
            if (rawParams != null) {
                if (buttonWidth <= 0 && rawParams.width > 0) {
                    buttonWidth = rawParams.width;
                }
                if (buttonHeight <= 0 && rawParams.height > 0) {
                    buttonHeight = rawParams.height;
                }
            }
            if (buttonWidth <= 0 || buttonHeight <= 0) {
                binding.btnToggleHistoryTrades.post(this::updateHistoryTradeButtonPosition);
                return false;
            }
        }
        KlineOverlayButtonLayoutHelper.Bounds priceBounds = new KlineOverlayButtonLayoutHelper.Bounds(
                pricePaneLeftPx,
                pricePaneTopPx,
                pricePaneRightPx,
                pricePaneBottomPx
        );
        KlineOverlayButtonLayoutHelper.Position position =
                KlineOverlayButtonLayoutHelper.resolveBottomLeftStackedButtonPosition(
                        priceBounds,
                        buttonWidth,
                        buttonHeight,
                        dpToPx(2f),
                        0,
                        dpToPx(4f)
                );
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) binding.btnToggleHistoryTrades.getLayoutParams();
        int targetGravity = Gravity.TOP | Gravity.START;
        if (params.gravity == targetGravity
                && params.leftMargin == position.left
                && params.topMargin == position.top
                && params.rightMargin == 0
                && params.bottomMargin == 0) {
            return true;
        }
        params.gravity = targetGravity;
        params.leftMargin = position.left;
        params.topMargin = position.top;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        binding.btnToggleHistoryTrades.setLayoutParams(params);
        return true;
    }

    private boolean updatePositionOverlayButtonPosition() {
        if (binding == null || binding.btnTogglePositionOverlays == null) {
            return false;
        }
        int buttonWidth = binding.btnTogglePositionOverlays.getWidth();
        int buttonHeight = binding.btnTogglePositionOverlays.getHeight();
        if (buttonWidth <= 0 || buttonHeight <= 0) {
            ViewGroup.LayoutParams rawParams = binding.btnTogglePositionOverlays.getLayoutParams();
            if (rawParams != null) {
                if (buttonWidth <= 0 && rawParams.width > 0) {
                    buttonWidth = rawParams.width;
                }
                if (buttonHeight <= 0 && rawParams.height > 0) {
                    buttonHeight = rawParams.height;
                }
            }
            if (buttonWidth <= 0 || buttonHeight <= 0) {
                binding.btnTogglePositionOverlays.post(this::updatePositionOverlayButtonPosition);
                return false;
            }
        }
        KlineOverlayButtonLayoutHelper.Bounds priceBounds = new KlineOverlayButtonLayoutHelper.Bounds(
                pricePaneLeftPx,
                pricePaneTopPx,
                pricePaneRightPx,
                pricePaneBottomPx
        );
        KlineOverlayButtonLayoutHelper.Position position =
                KlineOverlayButtonLayoutHelper.resolveBottomLeftStackedButtonPosition(
                        priceBounds,
                        buttonWidth,
                        buttonHeight,
                        dpToPx(2f),
                        1,
                        dpToPx(4f)
                );
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) binding.btnTogglePositionOverlays.getLayoutParams();
        int targetGravity = Gravity.TOP | Gravity.START;
        if (params.gravity == targetGravity
                && params.leftMargin == position.left
                && params.topMargin == position.top
                && params.rightMargin == 0
                && params.bottomMargin == 0) {
            return true;
        }
        params.gravity = targetGravity;
        params.leftMargin = position.left;
        params.topMargin = position.top;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        binding.btnTogglePositionOverlays.setLayoutParams(params);
        return true;
    }

    // 输出当前页面支持的全部周期键，供运行态恢复时校验。
    private String[] resolveSupportedIntervalKeys() {
        String[] keys = new String[INTERVALS.length];
        for (int i = 0; i < INTERVALS.length; i++) {
            keys[i] = INTERVALS[i].key;
        }
        return keys;
    }

    // 根据周期键回查具体的周期配置。
    private IntervalOption findIntervalOptionByKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        for (IntervalOption option : INTERVALS) {
            if (option != null && key.equalsIgnoreCase(option.key)) {
                return option;
            }
        }
        return null;
    }

    private void updateIndicatorButtons() {
        styleTabButton(binding.btnIndicatorVolume, showVolume);
        styleTabButton(binding.btnIndicatorMacd, showMacd);
        styleTabButton(binding.btnIndicatorStochRsi, showStochRsi);
        styleTabButton(binding.btnIndicatorBoll, showBoll);
        styleTabButton(binding.btnIndicatorMa, showMa);
        styleTabButton(binding.btnIndicatorEma, showEma);
        styleTabButton(binding.btnIndicatorSra, showSra);
        styleTabButton(binding.btnIndicatorAvl, showAvl);
        styleTabButton(binding.btnIndicatorRsi, showRsi);
        styleTabButton(binding.btnIndicatorKdj, showKdj);
    }

    private void styleTabButton(Button button, boolean selected) {
        UiPaletteManager.styleInlineTextButton(
                button,
                selected,
                UiPaletteManager.resolve(this),
                isIntervalButton(button) ? 11f : 10f
        );
    }

    private void normalizeOptionButtons() {
        Button[] buttons = new Button[]{
                binding.btnInterval1m, binding.btnInterval5m, binding.btnInterval15m, binding.btnInterval30m,
                binding.btnInterval1h, binding.btnInterval4h, binding.btnInterval1d, binding.btnInterval1w,
                binding.btnInterval1mo, binding.btnInterval1y,
                binding.btnIndicatorVolume, binding.btnIndicatorMacd, binding.btnIndicatorStochRsi, binding.btnIndicatorBoll,
                binding.btnIndicatorMa, binding.btnIndicatorEma, binding.btnIndicatorSra,
                binding.btnIndicatorAvl, binding.btnIndicatorRsi, binding.btnIndicatorKdj,
                binding.btnTogglePositionOverlays,
                binding.btnToggleHistoryTrades,
                binding.btnChartTradeBuy, binding.btnChartTradeSell, binding.btnChartTradePending
        };
        for (Button button : buttons) {
            if (button == null) {
                continue;
            }
            button.setIncludeFontPadding(false);
            button.setMinHeight(0);
            button.setGravity(Gravity.CENTER);
            button.setPadding(button.getPaddingLeft(), 0, button.getPaddingRight(), 0);
        }
    }

    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        binding.klineChartView.applyPalette(palette);
        binding.cardSymbolPanel.setBackground(UiPaletteManager.createFilledDrawable(this, palette.card));
        binding.cardChartPanel.setBackground(UiPaletteManager.createFilledDrawable(this, palette.card));
        binding.cardChartPositions.setBackground(UiPaletteManager.createFilledDrawable(this, palette.card));
        binding.spinnerSymbolPicker.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.control, palette.stroke));
        binding.spinnerChartPositionSort.setBackgroundColor(Color.TRANSPARENT);
        applyStripBackground(binding.btnInterval1m, palette);
        applyStripBackground(binding.btnIndicatorVolume, palette);
        applyStripBackground(binding.btnChartTradeBuy, palette);
        binding.tvChartSymbolPickerLabel.setTextColor(palette.textPrimary);
        binding.tvChartPositionSortLabel.setTextColor(palette.textPrimary);
        applyChartSymbolPickerIndicator();
        binding.btnRetryLoad.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
        binding.btnRetryLoad.setTextColor(ContextCompat.getColor(this, R.color.white));
        binding.tvChartState.setTextColor(palette.textSecondary);
        binding.tvChartInfo.setTextColor(palette.textSecondary);
        binding.tvChartLoading.setTextColor(palette.textSecondary);
        binding.tvChartRefreshCountdown.setTextColor(palette.textSecondary);
        binding.tvChartRefreshCountdown.setBackground(null);
        binding.tvError.setTextColor(palette.fall);
        binding.tvChartPositionTitle.setTextColor(palette.textPrimary);
        binding.tvChartPositionSummary.setTextColor(palette.textPrimary);
        binding.tvChartPositionAggregateTitle.setTextColor(palette.textPrimary);
        binding.tvChartPositionDetailTitle.setTextColor(palette.textPrimary);
        binding.tvChartPendingOrdersTitle.setTextColor(palette.textPrimary);
        if (binding.tvChartPositionAggregateEmpty != null) {
            binding.tvChartPositionAggregateEmpty.setTextColor(palette.textSecondary);
        }
        if (binding.tvChartPositionsEmpty != null) {
            binding.tvChartPositionsEmpty.setTextColor(palette.textSecondary);
        }
        binding.btnScrollToLatest.setBackground(UiPaletteManager.createOutlinedDrawable(
                this,
                ColorUtils.setAlphaComponent(palette.card, 224),
                ColorUtils.setAlphaComponent(palette.stroke, 220)));
        binding.btnScrollToLatest.setImageTintList(ColorStateList.valueOf(palette.textPrimary));
        if (binding.tvChartPendingOrdersEmpty != null) {
            binding.tvChartPendingOrdersEmpty.setTextColor(palette.textSecondary);
        }
        if (binding.spinnerSymbolPicker.getAdapter() instanceof BaseAdapter) {
            ((BaseAdapter) binding.spinnerSymbolPicker.getAdapter()).notifyDataSetChanged();
        }
        if (binding.spinnerChartPositionSort.getAdapter() instanceof BaseAdapter) {
            ((BaseAdapter) binding.spinnerChartPositionSort.getAdapter()).notifyDataSetChanged();
        }
        updateBottomTabs(false, true, false, false);
        syncSymbolSelector();
        syncChartPositionSortSelection();
        updateIntervalButtons();
        updateIndicatorButtons();
        updateTradeActionButtons();
        updatePositionOverlayToggleButton();
        updateHistoryTradeToggleButton();
        applyPrivacyMaskState();
    }

    // 图表页交易按钮保持和横向轻量按钮一致的样式层级。
    private void updateTradeActionButtons() {
        styleTabButton(binding.btnChartTradeBuy, false);
        styleTabButton(binding.btnChartTradeSell, false);
        styleTabButton(binding.btnChartTradePending, false);
    }

    private void openMarketMonitor() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openAccountStats() {
        Intent intent = new Intent(this, AccountStatsBridgeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void sendServiceAction(String action) {
        Intent intent = new Intent(this, MonitorService.class);
        intent.setAction(action);
        ContextCompat.startForegroundService(this, intent);
    }

    // 首次创建图表页时确保监控服务已启动，避免悬浮窗直达时主链尚未建立。
    private void ensureMonitorServiceStarted() {
        if (MonitorService.isServiceRunning()) {
            return;
        }
        sendServiceAction(AppConstants.ACTION_BOOTSTRAP);
    }
}

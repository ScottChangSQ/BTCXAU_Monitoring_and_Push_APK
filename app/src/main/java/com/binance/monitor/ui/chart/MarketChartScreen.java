/*
 * 行情图表页，负责展示 K 线、指标、异常交易标注和轻量账户叠加信息。
 * 与行情接口、账户快照预加载、主题系统和异常记录模块协同工作。
 */
package com.binance.monitor.ui.chart;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.ColorRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.GridLayoutManager;
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
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeTemplate;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.data.remote.v2.GatewayV2Client;
import com.binance.monitor.data.remote.v2.GatewayV2TradeClient;
import com.binance.monitor.databinding.ActivityMarketChartBinding;
import com.binance.monitor.databinding.DialogTradeCommandBinding;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;
import com.binance.monitor.runtime.ui.PageBootstrapSnapshot;
import com.binance.monitor.runtime.ui.PageBootstrapState;
import com.binance.monitor.runtime.ui.PageBootstrapStateMachine;
import com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStore;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.ui.account.AccountPositionActivity;
import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.ui.account.AccountValueStyleHelper;
import com.binance.monitor.ui.rules.IndicatorId;
import com.binance.monitor.ui.rules.IndicatorPresentationPolicy;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.ui.account.adapter.PendingOrderAdapter;
import com.binance.monitor.ui.account.adapter.PositionAdapterV2;
import com.binance.monitor.ui.account.adapter.PositionAggregateAdapter;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.ui.chart.MarketChartPageController;
import com.binance.monitor.ui.chart.MarketChartPageRuntime;
import com.binance.monitor.ui.chart.runtime.ChartRefreshBudget;
import com.binance.monitor.ui.chart.runtime.ChartRefreshEvent;
import com.binance.monitor.ui.chart.runtime.ChartRefreshScheduler;
import com.binance.monitor.ui.host.GlobalStatusBottomSheetController;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.SpacingTokenResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.ui.trade.TradeCommandFactory;
import com.binance.monitor.ui.trade.TradeCommandStateMachine;
import com.binance.monitor.ui.trade.TradeConfirmDialogController;
import com.binance.monitor.ui.trade.BatchTradeCoordinator;
import com.binance.monitor.ui.trade.TradeAuditStore;
import com.binance.monitor.ui.trade.TradeExecutionCoordinator;
import com.binance.monitor.ui.trade.TradeRiskGuard;
import com.binance.monitor.ui.trade.TradeTemplateRepository;
import com.binance.monitor.service.MonitorServiceController;
import com.binance.monitor.util.ChainLatencyTracer;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.ProductSymbolMapper;
import com.binance.monitor.util.SensitiveDisplayMasker;
import com.google.android.material.button.MaterialButton;
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

final class MarketChartScreen extends android.view.ContextThemeWrapper {

    public static final String EXTRA_TARGET_SYMBOL = "extra_target_symbol";
    public static final String EXTRA_TRADE_ACTION = "extra_trade_action";
    public static final String EXTRA_TRADE_POSITION_TICKET = "extra_trade_position_ticket";
    public static final String EXTRA_TRADE_ORDER_TICKET = "extra_trade_order_ticket";
    public static final String EXTRA_TRADE_ACTION_CLOSE_POSITION = "close_position";
    public static final String EXTRA_TRADE_ACTION_MODIFY_POSITION = "modify_position";
    public static final String EXTRA_TRADE_ACTION_MODIFY_PENDING = "modify_pending";
    public static final String EXTRA_TRADE_ACTION_CANCEL_PENDING = "cancel_pending";
    public static final String PREF_RUNTIME_NAME = "market_chart_runtime";
    private static final String PREF_KEY_SELECTED_INTERVAL = "selected_interval";
    private static final String PREF_KEY_SHOW_HISTORY_TRADES = "show_history_trades";
    private static final String PREF_KEY_SHOW_POSITION_OVERLAYS = "show_position_overlays";
    private static final String PREF_KEY_CHART_CACHE_SCHEMA_VERSION = "chart_cache_schema_version";
    private static final int CHART_CACHE_SCHEMA_VERSION = 2;

    private static final int HISTORY_PERSIST_LIMIT = 5_000;
    private static final int RESTORE_WINDOW_LIMIT = 500;
    private static final int HISTORY_PAGE_LIMIT = 500;
    private static final int GAP_FILL_MAX_ROUNDS = 8;

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
    private AppCompatActivity activity;
    private final LifecycleOwner lifecycleOwner;


    MarketChartScreen(@NonNull AppCompatActivity activity,
                      @NonNull LifecycleOwner lifecycleOwner,
                      @NonNull ActivityMarketChartBinding binding) {
        super(activity, activity.getTheme());
        this.activity = activity;
        this.lifecycleOwner = lifecycleOwner;
        this.binding = binding;
    }

    void initialize() {
        MonitorServiceController.ensureStarted(activity);
        gatewayV2Client = new GatewayV2Client(activity);
        chartSeriesLoaderHelper = new MarketChartSeriesLoaderHelper();
        realtimeTailHelper = new MarketChartRealtimeTailHelper();
        gatewayV2TradeClient = new GatewayV2TradeClient(activity);
        monitorRepository = MonitorRepository.getInstance(getApplicationContext());
        ioExecutor = Executors.newFixedThreadPool(2);
        chartHistoryRepository = new ChartHistoryRepository(activity);
        accountStorageRepository = new AccountStorageRepository(getApplicationContext());
        accountStatsPreloadManager = AccountStatsPreloadManager.getInstance(getApplicationContext());
        abnormalRecordManager = AbnormalRecordManager.getInstance(getApplicationContext());
        secureSessionPrefs = new SecureSessionPrefs(getApplicationContext());
        globalStatusBottomSheetController = new GlobalStatusBottomSheetController(activity);
        tradeTemplateRepository = new TradeTemplateRepository(getApplicationContext());
        tradeExecutionCoordinator = createTradeExecutionCoordinator();
        batchTradeCoordinator = createBatchTradeCoordinator();
        tradeDialogCoordinator = new MarketChartTradeDialogCoordinator(
                activity,
                binding,
                mainHandler,
                ioExecutor,
                accountStatsPreloadManager,
                tradeExecutionCoordinator,
                batchTradeCoordinator,
                tradeTemplateRepository,
                () -> selectedSymbol,
                () -> loadedCandles,
                () -> {
                    if (dataCoordinator != null) {
                        dataCoordinator.refreshChartOverlays();
                    }
                }
        );
        chartQuickTradeCoordinator = new ChartQuickTradeCoordinator(
                this::resolveQuickTradeAccountId,
                () -> MarketChartTradeSupport.toTradeSymbol(selectedSymbol),
                this::resolveQuickTradeCurrentPrice,
                this::executeQuickTradeCommand
        );
        dataCoordinator = new MarketChartDataCoordinator(createDataCoordinatorHost());
    }

    void bindPageContent() {
        setupGlobalStatusButton();
        observeStatusSources();
        setupChart();
        dataCoordinator.observeRealtimeDisplayKlines();
        setupSymbolSelector();
        setupIntervalButtons();
        setupIndicatorButtons();
        setupChartPositionPanel();
        normalizeOptionButtons();
        binding.btnRetryLoad.setOnClickListener(v -> requestKlines());
    }

    void restoreChartOverlayFromLatestCache() {
        if (dataCoordinator != null) {
            dataCoordinator.restoreChartOverlayFromLatestCache();
        }
    }

    void requestKlines() {
        if (dataCoordinator != null) {
            dataCoordinator.requestKlines(true, false);
        }
    }

    void requestColdStartKlines() {
        if (dataCoordinator != null) {
            dataCoordinator.requestColdStartKlines();
        }
    }

    void requestResumeKlines() {
        if (dataCoordinator != null) {
            dataCoordinator.requestResumeKlines();
        }
    }

    void requestSelectionChangeKlines() {
        if (dataCoordinator != null) {
            dataCoordinator.requestSelectionChangeKlines();
        }
    }

    void refreshChartOverlays() {
        if (dataCoordinator != null) {
            dataCoordinator.refreshChartOverlays();
        }
    }

    void cancelTradeTasks() {
        if (tradeDialogCoordinator != null) {
            tradeDialogCoordinator.cancelTradeTasks();
        }
    }

    void requestAutoRefreshKlines() {
        if (dataCoordinator != null) {
            dataCoordinator.requestKlines(false, true);
        }
    }

    @NonNull
    String getSelectedChartSymbol() {
        return selectedSymbol == null ? "" : selectedSymbol;
    }

    @NonNull
    String getAppliedMarketWindowSignature() {
        return lastAppliedMarketWindowSignature;
    }

    long getAppliedMarketWindowUpdatedAt() {
        return lastAppliedMarketWindowUpdatedAtMs;
    }

    @NonNull
    String getCurrentMarketWindowSignature() {
        return resolveCurrentMarketWindowSignature();
    }

    long getAutoRefreshStaleAfterMs() {
        return Math.max(1_000L, resolveAutoRefreshDelayMs());
    }

    void toggleHistoryTradeVisibilityState() {
        showHistoryTrades = !showHistoryTrades;
        persistHistoryTradeVisibility();
    }

    void togglePositionOverlayVisibilityState() {
        showPositionOverlays = !showPositionOverlays;
        persistPositionOverlayVisibility();
    }

    void toggleIndicatorState(@NonNull String indicatorKey) {
        switch (indicatorKey) {
            case MarketChartPageRuntime.INDICATOR_VOLUME:
                showVolume = !showVolume;
                break;
            case MarketChartPageRuntime.INDICATOR_MACD:
                showMacd = !showMacd;
                break;
            case MarketChartPageRuntime.INDICATOR_STOCH_RSI:
                showStochRsi = !showStochRsi;
                break;
            case MarketChartPageRuntime.INDICATOR_BOLL:
                showBoll = !showBoll;
                break;
            case MarketChartPageRuntime.INDICATOR_MA:
                showMa = !showMa;
                break;
            case MarketChartPageRuntime.INDICATOR_EMA:
                showEma = !showEma;
                break;
            case MarketChartPageRuntime.INDICATOR_SRA:
                showSra = !showSra;
                break;
            case MarketChartPageRuntime.INDICATOR_AVL:
                showAvl = !showAvl;
                break;
            case MarketChartPageRuntime.INDICATOR_RSI:
                showRsi = !showRsi;
                break;
            case MarketChartPageRuntime.INDICATOR_KDJ:
                showKdj = !showKdj;
                break;
            default:
                break;
        }
    }

    void notifyIndicatorSelectionChanged() {
        binding.klineChartView.setIndicatorsVisible(showVolume, showMacd, showStochRsi, showBoll);
        binding.klineChartView.setExtendedIndicatorsVisible(showMa, showEma, showSra, showAvl, showRsi, showKdj);
        updateIndicatorButtons();
        dispatchChartRefresh(ChartRefreshEvent.uiStateChanged(), null, null);
        binding.klineChartView.post(this::updateScrollToLatestButtonPosition);
    }

    boolean canApplySelectedSymbol(@NonNull String symbol) {
        return isSupportedSymbol(symbol) && !symbol.equalsIgnoreCase(selectedSymbol);
    }

    void commitSelectedSymbol(@NonNull String symbol) {
        selectedSymbol = symbol;
        syncSymbolSelector();
    }

    boolean canApplySelectedInterval(@NonNull String intervalKey) {
        IntervalOption option = findIntervalOptionByKey(intervalKey);
        return option != null && option != selectedInterval;
    }

    void commitSelectedInterval(@NonNull String intervalKey) {
        IntervalOption option = findIntervalOptionByKey(intervalKey);
        if (option == null) {
            return;
        }
        selectedInterval = option;
        updateIntervalButtons();
    }

    @NonNull
    AppCompatActivity requireActivity() {
        return activity;
    }

    void attachPageRuntime(@NonNull MarketChartPageRuntime runtime) {
        this.pageRuntime = runtime;
    }

    private Intent getIntent() {
        return activity.getIntent();
    }

    void onNewIntent(@NonNull Intent intent) {
        activity.setIntent(intent);
        lastConsumedTradeActionToken = "";
        applyIntentSymbol(intent, true);
        consumePendingTradeActionIfNeeded();
    }

    private boolean isFinishing() {
        return activity.isFinishing();
    }

    private boolean isDestroyed() {
        return activity.isDestroyed();
    }

    private void runOnUiThread(@NonNull Runnable action) {
        activity.runOnUiThread(action);
    }
    private GatewayV2Client gatewayV2Client;
    private MarketChartSeriesLoaderHelper chartSeriesLoaderHelper;
    private MarketChartRealtimeTailHelper realtimeTailHelper;
    private GatewayV2TradeClient gatewayV2TradeClient;
    private MonitorRepository monitorRepository;
    private TradeExecutionCoordinator tradeExecutionCoordinator;
    private BatchTradeCoordinator batchTradeCoordinator;
    private MarketChartTradeDialogCoordinator tradeDialogCoordinator;
    private TradeTemplateRepository tradeTemplateRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private MarketChartPageController pageController;
    private MarketChartPageRuntime pageRuntime;
    private MarketChartDataCoordinator dataCoordinator;
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
    private GlobalStatusBottomSheetController globalStatusBottomSheetController;
    private Future<?> chartCacheInvalidationTask;
    private Future<?> storedChartOverlayRestoreTask;
    private AccountSnapshot storedChartOverlaySnapshot;
    private String storedChartOverlayAccount = "";
    private String storedChartOverlayServer = "";
    private String lastAppliedOverlayAccount = "";
    private String lastAppliedOverlayServer = "";

    private String selectedSymbol = AppConstants.SYMBOL_BTC;
    private ArrayAdapter<String> symbolAdapter;
    private IntervalOption selectedInterval = INTERVALS[2];
    private String activeDataKey = "";
    private String lastAppliedMarketWindowSignature = "";
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
    private boolean showHistoryTrades = false;
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
    @Nullable
    private ChartOverlaySnapshotFactory chartOverlaySnapshotFactory;
    private final UnifiedRuntimeSnapshotStore runtimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();
    private PageBootstrapStateMachine chartBootstrapStateMachine = new PageBootstrapStateMachine();
    private PageBootstrapSnapshot chartBootstrapSnapshot = PageBootstrapSnapshot.initial();
    private boolean chartBlockingLoadingRequested;
    private final ChartRefreshScheduler chartRefreshScheduler = new ChartRefreshScheduler();
    private ChartOverlaySnapshot lastChartOverlaySnapshot = ChartOverlaySnapshot.empty();
    private Future<?> chartOverlayBuildTask;
    private int chartOverlayBuildVersion;
    private int pricePaneLeftPx;
    private int pricePaneTopPx;
    private int pricePaneRightPx;
    private int pricePaneBottomPx;
    private int volumePaneLeftPx;
    private int volumePaneTopPx;
    private int volumePaneRightPx;
    private int volumePaneBottomPx;
    private volatile boolean loadingMore;
    private long lastSuccessUpdateMs;
    private long lastAppliedMarketWindowUpdatedAtMs;
    private long lastSuccessfulRequestLatencyMs = -1L;
    private List<AbnormalRecord> abnormalRecords = new ArrayList<>();
    private boolean statusSourcesObserved;
    private String lastAccountOverlaySignature = "";
    private String lastAbnormalOverlaySignature = "";
    private ChartQuickTradeMode quickTradeMode = ChartQuickTradeMode.CLOSED;
    private double pendingLinePrice = Double.NaN;
    private ChartQuickTradeCoordinator chartQuickTradeCoordinator;
    private final AccountStatsPreloadManager.CacheListener accountCacheListener = cache -> {
        if (pageRuntime != null) {
            pageRuntime.scheduleChartOverlayRefresh();
        }
        consumePendingTradeActionIfNeeded();
    };
    private final MarketChartStartupGate startupGate = new MarketChartStartupGate();
    @Nullable
    private ViewTreeObserver.OnDrawListener pendingStartupDrawListener;
    private String pendingStartupDrawKey = "";
    private String lastConsumedTradeActionToken = "";
    @Nullable
    private KlineData pendingRealtimeTailKline;
    private boolean realtimeTailDrainScheduled;
    @Nullable
    private Runnable pendingOverlayRefreshAction;
    @Nullable
    private Runnable pendingSummaryRefreshAction;
    private boolean overlaySummaryDrainScheduled;
    private boolean dialogDrainScheduled;
    private final Runnable realtimeTailDrainRunnable = new Runnable() {
        @Override
        public void run() {
            realtimeTailDrainScheduled = false;
            String renderToken = chartRefreshScheduler.drainPendingRenderToken();
            KlineData latestKline = pendingRealtimeTailKline;
            pendingRealtimeTailKline = null;
            if (renderToken == null || latestKline == null) {
                return;
            }
            applyRealtimeChartTailNow(latestKline);
        }
    };
    private final Runnable overlaySummaryDrainRunnable = new Runnable() {
        @Override
        public void run() {
            overlaySummaryDrainScheduled = false;
            String overlayToken = chartRefreshScheduler.drainPendingOverlayRenderToken();
            String summaryToken = chartRefreshScheduler.drainPendingSummaryBindToken();
            Runnable overlayAction = pendingOverlayRefreshAction;
            Runnable summaryAction = pendingSummaryRefreshAction;
            pendingOverlayRefreshAction = null;
            pendingSummaryRefreshAction = null;
            if (overlayToken != null && overlayAction != null) {
                overlayAction.run();
            }
            if (summaryToken != null && summaryAction != null) {
                summaryAction.run();
            }
        }
    };
    private final Runnable dialogDrainRunnable = new Runnable() {
        @Override
        public void run() {
            dialogDrainScheduled = false;
            String dialogToken = chartRefreshScheduler.drainPendingDialogBindToken();
            if (dialogToken == null) {
                return;
            }
            applyDialogStateRefresh();
        }
    };

    // 装配图表页数据协调器，把请求入口、实时观察和叠加层主链从旧 Activity 抽离。
    private MarketChartDataCoordinator.Host createDataCoordinatorHost() {
        return new MarketChartDataHostDelegate(new MarketChartDataHostDelegate.Owner() {
            @Override
            public MonitorRepository getMonitorRepository() {
                return monitorRepository;
            }

            @NonNull
            @Override
            public LifecycleOwner getLifecycleOwner() {
                return activity;
            }

            @Override
            public String getSelectedSymbol() {
                return selectedSymbol;
            }

            @NonNull
            @Override
            public MarketChartDataCoordinator.IntervalSelection getSelectedInterval() {
                IntervalOption option = selectedInterval;
                if (option == null) {
                    return new MarketChartDataCoordinator.IntervalSelection("default", "default", RESTORE_WINDOW_LIMIT, false);
                }
                return new MarketChartDataCoordinator.IntervalSelection(
                        option.key,
                        option.apiInterval,
                        option.limit,
                        option.yearAggregate
                );
            }

            @NonNull
            @Override
            public String buildCacheKey(@NonNull String symbol,
                                        @NonNull MarketChartDataCoordinator.IntervalSelection interval) {
                return MarketChartCacheKeyHelper.build(
                        symbol,
                        interval.getKey(),
                        interval.getApiInterval(),
                        interval.isYearAggregate()
                );
            }

            @Override
            public boolean cancelRunningRequestIfNeeded(boolean allowCancelRunning, boolean autoRefresh) {
                if (runningTask != null && !runningTask.isDone()) {
                    if (!allowCancelRunning) {
                        boolean taskStale = autoRefresh && (System.currentTimeMillis() - runningTaskStartMs > 25_000L);
                        if (!taskStale) {
                            return false;
                        }
                    }
                    runningTask.cancel(true);
                    runningTask = null;
                }
                return true;
            }

            @Override
            public List<CandleEntry> getCachedCandles(@NonNull String key) {
                return MarketChartScreen.this.getCachedCandles(key);
            }

            @Override
            public void schedulePersistedCacheRestore(@NonNull String key, boolean applyWhenLoaded) {
                MarketChartScreen.this.schedulePersistedCacheRestore(key, applyWhenLoaded);
            }

            @Override
            public List<CandleEntry> buildWarmDisplayCandles(@NonNull String symbol,
                                                             @NonNull MarketChartDataCoordinator.IntervalSelection targetInterval) {
                return MarketChartScreen.this.buildWarmDisplayCandles(
                        symbol,
                        toIntervalOption(targetInterval)
                );
            }

            @Override
            public void applyLocalDisplayCandles(@NonNull String key, @NonNull List<CandleEntry> candles) {
                MarketChartScreen.this.applyLocalDisplayCandles(key, candles);
            }

            @NonNull
            @Override
            public String getActiveDataKey() {
                return activeDataKey;
            }

            @NonNull
            @Override
            public List<CandleEntry> getLoadedCandles() {
                return loadedCandles;
            }

            @Override
            public long resolveLatestVisibleCandleTime(@Nullable List<CandleEntry> visible) {
                return MarketChartScreen.this.resolveLatestVisibleCandleTime(visible);
            }

            @Override
            public long intervalToMs(@NonNull String key) {
                return MarketChartScreen.this.intervalToMs(key);
            }

            @Override
            public boolean hasRealtimeTailSourceForChart() {
                return MarketChartScreen.this.hasRealtimeTailSourceForChart();
            }

            @Override
            public void applyRequestSkipState(@NonNull MarketChartRefreshHelper.SyncPlan refreshPlan) {
                MarketChartScreen.this.applyRequestSkipState(refreshPlan);
            }

            @Override
            public int nextRequestVersion() {
                return ++requestVersion;
            }

            @Override
            public void applyRequestStartState(boolean autoRefresh) {
                MarketChartScreen.this.applyRequestStartState(autoRefresh);
            }

            @Override
            public void setRunningTaskStartMs(long startedAtMs) {
                runningTaskStartMs = startedAtMs;
            }

            @Override
            public void cancelProgressiveGapFillTask() {
                MarketChartScreen.this.cancelProgressiveGapFillTask();
            }

            @Override
            public void submitRunningTask(@NonNull Runnable action) {
                runningTask = ioExecutor.submit(action);
            }

            @NonNull
            @Override
            public List<CandleEntry> loadCandlesForRequest(@NonNull MarketChartRefreshHelper.SyncPlan plan,
                                                           @Nullable List<CandleEntry> seed,
                                                           long previousLatestOpenTime,
                                                           long previousOldestOpenTime,
                                                           int previousWindowSize,
                                                           @NonNull String symbol,
                                                           @NonNull MarketChartDataCoordinator.IntervalSelection interval) throws Exception {
                return chartSeriesLoaderHelper.loadCandlesForRequest(
                        plan,
                        seed,
                        symbol,
                        interval,
                        RESTORE_WINDOW_LIMIT,
                        seriesGateway()
                );
            }

            @Override
            public void postToMainThread(@NonNull Runnable action) {
                mainHandler.post(action);
            }

            @Override
            public boolean shouldIgnoreRequestResult(int requestVersion) {
                return requestVersion != MarketChartScreen.this.requestVersion || isFinishing() || isDestroyed();
            }

            @Override
            public boolean shouldFollowLatestViewportOnRefresh() {
                return MarketChartScreen.this.shouldFollowLatestViewportOnRefresh();
            }

            @Override
            public void setActiveDataKey(@NonNull String key) {
                activeDataKey = key;
            }

            @Override
            public void applyDisplayCandles(@NonNull String key,
                                            @NonNull List<CandleEntry> candles,
                                            boolean keepViewport,
                                            boolean shouldFollowLatest,
                                            boolean updateMemoryCache) {
                MarketChartScreen.this.applyDisplayCandles(
                        key,
                        candles,
                        keepViewport,
                        shouldFollowLatest,
                        updateMemoryCache
                );
            }

            @Override
            public void persistClosedCandles(@NonNull String key,
                                             @NonNull List<CandleEntry> finalProcessed,
                                             @NonNull String symbol,
                                             @NonNull MarketChartDataCoordinator.IntervalSelection interval) {
                List<CandleEntry> closedPersistenceWindow =
                        ChartPersistenceWindowHelper.retainClosedCandles(finalProcessed, System.currentTimeMillis());
                persistCandlesAsync(key, closedPersistenceWindow, symbol, toIntervalOption(interval), true);
            }

            @Override
            public void startProgressiveGapFill(@NonNull String reqSymbol,
                                                @NonNull MarketChartDataCoordinator.IntervalSelection reqInterval,
                                                int current,
                                                @Nullable List<CandleEntry> visibleWindow,
                                                long previousOldestOpenTime,
                                                int previousWindowSize) {
                MarketChartScreen.this.startProgressiveGapFill(
                        reqSymbol,
                        toIntervalOption(reqInterval),
                        current,
                        visibleWindow,
                        previousOldestOpenTime,
                        previousWindowSize
                );
            }

            @Override
            public void applyRequestSuccessState(boolean autoRefresh, long requestStartedAtMs) {
                MarketChartScreen.this.applyRequestSuccessState(autoRefresh, requestStartedAtMs);
            }

            @Override
            public void applyRequestFailureState(boolean autoRefresh,
                                                boolean deferTrueEmptyUntilStorageRestore,
                                                @NonNull String message) {
                MarketChartScreen.this.applyRequestFailureState(
                        autoRefresh,
                        deferTrueEmptyUntilStorageRestore,
                        message
                );
            }

            @Override
            public boolean beginLoadMore() {
                if (loadingMore || loadedCandles.isEmpty()) {
                    return false;
                }
                loadingMore = true;
                return true;
            }

            @Override
            public void notifyLoadMoreFinished() {
                binding.klineChartView.notifyLoadMoreFinished();
            }

            @Override
            public void cancelLoadMoreTask() {
                if (loadMoreTask != null) {
                    loadMoreTask.cancel(true);
                }
            }

            @Override
            public void submitLoadMoreTask(@NonNull Runnable action) {
                loadMoreTask = ioExecutor.submit(action);
            }

            @NonNull
            @Override
            public List<CandleEntry> fetchV2SeriesBefore(@NonNull String symbol,
                                                         @NonNull MarketChartDataCoordinator.IntervalSelection interval,
                                                         int limit,
                                                         long endTimeInclusive) throws Exception {
                return chartSeriesLoaderHelper.fetchV2SeriesBefore(
                        symbol,
                        interval,
                        limit,
                        endTimeInclusive,
                        seriesGateway()
                );
            }

            @NonNull
            @Override
            public List<CandleEntry> aggregateToYear(@Nullable List<CandleEntry> source, @NonNull String symbol) {
                return chartSeriesLoaderHelper.aggregateToYear(source, symbol);
            }

            @Override
            public boolean shouldIgnoreLoadMoreResult(@NonNull String reqSymbol,
                                                      @NonNull MarketChartDataCoordinator.IntervalSelection reqInterval) {
                return isFinishing()
                        || isDestroyed()
                        || !reqSymbol.equals(selectedSymbol)
                        || !sameInterval(reqInterval, selectedInterval)
                        || loadedCandles.isEmpty();
            }

            @Override
            public void applyLoadMoreSuccessState(@NonNull String reqSymbol,
                                                  @NonNull MarketChartDataCoordinator.IntervalSelection reqInterval,
                                                  @NonNull List<CandleEntry> older) {
                MarketChartScreen.this.applyLoadMoreSuccessState(
                        reqSymbol,
                        toIntervalOption(reqInterval),
                        older
                );
            }

            @Override
            public void finishLoadMoreState() {
                MarketChartScreen.this.finishLoadMoreState();
            }

            @Override
            public int getRestoreWindowLimit() {
                return RESTORE_WINDOW_LIMIT;
            }

            @Override
            public int getHistoryPageLimit() {
                return HISTORY_PAGE_LIMIT;
            }

            @Override
            public void applyRealtimeChartTail(@Nullable KlineData latestKline) {
                MarketChartScreen.this.applyRealtimeChartTail(latestKline);
            }

            @Override
            public void updateVolumeThresholdOverlay() {
                MarketChartScreen.this.updateVolumeThresholdOverlay();
            }

            @Override
            public void updateAccountAnnotationsOverlay() {
                MarketChartScreen.this.updateAccountAnnotationsOverlay();
            }

            @Override
            public void updateAbnormalAnnotationsOverlay() {
                MarketChartScreen.this.updateAbnormalAnnotationsOverlay();
            }

            @Override
            public boolean isChartViewReady() {
                return binding != null && binding.klineChartView != null;
            }

            @Override
            public boolean isAccountSessionActive() {
                return ConfigManager.getInstance(MarketChartScreen.this).isAccountSessionActive();
            }

            @Nullable
            @Override
            public AccountStatsPreloadManager.Cache getLatestAccountCache() {
                return accountStatsPreloadManager == null ? null : accountStatsPreloadManager.getLatestCache();
            }

            @Nullable
            @Override
            public AccountSnapshot resolveChartOverlaySnapshot(boolean sessionActive,
                                                               @Nullable AccountStatsPreloadManager.Cache cache) {
                return MarketChartScreen.this.resolveChartOverlaySnapshot(sessionActive, cache);
            }

            @Override
            public boolean isCurrentOverlayBoundToActiveSession() {
                return MarketChartScreen.this.isCurrentOverlayBoundToActiveSession();
            }

            @Override
            public void clearAccountAnnotationsOverlay() {
                MarketChartScreen.this.clearAccountAnnotationsOverlay();
            }

            @NonNull
            @Override
            public String buildCurrentCacheKey() {
                return buildCacheKey(selectedSymbol, getSelectedInterval());
            }

            @Override
            public boolean shouldDeferOverlayUntilPrimaryDisplay(@NonNull String key) {
                return startupGate.shouldDeferUntilPrimaryDisplay(key);
            }

            @Override
            public void replacePendingOverlay(@NonNull String key, @NonNull Runnable action) {
                startupGate.replacePendingOverlay(key, action);
            }

            @Override
            public void applyChartOverlaySnapshot(@NonNull AccountSnapshot snapshot,
                                                  @Nullable AccountStatsPreloadManager.Cache cache) {
                MarketChartScreen.this.applyChartOverlaySnapshot(snapshot, cache);
            }

            private IntervalOption toIntervalOption(@NonNull MarketChartDataCoordinator.IntervalSelection interval) {
                for (IntervalOption candidate : INTERVALS) {
                    if (candidate != null
                            && candidate.yearAggregate == interval.isYearAggregate()
                            && candidate.limit == interval.getLimit()
                            && interval.getKey().equals(candidate.key)
                            && interval.getApiInterval().equals(candidate.apiInterval)) {
                        return candidate;
                    }
                }
                return selectedInterval == null ? INTERVALS[0] : selectedInterval;
            }

            private boolean sameInterval(@NonNull MarketChartDataCoordinator.IntervalSelection interval,
                                         @Nullable IntervalOption current) {
                return current != null
                        && interval.isYearAggregate() == current.yearAggregate
                        && interval.getLimit() == current.limit
                        && interval.getKey().equals(current.key)
                        && interval.getApiInterval().equals(current.apiInterval);
            }
        });
    }

    private void setupChart() {
        binding.tvChartPositionSummary.setText("盈亏：-- | 持仓：--");
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
        binding.klineChartView.setOnRequestMoreListener(beforeOpenTime -> {
            if (dataCoordinator != null) {
                dataCoordinator.requestMoreHistory(beforeOpenTime);
            }
        });
        binding.klineChartView.setOnPricePaneLayoutListener((left, top, right, bottom) -> {
            pricePaneLeftPx = left;
            pricePaneTopPx = top;
            pricePaneRightPx = right;
            pricePaneBottomPx = bottom;
            updateChartPositionSummaryPosition();
            boolean positioned = updateScrollToLatestButtonPosition();
            if (positioned && binding.klineChartView.isLatestCandleOutOfBounds()) {
                binding.btnScrollToLatest.setVisibility(android.view.View.VISIBLE);
            }
        });
        binding.klineChartView.setOnVolumePaneLayoutListener((left, top, right, bottom) -> {
            volumePaneLeftPx = left;
            volumePaneTopPx = top;
            volumePaneRightPx = right;
            volumePaneBottomPx = bottom;
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
        binding.klineChartView.setOnQuickPendingLineChangeListener(price -> {
            pendingLinePrice = price;
            binding.klineChartView.setTradeLayerSnapshot(buildTradeLayerSnapshot());
        });
        binding.btnScrollToLatest.setOnClickListener(v -> {
            binding.klineChartView.scrollToLatest();
            updateScrollToLatestButtonPosition();
        });
        binding.btnTogglePositionOverlays.setOnClickListener(v -> pageRuntime.togglePositionOverlayVisibility());
        binding.btnToggleHistoryTrades.setOnClickListener(v -> pageRuntime.toggleHistoryTradeVisibility());
        binding.btnScrollToLatest.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateScrollToLatestButtonPosition());
        binding.tvChartPositionSummary.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateChartPositionSummaryPosition());
        binding.klineChartView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
        {
            updateChartPositionSummaryPosition();
            updateScrollToLatestButtonPosition();
        });
        binding.btnScrollToLatest.setVisibility(android.view.View.INVISIBLE);
        updatePositionOverlayToggleButton();
        updateHistoryTradeToggleButton();
        dataCoordinator.refreshChartOverlays();
    }

    // 根据隐私状态控制敏感叠加层和当前持仓模块，不再使用整页遮罩。
    void applyPagePalette() {
        applyPaletteStyles();
    }

    void applyPrivacyMaskState() {
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
        bindChartOverlayStatus(lastChartOverlaySnapshot, masked);
    }

    // 绑定顶部状态入口，把全局状态收口成一个轻按钮。
    private void setupGlobalStatusButton() {
        if (binding == null || binding.btnGlobalStatus == null) {
            return;
        }
        binding.btnGlobalStatus.setOnClickListener(v -> {
            if (globalStatusBottomSheetController == null) {
                return;
            }
            globalStatusBottomSheetController.show(buildGlobalStatusSnapshot());
        });
        refreshGlobalStatusButton();
    }

    // 观察连接、刷新时间和异常记录，保证按钮与图表异常标注走同一套真值。
    private void observeStatusSources() {
        if (statusSourcesObserved) {
            return;
        }
        statusSourcesObserved = true;
        if (monitorRepository != null) {
            monitorRepository.getConnectionStatus().observe(lifecycleOwner, ignored -> dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null));
            monitorRepository.getLastUpdateTime().observe(lifecycleOwner, ignored -> dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null));
            monitorRepository.getMonitoringEnabled().observe(lifecycleOwner, ignored -> dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null));
        }
        if (abnormalRecordManager != null) {
            abnormalRecordManager.getRecordsLiveData().observe(lifecycleOwner, records -> {
                abnormalRecords = records == null ? new ArrayList<>() : new ArrayList<>(records);
                dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);
                updateAbnormalAnnotationsOverlay();
            });
        }
        dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);
    }

    // 构造全局状态快照，供顶部按钮与底部弹层共用。
    @NonNull
    private GlobalStatusBottomSheetController.StatusSnapshot buildGlobalStatusSnapshot() {
        SessionSummarySnapshot sessionSummary = secureSessionPrefs == null
                ? SessionSummarySnapshot.empty()
                : secureSessionPrefs.loadSessionSummary();
        String connectionText = resolveConnectionStatusText();
        long refreshedAt = resolveLatestRefreshTimeMs();
        return new GlobalStatusBottomSheetController.StatusSnapshot(
                resolveCompactConnectionStatusText(connectionText),
                connectionText,
                resolveAccountDisplayText(sessionSummary),
                resolveCompactAccountStatusText(sessionSummary),
                resolveSyncStatusText(connectionText, refreshedAt),
                refreshedAt > 0L
                        ? FormatUtils.formatDateTime(refreshedAt)
                        : getString(R.string.global_status_value_pending),
                abnormalRecords == null ? 0 : abnormalRecords.size(),
                abnormalRecords
        );
    }

    // 刷新状态按钮文案，让一级入口始终显示最新摘要。
    private void refreshGlobalStatusButton() {
        if (binding == null || binding.btnGlobalStatus == null || globalStatusBottomSheetController == null) {
            return;
        }
        globalStatusBottomSheetController.bindCompactButton(binding.btnGlobalStatus, buildGlobalStatusSnapshot());
    }

    // 读取当前连接状态文案，没有真值时统一回退到离线态。
    @NonNull
    private String resolveConnectionStatusText() {
        if (monitorRepository == null) {
            return getString(R.string.connection_disconnected);
        }
        String value = monitorRepository.getConnectionStatus().getValue();
        return value == null || value.trim().isEmpty()
                ? getString(R.string.connection_disconnected)
                : value.trim();
    }

    // 选择当前账户展示名，优先展示活动账户，其次回退到最近草稿账号。
    @NonNull
    private String resolveAccountDisplayText(@NonNull SessionSummarySnapshot sessionSummary) {
        RemoteAccountProfile activeAccount = sessionSummary.getActiveAccount();
        if (activeAccount != null) {
            String displayName = safeTrim(activeAccount.getDisplayName());
            if (!displayName.isEmpty()) {
                return displayName;
            }
            String maskedLogin = safeTrim(activeAccount.getLoginMasked());
            if (!maskedLogin.isEmpty()) {
                return maskedLogin;
            }
            String login = safeTrim(activeAccount.getLogin());
            if (!login.isEmpty()) {
                return login;
            }
        }
        String draftAccount = safeTrim(sessionSummary.getDraftAccount());
        if (!draftAccount.isEmpty()) {
            return draftAccount;
        }
        return getString(R.string.global_status_value_no_account);
    }

    // 用现有连接状态收口成按钮短标签，避免入口文案过长。
    @NonNull
    private String resolveCompactConnectionStatusText(@NonNull String connectionText) {
        if (!isMonitoringEnabled()) {
            return getString(R.string.global_status_stage_paused);
        }
        if (isConnectedStatus(connectionText)) {
            return getString(R.string.global_status_compact_connected);
        }
        if (isRecoveringStatus(connectionText)) {
            return getString(R.string.global_status_stage_recovering);
        }
        return getString(R.string.global_status_compact_disconnected);
    }

    // 状态按钮只展示更短的登录态，给左侧产品名称留出完整显示空间。
    @NonNull
    private String resolveCompactAccountStatusText(@NonNull SessionSummarySnapshot sessionSummary) {
        return sessionSummary.getActiveAccount() == null
                ? getString(R.string.global_status_account_offline)
                : getString(R.string.global_status_compact_logged_in);
    }

    // 在没有独立同步状态源时，按连接状态和最近刷新时间生成同步文案。
    @NonNull
    private String resolveSyncStatusText(@NonNull String connectionText, long refreshedAt) {
        if (!isMonitoringEnabled()) {
            return getString(R.string.global_status_sync_paused);
        }
        if (isConnectedStatus(connectionText)) {
            return getString(R.string.global_status_sync_live);
        }
        if (isRecoveringStatus(connectionText)) {
            return getString(R.string.global_status_sync_recovering);
        }
        if (refreshedAt > 0L) {
            return getString(R.string.global_status_sync_waiting);
        }
        return getString(R.string.global_status_sync_initial);
    }

    // 统一选出最近一次有效刷新时间，避免状态弹层显示旧值。
    private long resolveLatestRefreshTimeMs() {
        long repositoryUpdateAt = 0L;
        if (monitorRepository != null && monitorRepository.getLastUpdateTime().getValue() != null) {
            Long value = monitorRepository.getLastUpdateTime().getValue();
            repositoryUpdateAt = value == null ? 0L : value;
        }
        return Math.max(lastSuccessUpdateMs, repositoryUpdateAt);
    }

    // 读取当前监控开关，供状态按钮与弹层共同判断是否暂停。
    private boolean isMonitoringEnabled() {
        if (monitorRepository == null || monitorRepository.getMonitoringEnabled().getValue() == null) {
            return true;
        }
        Boolean value = monitorRepository.getMonitoringEnabled().getValue();
        return value == null || value;
    }

    // 判断当前连接是否已经进入可用态。
    private boolean isConnectedStatus(@NonNull String connectionText) {
        return connectionText.contains(getString(R.string.connection_connected))
                || connectionText.contains("已连接")
                || connectionText.contains("正常");
    }

    // 判断当前连接是否仍在恢复链路中。
    private boolean isRecoveringStatus(@NonNull String connectionText) {
        return connectionText.contains(getString(R.string.connection_connecting))
                || connectionText.contains(getString(R.string.connection_reconnecting))
                || connectionText.contains("连接中")
                || connectionText.contains("重连");
    }

    // 去掉状态文本中的空白，避免展示层出现空占位。
    @NonNull
    private String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
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
                pageRuntime.requestSymbolSelection(symbol);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        syncSymbolSelector();
    }

    // 产品选择器统一走 SelectField 主体，避免锚点和下拉项继续分家。
    private ArrayAdapter<String> createSymbolAdapter(List<String> symbols) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.item_spinner_filter_anchor,
                android.R.id.text1,
                symbols
        ) {
            @Override
            public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                styleSymbolSelectFieldItem(view, R.style.TextAppearance_BinanceMonitor_Control);
                return view;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                styleSymbolSelectFieldItem(view, R.style.TextAppearance_BinanceMonitor_ControlCompact);
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    // 统一产品选择字段文字样式，不再继续扩散旧 spinner 入口。
    private void styleSymbolSelectFieldItem(@Nullable View view, int textAppearanceResId) {
        if (!(view instanceof TextView)) {
            return;
        }
        TextView textView = (TextView) view;
        UiPaletteManager.styleSelectFieldLabel(
                textView,
                UiPaletteManager.resolve(this),
                textAppearanceResId
        );
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
            pageRuntime.requestChartSelectionReload();
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
        return ProductSymbolMapper.toMarketSymbol(raw);
    }

    @NonNull
    private String resolveTradeAction(@Nullable Intent intent) {
        if (intent == null) {
            return "";
        }
        String raw = intent.getStringExtra(EXTRA_TRADE_ACTION);
        return raw == null ? "" : raw.trim();
    }

    private long resolveTradePositionTicket(@Nullable Intent intent) {
        return intent == null ? 0L : intent.getLongExtra(EXTRA_TRADE_POSITION_TICKET, 0L);
    }

    private long resolveTradeOrderTicket(@Nullable Intent intent) {
        return intent == null ? 0L : intent.getLongExtra(EXTRA_TRADE_ORDER_TICKET, 0L);
    }

    // 消费从账户持仓页转入的交易动作请求，并直接接到图表页既有交易协调器。
    void consumePendingTradeActionIfNeeded() {
        Intent intent = getIntent();
        String action = resolveTradeAction(intent);
        if (action.isEmpty() || tradeDialogCoordinator == null) {
            return;
        }
        String token = action
                + "|"
                + resolveTargetSymbol(intent)
                + "|"
                + resolveTradePositionTicket(intent)
                + "|"
                + resolveTradeOrderTicket(intent);
        if (token.equals(lastConsumedTradeActionToken)) {
            return;
        }
        AccountSnapshot snapshot = resolveTradeTargetSnapshot();
        if (snapshot == null) {
            return;
        }
        PositionItem targetItem = resolveTradeTargetItem(snapshot, intent, action);
        if (targetItem == null) {
            Toast.makeText(this, "未找到目标持仓或挂单，暂时不能执行该操作", Toast.LENGTH_SHORT).show();
            lastConsumedTradeActionToken = token;
            clearPendingTradeActionIntent(intent);
            return;
        }
        MarketChartTradeDialogCoordinator.ChartTradeAction chartAction = mapIntentTradeAction(action);
        if (chartAction == null) {
            clearPendingTradeActionIntent(intent);
            return;
        }
        lastConsumedTradeActionToken = token;
        tradeDialogCoordinator.showTradeCommandDialog(chartAction, targetItem);
        clearPendingTradeActionIntent(intent);
    }

    // 交易动作一旦进入页面消费链，就立刻从宿主 Intent 清掉，避免后续切回 tab 时被重复重放。
    private void clearPendingTradeActionIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        intent.removeExtra(EXTRA_TRADE_ACTION);
        intent.removeExtra(EXTRA_TRADE_POSITION_TICKET);
        intent.removeExtra(EXTRA_TRADE_ORDER_TICKET);
        activity.setIntent(intent);
    }

    @Nullable
    private MarketChartTradeDialogCoordinator.ChartTradeAction mapIntentTradeAction(@NonNull String action) {
        if (EXTRA_TRADE_ACTION_CLOSE_POSITION.equals(action)) {
            return MarketChartTradeDialogCoordinator.ChartTradeAction.CLOSE_POSITION;
        }
        if (EXTRA_TRADE_ACTION_MODIFY_POSITION.equals(action)) {
            return MarketChartTradeDialogCoordinator.ChartTradeAction.MODIFY_TPSL;
        }
        if (EXTRA_TRADE_ACTION_MODIFY_PENDING.equals(action)) {
            return MarketChartTradeDialogCoordinator.ChartTradeAction.PENDING_MODIFY;
        }
        if (EXTRA_TRADE_ACTION_CANCEL_PENDING.equals(action)) {
            return MarketChartTradeDialogCoordinator.ChartTradeAction.PENDING_CANCEL;
        }
        return null;
    }

    @Nullable
    private AccountSnapshot resolveTradeTargetSnapshot() {
        AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager == null
                ? null
                : accountStatsPreloadManager.getLatestCache();
        if (cache != null && matchesActiveSessionIdentity(cache.getAccount(), cache.getServer())) {
            return cache.getSnapshot();
        }
        if (storedChartOverlaySnapshot != null
                && matchesStoredChartOverlayIdentity(resolveActiveSessionAccount(), resolveActiveSessionServer())) {
            return storedChartOverlaySnapshot;
        }
        return null;
    }

    @Nullable
    private PositionItem resolveTradeTargetItem(@NonNull AccountSnapshot snapshot,
                                                @Nullable Intent intent,
                                                @NonNull String action) {
        if (EXTRA_TRADE_ACTION_CANCEL_PENDING.equals(action)) {
            return findPendingOrderByTicket(snapshot.getPendingOrders(), resolveTradeOrderTicket(intent));
        }
        if (EXTRA_TRADE_ACTION_MODIFY_PENDING.equals(action)) {
            return findPendingOrderByTicket(snapshot.getPendingOrders(), resolveTradeOrderTicket(intent));
        }
        return findPositionByTicket(snapshot.getPositions(), resolveTradePositionTicket(intent));
    }

    @Nullable
    private PositionItem findPositionByTicket(@Nullable List<PositionItem> positions, long positionTicket) {
        if (positions == null || positions.isEmpty() || positionTicket <= 0L) {
            return null;
        }
        for (PositionItem item : positions) {
            if (item != null && item.getPositionTicket() == positionTicket) {
                return item;
            }
        }
        return null;
    }

    @Nullable
    private PositionItem findPendingOrderByTicket(@Nullable List<PositionItem> pendingOrders, long orderTicket) {
        if (pendingOrders == null || pendingOrders.isEmpty() || orderTicket <= 0L) {
            return null;
        }
        for (PositionItem item : pendingOrders) {
            if (item != null && item.getOrderId() == orderTicket) {
                return item;
            }
        }
        return null;
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
        binding.btnInterval1m.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[0].key));
        binding.btnInterval5m.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[1].key));
        binding.btnInterval15m.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[2].key));
        binding.btnInterval30m.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[3].key));
        binding.btnInterval1h.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[4].key));
        binding.btnInterval4h.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[5].key));
        binding.btnInterval1d.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[6].key));
        binding.btnInterval1w.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[7].key));
        binding.btnInterval1mo.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[8].key));
        binding.btnInterval1y.setOnClickListener(v -> pageRuntime.requestIntervalSelection(INTERVALS[9].key));
        updateIntervalButtons();
    }

    private void setupIndicatorButtons() {
        binding.btnIndicatorVolume.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_VOLUME));
        binding.btnIndicatorMacd.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_MACD));
        binding.btnIndicatorStochRsi.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_STOCH_RSI));
        binding.btnIndicatorBoll.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_BOLL));
        binding.btnIndicatorMa.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_MA));
        binding.btnIndicatorEma.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_EMA));
        binding.btnIndicatorSra.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_SRA));
        binding.btnIndicatorAvl.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_AVL));
        binding.btnIndicatorRsi.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_RSI));
        binding.btnIndicatorKdj.setOnClickListener(v -> pageRuntime.toggleIndicator(MarketChartPageRuntime.INDICATOR_KDJ));

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
                        pageRuntime.notifyIndicatorSelectionChanged();
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
                        pageRuntime.notifyIndicatorSelectionChanged();
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
                        pageRuntime.notifyIndicatorSelectionChanged();
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
                        pageRuntime.notifyIndicatorSelectionChanged();
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
                        pageRuntime.notifyIndicatorSelectionChanged();
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
                        pageRuntime.notifyIndicatorSelectionChanged();
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
                        pageRuntime.notifyIndicatorSelectionChanged();
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
                        pageRuntime.notifyIndicatorSelectionChanged();
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
                        pageRuntime.notifyIndicatorSelectionChanged();
                    });
            return true;
        });
        updateIndicatorButtons();
    }

    // 创建图表页复用的交易执行协调器，统一复用检查、确认和强刷逻辑。
    private TradeExecutionCoordinator createTradeExecutionCoordinator() {
        TradeAuditStore auditStore = new TradeAuditStore(activity);
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
                range -> accountStatsPreloadManager == null ? null : accountStatsPreloadManager.fetchFullForUi(range),
                new TradeConfirmDialogController(this::buildTradeRiskConfig),
                3,
                this::buildTradeRiskConfig,
                auditStore
        );
    }

    // 创建图表页复用的批量交易协调器，第三阶段复杂动作统一走这条主链。
    private BatchTradeCoordinator createBatchTradeCoordinator() {
        TradeAuditStore auditStore = new TradeAuditStore(activity);
        return new BatchTradeCoordinator(
                new BatchTradeCoordinator.BatchTradeGateway() {
                    @Override
                    public com.binance.monitor.data.model.v2.trade.BatchTradeReceipt submit(
                            com.binance.monitor.data.model.v2.trade.BatchTradePlan plan
                    ) throws Exception {
                        return gatewayV2TradeClient.submitBatch(plan);
                    }

                    @Override
                    public com.binance.monitor.data.model.v2.trade.BatchTradeReceipt result(String batchId) throws Exception {
                        return gatewayV2TradeClient.batchResult(batchId);
                    }
                },
                range -> accountStatsPreloadManager == null ? null : accountStatsPreloadManager.fetchFullForUi(range),
                2,
                this::buildTradeRiskConfig,
                auditStore
        );
    }

    @NonNull
    private TradeRiskGuard.Config buildTradeRiskConfig() {
        ConfigManager manager = ConfigManager.getInstance(activity.getApplicationContext());
        return new TradeRiskGuard.Config(
                manager.getTradeMaxQuickMarketVolume(),
                manager.getTradeMaxSingleMarketVolume(),
                manager.getTradeMaxBatchItems(),
                manager.getTradeMaxBatchTotalVolume(),
                manager.isTradeForceConfirmAddPosition(),
                manager.isTradeForceConfirmReverse()
        );
    }

    private void setupChartPositionPanel() {
        binding.layoutChartQuickTradeBar.setVisibility(View.GONE);
        binding.btnChartModeMarket.setOnClickListener(v -> toggleQuickTradeMode(ChartQuickTradeMode.MARKET));
        binding.btnChartModePending.setOnClickListener(v -> toggleQuickTradeMode(ChartQuickTradeMode.PENDING));
        binding.btnQuickTradeTemplate.setOnClickListener(v -> showQuickTradeTemplatePicker());
        binding.btnQuickTradePrimary.setOnClickListener(v -> executePrimaryQuickTrade());
        binding.btnQuickTradeSecondary.setOnClickListener(v -> executeSecondaryQuickTrade());
        updateQuickTradeBar();
        dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);
        dataCoordinator.restoreChartOverlayFromLatestCache();
    }

    // 切换快捷交易模式；再次点击当前模式时直接收起。
    private void toggleQuickTradeMode(@NonNull ChartQuickTradeMode targetMode) {
        if (quickTradeMode == targetMode) {
            applyQuickTradeMode(ChartQuickTradeMode.CLOSED);
            return;
        }
        applyQuickTradeMode(targetMode);
    }

    // 统一应用当前快捷模式，后续挂单线显示也从这里收口。
    private void applyQuickTradeMode(@NonNull ChartQuickTradeMode mode) {
        quickTradeMode = mode;
        if (mode == ChartQuickTradeMode.PENDING) {
            pendingLinePrice = resolveQuickTradeCurrentPrice();
        } else {
            pendingLinePrice = Double.NaN;
        }
        binding.klineChartView.setTradeLayerSnapshot(buildTradeLayerSnapshot());
        updateQuickTradeBar();
        dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);
    }

    // 根据当前快捷交易模式生成图表交易状态层快照。
    private ChartTradeLayerSnapshot buildTradeLayerSnapshot() {
        List<ChartTradeLine> liveLines = new ArrayList<>();
        List<ChartTradeLine> draftLines = new ArrayList<>();
        if (quickTradeMode == ChartQuickTradeMode.PENDING
                && Double.isFinite(pendingLinePrice)
                && pendingLinePrice > 0d) {
            draftLines.add(new ChartTradeLine(
                    "quick-pending-draft",
                    pendingLinePrice,
                    "草稿挂单",
                    ChartTradeLineState.DRAFT_PENDING
            ));
        }
        return new ChartTradeLayerSnapshot(liveLines, draftLines);
    }

    // 根据当前模式刷新快捷条可见性和主按钮文案。
    private void updateQuickTradeBar() {
        boolean visible = quickTradeMode != ChartQuickTradeMode.CLOSED;
        binding.layoutChartQuickTradeBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (binding.etQuickTradeVolume.getText() == null
                || binding.etQuickTradeVolume.getText().toString().trim().isEmpty()) {
            double defaultVolume = tradeTemplateRepository == null
                    ? 0d
                    : tradeTemplateRepository.getDefaultVolume();
            TradeTemplate quickTemplate = resolveQuickTradeTemplate();
            if (quickTemplate != null && quickTemplate.getDefaultVolume() > 0d) {
                defaultVolume = quickTemplate.getDefaultVolume();
            }
            if (defaultVolume > 0d) {
                binding.etQuickTradeVolume.setText(FormatUtils.formatVolume(defaultVolume));
            }
        }
        updateQuickTradeTemplateButton(resolveQuickTradeTemplate());
        if (quickTradeMode == ChartQuickTradeMode.PENDING) {
            binding.btnQuickTradePrimary.setText(R.string.chart_quick_trade_pending_buy);
            binding.btnQuickTradeSecondary.setText(R.string.chart_quick_trade_pending_sell);
            binding.layoutChartQuickTradeBar.post(this::applyQuickTradeBarLayoutContract);
            return;
        }
        binding.btnQuickTradePrimary.setText(R.string.chart_quick_trade_buy);
        binding.btnQuickTradeSecondary.setText(R.string.chart_quick_trade_sell);
        binding.layoutChartQuickTradeBar.post(this::applyQuickTradeBarLayoutContract);
    }

    // 运行时再次锁定快捷交易条的宽度合同，避免不同设备上权重布局被测量成内容宽度。
    private void applyQuickTradeBarLayoutContract() {
        if (binding == null || binding.layoutChartQuickTradeBar == null) {
            return;
        }
        int controlGapPx = SpacingTokenResolver.inlineGapPx(this);
        applyQuickTradeTemplateButtonLayout(controlGapPx);
        applyWeightedQuickTradeButtonLayout(binding.btnQuickTradePrimary, controlGapPx);
        applyQuickTradeVolumeLayout(controlGapPx);
        applyWeightedQuickTradeButtonLayout(binding.btnQuickTradeSecondary, controlGapPx);
        binding.layoutChartQuickTradeBar.requestLayout();
    }

    // 模板按钮保持内容宽度，只承担低频切换入口。
    private void applyQuickTradeTemplateButtonLayout(int controlGapPx) {
        if (binding == null || binding.btnQuickTradeTemplate == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = binding.btnQuickTradeTemplate.getLayoutParams();
        LinearLayout.LayoutParams params = rawParams instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) rawParams
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.weight = 0f;
        params.setMarginEnd(controlGapPx);
        binding.btnQuickTradeTemplate.setLayoutParams(params);
    }

    // 快捷条左右按钮必须平分剩余宽度，避免看起来像靠左堆在一起。
    private void applyWeightedQuickTradeButtonLayout(@Nullable View button, int marginStartPx) {
        if (button == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = button.getLayoutParams();
        LinearLayout.LayoutParams params = rawParams instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) rawParams
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.width = 0;
        params.weight = 1f;
        params.setMarginStart(marginStartPx);
        params.setMarginEnd(0);
        button.setLayoutParams(params);
    }

    // 中间手数输入框继续保持固定宽度，只保留左右统一间距。
    private void applyQuickTradeVolumeLayout(int controlGapPx) {
        if (binding == null || binding.etQuickTradeVolume == null) {
            return;
        }
        int widthPx = getResources().getDimensionPixelSize(R.dimen.chart_quick_trade_volume_width);
        ViewGroup.LayoutParams rawParams = binding.etQuickTradeVolume.getLayoutParams();
        LinearLayout.LayoutParams params = rawParams instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) rawParams
                : new LinearLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.width = widthPx;
        params.weight = 0f;
        params.setMarginStart(controlGapPx);
        params.setMarginEnd(controlGapPx);
        binding.etQuickTradeVolume.setLayoutParams(params);
    }

    // 快捷条左按钮按当前模式执行买入或挂单买入。
    private void executePrimaryQuickTrade() {
        if (chartQuickTradeCoordinator == null || quickTradeMode == ChartQuickTradeMode.CLOSED) {
            return;
        }
        try {
            String volumeText = resolveQuickTradeVolumeText();
            if (quickTradeMode == ChartQuickTradeMode.MARKET) {
                chartQuickTradeCoordinator.executeMarketBuy(volumeText);
            } else if (quickTradeMode == ChartQuickTradeMode.PENDING) {
                chartQuickTradeCoordinator.executePendingBuy(volumeText, pendingLinePrice);
            }
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 快捷条右按钮按当前模式执行卖出或挂单卖出。
    private void executeSecondaryQuickTrade() {
        if (chartQuickTradeCoordinator == null || quickTradeMode == ChartQuickTradeMode.CLOSED) {
            return;
        }
        try {
            String volumeText = resolveQuickTradeVolumeText();
            if (quickTradeMode == ChartQuickTradeMode.MARKET) {
                chartQuickTradeCoordinator.executeMarketSell(volumeText);
            } else if (quickTradeMode == ChartQuickTradeMode.PENDING) {
                chartQuickTradeCoordinator.executePendingSell(volumeText, pendingLinePrice);
            }
        } catch (IllegalArgumentException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private String resolveQuickTradeVolumeText() {
        if (binding.etQuickTradeVolume.getText() == null) {
            return "";
        }
        return binding.etQuickTradeVolume.getText().toString();
    }

    @NonNull
    private String resolveQuickTradeAccountId() {
        AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager == null
                ? null
                : accountStatsPreloadManager.getLatestCache();
        if (cache == null || !matchesActiveSessionIdentity(cache.getAccount(), cache.getServer())) {
            return "";
        }
        return trimToEmpty(cache.getAccount());
    }

    // 快捷交易优先复用最新K线收盘价，拿不到时直接返回未就绪。
    private double resolveQuickTradeCurrentPrice() {
        double latestPrice = MarketChartTradeSupport.resolveReferencePrice(loadedCandles, null, null);
        if (latestPrice > 0d) {
            return latestPrice;
        }
        if (!loadedCandles.isEmpty()) {
            CandleEntry latest = loadedCandles.get(loadedCandles.size() - 1);
            if (latest != null && latest.getClose() > 0d) {
                return latest.getClose();
            }
        }
        return 0d;
    }

    // 继续复用现有交易协调器主链，不在图表页复制第二套检查/提交逻辑。
    private void executeQuickTradeCommand(@NonNull TradeCommand command) {
        if (tradeDialogCoordinator == null) {
            Toast.makeText(this, "交易链路未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        tradeDialogCoordinator.submitDirectTradeCommand(applyQuickTradeTemplate(command));
    }

    @NonNull
    private TradeCommand applyQuickTradeTemplate(@NonNull TradeCommand command) {
        if (tradeTemplateRepository == null) {
            return command;
        }
        return tradeTemplateRepository.applyTemplate(command, resolveQuickTradeTemplate());
    }

    @Nullable
    private TradeTemplate resolveQuickTradeTemplate() {
        if (tradeTemplateRepository == null) {
            return null;
        }
        TradeTemplate quickTemplate = tradeTemplateRepository.getQuickTradeTemplate();
        String requiredScope = quickTradeMode == ChartQuickTradeMode.PENDING ? "pending" : "market";
        if (supportsQuickTradeScope(quickTemplate, requiredScope)) {
            return quickTemplate;
        }
        for (TradeTemplate template : tradeTemplateRepository.getTemplates()) {
            if (supportsQuickTradeScope(template, requiredScope)) {
                return template;
            }
        }
        return quickTemplate;
    }

    private boolean supportsQuickTradeScope(@Nullable TradeTemplate template, @NonNull String requiredScope) {
        if (template == null) {
            return false;
        }
        String scope = template.getEntryScope().trim().toLowerCase(Locale.ROOT);
        return scope.isEmpty() || "both".equals(scope) || requiredScope.equals(scope);
    }

    private void updateQuickTradeTemplateButton(@Nullable TradeTemplate template) {
        if (binding == null || binding.btnQuickTradeTemplate == null) {
            return;
        }
        String label = template == null || template.getDisplayName().trim().isEmpty()
                ? getString(R.string.chart_quick_trade_template)
                : template.getDisplayName().trim();
        binding.btnQuickTradeTemplate.setText(label);
    }

    private void showQuickTradeTemplatePicker() {
        if (tradeTemplateRepository == null) {
            Toast.makeText(this, "模板未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        List<TradeTemplate> templates = resolveQuickTradeTemplateOptions();
        if (templates.isEmpty()) {
            Toast.makeText(this, "当前模式暂无可用模板", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[templates.size()];
        int checkedItem = 0;
        TradeTemplate currentTemplate = resolveQuickTradeTemplate();
        String currentId = currentTemplate == null ? "" : currentTemplate.getTemplateId();
        for (int index = 0; index < templates.size(); index++) {
            TradeTemplate template = templates.get(index);
            labels[index] = template.getDisplayName();
            if (template.getTemplateId().equals(currentId)) {
                checkedItem = index;
            }
        }
        final int[] selectedIndex = new int[]{checkedItem};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.chart_quick_trade_template_title)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> selectedIndex[0] = which)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", (dialog, which) -> applyQuickTradeTemplateSelection(templates.get(selectedIndex[0])))
                .show();
    }

    @NonNull
    private List<TradeTemplate> resolveQuickTradeTemplateOptions() {
        if (tradeTemplateRepository == null) {
            return Collections.emptyList();
        }
        List<TradeTemplate> options = new ArrayList<>();
        String requiredScope = quickTradeMode == ChartQuickTradeMode.PENDING ? "pending" : "market";
        for (TradeTemplate template : tradeTemplateRepository.getTemplates()) {
            if (supportsQuickTradeScope(template, requiredScope)) {
                options.add(template);
            }
        }
        return options;
    }

    private void applyQuickTradeTemplateSelection(@NonNull TradeTemplate template) {
        if (tradeTemplateRepository == null) {
            return;
        }
        tradeTemplateRepository.setQuickTradeTemplateId(template.getTemplateId());
        if (template.getDefaultVolume() > 0d) {
            binding.etQuickTradeVolume.setText(FormatUtils.formatVolume(template.getDefaultVolume()));
        }
        updateQuickTradeTemplateButton(template);
    }

    // 当前页已不再保留独立异常摘要区，风险信息统一由图表内异常标记承接。
    private void updateCurrentSymbolAbnormalSummary() {
        // no-op
    }

    // 统一创建持仓明细排序下拉项，保证主题切换后选项仍可见。

    // 排序标签和下拉选项统一使用当前主题文字色。
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
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.styleInputField(etValue1, palette, R.style.TextAppearance_BinanceMonitor_Control);
        UiPaletteManager.styleInputField(etValue2, palette, R.style.TextAppearance_BinanceMonitor_Control);
        UiPaletteManager.styleInputField(etValue3, palette, R.style.TextAppearance_BinanceMonitor_Control);
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

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(hint)
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("应用", (dialogInterface, which) -> {
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
                .create();
        dialog.setOnShowListener(dialogInterface -> styleIndicatorParamDialogActions(dialog));
        dialog.show();
    }

    private int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            return value > 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long intervalToMs(String key) {
        if ("1M".equals(key)) return 30L * 24L * 60L * 60_000L;
        if ("1y".equals(key)) return 365L * 24L * 60L * 60_000L;
        if ("1m".equals(key)) return 60_000L;
        if ("5m".equals(key)) return 5L * 60_000L;
        if ("15m".equals(key)) return 15L * 60_000L;
        if ("30m".equals(key)) return 30L * 60_000L;
        if ("1h".equals(key)) return 60L * 60_000L;
        if ("4h".equals(key)) return 4L * 60L * 60_000L;
        if ("1d".equals(key)) return 24L * 60L * 60_000L;
        if ("1w".equals(key)) return 7L * 24L * 60L * 60_000L;
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

    void renderRefreshCountdown(long nextAutoRefreshAtMs) {
        if (binding == null || binding.tvChartPositionSummary == null) {
            return;
        }
        updateChartPositionSummaryPosition();
    }

    // 当前图表已切到推送优先链路，不再展示固定秒级刷新倒计时。
    boolean shouldShowRefreshCountdown() {
        return false;
    }

    long resolveAutoRefreshDelayMs() {
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
    boolean shouldRequestKlinesOnResume() {
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

    // 用最新分钟 K 线修正当前图表的末尾，避免只能每 5 秒靠轮询回填。
    private void applyRealtimeChartTail(@Nullable KlineData latestKline) {
        dispatchChartRefresh(
                ChartRefreshEvent.marketTickChanged(),
                null,
                () -> scheduleRealtimeTailRefresh(latestKline)
        );
    }

    private void scheduleRealtimeTailRefresh(@Nullable KlineData latestKline) {
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
        pendingRealtimeTailKline = latestKline;
        chartRefreshScheduler.requestChartRender(buildRealtimeTailRenderToken(latestKline));
        if (realtimeTailDrainScheduled) {
            return;
        }
        realtimeTailDrainScheduled = true;
        mainHandler.post(realtimeTailDrainRunnable);
    }

    private void applyRealtimeChartTailNow(@NonNull KlineData latestKline) {
        if (binding == null || !hasRealtimeTailSourceForChart()) {
            return;
        }
        String key = buildCacheKey(selectedSymbol, selectedInterval);
        CandleEntry realtimeBaseCandle = toRealtimeCandleEntry(latestKline);
        if (realtimeTailHelper == null) {
            return;
        }
        MarketChartDataCoordinator.IntervalSelection minuteInterval =
                new MarketChartDataCoordinator.IntervalSelection(
                        INTERVALS[0].key,
                        INTERVALS[0].apiInterval,
                        INTERVALS[0].limit,
                        INTERVALS[0].yearAggregate
                );
        String minuteKey = buildCacheKey(selectedSymbol, INTERVALS[0]);
        List<CandleEntry> minuteCandles = realtimeTailHelper.mergeRealtimeMinuteCache(
                selectedSymbol,
                minuteKey,
                getCachedCandles(minuteKey),
                realtimeBaseCandle,
                INTERVALS[0].limit,
                RESTORE_WINDOW_LIMIT
        );
        if (!minuteCandles.isEmpty()) {
            klineCache.put(minuteKey, new ArrayList<>(minuteCandles));
        }
        List<CandleEntry> realtimeDisplay = realtimeTailHelper.buildRealtimeDisplayCandles(
                selectedSymbol,
                minuteIntervalFromSelected(),
                loadedCandles,
                realtimeBaseCandle,
                minuteCandles
        );
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
        if (!binding.klineChartView.hasActiveCrosshair()) {
            renderInfoWithLatest();
        }
        if (pageRuntime != null) {
            pageRuntime.refreshRefreshCountdownDisplay();
        }
    }

    @NonNull
    private String buildRealtimeTailRenderToken(@NonNull KlineData latestKline) {
        return latestKline.getSymbol()
                + '|'
                + latestKline.getOpenTime()
                + '|'
                + latestKline.getCloseTime()
                + '|'
                + latestKline.getClosePrice();
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
    private List<CandleEntry> legacyMergeRealtimeMinuteCache(CandleEntry realtimeBaseCandle) {
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
    private List<CandleEntry> legacyBuildRealtimeDisplayCandles(CandleEntry realtimeBaseCandle,
                                                                List<CandleEntry> minuteCandles) {
        if (selectedInterval.yearAggregate) {
            return new ArrayList<>();
        }
        if ("1m".equals(selectedInterval.key)) {
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

    private MarketChartDataCoordinator.IntervalSelection minuteIntervalFromSelected() {
        IntervalOption option = selectedInterval == null ? INTERVALS[0] : selectedInterval;
        return new MarketChartDataCoordinator.IntervalSelection(
                option.key,
                option.apiInterval,
                option.limit,
                option.yearAggregate
        );
    }

    private MarketChartSeriesLoaderHelper.Gateway seriesGateway() {
        return new MarketChartSeriesLoaderHelper.Gateway() {
            @Nullable
            @Override
            public MarketSeriesPayload fetchMarketSeries(@NonNull String symbol, @NonNull String apiInterval, int limit) throws Exception {
                return gatewayV2Client == null ? null : gatewayV2Client.fetchMarketSeries(symbol, apiInterval, limit);
            }

            @Nullable
            @Override
            public MarketSeriesPayload fetchMarketSeriesBefore(@NonNull String symbol, @NonNull String apiInterval, int limit, long endTimeInclusive) throws Exception {
                return gatewayV2Client == null ? null : gatewayV2Client.fetchMarketSeriesBefore(symbol, apiInterval, limit, endTimeInclusive);
            }

            @Nullable
            @Override
            public MarketSeriesPayload fetchMarketSeriesAfter(@NonNull String symbol, @NonNull String apiInterval, int limit, long startTimeInclusive) throws Exception {
                return gatewayV2Client == null ? null : gatewayV2Client.fetchMarketSeriesAfter(symbol, apiInterval, limit, startTimeInclusive);
            }
        };
    }

    private String buildCacheKey(String symbol, IntervalOption interval) {
        if (interval == null) {
            return MarketChartCacheKeyHelper.build(symbol, "default", "default", false);
        }
        return MarketChartCacheKeyHelper.build(symbol, interval.key, interval.apiInterval, interval.yearAggregate);
    }

    private IntervalOption requireIntervalOption(@NonNull MarketChartDataCoordinator.IntervalSelection interval) {
        for (IntervalOption candidate : INTERVALS) {
            if (candidate != null
                    && candidate.yearAggregate == interval.isYearAggregate()
                    && candidate.limit == interval.getLimit()
                    && interval.getKey().equals(candidate.key)
                    && interval.getApiInterval().equals(candidate.apiInterval)) {
                return candidate;
            }
        }
        return selectedInterval == null ? INTERVALS[0] : selectedInterval;
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
        klineCache.clear();
        latestPersistedSignatures.clear();
        loadedCandles.clear();
        activeDataKey = "";
        scheduleChartCacheInvalidation();
    }

    // 图表缓存版本升级后的清库必须走后台线程，并且后续本地读写都要等它完成。
    private void scheduleChartCacheInvalidation() {
        if (chartHistoryRepository == null || ioExecutor == null || ioExecutor.isShutdown()) {
            chartCacheInvalidationTask = null;
            return;
        }
        chartCacheInvalidationTask = ioExecutor.submit(() -> {
            chartHistoryRepository.clearAllHistory();
            markChartCacheSchemaCurrent();
        });
    }

    // 只有旧缓存真正清理成功后，才把本地 schema 版本推进到最新，避免失败后失去重试机会。
    private void markChartCacheSchemaCurrent() {
        getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_KEY_CHART_CACHE_SCHEMA_VERSION, CHART_CACHE_SCHEMA_VERSION)
                .apply();
    }

    // 后台恢复和落库前统一等待旧缓存清理完成，避免版本升级后又把旧 K 线读回来。
    private void awaitChartCacheInvalidationIfNeeded() {
        Future<?> invalidationTask = chartCacheInvalidationTask;
        if (invalidationTask == null) {
            return;
        }
        try {
            invalidationTask.get();
            if (chartCacheInvalidationTask == invalidationTask) {
                chartCacheInvalidationTask = null;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("图表缓存清理失败", exception);
        }
    }

    // 冷启动恢复缓存时统一使用当前产品和周期生成键，避免宿主层重复拼 key。
    void beginChartBootstrap() {
        resetChartBootstrapState();
        invalidateForColdStart();
        String key = buildCacheKey(selectedSymbol, selectedInterval);
        List<CandleEntry> memoryCached = getCachedCandles(key);
        if (memoryCached == null || memoryCached.isEmpty()) {
            applyBootstrapState(chartBootstrapSnapshot);
            return;
        }
        applyLocalDisplayCandles(key, memoryCached);
        applyBootstrapState(chartBootstrapStateMachine.onMemoryDataReady(key));
    }

    // 冷启动恢复缓存时统一使用当前产品和周期生成键，避免宿主层重复拼 key。
    void restorePersistedCache() {
        restorePersistedCache(buildCacheKey(selectedSymbol, selectedInterval));
    }

    private void restorePersistedCache(String key) {
        List<CandleEntry> persisted = getCachedCandles(key);
        if (persisted == null || persisted.isEmpty()) {
            applyBootstrapState(chartBootstrapStateMachine.onStorageRestoreStarted());
            schedulePersistedCacheRestore(key, true);
            return;
        }
        applyLocalDisplayCandles(key, persisted);
        applyBootstrapState(chartBootstrapStateMachine.onMemoryDataReady(key));
    }

    // 本地缓存恢复和切周期预显示共用同一套落图入口，避免状态更新规则再次分叉。
    private void applyLocalDisplayCandles(String key, List<CandleEntry> candles) {
        if (key == null || key.trim().isEmpty() || candles == null || candles.isEmpty() || binding == null) {
            return;
        }
        List<CandleEntry> restoreWindow = ChartWindowSliceHelper.takeLatest(candles, RESTORE_WINDOW_LIMIT);
        applyDisplayCandles(key, restoreWindow, false, false, false);
        renderInfoWithLatest();
        updateStateCount();
        dataCoordinator.refreshChartOverlays();
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
        applyBootstrapState(chartBootstrapStateMachine.onRemoteDataReady(buildCacheKey(selectedSymbol, selectedInterval)));
        dataCoordinator.refreshChartOverlays();
        if (!(autoRefresh && binding.klineChartView.hasActiveCrosshair())) {
            renderInfoWithLatest();
        }
        long measuredLatencyMs = Math.max(0L, SystemClock.elapsedRealtime() - requestStartedAtMs);
        lastSuccessfulRequestLatencyMs = MarketChartRefreshHelper.smoothDisplayedLatencyMs(
                lastSuccessfulRequestLatencyMs,
                measuredLatencyMs
        );
        lastSuccessUpdateMs = System.currentTimeMillis();
        markCurrentMarketWindowApplied();
        updateStateCount();
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.btnRetryLoad.setVisibility(android.view.View.GONE);
        showLoading(false);
    }

    // 统一处理跳过网络请求后的页面状态，避免 SKIP 分支继续散写 UI 重置。
    private void applyRequestSkipState(MarketChartRefreshHelper.SyncPlan refreshPlan) {
        lastSuccessfulRequestLatencyMs =
                MarketChartRefreshHelper.resolveDisplayedLatencyMs(refreshPlan, lastSuccessfulRequestLatencyMs);
        markCurrentMarketWindowApplied();
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.btnRetryLoad.setVisibility(android.view.View.GONE);
        showLoading(false);
        if (pageRuntime != null) {
            pageRuntime.refreshRefreshCountdownDisplay();
        }
    }

    // 统一处理发起网络请求前的页面状态，避免 loading / error reset 再次分叉。
    private void applyRequestStartState(boolean autoRefresh) {
        showLoading(MarketChartDisplayHelper.shouldShowBlockingLoading(autoRefresh, loadedCandles));
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.btnRetryLoad.setVisibility(android.view.View.GONE);
    }

    // 统一处理失败后的页面状态，避免空图与错误提示规则继续散落在请求回调里。
    private void applyRequestFailureState(boolean autoRefresh,
                                          boolean deferTrueEmptyUntilStorageRestore,
                                          String message) {
        if (loadedCandles.isEmpty() && !deferTrueEmptyUntilStorageRestore) {
            applyBootstrapState(chartBootstrapStateMachine.onStorageMiss());
            dataCoordinator.refreshChartOverlays();
        }
        updateStateCount();
        binding.tvError.setText(getString(R.string.chart_error_prefix, message));
        binding.tvError.setVisibility(android.view.View.VISIBLE);
        binding.btnRetryLoad.setVisibility(autoRefresh ? android.view.View.GONE : android.view.View.VISIBLE);
        showLoading(false);
    }

    // 切产品或切周期时，先失效上一组图表上下文，避免旧数据挂在新选择下继续显示。
    void invalidateChartDisplayContext() {
        invalidateForSelectionChange();
    }

    private void invalidateForColdStart() {
        chartBlockingLoadingRequested = false;
        activeDataKey = "";
        lastAppliedMarketWindowSignature = "";
        lastAppliedMarketWindowUpdatedAtMs = 0L;
        lastAbnormalOverlaySignature = "";
        lastAccountOverlaySignature = "";
        startupGate.resetForDataKey(buildCacheKey(selectedSymbol, selectedInterval));
        clearStartupPrimaryDrawObserver();
        renderChartLoadingState();
    }

    private void invalidateForSelectionChange() {
        resetChartBootstrapState();
        chartBlockingLoadingRequested = false;
        activeDataKey = "";
        loadedCandles.clear();
        lastAppliedMarketWindowSignature = "";
        lastAppliedMarketWindowUpdatedAtMs = 0L;
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
        lastChartOverlaySnapshot = ChartOverlaySnapshot.empty();
        bindChartOverlayStatus(lastChartOverlaySnapshot, SensitiveDisplayMasker.isEnabled(this));
        updateStateCount();
        renderChartLoadingState();
    }

    // 图表远端应用或明确跳过回源后，都要记录当前市场窗口签名，避免定时器继续把同一版本当成未消费。
    private void markCurrentMarketWindowApplied() {
        lastAppliedMarketWindowSignature = resolveCurrentMarketWindowSignature();
        lastAppliedMarketWindowUpdatedAtMs = System.currentTimeMillis();
    }

    @NonNull
    private String resolveCurrentMarketWindowSignature() {
        if (monitorRepository == null) {
            return "";
        }
        return monitorRepository.selectMarketWindowSignature(selectedSymbol);
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
        dataCoordinator.refreshChartOverlays();
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
        if (!ChartGapFillHelper.shouldBackfillOlderHistory(
                previousWindowSize,
                RESTORE_WINDOW_LIMIT,
                previousOldestOpenTime,
                latestWindowOldestOpenTime)) {
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
                latestOldest)
                && rounds < GAP_FILL_MAX_ROUNDS) {
            List<CandleEntry> fetched = chartSeriesLoaderHelper.fetchV2SeriesBefore(
                    reqSymbol,
                    new MarketChartDataCoordinator.IntervalSelection(
                            reqInterval.key,
                            reqInterval.apiInterval,
                            reqInterval.limit,
                            reqInterval.yearAggregate
                    ),
                    HISTORY_PAGE_LIMIT,
                    Math.max(0L, latestOldest - 1L),
                    seriesGateway()
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

    // 统一绑定图表页轻量状态，避免再从多个列表控件反推文案。
    private void bindChartOverlayStatus(@NonNull ChartOverlaySnapshot overlaySnapshot, boolean masked) {
        if (binding == null) {
            return;
        }
        if (masked) {
            binding.tvChartPositionSummary.setText("盈亏：**** | 持仓：****");
            binding.tvChartPositionSummary.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            return;
        }
        String summaryText = overlaySnapshot.getPositionSummaryText();
        binding.tvChartPositionSummary.setText(IndicatorPresentationPolicy.buildDirectionalSpanAfterAnchor(
                this,
                summaryText,
                "盈亏：",
                IndicatorId.ACCOUNT_POSITION_PNL,
                R.color.text_secondary
        ));
        binding.tvChartPositionSummary.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }

    // 把图表刷新预算真正接到主链，按区块执行必要刷新。
    private void dispatchChartRefresh(@NonNull ChartRefreshEvent event,
                                      @Nullable Runnable overlayAction,
                                      @Nullable Runnable realtimeAction) {
        ChartRefreshBudget budget = ChartRefreshBudget.resolve(event);
        if (budget.needsUiStateBind()) {
            applyUiStateRefresh();
        }
        if (budget.needsDialogBind()) {
            chartRefreshScheduler.requestDialogBind(event.getType().name() + "|dialog");
            scheduleDialogDrain();
        }
        if (budget.needsOverlayRebuild() && overlayAction != null) {
            pendingOverlayRefreshAction = overlayAction;
            chartRefreshScheduler.requestOverlayRender(event.getType().name() + "|overlay");
        }
        if (budget.needsSummaryBind()) {
            pendingSummaryRefreshAction = this::applySummaryRefresh;
            chartRefreshScheduler.requestSummaryBind(event.getType().name() + "|summary");
        }
        if ((budget.needsOverlayRebuild() && overlayAction != null) || budget.needsSummaryBind()) {
            scheduleOverlaySummaryDrain();
        }
        if (budget.needsRealtimeTailInvalidate() && realtimeAction != null) {
            realtimeAction.run();
        }
    }

    private void applyUiStateRefresh() {
        updateStateCount();
    }

    private void applyDialogStateRefresh() {
        updateTradeActionButtons();
        refreshGlobalStatusButton();
        refreshVisibleGlobalStatusSheet();
    }

    private void applySummaryRefresh() {
        bindChartOverlayStatus(lastChartOverlaySnapshot, SensitiveDisplayMasker.isEnabled(this));
    }

    private void scheduleOverlaySummaryDrain() {
        if (overlaySummaryDrainScheduled) {
            return;
        }
        overlaySummaryDrainScheduled = true;
        mainHandler.post(overlaySummaryDrainRunnable);
    }

    private void scheduleDialogDrain() {
        if (dialogDrainScheduled) {
            return;
        }
        dialogDrainScheduled = true;
        mainHandler.post(dialogDrainRunnable);
    }

    private void refreshVisibleGlobalStatusSheet() {
        if (globalStatusBottomSheetController == null) {
            return;
        }
        globalStatusBottomSheetController.updateVisibleSheet(buildGlobalStatusSnapshot());
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
                latestOldest)
                && rounds < GAP_FILL_MAX_ROUNDS) {
            List<CandleEntry> olderMonthly = chartSeriesLoaderHelper.fetchV2SeriesBefore(
                    reqSymbol,
                    new MarketChartDataCoordinator.IntervalSelection(
                            reqInterval.key,
                            reqInterval.apiInterval,
                            reqInterval.limit,
                            reqInterval.yearAggregate
                    ),
                    HISTORY_PAGE_LIMIT,
                    Math.max(0L, latestOldest - 1L),
                    seriesGateway()
            );
            List<CandleEntry> olderYear = chartSeriesLoaderHelper.aggregateToYear(olderMonthly, reqSymbol);
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
                awaitChartCacheInvalidationIfNeeded();
                List<CandleEntry> persisted = chartHistoryRepository.loadCandles(key);
                if (persisted == null || persisted.isEmpty()) {
                    if (applyWhenLoaded) {
                        mainHandler.post(() -> {
                            applyPersistedCacheRestoreMiss(key);
                        });
                    }
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
        applyBootstrapState(chartBootstrapStateMachine.onStorageDataReady(key));
    }

    private void applyPersistedCacheRestoreMiss(String key) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (!key.equals(buildCacheKey(selectedSymbol, selectedInterval))) {
            return;
        }
        if (!loadedCandles.isEmpty()) {
            return;
        }
        applyBootstrapState(chartBootstrapStateMachine.onStorageMiss());
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
            awaitChartCacheInvalidationIfNeeded();
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

    private List<CandleEntry> legacyLoadCandlesForRequest(MarketChartRefreshHelper.SyncPlan plan,
                                                          @Nullable List<CandleEntry> seed,
                                                          long previousLatestOpenTime,
                                                          long previousOldestOpenTime,
                                                          int previousWindowSize,
                                                          @NonNull String symbol,
                                                          @NonNull MarketChartDataCoordinator.IntervalSelection interval) throws Exception {
        IntervalOption intervalOption = requireIntervalOption(interval);
        if (intervalOption.yearAggregate) {
            return legacyLoadYearAggregateCandlesForRequest(
                    plan,
                    seed,
                    previousLatestOpenTime,
                    previousOldestOpenTime,
                    previousWindowSize,
                    symbol,
                    intervalOption
            );
        }
        List<CandleEntry> result;
        if (plan != null && plan.mode == MarketChartRefreshHelper.SyncMode.INCREMENTAL) {
            List<CandleEntry> base = ChartWindowSliceHelper.takeLatest(seed, RESTORE_WINDOW_LIMIT);
            List<CandleEntry> tail = legacyFetchV2SeriesAfter(
                    symbol,
                    intervalOption,
                    RESTORE_WINDOW_LIMIT,
                    plan.startTimeInclusive
            );
            result = MarketChartDisplayHelper.mergeSeriesByOpenTime(base, tail);
        } else {
            result = fetchFullHistoryAndMark(symbol, intervalOption, RESTORE_WINDOW_LIMIT);
        }
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("币安未返回可用K线数据");
        }
        return result;
    }

    // 年线显示仍使用月线接口增量拉取，但先聚合成年桶再与本地年线窗口合并，避免跨粒度直接拼接导致重复统计。
    private List<CandleEntry> legacyLoadYearAggregateCandlesForRequest(MarketChartRefreshHelper.SyncPlan plan,
                                                                       @Nullable List<CandleEntry> seed,
                                                                       long previousLatestOpenTime,
                                                                       long previousOldestOpenTime,
                                                                       int previousWindowSize,
                                                                       @NonNull String symbol,
                                                                       @NonNull IntervalOption interval) throws Exception {
        List<CandleEntry> mergedYear;
        if (plan != null && plan.mode == MarketChartRefreshHelper.SyncMode.INCREMENTAL) {
            List<CandleEntry> baseYear = ChartWindowSliceHelper.takeLatest(seed, RESTORE_WINDOW_LIMIT);
            List<CandleEntry> tailMonthly = legacyFetchV2SeriesAfter(
                    symbol,
                    interval,
                    RESTORE_WINDOW_LIMIT,
                    plan.startTimeInclusive
            );
            List<CandleEntry> tailYear = legacyAggregateToYear(tailMonthly, symbol);
            mergedYear = MarketChartDisplayHelper.mergeSeriesByOpenTime(baseYear, tailYear);
        } else {
            List<CandleEntry> fullMonthly = fetchFullHistoryAndMark(symbol, interval, RESTORE_WINDOW_LIMIT);
            mergedYear = legacyAggregateToYear(fullMonthly, symbol);
        }
        if (mergedYear == null || mergedYear.isEmpty()) {
            throw new IllegalStateException("币安未返回可用K线数据");
        }
        return mergedYear;
    }

    private List<CandleEntry> fetchFullHistoryAndMark(String symbol,
                                                      @NonNull IntervalOption interval,
                                                      int limit) throws Exception {
        return legacyFetchV2FullSeries(symbol, interval, limit);
    }

    // v2 行情链路下，图表真值直接来自服务端的闭合 candles + latestPatch。
    private List<CandleEntry> legacyFetchV2FullSeries(String symbol,
                                                      @Nullable IntervalOption interval,
                                                      int limit) throws Exception {
        if (gatewayV2Client == null || interval == null) {
            return new ArrayList<>();
        }
        MarketSeriesPayload payload = gatewayV2Client.fetchMarketSeries(symbol, interval.apiInterval, limit);
        return legacyMergeMarketSeriesPayload(payload);
    }

    // v2 分页链路：按 endTime 拉窗口并复用同一套 candles + patch 合并逻辑。
    private List<CandleEntry> legacyFetchV2SeriesBefore(String symbol,
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
        return legacyMergeMarketSeriesPayload(payload);
    }

    // v2 增量链路：按 startTime 拉窗口，减少切周期后的补尾等待。
    private List<CandleEntry> legacyFetchV2SeriesAfter(String symbol,
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
        return legacyMergeMarketSeriesPayload(payload);
    }

    // 把 v2 返回的闭合 candles 与 latestPatch 合并成图表当前要显示的完整序列。
    private List<CandleEntry> legacyMergeMarketSeriesPayload(@Nullable MarketSeriesPayload payload) {
        if (payload == null) {
            return new ArrayList<>();
        }
        return MarketChartDisplayHelper.mergeSeriesWithLatestPatch(
                payload.getCandles(),
                payload.getLatestPatch()
        );
    }

    private List<CandleEntry> legacyAggregateToYear(List<CandleEntry> source, String symbol) {
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
        // 当前布局已不再展示独立十字线信息条，这里保留入口避免监听链断裂。
    }

    private void renderInfo(CandleEntry candle, double dif, double dea, double macd, double k, double d) {
        // 当前布局已不再展示独立十字线信息条，这里保留入口避免监听链断裂。
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

    void updateStateCount() {
        String base = loadedCandles.size() + "根";
        if (lastSuccessUpdateMs > 0L) {
            base += "，时间：" + stateTimeFormat.format(lastSuccessUpdateMs);
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
        chartBlockingLoadingRequested = loading;
        renderChartLoadingState();
    }

    private void renderChartLoadingState() {
        if (binding == null) {
            return;
        }
        PageBootstrapState state = chartBootstrapSnapshot == null
                ? PageBootstrapState.TRUE_EMPTY
                : chartBootstrapSnapshot.getState();
        if (state == PageBootstrapState.STORAGE_RESTORING) {
            showChartRestoreHint(getString(R.string.chart_restore_loading_text));
            return;
        }
        if (state == PageBootstrapState.LOCAL_READY_REMOTE_SYNCING) {
            showChartRestoreHint(getString(R.string.chart_restore_syncing_text));
            return;
        }
        if (chartBlockingLoadingRequested && !chartBootstrapSnapshot.hasRenderableContent()) {
            binding.progressChart.setVisibility(android.view.View.VISIBLE);
            binding.tvChartLoading.setVisibility(android.view.View.VISIBLE);
            binding.tvChartLoading.setText(R.string.chart_loading_text);
            return;
        }
        hideChartRestoreHint();
    }

    private void showChartRestoreHint(@NonNull String text) {
        binding.progressChart.setVisibility(android.view.View.GONE);
        binding.tvChartLoading.setVisibility(android.view.View.VISIBLE);
        binding.tvChartLoading.setText(text);
    }

    private void hideChartRestoreHint() {
        binding.progressChart.setVisibility(android.view.View.GONE);
        binding.tvChartLoading.setVisibility(android.view.View.GONE);
        binding.tvChartLoading.setText(R.string.chart_loading_text);
    }

    private void resetChartBootstrapState() {
        chartBootstrapStateMachine = new PageBootstrapStateMachine();
        chartBootstrapSnapshot = PageBootstrapSnapshot.initial();
    }

    private void applyBootstrapState(@NonNull PageBootstrapSnapshot snapshot) {
        chartBootstrapSnapshot = snapshot;
        renderChartLoadingState();
    }

    private void updateVolumeThresholdOverlay() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        SymbolConfig config = ConfigManager.getInstance(this).getSymbolConfig(selectedSymbol);
        boolean minuteInterval = "1m".equals(selectedInterval.apiInterval) && !selectedInterval.yearAggregate;
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
                AbnormalAnnotationOverlayBuilder.build(filteredRecords, loadedCandles, createAbnormalAnnotationColorRange());
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

    void updateAccountAnnotationsOverlay() {
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
        if (cache == null) {
            return;
        }
        if (!matchesActiveSessionIdentity(cache.getAccount(), cache.getServer())) {
            clearStoredChartOverlaySnapshot();
            clearAccountAnnotationsOverlay();
            return;
        }
        if (snapshot == null) {
            if (!isCurrentOverlayBoundToActiveSession()) {
                clearAccountAnnotationsOverlay();
            }
            return;
        }
        String overlaySignature = buildAccountOverlaySignature(cache, snapshot);
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

    // 图表页恢复时优先消费内存缓存；缓存还没回填时回退到本地已持久化快照。
    @Nullable
    private AccountSnapshot resolveChartOverlaySnapshot(boolean sessionActive,
                                                        @Nullable AccountStatsPreloadManager.Cache cache) {
        if (!sessionActive) {
            return null;
        }
        if (cache != null && !matchesActiveSessionIdentity(cache.getAccount(), cache.getServer())) {
            clearStoredChartOverlaySnapshot();
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
                String activeAccount = resolveActiveSessionAccount();
                String activeServer = resolveActiveSessionServer();
                AccountStorageRepository.StoredSnapshot storedSnapshot =
                        activeAccount.isEmpty() || activeServer.isEmpty()
                                ? accountStorageRepository.loadStoredSnapshot()
                                : accountStorageRepository.loadStoredSnapshot(activeAccount, activeServer);
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
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        cancelChartOverlayBuildTask();
        int buildVersion = ++chartOverlayBuildVersion;
        if (snapshot == null) {
            applyBuiltChartOverlaySnapshot(buildVersion,
                    ChartOverlaySnapshot.empty(),
                    buildAccountOverlaySignature(cache, null),
                    resolveOverlayAccount(cache),
                    resolveOverlayServer(cache));
            return;
        }
        if (ioExecutor == null || ioExecutor.isShutdown()) {
            ChartOverlaySnapshot overlaySnapshot = overlaySnapshotFactory().build(
                    selectedSymbol,
                    new ArrayList<>(loadedCandles),
                    snapshot,
                    cache,
                    cache == null ? null : runtimeSnapshotStore.selectChartProductRuntime(selectedSymbol)
            );
            applyBuiltChartOverlaySnapshot(buildVersion,
                    overlaySnapshot,
                    buildAccountOverlaySignature(cache, snapshot),
                    resolveOverlayAccount(cache),
                    resolveOverlayServer(cache));
            return;
        }
        String overlaySignature = buildAccountOverlaySignature(cache, snapshot);
        String overlayAccount = resolveOverlayAccount(cache);
        String overlayServer = resolveOverlayServer(cache);
        List<CandleEntry> candleSnapshot = new ArrayList<>(loadedCandles);
        final com.binance.monitor.runtime.state.model.ChartProductRuntimeModel chartRuntimeModel =
                cache == null ? null : runtimeSnapshotStore.selectChartProductRuntime(selectedSymbol);
        chartOverlayBuildTask = ioExecutor.submit(() -> {
            ChartOverlaySnapshot overlaySnapshot = overlaySnapshotFactory().build(
                    selectedSymbol,
                    candleSnapshot,
                    snapshot,
                    cache,
                    chartRuntimeModel
            );
            mainHandler.post(() -> applyBuiltChartOverlaySnapshot(
                    buildVersion,
                    overlaySnapshot,
                    overlaySignature,
                    overlayAccount,
                    overlayServer
            ));
        });
    }

    // 主线程只消费已经准备好的叠加层快照，按“图上标注 -> 轻量状态”顺序提交。
    private void applyBuiltChartOverlaySnapshot(int buildVersion,
                                                @NonNull ChartOverlaySnapshot overlaySnapshot,
                                                @NonNull String overlaySignature,
                                                @Nullable String overlayAccount,
                                                @Nullable String overlayServer) {
        if (binding == null || binding.klineChartView == null || isFinishing() || isDestroyed()) {
            return;
        }
        if (buildVersion != chartOverlayBuildVersion) {
            return;
        }
        boolean overlayChanged = ChartOverlayRefreshDiff.hasOverlayVisualChange(
                lastChartOverlaySnapshot,
                overlaySnapshot
        );
        lastChartOverlaySnapshot = overlaySnapshot;
        dispatchChartRefresh(
                ChartRefreshEvent.productRuntimeChanged(overlayChanged),
                () -> {
                    binding.klineChartView.setPositionAnnotations(overlaySnapshot.getPositionAnnotations());
                    binding.klineChartView.setPendingAnnotations(overlaySnapshot.getPendingAnnotations());
                    binding.klineChartView.setHistoryTradeAnnotations(overlaySnapshot.getHistoryTradeAnnotations());
                    binding.klineChartView.setTradeLayerSnapshot(overlaySnapshot.getTradeLayerSnapshot());
                    binding.klineChartView.setAggregateCostAnnotation(overlaySnapshot.getAggregateCostAnnotation());
                },
                null
        );
        syncLastAppliedOverlayIdentity(overlayAccount, overlayServer);
        lastAccountOverlaySignature = overlaySignature;
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

    void clearStartupPrimaryDrawObserver() {
        if (binding != null && binding.klineChartView != null && pendingStartupDrawListener != null) {
            ViewTreeObserver observer = binding.klineChartView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnDrawListener(pendingStartupDrawListener);
            }
        }
        pendingStartupDrawListener = null;
        pendingStartupDrawKey = "";
    }

    // 图表页离开前统一解绑账户缓存监听，避免后台继续触发图上叠加刷新。
    void attachAccountCacheListener() {
        if (accountStatsPreloadManager != null) {
            accountStatsPreloadManager.addCacheListener(accountCacheListener);
        }
    }

    void detachAccountCacheListener() {
        if (accountStatsPreloadManager != null) {
            accountStatsPreloadManager.removeCacheListener(accountCacheListener);
        }
    }

    // 页面销毁时统一关闭图表 IO 执行器，避免多个宿主入口重复展开相同逻辑。
    void shutdownIoExecutor() {
        mainHandler.removeCallbacks(realtimeTailDrainRunnable);
        mainHandler.removeCallbacks(overlaySummaryDrainRunnable);
        mainHandler.removeCallbacks(dialogDrainRunnable);
        realtimeTailDrainScheduled = false;
        overlaySummaryDrainScheduled = false;
        dialogDrainScheduled = false;
        pendingRealtimeTailKline = null;
        pendingOverlayRefreshAction = null;
        pendingSummaryRefreshAction = null;
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
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
        lastChartOverlaySnapshot = ChartOverlaySnapshot.empty();
        lastAccountOverlaySignature = buildAccountOverlaySignature(
                accountStatsPreloadManager == null ? null : accountStatsPreloadManager.getLatestCache(),
                null
        );
        syncLastAppliedOverlayIdentity("", "");
        binding.klineChartView.setPositionAnnotations(new ArrayList<>());
        binding.klineChartView.setPendingAnnotations(new ArrayList<>());
        binding.klineChartView.setHistoryTradeAnnotations(new ArrayList<>());
        binding.klineChartView.setTradeLayerSnapshot(new ChartTradeLayerSnapshot(null, null));
        binding.klineChartView.setAggregateCostAnnotation(null);
        bindChartOverlayStatus(lastChartOverlaySnapshot, SensitiveDisplayMasker.isEnabled(this));
    }

    // 用轻量签名识别账户叠加层是否真的变化，避免无效重算。
    private String buildAccountOverlaySignature(@Nullable AccountStatsPreloadManager.Cache cache,
                                                @Nullable AccountSnapshot snapshot) {
        AccountSnapshot resolvedSnapshot = snapshot;
        if (resolvedSnapshot == null && cache != null) {
            resolvedSnapshot = cache.getSnapshot();
        }
        return overlaySnapshotFactory().buildInputSignature(
                selectedSymbol,
                loadedCandles,
                resolvedSnapshot,
                cache
        );
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

    private void cancelChartOverlayBuildTask() {
        if (chartOverlayBuildTask == null) {
            return;
        }
        chartOverlayBuildTask.cancel(true);
        chartOverlayBuildTask = null;
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

    private boolean isCurrentOverlayBoundToActiveSession() {
        String expectedAccount = resolveActiveSessionAccount();
        String expectedServer = resolveActiveSessionServer();
        if (expectedAccount.isEmpty() || expectedServer.isEmpty()) {
            return false;
        }
        return expectedAccount.equalsIgnoreCase(lastAppliedOverlayAccount)
                && expectedServer.equalsIgnoreCase(lastAppliedOverlayServer);
    }

    private void syncLastAppliedOverlayIdentity(@Nullable String account, @Nullable String server) {
        lastAppliedOverlayAccount = trimToEmpty(account);
        lastAppliedOverlayServer = trimToEmpty(server);
    }

    @NonNull
    private String resolveOverlayAccount(@Nullable AccountStatsPreloadManager.Cache cache) {
        return cache == null ? resolveActiveSessionAccount() : trimToEmpty(cache.getAccount());
    }

    @NonNull
    private String resolveOverlayServer(@Nullable AccountStatsPreloadManager.Cache cache) {
        return cache == null ? resolveActiveSessionServer() : trimToEmpty(cache.getServer());
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

    private List<KlineChartView.PriceAnnotation> buildHistoricalTradeAnnotations(List<TradeRecordItem> trades) {
        List<KlineChartView.PriceAnnotation> result = new ArrayList<>();
        List<HistoricalTradeAnnotationBuilder.TradeAnnotation> source =
                HistoricalTradeAnnotationBuilder.build(selectedSymbol, trades, loadedCandles);
        for (HistoricalTradeAnnotationBuilder.TradeAnnotation item : source) {
            if (item == null) {
                continue;
            }
            String sideLabel = "SELL".equalsIgnoreCase(item.side) ? "卖出" : "买入";
            int entryColor = tradeEntryColor(item.side);
            int connectorColor = pnlColor(item.totalPnl);
            int exitColor = historyExitColor();
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

    @NonNull
    private ChartOverlaySnapshotFactory overlaySnapshotFactory() {
        if (chartOverlaySnapshotFactory == null) {
            chartOverlaySnapshotFactory = new ChartOverlaySnapshotFactory(createChartOverlayColorScheme());
        }
        return chartOverlaySnapshotFactory;
    }

    @NonNull
    private ChartOverlaySnapshotFactory.ColorScheme createChartOverlayColorScheme() {
        return new ChartOverlaySnapshotFactory.ColorScheme(
                colorToken(R.color.trade_buy),
                colorToken(R.color.trade_sell),
                colorToken(R.color.pnl_profit),
                colorToken(R.color.pnl_loss),
                colorToken(R.color.text_primary)
        );
    }

    @NonNull
    private AbnormalAnnotationOverlayBuilder.ColorRange createAbnormalAnnotationColorRange() {
        return new AbnormalAnnotationOverlayBuilder.ColorRange(
                colorToken(R.color.state_warning),
                colorToken(R.color.trade_sell)
        );
    }

    private int colorToken(@ColorRes int resId) {
        return ContextCompat.getColor(this, resId);
    }

    private int tradeEntryColor(@Nullable String side) {
        return isSellSide(side) ? colorToken(R.color.trade_sell) : colorToken(R.color.trade_buy);
    }

    private int pnlColor(double value) {
        return value >= 0d ? colorToken(R.color.pnl_profit) : colorToken(R.color.pnl_loss);
    }

    private int takeProfitColor() {
        return colorToken(R.color.pnl_profit);
    }

    private int stopLossColor() {
        return colorToken(R.color.pnl_loss);
    }

    private int historyExitColor() {
        return colorToken(R.color.text_primary);
    }

    private List<KlineChartView.PriceAnnotation> buildPositionAnnotations(List<PositionItem> positions,
                                                                          List<TradeRecordItem> trades) {
        List<KlineChartView.PriceAnnotation> result = new ArrayList<>();
        if (positions == null || positions.isEmpty()) {
            return result;
        }
        int gainColor = colorToken(R.color.pnl_profit);
        int lossColor = colorToken(R.color.pnl_loss);
        int tpColor = takeProfitColor();
        int slColor = stopLossColor();
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
        int buyColor = colorToken(R.color.trade_buy);
        int sellColor = colorToken(R.color.trade_sell);
        int tpColor = takeProfitColor();
        int slColor = stopLossColor();
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
        return FormatUtils.formatSignedMoney(value);
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

    // 将左上角持仓摘要固定在K线价格绘图区内左上角，与价格区标题基线对齐。
    private boolean updateChartPositionSummaryPosition() {
        if (binding == null || binding.tvChartPositionSummary == null || binding.klineChartView == null) {
            return false;
        }
        if (pricePaneRightPx <= 0 || pricePaneBottomPx <= 0) {
            return false;
        }
        int viewHeight = binding.tvChartPositionSummary.getHeight();
        int baseline = binding.tvChartPositionSummary.getBaseline();
        if (viewHeight <= 0 || baseline <= 0) {
            binding.tvChartPositionSummary.post(this::updateChartPositionSummaryPosition);
            return false;
        }
        int inset = dpToPx(1f);
        int targetLeft = Math.max(0, pricePaneLeftPx);
        int targetBaseline = pricePaneTopPx + binding.klineChartView.getPricePaneTitleBaselineOffsetPx();
        int targetTop = Math.max(0, targetBaseline - baseline);
        int targetWidth = Math.max(0, pricePaneRightPx - pricePaneLeftPx - inset);
        if (binding.tvChartPositionSummary.getGravity() != Gravity.START) {
            binding.tvChartPositionSummary.setGravity(Gravity.START);
        }
        if (binding.tvChartPositionSummary.getTextAlignment() != View.TEXT_ALIGNMENT_VIEW_START) {
            binding.tvChartPositionSummary.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        }
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) binding.tvChartPositionSummary.getLayoutParams();
        int targetGravity = Gravity.TOP | Gravity.START;
        if (params.gravity == targetGravity
                && params.leftMargin == targetLeft
                && params.topMargin == targetTop
                && params.width == targetWidth
                && params.rightMargin == 0
                && params.bottomMargin == 0) {
            return true;
        }
        params.gravity = targetGravity;
        params.width = targetWidth;
        params.leftMargin = targetLeft;
        params.topMargin = targetTop;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        binding.tvChartPositionSummary.setLayoutParams(params);
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
        binding.tvChartSymbolPickerLabel.setCompoundDrawablePadding(
                getResources().getDimensionPixelSize(R.dimen.inline_gap)
        );
        binding.tvChartSymbolPickerLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, tintedArrow, null);
    }

    private void updateIntervalButtons() {
        styleIntervalSegmentedOption(binding.btnInterval1m, selectedInterval == INTERVALS[0]);
        styleIntervalSegmentedOption(binding.btnInterval5m, selectedInterval == INTERVALS[1]);
        styleIntervalSegmentedOption(binding.btnInterval15m, selectedInterval == INTERVALS[2]);
        styleIntervalSegmentedOption(binding.btnInterval30m, selectedInterval == INTERVALS[3]);
        styleIntervalSegmentedOption(binding.btnInterval1h, selectedInterval == INTERVALS[4]);
        styleIntervalSegmentedOption(binding.btnInterval4h, selectedInterval == INTERVALS[5]);
        styleIntervalSegmentedOption(binding.btnInterval1d, selectedInterval == INTERVALS[6]);
        styleIntervalSegmentedOption(binding.btnInterval1w, selectedInterval == INTERVALS[7]);
        styleIntervalSegmentedOption(binding.btnInterval1mo, selectedInterval == INTERVALS[8]);
        styleIntervalSegmentedOption(binding.btnInterval1y, selectedInterval == INTERVALS[9]);
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
        showHistoryTrades = preferences.getBoolean(PREF_KEY_SHOW_HISTORY_TRADES, false);
    }

    // 恢复图上持仓相关标注显示开关，避免用户每次重进都重新设置。
    private void restorePositionOverlayVisibility() {
        SharedPreferences preferences = getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE);
        showPositionOverlays = preferences.getBoolean(PREF_KEY_SHOW_POSITION_OVERLAYS, true);
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

    private void updatePositionOverlayToggleButton() {
        if (binding == null || binding.btnTogglePositionOverlays == null) {
            return;
        }
        binding.btnTogglePositionOverlays.setText(showPositionOverlays
                ? R.string.chart_position_overlays_on
                : R.string.chart_position_overlays_off);
        styleOverlayToggleOption(binding.btnTogglePositionOverlays, showPositionOverlays);
    }

    private void updateHistoryTradeToggleButton() {
        if (binding == null || binding.btnToggleHistoryTrades == null) {
            return;
        }
        binding.btnToggleHistoryTrades.setText(showHistoryTrades
                ? R.string.chart_history_trades_on
                : R.string.chart_history_trades_off);
        styleOverlayToggleOption(binding.btnToggleHistoryTrades, showHistoryTrades);
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
            if (option != null && key.equals(option.key)) {
                return option;
            }
        }
        return null;
    }

    private void updateIndicatorButtons() {
        styleIndicatorOption(binding.btnIndicatorVolume, showVolume);
        styleIndicatorOption(binding.btnIndicatorMacd, showMacd);
        styleIndicatorOption(binding.btnIndicatorStochRsi, showStochRsi);
        styleIndicatorOption(binding.btnIndicatorBoll, showBoll);
        styleIndicatorOption(binding.btnIndicatorMa, showMa);
        styleIndicatorOption(binding.btnIndicatorEma, showEma);
        styleIndicatorOption(binding.btnIndicatorSra, showSra);
        styleIndicatorOption(binding.btnIndicatorAvl, showAvl);
        styleIndicatorOption(binding.btnIndicatorRsi, showRsi);
        styleIndicatorOption(binding.btnIndicatorKdj, showKdj);
    }

    private void styleStripSegmentedOption(@Nullable MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setCheckable(true);
        button.setChecked(selected);
        CharSequence text = button.getText();
        UiPaletteManager.styleSegmentedOption(
                button,
                UiPaletteManager.resolve(this),
                text == null ? "" : text.toString(),
                R.style.TextAppearance_BinanceMonitor_ControlCompact
        );
    }

    private void styleIntervalSegmentedOption(@Nullable MaterialButton button, boolean selected) {
        styleStripSegmentedOption(button, selected);
    }

    private void styleIndicatorOption(@Nullable MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        button.setCheckable(true);
        button.setChecked(selected);
        UiPaletteManager.applyTextAppearance(button, R.style.TextAppearance_BinanceMonitor_ControlCompact);
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        button.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        button.setStrokeWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        int paddingHorizontalPx = SpacingTokenResolver.px(this, R.dimen.field_padding_x_compact);
        button.setPadding(paddingHorizontalPx, 0, paddingHorizontalPx, 0);
        button.setTextColor(resolveIndicatorOptionTextColor(selected, palette));
    }

    // 指标条切成透明容器后，仅靠文字颜色表达开关状态。
    private int resolveIndicatorOptionTextColor(boolean selected, @NonNull UiPaletteManager.Palette palette) {
        return selected ? palette.primary : palette.textSecondary;
    }

    private void styleOverlayToggleOption(@Nullable MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        button.setCheckable(true);
        button.setChecked(selected);
        UiPaletteManager.applyTextAppearance(button, R.style.TextAppearance_BinanceMonitor_Caption);
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        button.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        button.setStrokeWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setTextColor(resolveOverlayToggleTextColor(button, selected, palette));
    }

    // 图表底部两个透明开关在选中时用更明确的强调色区分状态。
    private int resolveOverlayToggleTextColor(@Nullable MaterialButton button,
                                              boolean selected,
                                              @NonNull UiPaletteManager.Palette palette) {
        return selected ? palette.primary : palette.textSecondary;
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
                binding.btnChartModeMarket, binding.btnChartModePending, binding.btnQuickTradeTemplate,
                binding.btnQuickTradePrimary, binding.btnQuickTradeSecondary
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
        UiPaletteManager.applySystemBars(activity, palette);
        binding.klineChartView.applyPalette(palette);
        binding.cardSymbolPanel.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.surfaceEnd, palette.stroke));
        binding.cardChartPanel.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.surfaceEnd, palette.stroke));
        binding.layoutIntervalStrip.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.card, palette.stroke));
        binding.layoutIndicatorStrip.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        binding.layoutChartOverlayToggleGroup.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        applyTopControlGroupStyles();
        binding.btnRetryLoad.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
        binding.btnRetryLoad.setTextColor(colorToken(R.color.text_inverse));
        styleQuickTradeInputField(binding.etQuickTradeVolume);
        binding.tvChartState.setTextColor(palette.textSecondary);
        binding.tvChartLoading.setTextColor(palette.textSecondary);
        binding.tvError.setTextColor(palette.fall);
        binding.tvChartPositionSummary.setTextColor(palette.textPrimary);
        binding.btnScrollToLatest.setBackground(UiPaletteManager.createFilledDrawable(
                this,
                ColorUtils.setAlphaComponent(palette.card, 224)));
        binding.btnScrollToLatest.setImageTintList(ColorStateList.valueOf(palette.textPrimary));
        if (binding.spinnerSymbolPicker.getAdapter() instanceof BaseAdapter) {
            ((BaseAdapter) binding.spinnerSymbolPicker.getAdapter()).notifyDataSetChanged();
        }
        if (pageController != null) {
            pageController.onPageShown();
        }
        syncSymbolSelector();
        updateIntervalButtons();
        updateIndicatorButtons();
        dispatchChartRefresh(ChartRefreshEvent.dialogStateChanged(), null, null);
        updatePositionOverlayToggleButton();
        updateHistoryTradeToggleButton();
        applyPrivacyMaskState();
        refreshGlobalStatusButton();
    }

    // 图表页交易模式和快捷交易区统一收口到标准主体语言。
    private void updateTradeActionButtons() {
        styleQuickTradeModeSegmentedOption(binding.btnChartModeMarket, quickTradeMode == ChartQuickTradeMode.MARKET);
        styleQuickTradeModeSegmentedOption(binding.btnChartModePending, quickTradeMode == ChartQuickTradeMode.PENDING);
        styleQuickTradeTemplateButton(binding.btnQuickTradeTemplate);
        styleQuickTradeActionButton(binding.btnQuickTradePrimary, true);
        styleQuickTradeActionButton(binding.btnQuickTradeSecondary, false);
    }

    // 模板入口保持低频中性按钮样式，不与买卖动作语义混色。
    private void styleQuickTradeTemplateButton(@Nullable Button button) {
        if (button == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.styleActionButton(
                button,
                palette,
                palette.control,
                UiPaletteManager.controlUnselectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
    }

    // 顶部产品字段和状态入口统一走标准主体，避免同组控件继续分出独立壳子。
    private void applyTopControlGroupStyles() {
        binding.spinnerSymbolPicker.setBackground(null);
        styleSymbolSelectFieldLabel(binding.tvChartSymbolPickerLabel);
        styleHeaderActionButton(binding.btnGlobalStatus, false);
        applyChartSymbolPickerIndicator();
    }

    // 顶部产品选择字段使用 SelectField 真值，不再继续扩散旧顶部标签入口。
    private void styleSymbolSelectFieldLabel(@Nullable TextView label) {
        if (label == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.styleSelectFieldLabel(
                label,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_ControlCompact,
                8,
                R.dimen.subject_height_compact
        );
        label.setPadding(
                SpacingTokenResolver.px(this, R.dimen.field_padding_x_compact),
                0,
                SpacingTokenResolver.px(this, R.dimen.field_trailing_reserve_compact),
                0
        );
    }

    // 顶部状态入口使用 ActionButton 真值，和相邻字段保持同一外壳语言。
    private void styleHeaderActionButton(@Nullable TextView button, boolean selected) {
        if (button == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        int fillColor = selected ? palette.primarySoft : palette.control;
        int textColor = selected
                ? UiPaletteManager.controlSelectedText(this)
                : UiPaletteManager.controlUnselectedText(this);
        UiPaletteManager.styleActionButton(
                button,
                palette,
                fillColor,
                textColor,
                R.style.TextAppearance_BinanceMonitor_ControlCompact,
                8,
                R.dimen.subject_height_compact
        );
    }

    // 交易模式切换属于互斥选择，统一走 SegmentedOption 真值。
    private void styleQuickTradeModeSegmentedOption(@Nullable MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setCheckable(true);
        button.setChecked(selected);
        CharSequence text = button.getText();
        UiPaletteManager.styleSegmentedOption(
                button,
                UiPaletteManager.resolve(this),
                text == null ? "" : text.toString(),
                R.style.TextAppearance_BinanceMonitor_ControlCompact
        );
        int minWidthPx = getResources().getDimensionPixelSize(R.dimen.chart_top_mode_button_min_width);
        int paddingHorizontalPx = SpacingTokenResolver.px(this, R.dimen.field_padding_x);
        button.setMinWidth(minWidthPx);
        button.setMinimumWidth(minWidthPx);
        button.setPadding(paddingHorizontalPx, 0, paddingHorizontalPx, 0);
    }

    // 快捷交易按钮保留买卖语义色，但统一到 ActionButton 主体。
    private void styleQuickTradeActionButton(@Nullable Button button, boolean primaryAction) {
        if (button == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        int accentColor = primaryAction ? palette.rise : palette.fall;
        UiPaletteManager.styleActionButton(
                button,
                palette,
                ColorUtils.setAlphaComponent(accentColor, 26),
                accentColor,
                R.style.TextAppearance_BinanceMonitor_ControlCompact,
                8,
                R.dimen.subject_height_md
        );
    }

    // 快捷交易数量输入保持输入框主体，不再让页面自己拼一套伪字段外壳。
    private void styleQuickTradeInputField(@Nullable EditText input) {
        if (input == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.styleInputField(input, palette, R.style.TextAppearance_BinanceMonitor_Control);
    }

    private void styleIndicatorParamDialogActions(@NonNull AlertDialog dialog) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.styleActionButton(
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE),
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                dialog.getButton(AlertDialog.BUTTON_POSITIVE),
                palette,
                palette.primarySoft,
                UiPaletteManager.controlSelectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
    }

    void cancelChartBackgroundTasks() {
        if (runningTask != null) {
            runningTask.cancel(true);
            runningTask = null;
        }
        if (loadMoreTask != null) {
            loadMoreTask.cancel(true);
            loadMoreTask = null;
        }
        cancelChartOverlayBuildTask();
        cancelProgressiveGapFillTask();
    }
}

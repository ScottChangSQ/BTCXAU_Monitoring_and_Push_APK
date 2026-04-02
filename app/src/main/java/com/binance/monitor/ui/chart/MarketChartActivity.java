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
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.KlineCacheStore;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.local.db.repository.ChartHistoryRepository;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.data.remote.BinanceApiClient;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.databinding.ActivityMarketChartBinding;
import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.ui.account.AccountSnapshotDisplayResolver;
import com.binance.monitor.ui.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.account.adapter.PendingOrderAdapter;
import com.binance.monitor.ui.account.adapter.PositionAdapterV2;
import com.binance.monitor.ui.account.adapter.PositionAggregateAdapter;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MarketChartActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET_SYMBOL = "extra_target_symbol";
    public static final String PREF_RUNTIME_NAME = "market_chart_runtime";
    private static final String PREF_KEY_SELECTED_INTERVAL = "selected_interval";

    private static final int HISTORY_PERSIST_LIMIT = 5_000;
    private static final int FULL_WINDOW_LIMIT = 1500;
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
    private BinanceApiClient apiClient;
    private MonitorRepository monitorRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService ioExecutor;
    private Future<?> runningTask;
    private Future<?> loadMoreTask;
    private long runningTaskStartMs;
    private int requestVersion = 0;
    private final Map<String, List<CandleEntry>> klineCache = new ConcurrentHashMap<>();
    private final Map<String, String> persistedSignatures = new ConcurrentHashMap<>();
    private KlineCacheStore klineCacheStore;
    private ChartHistoryRepository chartHistoryRepository;
    private AccountStorageRepository accountStorageRepository;
    private AccountStatsPreloadManager accountStatsPreloadManager;
    private AbnormalRecordManager abnormalRecordManager;

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
    private int pricePaneLeftPx;
    private int pricePaneTopPx;
    private int pricePaneRightPx;
    private int pricePaneBottomPx;
    private long nextAutoRefreshAtMs;
    private volatile boolean loadingMore;
    private long lastSuccessUpdateMs;
    private long lastSuccessfulRequestLatencyMs = -1L;
    private List<AbnormalRecord> abnormalRecords = new ArrayList<>();
    private final Map<String, Long> lastRealtimeClosedKlineAt = new ConcurrentHashMap<>();
    private final Set<String> minuteCachePreloadInFlight = ConcurrentHashMap.newKeySet();
    private final AccountStatsPreloadManager.CacheListener accountCacheListener = cache -> refreshChartOverlays();
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMarketChartBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        apiClient = new BinanceApiClient(this);
        monitorRepository = MonitorRepository.getInstance(this);
        ioExecutor = Executors.newFixedThreadPool(2);
        klineCacheStore = new KlineCacheStore(this);
        chartHistoryRepository = new ChartHistoryRepository(this);
        accountStorageRepository = new AccountStorageRepository(getApplicationContext());
        accountStatsPreloadManager = AccountStatsPreloadManager.getInstance(getApplicationContext());
        abnormalRecordManager = AbnormalRecordManager.getInstance(getApplicationContext());
        applyIntentSymbol(getIntent(), false);
        restoreSelectedInterval();
        if (abnormalRecordManager != null) {
            abnormalRecordManager.getRecordsLiveData().observe(this, records -> {
                abnormalRecords = records == null ? new ArrayList<>() : new ArrayList<>(records);
                updateAbnormalAnnotationsOverlay();
            });
        }
        if (monitorRepository != null) {
            monitorRepository.getLatestClosedKlines().observe(this, this::handleRealtimeClosedKlines);
        }

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
        if (monitorRepository != null) {
            handleRealtimeClosedKlines(monitorRepository.getLatestClosedKlineSnapshot());
        }
        requestKlines();
        ensureMinuteBaseCacheAsync(selectedSymbol);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
        applyPrivacyMaskState();
        if (accountStatsPreloadManager != null) {
            accountStatsPreloadManager.addCacheListener(accountCacheListener);
            accountStatsPreloadManager.setFullSnapshotActive(true);
        }
        requestKlines();
        refreshChartOverlays();
        startAutoRefresh();
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
        if (accountStatsPreloadManager != null) {
            accountStatsPreloadManager.removeCacheListener(accountCacheListener);
            accountStatsPreloadManager.setFullSnapshotActive(false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopAutoRefresh();
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
            if (positioned && binding.klineChartView.isLatestCandleOutOfBounds()) {
                binding.btnScrollToLatest.setVisibility(android.view.View.VISIBLE);
            }
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
        binding.btnScrollToLatest.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateScrollToLatestButtonPosition());
        binding.tvChartRefreshCountdown.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateRefreshCountdownPosition());
        binding.klineChartView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
        {
            updateRefreshCountdownPosition();
            updateScrollToLatestButtonPosition();
        });
        binding.btnScrollToLatest.setVisibility(android.view.View.INVISIBLE);
        refreshChartOverlays();
    }

    // 根据隐私状态控制敏感叠加层和当前持仓模块，不再使用整页遮罩。
    private void applyPrivacyMaskState() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        boolean masked = SensitiveDisplayMasker.isEnabled(this);
        binding.klineChartView.setOverlayVisibility(!masked, !masked, !masked, !masked);
        updateChartPositionPanel(lastChartPositions, lastChartPendingOrders);
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
            requestKlines();
            ensureMinuteBaseCacheAsync(selectedSymbol);
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

    private void setupChartPositionPanel() {
        chartPositionAggregateAdapter = new PositionAggregateAdapter();
        chartPositionAdapter = new PositionAdapterV2();
        chartPendingOrderAdapter = new PendingOrderAdapter();
        binding.recyclerChartPositionByProduct.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerChartPositionByProduct.setAdapter(chartPositionAggregateAdapter);
        binding.recyclerChartPositions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerChartPositions.setItemAnimator(null);
        binding.recyclerChartPositions.setAdapter(chartPositionAdapter);
        binding.recyclerChartPendingOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerChartPendingOrders.setItemAnimator(null);
        binding.recyclerChartPendingOrders.setAdapter(chartPendingOrderAdapter);
        updateChartPositionPanel(new ArrayList<>(), new ArrayList<>());
    }

    private void updateChartPositionPanel(List<PositionItem> positions, List<PositionItem> pendingOrders) {
        if (binding == null
                || chartPositionAggregateAdapter == null
                || chartPositionAdapter == null
                || chartPendingOrderAdapter == null) {
            return;
        }
        lastChartPositions.clear();
        if (positions != null) {
            lastChartPositions.addAll(positions);
        }
        lastChartPendingOrders.clear();
        if (pendingOrders != null) {
            lastChartPendingOrders.addAll(pendingOrders);
        }
        boolean masked = SensitiveDisplayMasker.isEnabled(this);
        List<PositionItem> filteredPositions = new ArrayList<>();
        if (positions != null) {
            for (PositionItem item : positions) {
                if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                    continue;
                }
                filteredPositions.add(item);
            }
        }
        filteredPositions.sort(Comparator.comparing(PositionItem::getProductName));

        List<PositionItem> filteredPendingOrders = new ArrayList<>();
        if (pendingOrders != null) {
            for (PositionItem item : pendingOrders) {
                if (item == null) {
                    continue;
                }
                double pendingLots = resolvePendingLots(item);
                if (pendingLots <= 1e-9 && item.getPendingCount() <= 0) {
                    continue;
                }
                filteredPendingOrders.add(item);
            }
        }
        filteredPendingOrders.sort((a, b) -> Double.compare(resolvePendingSortWeight(b), resolvePendingSortWeight(a)));

        chartPositionAggregateAdapter.setMasked(masked);
        chartPositionAdapter.setMasked(masked);
        chartPendingOrderAdapter.setMasked(masked);
        chartPositionAggregateAdapter.submitList(buildPositionAggregatesForChart(filteredPositions));
        chartPositionAdapter.submitList(filteredPositions);
        chartPendingOrderAdapter.submitList(filteredPendingOrders);
        binding.tvChartPendingOrdersTitle.setVisibility(View.VISIBLE);
        binding.recyclerChartPendingOrders.setVisibility(View.VISIBLE);
        if (binding.tvChartPendingOrdersEmpty != null) {
            binding.tvChartPendingOrdersEmpty.setVisibility(filteredPendingOrders.isEmpty() ? View.VISIBLE : View.GONE);
        }

        double totalPnl = 0d;
        double totalMarketValue = 0d;
        for (PositionItem item : filteredPositions) {
            totalPnl += item.getTotalPnL() + item.getStorageFee();
            totalMarketValue += Math.max(0d, Math.abs(item.getMarketValue()));
        }
        double ratio = totalMarketValue <= 1e-9 ? 0d : totalPnl / totalMarketValue;
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
            spannable.setSpan(new AbsoluteSizeSpan(16, true),
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
            spannable.setSpan(new AbsoluteSizeSpan(15, true),
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
        requestKlines();
        ensureMinuteBaseCacheAsync(selectedSymbol);
        scheduleNextAutoRefresh();
    }

    private void switchInterval(IntervalOption option) {
        if (option == null || option.key.equals(selectedInterval.key)) {
            return;
        }
        selectedInterval = option;
        persistSelectedInterval();
        updateIntervalButtons();
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

        boolean shouldWarmDisplay = !autoRefresh || loadedCandles.isEmpty() || !key.equals(activeDataKey);
        if (shouldWarmDisplay) {
            List<CandleEntry> cached = getCachedOrPersisted(key);
            if (cached == null || cached.isEmpty()) {
                cached = buildWarmDisplayCandles(selectedSymbol, selectedInterval);
            }
            if (cached != null && !cached.isEmpty()) {
                loadedCandles.clear();
                loadedCandles.addAll(cached);
                activeDataKey = key;
                binding.klineChartView.setCandles(loadedCandles);
                renderInfoWithLatest();
                updateStateCount();
                refreshChartOverlays();
            }
        }

        List<CandleEntry> localForPlan = key.equals(activeDataKey)
                ? new ArrayList<>(loadedCandles)
                : getCachedOrPersisted(key);
        MarketChartRefreshHelper.SyncPlan refreshPlan = MarketChartRefreshHelper.resolvePlan(
                localForPlan,
                selectedInterval.limit,
                FULL_WINDOW_LIMIT,
                System.currentTimeMillis(),
                resolveLatestRealtimeClosedTime(selectedSymbol),
                intervalToMs(selectedInterval.key),
                selectedInterval.yearAggregate
        );
        if (refreshPlan.mode == MarketChartRefreshHelper.SyncMode.SKIP) {
            binding.tvError.setVisibility(android.view.View.GONE);
            binding.btnRetryLoad.setVisibility(android.view.View.GONE);
            showLoading(false);
            return;
        }

        final int current = ++requestVersion;
        final long previousLatestOpenTime = loadedCandles.isEmpty()
                ? -1L
                : loadedCandles.get(loadedCandles.size() - 1).getOpenTime();
        final long requestStartedAtMs = SystemClock.elapsedRealtime();
        showLoading(true);
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.btnRetryLoad.setVisibility(android.view.View.GONE);
        runningTaskStartMs = System.currentTimeMillis();
        final List<CandleEntry> refreshSeed = localForPlan == null ? new ArrayList<>() : new ArrayList<>(localForPlan);
        runningTask = ioExecutor.submit(() -> {
            try {
                List<CandleEntry> source = loadCandlesForRequest(refreshPlan, refreshSeed, previousLatestOpenTime);
                List<CandleEntry> processed = selectedInterval.yearAggregate
                        ? aggregateToYear(source, selectedSymbol)
                        : source;
                if (processed.isEmpty()) {
                    throw new IllegalStateException("币安未返回可用K线数据");
                }
                mainHandler.post(() -> {
                    if (current != requestVersion || isFinishing() || isDestroyed()) {
                        return;
                    }
                    List<CandleEntry> toDisplay = mergeLatestData(new ArrayList<>(), processed);
                    activeDataKey = key;
                    boolean candlesChanged = !isSameCandleSeries(loadedCandles, toDisplay);
                    boolean shouldFollowLatest = autoRefresh
                            && binding != null
                            && binding.klineChartView != null
                            && binding.klineChartView.isFollowingLatestViewport();
                    if (candlesChanged) {
                        loadedCandles.clear();
                        loadedCandles.addAll(toDisplay);
                        klineCache.put(key, new ArrayList<>(toDisplay));
                        if (autoRefresh) {
                            binding.klineChartView.setCandlesKeepingViewport(loadedCandles);
                            if (shouldFollowLatest) {
                                binding.klineChartView.scrollToLatest();
                            }
                        } else {
                            binding.klineChartView.setCandles(loadedCandles);
                        }
                        persistCurrentCandles(key);
                    }
                    refreshChartOverlays();
                    if (!(autoRefresh && binding.klineChartView.hasActiveCrosshair())) {
                        renderInfoWithLatest();
                    }
                    if ("1m".equalsIgnoreCase(selectedInterval.key) && !selectedInterval.yearAggregate) {
                        warmDerivedIntervalCachesFromMinuteAsync(selectedSymbol, toDisplay);
                    } else {
                        ensureMinuteBaseCacheAsync(selectedSymbol);
                    }
                    lastSuccessfulRequestLatencyMs = Math.max(0L, SystemClock.elapsedRealtime() - requestStartedAtMs);
                    lastSuccessUpdateMs = System.currentTimeMillis();
                    updateStateCount();
                    binding.tvError.setVisibility(android.view.View.GONE);
                    binding.btnRetryLoad.setVisibility(android.view.View.GONE);
                    showLoading(false);
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
                    if (loadedCandles.isEmpty()) {
                        binding.klineChartView.setCandles(new ArrayList<>());
                        refreshChartOverlays();
                        binding.tvChartInfo.setText(R.string.chart_info_empty);
                    }
                    updateStateCount();
                    binding.tvError.setText(getString(R.string.chart_error_prefix, message));
                    binding.tvError.setVisibility(android.view.View.VISIBLE);
                    binding.btnRetryLoad.setVisibility(autoRefresh ? android.view.View.GONE : android.view.View.VISIBLE);
                    showLoading(false);
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
        if (loadMoreTask != null) {
            loadMoreTask.cancel(true);
        }
        loadMoreTask = ioExecutor.submit(() -> {
            try {
                List<CandleEntry> fetched = apiClient.fetchChartKlineHistoryBefore(
                        reqSymbol,
                        reqInterval.apiInterval,
                        FULL_WINDOW_LIMIT,
                        beforeOpenTime - 1L
                );
                List<CandleEntry> processed = reqInterval.yearAggregate
                        ? aggregateToYear(fetched, reqSymbol)
                        : fetched;
                if (processed.isEmpty()) {
                    mainHandler.post(() -> {
                        loadingMore = false;
                        binding.klineChartView.notifyLoadMoreFinished();
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
                        long oldest = loadedCandles.get(0).getOpenTime();
                        Set<Long> existing = ConcurrentHashMap.newKeySet();
                        for (CandleEntry item : loadedCandles) {
                            existing.add(item.getOpenTime());
                        }
                    List<CandleEntry> older = new ArrayList<>();
                        for (CandleEntry item : processed) {
                            if (item.getOpenTime() < oldest && !existing.contains(item.getOpenTime())) {
                                older.add(item);
                                existing.add(item.getOpenTime());
                            }
                        }
                        if (older.isEmpty()) {
                            return;
                        }
                        Collections.sort(older, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));
                        loadedCandles.addAll(0, older);
                        String cacheKey = buildCacheKey(reqSymbol, reqInterval);
                        klineCache.put(cacheKey, new ArrayList<>(loadedCandles));
                        persistCurrentCandles(cacheKey);
                        binding.klineChartView.prependCandles(older);
                        refreshChartOverlays();
                        updateStateCount();
                    } finally {
                        loadingMore = false;
                        binding.klineChartView.notifyLoadMoreFinished();
                    }
                });
            } catch (Exception ignored) {
                mainHandler.post(() -> {
                    loadingMore = false;
                    binding.klineChartView.notifyLoadMoreFinished();
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

    private long resolveLatestRealtimeClosedTime(@Nullable String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return 0L;
        }
        Long latest = lastRealtimeClosedKlineAt.get(symbol.trim().toUpperCase(Locale.ROOT));
        return latest == null ? 0L : latest;
    }

    private List<CandleEntry> mergeLatestData(List<CandleEntry> existing, List<CandleEntry> latest) {
        if (latest == null || latest.isEmpty()) {
            return existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        }
        Map<Long, CandleEntry> map = new LinkedHashMap<>();
        if (existing != null) {
            for (CandleEntry item : existing) {
                map.put(item.getOpenTime(), item);
            }
        }
        for (CandleEntry item : latest) {
            map.put(item.getOpenTime(), item);
        }
        List<CandleEntry> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));
        return out;
    }

    // 切周期时，优先尝试用本地已有小周期缓存快速聚合出目标周期，减少首次等待网络的空窗。
    private List<CandleEntry> buildWarmDisplayCandles(String symbol, IntervalOption targetInterval) {
        if (symbol == null || symbol.trim().isEmpty() || targetInterval == null) {
            return new ArrayList<>();
        }
        List<CandleEntry> best = new ArrayList<>();
        long bestSourceDurationMs = Long.MAX_VALUE;
        for (IntervalOption candidate : INTERVALS) {
            if (!canWarmDisplayFrom(candidate, targetInterval)) {
                continue;
            }
            List<CandleEntry> source = getCachedOrPersisted(buildCacheKey(symbol, candidate));
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
            if (aggregated.isEmpty()) {
                continue;
            }
            long candidateDurationMs = intervalToMs(candidate.key);
            if (candidateDurationMs < bestSourceDurationMs
                    || (candidateDurationMs == bestSourceDurationMs && aggregated.size() > best.size())) {
                bestSourceDurationMs = candidateDurationMs;
                best = aggregated;
            }
        }
        return best;
    }

    private boolean canWarmDisplayFrom(@Nullable IntervalOption source, @Nullable IntervalOption target) {
        if (source == null || target == null || source == target) {
            return false;
        }
        long sourceDurationMs = intervalToMs(source.key);
        long targetDurationMs = intervalToMs(target.key);
        if (sourceDurationMs <= 0L || targetDurationMs <= 0L || sourceDurationMs > targetDurationMs) {
            return false;
        }
        if (target.yearAggregate || "1w".equalsIgnoreCase(target.key) || "1M".equalsIgnoreCase(target.key)) {
            return true;
        }
        return targetDurationMs % sourceDurationMs == 0L;
    }

    // 接入监控服务正在推送的 1 分钟已收盘 K 线，让当前周期图不用等下一次整窗请求才刷新。
    private void handleRealtimeClosedKlines(@Nullable Map<String, KlineData> klines) {
        if (klines == null || klines.isEmpty()) {
            return;
        }
        for (Map.Entry<String, KlineData> entry : klines.entrySet()) {
            KlineData data = entry == null ? null : entry.getValue();
            if (data == null || !data.isClosed()) {
                continue;
            }
            long closeTime = data.getCloseTime();
            String symbol = data.getSymbol();
            long lastApplied = lastRealtimeClosedKlineAt.containsKey(symbol)
                    ? lastRealtimeClosedKlineAt.get(symbol)
                    : 0L;
            if (closeTime <= lastApplied) {
                continue;
            }
            lastRealtimeClosedKlineAt.put(symbol, closeTime);
            applyRealtimeClosedKline(data);
        }
    }

    private void applyRealtimeClosedKline(@Nullable KlineData data) {
        if (data == null) {
            return;
        }
        CandleEntry minuteCandle = toMinuteCandleEntry(data);
        updateMinuteRealtimeCache(data.getSymbol(), minuteCandle);
        if (!data.getSymbol().equalsIgnoreCase(selectedSymbol) || binding == null || binding.klineChartView == null) {
            return;
        }
        String targetIntervalKey = selectedInterval.yearAggregate ? selectedInterval.key : selectedInterval.apiInterval;
        List<CandleEntry> updated = CandleAggregationHelper.mergeClosedBaseCandle(
                loadedCandles,
                minuteCandle,
                selectedSymbol,
                targetIntervalKey,
                selectedInterval.limit
        );
        if (updated.isEmpty() || isSameCandleSeries(loadedCandles, updated)) {
            return;
        }
        boolean shouldFollowLatest = binding.klineChartView.isFollowingLatestViewport();
        loadedCandles.clear();
        loadedCandles.addAll(updated);
        String key = buildCacheKey(selectedSymbol, selectedInterval);
        activeDataKey = key;
        klineCache.put(key, new ArrayList<>(updated));
        binding.klineChartView.setCandlesKeepingViewport(loadedCandles);
        if (shouldFollowLatest) {
            binding.klineChartView.scrollToLatest();
        }
        persistCurrentCandles(key);
        refreshChartOverlays();
        if (!binding.klineChartView.hasActiveCrosshair()) {
            renderInfoWithLatest();
        }
        updateStateCount();
    }

    private void updateMinuteRealtimeCache(String symbol, CandleEntry minuteCandle) {
        if (symbol == null || symbol.trim().isEmpty() || minuteCandle == null) {
            return;
        }
        IntervalOption minuteOption = INTERVALS[0];
        String minuteKey = buildCacheKey(symbol, minuteOption);
        List<CandleEntry> minuteSeries = mergeLatestData(getCachedOrPersisted(minuteKey), Collections.singletonList(minuteCandle));
        klineCache.put(minuteKey, new ArrayList<>(minuteSeries));
        persistCandlesAsync(minuteKey, minuteSeries, symbol, minuteOption);
        warmDerivedIntervalCachesFromMinuteAsync(symbol, minuteSeries);
    }

    private CandleEntry toMinuteCandleEntry(KlineData data) {
        return new CandleEntry(
                data.getSymbol(),
                data.getOpenTime(),
                data.getCloseTime(),
                data.getOpenPrice(),
                data.getHighPrice(),
                data.getLowPrice(),
                data.getClosePrice(),
                data.getVolume(),
                data.getQuoteAssetVolume()
        );
    }

    // 后台补齐 1 分钟底稿缓存，让切到更大周期时优先命中本地数据。
    private void ensureMinuteBaseCacheAsync(@Nullable String symbol) {
        if (symbol == null || symbol.trim().isEmpty() || ioExecutor == null || ioExecutor.isShutdown()) {
            return;
        }
        final String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        if (!minuteCachePreloadInFlight.add(normalizedSymbol)) {
            return;
        }
        ioExecutor.submit(() -> {
            try {
                IntervalOption minuteOption = INTERVALS[0];
                String minuteKey = buildCacheKey(normalizedSymbol, minuteOption);
                List<CandleEntry> existing = getCachedOrPersisted(minuteKey);
                if (existing != null && !existing.isEmpty()) {
                    warmDerivedIntervalCachesFromMinute(normalizedSymbol, existing);
                    return;
                }
                List<CandleEntry> fetched = apiClient.fetchChartKlineFullWindow(
                        normalizedSymbol,
                        minuteOption.apiInterval,
                        FULL_WINDOW_LIMIT
                );
                List<CandleEntry> minuteSeries = mergeLatestData(new ArrayList<>(), fetched);
                if (minuteSeries.isEmpty()) {
                    return;
                }
                klineCache.put(minuteKey, new ArrayList<>(minuteSeries));
                persistCandlesAsync(minuteKey, minuteSeries, normalizedSymbol, minuteOption);
                warmDerivedIntervalCachesFromMinute(normalizedSymbol, minuteSeries);
            } catch (Exception ignored) {
            } finally {
                minuteCachePreloadInFlight.remove(normalizedSymbol);
            }
        });
    }

    // 基于 1 分钟底稿异步扇出更高周期缓存，减少后续切周期时的等待。
    private void warmDerivedIntervalCachesFromMinuteAsync(@Nullable String symbol,
                                                          @Nullable List<CandleEntry> minuteSeries) {
        if (symbol == null
                || symbol.trim().isEmpty()
                || minuteSeries == null
                || minuteSeries.isEmpty()
                || ioExecutor == null
                || ioExecutor.isShutdown()) {
            return;
        }
        final String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        final List<CandleEntry> snapshot = new ArrayList<>(minuteSeries);
        ioExecutor.submit(() -> warmDerivedIntervalCachesFromMinute(normalizedSymbol, snapshot));
    }

    // 把 1 分钟序列派生为更高周期缓存，供切换周期时直接取本地预显示。
    private void warmDerivedIntervalCachesFromMinute(@Nullable String symbol,
                                                     @Nullable List<CandleEntry> minuteSeries) {
        if (symbol == null || symbol.trim().isEmpty() || minuteSeries == null || minuteSeries.isEmpty()) {
            return;
        }
        IntervalOption minuteOption = INTERVALS[0];
        for (IntervalOption option : INTERVALS) {
            if (option == null || option == minuteOption || !canWarmDisplayFrom(minuteOption, option)) {
                continue;
            }
            String targetIntervalKey = option.yearAggregate ? option.key : option.apiInterval;
            List<CandleEntry> aggregated = CandleAggregationHelper.aggregate(
                    minuteSeries,
                    symbol,
                    targetIntervalKey,
                    option.limit
            );
            if (aggregated.isEmpty()) {
                continue;
            }
            String targetKey = buildCacheKey(symbol, option);
            klineCache.put(targetKey, new ArrayList<>(aggregated));
            persistCandlesAsync(targetKey, aggregated, symbol, option);
        }
    }

    // 仅在数据实际变化时才重绘，减少无效 UI 更新。
    private boolean isSameCandleSeries(List<CandleEntry> left, List<CandleEntry> right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            CandleEntry l = left.get(i);
            CandleEntry r = right.get(i);
            if (l.getOpenTime() != r.getOpenTime()
                    || l.getCloseTime() != r.getCloseTime()
                    || Double.compare(l.getOpen(), r.getOpen()) != 0
                    || Double.compare(l.getHigh(), r.getHigh()) != 0
                    || Double.compare(l.getLow(), r.getLow()) != 0
                    || Double.compare(l.getClose(), r.getClose()) != 0
                    || Double.compare(l.getVolume(), r.getVolume()) != 0
                    || Double.compare(l.getQuoteVolume(), r.getQuoteVolume()) != 0) {
                return false;
            }
        }
        return true;
    }

    // 用于持久化去重，避免同一窗口反复写盘。
    private String buildSeriesSignature(List<CandleEntry> candles) {
        if (candles == null || candles.isEmpty()) {
            return "empty";
        }
        CandleEntry first = candles.get(0);
        CandleEntry last = candles.get(candles.size() - 1);
        return candles.size() + "|" + first.getOpenTime() + "|" + first.getCloseTime()
                + "|" + last.getOpenTime() + "|" + last.getCloseTime()
                + "|" + last.getClose() + "|" + last.getVolume() + "|" + last.getQuoteVolume();
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        scheduleNextAutoRefresh();
        mainHandler.post(refreshCountdownRunnable);
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
        mainHandler.post(refreshCountdownRunnable);
    }

    private void updateRefreshCountdownText() {
        if (binding == null || binding.tvChartRefreshCountdown == null) {
            return;
        }
        binding.tvChartRefreshCountdown.setText(ChartRefreshMetaFormatter.buildCountdownText(
                nextAutoRefreshAtMs,
                System.currentTimeMillis(),
                resolveAutoRefreshDelayMs(),
                lastSuccessfulRequestLatencyMs
        ));
        updateRefreshCountdownPosition();
    }

    private long resolveAutoRefreshDelayMs() {
        return AppConstants.CHART_AUTO_REFRESH_INTERVAL_MS;
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
            return symbol + "|default";
        }
        return symbol + "|" + interval.key + "|" + interval.apiInterval + "|" + interval.yearAggregate;
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

    private void restorePersistedCache(String key) {
        if (key == null || key.trim().isEmpty() || chartHistoryRepository == null) {
            return;
        }
        List<CandleEntry> persisted = chartHistoryRepository.loadCandles(key);
        if (persisted == null || persisted.isEmpty()) {
            return;
        }
        klineCache.put(key, new ArrayList<>(persisted));
        persistedSignatures.put(key, buildSeriesSignature(persisted));
        loadedCandles.clear();
        loadedCandles.addAll(persisted);
        activeDataKey = key;
        binding.klineChartView.setCandles(loadedCandles);
        refreshChartOverlays();
        renderInfoWithLatest();
    }

    private List<CandleEntry> getCachedOrPersisted(String key) {
        List<CandleEntry> cached = klineCache.get(key);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        if (chartHistoryRepository == null || key == null || key.trim().isEmpty()) {
            return cached;
        }
        List<CandleEntry> persisted = chartHistoryRepository.loadCandles(key);
        if (persisted != null && !persisted.isEmpty()) {
            klineCache.put(key, new ArrayList<>(persisted));
            persistedSignatures.put(key, buildSeriesSignature(persisted));
            return persisted;
        }
        return cached;
    }

    private void persistCurrentCandles(String key) {
        persistCandlesAsync(key, loadedCandles, selectedSymbol, selectedInterval);
    }

    // 避免主线程频繁写大文件，持久化统一异步执行且按签名去重。
    private void persistCandlesAsync(String key,
                                     List<CandleEntry> candles,
                                     String symbol,
                                     @Nullable IntervalOption interval) {
        if (key == null || key.trim().isEmpty() || candles == null || candles.isEmpty() || interval == null) {
            return;
        }
        List<CandleEntry> snapshot = new ArrayList<>(candles);
        String signature = buildSeriesSignature(snapshot);
        String persisted = persistedSignatures.get(key);
        if (signature.equals(persisted)) {
            return;
        }
        Runnable task = () -> {
            persistCandles(key, snapshot, symbol, interval);
            persistedSignatures.put(key, signature);
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
        chartHistoryRepository.mergeAndSave(
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
                                                    long previousLatestOpenTime) throws Exception {
        List<CandleEntry> result;
        if (plan != null && plan.mode == MarketChartRefreshHelper.SyncMode.INCREMENTAL) {
            List<CandleEntry> base = mergeLatestData(new ArrayList<>(), seed);
            List<CandleEntry> tail = apiClient.fetchKlineHistoryAfter(
                    selectedSymbol,
                    selectedInterval.apiInterval,
                    FULL_WINDOW_LIMIT,
                    plan.startTimeInclusive
            );
            result = mergeLatestData(base, tail);
        } else {
            result = fetchFullHistoryAndMark(selectedSymbol, FULL_WINDOW_LIMIT);
        }
        result = expandFullHistoryWhenGapDetected(selectedSymbol, selectedInterval, result, previousLatestOpenTime);
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("币安未返回可用K线数据");
        }
        return result;
    }

    private List<CandleEntry> expandFullHistoryWhenGapDetected(String symbol,
                                                               IntervalOption interval,
                                                               List<CandleEntry> latestWindow,
                                                               long previousLatestOpenTime) throws Exception {
        if (interval == null || latestWindow == null || latestWindow.isEmpty() || interval.yearAggregate) {
            return latestWindow == null ? new ArrayList<>() : latestWindow;
        }
        if (previousLatestOpenTime <= 0L) {
            return latestWindow;
        }
        long intervalMs = intervalToMs(interval.apiInterval);
        if (intervalMs <= 0L) {
            return latestWindow;
        }
        List<CandleEntry> merged = mergeLatestData(new ArrayList<>(), latestWindow);
        if (merged.isEmpty()) {
            return merged;
        }
        long latestOldest = merged.get(0).getOpenTime();
        if (latestOldest - previousLatestOpenTime <= intervalMs * 2L) {
            return merged;
        }

        int rounds = 0;
        while (latestOldest - previousLatestOpenTime > intervalMs * 2L && rounds < GAP_FILL_MAX_ROUNDS) {
            long endTime = Math.max(0L, latestOldest - 1L);
            List<CandleEntry> older = apiClient.fetchChartKlineHistoryBefore(
                    symbol,
                    interval.apiInterval,
                    FULL_WINDOW_LIMIT,
                    endTime);
            if (older == null || older.isEmpty()) {
                break;
            }
            int beforeSize = merged.size();
            long beforeOldest = latestOldest;
            merged = mergeLatestData(merged, older);
            latestOldest = merged.get(0).getOpenTime();
            if (merged.size() <= beforeSize || latestOldest >= beforeOldest) {
                break;
            }
            rounds++;
        }
        return merged;
    }

    private List<CandleEntry> fetchFullHistoryAndMark(String symbol, int limit) throws Exception {
        return apiClient.fetchChartKlineFullWindow(symbol, selectedInterval.apiInterval, limit);
    }

    private List<CandleEntry> aggregateToYear(List<CandleEntry> source, String symbol) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Integer, List<CandleEntry>> grouped = new LinkedHashMap<>();
        Calendar calendar = Calendar.getInstance();
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
        List<KlineChartView.PriceAnnotation> abnormalAnnotations = new ArrayList<>();
        ConfigManager configManager = ConfigManager.getInstance(this);
        SymbolConfig config = configManager == null ? null : configManager.getSymbolConfig(selectedSymbol);
        List<AbnormalRecord> filteredRecords = filterAbnormalRecordsForSelectedSymbol();
        List<CandleEntry> abnormalBaseCandles = resolveAbnormalBaseCandles();
        List<AbnormalRecord> derivedRecords = HistoricalAbnormalRecordBuilder.buildFromCandles(
                selectedSymbol,
                abnormalBaseCandles,
                config,
                configManager != null && configManager.isUseAndMode()
        );
        List<AbnormalRecord> mergedRecords = HistoricalAbnormalRecordBuilder.merge(filteredRecords, derivedRecords);
        List<AbnormalAnnotationOverlayBuilder.BucketAnnotation> groupedAnnotations =
                AbnormalAnnotationOverlayBuilder.build(mergedRecords, loadedCandles);
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
        binding.klineChartView.setAbnormalAnnotations(abnormalAnnotations);
    }

    // 异常圆点统一以 1 分钟 K 线为底稿重算，再投影到当前周期，避免高周期直接按粗粒度数据漏计次数。
    private List<CandleEntry> resolveAbnormalBaseCandles() {
        if (loadedCandles == null || loadedCandles.isEmpty()) {
            return new ArrayList<>();
        }
        IntervalOption minuteOption = INTERVALS[0];
        if ("1m".equalsIgnoreCase(selectedInterval.key) && !selectedInterval.yearAggregate) {
            return new ArrayList<>(loadedCandles);
        }
        List<CandleEntry> minuteCandles = getCachedOrPersisted(buildCacheKey(selectedSymbol, minuteOption));
        if (minuteCandles == null || minuteCandles.isEmpty()) {
            return new ArrayList<>(loadedCandles);
        }
        long startTime = loadedCandles.get(0).getOpenTime();
        long endTime = loadedCandles.get(loadedCandles.size() - 1).getCloseTime();
        if (endTime <= 0L) {
            endTime = loadedCandles.get(loadedCandles.size() - 1).getOpenTime() + 60_000L;
        }
        List<CandleEntry> filtered = new ArrayList<>();
        for (CandleEntry candle : minuteCandles) {
            if (candle == null) {
                continue;
            }
            long openTime = candle.getOpenTime();
            if (openTime < startTime || openTime > endTime) {
                continue;
            }
            filtered.add(candle);
        }
        return filtered.isEmpty() ? new ArrayList<>(loadedCandles) : filtered;
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

    private void updateAccountAnnotationsOverlay() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        if (!ConfigManager.getInstance(this).isAccountSessionActive()) {
            clearAccountAnnotationsOverlay();
            return;
        }
        AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager == null
                ? null
                : accountStatsPreloadManager.getLatestCache();
        AccountStorageRepository.StoredSnapshot storedSnapshot = accountStorageRepository == null
                ? null
                : accountStorageRepository.loadStoredSnapshot();
        AccountSnapshot snapshot = AccountSnapshotDisplayResolver.resolve(
                cache,
                storedSnapshot,
                System.currentTimeMillis()
        );
        if (snapshot == null) {
            clearAccountAnnotationsOverlay();
            return;
        }

        List<PositionItem> positions = snapshot.getPositions() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getPositions());
        List<PositionItem> pendingOrders = snapshot.getPendingOrders() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getPendingOrders());
        List<TradeRecordItem> trades = snapshot.getTrades() == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshot.getTrades());

        binding.klineChartView.setPositionAnnotations(buildPositionAnnotations(positions, trades));
        binding.klineChartView.setPendingAnnotations(buildPendingAnnotations(pendingOrders, trades));
        binding.klineChartView.setHistoryTradeAnnotations(buildHistoricalTradeAnnotations(trades));
        binding.klineChartView.setAggregateCostAnnotation(buildAggregateCostAnnotation(positions));
        updateChartPositionPanel(positions, pendingOrders);
    }

    // 当没有任何可用账户快照时，统一清空图表上的持仓与挂单标注。
    private void clearAccountAnnotationsOverlay() {
        binding.klineChartView.setPositionAnnotations(new ArrayList<>());
        binding.klineChartView.setPendingAnnotations(new ArrayList<>());
        binding.klineChartView.setHistoryTradeAnnotations(new ArrayList<>());
        binding.klineChartView.setAggregateCostAnnotation(null);
        updateChartPositionPanel(new ArrayList<>(), new ArrayList<>());
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
                    "开仓 " + FormatUtils.formatTime(item.openTimeMs) + " $" + FormatUtils.formatPrice(item.entryPrice),
                    "平仓 " + FormatUtils.formatTime(item.closeTimeMs) + " $" + FormatUtils.formatPrice(item.exitPrice),
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
            String side = normalizeTradeSideLabel(item.getSide());
            String label = side + " " + formatQuantity(Math.abs(item.getQuantity()))
                    + ", " + formatSignedUsd(item.getTotalPnL());
            int color = item.getTotalPnL() >= 0d ? gainColor : lossColor;
            String groupId = buildAnnotationGroupId("position", item, anchorTime, price);
            result.add(new KlineChartView.PriceAnnotation(anchorTime, price, label, color, groupId));
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
            String qtyLabel = lots > 1e-9
                    ? formatQuantity(lots)
                    : (item.getPendingCount() > 0 ? (item.getPendingCount() + "单") : "--");
            String label = "PENDING " + side + " " + qtyLabel
                    + ", @ $" + FormatUtils.formatPrice(price);
            String groupId = buildAnnotationGroupId("pending", item, anchorTime, price);
            result.add(new KlineChartView.PriceAnnotation(anchorTime, price, label, color, groupId));
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
        if (trades != null && !trades.isEmpty()) {
            long byPositionId = findTradeOpenTimeByPositionId(position.getPositionTicket(), position.getCostPrice(), trades);
            if (byPositionId > 0L) {
                return byPositionId;
            }
            long byOrderId = findTradeOpenTimeByOrderId(position.getOrderId(), position.getCostPrice(), trades);
            if (byOrderId > 0L) {
                return byOrderId;
            }
            long byCodeSide = findTradeOpenTimeByCodeAndSide(position.getCode(), position.getProductName(), position.getSide(), position.getCostPrice(), trades);
            if (byCodeSide > 0L) {
                return byCodeSide;
            }
        }
        return resolveLatestCandleOpenTime();
    }

    private long resolvePendingAnchorTime(PositionItem pendingOrder, List<TradeRecordItem> trades) {
        if (trades != null && !trades.isEmpty()) {
            long byOrderId = findTradeOpenTimeByOrderId(pendingOrder.getOrderId(), pendingOrder.getPendingPrice(), trades);
            if (byOrderId > 0L) {
                return byOrderId;
            }
            long byPositionId = findTradeOpenTimeByPositionId(pendingOrder.getPositionTicket(), pendingOrder.getPendingPrice(), trades);
            if (byPositionId > 0L) {
                return byPositionId;
            }
            long byCodeSide = findTradeOpenTimeByCodeAndSide(
                    pendingOrder.getCode(),
                    pendingOrder.getProductName(),
                    pendingOrder.getSide(),
                    pendingOrder.getPendingPrice(),
                    trades);
            if (byCodeSide > 0L) {
                return byCodeSide;
            }
        }
        return resolveLatestCandleOpenTime();
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

    private long findTradeOpenTimeByCodeAndSide(String code,
                                                String productName,
                                                String side,
                                                double targetPrice,
                                                List<TradeRecordItem> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0L;
        }
        String normalizedSide = normalizeTradeSideLabel(side);
        double bestScore = Double.MAX_VALUE;
        long bestTime = 0L;
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            if (!matchesSelectedSymbol(trade.getCode(), trade.getProductName())) {
                continue;
            }
            if (!normalizedSide.equals(normalizeTradeSideLabel(trade.getSide()))) {
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
        if (trade.getOpenTime() > 0L) {
            return trade.getOpenTime();
        }
        if (trade.getTimestamp() > 0L) {
            return trade.getTimestamp();
        }
        if (trade.getCloseTime() > 0L) {
            return trade.getCloseTime();
        }
        return 0L;
    }

    private double resolveTradeOpenPrice(TradeRecordItem trade) {
        if (trade == null) {
            return 0d;
        }
        if (trade.getOpenPrice() > 0d) {
            return trade.getOpenPrice();
        }
        return trade.getPrice();
    }

    private double priceDistance(double left, double right) {
        if (left <= 0d || right <= 0d) {
            return Double.MAX_VALUE / 4d;
        }
        return Math.abs(left - right) / Math.max(1d, Math.abs(right));
    }

    private long resolveLatestCandleOpenTime() {
        if (loadedCandles == null || loadedCandles.isEmpty()) {
            return System.currentTimeMillis();
        }
        return loadedCandles.get(loadedCandles.size() - 1).getOpenTime();
    }

    private boolean matchesSelectedSymbol(String code, String productName) {
        String symbol = selectedSymbol == null ? "" : selectedSymbol.trim().toUpperCase(Locale.ROOT);
        String asset = symbol.endsWith("USDT") && symbol.length() > 4
                ? symbol.substring(0, symbol.length() - 4)
                : symbol;
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        String normalizedName = productName == null ? "" : productName.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.isEmpty() && normalizedName.isEmpty()) {
            return true;
        }
        List<String> candidates = new ArrayList<>();
        if (!symbol.isEmpty()) {
            candidates.add(symbol);
        }
        if (!asset.isEmpty()) {
            candidates.add(asset);
            candidates.add(asset + "USD");
            candidates.add(asset + "USDT");
            if ("XAU".equals(asset)) {
                candidates.add("XAUUSD");
                candidates.add("GOLD");
            } else if ("BTC".equals(asset)) {
                candidates.add("BTCUSD");
                candidates.add("XBT");
            }
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            String value = candidate.trim().toUpperCase(Locale.ROOT);
            if (normalizedCode.contains(value) || normalizedName.contains(value)) {
                return true;
            }
        }
        return false;
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

    private String buildAnnotationGroupId(String type, PositionItem item, long anchorTime, double price) {
        String safeType = type == null ? "order" : type;
        if (item == null) {
            return safeType + "|na|" + anchorTime;
        }
        if (item.getPositionTicket() > 0L) {
            return safeType + "|position|" + item.getPositionTicket();
        }
        if (item.getOrderId() > 0L) {
            return safeType + "|order|" + item.getOrderId();
        }
        String code = item.getCode() == null ? "" : item.getCode().trim().toUpperCase(Locale.ROOT);
        String side = normalizeTradeSideLabel(item.getSide());
        String priceToken = String.format(Locale.US, "%.4f", Math.max(0d, price));
        return safeType + "|fallback|" + code + "|" + side + "|" + anchorTime + "|" + priceToken;
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

    // 持久化当前选中的K线周期，供下次进入时恢复。
    private void persistSelectedInterval() {
        getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_SELECTED_INTERVAL, selectedInterval.key)
                .apply();
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
                binding.btnIndicatorAvl, binding.btnIndicatorRsi, binding.btnIndicatorKdj
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
        applyStripBackground(binding.btnInterval1m, palette);
        applyStripBackground(binding.btnIndicatorVolume, palette);
        binding.tvChartSymbolPickerLabel.setTextColor(palette.textPrimary);
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
        updateBottomTabs(false, true, false, false);
        syncSymbolSelector();
        updateIntervalButtons();
        updateIndicatorButtons();
        applyPrivacyMaskState();
    }

    private void openMarketMonitor() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openAccountStats() {
        Intent intent = new Intent(this, AccountStatsBridgeActivity.class);
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
}

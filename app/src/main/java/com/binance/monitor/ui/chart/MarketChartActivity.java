package com.binance.monitor.ui.chart;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.KlineCacheStore;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.data.remote.BinanceApiClient;
import com.binance.monitor.databinding.ActivityMarketChartBinding;
import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.ui.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

    private static final long AUTO_REFRESH_INTERVAL_MS = 10_000L;
    private static final long XAU_FULL_HISTORY_CHECK_INTERVAL_MS = 60L * 60L * 1000L;
    private static final String KEY_XAU_FULL_CHECK_PREFIX = "xau_full_check_";
    private static final int HISTORY_PERSIST_LIMIT = 5_000;

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

    private static final IntervalOption[] INTERVALS = new IntervalOption[]{
            new IntervalOption("1m", "1分", "1m", 500, false),
            new IntervalOption("5m", "5分", "5m", 500, false),
            new IntervalOption("15m", "15分", "15m", 500, false),
            new IntervalOption("30m", "30分", "30m", 500, false),
            new IntervalOption("1h", "1小时", "1h", 500, false),
            new IntervalOption("4h", "4小时", "4h", 500, false),
            new IntervalOption("1d", "日线", "1d", 500, false),
            new IntervalOption("1w", "周线", "1w", 500, false),
            new IntervalOption("1M", "月线", "1M", 500, false),
            new IntervalOption("1y", "年线", "1M", 500, true)
    };

    private ActivityMarketChartBinding binding;
    private final BinanceApiClient apiClient = new BinanceApiClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService ioExecutor;
    private Future<?> runningTask;
    private Future<?> loadMoreTask;
    private long runningTaskStartMs;
    private int requestVersion = 0;
    private final Map<String, List<CandleEntry>> klineCache = new ConcurrentHashMap<>();
    private final Set<String> prefetchInFlight = ConcurrentHashMap.newKeySet();
    private KlineCacheStore klineCacheStore;
    private AccountStatsPreloadManager accountStatsPreloadManager;
    private AbnormalRecordManager abnormalRecordManager;
    private SharedPreferences runtimePreferences;

    private String selectedSymbol = AppConstants.SYMBOL_BTC;
    private IntervalOption selectedInterval = INTERVALS[2];
    private String activeDataKey = "";
    private boolean showVolume = true;
    private boolean showMacd = true;
    private boolean showStochRsi = true;
    private boolean showBoll = true;
    private final List<CandleEntry> loadedCandles = new ArrayList<>();
    private final SimpleDateFormat infoTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final SimpleDateFormat stateTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final DecimalFormat qtyFormat = new DecimalFormat("0.####");
    private final DecimalFormat pnlFormat = new DecimalFormat("0.##");
    private int pricePaneRightPx;
    private int pricePaneBottomPx;
    private volatile boolean loadingMore;
    private long lastSuccessUpdateMs;
    private List<AbnormalRecord> abnormalRecords = new ArrayList<>();
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            requestKlines(false, true);
            scheduleNextAutoRefresh();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMarketChartBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ioExecutor = Executors.newFixedThreadPool(2);
        klineCacheStore = new KlineCacheStore(this);
        accountStatsPreloadManager = AccountStatsPreloadManager.getInstance(getApplicationContext());
        abnormalRecordManager = AbnormalRecordManager.getInstance(getApplicationContext());
        runtimePreferences = getSharedPreferences(PREF_RUNTIME_NAME, MODE_PRIVATE);
        applyIntentSymbol(getIntent(), false);
        if (abnormalRecordManager != null) {
            abnormalRecordManager.getRecordsLiveData().observe(this, records -> {
                abnormalRecords = records == null ? new ArrayList<>() : new ArrayList<>(records);
                updateAbnormalAnnotationsOverlay();
            });
        }

        setupChart();
        setupSymbolSelector();
        setupIntervalButtons();
        setupIndicatorButtons();
        setupBottomNav();
        normalizeOptionButtons();
        binding.btnRetryLoad.setOnClickListener(v -> requestKlines());
        applyPaletteStyles();
        restorePersistedCache(buildCacheKey(selectedSymbol, selectedInterval));
        updateStateCount();
        requestKlines();
        prefetch(selectedSymbol, INTERVALS[0]);
        prefetch(selectedSymbol, INTERVALS[1]);
        prefetchOtherSymbols(INTERVALS[0]);
        prefetchOtherSymbols(INTERVALS[1]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
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
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopAutoRefresh();
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
        binding.klineChartView.setOnCrosshairListener(value -> {
            if (value == null) {
                renderInfoWithLatest();
            } else {
                renderInfo(value.candle, value.macdDif, value.macdDea, value.macdHist, value.stochK, value.stochD);
            }
        });
        binding.klineChartView.setOnRequestMoreListener(this::requestMoreHistory);
        binding.klineChartView.setOnPricePaneLayoutListener((left, top, right, bottom) -> {
            pricePaneRightPx = right;
            pricePaneBottomPx = bottom;
            updateScrollToLatestButtonPosition();
        });
        binding.klineChartView.setOnViewportStateListener(outOfBounds ->
        {
            binding.btnScrollToLatest.setVisibility(outOfBounds ? android.view.View.VISIBLE : android.view.View.GONE);
            if (outOfBounds) {
                binding.btnScrollToLatest.post(this::updateScrollToLatestButtonPosition);
            }
        });
        binding.btnScrollToLatest.setOnClickListener(v -> {
            binding.klineChartView.scrollToLatest();
            updateScrollToLatestButtonPosition();
        });
        binding.btnScrollToLatest.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateScrollToLatestButtonPosition());
        binding.klineChartView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateScrollToLatestButtonPosition());
        binding.btnScrollToLatest.setVisibility(android.view.View.GONE);
        refreshChartOverlays();
    }

    private void setupSymbolSelector() {
        List<String> symbols = getSupportedSymbols();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.item_spinner_filter,
                android.R.id.text1,
                symbols
        );
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        binding.spinnerSymbolPicker.setAdapter(adapter);
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
                switchSymbol(symbol);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        syncSymbolSelector();
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
            prefetch(selectedSymbol, INTERVALS[0]);
            prefetch(selectedSymbol, INTERVALS[1]);
            prefetchOtherSymbols(INTERVALS[0]);
            prefetchOtherSymbols(INTERVALS[1]);
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
        binding.btnIndicatorVolume.setOnClickListener(v -> {
            showVolume = !showVolume;
            onIndicatorChanged();
        });
        binding.btnIndicatorMacd.setOnClickListener(v -> {
            showMacd = !showMacd;
            onIndicatorChanged();
        });
        binding.btnIndicatorStochRsi.setOnClickListener(v -> {
            showStochRsi = !showStochRsi;
            onIndicatorChanged();
        });
        binding.btnIndicatorBoll.setOnClickListener(v -> {
            showBoll = !showBoll;
            onIndicatorChanged();
        });
        updateIndicatorButtons();
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
        styleNavTab(binding.tabMarketMonitor, market, palette);
        styleNavTab(binding.tabMarketChart, chart, palette);
        styleNavTab(binding.tabAccountStats, account, palette);
        styleNavTab(binding.tabSettings, settings, palette);
    }

    private void styleNavTab(TextView button, boolean selected, UiPaletteManager.Palette palette) {
        button.setBackground(selected
                ? UiPaletteManager.createFilledDrawable(this, palette.primary)
                : UiPaletteManager.createOutlinedDrawable(this,
                UiPaletteManager.neutralFill(this),
                UiPaletteManager.neutralStroke(this)));
        button.setTextColor(ContextCompat.getColor(this, selected ? R.color.white : R.color.text_secondary));
    }

    private void switchSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty() || symbol.equalsIgnoreCase(selectedSymbol)) {
            return;
        }
        selectedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        syncSymbolSelector();
        requestKlines();
        prefetch(selectedSymbol, INTERVALS[0]);
        prefetch(selectedSymbol, INTERVALS[1]);
        prefetchOtherSymbols(INTERVALS[0]);
        prefetchOtherSymbols(INTERVALS[1]);
        scheduleNextAutoRefresh();
    }

    private void switchInterval(IntervalOption option) {
        if (option == null || option.key.equals(selectedInterval.key)) {
            return;
        }
        selectedInterval = option;
        updateIntervalButtons();
        requestKlines();
        prefetchOtherSymbols(option);
        scheduleNextAutoRefresh();
    }

    private void onIndicatorChanged() {
        binding.klineChartView.setIndicatorsVisible(showVolume, showMacd, showStochRsi, showBoll);
        updateIndicatorButtons();
        refreshChartOverlays();
        binding.klineChartView.post(this::updateScrollToLatestButtonPosition);
    }

    private void requestKlines() {
        requestKlines(true, false);
    }

    private void requestKlines(boolean allowCancelRunning, boolean autoRefresh) {
        final String key = buildCacheKey(selectedSymbol, selectedInterval);
        if (autoRefresh && binding.klineChartView.isUserInteracting()) {
            return;
        }

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
            if (cached != null && !cached.isEmpty()) {
                loadedCandles.clear();
                loadedCandles.addAll(cached);
                activeDataKey = key;
                binding.klineChartView.setCandles(loadedCandles);
                renderInfoWithLatest();
                updateStateCount();
                refreshChartOverlays();
            } else {
                List<CandleEntry> quick = buildQuickSwitchData(selectedSymbol, selectedInterval);
                if (!quick.isEmpty()) {
                    loadedCandles.clear();
                    loadedCandles.addAll(quick);
                    activeDataKey = key;
                    binding.klineChartView.setCandles(loadedCandles);
                    renderInfoWithLatest();
                    updateStateCount();
                    refreshChartOverlays();
                } else if (!key.equals(activeDataKey)) {
                    loadedCandles.clear();
                    activeDataKey = key;
                    binding.klineChartView.setCandles(new ArrayList<>());
                    updateStateCount();
                    refreshChartOverlays();
                }
            }
        }

        final int current = ++requestVersion;
        showLoading(true);
        binding.tvError.setVisibility(android.view.View.GONE);
        binding.btnRetryLoad.setVisibility(android.view.View.GONE);
        runningTaskStartMs = System.currentTimeMillis();
        runningTask = ioExecutor.submit(() -> {
            try {
                List<CandleEntry> source = loadCandlesForRequest(autoRefresh);
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
                    List<CandleEntry> mergeBase = key.equals(activeDataKey)
                            ? new ArrayList<>(loadedCandles)
                            : new ArrayList<>();
                    List<CandleEntry> toDisplay = mergeLatestData(mergeBase, processed);
                    loadedCandles.clear();
                    loadedCandles.addAll(toDisplay);
                    activeDataKey = key;
                    klineCache.put(key, new ArrayList<>(toDisplay));
                    if (autoRefresh) {
                        binding.klineChartView.setCandlesKeepingViewport(loadedCandles);
                    } else {
                        binding.klineChartView.setCandles(loadedCandles);
                    }
                    refreshChartOverlays();
                    if (!(autoRefresh && binding.klineChartView.hasActiveCrosshair())) {
                        renderInfoWithLatest();
                    }
                    lastSuccessUpdateMs = System.currentTimeMillis();
                    persistCurrentCandles(key);
                    updateStateCount();
                    binding.tvError.setVisibility(android.view.View.GONE);
                    binding.btnRetryLoad.setVisibility(android.view.View.GONE);
                    showLoading(false);
                    prefetchOtherSymbols(selectedInterval);
                    prefetch(selectedSymbol, INTERVALS[0]);
                    prefetch(selectedSymbol, INTERVALS[1]);
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
                List<CandleEntry> fetched = apiClient.fetchKlineHistoryBefore(
                        reqSymbol,
                        reqInterval.apiInterval,
                        reqInterval.limit,
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

    private List<CandleEntry> buildQuickSwitchData(String symbol, IntervalOption target) {
        if (symbol == null || target == null) {
            return new ArrayList<>();
        }
        String prefix = symbol + "|";
        long targetMs = intervalToMs(target.key);
        if (targetMs <= 0L) {
            return new ArrayList<>();
        }
        List<CandleEntry> best = null;
        long bestSourceMs = Long.MAX_VALUE;
        for (Map.Entry<String, List<CandleEntry>> entry : klineCache.entrySet()) {
            String key = entry.getKey();
            if (key == null || !key.startsWith(prefix)) {
                continue;
            }
            String[] parts = key.split("\\|");
            if (parts.length < 2) {
                continue;
            }
            long srcMs = intervalToMs(parts[1]);
            if (srcMs <= 0L || srcMs > targetMs) {
                continue;
            }
            if (targetMs % srcMs != 0L) {
                continue;
            }
            List<CandleEntry> source = entry.getValue();
            if (source == null || source.isEmpty()) {
                continue;
            }
            if (best == null || srcMs < bestSourceMs) {
                best = source;
                bestSourceMs = srcMs;
            }
        }
        if (best == null) {
            return new ArrayList<>();
        }
        if (bestSourceMs == targetMs && !target.yearAggregate) {
            return new ArrayList<>(best);
        }
        if (target.yearAggregate) {
            return aggregateToYear(best, symbol);
        }
        return aggregateByInterval(best, symbol, targetMs);
    }

    private List<CandleEntry> aggregateByInterval(List<CandleEntry> source, String symbol, long intervalMs) {
        if (source == null || source.isEmpty() || intervalMs <= 0L) {
            return new ArrayList<>();
        }
        Map<Long, CandleEntry> out = new LinkedHashMap<>();
        for (CandleEntry item : source) {
            long bucketStart = (item.getOpenTime() / intervalMs) * intervalMs;
            CandleEntry existing = out.get(bucketStart);
            if (existing == null) {
                out.put(bucketStart, new CandleEntry(
                        symbol,
                        bucketStart,
                        bucketStart + intervalMs - 1L,
                        item.getOpen(),
                        item.getHigh(),
                        item.getLow(),
                        item.getClose(),
                        item.getVolume(),
                        item.getQuoteVolume()
                ));
            } else {
                out.put(bucketStart, new CandleEntry(
                        symbol,
                        existing.getOpenTime(),
                        Math.max(existing.getCloseTime(), item.getCloseTime()),
                        existing.getOpen(),
                        Math.max(existing.getHigh(), item.getHigh()),
                        Math.min(existing.getLow(), item.getLow()),
                        item.getClose(),
                        existing.getVolume() + item.getVolume(),
                        existing.getQuoteVolume() + item.getQuoteVolume()
                ));
            }
        }
        List<CandleEntry> merged = new ArrayList<>(out.values());
        Collections.sort(merged, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));
        return merged;
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

    private void startAutoRefresh() {
        stopAutoRefresh();
        scheduleNextAutoRefresh();
    }

    private void stopAutoRefresh() {
        mainHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void scheduleNextAutoRefresh() {
        mainHandler.removeCallbacks(autoRefreshRunnable);
        mainHandler.postDelayed(autoRefreshRunnable, resolveAutoRefreshDelayMs());
    }

    private long resolveAutoRefreshDelayMs() {
        return AUTO_REFRESH_INTERVAL_MS;
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

    private void prefetchOtherSymbols(IntervalOption interval) {
        if (interval == null) {
            return;
        }
        String current = selectedSymbol == null ? "" : selectedSymbol.trim().toUpperCase(Locale.ROOT);
        for (String symbol : getSupportedSymbols()) {
            if (symbol.equalsIgnoreCase(current)) {
                continue;
            }
            prefetch(symbol, interval);
        }
    }

    private void prefetch(String symbol, IntervalOption interval) {
        if (symbol == null || interval == null) {
            return;
        }
        String key = buildCacheKey(symbol, interval);
        if (klineCache.containsKey(key) || !prefetchInFlight.add(key)) {
            return;
        }
        ioExecutor.submit(() -> {
            try {
                List<CandleEntry> source = apiClient.fetchKlineHistory(symbol, interval.apiInterval, interval.limit);
                List<CandleEntry> processed = interval.yearAggregate ? aggregateToYear(source, symbol) : source;
                if (!processed.isEmpty()) {
                    List<CandleEntry> merged = mergeLatestData(getCachedOrPersisted(key), processed);
                    klineCache.put(key, new ArrayList<>(merged));
                    persistCurrentCandles(key);
                }
            } catch (Exception ignored) {
            } finally {
                prefetchInFlight.remove(key);
            }
        });
    }

    private void restorePersistedCache(String key) {
        if (key == null || key.trim().isEmpty() || klineCacheStore == null) {
            return;
        }
        List<CandleEntry> persisted = klineCacheStore.read(key, HISTORY_PERSIST_LIMIT);
        if (persisted == null || persisted.isEmpty()) {
            return;
        }
        klineCache.put(key, new ArrayList<>(persisted));
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
        if (klineCacheStore == null || key == null || key.trim().isEmpty()) {
            return cached;
        }
        List<CandleEntry> persisted = klineCacheStore.read(key, HISTORY_PERSIST_LIMIT);
        if (persisted != null && !persisted.isEmpty()) {
            klineCache.put(key, new ArrayList<>(persisted));
            return persisted;
        }
        return cached;
    }

    private void persistCurrentCandles(String key) {
        if (klineCacheStore == null || key == null || key.trim().isEmpty() || loadedCandles.isEmpty()) {
            return;
        }
        klineCacheStore.write(key, loadedCandles, HISTORY_PERSIST_LIMIT);
    }

    private List<CandleEntry> loadCandlesForRequest(boolean autoRefresh) throws Exception {
        boolean requireFullHistory = !autoRefresh
                || loadedCandles.isEmpty()
                || !buildCacheKey(selectedSymbol, selectedInterval).equals(activeDataKey)
                || shouldForceXauFullHistoryCheck(autoRefresh);
        if (requireFullHistory) {
            return fetchFullHistoryAndMark(selectedSymbol, selectedInterval.limit);
        }
        long intervalMs = intervalToMs(selectedInterval.apiInterval);
        int incrementalLimit = resolveAutoRefreshFetchLimit(intervalMs);
        if (intervalMs <= 0L) {
            return fetchFullHistoryAndMark(selectedSymbol, incrementalLimit);
        }
        long latestOpenTime = loadedCandles.get(loadedCandles.size() - 1).getOpenTime();
        long startTime = Math.max(0L, latestOpenTime - intervalMs);
        List<CandleEntry> incremental = apiClient.fetchKlineHistoryAfter(
                selectedSymbol,
                selectedInterval.apiInterval,
                incrementalLimit,
                startTime);
        if (!incremental.isEmpty()) {
            return incremental;
        }
        return fetchFullHistoryAndMark(selectedSymbol, Math.min(120, incrementalLimit));
    }

    private List<CandleEntry> fetchFullHistoryAndMark(String symbol, int limit) throws Exception {
        List<CandleEntry> full = apiClient.fetchKlineHistory(symbol, selectedInterval.apiInterval, limit);
        if (AppConstants.SYMBOL_XAU.equalsIgnoreCase(symbol) && full != null && !full.isEmpty()) {
            markXauFullHistoryChecked();
        }
        return full;
    }

    private boolean shouldForceXauFullHistoryCheck(boolean autoRefresh) {
        if (!autoRefresh || runtimePreferences == null) {
            return false;
        }
        if (!AppConstants.SYMBOL_XAU.equalsIgnoreCase(selectedSymbol)) {
            return false;
        }
        long now = System.currentTimeMillis();
        long lastChecked = runtimePreferences.getLong(buildXauFullHistoryCheckKey(), 0L);
        return now - lastChecked >= XAU_FULL_HISTORY_CHECK_INTERVAL_MS;
    }

    private void markXauFullHistoryChecked() {
        if (runtimePreferences == null || !AppConstants.SYMBOL_XAU.equalsIgnoreCase(selectedSymbol)) {
            return;
        }
        runtimePreferences.edit()
                .putLong(buildXauFullHistoryCheckKey(), System.currentTimeMillis())
                .apply();
    }

    private String buildXauFullHistoryCheckKey() {
        String intervalKey = selectedInterval == null ? "default" : selectedInterval.key;
        return KEY_XAU_FULL_CHECK_PREFIX + intervalKey;
    }

    private int resolveAutoRefreshFetchLimit(long intervalMs) {
        if (loadedCandles.isEmpty()) {
            return selectedInterval.limit;
        }
        if (intervalMs <= 0L) {
            return 80;
        }
        long latestOpenTime = loadedCandles.get(loadedCandles.size() - 1).getOpenTime();
        long gapMs = Math.max(0L, System.currentTimeMillis() - latestOpenTime);
        long estimatedMissing = (gapMs / intervalMs) + 5L;
        long bounded = Math.max(8L, Math.min(1500L, estimatedMissing));
        return (int) bounded;
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
        if (abnormalRecords != null) {
            int color = Color.parseColor("#F59E0B");
            int overlayCount = 0;
            for (AbnormalRecord record : abnormalRecords) {
                if (record == null) {
                    continue;
                }
                if (!matchesSelectedSymbol(record.getSymbol(), record.getSymbol())) {
                    continue;
                }
                double price = record.getClosePrice() > 0d ? record.getClosePrice() : record.getOpenPrice();
                if (price <= 0d) {
                    continue;
                }
                long anchorTime = record.getCloseTime() > 0L ? record.getCloseTime() : record.getTimestamp();
                if (anchorTime <= 0L) {
                    continue;
                }
                String label = "ABN $" + FormatUtils.formatPrice(price);
                String summary = record.getTriggerSummary() == null ? "" : record.getTriggerSummary().trim();
                if (!summary.isEmpty()) {
                    String shortSummary = summary.length() > 12 ? summary.substring(0, 12) + "…" : summary;
                    label = label + " " + shortSummary;
                }
                abnormalAnnotations.add(new KlineChartView.PriceAnnotation(
                        anchorTime,
                        price,
                        label,
                        color,
                        "abn|" + record.getId()));
                overlayCount++;
                if (overlayCount >= 180) {
                    break;
                }
            }
        }
        abnormalAnnotations.sort((a, b) -> Long.compare(a.anchorTimeMs, b.anchorTimeMs));
        binding.klineChartView.setAbnormalAnnotations(abnormalAnnotations);
    }

    private void updateAccountAnnotationsOverlay() {
        if (binding == null || binding.klineChartView == null) {
            return;
        }
        AccountSnapshot snapshot = null;
        if (accountStatsPreloadManager != null) {
            AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager.getLatestCache();
            if (cache != null) {
                snapshot = cache.getSnapshot();
            }
        }
        if (snapshot == null) {
            binding.klineChartView.setPositionAnnotations(new ArrayList<>());
            binding.klineChartView.setPendingAnnotations(new ArrayList<>());
            binding.klineChartView.setAggregateCostAnnotation(null);
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
        binding.klineChartView.setAggregateCostAnnotation(buildAggregateCostAnnotation(positions));
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
            double lots = Math.abs(item.getPendingLots()) > 1e-9 ? Math.abs(item.getPendingLots()) : Math.abs(item.getQuantity());
            if (lots <= 1e-9) {
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
            String label = "PENDING " + side + " " + formatQuantity(lots)
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
        if (!symbol.isEmpty() && (normalizedCode.contains(symbol) || normalizedName.contains(symbol))) {
            return true;
        }
        return !asset.isEmpty() && (normalizedCode.contains(asset) || normalizedName.contains(asset));
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

    private void updateScrollToLatestButtonPosition() {
        if (binding == null || binding.btnScrollToLatest == null || binding.klineChartView == null) {
            return;
        }
        if (pricePaneRightPx <= 0 || pricePaneBottomPx <= 0) {
            return;
        }
        int buttonWidth = binding.btnScrollToLatest.getWidth();
        int buttonHeight = binding.btnScrollToLatest.getHeight();
        if (buttonWidth <= 0 || buttonHeight <= 0) {
            binding.btnScrollToLatest.post(this::updateScrollToLatestButtonPosition);
            return;
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
            return;
        }
        params.gravity = targetGravity;
        params.leftMargin = targetLeft;
        params.topMargin = targetTop;
        params.rightMargin = 0;
        params.bottomMargin = 0;
        binding.btnScrollToLatest.setLayoutParams(params);
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
            return;
        }
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

    private void updateIndicatorButtons() {
        styleTabButton(binding.btnIndicatorVolume, showVolume);
        styleTabButton(binding.btnIndicatorMacd, showMacd);
        styleTabButton(binding.btnIndicatorStochRsi, showStochRsi);
        styleTabButton(binding.btnIndicatorBoll, showBoll);
    }

    private void styleTabButton(Button button, boolean selected) {
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setTextColor(selected ? Color.parseColor("#E6EDF9") : Color.parseColor("#7F8EA9"));
        button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void normalizeOptionButtons() {
        Button[] buttons = new Button[]{
                binding.btnInterval1m, binding.btnInterval5m, binding.btnInterval15m, binding.btnInterval30m,
                binding.btnInterval1h, binding.btnInterval4h, binding.btnInterval1d, binding.btnInterval1w,
                binding.btnInterval1mo, binding.btnInterval1y,
                binding.btnIndicatorVolume, binding.btnIndicatorMacd, binding.btnIndicatorStochRsi, binding.btnIndicatorBoll
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
        binding.getRoot().setBackgroundColor(Color.parseColor("#111B2E"));
        updateBottomTabs(false, true, false, false);
        syncSymbolSelector();
        updateIntervalButtons();
        updateIndicatorButtons();
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

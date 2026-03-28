package com.binance.monitor.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.data.remote.BinanceApiClient;
import com.binance.monitor.data.remote.WebSocketManager;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.ui.floating.FloatingWindowManager;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.NotificationHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MonitorService extends Service {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> socketStates = new HashMap<>();
    private final Map<String, Integer> reconnectCounts = new HashMap<>();
    private final Map<String, Long> lastNotifyAt = new HashMap<>();
    private final Map<String, Long> lastKlineTickAt = new HashMap<>();
    private final Map<String, Long> lastPricePublishAt = new HashMap<>();
    private final Map<String, Double> lastPublishedPrice = new HashMap<>();
    private final Map<Long, PendingRound> pendingRounds = new HashMap<>();
    private final Runnable connectionWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            checkStreamFreshness();
            mainHandler.postDelayed(this, AppConstants.CONNECTION_HEARTBEAT_INTERVAL_MS);
        }
    };
    private final Runnable floatingRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            floatingRefreshScheduled = false;
            lastFloatingRefreshAt = System.currentTimeMillis();
            refreshFloatingWindow();
        }
    };

    private MonitorRepository repository;
    private LogManager logManager;
    private ConfigManager configManager;
    private AbnormalRecordManager recordManager;
    private NotificationHelper notificationHelper;
    private FloatingWindowManager floatingWindowManager;
    private BinanceApiClient apiClient;
    private WebSocketManager webSocketManager;
    private ExecutorService executorService;
    private boolean pipelineStarted;
    private boolean foregroundStarted;
    private long lastFloatingRefreshAt;
    private boolean floatingRefreshScheduled;
    private long lastForcedReconnectAt;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = MonitorRepository.getInstance(this);
        logManager = repository.getLogManager();
        configManager = repository.getConfigManager();
        recordManager = repository.getRecordManager();
        notificationHelper = new NotificationHelper(this);
        floatingWindowManager = new FloatingWindowManager(this);
        apiClient = new BinanceApiClient();
        webSocketManager = new WebSocketManager();
        executorService = Executors.newSingleThreadExecutor();
        repository.setMonitoringEnabled(false);
        logManager.info("服务初始化完成");
        applyFloatingPreferences();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : AppConstants.ACTION_BOOTSTRAP;
        if (action == null) {
            action = AppConstants.ACTION_BOOTSTRAP;
        }
        ensureForeground();
        startPipelineIfNeeded();
        switch (action) {
            case AppConstants.ACTION_START_MONITORING:
                repository.setMonitoringEnabled(true);
                logManager.info("异常监控已开启");
                break;
            case AppConstants.ACTION_STOP_MONITORING:
                repository.setMonitoringEnabled(false);
                logManager.info("异常监控已停止，行情继续更新");
                break;
            case AppConstants.ACTION_REFRESH_CONFIG:
                applyFloatingPreferences();
                break;
            case AppConstants.ACTION_BOOTSTRAP:
            default:
                break;
        }
        refreshForegroundState();
        requestFloatingWindowRefresh(true);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webSocketManager != null) {
            webSocketManager.disconnectAll();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        mainHandler.removeCallbacksAndMessages(null);
        if (floatingWindowManager != null) {
            floatingWindowManager.hide();
        }
        logManager.info("服务已销毁");
    }

    private void ensureForeground() {
        if (foregroundStarted) {
            return;
        }
        startForeground(AppConstants.SERVICE_NOTIFICATION_ID,
                notificationHelper.buildServiceNotification(
                        repository.getConnectionStatus().getValue(),
                        Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue())));
        foregroundStarted = true;
    }

    private void refreshForegroundState() {
        if (!foregroundStarted) {
            return;
        }
        startForeground(AppConstants.SERVICE_NOTIFICATION_ID,
                notificationHelper.buildServiceNotification(
                        repository.getConnectionStatus().getValue(),
                        Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue())));
    }

    private synchronized void startPipelineIfNeeded() {
        if (pipelineStarted) {
            return;
        }
        pipelineStarted = true;
        repository.setConnectionStatus(getString(R.string.connection_connecting));
        fetchBootstrapData();
        mainHandler.removeCallbacks(connectionWatchdogRunnable);
        mainHandler.postDelayed(connectionWatchdogRunnable, AppConstants.CONNECTION_HEARTBEAT_INTERVAL_MS);
        webSocketManager.connect(AppConstants.MONITOR_SYMBOLS, new WebSocketManager.Listener() {
            @Override
            public void onSocketStateChanged(String symbol, boolean connected, int reconnectAttempt, String message) {
                mainHandler.post(() -> {
                    socketStates.put(symbol, connected);
                    reconnectCounts.put(symbol, reconnectAttempt);
                    updateConnectionStatus();
                });
            }

            @Override
            public void onKlineUpdate(String symbol, KlineData data) {
                mainHandler.post(() -> {
                    long now = System.currentTimeMillis();
                    lastKlineTickAt.put(symbol, now);
                    if (data.isClosed()) {
                        maybePublishPrice(symbol, data, now, true);
                        repository.updateClosedKline(data);
                        if (Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue())) {
                            handleClosedKline(data);
                        }
                    } else {
                        maybePublishPrice(symbol, data, now, false);
                    }
                    requestFloatingWindowRefresh(data.isClosed());
                });
            }

            @Override
            public void onSocketError(String symbol, String message) {
                mainHandler.post(() -> {
                    logManager.warn(symbol + " WebSocket: " + message);
                    updateConnectionStatus();
                });
            }
        });
        logManager.info("行情流已启动");
    }

    private void fetchBootstrapData() {
        executorService.execute(() -> {
            for (String symbol : AppConstants.MONITOR_SYMBOLS) {
                try {
                    KlineData data = apiClient.fetchLatestClosedKline(symbol);
                    if (data != null) {
                        long now = System.currentTimeMillis();
                        lastKlineTickAt.put(symbol, now);
                        repository.updateClosedKline(data);
                        maybePublishPrice(symbol, data, now, true);
                        logManager.info(symbol + " 初始化成功，最近收盘时间 " + FormatUtils.formatDateTime(data.getCloseTime()));
                    }
                } catch (Exception exception) {
                    logManager.error(symbol + " 初始化失败: " + exception.getMessage());
                }
            }
            mainHandler.post(() -> requestFloatingWindowRefresh(true));
        });
    }

    private void handleClosedKline(KlineData data) {
        EvaluationResult evaluation = evaluate(data, configManager.getSymbolConfig(data.getSymbol()), configManager.isUseAndMode());
        if (!evaluation.participating || !evaluation.abnormal) {
            return;
        }
        AbnormalRecord record = recordManager.createRecord(
                data.getSymbol(),
                data.getCloseTime(),
                data.getOpenPrice(),
                data.getClosePrice(),
                data.getVolume(),
                data.getQuoteAssetVolume(),
                data.getPriceChange(),
                data.getPercentChange(),
                evaluation.summary
        );
        repository.addRecord(record);
        floatingWindowManager.notifyAbnormalEvent(data.getSymbol());
        logManager.warn(data.getSymbol()
                + " 异常触发: "
                + evaluation.summary
                + " | close="
                + FormatUtils.formatPriceWithUnit(data.getClosePrice())
                + " | volume="
                + FormatUtils.formatVolume(data.getVolume())
                + " | amount="
                + FormatUtils.formatAmount(data.getQuoteAssetVolume()));
        queueNotification(record);
    }

    private EvaluationResult evaluate(KlineData data, SymbolConfig config, boolean useAndMode) {
        int enabledCount = 0;
        List<String> triggered = new ArrayList<>();
        if (config.isVolumeEnabled()) {
            enabledCount++;
            if (data.getVolume() >= config.getVolumeThreshold()) {
                triggered.add("成交量");
            }
        }
        if (config.isAmountEnabled()) {
            enabledCount++;
            if (data.getQuoteAssetVolume() >= config.getAmountThreshold()) {
                triggered.add("成交额");
            }
        }
        if (config.isPriceChangeEnabled()) {
            enabledCount++;
            if (data.getAbsolutePriceChange() >= config.getPriceChangeThreshold()) {
                triggered.add("价格变化");
            }
        }
        if (enabledCount == 0) {
            return new EvaluationResult(false, false, "");
        }
        boolean abnormal = useAndMode
                ? triggered.size() == enabledCount
                : !triggered.isEmpty();
        return new EvaluationResult(true, abnormal, String.join(" / ", triggered));
    }

    private void queueNotification(AbnormalRecord record) {
        synchronized (pendingRounds) {
            PendingRound round = pendingRounds.get(record.getCloseTime());
            if (round == null) {
                round = new PendingRound();
                pendingRounds.put(record.getCloseTime(), round);
                long roundKey = record.getCloseTime();
                mainHandler.postDelayed(() -> dispatchRound(roundKey), AppConstants.MERGE_WINDOW_MS);
            }
            round.records.put(record.getSymbol(), record);
        }
    }

    private void dispatchRound(long roundKey) {
        PendingRound round;
        synchronized (pendingRounds) {
            round = pendingRounds.remove(roundKey);
        }
        if (round == null || round.records.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        AbnormalRecord btcRecord = round.records.get(AppConstants.SYMBOL_BTC);
        AbnormalRecord xauRecord = round.records.get(AppConstants.SYMBOL_XAU);
        boolean btcEligible = isEligible(btcRecord, now);
        boolean xauEligible = isEligible(xauRecord, now);

        if (btcEligible && xauEligible) {
            String content = composeAlertLine("BTC", btcRecord.getTriggerSummary())
                    + "\n"
                    + composeAlertLine("XAU", xauRecord.getTriggerSummary());
            notificationHelper.notifyAlert(
                    AppConstants.COMBINED_ALERT_NOTIFICATION_ID,
                    getString(R.string.alert_title),
                    content
            );
            lastNotifyAt.put(AppConstants.SYMBOL_BTC, now);
            lastNotifyAt.put(AppConstants.SYMBOL_XAU, now);
            return;
        }
        if (btcEligible) {
            notificationHelper.notifyAlert(
                    AppConstants.BTC_ALERT_NOTIFICATION_ID,
                    getString(R.string.alert_title),
                    composeAlertLine("BTC", btcRecord.getTriggerSummary())
            );
            lastNotifyAt.put(AppConstants.SYMBOL_BTC, now);
        }
        if (xauEligible) {
            notificationHelper.notifyAlert(
                    AppConstants.XAU_ALERT_NOTIFICATION_ID,
                    getString(R.string.alert_title),
                    composeAlertLine("XAU", xauRecord.getTriggerSummary())
            );
            lastNotifyAt.put(AppConstants.SYMBOL_XAU, now);
        }
    }

    private boolean isEligible(AbnormalRecord record, long now) {
        if (record == null) {
            return false;
        }
        long last = lastNotifyAt.getOrDefault(record.getSymbol(), 0L);
        return now - last >= AppConstants.NOTIFICATION_COOLDOWN_MS;
    }

    private String composeAlertLine(String asset, String triggerSummary) {
        return asset + " 的 " + triggerSummary + " 出现异常！";
    }

    private void updateConnectionStatus() {
        int connectedCount = 0;
        int maxReconnect = 0;
        for (String symbol : AppConstants.MONITOR_SYMBOLS) {
            if (Boolean.TRUE.equals(socketStates.get(symbol))) {
                connectedCount++;
            }
            maxReconnect = Math.max(maxReconnect, reconnectCounts.getOrDefault(symbol, 0));
        }
        String status;
        if (connectedCount == AppConstants.MONITOR_SYMBOLS.size()) {
            status = getString(R.string.connection_connected);
        } else if (connectedCount > 0) {
            status = getString(R.string.connection_partial) + " " + connectedCount + "/" + AppConstants.MONITOR_SYMBOLS.size();
        } else if (maxReconnect > 0) {
            status = "重连中(" + maxReconnect + "/" + AppConstants.MAX_RECONNECT_ATTEMPTS + ")";
        } else {
            status = getString(R.string.connection_connecting);
        }
        repository.setConnectionStatus(status);
        refreshForegroundState();
        requestFloatingWindowRefresh(true);
    }

    private void applyFloatingPreferences() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::applyFloatingPreferences);
            return;
        }
        floatingWindowManager.applyPreferences(
                configManager.isFloatingEnabled(),
                configManager.getFloatingAlpha(),
                configManager.isShowBtc(),
                configManager.isShowXau()
        );
        requestFloatingWindowRefresh(true);
    }

    private void maybePublishPrice(String symbol, KlineData data, long now, boolean force) {
        if (data == null) {
            return;
        }
        double price = data.getClosePrice();
        long lastAt = lastPricePublishAt.getOrDefault(symbol, 0L);
        Double lastPrice = lastPublishedPrice.get(symbol);
        boolean intervalReached = now - lastAt >= AppConstants.PRICE_UPDATE_THROTTLE_MS;
        boolean deltaReached = lastPrice == null || Math.abs(price - lastPrice) >= minPriceDelta(price);
        if (!force && !intervalReached && !deltaReached) {
            return;
        }
        repository.updatePrice(symbol, price);
        lastPricePublishAt.put(symbol, now);
        lastPublishedPrice.put(symbol, price);
    }

    private double minPriceDelta(double price) {
        return Math.max(0.1d, Math.abs(price) * 0.0002d);
    }

    private void requestFloatingWindowRefresh(boolean immediate) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> requestFloatingWindowRefresh(immediate));
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastFloatingRefreshAt;
        if (immediate || elapsed >= AppConstants.FLOATING_UPDATE_THROTTLE_MS) {
            mainHandler.removeCallbacks(floatingRefreshRunnable);
            floatingRefreshScheduled = false;
            lastFloatingRefreshAt = now;
            refreshFloatingWindow();
            return;
        }
        if (floatingRefreshScheduled) {
            return;
        }
        long delay = Math.max(120L, AppConstants.FLOATING_UPDATE_THROTTLE_MS - elapsed);
        floatingRefreshScheduled = true;
        mainHandler.postDelayed(floatingRefreshRunnable, delay);
    }

    private void checkStreamFreshness() {
        if (!pipelineStarted) {
            return;
        }
        long now = System.currentTimeMillis();
        List<String> staleSymbols = new ArrayList<>();
        for (String symbol : AppConstants.MONITOR_SYMBOLS) {
            long last = lastKlineTickAt.getOrDefault(symbol, 0L);
            if (last <= 0L || now - last > AppConstants.SOCKET_STALE_TIMEOUT_MS) {
                staleSymbols.add(symbol);
            }
        }
        if (staleSymbols.isEmpty()) {
            return;
        }
        if (now - lastForcedReconnectAt < AppConstants.STALE_RECONNECT_COOLDOWN_MS) {
            return;
        }
        lastForcedReconnectAt = now;
        for (String symbol : staleSymbols) {
            socketStates.put(symbol, false);
        }
        logManager.warn("行情心跳超时，触发重连: " + String.join(", ", staleSymbols));
        webSocketManager.forceReconnect("行情心跳超时");
        updateConnectionStatus();
    }

    private void refreshFloatingWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::refreshFloatingWindow);
            return;
        }
        floatingWindowManager.update(
                repository.getLatestPriceSnapshot(),
                repository.getLatestClosedKlineSnapshot(),
                Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue()),
                repository.getConnectionStatus().getValue()
        );
    }

    private static class PendingRound {
        private final Map<String, AbnormalRecord> records = new LinkedHashMap<>();
    }

    private static class EvaluationResult {
        private final boolean participating;
        private final boolean abnormal;
        private final String summary;

        private EvaluationResult(boolean participating, boolean abnormal, String summary) {
            this.participating = participating;
            this.abnormal = abnormal;
            this.summary = summary;
        }
    }
}

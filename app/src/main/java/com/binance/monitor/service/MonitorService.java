/*
 * 行情前台服务，负责拉取/接收行情、同步异常记录，以及刷新悬浮窗。
 * WebSocket、异常网关、本地异常记录和悬浮窗都在这里汇总调度。
 */
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
import com.binance.monitor.data.model.AbnormalAlertItem;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.data.remote.AbnormalGatewayClient;
import com.binance.monitor.data.remote.FallbackKlineSocketManager;
import com.binance.monitor.data.remote.v2.GatewayV2Client;
import com.binance.monitor.data.remote.v2.GatewayV2StreamClient;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.ui.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.AppForegroundTracker;
import com.binance.monitor.ui.floating.FloatingPositionAggregator;
import com.binance.monitor.ui.floating.FloatingSymbolCardData;
import com.binance.monitor.ui.floating.FloatingWindowSnapshot;
import com.binance.monitor.ui.floating.FloatingWindowManager;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.ChainLatencyTracer;
import com.binance.monitor.util.GatewayUrlResolver;
import com.binance.monitor.util.NotificationHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class MonitorService extends Service {
    private static final long ABNORMAL_SYNC_ERROR_LOG_COOLDOWN_MS = 60_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastNotifyAt = new HashMap<>();
    private final AppForegroundTracker.ForegroundStateListener appForegroundListener =
            foreground -> mainHandler.post(() -> handleForegroundStateChanged(foreground));
    private final Runnable connectionWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            checkStreamFreshness();
            scheduleConnectionWatchdog(resolveHeartbeatDelayMs());
        }
    };
    private final Runnable abnormalSyncRunnable = new Runnable() {
        @Override
        public void run() {
            requestAbnormalSync();
            scheduleAbnormalSync(resolveAbnormalSyncDelayMs());
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
    private AbnormalGatewayClient abnormalGatewayClient;
    private FallbackKlineSocketManager fallbackKlineSocketManager;
    private GatewayV2Client gatewayV2Client;
    private GatewayV2StreamClient v2StreamClient;
    private AccountStatsPreloadManager accountStatsPreloadManager;
    private ExecutorService executorService;
    private boolean pipelineStarted;
    private boolean foregroundStarted;
    private long lastFloatingRefreshAt;
    private boolean floatingRefreshScheduled;
    private volatile boolean abnormalSyncInFlight;
    private long abnormalSyncSeq;
    private boolean abnormalBootstrapSynced;
    private boolean abnormalSyncAttempted;
    private boolean abnormalSyncHealthy;
    private boolean abnormalEndpointUnsupported;
    private String lastAbnormalSyncError = "";
    private long lastAbnormalSyncErrorAt;
    private volatile boolean v2AccountRefreshInFlight;
    private volatile boolean v2StreamConnected;
    private volatile long lastV2StreamMessageAt;
    private final List<com.binance.monitor.ui.account.model.PositionItem> streamPositionSnapshot = new ArrayList<>();
    private volatile boolean streamAccountSnapshotReceived;
    private volatile long streamPositionsUpdatedAt;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = MonitorRepository.getInstance(this);
        logManager = repository.getLogManager();
        configManager = repository.getConfigManager();
        recordManager = repository.getRecordManager();
        notificationHelper = new NotificationHelper(this);
        floatingWindowManager = new FloatingWindowManager(this);
        abnormalGatewayClient = new AbnormalGatewayClient(this);
        fallbackKlineSocketManager = new FallbackKlineSocketManager(this);
        gatewayV2Client = new GatewayV2Client(this);
        v2StreamClient = new GatewayV2StreamClient(this);
        accountStatsPreloadManager = AccountStatsPreloadManager.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        AppForegroundTracker.getInstance().addListener(appForegroundListener);
        repository.setMonitoringEnabled(true);
        logManager.info("服务初始化完成");
        logResolvedGatewayAddresses();
        applyFloatingPreferences();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : AppConstants.ACTION_BOOTSTRAP;
        if (action == null) {
            action = AppConstants.ACTION_BOOTSTRAP;
        }
        ensureForeground();
        suppressForegroundNotification();
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
                abnormalEndpointUnsupported = false;
                logResolvedGatewayAddresses();
                syncAbnormalConfigAsync();
                scheduleAbnormalSync(0L);
                break;
            case AppConstants.ACTION_BOOTSTRAP:
            default:
                requestForegroundEntryRefresh();
                break;
        }
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
        if (fallbackKlineSocketManager != null) {
            fallbackKlineSocketManager.disconnectAll();
        }
        if (v2StreamClient != null) {
            v2StreamClient.disconnect();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        mainHandler.removeCallbacksAndMessages(null);
        if (floatingWindowManager != null) {
            floatingWindowManager.hide();
        }
        AppForegroundTracker.getInstance().removeListener(appForegroundListener);
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

    // 启动前台服务后立即撤掉通知，避免状态栏和顶部内容提醒继续常驻。
    private void suppressForegroundNotification() {
        if (!foregroundStarted) {
            notificationHelper.cancelServiceNotification();
            return;
        }
        stopForeground(true);
        notificationHelper.cancelServiceNotification();
        foregroundStarted = false;
    }

    private synchronized void startPipelineIfNeeded() {
        if (pipelineStarted) {
            return;
        }
        pipelineStarted = true;
        repository.setConnectionStatus(getString(R.string.connection_connecting));
        fetchBootstrapData();
        scheduleConnectionWatchdog(resolveHeartbeatDelayMs());
        syncAbnormalConfigAsync();
        scheduleAbnormalSync(800L);
        v2StreamClient.connect(new GatewayV2StreamClient.Listener() {
            @Override
            public void onStateChanged(boolean connected, String message) {
                mainHandler.post(() -> {
                    v2StreamConnected = connected;
                    if (connected) {
                        lastV2StreamMessageAt = System.currentTimeMillis();
                    }
                    if (!connected && message != null && !message.trim().isEmpty()) {
                        logManager.warn("v2 stream: " + message);
                    }
                    updateConnectionStatus();
                });
            }

            @Override
            public void onMessage(GatewayV2StreamClient.StreamMessage message) {
                mainHandler.post(() -> {
                    lastV2StreamMessageAt = System.currentTimeMillis();
                    handleV2StreamMessage(message);
                    updateConnectionStatus();
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    if (message != null && !message.trim().isEmpty()) {
                        logManager.warn("v2 stream: " + message);
                    }
                });
            }
        });
        fallbackKlineSocketManager.connect(AppConstants.MONITOR_SYMBOLS, new FallbackKlineSocketManager.Listener() {
            @Override
            public void onFallbackStreamStateChanged(String symbol, boolean connected, int reconnectAttempt, String message) {
                // fallback 仅作为观测层保留，不再参与主链状态或真值写入。
            }

            @Override
            public void onFallbackKlineUpdate(String symbol, KlineData data) {
                // fallback 仅作为观测层保留，不再写入主链展示真值。
            }

            @Override
            public void onFallbackStreamError(String symbol, String message) {
                mainHandler.post(() -> {
                    if (message != null && !message.trim().isEmpty()) {
                        logManager.warn(symbol + " fallback WebSocket: " + message);
                    }
                });
            }
        });
        logManager.info("行情流已启动");
    }

    // 消费统一 v2 stream 消息，主链直接应用增量快照，不再每次回源补拉。
    private void handleV2StreamMessage(@Nullable GatewayV2StreamClient.StreamMessage message) {
        if (message == null) {
            return;
        }
        V2StreamRefreshPlanner.RefreshPlan plan = V2StreamRefreshPlanner.plan(
                message.getType(),
                message.getPayload()
        );
        ChainLatencyTracer.markStreamMessage(message.getType(), plan.shouldRefreshMarket());
        if (plan.shouldRefreshMarket()) {
            applyMarketSnapshotFromStream(plan.getMarketSnapshot());
        }
        if (plan.shouldRefreshAccount()) {
            applyAccountSnapshotFromStream(plan.getAccountSnapshot());
        }
        if (plan.shouldPullAccountSnapshot()) {
            requestAccountRefreshFromV2();
        }
        if (plan.shouldRefreshFloating()) {
            requestFloatingWindowRefresh(false);
        }
    }

    // 收到账户侧 delta 或 full refresh 时，统一补拉最新账户真值并刷新本地库。
    private void requestAccountRefreshFromV2() {
        if (executorService == null || accountStatsPreloadManager == null || v2AccountRefreshInFlight) {
            return;
        }
        v2AccountRefreshInFlight = true;
        executorService.execute(() -> {
            try {
                AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager.fetchForOverlay();
                if (cache == null) {
                    clearStreamAccountSnapshot();
                }
            } catch (Exception exception) {
                logManager.warn("v2 stream 账户补拉失败: " + exception.getMessage());
            } finally {
                v2AccountRefreshInFlight = false;
                mainHandler.post(() -> requestFloatingWindowRefresh(false));
            }
        });
    }

    // 会话清空后同步清掉 stream 持仓快照，避免悬浮窗短时显示旧仓位。
    private void clearStreamAccountSnapshot() {
        synchronized (streamPositionSnapshot) {
            streamPositionSnapshot.clear();
            streamAccountSnapshotReceived = false;
            streamPositionsUpdatedAt = 0L;
        }
    }

    // 启动时打印构建默认值和运行时解析值，便于直接确认 APP 实际在用哪个入口。
    private void logResolvedGatewayAddresses() {
        if (logManager == null || configManager == null) {
            return;
        }
        String runtimeMt5 = configManager.getMt5GatewayBaseUrl();
        String runtimeRoot = GatewayUrlResolver.resolveGatewayRootBaseUrl(
                runtimeMt5,
                AppConstants.MT5_GATEWAY_BASE_URL
        );
        String runtimeRest = configManager.getBinanceRestBaseUrl();
        String runtimeWs = configManager.getBinanceWebSocketBaseUrl();
        String runtimeV2Stream = GatewayUrlResolver.buildEndpoint(runtimeMt5, "/v2/stream");
        logManager.info("APP诊断 BuildConfig MT5=" + AppConstants.MT5_GATEWAY_BASE_URL);
        logManager.info("APP诊断 BuildConfig BinanceREST=" + AppConstants.BASE_REST_URL);
        logManager.info("APP诊断 BuildConfig BinanceWS=" + AppConstants.BASE_WS_URL);
        logManager.info("APP诊断 Runtime MT5=" + runtimeMt5);
        logManager.info("APP诊断 Runtime GatewayRoot=" + runtimeRoot);
        logManager.info("APP诊断 Runtime BinanceREST=" + runtimeRest);
        logManager.info("APP诊断 Runtime BinanceWS=" + runtimeWs);
        logManager.info("APP诊断 Runtime V2Stream=" + runtimeV2Stream);
    }

    // 应用 stream 市场快照，直接回写监控页/悬浮窗共用真值。
    private void applyMarketSnapshotFromStream(@Nullable JSONObject marketSnapshot) {
        if (marketSnapshot == null) {
            return;
        }
        JSONArray states = marketSnapshot.optJSONArray("symbolStates");
        if (states == null || states.length() == 0) {
            return;
        }
        Map<String, KlineData> klineDelta = new HashMap<>();
        Map<String, Double> priceDelta = new HashMap<>();
        for (int i = 0; i < states.length(); i++) {
            JSONObject state = states.optJSONObject(i);
            if (state == null) {
                continue;
            }
            String symbol = state.optString("marketSymbol", "").trim();
            if (symbol.isEmpty()) {
                continue;
            }
            JSONObject latestPatch = state.optJSONObject("latestPatch");
            JSONObject latestClosed = state.optJSONObject("latestClosedCandle");
            JSONObject effective = latestPatch != null ? latestPatch : latestClosed;
            if (effective == null) {
                continue;
            }
            boolean closed = effective.optBoolean("isClosed", latestPatch == null);
            KlineData data = toKlineDataFromJson(symbol, effective, closed);
            klineDelta.put(symbol, data);
            priceDelta.put(symbol, data.getClosePrice());
            ChainLatencyTracer.markMarketPayloadApplied(symbol, data.getCloseTime(), -1L, -1L, -1L);
            if (latestClosed != null) {
                KlineData closedData = toKlineDataFromJson(symbol, latestClosed, true);
                handleClosedKline(closedData);
            }
        }
        repository.applyMarketDelta(klineDelta, priceDelta);
    }

    // 应用 stream 账户快照，仅更新悬浮窗消费层所需持仓数据。
    private void applyAccountSnapshotFromStream(@Nullable JSONObject accountSnapshot) {
        if (accountSnapshot == null) {
            return;
        }
        JSONArray positions = accountSnapshot.optJSONArray("positions");
        if (positions == null) {
            return;
        }
        List<com.binance.monitor.ui.account.model.PositionItem> mappedPositions = new ArrayList<>();
        for (int i = 0; i < positions.length(); i++) {
            JSONObject item = positions.optJSONObject(i);
            if (item == null) {
                continue;
            }
            mappedPositions.add(new com.binance.monitor.ui.account.model.PositionItem(
                    optString(item, "productName",
                            optString(item, "tradeSymbol", optString(item, "code", optString(item, "marketSymbol", "")))),
                    optString(item, "tradeSymbol", optString(item, "code", optString(item, "marketSymbol", ""))),
                    optString(item, "side", ""),
                    optLong(item, "positionTicket", 0L),
                    optLong(item, "orderId", 0L),
                    optDouble(item, "quantity"),
                    optDouble(item, "sellableQuantity"),
                    optDouble(item, "costPrice"),
                    optDouble(item, "latestPrice"),
                    optDouble(item, "marketValue"),
                    optDouble(item, "positionRatio"),
                    optDouble(item, "dayPnL"),
                    optDouble(item, "totalPnL"),
                    optDouble(item, "returnRate"),
                    optDouble(item, "pendingLots"),
                    item.optInt("pendingCount", 0),
                    optDouble(item, "pendingPrice"),
                    optDouble(item, "takeProfit"),
                    optDouble(item, "stopLoss"),
                    optDouble(item, "storageFee")
            ));
        }
        synchronized (streamPositionSnapshot) {
            streamPositionSnapshot.clear();
            for (com.binance.monitor.ui.account.model.PositionItem item : mappedPositions) {
                if (item == null || item.getCode() == null || item.getCode().trim().isEmpty()) {
                    continue;
                }
                streamPositionSnapshot.add(item);
            }
            streamAccountSnapshotReceived = true;
            streamPositionsUpdatedAt = System.currentTimeMillis();
        }
    }

    // 把 stream candle JSON 直接转换为服务层统一 KlineData。
    private KlineData toKlineDataFromJson(String symbol, JSONObject candle, boolean closed) {
        return new KlineData(
                symbol,
                optDouble(candle, "open"),
                optDouble(candle, "high"),
                optDouble(candle, "low"),
                optDouble(candle, "close"),
                optDouble(candle, "volume"),
                optDouble(candle, "quoteVolume"),
                optLong(candle, "openTime", 0L),
                optLong(candle, "closeTime", 0L),
                closed
        );
    }

    private void fetchBootstrapData() {
        requestAccountRefreshFromV2();
        requestFloatingWindowRefresh(true);
    }

    // 按固定节奏向网关拉取异常记录，首轮只落历史数据，不补发旧提醒。
    private void requestAbnormalSync() {
        if (abnormalEndpointUnsupported
                || abnormalGatewayClient == null
                || executorService == null
                || abnormalSyncInFlight) {
            return;
        }
        abnormalSyncInFlight = true;
        long sinceSeq = abnormalBootstrapSynced ? abnormalSyncSeq : 0L;
        executorService.execute(() -> {
            AbnormalGatewayClient.SyncResult result = abnormalGatewayClient.fetch(sinceSeq);
            mainHandler.post(() -> applyAbnormalSyncResult(result));
        });
    }

    // 同步完成后统一写入本地记录，并只对真正新的提醒发通知。
    private void applyAbnormalSyncResult(@Nullable AbnormalGatewayClient.SyncResult result) {
        abnormalSyncInFlight = false;
        abnormalSyncAttempted = true;
        long now = System.currentTimeMillis();
        if (result == null || !result.isSuccess()) {
            abnormalSyncHealthy = false;
            String error = normalizeAbnormalSyncError(result == null ? "未知错误" : result.getError());
            if (AbnormalSyncRuntimeHelper.isUnsupportedEndpointError(error)) {
                abnormalEndpointUnsupported = true;
                logManager.warn("异常同步接口不存在，已暂停后续轮询: " + error);
                scheduleAbnormalSync(0L);
                lastAbnormalSyncError = error;
                lastAbnormalSyncErrorAt = now;
                return;
            }
            if (AbnormalSyncRuntimeHelper.shouldLogSyncError(
                    lastAbnormalSyncError,
                    lastAbnormalSyncErrorAt,
                    error,
                    now,
                    ABNORMAL_SYNC_ERROR_LOG_COOLDOWN_MS)) {
                logManager.warn("异常同步失败: " + error);
                lastAbnormalSyncError = error;
                lastAbnormalSyncErrorAt = now;
            }
            return;
        }
        abnormalEndpointUnsupported = false;
        abnormalSyncHealthy = true;
        lastAbnormalSyncError = "";
        lastAbnormalSyncErrorAt = 0L;
        abnormalSyncSeq = Math.max(abnormalSyncSeq, result.getSyncSeq());
        int appendedCount = 0;
        for (AbnormalRecord record : result.getRecords()) {
            if (recordManager.addRecordIfAbsent(record)) {
                appendedCount++;
            }
        }
        if (Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue())) {
            for (AbnormalAlertItem alert : result.getAlerts()) {
                dispatchServerAlertIfNeeded(alert);
            }
        }
        abnormalBootstrapSynced = true;
    }

    // 把当前本地阈值配置推到网关端，保证服务端判断与设置页一致。
    private void syncAbnormalConfigAsync() {
        if (abnormalGatewayClient == null || executorService == null || configManager == null) {
            return;
        }
        List<SymbolConfig> configs = new ArrayList<>();
        for (String symbol : AppConstants.MONITOR_SYMBOLS) {
            configs.add(configManager.getSymbolConfig(symbol));
        }
        boolean logicAnd = configManager.isUseAndMode();
        executorService.execute(() -> {
            AbnormalGatewayClient.PushResult result = abnormalGatewayClient.pushConfig(logicAnd, configs);
            if (result != null && !result.isSuccess() && result.getError() != null && !result.getError().trim().isEmpty()) {
                logManager.warn("异常配置同步失败: " + result.getError());
            }
        });
    }

    private void scheduleAbnormalSync(long delayMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> scheduleAbnormalSync(delayMs));
            return;
        }
        mainHandler.removeCallbacks(abnormalSyncRunnable);
        if (abnormalEndpointUnsupported) {
            return;
        }
        mainHandler.postDelayed(abnormalSyncRunnable, Math.max(0L, delayMs));
    }

    // 按当前前后台状态重新安排固定任务节奏。
    private void rescheduleRuntimePolicies() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::rescheduleRuntimePolicies);
            return;
        }
        if (!pipelineStarted) {
            return;
        }
        scheduleConnectionWatchdog(resolveHeartbeatDelayMs());
        scheduleAbnormalSync(resolveAbnormalSyncDelayMs());
    }

    // 应用回到前台时立即补拉一次账户与行情，保证页面恢复后先拿到一轮新数据。
    private void handleForegroundStateChanged(boolean foreground) {
        rescheduleRuntimePolicies();
        if (!foreground || !pipelineStarted) {
            return;
        }
        restartV2Stream("foreground_resume");
        requestForegroundEntryRefresh();
    }

    // 统一收口 v2 stream 重建，避免息屏或后台切回后继续占用僵死连接。
    private void restartV2Stream(String reason) {
        if (v2StreamClient == null || !pipelineStarted) {
            return;
        }
        v2StreamConnected = false;
        lastV2StreamMessageAt = 0L;
        v2StreamClient.restart(reason);
    }

    // 统一处理“新进入 APP / 后台回前台”的一次性刷新，避免不同入口刷新不一致。
    private void requestForegroundEntryRefresh() {
        requestAccountRefreshFromV2();
        requestFloatingWindowRefresh(true);
    }

    // 安排下一次连接心跳检查。
    private void scheduleConnectionWatchdog(long delayMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> scheduleConnectionWatchdog(delayMs));
            return;
        }
        mainHandler.removeCallbacks(connectionWatchdogRunnable);
        mainHandler.postDelayed(connectionWatchdogRunnable, Math.max(0L, delayMs));
    }

    private void handleClosedKline(KlineData data) {
        if (data == null || configManager == null || recordManager == null) {
            return;
        }
        if (!Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue())) {
            return;
        }
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
        if (!recordManager.addRecordIfAbsent(record)) {
            return;
        }
        dispatchLocalAbnormalNotification(record);
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
    }

    // 本地命中新异常后立即发通知，并沿用品种级冷却避免同一轮重复弹出。
    private void dispatchLocalAbnormalNotification(AbnormalRecord record) {
        if (record == null || notificationHelper == null) {
            return;
        }
        List<String> symbols = java.util.Collections.singletonList(record.getSymbol());
        long now = System.currentTimeMillis();
        if (!AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                symbols,
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS)) {
            return;
        }
        String asset = AppConstants.symbolToAsset(record.getSymbol());
        notificationHelper.notifyAbnormalAlert(
                "异常交易提醒",
                asset + " 的 " + record.getTriggerSummary() + " 出现异常",
                resolveAlertNotificationId(symbols)
        );
        lastNotifyAt.put(record.getSymbol(), now);
    }

    // 服务端 alert 只做补发，仍按同一套冷却判定避免与本地即时提醒重复。
    private void dispatchServerAlertIfNeeded(@Nullable AbnormalAlertItem alert) {
        if (alert == null || notificationHelper == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                alert.getSymbols(),
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS)) {
            return;
        }
        notificationHelper.notifyAbnormalAlert(
                alert.getTitle(),
                alert.getContent(),
                resolveAlertNotificationId(alert.getSymbols())
        );
        for (String symbol : alert.getSymbols()) {
            if (symbol != null && !symbol.trim().isEmpty()) {
                lastNotifyAt.put(symbol.trim(), now);
            }
        }
    }

    private int resolveAlertNotificationId(@Nullable List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return AppConstants.COMBINED_ALERT_NOTIFICATION_ID;
        }
        boolean hasBtc = false;
        boolean hasXau = false;
        for (String symbol : symbols) {
            if (AppConstants.SYMBOL_BTC.equalsIgnoreCase(symbol)) {
                hasBtc = true;
            } else if (AppConstants.SYMBOL_XAU.equalsIgnoreCase(symbol)) {
                hasXau = true;
            }
        }
        if (hasBtc && hasXau) {
            return AppConstants.COMBINED_ALERT_NOTIFICATION_ID;
        }
        if (hasXau) {
            return AppConstants.XAU_ALERT_NOTIFICATION_ID;
        }
        return AppConstants.BTC_ALERT_NOTIFICATION_ID;
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

    private void updateConnectionStatus() {
        String status = ConnectionStatusResolver.resolveStatus(
                v2StreamConnected,
                lastV2StreamMessageAt,
                System.currentTimeMillis(),
                AppConstants.SOCKET_STALE_TIMEOUT_MS,
                getString(R.string.connection_connected),
                getString(R.string.connection_connecting)
        );
        repository.setConnectionStatus(status);
        suppressForegroundNotification();
        requestFloatingWindowRefresh(false);
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

    private void requestFloatingWindowRefresh(boolean immediate) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> requestFloatingWindowRefresh(immediate));
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastFloatingRefreshAt;
        long throttleMs = resolveFloatingRefreshThrottleMs();
        if (immediate || elapsed >= throttleMs) {
            mainHandler.removeCallbacks(floatingRefreshRunnable);
            floatingRefreshScheduled = false;
            lastFloatingRefreshAt = now;
            refreshFloatingWindow();
            return;
        }
        if (floatingRefreshScheduled) {
            return;
        }
        long delay = Math.max(120L, throttleMs - elapsed);
        floatingRefreshScheduled = true;
        mainHandler.postDelayed(floatingRefreshRunnable, delay);
    }

    // 读取当前连接心跳节奏。
    private long resolveHeartbeatDelayMs() {
        return MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(
                AppForegroundTracker.getInstance().isForeground());
    }

    // 读取当前异常同步节奏。
    private long resolveAbnormalSyncDelayMs() {
        return MonitorRuntimePolicyHelper.resolveAbnormalSyncDelayMs(
                AppForegroundTracker.getInstance().isForeground());
    }

    // 后台时放慢悬浮窗刷新，减少不必要的主线程绘制。
    private long resolveFloatingRefreshThrottleMs() {
        return MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(
                AppForegroundTracker.getInstance().isForeground());
    }

    private void checkStreamFreshness() {
        if (!pipelineStarted) {
            return;
        }
        long now = System.currentTimeMillis();
        if (isV2StreamHealthy(now)) {
            return;
        }
        restartV2Stream("stale_watchdog");
        updateConnectionStatus();
    }

    // 当 v2 stream 仍健康时，旧 WebSocket 只保留为回退层，不再主导状态和重连。
    private boolean isV2StreamHealthy(long now) {
        return ConnectionStatusResolver.isV2StreamHealthy(
                v2StreamConnected,
                lastV2StreamMessageAt,
                now,
                AppConstants.SOCKET_STALE_TIMEOUT_MS
        );
    }

    private void refreshFloatingWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::refreshFloatingWindow);
            return;
        }
        FloatingWindowSnapshot snapshot = buildFloatingSnapshot();
        for (FloatingSymbolCardData card : snapshot.getCards()) {
            if (card == null) {
                continue;
            }
            ChainLatencyTracer.markFloatingUpdate(card.getCode(), card.getUpdatedAt());
        }
        floatingWindowManager.update(snapshot);
    }

    // 组装一份统一悬浮窗快照，确保所有字段在同一次 UI 刷新中一起变化。
    private FloatingWindowSnapshot buildFloatingSnapshot() {
        AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager == null
                ? null
                : accountStatsPreloadManager.getLatestCache();
        List<com.binance.monitor.ui.account.model.PositionItem> positions = new ArrayList<>();
        synchronized (streamPositionSnapshot) {
            if (!streamPositionSnapshot.isEmpty()) {
                positions.addAll(streamPositionSnapshot);
            }
        }
        if (!streamAccountSnapshotReceived && positions.isEmpty()) {
            positions = cache == null || cache.getSnapshot() == null || cache.getSnapshot().getPositions() == null
                    ? new ArrayList<>()
                    : cache.getSnapshot().getPositions();
        }
        List<FloatingSymbolCardData> cards = FloatingPositionAggregator.buildSymbolCards(
                positions,
                repository.getDisplayKlineSnapshot(),
                repository.getDisplayPriceSnapshot(),
                configManager.isShowBtc(),
                configManager.isShowXau()
        );
        return new FloatingWindowSnapshot(
                repository.getConnectionStatus().getValue(),
                Math.max(resolveFloatingUpdatedAt(cards), streamPositionsUpdatedAt),
                cards
        );
    }

    // 从产品卡片里挑出本轮悬浮窗的统一刷新时间。
    private long resolveFloatingUpdatedAt(List<FloatingSymbolCardData> cards) {
        long updatedAt = 0L;
        if (cards == null || cards.isEmpty()) {
            return updatedAt;
        }
        for (FloatingSymbolCardData card : cards) {
            if (card != null) {
                updatedAt = Math.max(updatedAt, card.getUpdatedAt());
            }
        }
        return updatedAt;
    }

    // 读取字符串字段，空值时使用默认值。
    private String optString(JSONObject item, String key, String fallback) {
        if (item == null || key == null || key.trim().isEmpty() || !item.has(key)) {
            return fallback;
        }
        String value = item.optString(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }

    // 读取浮点字段，缺失或异常时返回 0。
    private double optDouble(JSONObject item, String key) {
        if (item == null || key == null || key.trim().isEmpty() || !item.has(key)) {
            return 0d;
        }
        Object value = item.opt(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (Exception ignored) {
                return 0d;
            }
        }
        return 0d;
    }

    // 读取长整型字段，缺失或异常时返回默认值。
    private long optLong(JSONObject item, String key, long fallback) {
        if (item == null || key == null || key.trim().isEmpty() || !item.has(key)) {
            return fallback;
        }
        Object value = item.opt(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    // 统一整理异常同步错误文案，避免空串和空白内容破坏节流判断。
    private String normalizeAbnormalSyncError(@Nullable String error) {
        String safe = error == null ? "" : error.trim();
        return safe.isEmpty() ? "未知错误" : safe;
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

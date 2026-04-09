/*
 * 行情前台服务，负责消费服务端已发布的市场、账户和异常状态，并刷新悬浮窗。
 * v2 stream、异常记录存储和悬浮窗都在这里做统一调度。
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
import com.binance.monitor.data.local.AbnormalAlertDispatchStore;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.model.AbnormalAlertItem;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.data.remote.AbnormalGatewayClient;
import com.binance.monitor.data.remote.v2.GatewayV2Client;
import com.binance.monitor.data.remote.v2.GatewayV2StreamClient;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.runtime.ConnectionStage;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

public class MonitorService extends Service {
    private static volatile boolean serviceRunning;
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
    private AbnormalAlertDispatchStore abnormalAlertDispatchStore;
    private NotificationHelper notificationHelper;
    private FloatingWindowManager floatingWindowManager;
    private AbnormalGatewayClient abnormalGatewayClient;
    private GatewayV2Client gatewayV2Client;
    private GatewayV2StreamClient v2StreamClient;
    private AccountStatsPreloadManager accountStatsPreloadManager;
    private ExecutorService executorService;
    private boolean pipelineStarted;
    private boolean foregroundStarted;
    private long lastFloatingRefreshAt;
    private boolean floatingRefreshScheduled;
    private volatile boolean v2AccountHistoryRefreshInFlight;
    private final Object accountHistoryRefreshLock = new Object();
    private String pendingAccountHistoryRevision = "";
    private volatile ConnectionStage v2StreamStage = ConnectionStage.CONNECTING;
    private volatile boolean v2StreamConnected;
    private volatile long lastV2StreamMessageAt;
    private final List<com.binance.monitor.ui.account.model.PositionItem> streamPositionSnapshot = new ArrayList<>();
    private volatile boolean streamAccountSnapshotReceived;
    private volatile long streamPositionsUpdatedAt;
    private String lastForegroundNotificationSignature = "";
    private String lastPublishedConnectionStatus = "";
    private final Set<String> dispatchedServerAlertIds = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        serviceRunning = true;
        repository = MonitorRepository.getInstance(this);
        logManager = repository.getLogManager();
        configManager = repository.getConfigManager();
        recordManager = repository.getRecordManager();
        abnormalAlertDispatchStore = new AbnormalAlertDispatchStore(this);
        notificationHelper = new NotificationHelper(this);
        floatingWindowManager = new FloatingWindowManager(this);
        abnormalGatewayClient = new AbnormalGatewayClient(this);
        gatewayV2Client = new GatewayV2Client(this);
        v2StreamClient = new GatewayV2StreamClient(this);
        accountStatsPreloadManager = AccountStatsPreloadManager.getInstance(this);
        executorService = Executors.newSingleThreadExecutor();
        synchronized (dispatchedServerAlertIds) {
            dispatchedServerAlertIds.clear();
            if (abnormalAlertDispatchStore != null) {
                dispatchedServerAlertIds.addAll(abnormalAlertDispatchStore.snapshot());
            }
        }
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
                logResolvedGatewayAddresses();
                syncAbnormalConfigAsync();
                break;
            case AppConstants.ACTION_BOOTSTRAP:
            default:
                requestForegroundEntryRefresh();
                break;
        }
        refreshForegroundNotification();
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
        foregroundStarted = false;
        serviceRunning = false;
        notificationHelper.cancelServiceNotification();
        logManager.info("服务已销毁");
    }

    // 返回当前进程内监控服务是否已经创建完成，供入口页避免重复启动。
    public static boolean isServiceRunning() {
        return serviceRunning;
    }

    private void ensureForeground() {
        if (notificationHelper == null || repository == null) {
            return;
        }
        String connectionState = getCurrentConnectionStatus();
        boolean monitoringEnabled = Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue());
        if (foregroundStarted) {
            refreshForegroundNotification();
            return;
        }
        String signature = buildForegroundNotificationSignature(connectionState, monitoringEnabled);
        startForeground(AppConstants.SERVICE_NOTIFICATION_ID,
                notificationHelper.buildServiceNotification(connectionState, monitoringEnabled));
        lastForegroundNotificationSignature = signature;
        foregroundStarted = true;
    }

    // 刷新前台服务通知文案，保持 Android 认可的持续运行资格。
    private void refreshForegroundNotification() {
        if (!foregroundStarted || notificationHelper == null || repository == null) {
            return;
        }
        String connectionState = getCurrentConnectionStatus();
        boolean monitoringEnabled = Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue());
        String signature = buildForegroundNotificationSignature(connectionState, monitoringEnabled);
        if (signature.equals(lastForegroundNotificationSignature)) {
            return;
        }
        notificationHelper.updateServiceNotification(connectionState, monitoringEnabled);
        lastForegroundNotificationSignature = signature;
    }

    // 只把真正影响前台通知文案的字段纳入签名，避免重复 notify。
    private String buildForegroundNotificationSignature(String connectionState, boolean monitoringEnabled) {
        return (connectionState == null ? "" : connectionState.trim())
                + "|"
                + monitoringEnabled;
    }

    // 读取当前连接状态真值，优先使用服务内同步状态，避免 postValue 回读滞后。
    private String getCurrentConnectionStatus() {
        if (lastPublishedConnectionStatus != null && !lastPublishedConnectionStatus.trim().isEmpty()) {
            return lastPublishedConnectionStatus;
        }
        if (repository == null) {
            return "";
        }
        String currentStatus = repository.getConnectionStatus().getValue();
        return currentStatus == null ? "" : currentStatus;
    }

    // 统一发布连接状态，先更新服务内真值，再同步写入仓库。
    private void publishConnectionStatus(String status) {
        String normalized = status == null ? "" : status;
        lastPublishedConnectionStatus = normalized;
        if (repository != null) {
            repository.setConnectionStatus(normalized);
        }
    }

    private synchronized void startPipelineIfNeeded() {
        if (pipelineStarted) {
            return;
        }
        pipelineStarted = true;
        v2StreamStage = ConnectionStage.CONNECTING;
        publishConnectionStatus(getString(R.string.connection_connecting));
        fetchBootstrapData();
        scheduleConnectionWatchdog(resolveHeartbeatDelayMs());
        syncAbnormalConfigAsync();
        v2StreamClient.connect(new GatewayV2StreamClient.Listener() {
            @Override
            public void onStateChanged(GatewayV2StreamClient.ConnectionEvent event) {
                mainHandler.post(() -> {
                    v2StreamStage = event == null ? ConnectionStage.CONNECTING : event.getStage();
                    v2StreamConnected = event != null && event.isConnected();
                    if (v2StreamConnected) {
                        lastV2StreamMessageAt = System.currentTimeMillis();
                    }
                    String message = event == null ? "" : event.getMessage();
                    if (!v2StreamConnected && message != null && !message.trim().isEmpty()) {
                        logManager.warn("v2 stream: " + message);
                    }
                    updateConnectionStatus();
                });
            }

            @Override
            public void onMessage(GatewayV2StreamClient.StreamMessage message) {
                mainHandler.post(() -> {
                    lastV2StreamMessageAt = System.currentTimeMillis();
                    try {
                        handleV2StreamMessage(message);
                    } catch (RuntimeException exception) {
                        logManager.warn("v2 stream payload invalid: " + exception.getMessage());
                    }
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
            applyAccountSnapshotFromStream(plan.getAccountSnapshot(), message.getPublishedAt());
        }
        if (plan.shouldPullAccountHistory()) {
            requestAccountHistoryRefreshFromV2(plan.getAccountHistoryRevision());
        }
        if (plan.hasAbnormalChange()) {
            applyAbnormalSnapshotFromStream(message.getPayload().optJSONObject("changes").optJSONObject("abnormal"));
        }
        if (plan.shouldRefreshFloating()) {
            requestFloatingWindowRefresh(false);
        }
    }

    // 只有 history revision 前进时才补拉 history，避免运行态每次都重复打 snapshot。
    private void requestAccountHistoryRefreshFromV2(@Nullable String historyRevision) {
        String safeHistoryRevision = historyRevision == null ? "" : historyRevision.trim();
        if (safeHistoryRevision.isEmpty()
                || executorService == null
                || accountStatsPreloadManager == null) {
            return;
        }
        synchronized (accountHistoryRefreshLock) {
            if (v2AccountHistoryRefreshInFlight) {
                pendingAccountHistoryRevision = safeHistoryRevision;
                return;
            }
            v2AccountHistoryRefreshInFlight = true;
            pendingAccountHistoryRevision = "";
        }
        executorService.execute(() -> {
            try {
                AccountStatsPreloadManager.Cache cache =
                        accountStatsPreloadManager.refreshHistoryForRevision(safeHistoryRevision);
                if (cache == null) {
                    clearStreamAccountSnapshot();
                }
            } catch (Exception exception) {
                logManager.warn("v2 stream 账户历史补拉失败: " + exception.getMessage());
            } finally {
                String nextHistoryRevision;
                synchronized (accountHistoryRefreshLock) {
                    nextHistoryRevision = pendingAccountHistoryRevision == null
                            ? ""
                            : pendingAccountHistoryRevision.trim();
                    pendingAccountHistoryRevision = "";
                    v2AccountHistoryRefreshInFlight = false;
                }
                mainHandler.post(() -> {
                    requestFloatingWindowRefresh(false);
                    if (!nextHistoryRevision.isEmpty() && !nextHistoryRevision.equals(safeHistoryRevision)) {
                        requestAccountHistoryRefreshFromV2(nextHistoryRevision);
                    }
                });
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
        Map<String, KlineData> overviewKlineDelta = new HashMap<>();
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
            if (latestClosed != null) {
                overviewKlineDelta.put(symbol, toKlineDataFromJson(symbol, latestClosed, true));
            }
            ChainLatencyTracer.markMarketPayloadApplied(symbol, data.getCloseTime(), -1L, -1L, -1L);
        }
        repository.applyMarketDelta(klineDelta, priceDelta, overviewKlineDelta);
    }

    // 应用 stream 账户运行态，同时更新悬浮窗即时持仓和账户页本地运行态缓存。
    private void applyAccountSnapshotFromStream(@Nullable JSONObject accountSnapshot, long publishedAt) {
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
            String tradeSymbol = requireCanonicalTradeSymbol(item, "v2 stream position");
            String productName = requireCanonicalProductName(item, tradeSymbol, "v2 stream position");
            mappedPositions.add(new com.binance.monitor.ui.account.model.PositionItem(
                    productName,
                    tradeSymbol,
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
        if (executorService == null || accountStatsPreloadManager == null) {
            return;
        }
        final String snapshotBody = accountSnapshot.toString();
        executorService.execute(() -> {
            try {
                JSONObject snapshotCopy = new JSONObject(snapshotBody);
                AccountStatsPreloadManager.Cache cache =
                        accountStatsPreloadManager.applyPublishedAccountRuntime(snapshotCopy, publishedAt);
                if (cache == null) {
                    clearStreamAccountSnapshot();
                }
            } catch (Exception exception) {
                logManager.warn("v2 stream 账户运行态应用失败: " + exception.getMessage());
            } finally {
                mainHandler.post(() -> requestFloatingWindowRefresh(false));
            }
        });
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
        requestFloatingWindowRefresh(true);
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
    }

    // 应用回到前台时只切换运行节奏；仅当主链已失活时才重建 stream。
    private void handleForegroundStateChanged(boolean foreground) {
        rescheduleRuntimePolicies();
        if (!foreground || !pipelineStarted) {
            return;
        }
        boolean streamHealthy = isV2StreamHealthy(System.currentTimeMillis());
        if (streamHealthy) {
            requestFloatingWindowRefresh(false);
            updateConnectionStatus();
            return;
        }
        if (abnormalGatewayClient != null) {
            abnormalGatewayClient.resetTransport();
        }
        if (gatewayV2Client != null) {
            gatewayV2Client.resetTransport();
        }
        restartV2Stream("foreground_resume");
    }

    // 统一收口 v2 stream 重建，避免息屏或后台切回后继续占用僵死连接。
    private void restartV2Stream(String reason) {
        if (v2StreamClient == null || !pipelineStarted) {
            return;
        }
        v2StreamStage = ConnectionStage.RECONNECTING;
        v2StreamConnected = false;
        lastV2StreamMessageAt = 0L;
        v2StreamClient.restart(reason);
    }

    // 统一处理“新进入 APP / 后台回前台”的一次性刷新，避免不同入口刷新不一致。
    private void requestForegroundEntryRefresh() {
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

    // 消费 stream 下发的异常快照或增量，统一写入本地 store 并补发服务端 alerts。
    private void applyAbnormalSnapshotFromStream(@Nullable JSONObject abnormalChange) {
        if (abnormalChange == null || recordManager == null) {
            return;
        }
        JSONObject snapshot = abnormalChange.optJSONObject("snapshot");
        if (snapshot != null) {
            recordManager.replaceAll(parseAbnormalRecords(snapshot.optJSONArray("records")));
            return;
        }

        JSONObject delta = abnormalChange.optJSONObject("delta");
        if (delta == null) {
            return;
        }
        for (AbnormalRecord record : parseAbnormalRecords(delta.optJSONArray("records"))) {
            if (recordManager.addRecordIfAbsent(record) && floatingWindowManager != null) {
                floatingWindowManager.notifyAbnormalEvent(record.getSymbol());
            }
        }
        if (!Boolean.TRUE.equals(repository.getMonitoringEnabled().getValue())) {
            return;
        }
        for (AbnormalAlertItem alert : parseAbnormalAlerts(delta.optJSONArray("alerts"))) {
            dispatchServerAlertIfNeeded(alert);
        }
    }

    private List<AbnormalRecord> parseAbnormalRecords(@Nullable JSONArray array) {
        List<AbnormalRecord> records = new ArrayList<>();
        if (array == null) {
            return records;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            try {
                records.add(AbnormalRecord.fromJson(item));
            } catch (Exception ignored) {
            }
        }
        return records;
    }

    private List<AbnormalAlertItem> parseAbnormalAlerts(@Nullable JSONArray array) {
        List<AbnormalAlertItem> alerts = new ArrayList<>();
        if (array == null) {
            return alerts;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            try {
                alerts.add(AbnormalAlertItem.fromJson(item));
            } catch (Exception ignored) {
            }
        }
        return alerts;
    }

    // 服务端 alert 只做补发，仍按同一套冷却判定避免与本地即时提醒重复。
    private void dispatchServerAlertIfNeeded(@Nullable AbnormalAlertItem alert) {
        if (alert == null || notificationHelper == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Set<String> dispatchedAlertIdSnapshot;
        synchronized (dispatchedServerAlertIds) {
            dispatchedAlertIdSnapshot = new HashSet<>(dispatchedServerAlertIds);
        }
        if (!AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                alert.getId(),
                alert.getSymbols(),
                dispatchedAlertIdSnapshot,
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
        synchronized (dispatchedServerAlertIds) {
            dispatchedServerAlertIds.add(alert.getId());
        }
        if (abnormalAlertDispatchStore != null) {
            abnormalAlertDispatchStore.markDispatched(alert.getId());
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

    private void updateConnectionStatus() {
        ConnectionStage resolvedStage = getCurrentConnectionStage();
        String status = ConnectionStatusResolver.resolveStatus(
                resolvedStage,
                v2StreamConnected,
                lastV2StreamMessageAt,
                System.currentTimeMillis(),
                AppConstants.SOCKET_STALE_TIMEOUT_MS,
                getString(R.string.connection_connected),
                getString(R.string.connection_connecting),
                getString(R.string.connection_reconnecting),
                getString(R.string.connection_disconnected)
        );
        String currentStatus = getCurrentConnectionStatus();
        if (!status.equals(currentStatus)) {
            publishConnectionStatus(status);
            refreshForegroundNotification();
            requestFloatingWindowRefresh(false);
        }
    }

    // 统一读取当前连接阶段，确保字符串状态和悬浮窗状态使用同一真值。
    private ConnectionStage getCurrentConnectionStage() {
        return ConnectionStatusResolver.resolveStage(
                v2StreamStage,
                v2StreamConnected,
                lastV2StreamMessageAt,
                System.currentTimeMillis(),
                AppConstants.SOCKET_STALE_TIMEOUT_MS
        );
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
                repository.getDisplayOverviewKlineSnapshot(),
                repository.getDisplayPriceSnapshot(),
                configManager.isShowBtc(),
                configManager.isShowXau()
        );
        return new FloatingWindowSnapshot(
                getCurrentConnectionStage(),
                getCurrentConnectionStatus(),
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

    // 主链字段必须来自 canonical 协议字段，缺失时直接报错，不再跨字段拼装。
    private String requireCanonicalTradeSymbol(JSONObject item, String context) {
        String tradeSymbol = optString(item, "tradeSymbol", "").trim();
        if (tradeSymbol.isEmpty()) {
            throw new IllegalStateException(context + " missing tradeSymbol");
        }
        return tradeSymbol;
    }

    // 展示字段也必须和 canonical tradeSymbol 保持一致，避免同一产品多种名字混用。
    private String requireCanonicalProductName(JSONObject item, String tradeSymbol, String context) {
        String productName = optString(item, "productName", "").trim();
        if (productName.isEmpty()) {
            throw new IllegalStateException(context + " missing productName");
        }
        if (!productName.equals(tradeSymbol)) {
            throw new IllegalStateException(context + " productName must equal tradeSymbol");
        }
        return productName;
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

}

/*
 * 行情前台服务，负责消费服务端已发布的市场、账户和异常状态，并刷新悬浮窗。
 * v2 stream、异常记录存储和悬浮窗都在这里做统一调度。
 */
package com.binance.monitor.service;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

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
import com.binance.monitor.data.remote.v2.GatewayV2SessionClient;
import com.binance.monitor.data.remote.v2.GatewayV2StreamClient;
import com.binance.monitor.data.repository.MonitorRepository;
import com.binance.monitor.runtime.ConnectionStage;
import com.binance.monitor.runtime.AppForegroundTracker;
import com.binance.monitor.runtime.account.AccountSessionRecoveryHelper;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;
import com.binance.monitor.runtime.market.truth.GapDetector;
import com.binance.monitor.runtime.market.truth.model.MarketTruthSnapshot;
import com.binance.monitor.runtime.market.truth.model.MarketTruthSymbolState;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.service.stream.V2StreamSequenceGuard;
import com.binance.monitor.ui.floating.FloatingWindowManager;
import com.binance.monitor.util.ChainLatencyTracer;
import com.binance.monitor.util.GatewayUrlResolver;
import com.binance.monitor.util.NotificationHelper;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;

public class MonitorService extends Service {
    private static final int MARKET_TRUTH_REPAIR_LIMIT = 180;
    private static final long MARKET_TRUTH_REPAIR_COOLDOWN_MS = 5_000L;
    private static volatile boolean serviceRunning;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Long> lastNotifyAt = new HashMap<>();
    private final AppForegroundTracker.ForegroundStateListener appForegroundListener =
            foreground -> mainHandler.post(() -> handleForegroundStateChanged(foreground));
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? "" : String.valueOf(intent.getAction());
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mainHandler.post(() -> handleScreenInteractiveChanged(false));
                return;
            }
            if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                mainHandler.post(() -> handleScreenInteractiveChanged(true));
            }
        }
    };
    private final Runnable connectionWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            checkStreamFreshness();
            scheduleConnectionWatchdog(resolveHeartbeatDelayMs());
        }
    };
    private final Observer<MarketTruthSnapshot> marketTruthObserver = snapshot -> handleMarketTruthChanged();

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
    private SecureSessionPrefs secureSessionPrefs;
    private AccountSessionRecoveryHelper accountSessionRecoveryHelper;
    private AccountStatsPreloadManager accountStatsPreloadManager;
    private ExecutorService realtimeMarketExecutorService;
    private ExecutorService accountRuntimeExecutorService;
    private ExecutorService backgroundExecutorService;
    private MonitorForegroundNotificationCoordinator foregroundNotificationCoordinator;
    private MonitorFloatingCoordinator floatingCoordinator;
    private MonitorStreamCoordinator streamCoordinator;
    private MonitorAlertCoordinator alertCoordinator;
    private boolean pipelineStarted;
    private final V2StreamSequenceGuard v2StreamSequenceGuard = new V2StreamSequenceGuard();
    private final AtomicBoolean remoteSessionRecoveryInFlight = new AtomicBoolean(false);
    private final AtomicBoolean marketTruthRepairInFlight = new AtomicBoolean(false);
    private boolean screenStateReceiverRegistered;
    private volatile boolean deviceInteractive = true;
    private volatile ConnectionStage v2StreamStage = ConnectionStage.CONNECTING;
    private volatile boolean v2StreamConnected;
    private volatile boolean screenInteractive = true;
    private volatile long lastV2StreamMessageAt;
    private volatile long lastMarketTruthRepairAt;
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
        secureSessionPrefs = new SecureSessionPrefs(this);
        accountStatsPreloadManager = AccountStatsPreloadManager.getInstance(this);
        deviceInteractive = resolveDeviceInteractive();
        screenInteractive = deviceInteractive;
        registerScreenStateReceiver();
        accountSessionRecoveryHelper = new AccountSessionRecoveryHelper(
                new GatewayV2SessionClient(this),
                secureSessionPrefs,
                configManager,
                accountStatsPreloadManager,
                logManager
        );
        realtimeMarketExecutorService = Executors.newSingleThreadExecutor();
        accountRuntimeExecutorService = Executors.newSingleThreadExecutor();
        backgroundExecutorService = Executors.newSingleThreadExecutor();
        foregroundNotificationCoordinator = new MonitorForegroundNotificationCoordinator(notificationHelper, repository);
        streamCoordinator = new MonitorStreamCoordinator(new MonitorStreamCoordinator.Host() {
            @Override
            public void updateConnectionStatus() {
                MonitorService.this.updateConnectionStatus();
            }

            @Override
            public void applyRealtimeMessage(@Nullable Object message) {
                // 先只建立 seam，后续再把具体流消息处理迁入协调器。
            }
        });
        alertCoordinator = new MonitorAlertCoordinator(new MonitorAlertCoordinator.Host() {
            @Override
            public void dispatchParsedServerAlert(@Nullable Object alert) {
                // 先只建立 seam，后续再把具体提醒编排迁入协调器。
            }
        });
        floatingCoordinator = new MonitorFloatingCoordinator(
                mainHandler,
                floatingWindowManager,
                configManager,
                repository,
                new MonitorFloatingCoordinator.DataSource() {
                    @Override
                    public AccountStatsPreloadManager.Cache getLatestAccountCache() {
                        return resolveCurrentSessionFloatingCache();
                    }

                    @Override
                    public ConnectionStage getCurrentConnectionStage() {
                        return MonitorService.this.getCurrentConnectionStage();
                    }

                    @Override
                    public String getCurrentConnectionStatus() {
                        return MonitorService.this.getCurrentConnectionStatus();
                    }
                }
        );
        repository.getMarketTruthSnapshotLiveData().observeForever(marketTruthObserver);
        synchronized (dispatchedServerAlertIds) {
            dispatchedServerAlertIds.clear();
            if (abnormalAlertDispatchStore != null) {
                dispatchedServerAlertIds.addAll(abnormalAlertDispatchStore.snapshot());
            }
        }
        AppForegroundTracker.getInstance().addListener(appForegroundListener);
        logManager.info("服务初始化完成");
        floatingCoordinator.applyPreferences();
        floatingCoordinator.setScreenInteractive(screenInteractive);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : AppConstants.ACTION_BOOTSTRAP;
        if (action == null) {
            action = AppConstants.ACTION_BOOTSTRAP;
        }
        foregroundNotificationCoordinator.ensureForeground(new MonitorForegroundNotificationCoordinator.Host() {
            @Override
            public void enterForeground(int notificationId, @NonNull Notification notification) {
                startForeground(notificationId, notification);
            }

            @Override
            public void exitForeground() {
                stopForeground(true);
            }
        }, getCurrentConnectionStatus());
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
                floatingCoordinator.applyPreferences();
                syncAbnormalConfigAsync();
                break;
            case AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME:
                if (accountStatsPreloadManager != null) {
                    accountStatsPreloadManager.clearAccountRuntimeState(null, null);
                }
                floatingCoordinator.requestRefresh(true);
                break;
            case AppConstants.ACTION_BOOTSTRAP:
            default:
                requestForegroundEntryRefresh();
                break;
        }
        foregroundNotificationCoordinator.refreshNotification(getCurrentConnectionStatus());
        floatingCoordinator.requestRefresh(true);
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
        if (repository != null) {
            repository.getMarketTruthSnapshotLiveData().removeObserver(marketTruthObserver);
        }
        unregisterScreenStateReceiver();
        if (realtimeMarketExecutorService != null) {
            realtimeMarketExecutorService.shutdownNow();
            realtimeMarketExecutorService = null;
        }
        if (accountRuntimeExecutorService != null) {
            accountRuntimeExecutorService.shutdownNow();
            accountRuntimeExecutorService = null;
        }
        if (backgroundExecutorService != null) {
            backgroundExecutorService.shutdownNow();
            backgroundExecutorService = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        AppForegroundTracker.getInstance().removeListener(appForegroundListener);
        serviceRunning = false;
        if (floatingCoordinator != null) {
            floatingCoordinator.onDestroy();
        }
        if (foregroundNotificationCoordinator != null) {
            foregroundNotificationCoordinator.onDestroy();
        }
        logManager.info("服务已销毁");
    }

    // 统一在主线程把市场真值变化转成悬浮窗刷新请求，避免 service 和 chart 各自再接一条悬浮窗市场链。
    private void handleMarketTruthChanged() {
        if (floatingCoordinator == null) {
            return;
        }
        mainHandler.post(() -> floatingCoordinator.requestRefresh(false));
    }

    // 返回当前进程内监控服务是否已经创建完成，供入口页避免重复启动。
    public static boolean isServiceRunning() {
        return serviceRunning;
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
                        v2StreamSequenceGuard.reset();
                        lastV2StreamMessageAt = System.currentTimeMillis();
                    }
                    updateConnectionStatus();
                });
            }

            @Override
            public void onMessage(GatewayV2StreamClient.StreamMessage message) {
                executeRealtimeMarket(() -> {
                    try {
                        handleV2StreamMessage(message);
                    } catch (RuntimeException exception) {
                        logManager.warn("v2 stream payload invalid: " + exception.getMessage());
                    }
                    mainHandler.post(MonitorService.this::updateConnectionStatus);
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    if (message != null && !message.trim().isEmpty()) {
                        logManager.warn("行情流连接异常: " + message.trim());
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
        MonitorStreamRuntimeModeHelper.RuntimeMode runtimeMode = resolveStreamRuntimeMode();
        if (GatewayV2StreamClient.MESSAGE_TYPE_MARKET_TICK.equals(message.getType())) {
            handleMarketTickMessage(message, runtimeMode);
            return;
        }
        long busSeq = message.getBusSeq();
        if (!v2StreamSequenceGuard.shouldApplyBusSeq(busSeq)) {
            return;
        }
        V2StreamRefreshPlanner.RefreshPlan plan = V2StreamRefreshPlanner.plan(
                message.getType(),
                message.getPayload()
        );
        boolean shouldApplyMarketSnapshot = plan.shouldRefreshMarket()
                && MonitorStreamRuntimeModeHelper.shouldApplyMarketSnapshot(runtimeMode);
        ChainLatencyTracer.markStreamMessage(message.getType(), shouldApplyMarketSnapshot);
        if (plan.hasAbnormalChange()) {
            applyAbnormalSnapshotFromStream(message.getPayload().optJSONObject("changes").optJSONObject("abnormal"));
        }
        if (shouldApplyMarketSnapshot) {
            applyMarketSnapshotFromStream(plan.getMarketSnapshot());
        }
        boolean accountApplyPending = false;
        if (plan.shouldRefreshAccount()) {
            if (MonitorStreamRuntimeModeHelper.shouldApplyFullAccountRuntime(runtimeMode)) {
                accountApplyPending = applyAccountSnapshotFromStream(plan.getAccountSnapshot(), message.getPublishedAt(), busSeq);
            } else if (MonitorStreamRuntimeModeHelper.shouldApplyLiteAccountRuntime(runtimeMode)) {
                applyAccountSnapshotLiteFromStream(plan.getAccountSnapshot(), message.getPublishedAt());
            }
        }
        if (plan.shouldPullAccountHistory() && MonitorStreamRuntimeModeHelper.shouldPullAccountHistory(runtimeMode)) {
            requestAccountHistoryRefreshFromV2(plan.getAccountHistoryRevision());
        }
        if (!accountApplyPending) {
            v2StreamSequenceGuard.commitAppliedBusSeq(busSeq);
            lastV2StreamMessageAt = System.currentTimeMillis();
        }
        if (plan.shouldRefreshFloating() && MonitorStreamRuntimeModeHelper.shouldRefreshFloating(runtimeMode)) {
            floatingCoordinator.requestRefresh(false);
        }
    }

    // 市场直推链单独消费 marketTick，不再依赖旧 changes.market 才允许推进真值。
    private void handleMarketTickMessage(@Nullable GatewayV2StreamClient.StreamMessage message,
                                         @NonNull MonitorStreamRuntimeModeHelper.RuntimeMode runtimeMode) {
        if (message == null) {
            return;
        }
        long marketSeq = message.getMarketSeq();
        if (!v2StreamSequenceGuard.shouldApplyMarketSeq(marketSeq)) {
            return;
        }
        boolean shouldApplyMarketSnapshot = MonitorStreamRuntimeModeHelper.shouldApplyMarketSnapshot(runtimeMode);
        ChainLatencyTracer.markStreamMessage(message.getType(), shouldApplyMarketSnapshot);
        boolean marketApplied = false;
        if (shouldApplyMarketSnapshot) {
            marketApplied = applyMarketSnapshotFromStream(message.getMarketSnapshot());
        }
        v2StreamSequenceGuard.commitAppliedMarketSeq(marketSeq);
        if (marketApplied) {
            lastV2StreamMessageAt = System.currentTimeMillis();
        }
        if (MonitorStreamRuntimeModeHelper.shouldRefreshFloating(runtimeMode)) {
            floatingCoordinator.requestRefresh(false);
        }
    }

    // 只有 history revision 前进时才补拉 history，避免运行态每次都重复打 snapshot。
    private void requestAccountHistoryRefreshFromV2(@Nullable String historyRevision) {
        String safeHistoryRevision = historyRevision == null ? "" : historyRevision.trim();
        if (safeHistoryRevision.isEmpty()
                || accountStatsPreloadManager == null) {
            return;
        }
        accountStatsPreloadManager.queueHistoryRefreshForRevision(
                safeHistoryRevision,
                () -> floatingCoordinator.requestRefresh(false)
        );
    }

    // 应用 stream 市场快照，直接回写监控页/悬浮窗共用真值。
    private boolean applyMarketSnapshotFromStream(@Nullable JSONObject marketSnapshot) {
        if (marketSnapshot == null) {
            return false;
        }
        JSONArray states = marketSnapshot.optJSONArray("symbolStates");
        if (states == null || states.length() == 0) {
            return false;
        }
        List<SymbolMarketWindow> symbolWindows = new ArrayList<>();
        for (int i = 0; i < states.length(); i++) {
            JSONObject state = states.optJSONObject(i);
            if (state == null) {
                continue;
            }
            String symbol = state.optString("marketSymbol", "").trim();
            String tradeSymbol = state.optString("tradeSymbol", "").trim();
            if (symbol.isEmpty()) {
                continue;
            }
            JSONObject latestPatch = state.optJSONObject("latestPatch");
            JSONObject latestClosed = state.optJSONObject("latestClosedCandle");
            JSONObject effective = latestPatch != null ? latestPatch : latestClosed;
            if (effective == null) {
                continue;
            }
            KlineData latestPatchData = latestPatch == null
                    ? null
                    : toKlineDataFromJson(symbol, latestPatch, latestPatch.optBoolean("isClosed", false));
            KlineData latestClosedData = latestClosed == null
                    ? null
                    : toKlineDataFromJson(symbol, latestClosed, true);
            KlineData displayData = latestPatchData != null ? latestPatchData : latestClosedData;
            if (displayData == null) {
                continue;
            }
            symbolWindows.add(new SymbolMarketWindow(
                    symbol,
                    tradeSymbol,
                    displayData.getClosePrice(),
                    displayData.getOpenTime(),
                    displayData.getCloseTime(),
                    latestClosedData,
                    latestPatchData
            ));
            ChainLatencyTracer.markMarketPayloadApplied(symbol, displayData.getCloseTime(), -1L, -1L, -1L);
        }
        if (symbolWindows.isEmpty()) {
            return false;
        }
        repository.applyMarketRuntimeSnapshot(new MarketRuntimeSnapshot(0L, 0L, System.currentTimeMillis(), toSymbolWindowMap(symbolWindows)));
        return true;
    }

    @NonNull
    private Map<String, SymbolMarketWindow> toSymbolWindowMap(@NonNull List<SymbolMarketWindow> symbolWindows) {
        Map<String, SymbolMarketWindow> symbolWindowMap = new HashMap<>();
        for (SymbolMarketWindow window : symbolWindows) {
            if (window == null || window.getMarketSymbol().trim().isEmpty()) {
                continue;
            }
            symbolWindowMap.put(window.getMarketSymbol(), window);
        }
        return symbolWindowMap;
    }

    // 应用 stream 账户运行态，同时更新悬浮窗即时持仓和账户页本地运行态缓存。
    private boolean applyAccountSnapshotFromStream(@Nullable JSONObject accountSnapshot, long publishedAt, long busSeq) {
        if (accountSnapshot == null) {
            return false;
        }
        if (accountRuntimeExecutorService == null || accountStatsPreloadManager == null) {
            if (logManager != null) {
                logManager.warn("v2 stream 账户运行态应用失败: account runtime executor unavailable");
            }
            return true;
        }
        final String snapshotBody = accountSnapshot.toString();
        boolean submitted = executeAccountRuntime(() -> {
            try {
                JSONObject snapshotCopy = new JSONObject(snapshotBody);
                AccountStatsPreloadManager.ApplyResult result =
                        accountStatsPreloadManager.applyPublishedAccountRuntime(snapshotCopy, publishedAt);
                if (result.isSuccess()) {
                    v2StreamSequenceGuard.commitAppliedBusSeq(busSeq);
                    lastV2StreamMessageAt = System.currentTimeMillis();
                } else {
                    logManager.warn("v2 stream 账户运行态应用失败: " + result.getMessage());
                }
            } catch (Exception exception) {
                logManager.warn("v2 stream 账户运行态应用失败: " + exception.getMessage());
            } finally {
                mainHandler.post(() -> {
                    updateConnectionStatus();
                    floatingCoordinator.requestRefresh(false);
                });
            }
        });
        return submitted;
    }

    // 后台亮屏最小模式只保留悬浮窗所需账户运行态，避免整页统计链继续高频推进。
    private void applyAccountSnapshotLiteFromStream(@Nullable JSONObject accountSnapshot, long publishedAt) {
        if (accountSnapshot == null) {
            return;
        }
        if (accountRuntimeExecutorService == null || accountStatsPreloadManager == null) {
            return;
        }
        final String snapshotBody = accountSnapshot.toString();
        executeAccountRuntime(() -> {
            try {
                JSONObject snapshotCopy = new JSONObject(snapshotBody);
                accountStatsPreloadManager.applyPublishedAccountRuntimeLite(snapshotCopy, publishedAt);
            } catch (Exception exception) {
                logManager.warn("v2 stream 账户轻量运行态应用失败: " + exception.getMessage());
            } finally {
                mainHandler.post(() -> floatingCoordinator.requestRefresh(false));
            }
        });
    }

    // 把 stream candle JSON 直接转换为服务层统一 KlineData。
    private KlineData toKlineDataFromJson(String symbol, JSONObject candle, boolean closed) {
        return new KlineData(
                symbol,
                requireFiniteDouble(candle, "open", "v2 stream candle"),
                requireFiniteDouble(candle, "high", "v2 stream candle"),
                requireFiniteDouble(candle, "low", "v2 stream candle"),
                requireFiniteDouble(candle, "close", "v2 stream candle"),
                requireFiniteDouble(candle, "volume", "v2 stream candle"),
                requireFiniteDouble(candle, "quoteVolume", "v2 stream candle"),
                optLong(candle, "openTime", 0L),
                optLong(candle, "closeTime", 0L),
                closed
        );
    }

    private void fetchBootstrapData() {
        requestMarketTruthRepair(true);
        floatingCoordinator.requestRefresh(true);
    }

    // 把当前本地阈值配置推到网关端，保证服务端判断与设置页一致。
    private void syncAbnormalConfigAsync() {
        if (abnormalGatewayClient == null || backgroundExecutorService == null || configManager == null) {
            return;
        }
        List<SymbolConfig> configs = new ArrayList<>();
        for (String symbol : AppConstants.MONITOR_SYMBOLS) {
            configs.add(configManager.getSymbolConfig(symbol));
        }
        boolean logicAnd = configManager.isUseAndMode();
        executeBackgroundWork(() -> {
            AbnormalGatewayClient.PushResult result = abnormalGatewayClient.pushConfig(logicAnd, configs);
            if (result != null && !result.isSuccess() && result.getError() != null && !result.getError().trim().isEmpty()) {
                logManager.warn("异常配置同步失败: " + result.getError());
            }
        });
    }

    // 实时市场链独占一条串行执行器，保证 stream 消息不会被补修和配置同步阻塞。
    private boolean executeRealtimeMarket(Runnable task) {
        return executeOnExecutor(realtimeMarketExecutorService, task);
    }

    // 账户运行态高频更新单独走一条串行执行器，避免 0.5s 刷新被通用后台任务挤压。
    private boolean executeAccountRuntime(Runnable task) {
        return executeOnExecutor(accountRuntimeExecutorService, task);
    }

    // 补修、配置同步、会话恢复等后台动作统一走非实时执行器。
    private boolean executeBackgroundWork(Runnable task) {
        return executeOnExecutor(backgroundExecutorService, task);
    }

    // 统一把任务投递到指定执行器；服务销毁或线程池关闭后直接丢弃晚到回调。
    private boolean executeOnExecutor(@Nullable ExecutorService executor, Runnable task) {
        if (task == null) {
            return false;
        }
        ExecutorService currentExecutor = executor;
        if (currentExecutor == null || currentExecutor.isShutdown() || currentExecutor.isTerminated()) {
            return false;
        }
        try {
            currentExecutor.execute(task);
            return true;
        } catch (RejectedExecutionException exception) {
            if (logManager != null) {
                logManager.warn("后台任务已跳过，服务正在关闭");
            }
            return false;
        }
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
            requestMarketTruthRepair(false);
            reconcileRemoteSessionIfNeeded();
            floatingCoordinator.requestRefresh(false);
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

    // 亮灭屏时同步切换心跳与悬浮窗策略；熄屏时退到 alert-only，亮屏后按需要恢复。
    private void handleScreenInteractiveChanged(boolean interactive) {
        handleDeviceInteractiveChanged(interactive);
    }

    // 亮灭屏时同步切换心跳与悬浮窗策略；熄屏时退到 alert-only，亮屏后按需要恢复。
    private void handleDeviceInteractiveChanged(boolean interactive) {
        boolean changed = deviceInteractive != interactive;
        deviceInteractive = interactive;
        screenInteractive = interactive;
        if (floatingCoordinator != null) {
            floatingCoordinator.setScreenInteractive(interactive);
        }
        if (!changed || !pipelineStarted) {
            return;
        }
        rescheduleRuntimePolicies();
        if (!interactive) {
            return;
        }
        if (isV2StreamHealthy(System.currentTimeMillis())) {
            floatingCoordinator.requestRefresh(true);
            updateConnectionStatus();
            return;
        }
        restartV2Stream("screen_on_resume");
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
        floatingCoordinator.requestRefresh(true);
        reconcileRemoteSessionIfNeeded();
    }

    @NonNull
    private MonitorStreamRuntimeModeHelper.RuntimeMode resolveStreamRuntimeMode() {
        if (AppForegroundTracker.getInstance().isForeground()) {
            return MonitorStreamRuntimeModeHelper.RuntimeMode.FOREGROUND_FULL;
        }
        if (shouldUseScreenOffAlertOnlyMode()) {
            return MonitorStreamRuntimeModeHelper.RuntimeMode.ALERT_ONLY;
        }
        if (shouldUseMinimalFloatingMode()) {
            return MonitorStreamRuntimeModeHelper.RuntimeMode.BACKGROUND_FLOATING_MINIMAL;
        }
        return MonitorStreamRuntimeModeHelper.RuntimeMode.ALERT_ONLY;
    }

    private boolean shouldUseMinimalFloatingMode() {
        return !AppForegroundTracker.getInstance().isForeground()
                && deviceInteractive
                && configManager != null
                && configManager.isFloatingEnabled();
    }

    private boolean shouldUseScreenOffAlertOnlyMode() {
        return !deviceInteractive;
    }

    // 悬浮窗只允许看到当前活动会话对应的账户缓存，旧账号 cache 不能继续透传下去。
    @Nullable
    private AccountStatsPreloadManager.Cache resolveCurrentSessionFloatingCache() {
        if (accountStatsPreloadManager == null
                || configManager == null
                || secureSessionPrefs == null
                || !configManager.isAccountSessionActive()) {
            return null;
        }
        AccountStatsPreloadManager.Cache cache = accountStatsPreloadManager.getLatestCache();
        if (cache == null) {
            return null;
        }
        SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();
        if (sessionSummary.hasStorageFailure()) {
            return null;
        }
        RemoteAccountProfile activeAccount = sessionSummary.getActiveAccount();
        if (activeAccount == null) {
            return null;
        }
        String expectedAccount = trimToEmpty(activeAccount.getLogin());
        String expectedServer = trimToEmpty(activeAccount.getServer());
        if (expectedAccount.isEmpty() || expectedServer.isEmpty()) {
            return null;
        }
        if (!expectedAccount.equalsIgnoreCase(trimToEmpty(cache.getAccount()))
                || !expectedServer.equalsIgnoreCase(trimToEmpty(cache.getServer()))) {
            return null;
        }
        return cache;
    }

    @NonNull
    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    // 当前台仍保留本地活动会话时，只做一次轻量远程确认；发现账号失配就落未登录并通知用户。
    private void reconcileRemoteSessionIfNeeded() {
        if (configManager == null
                || accountSessionRecoveryHelper == null
                || !configManager.isAccountSessionActive()
                || !remoteSessionRecoveryInFlight.compareAndSet(false, true)) {
            return;
        }
        boolean submitted = executeBackgroundWork(() -> {
            try {
                AccountSessionRecoveryHelper.RecoveryResult recoveryResult =
                        accountSessionRecoveryHelper.reconcileRemoteSession();
                if (recoveryResult.requiresUiRefresh()) {
                    mainHandler.post(() -> {
                        updateConnectionStatus();
                        if (recoveryResult == AccountSessionRecoveryHelper.RecoveryResult.ACCOUNT_MISMATCH
                                && notificationHelper != null) {
                            notificationHelper.notifyAccountMismatch("", "");
                        }
                        if (floatingCoordinator != null) {
                            floatingCoordinator.requestRefresh(true);
                        }
                    });
                }
            } finally {
                remoteSessionRecoveryInFlight.set(false);
            }
        });
        if (!submitted) {
            remoteSessionRecoveryInFlight.set(false);
        }
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
            if (recordManager.addRecordIfAbsent(record) && floatingCoordinator != null) {
                floatingCoordinator.notifyAbnormalEvent(record.getSymbol());
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
            AbnormalRecord record = AbnormalRecord.parseOrNull(item);
            if (record == null) {
                warnMalformedPayload("异常记录解析失败", new IllegalArgumentException("invalid abnormal record"));
            } else {
                records.add(record);
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
            } catch (Exception exception) {
                warnMalformedPayload("异常提醒解析失败", exception);
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
        if (AbnormalSyncRuntimeHelper.isServerAlertAlreadyDispatched(alert.getId(), dispatchedAlertIdSnapshot)) {
            return;
        }
        List<String> normalizedSymbols = AbnormalSyncRuntimeHelper.normalizeServerAlertSymbols(alert.getSymbols());
        if (normalizedSymbols.isEmpty()) {
            return;
        }
        List<String> dispatchableSymbols = AbnormalSyncRuntimeHelper.collectDispatchableServerAlertSymbols(
                normalizedSymbols,
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        );
        for (String symbol : dispatchableSymbols) {
            notificationHelper.notifyAbnormalAlert(
                    alert.getTitle(),
                    AbnormalSyncRuntimeHelper.buildSymbolScopedAlertContent(alert.getContent(), symbol),
                    resolveAlertNotificationId(Collections.singletonList(symbol))
            );
            lastNotifyAt.put(symbol, now);
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
            foregroundNotificationCoordinator.refreshNotification(status);
            if (MonitorStreamRuntimeModeHelper.shouldRefreshFloating(resolveStreamRuntimeMode())) {
                floatingCoordinator.requestRefresh(false);
            }
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

    // 读取当前连接心跳节奏。
    private long resolveHeartbeatDelayMs() {
        return MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(
                AppForegroundTracker.getInstance().isForeground(),
                screenInteractive);
    }

    private void registerScreenStateReceiver() {
        if (screenStateReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenStateReceiver, filter);
        }
        screenStateReceiverRegistered = true;
    }

    private void unregisterScreenStateReceiver() {
        if (!screenStateReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        screenStateReceiverRegistered = false;
    }

    private boolean resolveDeviceInteractive() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return powerManager == null || powerManager.isInteractive();
    }

    private void checkStreamFreshness() {
        if (!pipelineStarted) {
            return;
        }
        long now = System.currentTimeMillis();
        if (isV2StreamHealthy(now)) {
            requestMarketTruthRepair(false);
            return;
        }
        restartV2Stream("stale_watchdog");
        updateConnectionStatus();
    }

    // 当 stream 长时间没有推进市场真值时，用正式 1m REST 窗口补修统一市场底稿。
    private void requestMarketTruthRepair(boolean force) {
        long requestedAt = System.currentTimeMillis();
        if (repository == null
                || gatewayV2Client == null
                || !force && !shouldRepairMarketTruth(requestedAt)) {
            return;
        }
        if (!marketTruthRepairInFlight.compareAndSet(false, true)) {
            return;
        }
        boolean submitted = executeBackgroundWork(() -> {
            try {
                MarketTruthSnapshot snapshot = repository.getMarketTruthSnapshotLiveData().getValue();
                for (String symbol : AppConstants.MONITOR_SYMBOLS) {
                    if (symbol == null || symbol.trim().isEmpty()) {
                        continue;
                    }
                    if (!force && !shouldRepairMarketTruthForSymbol(snapshot, symbol, requestedAt)) {
                        continue;
                    }
                    repairMarketTruthForSymbol(symbol, requestedAt);
                }
            } catch (Exception exception) {
                if (logManager != null) {
                    logManager.warn("市场真值补修失败: " + exception.getMessage());
                }
            } finally {
                lastMarketTruthRepairAt = System.currentTimeMillis();
                marketTruthRepairInFlight.set(false);
            }
        });
        if (!submitted) {
            marketTruthRepairInFlight.set(false);
        }
    }

    // 真值长时间未推进时，允许服务层用 REST 对统一市场底稿做一次正式补修。
    private boolean shouldRepairMarketTruth(long now) {
        long safeNow = Math.max(0L, now);
        if (safeNow - lastMarketTruthRepairAt < MARKET_TRUTH_REPAIR_COOLDOWN_MS) {
            return false;
        }
        MarketTruthSnapshot snapshot = repository == null
                ? null
                : repository.getMarketTruthSnapshotLiveData().getValue();
        for (String symbol : AppConstants.MONITOR_SYMBOLS) {
            if (shouldRepairMarketTruthForSymbol(snapshot, symbol, safeNow)) {
                return true;
            }
        }
        return false;
    }

    // 补修门闩只看 1m 真值真正前进的时间，不能把重复旧包的外层到达时间误判成进度。
    private long resolveMarketTruthProgressAt(@Nullable MarketTruthSnapshot snapshot) {
        if (snapshot == null) {
            return 0L;
        }
        long oldestRequiredProgressAt = Long.MAX_VALUE;
        for (String symbol : AppConstants.MONITOR_SYMBOLS) {
            if (symbol == null || symbol.trim().isEmpty()) {
                continue;
            }
            MarketTruthSymbolState state = snapshot.getSymbolState(symbol);
            long symbolUpdatedAt = state == null ? 0L : Math.max(0L, state.getLastTruthUpdateAt());
            if (symbolUpdatedAt <= 0L) {
                return 0L;
            }
            oldestRequiredProgressAt = Math.min(oldestRequiredProgressAt, symbolUpdatedAt);
        }
        return oldestRequiredProgressAt == Long.MAX_VALUE ? 0L : oldestRequiredProgressAt;
    }

    // 对单个品种判断当前是“真值长时间不前进”还是“存在允许重试的历史缺口”。
    private boolean shouldRepairMarketTruthForSymbol(@Nullable MarketTruthSnapshot snapshot,
                                                     @Nullable String symbol,
                                                     long now) {
        if (repository == null || symbol == null || symbol.trim().isEmpty()) {
            return false;
        }
        GapDetector.Gap gap = repository.selectMinuteGap(symbol);
        if (gap != null) {
            String evidenceToken = repository.buildMinuteGapEvidenceToken(symbol);
            return repository.shouldRetryMinuteGapRepair(symbol, gap, evidenceToken, now);
        }
        MarketTruthSymbolState state = snapshot == null ? null : snapshot.getSymbolState(symbol);
        long symbolUpdatedAt = state == null ? 0L : Math.max(0L, state.getLastTruthUpdateAt());
        return symbolUpdatedAt <= 0L || now - symbolUpdatedAt >= AppConstants.SOCKET_STALE_TIMEOUT_MS;
    }

    // 对单个品种执行正式补修，并把缺口状态机从“请求中”推进到“已解决/仍缺失”。
    private void repairMarketTruthForSymbol(@NonNull String symbol, long requestedAt) throws Exception {
        if (repository == null || gatewayV2Client == null) {
            return;
        }
        GapDetector.Gap gapBefore = repository.selectMinuteGap(symbol);
        String evidenceToken = gapBefore == null ? "" : repository.buildMinuteGapEvidenceToken(symbol);
        if (gapBefore != null) {
            repository.markMinuteGapRepairAttempted(symbol, gapBefore, evidenceToken, requestedAt);
        }
        try {
            repository.applyMarketSeriesPayload(
                    symbol,
                    "1m",
                    gatewayV2Client.fetchMarketSeries(symbol, "1m", MARKET_TRUTH_REPAIR_LIMIT)
            );
            settleMinuteGapRepairState(symbol, gapBefore, evidenceToken, System.currentTimeMillis());
        } catch (Exception exception) {
            if (gapBefore != null) {
                repository.markMinuteGapRepairStillMissing(symbol, gapBefore, evidenceToken, System.currentTimeMillis());
            }
            throw exception;
        }
    }

    // 只冻结“补完后仍是同一段”的缺口；若范围变化则交给新的缺口重新走状态机。
    private void settleMinuteGapRepairState(@NonNull String symbol,
                                            @Nullable GapDetector.Gap gapBefore,
                                            @Nullable String evidenceToken,
                                            long settledAt) {
        if (repository == null || gapBefore == null) {
            return;
        }
        GapDetector.Gap gapAfter = repository.selectMinuteGap(symbol);
        if (gapAfter == null) {
            repository.markMinuteGapRepairResolved(symbol, gapBefore, settledAt);
            return;
        }
        if (isSameMinuteGap(gapBefore, gapAfter)) {
            repository.markMinuteGapRepairStillMissing(symbol, gapBefore, evidenceToken, settledAt);
            return;
        }
        repository.markMinuteGapRepairResolved(symbol, gapBefore, settledAt);
    }

    // 同一缺口必须起止都一致，才能继续沿用旧状态。
    private boolean isSameMinuteGap(@NonNull GapDetector.Gap left, @NonNull GapDetector.Gap right) {
        return left.getMissingStartOpenTime() == right.getMissingStartOpenTime()
                && left.getMissingEndCloseTime() == right.getMissingEndCloseTime();
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
            } catch (NumberFormatException exception) {
                warnMalformedPayload("浮点字段解析失败: " + key, exception);
                return 0d;
            }
        }
        return 0d;
    }

    // 关键数值字段必须是有限实数，异常时直接拒绝整包，避免坏包被静默写成 0。
    private double requireFiniteDouble(JSONObject item, String key, String context) {
        if (item == null || key == null || key.trim().isEmpty() || !item.has(key)) {
            throw new IllegalStateException(context + " missing " + key);
        }
        Object value = item.opt(key);
        double parsed;
        if (value instanceof Number) {
            parsed = ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                parsed = Double.parseDouble(((String) value).trim());
            } catch (Exception exception) {
                throw new IllegalStateException(context + " invalid " + key, exception);
            }
        } else {
            throw new IllegalStateException(context + " invalid " + key);
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalStateException(context + " invalid " + key);
        }
        return parsed;
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
            } catch (NumberFormatException exception) {
                warnMalformedPayload("长整数字段解析失败: " + key, exception);
                return fallback;
            }
        }
        return fallback;
    }

    private void warnMalformedPayload(@NonNull String message, @Nullable Exception exception) {
        if (logManager == null) {
            return;
        }
        String detail = exception == null || exception.getMessage() == null
                ? ""
                : " - " + exception.getMessage();
        logManager.warn(message + detail);
    }

}

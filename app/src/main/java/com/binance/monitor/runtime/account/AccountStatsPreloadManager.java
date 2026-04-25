/*
 * 账户预加载管理器，负责消费服务端已发布的账户运行态并管理历史补拉。
 * 供账户页、图表页等依赖账户信息的界面复用。
 */
package com.binance.monitor.runtime.account;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.model.v2.AccountHistoryPayload;
import com.binance.monitor.data.model.v2.AccountFullPayload;
import com.binance.monitor.data.model.v2.AccountSnapshotPayload;
import com.binance.monitor.data.remote.v2.GatewayV2Client;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.runtime.AppForegroundTracker;
import com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStore;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.security.SecureSessionPrefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountStatsPreloadManager {
    private static volatile AccountStatsPreloadManager instance;

    private final GatewayV2Client gatewayV2Client;
    private final AccountStorageRepository accountStorageRepository;
    private final ConfigManager configManager;
    private final SecureSessionPrefs secureSessionPrefs;
    private final UnifiedRuntimeSnapshotStore unifiedRuntimeSnapshotStore;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<CacheListener> cacheListeners = new CopyOnWriteArraySet<>();
    private final AppForegroundTracker.ForegroundStateListener appForegroundListener =
            foreground -> handleForegroundStateChanged(foreground);
    private final Object lock = new Object();
    private static final long MAX_REFRESH_MS = AppConstants.ACCOUNT_REFRESH_MAX_INTERVAL_MS;

    private volatile Cache latestCache;
    private volatile boolean started;
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private volatile boolean liveScreenActive;
    private volatile boolean fullSnapshotActive;
    private volatile long nextDelayMs = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS;
    private final AccountHistoryRefreshGate accountHistoryRefreshGate = new AccountHistoryRefreshGate();
    private final AtomicBoolean overlayFetchInFlight = new AtomicBoolean(false);
    private final AtomicBoolean storageHydrationInFlight = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledFetchFuture;
    private long scheduleGeneration;
    private volatile boolean foregroundListenerRegistered;

    private AccountStatsPreloadManager(Context context) {
        gatewayV2Client = new GatewayV2Client(context.getApplicationContext());
        accountStorageRepository = new AccountStorageRepository(context.getApplicationContext());
        configManager = ConfigManager.getInstance(context.getApplicationContext());
        secureSessionPrefs = new SecureSessionPrefs(context.getApplicationContext());
        unifiedRuntimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();
    }

    public static AccountStatsPreloadManager getInstance(Context context) {
        if (instance == null) {
            synchronized (AccountStatsPreloadManager.class) {
                if (instance == null) {
                    instance = new AccountStatsPreloadManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public void start() {
        if (started) {
            return;
        }
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
        }
        registerForegroundListenerIfNeeded();
        preheatLatestCacheFromStorageIfNeeded();
        scheduleFetch(0L);
    }

    public Cache getLatestCache() {
        Cache cache = latestCache;
        if (cache != null || !isAccountSessionActive()) {
            return cache;
        }
        return null;
    }

    // 在后台线程里把本地已持久化快照回填到内存缓存，避免页面首帧同步读库。
    public Cache hydrateLatestCacheFromStorage() {
        return hydrateLatestCacheFromStorageInternal(false);
    }

    // 启动阶段只做一次异步本地预热，把可用快照尽量提前装进内存与统一运行态。
    private void preheatLatestCacheFromStorageIfNeeded() {
        if (!started || !isAccountSessionActive() || latestCache != null) {
            return;
        }
        if (!storageHydrationInFlight.compareAndSet(false, true)) {
            return;
        }
        executor.execute(() -> {
            try {
                hydrateLatestCacheFromStorageInternal(true);
            } finally {
                storageHydrationInFlight.set(false);
            }
        });
    }

    // 统一封装本地快照恢复；页面按需恢复可只写内存，启动预热则走统一派发链。
    private Cache hydrateLatestCacheFromStorageInternal(boolean notifyConsumers) {
        Cache cache = latestCache;
        if (cache != null || !isAccountSessionActive()) {
            return cache;
        }
        AccountStorageRepository.StoredSnapshot storedSnapshot = loadStoredSnapshotForWorkerThread();
        if (!hasStoredSnapshotContent(storedSnapshot)) {
            return null;
        }
        Cache hydratedCache = buildCache(storedSnapshot, storedSnapshot.getHistoryRevision());
        if (notifyConsumers) {
            updateLatestCache(hydratedCache);
        } else {
            latestCache = hydratedCache;
        }
        return hydratedCache;
    }

    // 注册缓存更新监听，页面可在首屏时收到最新快照并立即刷新。
    public void addCacheListener(CacheListener listener) {
        if (listener == null) {
            return;
        }
        cacheListeners.add(listener);
        Cache cache = latestCache;
        if (cache != null) {
            mainHandler.post(() -> listener.onCacheUpdated(cache));
        }
    }

    // 移除缓存更新监听，避免页面离开后继续接收刷新。
    public void removeCacheListener(CacheListener listener) {
        if (listener == null) {
            return;
        }
        cacheListeners.remove(listener);
    }

    // 标记账户统计页是否正在前台显示，显示时把后台预加载主动放慢。
    public void setLiveScreenActive(boolean active) {
        boolean changed = liveScreenActive != active;
        liveScreenActive = active;
        collapseFullSnapshotWhenBackgroundPassive();
        if (!changed || !started || !isAccountSessionActive()) {
            return;
        }
        if (active) {
            nextDelayMs = MAX_REFRESH_MS;
            scheduleFetch(nextDelayMs);
            return;
        }
        nextDelayMs = resolveRefreshDelayMs();
        scheduleFetch(0L);
    }

    // 标记当前是否需要全量快照，登录后和图表依赖全量数据时会临时提速。
    public void setFullSnapshotActive(boolean active) {
        boolean changed = fullSnapshotActive != active;
        fullSnapshotActive = active;
        if (!changed || !started || !isAccountSessionActive()) {
            return;
        }
        nextDelayMs = resolveRefreshDelayMs();
        if (active) {
            scheduleFetch(0L);
            return;
        }
        if (!liveScreenActive) {
            scheduleFetch(nextDelayMs);
        }
    }

    // 清空内存中的最新预加载缓存，供登录态切换和配置变化时复位页面。
    public void clearLatestCache() {
        updateLatestCache(null);
        nextDelayMs = resolveRefreshDelayMs();
    }

    // 统一准备一次强一致全量刷新，避免外部调用方直接碰缓存和节奏开关。
    public void prepareFullSnapshotRefresh() {
        clearLatestCache();
        setFullSnapshotActive(true);
    }

    // 统一清空账户运行态内存与指定身份的持久化分区，避免服务层自己再维护一套删除链路。
    public void clearAccountRuntimeState(String account, String server) {
        fullSnapshotActive = false;
        updateLatestCache(null);
        clearStoredSnapshotForIdentity(account, server);
        nextDelayMs = resolveRefreshDelayMs();
    }

    // 只注册一次应用前后台监听，避免重复回调同一套调度逻辑。
    private void registerForegroundListenerIfNeeded() {
        if (foregroundListenerRegistered) {
            return;
        }
        synchronized (lock) {
            if (foregroundListenerRegistered) {
                return;
            }
            AppForegroundTracker.getInstance().addListener(appForegroundListener);
            foregroundListenerRegistered = true;
        }
    }

    // 前后台切换后按最新策略重排下一次预加载，只切节奏，不因回前台立刻补拉。
    private void handleForegroundStateChanged(boolean foreground) {
        collapseFullSnapshotWhenBackgroundPassive();
        nextDelayMs = resolveRefreshDelayMs();
        if (foreground) {
            gatewayV2Client.resetTransport();
        }
        if (!started || !isAccountSessionActive() || liveScreenActive) {
            return;
        }
        scheduleFetch(nextDelayMs);
    }

    // 统一读取当前预加载节奏，避免不同入口写出不一致的时间。
    private long resolveRefreshDelayMs() {
        return AccountPreloadPolicyHelper.resolveRefreshDelayMs(
                AppForegroundTracker.getInstance().isForeground(),
                fullSnapshotActive
        );
    }

    private void collapseFullSnapshotWhenBackgroundPassive() {
        if (!AppForegroundTracker.getInstance().isForeground() && !liveScreenActive) {
            fullSnapshotActive = false;
        }
    }

    // 统一判断账户会话是否有效，避免各处重复判空和读配置。
    private boolean isAccountSessionActive() {
        return configManager == null || configManager.isAccountSessionActive();
    }

    // 重新安排下一次拉取，进入全量页面时可立即打断休眠并刷新。
    private void scheduleFetch(long delayMs) {
        synchronized (lock) {
            if (!started) {
                return;
            }
            scheduleGeneration++;
            long generation = scheduleGeneration;
            cancelScheduledFetchLocked();
            scheduledFetchFuture = executor.schedule(
                    () -> runScheduledFetch(generation),
                    Math.max(0L, delayMs),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    // 执行一次拉取，并在当前调度代次仍有效时安排下一次刷新。
    private void runScheduledFetch(long generation) {
        fetchOnce();
        synchronized (lock) {
            if (!started || generation != scheduleGeneration) {
                return;
            }
            scheduledFetchFuture = executor.schedule(
                    () -> runScheduledFetch(generation),
                    Math.max(0L, nextDelayMs),
                    TimeUnit.MILLISECONDS
            );
        }
    }

    // 取消尚未开始执行的定时任务，避免休眠中的旧任务阻塞紧急刷新。
    private void cancelScheduledFetchLocked() {
        if (scheduledFetchFuture == null) {
            return;
        }
        scheduledFetchFuture.cancel(false);
        scheduledFetchFuture = null;
    }

    // 刷新一次预加载节奏；账户真值由 stream 直接写入，本方法不再主动拉 snapshot。
    private void fetchOnce() {
        if (!loading.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!isAccountSessionActive()) {
                clearStoredSnapshotForResolvedIdentity();
                clearLatestCache();
                nextDelayMs = MAX_REFRESH_MS;
                return;
            }
            nextDelayMs = resolveRefreshDelayMs();
        } catch (Exception exception) {
            nextDelayMs = resolveRefreshDelayMs();
            Cache previous = latestCache;
            if (previous != null) {
                updateLatestCache(buildFailureCache(previous, exception.getMessage()));
            }
        } finally {
            loading.set(false);
        }
    }

    // 应用服务端发布的账户运行态快照，统一更新本地运行态与页面缓存。
    public ApplyResult applyPublishedAccountRuntime(JSONObject accountRuntimeSnapshot, long publishedAt) {
        if (accountRuntimeSnapshot == null) {
            Cache cache = latestCache;
            return ApplyResult.failure("accountRuntimeSnapshot 为空", cache);
        }
        try {
            if (!isAccountSessionActive()) {
                clearStoredSnapshotForResolvedIdentity();
                clearLatestCache();
                nextDelayMs = MAX_REFRESH_MS;
                return ApplyResult.failure("远程账户会话未激活", null);
            }
            AccountStorageRepository.StoredSnapshot runtimeSnapshot =
                    buildStoredSnapshotFromPublishedRuntime(accountRuntimeSnapshot, publishedAt);
            accountStorageRepository.persistIncrementalSnapshot(runtimeSnapshot);
            AccountStorageRepository.StoredSnapshot previousSnapshot = loadStoredSnapshotForWorkerThread();
            AccountStorageRepository.StoredSnapshot mergedSnapshot =
                    mergePublishedRuntimeWithStoredHistory(runtimeSnapshot, previousSnapshot);
            String resolvedRevision = resolveHistoryRevisionFromPublishedRuntime(accountRuntimeSnapshot);
            Cache cache = buildCache(
                    mergedSnapshot,
                    resolvedRevision,
                    resolveAccountMode(accountRuntimeSnapshot.optJSONObject("accountMeta"))
            );
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(cache);
            return ApplyResult.success(cache);
        } catch (Exception exception) {
            nextDelayMs = resolveRefreshDelayMs();
            Cache previous = latestCache;
            if (previous != null) {
                Cache cache = buildFailureCache(previous, exception.getMessage());
                updateLatestCache(cache);
                return ApplyResult.failure(exception.getMessage(), cache);
            }
            Cache cache = buildInitialFailureCache(exception.getMessage());
            updateLatestCache(cache);
            return ApplyResult.failure(exception.getMessage(), cache);
        }
    }

    // 后台仅悬浮窗场景只保留最小账户字段，避免每次账户推送都重建整页分析依赖。
    public Cache applyPublishedAccountRuntimeLite(JSONObject accountRuntimeSnapshot, long publishedAt) {
        if (accountRuntimeSnapshot == null) {
            return latestCache;
        }
        try {
            if (!isAccountSessionActive()) {
                clearStoredSnapshotForResolvedIdentity();
                clearLatestCache();
                nextDelayMs = MAX_REFRESH_MS;
                return null;
            }
            Cache cache = buildLiteCacheFromPublishedRuntime(
                    accountRuntimeSnapshot,
                    publishedAt,
                    resolveAccountMode(accountRuntimeSnapshot.optJSONObject("accountMeta"))
            );
            nextDelayMs = resolveRefreshDelayMs();
            replaceLatestCache(cache, false);
            return cache;
        } catch (Exception exception) {
            nextDelayMs = resolveRefreshDelayMs();
            return latestCache;
        }
    }

    // 仅当 history revision 前进或本地还没有历史时，才补拉一次全量历史。
    public Cache refreshHistoryForRevision(String remoteHistoryRevision) {
        if (!overlayFetchInFlight.compareAndSet(false, true)) {
            return latestCache;
        }
        try {
            if (!isAccountSessionActive()) {
                clearStoredSnapshotForResolvedIdentity();
                clearLatestCache();
                nextDelayMs = MAX_REFRESH_MS;
                return null;
            }
            Cache previous = latestCache;
            AccountStorageRepository.StoredSnapshot storedSnapshot =
                    loadStoredSnapshotForWorkerThread();
            String cachedHistoryRevision = previous == null
                    ? storedSnapshot.getHistoryRevision()
                    : previous.getHistoryRevision();
            int storedTradeCount = loadStoredTradeCountForWorkerThread();
            boolean hasStoredTradeHistory = storedTradeCount > 0;
            boolean shouldRefreshAllHistory = AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(
                    remoteHistoryRevision,
                    cachedHistoryRevision,
                    hasStoredTradeHistory
            );
            if (!shouldRefreshAllHistory) {
                if (previous != null) {
                    return previous;
                }
                Cache cache = buildCache(storedSnapshot, cachedHistoryRevision);
                updateLatestCache(cache);
                return cache;
            }
            AccountHistoryPayload historyPayload = fetchCompleteHistoryPayload(AccountTimeRange.ALL);
            AccountStorageRepository.StoredSnapshot mergedSnapshot =
                    buildStoredSnapshotFromHistoryOnly(storedSnapshot, historyPayload, remoteHistoryRevision);
            accountStorageRepository.persistV2Snapshot(mergedSnapshot);
            String resolvedRevision = requireHistoryRevision(remoteHistoryRevision);
            Cache cache = buildCache(mergedSnapshot, resolvedRevision);
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(cache);
            return cache;
        } catch (Exception exception) {
            nextDelayMs = resolveRefreshDelayMs();
            Cache previous = latestCache;
            if (previous != null) {
                Cache cache = buildFailureCache(previous, exception.getMessage());
                updateLatestCache(cache);
                return cache;
            }
            Cache cache = buildInitialFailureCache(exception.getMessage());
            updateLatestCache(cache);
            return cache;
        } finally {
            overlayFetchInFlight.set(false);
        }
    }

    // 历史补拉的并发 gate 与续跑节奏统一收口到账户域，服务层只再负责把 revision 交进来。
    public void queueHistoryRefreshForRevision(String remoteHistoryRevision, Runnable afterRefresh) {
        String safeHistoryRevision = remoteHistoryRevision == null ? "" : remoteHistoryRevision.trim();
        if (safeHistoryRevision.isEmpty()) {
            return;
        }
        AccountHistoryRefreshGate.StartDecision startDecision = accountHistoryRefreshGate.tryStart(safeHistoryRevision);
        if (!startDecision.shouldStart()) {
            return;
        }
        executor.execute(() -> runQueuedHistoryRefresh(startDecision.getRevision(), afterRefresh));
    }

    // 显式 UI 刷新必须一次取回当前运行态和历史主体，避免客户端继续双接口拼装。
    public Cache fetchFullForUi(AccountTimeRange range) {
        if (!isAccountSessionActive()) {
            clearStoredSnapshotForResolvedIdentity();
            clearLatestCache();
            nextDelayMs = MAX_REFRESH_MS;
            return null;
        }
        try {
            AccountFullPayload fullPayload = gatewayV2Client.fetchAccountFull();
            AccountStorageRepository.StoredSnapshot mergedSnapshot =
                    buildStoredSnapshotFromFullPayload(fullPayload);
            accountStorageRepository.persistV2Snapshot(mergedSnapshot);
            String resolvedRevision = resolveHistoryRevisionFromFullPayload(fullPayload);
            Cache cache = buildCache(
                    mergedSnapshot,
                    resolvedRevision,
                    resolveAccountMode(fullPayload == null ? null : fullPayload.getAccountMeta())
            );
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(cache);
            return cache;
        } catch (Exception exception) {
            return buildAndApplyFailureCache(exception.getMessage());
        }
    }

    // 远程会话恢复后，统一按目标账号校验全量快照，避免服务层自己再维护一套身份比对分支。
    public Cache fetchFullForUiForIdentity(AccountTimeRange range, String expectedAccount, String expectedServer) {
        return requireExpectedIdentityCache(fetchFullForUi(range), expectedAccount, expectedServer);
    }

    // 登录确认阶段只拉当前运行态，避免把 full 全量补全再次绑成登录闸门。
    public Cache fetchSnapshotForUi() {
        if (!isAccountSessionActive()) {
            clearStoredSnapshotForResolvedIdentity();
            clearLatestCache();
            nextDelayMs = MAX_REFRESH_MS;
            return null;
        }
        try {
            AccountSnapshotPayload snapshotPayload = gatewayV2Client.fetchAccountSnapshot();
            AccountStorageRepository.StoredSnapshot runtimeSnapshot =
                    buildStoredSnapshotFromSnapshotPayload(snapshotPayload);
            AccountStorageRepository.StoredSnapshot mergedSnapshot =
                    mergePublishedRuntimeWithStoredHistory(runtimeSnapshot, loadStoredSnapshotForWorkerThread());
            accountStorageRepository.persistIncrementalSnapshot(mergedSnapshot);
            String resolvedRevision = resolveHistoryRevisionFromSnapshotPayload(snapshotPayload);
            Cache cache = buildCache(
                    mergedSnapshot,
                    resolvedRevision,
                    resolveAccountMode(snapshotPayload == null ? null : snapshotPayload.getAccountMeta())
            );
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(cache);
            return cache;
        } catch (Exception exception) {
            return buildAndApplyFailureCache(exception.getMessage());
        }
    }

    // 轻量确认阶段也必须按目标身份校验，避免旧账号运行态误收口新账号。
    public Cache fetchSnapshotForUiForIdentity(String expectedAccount, String expectedServer) {
        return requireExpectedIdentityCache(fetchSnapshotForUi(), expectedAccount, expectedServer);
    }

    // 单次 history 补拉完成后，若期间又来了新 revision，则继续在同一后台链路上补最新一版。
    private void runQueuedHistoryRefresh(String historyRevision, Runnable afterRefresh) {
        try {
            refreshHistoryForRevision(historyRevision);
        } finally {
            dispatchAfterHistoryRefresh(afterRefresh);
            AccountHistoryRefreshGate.FinishDecision finishDecision = accountHistoryRefreshGate.finish(historyRevision);
            if (finishDecision.shouldContinue()) {
                runQueuedHistoryRefresh(finishDecision.getNextRevision(), afterRefresh);
            }
        }
    }

    // 历史补拉后的 UI 衍生刷新统一走主线程，避免回调方再自己切线程。
    private void dispatchAfterHistoryRefresh(Runnable afterRefresh) {
        if (afterRefresh == null) {
            return;
        }
        mainHandler.post(afterRefresh);
    }

    // 更新最新缓存并把刷新结果派发给前台页面。
    private void updateLatestCache(Cache cache) {
        replaceLatestCache(cache, true);
    }

    private void replaceLatestCache(Cache cache, boolean notifyConsumers) {
        latestCache = cache;
        if (cache == null) {
            unifiedRuntimeSnapshotStore.clearAccountRuntime();
        } else {
            unifiedRuntimeSnapshotStore.applyAccountCache(cache);
        }
        if (notifyConsumers) {
            notifyCacheListeners(cache);
        }
    }

    // 在主线程通知页面刷新，避免监听方自己再切线程。
    private void notifyCacheListeners(Cache cache) {
        if (cacheListeners.isEmpty()) {
            return;
        }
        mainHandler.post(() -> {
            for (CacheListener listener : cacheListeners) {
                listener.onCacheUpdated(cache);
            }
        });
    }

    // stream 运行态只刷新“当前状态”，历史成交与曲线真值应继续沿用已持久化的完整历史快照。
    private AccountStorageRepository.StoredSnapshot mergePublishedRuntimeWithStoredHistory(
            AccountStorageRepository.StoredSnapshot runtimeSnapshot,
            AccountStorageRepository.StoredSnapshot storedSnapshot) {
        if (runtimeSnapshot == null) {
            return storedSnapshot;
        }
        if (storedSnapshot == null || !matchesSnapshotIdentity(runtimeSnapshot, storedSnapshot)) {
            return runtimeSnapshot;
        }
        return new AccountStorageRepository.StoredSnapshot(
                runtimeSnapshot.isConnected(),
                runtimeSnapshot.getAccount(),
                runtimeSnapshot.getServer(),
                runtimeSnapshot.getSource(),
                runtimeSnapshot.getGateway(),
                runtimeSnapshot.getUpdatedAt() > 0L ? runtimeSnapshot.getUpdatedAt() : storedSnapshot.getUpdatedAt(),
                runtimeSnapshot.getError(),
                runtimeSnapshot.getFetchedAt() > 0L ? runtimeSnapshot.getFetchedAt() : storedSnapshot.getFetchedAt(),
                runtimeSnapshot.getHistoryRevision().trim().isEmpty()
                        ? storedSnapshot.getHistoryRevision()
                        : runtimeSnapshot.getHistoryRevision(),
                runtimeSnapshot.getOverviewMetrics().isEmpty()
                        ? storedSnapshot.getOverviewMetrics()
                        : runtimeSnapshot.getOverviewMetrics(),
                runtimeSnapshot.getCurvePoints().isEmpty()
                        ? storedSnapshot.getCurvePoints()
                        : runtimeSnapshot.getCurvePoints(),
                runtimeSnapshot.getCurveIndicators().isEmpty()
                        ? storedSnapshot.getCurveIndicators()
                        : runtimeSnapshot.getCurveIndicators(),
                runtimeSnapshot.getPositions(),
                runtimeSnapshot.getPendingOrders(),
                runtimeSnapshot.getTrades().isEmpty()
                        ? storedSnapshot.getTrades()
                        : runtimeSnapshot.getTrades(),
                runtimeSnapshot.getStatsMetrics().isEmpty()
                        ? storedSnapshot.getStatsMetrics()
                        : runtimeSnapshot.getStatsMetrics()
        );
    }

    // 合并前必须先确认两份快照属于同一账户，避免旧账号历史误并到新账号运行态。
    private boolean matchesSnapshotIdentity(AccountStorageRepository.StoredSnapshot left,
                                            AccountStorageRepository.StoredSnapshot right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getAccount().trim().equalsIgnoreCase(right.getAccount().trim())
                && left.getServer().trim().equalsIgnoreCase(right.getServer().trim());
    }

    // 将 stream 下发的账户运行态转换为本地统一运行态结构，不再通过 HTTP snapshot 再拼一次。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromPublishedRuntime(JSONObject runtimeSnapshot,
                                                                                           long publishedAt) {
        JSONObject runtimeMeta = runtimeSnapshot == null ? new JSONObject() : runtimeSnapshot.optJSONObject("accountMeta");
        JSONArray positions = runtimeSnapshot == null ? new JSONArray() : runtimeSnapshot.optJSONArray("positions");
        JSONArray orders = runtimeSnapshot == null ? new JSONArray() : runtimeSnapshot.optJSONArray("orders");
        JSONArray overviewMetrics = runtimeSnapshot == null ? null : runtimeSnapshot.optJSONArray("overviewMetrics");
        JSONArray curveIndicators = runtimeSnapshot == null ? null : runtimeSnapshot.optJSONArray("curveIndicators");
        JSONArray statsMetrics = runtimeSnapshot == null ? null : runtimeSnapshot.optJSONArray("statsMetrics");
        long updatedAt = publishedAt > 0L ? publishedAt : System.currentTimeMillis();
        return new AccountStorageRepository.StoredSnapshot(
                resolveRuntimeConnected(runtimeMeta),
                optString(runtimeMeta, "login", ""),
                optString(runtimeMeta, "server", ""),
                resolveV2Source(runtimeMeta),
                resolveGatewayEndpoint(),
                updatedAt,
                "",
                System.currentTimeMillis(),
                resolveHistoryRevisionFromPublishedRuntime(runtimeSnapshot),
                parseMetrics(overviewMetrics),
                new ArrayList<>(),
                parseMetrics(curveIndicators),
                parsePositionItems(positions, false),
                parsePositionItems(orders, true),
                new ArrayList<>(),
                parseMetrics(statsMetrics)
        );
    }

    private Cache buildLiteCacheFromPublishedRuntime(JSONObject runtimeSnapshot,
                                                     long publishedAt,
                                                     String accountMode) {
        JSONObject runtimeMeta = runtimeSnapshot == null ? new JSONObject() : runtimeSnapshot.optJSONObject("accountMeta");
        JSONArray positions = runtimeSnapshot == null ? new JSONArray() : runtimeSnapshot.optJSONArray("positions");
        JSONArray orders = runtimeSnapshot == null ? new JSONArray() : runtimeSnapshot.optJSONArray("orders");
        long updatedAt = resolveUpdatedAt(runtimeMeta, publishedAt);
        AccountSnapshot snapshot = new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                parsePositionItems(positions, false),
                parsePositionItems(orders, true),
                new ArrayList<>(),
                new ArrayList<>()
        );
        return new Cache(
                resolveRuntimeConnected(runtimeMeta),
                snapshot,
                optString(runtimeMeta, "login", ""),
                optString(runtimeMeta, "server", ""),
                resolveV2Source(runtimeMeta),
                resolveGatewayEndpoint(),
                updatedAt,
                "",
                System.currentTimeMillis(),
                resolveHistoryRevisionFromPublishedRuntime(runtimeSnapshot),
                accountMode
        );
    }

    // 将 snapshot 轻量载荷转换为当前运行态结构，供登录确认和页面前台轻刷共用。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromSnapshotPayload(AccountSnapshotPayload snapshotPayload) {
        JSONObject accountMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        JSONArray positions = snapshotPayload == null ? new JSONArray() : snapshotPayload.getPositions();
        JSONArray orders = snapshotPayload == null ? new JSONArray() : snapshotPayload.getOrders();
        long updatedAt = snapshotPayload == null || snapshotPayload.getServerTime() <= 0L
                ? System.currentTimeMillis()
                : snapshotPayload.getServerTime();
        return new AccountStorageRepository.StoredSnapshot(
                resolveRuntimeConnected(accountMeta),
                optString(accountMeta, "login", ""),
                optString(accountMeta, "server", ""),
                resolveV2Source(accountMeta),
                resolveGatewayEndpoint(),
                updatedAt,
                "",
                System.currentTimeMillis(),
                resolveHistoryRevisionFromSnapshotPayload(snapshotPayload),
                parseMetrics(snapshotPayload == null ? null : snapshotPayload.getOverviewMetrics()),
                new ArrayList<>(),
                parseMetrics(snapshotPayload == null ? null : snapshotPayload.getCurveIndicators()),
                parsePositionItems(positions, false),
                parsePositionItems(orders, true),
                new ArrayList<>(),
                parseMetrics(snapshotPayload == null ? null : snapshotPayload.getStatsMetrics())
        );
    }

    // 将单次完整快照转换成本地完整账户真值，避免客户端继续双接口拼装。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromFullPayload(AccountFullPayload fullPayload) {
        JSONObject accountMeta = fullPayload == null ? new JSONObject() : fullPayload.getAccountMeta();
        JSONArray positions = fullPayload == null ? new JSONArray() : fullPayload.getPositions();
        JSONArray orders = fullPayload == null ? new JSONArray() : fullPayload.getOrders();
        JSONArray trades = fullPayload == null ? new JSONArray() : fullPayload.getTrades();
        JSONArray curvePoints = fullPayload == null ? new JSONArray() : fullPayload.getCurvePoints();
        return new AccountStorageRepository.StoredSnapshot(
                resolveRuntimeConnected(accountMeta),
                optString(accountMeta, "login", ""),
                optString(accountMeta, "server", ""),
                resolveV2Source(accountMeta),
                resolveGatewayEndpoint(),
                resolveUpdatedAt(fullPayload),
                "",
                System.currentTimeMillis(),
                resolveHistoryRevisionFromFullPayload(fullPayload),
                parseMetrics(fullPayload == null ? null : fullPayload.getOverviewMetrics()),
                parseCurvePoints(curvePoints),
                parseMetrics(fullPayload == null ? null : fullPayload.getCurveIndicators()),
                parsePositionItems(positions, false),
                parsePositionItems(orders, true),
                parseTradeItems(trades),
                parseMetrics(fullPayload == null ? null : fullPayload.getStatsMetrics())
        );
    }

    // 运行态连接真值只认完整远程账号身份；明确 logged_out 时不能继续伪装成已连接。
    private static boolean resolveRuntimeConnected(JSONObject runtimeMeta) {
        String source = runtimeMeta == null ? "" : runtimeMeta.optString("source", "").trim();
        if ("remote_logged_out".equals(source)) {
            return false;
        }
        String login = runtimeMeta == null ? "" : runtimeMeta.optString("login", "").trim();
        String server = runtimeMeta == null ? "" : runtimeMeta.optString("server", "").trim();
        return !login.isEmpty() && !server.isEmpty();
    }

    // 将 history 结果合并进当前运行态，只替换历史、曲线与历史侧指标。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromHistoryOnly(AccountStorageRepository.StoredSnapshot runtimeSnapshot,
                                                                                       AccountHistoryPayload historyPayload,
                                                                                       String historyRevision) {
        AccountStorageRepository.StoredSnapshot baseSnapshot = runtimeSnapshot == null
                ? loadStoredSnapshotForWorkerThread()
                : runtimeSnapshot;
        JSONObject historyMeta = historyPayload == null ? new JSONObject() : historyPayload.getAccountMeta();
        String account = resolveIdentityField(baseSnapshot == null ? "" : baseSnapshot.getAccount(), historyMeta, "login");
        String server = resolveIdentityField(baseSnapshot == null ? "" : baseSnapshot.getServer(), historyMeta, "server");
        String source = baseSnapshot == null || baseSnapshot.getSource().trim().isEmpty()
                ? resolveV2Source(historyMeta)
                : baseSnapshot.getSource();
        String gateway = baseSnapshot == null || baseSnapshot.getGateway().trim().isEmpty()
                ? resolveGatewayEndpoint()
                : baseSnapshot.getGateway();
        long updatedAt = historyPayload == null ? 0L : historyPayload.getServerTime();
        if (updatedAt <= 0L && baseSnapshot != null) {
            updatedAt = baseSnapshot.getUpdatedAt();
        }
        return new AccountStorageRepository.StoredSnapshot(
                baseSnapshot != null && baseSnapshot.isConnected(),
                account,
                server,
                source,
                gateway,
                updatedAt,
                "",
                System.currentTimeMillis(),
                requireHistoryRevision(historyRevision),
                parseMetrics(historyPayload == null ? null : historyPayload.getOverviewMetrics()),
                parseCurvePoints(historyPayload == null ? null : historyPayload.getCurvePoints()),
                parseMetrics(historyPayload == null ? null : historyPayload.getCurveIndicators()),
                baseSnapshot == null ? new ArrayList<>() : baseSnapshot.getPositions(),
                baseSnapshot == null ? new ArrayList<>() : baseSnapshot.getPendingOrders(),
                parseTradeItems(historyPayload == null ? null : historyPayload.getTrades()),
                parseMetrics(historyPayload == null ? null : historyPayload.getStatsMetrics())
        );
    }

    // 历史接口返回游标时必须顺着拉满，不能只消费第一页。
    private AccountHistoryPayload fetchCompleteHistoryPayload(AccountTimeRange range) throws Exception {
        AccountHistoryPayload firstPage = gatewayV2Client.fetchAccountHistory(range, "");
        List<AccountHistoryPayload> pages = new ArrayList<>();
        pages.add(firstPage);
        String nextCursor = firstPage.getNextCursor();
        while (!nextCursor.trim().isEmpty()) {
            AccountHistoryPayload nextPage = gatewayV2Client.fetchAccountHistory(range, nextCursor);
            pages.add(nextPage);
            nextCursor = nextPage.getNextCursor();
        }
        return mergeHistoryPages(pages);
    }

    // history 分页中只有列表字段需要跨页拼接，单值字段固定以第一页为准。
    private static AccountHistoryPayload mergeHistoryPages(List<AccountHistoryPayload> pages) {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("history pages are required");
        }
        AccountHistoryPayload firstPage = pages.get(0);
        JSONArray trades = new JSONArray();
        JSONArray orders = new JSONArray();
        JSONArray curvePoints = new JSONArray();
        for (AccountHistoryPayload page : pages) {
            if (page == null) {
                continue;
            }
            appendJsonArray(trades, page.getTrades());
            appendJsonArray(orders, page.getOrders());
            appendJsonArray(curvePoints, page.getCurvePoints());
        }
        return new AccountHistoryPayload(
                firstPage.getServerTime(),
                firstPage.getSyncToken(),
                copyJsonObject(firstPage.getAccountMeta()),
                copyJsonArray(firstPage.getOverviewMetrics()),
                copyJsonArray(firstPage.getCurveIndicators()),
                copyJsonArray(firstPage.getStatsMetrics()),
                trades,
                orders,
                curvePoints,
                "",
                firstPage.getRawJson()
        );
    }

    // 统一把存储快照转成页面缓存，避免同一份字段在多处重复拼装。
    private Cache buildCache(AccountStorageRepository.StoredSnapshot storedSnapshot, String historyRevision) {
        return buildCache(
                storedSnapshot,
                historyRevision,
                latestCache == null ? "" : latestCache.getAccountMode()
        );
    }

    // 统一把存储快照转成页面缓存，accountMode 优先认本轮服务端显式返回的真值。
    private Cache buildCache(AccountStorageRepository.StoredSnapshot storedSnapshot,
                             String historyRevision,
                             String accountMode) {
        String resolvedHistoryRevision = historyRevision == null || historyRevision.trim().isEmpty()
                ? storedSnapshot.getHistoryRevision()
                : historyRevision.trim();
        AccountSnapshot snapshot = new AccountSnapshot(
                storedSnapshot.getOverviewMetrics(),
                storedSnapshot.getCurvePoints(),
                storedSnapshot.getCurveIndicators(),
                storedSnapshot.getPositions(),
                storedSnapshot.getPendingOrders(),
                storedSnapshot.getTrades(),
                storedSnapshot.getStatsMetrics()
        );
        return new Cache(
                storedSnapshot.isConnected(),
                snapshot,
                storedSnapshot.getAccount(),
                storedSnapshot.getServer(),
                storedSnapshot.getSource(),
                storedSnapshot.getGateway(),
                storedSnapshot.getUpdatedAt(),
                "",
                System.currentTimeMillis(),
                resolvedHistoryRevision,
                accountMode
        );
    }

    // 同步读库只允许在后台线程执行，调用点若退回主线程必须立刻暴露。
    private void assertWorkerThreadForStorageAccess(String operation) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("AccountStatsPreloadManager synchronous storage access must stay off main thread: " + operation);
        }
    }

    // 统一收口同步快照读取，避免调用点绕过线程边界检查。
    private AccountStorageRepository.StoredSnapshot loadStoredSnapshotForWorkerThread() {
        assertWorkerThreadForStorageAccess("loadStoredSnapshot");
        String[] identity = resolveStorageIdentity();
        if (identity == null) {
            return accountStorageRepository.loadStoredSnapshot();
        }
        return accountStorageRepository.loadStoredSnapshot(identity[0], identity[1]);
    }

    // 统一收口同步成交历史读取，避免调用点绕过线程边界检查。
    private int loadStoredTradeCountForWorkerThread() {
        assertWorkerThreadForStorageAccess("loadTrades");
        String[] identity = resolveStorageIdentity();
        if (identity == null) {
            return accountStorageRepository.loadTrades().size();
        }
        return accountStorageRepository.loadTrades(identity[0], identity[1]).size();
    }

    // 按当前最新内存或安全会话摘要解析本地持久层应访问的账户身份。
    private String[] resolveStorageIdentity() {
        Cache cache = latestCache;
        if (cache != null && !cache.getAccount().trim().isEmpty() && !cache.getServer().trim().isEmpty()) {
            return new String[]{cache.getAccount().trim(), cache.getServer().trim()};
        }
        if (secureSessionPrefs == null) {
            return null;
        }
        RemoteAccountProfile activeAccount = secureSessionPrefs.getActiveAccount();
        if (activeAccount == null) {
            return null;
        }
        String account = activeAccount.getLogin() == null ? "" : activeAccount.getLogin().trim();
        String server = activeAccount.getServer() == null ? "" : activeAccount.getServer().trim();
        if (account.isEmpty() || server.isEmpty()) {
            return null;
        }
        return new String[]{account, server};
    }

    // 只清理当前会话对应的持久化分区，避免切号或登出时顺带抹掉其他账户缓存。
    private void clearStoredSnapshotForResolvedIdentity() {
        String[] identity = resolveStorageIdentity();
        if (identity == null) {
            return;
        }
        clearStoredSnapshotForIdentity(identity[0], identity[1]);
    }

    // 统一按指定身份清理持久化快照；调用方需要先确保传入的是明确身份。
    private void clearStoredSnapshotForIdentity(String account, String server) {
        if (account == null || server == null || account.trim().isEmpty() || server.trim().isEmpty()) {
            return;
        }
        accountStorageRepository.clearRuntimeSnapshot(account.trim(), server.trim());
        accountStorageRepository.clearTradeHistory(account.trim(), server.trim());
    }

    // 冷启动时如果内存缓存还没被 stream 填充，就先从本地已保存快照恢复页面。
    private boolean hasStoredSnapshotContent(AccountStorageRepository.StoredSnapshot storedSnapshot) {
        if (storedSnapshot == null) {
            return false;
        }
        if (storedSnapshot.getUpdatedAt() > 0L || storedSnapshot.getFetchedAt() > 0L) {
            return true;
        }
        if (!storedSnapshot.getOverviewMetrics().isEmpty()
                || !storedSnapshot.getCurveIndicators().isEmpty()
                || !storedSnapshot.getCurvePoints().isEmpty()
                || !storedSnapshot.getPositions().isEmpty()
                || !storedSnapshot.getPendingOrders().isEmpty()
                || !storedSnapshot.getTrades().isEmpty()
                || !storedSnapshot.getStatsMetrics().isEmpty()) {
            return true;
        }
        return !(storedSnapshot.getAccount().trim().isEmpty()
                && storedSnapshot.getServer().trim().isEmpty()
                && storedSnapshot.getSource().trim().isEmpty()
                && storedSnapshot.getGateway().trim().isEmpty()
                && storedSnapshot.getError().trim().isEmpty());
    }

    // 统一构建失败缓存，失败时不再把旧快照继续伪装成当前真值。
    private Cache buildFailureCache(Cache previous, String errorMessage) {
        return new Cache(
                false,
                new AccountSnapshot(
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>(),
                        new ArrayList<>()
                ),
                previous.account,
                previous.server,
                previous.source,
                previous.gateway,
                previous.updatedAt,
                errorMessage,
                System.currentTimeMillis(),
                previous.historyRevision,
                previous.accountMode
        );
    }

    // 首次请求失败时返回统一错误缓存，避免页面继续走本地旧网关数据。
    private Cache buildInitialFailureCache(String errorMessage) {
        return new Cache(
                false,
                null,
                "",
                "",
                "V2网关",
                resolveGatewayEndpoint(),
                0L,
                errorMessage,
                System.currentTimeMillis()
        );
    }

    // 账户模式真值只认服务端快照里的正式字段，不在客户端本地推断。
    private static String resolveAccountMode(JSONObject accountMeta) {
        return accountMeta == null ? "" : accountMeta.optString("accountMode", "").trim().toLowerCase(Locale.ROOT);
    }

    // 恢复链只接受已连通且身份一致的全量快照，缺失时直接暴露给上游统一处理。
    private static Cache requireExpectedIdentityCache(Cache cache, String expectedAccount, String expectedServer) {
        if (cache == null || !cache.isConnected()) {
            throw new IllegalStateException("v2 account full recovered cache missing connected account");
        }
        String safeExpectedAccount = expectedAccount == null ? "" : expectedAccount.trim();
        String safeExpectedServer = expectedServer == null ? "" : expectedServer.trim();
        String actualAccount = cache.getAccount() == null ? "" : cache.getAccount().trim();
        String actualServer = cache.getServer() == null ? "" : cache.getServer().trim();
        if (!safeExpectedAccount.equals(actualAccount) || !safeExpectedServer.equals(actualServer)) {
            throw new IllegalStateException("v2 account full recovered cache identity mismatch");
        }
        return cache;
    }

    // stream 运行态必须显式给出 historyRevision，缺失时直接暴露协议问题。
    private String resolveHistoryRevisionFromPublishedRuntime(JSONObject runtimeSnapshot) {
        JSONObject runtimeMeta = runtimeSnapshot == null ? new JSONObject() : runtimeSnapshot.optJSONObject("accountMeta");
        String runtimeRevision = optString(runtimeMeta, "historyRevision", "");
        if (!runtimeRevision.trim().isEmpty()) {
            return runtimeRevision.trim();
        }
        throw new IllegalStateException("published account runtime missing historyRevision");
    }

    private String resolveHistoryRevisionFromSnapshotPayload(AccountSnapshotPayload snapshotPayload) {
        JSONObject accountMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        String historyRevision = optString(accountMeta, "historyRevision", "");
        if (!historyRevision.trim().isEmpty()) {
            return historyRevision.trim();
        }
        throw new IllegalStateException("v2 account snapshot missing historyRevision");
    }

    // 单次完整快照强刷只接受服务端显式返回的 historyRevision。
    private String resolveHistoryRevisionFromFullPayload(AccountFullPayload fullPayload) {
        JSONObject accountMeta = fullPayload == null ? new JSONObject() : fullPayload.getAccountMeta();
        String historyRevision = optString(accountMeta, "historyRevision", "");
        if (!historyRevision.trim().isEmpty()) {
            return historyRevision.trim();
        }
        throw new IllegalStateException("v2 account full missing historyRevision");
    }

    // history revision 真值来自 stream revisions，空值时直接视为协议断裂。
    private String requireHistoryRevision(String historyRevision) {
        String safeRevision = historyRevision == null ? "" : historyRevision.trim();
        if (!safeRevision.isEmpty()) {
            return safeRevision;
        }
        throw new IllegalStateException("v2 account history refresh missing historyRevision");
    }

    private Cache buildAndApplyFailureCache(String errorMessage) {
        nextDelayMs = resolveRefreshDelayMs();
        Cache previous = latestCache;
        if (previous != null) {
            Cache cache = buildFailureCache(previous, errorMessage);
            updateLatestCache(cache);
            return cache;
        }
        Cache cache = buildInitialFailureCache(errorMessage);
        updateLatestCache(cache);
        return cache;
    }

    // 解析服务端直接返回的展示指标，不再本地补算账户真值。
    private List<AccountMetric> parseMetrics(JSONArray array) {
        List<AccountMetric> metrics = new ArrayList<>();
        if (array == null) {
            return metrics;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            metrics.add(new AccountMetric(
                    MetricNameTranslator.toChinese(item.optString("name", "--")),
                    item.optString("value", "--")
            ));
        }
        return metrics;
    }

    // 解析 v2 持仓与挂单数组，统一转为页面复用的 PositionItem。
    private List<PositionItem> parsePositionItems(JSONArray array, boolean pending) {
        List<PositionItem> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String tradeSymbol = requireCanonicalTradeSymbol(item, pending ? "v2 pending order" : "v2 position");
            String productName = requireCanonicalProductName(
                    item,
                    tradeSymbol,
                    pending ? "v2 pending order" : "v2 position"
            );
            items.add(new PositionItem(
                    productName,
                    tradeSymbol,
                    optString(item, "side", ""),
                    optLong(item, "positionTicket", 0L),
                    optLong(item, "orderId", 0L),
                    optLong(item, "openTime", 0L),
                    optDouble(item, "quantity"),
                    pending ? 0d : optDouble(item, "sellableQuantity"),
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
        return items;
    }

    // 解析 v2 历史成交数组，转换为账户页当前使用的成交模型。
    private List<TradeRecordItem> parseTradeItems(JSONArray array) {
        List<TradeRecordItem> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String tradeSymbol = requireCanonicalTradeSymbol(item, "v2 trade");
            String productName = requireCanonicalProductName(item, tradeSymbol, "v2 trade");
            items.add(new TradeRecordItem(
                    optLong(item, "timestamp", 0L),
                    productName,
                    tradeSymbol,
                    optString(item, "side", ""),
                    optDouble(item, "price"),
                    optDouble(item, "quantity"),
                    optDouble(item, "amount"),
                    optDouble(item, "fee"),
                    optString(item, "remark", ""),
                    optDouble(item, "profit"),
                    optLong(item, "openTime", 0L),
                    optLong(item, "closeTime", 0L),
                    optDouble(item, "storageFee"),
                    optDouble(item, "openPrice"),
                    optDouble(item, "closePrice"),
                    optLong(item, "dealTicket", 0L),
                    optLong(item, "orderId", 0L),
                    optLong(item, "positionId", 0L),
                    item.optInt("entryType", 0)
            ));
        }
        return items;
    }

    // 解析 v2 净值曲线数组。
    private List<CurvePoint> parseCurvePoints(JSONArray array) {
        List<CurvePoint> items = new ArrayList<>();
        if (array == null) {
            return items;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            items.add(new CurvePoint(
                    item.optLong("timestamp", 0L),
                    optDouble(item, "equity"),
                    optDouble(item, "balance"),
                    optDouble(item, "positionRatio")
            ));
        }
        return items;
    }

    // 显式完整快照优先使用 full 接口时间，不再拆快照/历史时间来源。
    private long resolveUpdatedAt(AccountFullPayload fullPayload) {
        return fullPayload == null ? 0L : fullPayload.getServerTime();
    }

    // stream 运行态优先沿用服务端发布时间，缺失时再退回当前本地时间。
    private long resolveUpdatedAt(JSONObject runtimeMeta, long fallbackPublishedAt) {
        long publishedAt = Math.max(0L, fallbackPublishedAt);
        if (publishedAt > 0L) {
            return publishedAt;
        }
        return System.currentTimeMillis();
    }

    // 统一读取当前网关地址，用于页面元信息展示。
    private String resolveGatewayEndpoint() {
        if (configManager == null) {
            return "--";
        }
        String baseUrl = configManager.getMt5GatewayBaseUrl();
        return baseUrl == null || baseUrl.trim().isEmpty() ? "--" : baseUrl.trim();
    }

    // 对 v2 数据源名称做最小本地化，避免页面直接露出底层英文实现名。
    private String resolveV2Source(JSONObject accountMeta) {
        String raw = accountMeta == null ? "" : accountMeta.optString("source", "");
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("gateway")) {
            return "V2网关";
        }
        if (normalized.contains("pull")) {
            return "MT5拉取";
        }
        return raw == null || raw.trim().isEmpty() ? "V2网关" : raw.trim();
    }

    // 账号与服务器优先沿用当前运行态真值，缺失时才补本次 history meta。
    private String resolveIdentityField(String currentValue, JSONObject meta, String key) {
        String normalizedCurrent = currentValue == null ? "" : currentValue.trim();
        if (!normalizedCurrent.isEmpty()) {
            return normalizedCurrent;
        }
        return optString(meta, key, "");
    }

    // 复制对象，避免后续页拼接时意外改写第一页原始载荷。
    private static JSONObject copyJsonObject(JSONObject source) {
        if (source == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(source.toString());
        } catch (Exception exception) {
            throw new IllegalStateException("failed to copy history object", exception);
        }
    }

    // 复制数组，避免合并结果与原始页共享同一引用。
    private static JSONArray copyJsonArray(JSONArray source) {
        if (source == null) {
            return new JSONArray();
        }
        try {
            return new JSONArray(source.toString());
        } catch (Exception exception) {
            throw new IllegalStateException("failed to copy history array", exception);
        }
    }

    // 逐项追加分页数组，保持服务端返回顺序不变。
    private static void appendJsonArray(JSONArray target, JSONArray source) {
        if (target == null || source == null) {
            return;
        }
        for (int i = 0; i < source.length(); i++) {
            target.put(source.opt(i));
        }
    }

    // 读取字符串字段，空值时回退到默认值。
    private String optString(JSONObject item, String key, String fallback) {
        if (item == null || key == null || key.trim().isEmpty() || !item.has(key)) {
            return fallback;
        }
        String value = item.optString(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }

    // 主链协议字段必须显式提供 tradeSymbol，不能再从其他字段兜底拼装。
    private String requireCanonicalTradeSymbol(JSONObject item, String context) {
        String tradeSymbol = optString(item, "tradeSymbol", "").trim();
        if (tradeSymbol.isEmpty()) {
            throw new IllegalStateException(context + " missing tradeSymbol");
        }
        return tradeSymbol;
    }

    // 展示名称必须与 canonical tradeSymbol 对齐，避免同一产品多口径并存。
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

    // 读取浮点字段，缺失或非法时返回 0。
    private double optDouble(JSONObject item, String key) {
        return optDouble(item, key, 0d);
    }

    // 读取浮点字段，允许指定默认值。
    private double optDouble(JSONObject item, String key, double fallback) {
        if (item == null || key == null || key.trim().isEmpty() || !item.has(key)) {
            return fallback;
        }
        Object value = item.opt(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    // 读取多种候选浮点字段，兼容服务端驼峰与下划线口径。
    private double optDoubleAny(JSONObject item, double fallback, String... keys) {
        if (item == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            double value = optDouble(item, key, Double.NaN);
            if (!Double.isNaN(value)) {
                return value;
            }
        }
        return fallback;
    }

    // 读取长整数字段，缺失或非法时返回默认值。
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

    // 读取多种候选长整数字段，兼容服务端驼峰与下划线口径。
    private long optLongAny(JSONObject item, long fallback, String... keys) {
        if (item == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty() || !item.has(key)) {
                continue;
            }
            Object value = item.opt(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                try {
                    return Long.parseLong(((String) value).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return fallback;
    }

    public static class ApplyResult {
        private final boolean success;
        private final String message;
        private final Cache cache;

        private ApplyResult(boolean success, String message, Cache cache) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.cache = cache;
        }

        public static ApplyResult success(Cache cache) {
            return new ApplyResult(true, "", cache);
        }

        public static ApplyResult failure(String message, Cache cache) {
            return new ApplyResult(false, message, cache);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Cache getCache() {
            return cache;
        }
    }

    public static class Cache {
        private final boolean connected;
        private final AccountSnapshot snapshot;
        private final String account;
        private final String server;
        private final String source;
        private final String gateway;
        private final long updatedAt;
        private final String error;
        private final long fetchedAt;
        private final String historyRevision;
        private final String accountMode;

        public Cache(boolean connected,
                     AccountSnapshot snapshot,
                     String account,
                     String server,
                     String source,
                     String gateway,
                     long updatedAt,
                     String error,
                     long fetchedAt) {
            this(connected, snapshot, account, server, source, gateway, updatedAt, error, fetchedAt, "", "");
        }

        public Cache(boolean connected,
                     AccountSnapshot snapshot,
                     String account,
                     String server,
                     String source,
                     String gateway,
                     long updatedAt,
                     String error,
                     long fetchedAt,
                     String historyRevision) {
            this(connected, snapshot, account, server, source, gateway, updatedAt, error, fetchedAt, historyRevision, "");
        }

        public Cache(boolean connected,
                     AccountSnapshot snapshot,
                     String account,
                     String server,
                     String source,
                     String gateway,
                     long updatedAt,
                     String error,
                     long fetchedAt,
                     String historyRevision,
                     String accountMode) {
            this.connected = connected;
            this.snapshot = snapshot;
            this.account = account == null ? "" : account;
            this.server = server == null ? "" : server;
            this.source = source == null ? "" : source;
            this.gateway = gateway == null ? "" : gateway;
            this.updatedAt = updatedAt;
            this.error = error == null ? "" : error;
            this.fetchedAt = fetchedAt;
            this.historyRevision = historyRevision == null ? "" : historyRevision;
            this.accountMode = accountMode == null ? "" : accountMode;
        }

        public boolean isConnected() {
            return connected;
        }

        public AccountSnapshot getSnapshot() {
            return snapshot;
        }

        public String getAccount() {
            return account;
        }

        public String getServer() {
            return server;
        }

        public String getSource() {
            return source;
        }

        public String getGateway() {
            return gateway;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }

        public String getError() {
            return error;
        }

        public long getFetchedAt() {
            return fetchedAt;
        }

        public String getHistoryRevision() {
            return historyRevision;
        }

        public String getAccountMode() {
            return accountMode;
        }
    }

    public interface CacheListener {
        void onCacheUpdated(Cache cache);
    }
}

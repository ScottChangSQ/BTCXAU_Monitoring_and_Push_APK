/*
 * 账户预加载管理器，负责消费服务端已发布的账户运行态并管理历史补拉。
 * 供账户页、图表页等依赖账户信息的界面复用。
 */
package com.binance.monitor.ui.account;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.model.v2.AccountHistoryPayload;
import com.binance.monitor.data.model.v2.AccountSnapshotPayload;
import com.binance.monitor.data.remote.v2.GatewayV2Client;
import com.binance.monitor.runtime.AppForegroundTracker;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

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
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<CacheListener> cacheListeners = new CopyOnWriteArraySet<>();
    private final AppForegroundTracker.ForegroundStateListener appForegroundListener =
            foreground -> handleForegroundStateChanged(foreground);
    private final Object lock = new Object();
    private static final long MAX_REFRESH_MS = AppConstants.ACCOUNT_REFRESH_MAX_INTERVAL_MS;

    private volatile Cache latestCache;
    private volatile boolean started;
    private volatile boolean loading;
    private volatile boolean liveScreenActive;
    private volatile boolean fullSnapshotActive;
    private volatile long nextDelayMs = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS;
    private final AtomicBoolean overlayFetchInFlight = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledFetchFuture;
    private long scheduleGeneration;
    private volatile boolean foregroundListenerRegistered;

    private AccountStatsPreloadManager(Context context) {
        gatewayV2Client = new GatewayV2Client(context.getApplicationContext());
        accountStorageRepository = new AccountStorageRepository(context.getApplicationContext());
        configManager = ConfigManager.getInstance(context.getApplicationContext());
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
        scheduleFetch(0L);
    }

    public Cache getLatestCache() {
        Cache cache = latestCache;
        if (cache != null || !isAccountSessionActive()) {
            return cache;
        }
        AccountStorageRepository.StoredSnapshot storedSnapshot = accountStorageRepository.loadStoredSnapshot();
        if (!hasStoredSnapshotContent(storedSnapshot)) {
            return null;
        }
        Cache hydratedCache = buildCache(storedSnapshot, storedSnapshot.getHistoryRevision());
        latestCache = hydratedCache;
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
        if (loading) {
            return;
        }
        loading = true;
        try {
            if (!isAccountSessionActive()) {
                accountStorageRepository.clearRuntimeSnapshot();
                accountStorageRepository.clearTradeHistory();
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
            loading = false;
        }
    }

    // 应用服务端发布的账户运行态快照，统一更新本地运行态与页面缓存。
    public Cache applyPublishedAccountRuntime(JSONObject accountRuntimeSnapshot, long publishedAt) {
        if (accountRuntimeSnapshot == null) {
            return latestCache;
        }
        try {
            if (!isAccountSessionActive()) {
                accountStorageRepository.clearRuntimeSnapshot();
                accountStorageRepository.clearTradeHistory();
                clearLatestCache();
                nextDelayMs = MAX_REFRESH_MS;
                return null;
            }
            AccountStorageRepository.StoredSnapshot storedSnapshot =
                    buildStoredSnapshotFromPublishedRuntime(accountRuntimeSnapshot, publishedAt);
            accountStorageRepository.persistIncrementalSnapshot(storedSnapshot);
            AccountStorageRepository.StoredSnapshot cachedSnapshot =
                    accountStorageRepository.loadStoredSnapshot();
            String resolvedRevision = resolveHistoryRevisionFromPublishedRuntime(accountRuntimeSnapshot);
            Cache cache = buildCache(cachedSnapshot, resolvedRevision);
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
        }
    }

    // 仅当 history revision 前进或本地还没有历史时，才补拉一次全量历史。
    public Cache refreshHistoryForRevision(String remoteHistoryRevision) {
        if (!overlayFetchInFlight.compareAndSet(false, true)) {
            return latestCache;
        }
        try {
            if (!isAccountSessionActive()) {
                accountStorageRepository.clearRuntimeSnapshot();
                accountStorageRepository.clearTradeHistory();
                clearLatestCache();
                nextDelayMs = MAX_REFRESH_MS;
                return null;
            }
            Cache previous = latestCache;
            AccountStorageRepository.StoredSnapshot storedSnapshot =
                    accountStorageRepository.loadStoredSnapshot();
            String cachedHistoryRevision = previous == null
                    ? storedSnapshot.getHistoryRevision()
                    : previous.getHistoryRevision();
            int storedTradeCount = accountStorageRepository.loadTrades().size();
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
            AccountHistoryPayload historyPayload = gatewayV2Client.fetchAccountHistory(AccountTimeRange.ALL, "");
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

    // 图表页和悬浮窗读取当前账户缓存，不再主动拉取 account snapshot。
    public Cache fetchForOverlay() {
        return latestCache;
    }

    // 供账户页主动刷新与交易后强一致确认复用；显式入口仍走 canonical snapshot/history。
    public Cache fetchForUi(AccountTimeRange range) {
        if (!isAccountSessionActive()) {
            accountStorageRepository.clearRuntimeSnapshot();
            accountStorageRepository.clearTradeHistory();
            clearLatestCache();
            nextDelayMs = MAX_REFRESH_MS;
            return null;
        }
        AccountTimeRange safeRange = range == null ? AccountTimeRange.ALL : range;
        try {
            AccountSnapshotPayload snapshotPayload = gatewayV2Client.fetchAccountSnapshot();
            String remoteHistoryRevision = resolveRemoteHistoryRevision(snapshotPayload);
            Cache previous = latestCache;
            AccountStorageRepository.StoredSnapshot storedSnapshot =
                    accountStorageRepository.loadStoredSnapshot();
            String cachedHistoryRevision = previous == null
                    ? storedSnapshot.getHistoryRevision()
                    : previous.getHistoryRevision();
            int storedTradeCount = accountStorageRepository.loadTrades().size();
            boolean hasStoredTradeHistory = storedTradeCount > 0;
            boolean shouldRefreshAllHistory = AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(
                    remoteHistoryRevision,
                    cachedHistoryRevision,
                    hasStoredTradeHistory
            );

            Cache cache;
            if (shouldRefreshAllHistory) {
                AccountHistoryPayload historyPayload = gatewayV2Client.fetchAccountHistory(safeRange, "");
                AccountStorageRepository.StoredSnapshot mergedSnapshot =
                        buildStoredSnapshotFromV2(snapshotPayload, historyPayload);
                accountStorageRepository.persistV2Snapshot(mergedSnapshot);
                String resolvedRevision = resolveHistoryRevisionFromPayload(snapshotPayload, historyPayload);
                cache = buildCache(mergedSnapshot, resolvedRevision);
            } else {
                AccountStorageRepository.StoredSnapshot incrementalSnapshot =
                        buildStoredSnapshotFromSnapshotOnly(snapshotPayload);
                accountStorageRepository.persistIncrementalSnapshot(incrementalSnapshot);
                AccountStorageRepository.StoredSnapshot cachedSnapshot =
                        accountStorageRepository.loadStoredSnapshot();
                String resolvedRevision = resolveHistoryRevisionFromPayload(snapshotPayload, null);
                cache = buildCache(cachedSnapshot, resolvedRevision);
            }
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
        }
    }

    // 更新最新缓存并把刷新结果派发给前台页面。
    private void updateLatestCache(Cache cache) {
        latestCache = cache;
        notifyCacheListeners(cache);
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

    // 将显式 snapshot + history 响应合并成本地完整快照，供账户页和交易确认强刷新复用。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromV2(AccountSnapshotPayload snapshotPayload,
                                                                              AccountHistoryPayload historyPayload) {
        JSONObject accountMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        JSONArray positions = snapshotPayload == null ? new JSONArray() : snapshotPayload.getPositions();
        JSONArray orders = snapshotPayload == null ? new JSONArray() : snapshotPayload.getOrders();
        JSONArray trades = historyPayload == null ? new JSONArray() : historyPayload.getTrades();
        JSONArray curvePoints = historyPayload == null ? new JSONArray() : historyPayload.getCurvePoints();
        return new AccountStorageRepository.StoredSnapshot(
                resolveRuntimeConnected(accountMeta),
                optString(accountMeta, "login", ""),
                optString(accountMeta, "server", ""),
                resolveV2Source(accountMeta),
                resolveGatewayEndpoint(),
                resolveUpdatedAt(snapshotPayload, historyPayload),
                "",
                System.currentTimeMillis(),
                resolveHistoryRevisionFromPayload(snapshotPayload, historyPayload),
                parseMetrics(snapshotPayload == null ? null : snapshotPayload.getOverviewMetrics()),
                parseCurvePoints(curvePoints),
                parseMetrics(historyPayload == null ? null : historyPayload.getCurveIndicators()),
                parsePositionItems(positions, false),
                parsePositionItems(orders, true),
                parseTradeItems(trades),
                parseMetrics(historyPayload == null ? null : historyPayload.getStatsMetrics())
        );
    }

    // 将显式 snapshot 响应转换为只更新运行态的本地结构，保留现有历史侧数据。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromSnapshotOnly(AccountSnapshotPayload snapshotPayload) {
        JSONObject accountMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        JSONArray positions = snapshotPayload == null ? new JSONArray() : snapshotPayload.getPositions();
        JSONArray orders = snapshotPayload == null ? new JSONArray() : snapshotPayload.getOrders();
        return new AccountStorageRepository.StoredSnapshot(
                resolveRuntimeConnected(accountMeta),
                optString(accountMeta, "login", ""),
                optString(accountMeta, "server", ""),
                resolveV2Source(accountMeta),
                resolveGatewayEndpoint(),
                resolveUpdatedAt(snapshotPayload, null),
                "",
                System.currentTimeMillis(),
                resolveHistoryRevisionFromPayload(snapshotPayload, null),
                parseMetrics(snapshotPayload == null ? null : snapshotPayload.getOverviewMetrics()),
                new ArrayList<>(),
                parseMetrics(snapshotPayload == null ? null : snapshotPayload.getCurveIndicators()),
                parsePositionItems(positions, false),
                parsePositionItems(orders, true),
                new ArrayList<>(),
                parseMetrics(snapshotPayload == null ? null : snapshotPayload.getStatsMetrics())
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
                ? accountStorageRepository.loadStoredSnapshot()
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

    // 统一把存储快照转成页面缓存，避免同一份字段在多处重复拼装。
    private Cache buildCache(AccountStorageRepository.StoredSnapshot storedSnapshot, String historyRevision) {
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
                resolvedHistoryRevision
        );
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
                previous.historyRevision
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

    // stream 运行态必须显式给出 historyRevision，缺失时直接暴露协议问题。
    private String resolveHistoryRevisionFromPublishedRuntime(JSONObject runtimeSnapshot) {
        JSONObject runtimeMeta = runtimeSnapshot == null ? new JSONObject() : runtimeSnapshot.optJSONObject("accountMeta");
        String runtimeRevision = optString(runtimeMeta, "historyRevision", "");
        if (!runtimeRevision.trim().isEmpty()) {
            return runtimeRevision.trim();
        }
        throw new IllegalStateException("published account runtime missing historyRevision");
    }

    // 主动 snapshot 刷新时，historyRevision 只认 accountMeta 里的显式字段。
    private String resolveRemoteHistoryRevision(AccountSnapshotPayload snapshotPayload) {
        JSONObject accountMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        return optString(accountMeta, "historyRevision", "");
    }

    // 显式 snapshot/history 刷新只接受服务端给出的 historyRevision，缺失视为协议错误。
    private String resolveHistoryRevisionFromPayload(AccountSnapshotPayload snapshotPayload,
                                                     AccountHistoryPayload historyPayload) {
        JSONObject snapshotMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        String snapshotRevision = optString(snapshotMeta, "historyRevision", "");
        if (!snapshotRevision.trim().isEmpty()) {
            return snapshotRevision.trim();
        }
        throw new IllegalStateException("v2 account snapshot missing historyRevision");
    }

    // history revision 真值来自 stream revisions，空值时直接视为协议断裂。
    private String requireHistoryRevision(String historyRevision) {
        String safeRevision = historyRevision == null ? "" : historyRevision.trim();
        if (!safeRevision.isEmpty()) {
            return safeRevision;
        }
        throw new IllegalStateException("v2 account history refresh missing historyRevision");
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

    // 显式刷新优先使用 snapshot 时间，没有时再回退 history 时间。
    private long resolveUpdatedAt(AccountSnapshotPayload snapshotPayload, AccountHistoryPayload historyPayload) {
        long snapshotTime = snapshotPayload == null ? 0L : snapshotPayload.getServerTime();
        if (snapshotTime > 0L) {
            return snapshotTime;
        }
        return historyPayload == null ? 0L : historyPayload.getServerTime();
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

        public Cache(boolean connected,
                     AccountSnapshot snapshot,
                     String account,
                     String server,
                     String source,
                     String gateway,
                     long updatedAt,
                     String error,
                     long fetchedAt) {
            this(connected, snapshot, account, server, source, gateway, updatedAt, error, fetchedAt, "");
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
    }

    public interface CacheListener {
        void onCacheUpdated(Cache cache);
    }
}

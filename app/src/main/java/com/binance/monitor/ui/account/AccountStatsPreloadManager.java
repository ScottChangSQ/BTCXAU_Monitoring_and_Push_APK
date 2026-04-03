/*
 * 账户快照预加载管理器，负责后台拉取 MT5 网关快照并向页面分发最新缓存。
 * 供账户页、图表页等依赖账户信息的界面复用。
 */
package com.binance.monitor.ui.account;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.runtime.AppForegroundTracker;
import com.binance.monitor.ui.account.model.AccountSnapshot;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AccountStatsPreloadManager {
    private static volatile AccountStatsPreloadManager instance;

    private final Mt5BridgeGatewayClient gatewayClient;
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
    private ScheduledFuture<?> scheduledFetchFuture;
    private long scheduleGeneration;
    private volatile boolean foregroundListenerRegistered;

    private AccountStatsPreloadManager(Context context) {
        gatewayClient = new Mt5BridgeGatewayClient(context.getApplicationContext());
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
        return latestCache;
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

    // 前后台切换后按最新策略重新排下一次预加载，前台优先立即补新数据。
    private void handleForegroundStateChanged(boolean foreground) {
        nextDelayMs = resolveRefreshDelayMs();
        if (!started || !isAccountSessionActive() || liveScreenActive) {
            return;
        }
        scheduleFetch(foreground ? 0L : nextDelayMs);
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

    // 拉取一次最新账户快照，并根据当前运行态决定下一次调度节奏。
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
            if (liveScreenActive) {
                nextDelayMs = MAX_REFRESH_MS;
                return;
            }
            Mt5BridgeGatewayClient.SnapshotResult result = gatewayClient.fetch(AccountTimeRange.ALL);
            if (!result.isSuccess()) {
                nextDelayMs = resolveRefreshDelayMs();
                Cache previous = latestCache;
                if (previous != null) {
                    updateLatestCache(new Cache(
                            false,
                            previous.snapshot,
                            previous.account,
                            previous.server,
                            previous.source,
                            previous.gateway,
                            previous.updatedAt,
                            result.getError(),
                            System.currentTimeMillis()));
                }
                return;
            }
            AccountSnapshot snapshot = result.getSnapshot();
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(new Cache(
                    true,
                    snapshot,
                    result.getAccount(""),
                    result.getServer(""),
                    result.getLocalizedSource(),
                    result.getGatewayEndpoint(),
                    result.getUpdatedAt(),
                    "",
                    System.currentTimeMillis()));
            persistPreloadSnapshot(snapshot, result, fullSnapshotActive);
        } catch (Exception exception) {
            nextDelayMs = resolveRefreshDelayMs();
            Cache previous = latestCache;
            if (previous != null) {
                updateLatestCache(new Cache(
                        false,
                        previous.snapshot,
                        previous.account,
                        previous.server,
                        previous.source,
                        previous.gateway,
                        previous.updatedAt,
                        exception.getMessage(),
                        System.currentTimeMillis()));
            }
        } finally {
            loading = false;
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

    private void persistPreloadSnapshot(AccountSnapshot snapshot,
                                        Mt5BridgeGatewayClient.SnapshotResult result,
                                        boolean fullSnapshot) {
        if (accountStorageRepository == null || snapshot == null || result == null) {
            return;
        }
        AccountStorageRepository.StoredSnapshot storedSnapshot =
                new AccountStorageRepository.StoredSnapshot(
                        true,
                        result.getAccount(""),
                        result.getServer(""),
                        result.getLocalizedSource(),
                        result.getGatewayEndpoint(),
                        result.getUpdatedAt(),
                        "",
                        System.currentTimeMillis(),
                        snapshot.getOverviewMetrics(),
                        snapshot.getCurvePoints(),
                        snapshot.getCurveIndicators(),
                        snapshot.getPositions(),
                        snapshot.getPendingOrders(),
                        snapshot.getTrades(),
                        snapshot.getStatsMetrics()
                );
        if (fullSnapshot) {
            accountStorageRepository.persistSnapshot(storedSnapshot);
        } else {
            accountStorageRepository.persistIncrementalSnapshot(storedSnapshot);
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

        public Cache(boolean connected,
                     AccountSnapshot snapshot,
                     String account,
                     String server,
                     String source,
                     String gateway,
                     long updatedAt,
                     String error,
                     long fetchedAt) {
            this.connected = connected;
            this.snapshot = snapshot;
            this.account = account == null ? "" : account;
            this.server = server == null ? "" : server;
            this.source = source == null ? "" : source;
            this.gateway = gateway == null ? "" : gateway;
            this.updatedAt = updatedAt;
            this.error = error == null ? "" : error;
            this.fetchedAt = fetchedAt;
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
    }

    public interface CacheListener {
        void onCacheUpdated(Cache cache);
    }
}

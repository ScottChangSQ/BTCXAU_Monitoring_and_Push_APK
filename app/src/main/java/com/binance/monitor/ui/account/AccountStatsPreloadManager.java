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
import com.binance.monitor.data.local.V2SnapshotStore;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.model.v2.AccountHistoryPayload;
import com.binance.monitor.data.remote.v2.GatewayV2Client;
import com.binance.monitor.data.model.v2.AccountSnapshotPayload;
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

public class AccountStatsPreloadManager {
    private static volatile AccountStatsPreloadManager instance;

    private final Mt5BridgeGatewayClient gatewayClient;
    private final GatewayV2Client gatewayV2Client;
    private final AccountStorageRepository accountStorageRepository;
    private final V2SnapshotStore v2SnapshotStore;
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
        gatewayV2Client = new GatewayV2Client(context.getApplicationContext());
        accountStorageRepository = new AccountStorageRepository(context.getApplicationContext());
        v2SnapshotStore = new V2SnapshotStore(context.getApplicationContext());
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
            // 图表页等前台轻量轮询时，只拉账户快照；只有历史成交数变化时才补拉全量历史。
            if (fullSnapshotActive) {
                Cache cache = fetchForOverlay();
                if (cache != null) {
                    nextDelayMs = resolveRefreshDelayMs();
                    return;
                }
            }
            Mt5BridgeGatewayClient.SnapshotResult result = gatewayClient.fetch(AccountTimeRange.ALL);
            if (!result.isSuccess()) {
                nextDelayMs = resolveRefreshDelayMs();
                Cache previous = latestCache;
                if (previous != null) {
                    updateLatestCache(buildFailureCache(previous, result.getError()));
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
                    System.currentTimeMillis(),
                    snapshot == null ? 0 : snapshot.getTrades().size()));
            persistPreloadSnapshot(snapshot, result, fullSnapshotActive);
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

    // 图表页和悬浮窗的高频账户刷新入口：先拿轻快照，只有检测到新历史成交时才补拉全历史。
    public Cache fetchForOverlay() {
        if (!isAccountSessionActive()) {
            accountStorageRepository.clearRuntimeSnapshot();
            accountStorageRepository.clearTradeHistory();
            clearLatestCache();
            nextDelayMs = MAX_REFRESH_MS;
            return null;
        }
        try {
            AccountSnapshotPayload snapshotPayload = gatewayV2Client.fetchAccountSnapshot();
            v2SnapshotStore.writeAccountSnapshot(snapshotPayload.getRawJson());
            int remoteTradeCount = resolveRemoteTradeCount(snapshotPayload);
            Cache previous = latestCache;
            int cachedTradeCount = previous == null ? -1 : previous.getHistoryTradeCount();
            boolean hasStoredTradeHistory = !accountStorageRepository.loadTrades().isEmpty();
            boolean shouldRefreshAllHistory = AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(
                    remoteTradeCount,
                    cachedTradeCount,
                    hasStoredTradeHistory
            );
            if (shouldRefreshAllHistory) {
                AccountHistoryPayload historyPayload = gatewayV2Client.fetchAccountHistory(AccountTimeRange.ALL, "");
                AccountStorageRepository.StoredSnapshot storedSnapshot =
                        buildStoredSnapshotFromV2(snapshotPayload, historyPayload);
                accountStorageRepository.persistV2Snapshot(storedSnapshot);
                Cache cache = buildCache(storedSnapshot, storedSnapshot.getTrades().size());
                nextDelayMs = resolveRefreshDelayMs();
                updateLatestCache(cache);
                return cache;
            }

            AccountStorageRepository.StoredSnapshot storedSnapshot =
                    buildStoredSnapshotFromSnapshotOnly(snapshotPayload);
            accountStorageRepository.persistIncrementalSnapshot(storedSnapshot);
            Cache cache = buildCache(storedSnapshot, remoteTradeCount);
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(cache);
            return cache;
        } catch (Exception ignored) {
        }

        try {
            Mt5BridgeGatewayClient.SnapshotResult result = gatewayClient.fetch(AccountTimeRange.ALL);
            if (!result.isSuccess()) {
                nextDelayMs = resolveRefreshDelayMs();
                Cache previous = latestCache;
                if (previous != null) {
                    Cache cache = buildFailureCache(previous, result.getError());
                    updateLatestCache(cache);
                    return cache;
                }
                return new Cache(
                        false,
                        null,
                        "",
                        "",
                        "历史数据（网关离线）",
                        "Gateway offline",
                        System.currentTimeMillis(),
                        result.getError(),
                        System.currentTimeMillis()
                );
            }
            AccountSnapshot snapshot = result.getSnapshot();
            Cache cache = new Cache(
                    true,
                    snapshot,
                    result.getAccount(""),
                    result.getServer(""),
                    result.getLocalizedSource(),
                    result.getGatewayEndpoint(),
                    result.getUpdatedAt(),
                    "",
                    System.currentTimeMillis(),
                    snapshot == null ? 0 : snapshot.getTrades().size()
            );
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(cache);
            persistPreloadSnapshot(snapshot, result, true);
            return cache;
        } catch (Exception exception) {
            nextDelayMs = resolveRefreshDelayMs();
            Cache previous = latestCache;
            if (previous != null) {
                Cache cache = buildFailureCache(previous, exception.getMessage());
                updateLatestCache(cache);
                return cache;
            }
            return new Cache(
                    false,
                    null,
                    "",
                    "",
                    "历史数据（网关离线）",
                    "Gateway offline",
                    System.currentTimeMillis(),
                    exception.getMessage(),
                    System.currentTimeMillis()
            );
        }
    }

    // 供账户页主动触发一次前台刷新，统一复用 v2 优先、旧网关回退的抓取逻辑。
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
            AccountSnapshotPayload v2Payload = gatewayV2Client.fetchAccountSnapshot();
            AccountHistoryPayload historyPayload = gatewayV2Client.fetchAccountHistory(safeRange, "");
            v2SnapshotStore.writeAccountSnapshot(v2Payload.getRawJson());
            AccountStorageRepository.StoredSnapshot storedSnapshot =
                    buildStoredSnapshotFromV2(v2Payload, historyPayload);
            accountStorageRepository.persistV2Snapshot(storedSnapshot);
            AccountSnapshot snapshot = new AccountSnapshot(
                    storedSnapshot.getOverviewMetrics(),
                    storedSnapshot.getCurvePoints(),
                    storedSnapshot.getCurveIndicators(),
                    storedSnapshot.getPositions(),
                    storedSnapshot.getPendingOrders(),
                    storedSnapshot.getTrades(),
                    storedSnapshot.getStatsMetrics()
            );
            Cache cache = new Cache(
                    true,
                    snapshot,
                    storedSnapshot.getAccount(),
                    storedSnapshot.getServer(),
                    storedSnapshot.getSource(),
                    storedSnapshot.getGateway(),
                    storedSnapshot.getUpdatedAt(),
                    "",
                    System.currentTimeMillis(),
                    storedSnapshot.getTrades().size()
            );
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(cache);
            return cache;
        } catch (Exception ignored) {
        }

        try {
            Mt5BridgeGatewayClient.SnapshotResult result = gatewayClient.fetch(safeRange);
            if (!result.isSuccess()) {
                nextDelayMs = resolveRefreshDelayMs();
                Cache previous = latestCache;
                if (previous != null) {
                    Cache cache = new Cache(
                            false,
                            previous.snapshot,
                            previous.account,
                            previous.server,
                            previous.source,
                            previous.gateway,
                            previous.updatedAt,
                            result.getError(),
                            System.currentTimeMillis()
                    );
                    updateLatestCache(cache);
                    return cache;
                }
                return new Cache(
                        false,
                        null,
                        "",
                        "",
                        "历史数据（网关离线）",
                        "Gateway offline",
                        System.currentTimeMillis(),
                        result.getError(),
                        System.currentTimeMillis()
                );
            }
            AccountSnapshot snapshot = result.getSnapshot();
            Cache cache = new Cache(
                    true,
                    snapshot,
                    result.getAccount(""),
                    result.getServer(""),
                    result.getLocalizedSource(),
                    result.getGatewayEndpoint(),
                    result.getUpdatedAt(),
                    "",
                    System.currentTimeMillis(),
                    snapshot == null ? 0 : snapshot.getTrades().size()
            );
            nextDelayMs = resolveRefreshDelayMs();
            updateLatestCache(cache);
            persistPreloadSnapshot(snapshot, result, true);
            return cache;
        } catch (Exception exception) {
            nextDelayMs = resolveRefreshDelayMs();
            Cache previous = latestCache;
            if (previous != null) {
                Cache cache = buildFailureCache(previous, exception.getMessage());
                updateLatestCache(cache);
                return cache;
            }
            return new Cache(
                    false,
                    null,
                    "",
                    "",
                    "历史数据（网关离线）",
                    "Gateway offline",
                    System.currentTimeMillis(),
                    exception.getMessage(),
                    System.currentTimeMillis()
            );
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

    // 将 v2 快照与历史载荷转换为本地统一存储结构。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromV2(AccountSnapshotPayload snapshotPayload,
                                                                              AccountHistoryPayload historyPayload) {
        JSONObject accountMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        JSONObject account = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccount();
        JSONArray positions = snapshotPayload == null ? new JSONArray() : snapshotPayload.getPositions();
        JSONArray orders = snapshotPayload == null ? new JSONArray() : snapshotPayload.getOrders();
        JSONArray trades = historyPayload == null ? new JSONArray() : historyPayload.getTrades();
        JSONArray curvePoints = historyPayload == null ? new JSONArray() : historyPayload.getCurvePoints();

        List<TradeRecordItem> tradeItems = parseTradeItems(trades);
        return new AccountStorageRepository.StoredSnapshot(
                true,
                accountMeta.optString("login", ""),
                accountMeta.optString("server", ""),
                resolveV2Source(accountMeta),
                resolveGatewayEndpoint(),
                resolveUpdatedAt(snapshotPayload, historyPayload),
                "",
                System.currentTimeMillis(),
                buildOverviewMetrics(account),
                parseCurvePoints(curvePoints),
                new ArrayList<>(),
                parsePositionItems(positions, false),
                parsePositionItems(orders, true),
                tradeItems,
                buildStatsMetrics(account, tradeItems)
        );
    }

    // 将 v2 轻快照转换为只包含账户摘要、当前持仓和挂单的本地结构。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromSnapshotOnly(AccountSnapshotPayload snapshotPayload) {
        JSONObject accountMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        JSONObject account = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccount();
        JSONArray positions = snapshotPayload == null ? new JSONArray() : snapshotPayload.getPositions();
        JSONArray orders = snapshotPayload == null ? new JSONArray() : snapshotPayload.getOrders();
        return new AccountStorageRepository.StoredSnapshot(
                true,
                accountMeta.optString("login", ""),
                accountMeta.optString("server", ""),
                resolveV2Source(accountMeta),
                resolveGatewayEndpoint(),
                resolveUpdatedAt(snapshotPayload, null),
                "",
                System.currentTimeMillis(),
                buildOverviewMetrics(account),
                new ArrayList<>(),
                new ArrayList<>(),
                parsePositionItems(positions, false),
                parsePositionItems(orders, true),
                new ArrayList<>(),
                buildStatsMetrics(account, new ArrayList<>())
        );
    }

    // 统一把存储快照转成页面缓存，避免同一份字段在多处重复拼装。
    private Cache buildCache(AccountStorageRepository.StoredSnapshot storedSnapshot, int historyTradeCount) {
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
                true,
                snapshot,
                storedSnapshot.getAccount(),
                storedSnapshot.getServer(),
                storedSnapshot.getSource(),
                storedSnapshot.getGateway(),
                storedSnapshot.getUpdatedAt(),
                "",
                System.currentTimeMillis(),
                historyTradeCount
        );
    }

    // 统一构建失败缓存，保留上一轮成功数据，只更新错误信息和抓取时间。
    private Cache buildFailureCache(Cache previous, String errorMessage) {
        return new Cache(
                false,
                previous.snapshot,
                previous.account,
                previous.server,
                previous.source,
                previous.gateway,
                previous.updatedAt,
                errorMessage,
                System.currentTimeMillis(),
                previous.historyTradeCount
        );
    }

    // 读取服务端当前历史成交总数，用它判断是否需要补拉全量历史。
    private int resolveRemoteTradeCount(AccountSnapshotPayload snapshotPayload) {
        JSONObject accountMeta = snapshotPayload == null ? new JSONObject() : snapshotPayload.getAccountMeta();
        return (int) optLongAny(accountMeta, 0L, "tradeCount", "trade_count");
    }

    // 生成账户页概要指标，保证 v2 成功时页面也有可展示的基础数字。
    private List<AccountMetric> buildOverviewMetrics(JSONObject account) {
        List<AccountMetric> metrics = new ArrayList<>();
        metrics.add(new AccountMetric("总资产", formatMoney(optDouble(account, "equity"))));
        metrics.add(new AccountMetric("结余", formatMoney(optDouble(account, "balance"))));
        metrics.add(new AccountMetric("浮动盈亏", formatSignedMoney(optDouble(account, "profit"))));
        metrics.add(new AccountMetric("保证金", formatMoney(optDouble(account, "margin"))));
        metrics.add(new AccountMetric("可用保证金", formatMoney(optDouble(account, "freeMargin"))));
        metrics.add(new AccountMetric("保证金率", formatPercent(optDouble(account, "marginLevel"))));
        return metrics;
    }

    // 生成统计区最小指标，避免 v2 预加载时页面完全空白。
    private List<AccountMetric> buildStatsMetrics(JSONObject account, List<TradeRecordItem> trades) {
        List<AccountMetric> metrics = new ArrayList<>();
        double totalProfit = 0d;
        for (TradeRecordItem item : trades) {
            if (item != null) {
                totalProfit += item.getProfit();
            }
        }
        metrics.add(new AccountMetric("累计盈亏", formatSignedMoney(totalProfit)));
        metrics.add(new AccountMetric("交易笔数", String.valueOf(trades.size())));
        metrics.add(new AccountMetric("当前浮盈亏", formatSignedMoney(optDouble(account, "profit"))));
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
            items.add(new PositionItem(
                    optString(item, "productName", optString(item, "code", "--")),
                    optString(item, "code", optString(item, "productName", "--")),
                    optString(item, "side", "Buy"),
                    item.optLong(pending ? "positionTicket" : "positionTicket", item.optLong("positionId", 0L)),
                    item.optLong("orderId", 0L),
                    optDouble(item, "quantity"),
                    optDouble(item, "sellableQuantity", optDouble(item, "quantity")),
                    optDouble(item, "costPrice"),
                    optDouble(item, "latestPrice", optDouble(item, "pendingPrice")),
                    optDouble(item, "marketValue"),
                    optDouble(item, "positionRatio"),
                    optDouble(item, "dayPnL"),
                    optDouble(item, "totalPnL"),
                    optDouble(item, "returnRate"),
                    optDouble(item, pending ? "pendingLots" : "pendingLots"),
                    item.optInt("pendingCount", pending ? 1 : 0),
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
            long timestamp = normalizeEpochMs(optLongAny(item, 0L, "timestamp", "time"));
            long openTime = normalizeEpochMs(optLongAny(item, timestamp,
                    "openTime", "open_time", "timeOpen", "time_open"));
            long closeTime = normalizeEpochMs(optLongAny(item, timestamp,
                    "closeTime", "close_time", "timeClose", "time_close"));
            double price = optDoubleAny(item, 0d, "price");
            items.add(new TradeRecordItem(
                    timestamp,
                    optString(item, "productName", optString(item, "code", "--")),
                    optString(item, "code", optString(item, "productName", "--")),
                    optString(item, "side", "Buy"),
                    price,
                    optDoubleAny(item, 0d, "quantity", "qty", "volume"),
                    optDoubleAny(item, 0d, "amount"),
                    optDoubleAny(item, 0d, "fee"),
                    optString(item, "remark", ""),
                    optDoubleAny(item, 0d, "profit", "pnl"),
                    openTime,
                    closeTime,
                    optDoubleAny(item, 0d, "storageFee", "storage_fee", "swap", "fee"),
                    optDoubleAny(item, price,
                            "openPrice", "open_time_price", "open_price", "open", "priceOpen", "entryPrice", "entry_price"),
                    optDoubleAny(item, price,
                            "closePrice", "close_time_price", "close_price", "close", "priceClose", "exitPrice", "exit_price"),
                    optLongAny(item, 0L, "dealTicket", "deal_ticket"),
                    optLongAny(item, 0L, "orderId", "order_id"),
                    optLongAny(item, 0L, "positionId", "position_id"),
                    (int) optLongAny(item, 0L, "entryType", "entry_type")
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
                    normalizeEpochMs(item.optLong("timestamp", 0L)),
                    optDouble(item, "equity"),
                    optDouble(item, "balance"),
                    optDouble(item, "positionRatio")
            ));
        }
        return items;
    }

    // 统一读取 v2 响应里的更新时间，优先取账户快照时间。
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

    // 统一兼容秒/毫秒时间戳。
    private long normalizeEpochMs(long value) {
        if (value <= 0L) {
            return 0L;
        }
        return value < 10_000_000_000L ? value * 1000L : value;
    }

    // 读取字符串字段，空值时回退到默认值。
    private String optString(JSONObject item, String key, String fallback) {
        if (item == null || key == null || key.trim().isEmpty() || !item.has(key)) {
            return fallback;
        }
        String value = item.optString(key, "").trim();
        return value.isEmpty() ? fallback : value;
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

    // 统一格式化金额展示。
    private String formatMoney(double value) {
        return String.format(Locale.US, "$%,.2f", value);
    }

    // 统一格式化带符号金额展示。
    private String formatSignedMoney(double value) {
        return String.format(Locale.US, "%s$%,.2f", value >= 0d ? "+" : "-", Math.abs(value));
    }

    // 统一格式化百分比展示。
    private String formatPercent(double value) {
        return String.format(Locale.US, "%.2f%%", value);
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
        private final int historyTradeCount;

        public Cache(boolean connected,
                     AccountSnapshot snapshot,
                     String account,
                     String server,
                     String source,
                     String gateway,
                     long updatedAt,
                     String error,
                     long fetchedAt) {
            this(connected, snapshot, account, server, source, gateway, updatedAt, error, fetchedAt, -1);
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
                     int historyTradeCount) {
            this.connected = connected;
            this.snapshot = snapshot;
            this.account = account == null ? "" : account;
            this.server = server == null ? "" : server;
            this.source = source == null ? "" : source;
            this.gateway = gateway == null ? "" : gateway;
            this.updatedAt = updatedAt;
            this.error = error == null ? "" : error;
            this.fetchedAt = fetchedAt;
            this.historyTradeCount = historyTradeCount;
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

        public int getHistoryTradeCount() {
            return historyTradeCount;
        }
    }

    public interface CacheListener {
        void onCacheUpdated(Cache cache);
    }
}

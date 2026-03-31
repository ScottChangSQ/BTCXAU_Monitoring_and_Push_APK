package com.binance.monitor.ui.account;

import android.content.Context;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.ui.account.model.AccountSnapshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountStatsPreloadManager {
    private static final long BACKGROUND_REFRESH_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS * 2L;

    private static volatile AccountStatsPreloadManager instance;

    private final Mt5BridgeGatewayClient gatewayClient;
    private final AccountStorageRepository accountStorageRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private static final long MIN_REFRESH_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS;
    private static final long MAX_REFRESH_MS = AppConstants.ACCOUNT_REFRESH_MAX_INTERVAL_MS;

    private volatile Cache latestCache;
    private volatile boolean started;
    private volatile boolean loading;
    private volatile boolean liveScreenActive;
    private volatile boolean fullSnapshotActive;
    private volatile long nextDelayMs = MIN_REFRESH_MS;

    private AccountStatsPreloadManager(Context context) {
        gatewayClient = new Mt5BridgeGatewayClient(context.getApplicationContext());
        accountStorageRepository = new AccountStorageRepository(context.getApplicationContext());
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
        scheduleFetch(0L);
    }

    public Cache getLatestCache() {
        return latestCache;
    }

    public void setLiveScreenActive(boolean active) {
        liveScreenActive = active;
    }

    public void setFullSnapshotActive(boolean active) {
        boolean changed = fullSnapshotActive != active;
        fullSnapshotActive = active;
        if (!changed || !active || !started) {
            return;
        }
        nextDelayMs = MIN_REFRESH_MS;
        executor.execute(this::fetchOnce);
    }

    public void clearLatestCache() {
        latestCache = null;
        nextDelayMs = MIN_REFRESH_MS;
    }

    private void scheduleFetch(long delayMs) {
        executor.execute(() -> {
            if (delayMs > 0L) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            fetchOnce();
            if (started) {
                scheduleFetch(nextDelayMs);
            }
        });
    }

    private void fetchOnce() {
        if (loading) {
            return;
        }
        loading = true;
        try {
            if (liveScreenActive) {
                nextDelayMs = MAX_REFRESH_MS;
                return;
            }
            Mt5BridgeGatewayClient.SnapshotResult result = fullSnapshotActive
                    ? gatewayClient.fetch(AccountTimeRange.ALL)
                    : gatewayClient.fetchLive(AccountTimeRange.ALL);
            if (!result.isSuccess()) {
                nextDelayMs = fullSnapshotActive ? MIN_REFRESH_MS : BACKGROUND_REFRESH_MS;
                Cache previous = latestCache;
                if (previous != null) {
                    latestCache = new Cache(
                            false,
                            previous.snapshot,
                            previous.account,
                            previous.server,
                            previous.source,
                            previous.gateway,
                            previous.updatedAt,
                            result.getError(),
                            System.currentTimeMillis());
                }
                return;
            }
            AccountSnapshot snapshot = result.getSnapshot();
            nextDelayMs = fullSnapshotActive ? MIN_REFRESH_MS : BACKGROUND_REFRESH_MS;
            latestCache = new Cache(
                    true,
                    snapshot,
                    result.getAccount(""),
                    result.getServer(""),
                    result.getLocalizedSource(),
                    result.getGatewayEndpoint(),
                    result.getUpdatedAt(),
                    "",
                    System.currentTimeMillis());
            persistPreloadSnapshot(snapshot, result, fullSnapshotActive);
        } catch (Exception exception) {
            nextDelayMs = fullSnapshotActive ? MIN_REFRESH_MS : BACKGROUND_REFRESH_MS;
            Cache previous = latestCache;
            if (previous != null) {
                latestCache = new Cache(
                        false,
                        previous.snapshot,
                        previous.account,
                        previous.server,
                        previous.source,
                        previous.gateway,
                        previous.updatedAt,
                        exception.getMessage(),
                        System.currentTimeMillis());
            }
        } finally {
            loading = false;
        }
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
            accountStorageRepository.persistLiveSnapshot(storedSnapshot);
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
}

package com.binance.monitor.ui.account;

import android.content.Context;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.ui.account.model.AccountSnapshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountStatsPreloadManager {

    private static volatile AccountStatsPreloadManager instance;

    private final Mt5BridgeGatewayClient gatewayClient = new Mt5BridgeGatewayClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private static final long MIN_REFRESH_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS;
    private static final long MAX_REFRESH_MS = 15_000L;

    private volatile Cache latestCache;
    private volatile boolean started;
    private volatile boolean loading;
    private volatile int unchangedStreak;
    private volatile long nextDelayMs = MIN_REFRESH_MS;

    private AccountStatsPreloadManager(Context context) {
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
            Mt5BridgeGatewayClient.SnapshotResult result = gatewayClient.fetch(AccountTimeRange.ALL);
            if (!result.isSuccess()) {
                unchangedStreak = 0;
                nextDelayMs = Math.min(MAX_REFRESH_MS, Math.max(MIN_REFRESH_MS * 2L, nextDelayMs));
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
            if (result.isUnchanged()) {
                unchangedStreak = Math.min(8, unchangedStreak + 1);
            } else {
                unchangedStreak = 0;
            }
            nextDelayMs = Math.min(MAX_REFRESH_MS, MIN_REFRESH_MS + unchangedStreak * 1_500L);
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
        } catch (Exception exception) {
            unchangedStreak = 0;
            nextDelayMs = Math.min(MAX_REFRESH_MS, Math.max(MIN_REFRESH_MS * 2L, nextDelayMs));
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

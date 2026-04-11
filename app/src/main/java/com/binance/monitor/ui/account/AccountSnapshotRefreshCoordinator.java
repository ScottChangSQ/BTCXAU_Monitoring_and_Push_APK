/*
 * 账户页快照刷新协调器，统一处理预加载命中、前台进入和主动快照请求链。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

final class AccountSnapshotRefreshCoordinator {

    interface Host {
        boolean isLoading();

        void setLoading(boolean loading);

        boolean isUserLoggedIn();

        boolean isFinishingOrDestroyed();

        boolean isAccountSessionReady();

        @Nullable
        AccountStatsPreloadManager.Cache resolveCurrentSessionCache();

        void applyLoggedOutEmptyState();

        void clearScheduledRefresh();

        void updateOverviewHeader();

        boolean hasRenderableCurrentSessionState();

        boolean shouldKeepRefreshLoop();

        long getDynamicRefreshDelayMs();

        void scheduleNextSnapshot(long delayMs);

        boolean shouldBootstrapRemoteSession();

        void refreshRemoteSessionStatus(boolean requestSnapshotAfter);

        void applyCacheMeta(@NonNull AccountStatsPreloadManager.Cache cache);

        void logConnectionEvent(boolean connected);

        @NonNull
        String buildRefreshSignature(@Nullable AccountSnapshot snapshot,
                                     @Nullable String historyRevision,
                                     boolean connected,
                                     @Nullable String account,
                                     @Nullable String server);

        @NonNull
        String getLastAppliedSnapshotSignature();

        void setLastAppliedSnapshotSignature(@NonNull String signature);

        boolean isOlderThanCurrentSnapshot(long incomingUpdatedAt);

        boolean isLoginCredentialMatched(@Nullable String remoteAccount, @Nullable String remoteServer);

        @NonNull
        AccountSnapshot buildEmptyAccountSnapshot();

        @NonNull
        String normalizeSource(@Nullable String source);

        @NonNull
        String getLoginAccountInput();

        @NonNull
        String getLoginServerInput();

        @NonNull
        String getDefaultAccount();

        @NonNull
        String getDefaultServer();

        void applyConnectedMeta(boolean connected,
                                @NonNull String account,
                                @NonNull String accountName,
                                @NonNull String server,
                                @NonNull String source,
                                @NonNull String gateway,
                                long updatedAt,
                                @NonNull String error);

        void setConnectionStatus(boolean connected);

        boolean onSnapshotApplied(@NonNull String account, @NonNull String server);

        boolean isAwaitingSync();

        void markSyncFailed(@NonNull String message);

        void markAwaitingGatewaySync(@NonNull String message);

        void showLoginSuccessBanner();

        void applySnapshot(@NonNull AccountSnapshot snapshot, boolean remoteConnected);

        boolean shouldApplyFetchedSnapshot(@Nullable AccountSnapshot snapshot,
                                           boolean remoteConnected,
                                           boolean syntheticDisconnectedSnapshot,
                                           @Nullable String incomingHistoryRevision,
                                           @Nullable String requestStartHistoryRevision);

        void adjustRefreshCadence(boolean connected, boolean unchanged);

        @Nullable
        AccountStatsPreloadManager.Cache fetchForUi(@NonNull AccountTimeRange range);

        void executeIo(@NonNull Runnable action);

        void runOnUiThread(@NonNull Runnable action);

        void logWarning(@NonNull String message);
    }

    private final Host host;
    private final AccountSnapshotRequestGuard snapshotRequestGuard = new AccountSnapshotRequestGuard();

    // 创建账户页快照刷新协调器。
    AccountSnapshotRefreshCoordinator(@NonNull Host host) {
        this.host = host;
    }

    // 会话切换或登出时失效旧请求，避免旧回包再改写页面。
    void invalidateSession() {
        snapshotRequestGuard.invalidateSession();
        host.setLoading(false);
    }

    // 预加载缓存一旦可用就先消费，避免页面先抖成离线再被真实快照纠正。
    void applyPreloadedCacheIfAvailable() {
        if (!host.isAccountSessionReady()) {
            host.applyLoggedOutEmptyState();
            return;
        }
        AccountStatsPreloadManager.Cache cache = host.resolveCurrentSessionCache();
        if (cache == null || cache.getSnapshot() == null) {
            return;
        }
        long updateAt = cache.getUpdatedAt() > 0L ? cache.getUpdatedAt() : cache.getFetchedAt();
        if (host.isOlderThanCurrentSnapshot(updateAt)) {
            return;
        }
        host.applyCacheMeta(cache);
        host.updateOverviewHeader();
        host.logConnectionEvent(cache.isConnected());
        String cacheSignature = host.buildRefreshSignature(
                cache.getSnapshot(),
                cache.getHistoryRevision(),
                cache.isConnected(),
                cache.getAccount(),
                cache.getServer()
        );
        if (cacheSignature.equals(host.getLastAppliedSnapshotSignature())) {
            return;
        }
        host.applySnapshot(cache.getSnapshot(), cache.isConnected());
        host.setLastAppliedSnapshotSignature(cacheSignature);
    }

    // 账户页进入前台时只消费已有真值；只有缺少可渲染状态时才重建主链。
    void enterAccountScreen(boolean coldStart) {
        applyPreloadedCacheIfAvailable();
        if (!host.isUserLoggedIn()) {
            host.clearScheduledRefresh();
            host.setConnectionStatus(false);
            host.updateOverviewHeader();
            return;
        }
        if (host.hasRenderableCurrentSessionState()) {
            if (host.shouldKeepRefreshLoop()) {
                host.scheduleNextSnapshot(host.getDynamicRefreshDelayMs());
            }
            host.updateOverviewHeader();
            return;
        }
        if (!coldStart && host.isLoading()) {
            return;
        }
        if (host.shouldBootstrapRemoteSession()) {
            host.refreshRemoteSessionStatus(true);
            return;
        }
        requestForegroundEntrySnapshot();
    }

    // 页面首次进入和回前台时统一触发一次明确刷新。
    void requestForegroundEntrySnapshot() {
        if (!host.isUserLoggedIn()) {
            host.clearScheduledRefresh();
            return;
        }
        host.clearScheduledRefresh();
        if (host.isLoading()) {
            return;
        }
        requestSnapshot();
    }

    // 统一走账户全量快照请求闭环。
    void requestSnapshot() {
        if (!host.isUserLoggedIn()) {
            host.setLoading(false);
            host.setConnectionStatus(false);
            host.clearScheduledRefresh();
            host.updateOverviewHeader();
            return;
        }
        if (host.isLoading()) {
            return;
        }
        host.setLoading(true);
        final AccountSnapshotRequestGuard.RequestToken requestToken = snapshotRequestGuard.openRequest();
        AccountStatsPreloadManager.Cache requestStartCache = host.resolveCurrentSessionCache();
        final String requestStartHistoryRevision = requestStartCache == null
                ? ""
                : trim(requestStartCache.getHistoryRevision());

        host.executeIo(() -> {
            try {
                AccountStatsPreloadManager.Cache remote = host.fetchForUi(AccountTimeRange.ALL);
                AccountSnapshot snapshot;
                boolean syntheticDisconnectedSnapshot = false;
                boolean connected;
                String account;
                String accountName;
                String server;
                String source;
                String gateway;
                long updatedAt;
                String error;

                if (remote != null && remote.isConnected()) {
                    boolean loginMatched = host.isLoginCredentialMatched(remote.getAccount(), remote.getServer());
                    if (loginMatched) {
                        snapshot = remote.getSnapshot();
                        connected = true;
                        account = remote.getAccount().isEmpty()
                                ? resolveFallbackIdentity(host.getLoginAccountInput(), host.getDefaultAccount())
                                : remote.getAccount();
                        accountName = account;
                        server = remote.getServer().isEmpty()
                                ? resolveFallbackServer(host.getLoginServerInput(), host.getDefaultServer())
                                : remote.getServer();
                        source = host.normalizeSource(remote.getSource());
                        gateway = remote.getGateway().isEmpty() ? "--" : remote.getGateway();
                        updatedAt = remote.getUpdatedAt() > 0L ? remote.getUpdatedAt() : System.currentTimeMillis();
                        error = "";
                    } else {
                        snapshot = null;
                        connected = false;
                        account = resolveFallbackIdentity(host.getLoginAccountInput(), host.getDefaultAccount());
                        accountName = account;
                        server = resolveFallbackServer(host.getLoginServerInput(), host.getDefaultServer());
                        source = "登录校验失败";
                        gateway = remote.getGateway().isEmpty() ? "--" : remote.getGateway();
                        updatedAt = System.currentTimeMillis();
                        error = "登录账户或服务器与网关返回不一致";
                    }
                } else {
                    snapshot = host.buildEmptyAccountSnapshot();
                    syntheticDisconnectedSnapshot = true;
                    connected = false;
                    account = resolveFallbackIdentity(host.getLoginAccountInput(), host.getDefaultAccount());
                    accountName = account;
                    server = resolveFallbackServer(host.getLoginServerInput(), host.getDefaultServer());
                    source = remote == null || trim(remote.getSource()).isEmpty()
                            ? "历史数据（网关离线）"
                            : host.normalizeSource(remote.getSource());
                    gateway = remote == null || trim(remote.getGateway()).isEmpty()
                            ? "Gateway offline"
                            : remote.getGateway();
                    updatedAt = remote == null || remote.getUpdatedAt() <= 0L
                            ? System.currentTimeMillis()
                            : remote.getUpdatedAt();
                    error = remote == null ? "网关离线" : remote.getError();
                }

                final AccountSnapshot finalSnapshot = snapshot;
                final boolean finalConnected = connected;
                final String finalAccount = account;
                final String finalAccountName = accountName;
                final String finalServer = server;
                final String finalSource = source;
                final String finalGateway = gateway;
                final long finalUpdatedAt = updatedAt;
                final String finalError = error;
                final String finalHistoryRevision = remote == null ? "" : trim(remote.getHistoryRevision());
                final boolean finalSyntheticDisconnectedSnapshot = syntheticDisconnectedSnapshot;
                final String finalSignature = host.buildRefreshSignature(
                        finalSnapshot,
                        finalHistoryRevision,
                        finalConnected,
                        finalAccount,
                        finalServer
                );

                host.runOnUiThread(() -> {
                    if (!snapshotRequestGuard.shouldApply(requestToken)) {
                        return;
                    }
                    final boolean finalUnchanged = finalSignature.equals(host.getLastAppliedSnapshotSignature());
                    host.applyConnectedMeta(
                            finalConnected,
                            finalAccount,
                            finalAccountName,
                            finalServer,
                            finalSource,
                            finalGateway,
                            finalUpdatedAt,
                            finalError
                    );
                    if (host.isOlderThanCurrentSnapshot(finalUpdatedAt)) {
                        host.setLoading(false);
                        host.adjustRefreshCadence(finalConnected, finalUnchanged);
                        if (host.shouldKeepRefreshLoop()) {
                            host.scheduleNextSnapshot(host.getDynamicRefreshDelayMs());
                        }
                        return;
                    }
                    host.setConnectionStatus(finalConnected);
                    boolean sessionActivatedNow = finalConnected && host.onSnapshotApplied(finalAccount, finalServer);
                    if (!finalConnected && host.isAwaitingSync()) {
                        if ("登录校验失败".equals(finalSource)) {
                            host.markSyncFailed(finalError);
                        } else if ("历史数据（网关离线）".equals(finalSource)) {
                            host.markAwaitingGatewaySync("会话已受理，等待网关上线");
                        }
                    }
                    if (sessionActivatedNow) {
                        host.showLoginSuccessBanner();
                    }
                    host.updateOverviewHeader();
                    host.logConnectionEvent(finalConnected);
                    if (host.shouldApplyFetchedSnapshot(
                            finalSnapshot,
                            finalConnected,
                            finalSyntheticDisconnectedSnapshot,
                            finalHistoryRevision,
                            requestStartHistoryRevision)) {
                        if (finalSnapshot != null) {
                            host.applySnapshot(finalSnapshot, finalConnected);
                        }
                    }
                    host.setLastAppliedSnapshotSignature(finalSignature);
                    host.adjustRefreshCadence(finalConnected, finalUnchanged);
                    host.setLoading(false);
                    if (host.shouldKeepRefreshLoop()) {
                        host.scheduleNextSnapshot(host.getDynamicRefreshDelayMs());
                    } else {
                        host.clearScheduledRefresh();
                    }
                    host.updateOverviewHeader();
                });
            } catch (Exception exception) {
                host.logWarning("AccountStats snapshot refresh failed: " + safeMessage(exception.getMessage()));
                host.runOnUiThread(() -> {
                    if (!snapshotRequestGuard.shouldApply(requestToken) || !host.isLoading()) {
                        return;
                    }
                    host.setLoading(false);
                    if (host.shouldKeepRefreshLoop()) {
                        host.scheduleNextSnapshot(host.getDynamicRefreshDelayMs());
                    } else {
                        host.clearScheduledRefresh();
                    }
                    host.updateOverviewHeader();
                });
            }
        });
    }

    @NonNull
    private static String resolveFallbackIdentity(@Nullable String login, @NonNull String fallback) {
        String safeLogin = trim(login);
        return safeLogin.isEmpty() ? fallback : safeLogin;
    }

    @NonNull
    private static String resolveFallbackServer(@Nullable String server, @NonNull String fallback) {
        String safeServer = trim(server);
        return safeServer.isEmpty() ? fallback : safeServer;
    }

    @NonNull
    private static String safeMessage(@Nullable String message) {
        String safe = trim(message);
        return safe.isEmpty() ? "unknown error" : safe;
    }

    @NonNull
    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

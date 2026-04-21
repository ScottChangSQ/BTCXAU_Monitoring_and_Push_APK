/*
 * 账户快照刷新宿主委托，统一适配快照刷新协调器宿主能力。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

public final class AccountSnapshotRefreshHostDelegate implements AccountSnapshotRefreshCoordinator.Host {

    private final Owner owner;

    public AccountSnapshotRefreshHostDelegate(@NonNull Owner owner) {
        this.owner = owner;
    }

    @Override
    public boolean isLoading() {
        return owner.isLoading();
    }

    @Override
    public void setLoading(boolean loading) {
        owner.setLoading(loading);
    }

    @Override
    public boolean isUserLoggedIn() {
        return owner.isUserLoggedIn();
    }

    @Override
    public boolean isFinishingOrDestroyed() {
        return owner.isFinishingOrDestroyed();
    }

    @Override
    public boolean isAccountSessionReady() {
        return owner.isAccountSessionReady();
    }

    @Nullable
    @Override
    public AccountStatsPreloadManager.Cache resolveCurrentSessionCache() {
        return owner.resolveCurrentSessionCache();
    }

    @Override
    public boolean isStoredSnapshotRestorePending() {
        return owner.isStoredSnapshotRestorePending();
    }

    @Override
    public void setStoredSnapshotRestorePending(boolean pending) {
        owner.setStoredSnapshotRestorePending(pending);
    }

    @Override
    public void onStoredSnapshotRestoreStarted() {
        owner.onStoredSnapshotRestoreStarted();
    }

    @Override
    public void onStoredSnapshotDataReady(@NonNull AccountStatsPreloadManager.Cache cache) {
        owner.onStoredSnapshotDataReady(cache);
    }

    @Override
    public void onStoredSnapshotRestoreMiss() {
        owner.onStoredSnapshotRestoreMiss();
    }

    @Nullable
    @Override
    public AccountStatsPreloadManager.Cache hydrateLatestCacheFromStorage() {
        return owner.hydrateLatestCacheFromStorage();
    }

    @Override
    public boolean isPreloadedCacheForCurrentSession(@Nullable AccountStatsPreloadManager.Cache cache) {
        return owner.isPreloadedCacheForCurrentSession(cache);
    }

    @Override
    public void clearLatestCacheIfCurrent(@Nullable AccountStatsPreloadManager.Cache cache) {
        owner.clearLatestCacheIfCurrent(cache);
    }

    @Override
    public void applyLoggedOutEmptyState() {
        owner.applyLoggedOutEmptyState();
    }

    @Override
    public void clearScheduledRefresh() {
        owner.clearScheduledRefresh();
    }

    @Override
    public void updateOverviewHeader() {
        owner.updateOverviewHeader();
    }

    @Override
    public boolean hasRenderableCurrentSessionState() {
        return owner.hasRenderableCurrentSessionState();
    }

    @Override
    public boolean hasRenderableHistorySections(@Nullable AccountSnapshot snapshot) {
        return owner.hasRenderableHistorySections(snapshot);
    }

    @Override
    public boolean shouldKeepRefreshLoop() {
        return owner.shouldKeepRefreshLoop();
    }

    @Override
    public long getDynamicRefreshDelayMs() {
        return owner.getDynamicRefreshDelayMs();
    }

    @Override
    public void scheduleNextSnapshot(long delayMs) {
        owner.scheduleNextSnapshot(delayMs);
    }

    @Override
    public boolean shouldBootstrapRemoteSession() {
        return owner.shouldBootstrapRemoteSession();
    }

    @Override
    public void refreshRemoteSessionStatus(boolean requestSnapshotAfter) {
        owner.refreshRemoteSessionStatus(requestSnapshotAfter);
    }

    @Override
    public void applyCacheMeta(@NonNull AccountStatsPreloadManager.Cache cache) {
        owner.applyCacheMeta(cache);
    }

    @Override
    public void logConnectionEvent(boolean connected) {
        owner.logConnectionEvent(connected);
    }

    @NonNull
    @Override
    public String buildRefreshSignature(@Nullable AccountSnapshot snapshot,
                                        @Nullable String historyRevision,
                                        boolean connected,
                                        @Nullable String account,
                                        @Nullable String server) {
        return owner.buildRefreshSignature(snapshot, historyRevision, connected, account, server);
    }

    @NonNull
    @Override
    public String getLastAppliedSnapshotSignature() {
        return owner.getLastAppliedSnapshotSignature();
    }

    @Override
    public void setLastAppliedSnapshotSignature(@NonNull String signature) {
        owner.setLastAppliedSnapshotSignature(signature);
    }

    @Override
    public boolean isOlderThanCurrentSnapshot(long incomingUpdatedAt) {
        return owner.isOlderThanCurrentSnapshot(incomingUpdatedAt);
    }

    @Override
    public boolean isLoginCredentialMatched(@Nullable String remoteAccount, @Nullable String remoteServer) {
        return owner.isLoginCredentialMatched(remoteAccount, remoteServer);
    }

    @NonNull
    @Override
    public String normalizeSource(@Nullable String source) {
        return owner.normalizeSource(source);
    }

    @NonNull
    @Override
    public String getLoginAccountInput() {
        return owner.getLoginAccountInput();
    }

    @NonNull
    @Override
    public String getLoginServerInput() {
        return owner.getLoginServerInput();
    }

    @NonNull
    @Override
    public String getDefaultAccount() {
        return owner.getDefaultAccount();
    }

    @NonNull
    @Override
    public String getDefaultServer() {
        return owner.getDefaultServer();
    }

    @Override
    public void applyConnectedMeta(boolean connected,
                                   @NonNull String account,
                                   @NonNull String accountName,
                                   @NonNull String server,
                                   @NonNull String source,
                                   @NonNull String gateway,
                                   long updatedAt,
                                   @NonNull String error) {
        owner.applyConnectedMeta(connected, account, accountName, server, source, gateway, updatedAt, error);
    }

    @Override
    public void setConnectionStatus(boolean connected) {
        owner.setConnectionStatus(connected);
    }

    @Override
    public boolean onSnapshotApplied(@NonNull String account, @NonNull String server) {
        return owner.onSnapshotApplied(account, server);
    }

    @Override
    public boolean isAwaitingSync() {
        return owner.isAwaitingSync();
    }

    @Override
    public void markSyncFailed(@NonNull String message) {
        owner.markSyncFailed(message);
    }

    @Override
    public void markAwaitingGatewaySync(@NonNull String message) {
        owner.markAwaitingGatewaySync(message);
    }

    @Override
    public void showLoginSuccessBanner() {
        owner.showLoginSuccessBanner();
    }

    @Override
    public void applySnapshot(@NonNull AccountSnapshot snapshot, boolean remoteConnected) {
        owner.applySnapshot(snapshot, remoteConnected);
    }

    @Override
    public boolean shouldApplyFetchedSnapshot(@Nullable AccountSnapshot snapshot,
                                              boolean remoteConnected,
                                              @Nullable String incomingHistoryRevision,
                                              @Nullable String requestStartHistoryRevision) {
        return owner.shouldApplyFetchedSnapshot(snapshot,
                remoteConnected,
                incomingHistoryRevision,
                requestStartHistoryRevision);
    }

    @Override
    public void adjustRefreshCadence(boolean connected, boolean unchanged) {
        owner.adjustRefreshCadence(connected, unchanged);
    }

    @Nullable
    @Override
    public AccountStatsPreloadManager.Cache fetchSnapshotForUiForIdentity(@NonNull String expectedAccount,
                                                                          @NonNull String expectedServer) {
        return owner.fetchSnapshotForUiForIdentity(expectedAccount, expectedServer);
    }

    @Nullable
    @Override
    public AccountStatsPreloadManager.Cache fetchFullForUi(@NonNull AccountTimeRange range) {
        return owner.fetchFullForUi(range);
    }

    @Override
    public void requestFullRefreshInBackground() {
        owner.requestFullRefreshInBackground();
    }

    @Override
    public void executeIo(@NonNull Runnable action) {
        owner.executeIo(action);
    }

    @Override
    public void runOnUiThread(@NonNull Runnable action) {
        owner.runOnUiThread(action);
    }

    @Override
    public void logWarning(@NonNull String message) {
        owner.logWarning(message);
    }

    public interface Owner {
        boolean isLoading();

        void setLoading(boolean loading);

        boolean isUserLoggedIn();

        boolean isFinishingOrDestroyed();

        boolean isAccountSessionReady();

        @Nullable
        AccountStatsPreloadManager.Cache resolveCurrentSessionCache();

        boolean isStoredSnapshotRestorePending();

        void setStoredSnapshotRestorePending(boolean pending);

        void onStoredSnapshotRestoreStarted();

        void onStoredSnapshotDataReady(@NonNull AccountStatsPreloadManager.Cache cache);

        void onStoredSnapshotRestoreMiss();

        @Nullable
        AccountStatsPreloadManager.Cache hydrateLatestCacheFromStorage();

        boolean isPreloadedCacheForCurrentSession(@Nullable AccountStatsPreloadManager.Cache cache);

        void clearLatestCacheIfCurrent(@Nullable AccountStatsPreloadManager.Cache cache);

        void applyLoggedOutEmptyState();

        void clearScheduledRefresh();

        void updateOverviewHeader();

        boolean hasRenderableCurrentSessionState();

        boolean hasRenderableHistorySections(@Nullable AccountSnapshot snapshot);

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
                                           @Nullable String incomingHistoryRevision,
                                           @Nullable String requestStartHistoryRevision);

        void adjustRefreshCadence(boolean connected, boolean unchanged);

        @Nullable
        AccountStatsPreloadManager.Cache fetchSnapshotForUiForIdentity(@NonNull String expectedAccount,
                                                                       @NonNull String expectedServer);

        @Nullable
        AccountStatsPreloadManager.Cache fetchFullForUi(@NonNull AccountTimeRange range);

        void requestFullRefreshInBackground();

        void executeIo(@NonNull Runnable action);

        void runOnUiThread(@NonNull Runnable action);

        void logWarning(@NonNull String message);
    }
}

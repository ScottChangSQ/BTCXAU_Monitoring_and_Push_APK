/*
 * 本地会话摘要读取快照，显式区分正常、空缓存和读取失败三种状态。
 * 供 UI 层和会话协调器统一判断，不再把损坏缓存误当成空缓存。
 */
package com.binance.monitor.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SessionSummarySnapshot {

    public enum LoadState {
        EMPTY,
        READY,
        STORAGE_FAILURE
    }

    private final LoadState loadState;
    private final boolean sessionMarkedActive;
    private final RemoteAccountProfile activeAccount;
    private final List<RemoteAccountProfile> savedAccounts;
    private final String draftAccount;
    private final String draftServer;
    private final String storageError;

    private SessionSummarySnapshot(@NonNull LoadState loadState,
                                   boolean sessionMarkedActive,
                                   @Nullable RemoteAccountProfile activeAccount,
                                   @Nullable List<RemoteAccountProfile> savedAccounts,
                                   @Nullable String draftAccount,
                                   @Nullable String draftServer,
                                   @Nullable String storageError) {
        this.loadState = loadState;
        this.sessionMarkedActive = sessionMarkedActive;
        this.activeAccount = activeAccount;
        this.savedAccounts = savedAccounts == null ? new ArrayList<>() : new ArrayList<>(savedAccounts);
        this.draftAccount = draftAccount == null ? "" : draftAccount.trim();
        this.draftServer = draftServer == null ? "" : draftServer.trim();
        this.storageError = storageError == null ? "" : storageError.trim();
    }

    // 构建空缓存快照。
    @NonNull
    public static SessionSummarySnapshot empty() {
        return new SessionSummarySnapshot(LoadState.EMPTY, false, null, Collections.emptyList(), "", "", "");
    }

    // 构建正常可读的会话摘要快照。
    @NonNull
    public static SessionSummarySnapshot ready(boolean sessionMarkedActive,
                                               @Nullable RemoteAccountProfile activeAccount,
                                               @Nullable List<RemoteAccountProfile> savedAccounts,
                                               @Nullable String draftAccount,
                                               @Nullable String draftServer) {
        return new SessionSummarySnapshot(
                LoadState.READY,
                sessionMarkedActive,
                activeAccount,
                savedAccounts,
                draftAccount,
                draftServer,
                ""
        );
    }

    // 构建读取失败快照，只暴露失败语义，不再伪装成空缓存。
    @NonNull
    public static SessionSummarySnapshot storageFailure(@Nullable String storageError) {
        return new SessionSummarySnapshot(
                LoadState.STORAGE_FAILURE,
                false,
                null,
                Collections.emptyList(),
                "",
                "",
                storageError
        );
    }

    @NonNull
    public LoadState getLoadState() {
        return loadState;
    }

    public boolean isEmpty() {
        return loadState == LoadState.EMPTY;
    }

    public boolean hasStorageFailure() {
        return loadState == LoadState.STORAGE_FAILURE;
    }

    public boolean isSessionMarkedActive() {
        return sessionMarkedActive;
    }

    @Nullable
    public RemoteAccountProfile getActiveAccount() {
        return activeAccount;
    }

    @NonNull
    public List<RemoteAccountProfile> getSavedAccountsSnapshot() {
        return new ArrayList<>(savedAccounts);
    }

    @NonNull
    public String getDraftAccount() {
        return draftAccount;
    }

    @NonNull
    public String getDraftServer() {
        return draftServer;
    }

    @NonNull
    public String getStorageError() {
        return storageError;
    }
}

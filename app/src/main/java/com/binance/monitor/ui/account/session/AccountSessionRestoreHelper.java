/*
 * 账户页会话恢复助手，负责把安全会话摘要和本地 UI 偏好合成为页面初始化状态。
 * 供 AccountStatsBridgeActivity 复用，避免页面类自己堆叠会话恢复细节。
 */
package com.binance.monitor.ui.account.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.security.SessionSummarySnapshot;

import java.util.ArrayList;
import java.util.List;

public final class AccountSessionRestoreHelper {

    private AccountSessionRestoreHelper() {
    }

    // 根据会话摘要和本地偏好恢复账户页初始化所需的会话字段。
    @NonNull
    public static RestoreResult restore(@NonNull RestoreRequest request) {
        SessionSummarySnapshot sessionSummary = request.getSessionSummary();
        RemoteAccountProfile activeAccount = sessionSummary.hasStorageFailure()
                ? null
                : sanitizeProfile(sessionSummary.getActiveAccount());
        boolean cachedSessionActive = !sessionSummary.hasStorageFailure()
                && sessionSummary.isSessionMarkedActive()
                && activeAccount != null;
        List<RemoteAccountProfile> savedAccounts = sessionSummary.hasStorageFailure()
                ? new ArrayList<>()
                : sanitizeProfiles(sessionSummary.getSavedAccountsSnapshot());
        boolean userLoggedIn = (request.isPersistedLoginEnabled() || cachedSessionActive)
                && request.isAccountSessionActive();
        String loginAccountInput = firstNonEmpty(
                sessionSummary.getDraftAccount(),
                request.getPersistedLoginAccount(),
                activeAccount == null ? "" : activeAccount.getLogin(),
                request.getDefaultLoginAccount()
        );
        String loginServerInput = firstNonEmpty(
                sessionSummary.getDraftServer(),
                request.getPersistedLoginServer(),
                activeAccount == null ? "" : activeAccount.getServer(),
                request.getDefaultLoginServer()
        );
        return new RestoreResult(
                userLoggedIn,
                activeAccount,
                savedAccounts,
                loginAccountInput,
                loginServerInput,
                sessionSummary.getStorageError()
        );
    }

    // 只接受完整账号摘要，缺关键字段的对象不再回灌到页面。
    @Nullable
    private static RemoteAccountProfile sanitizeProfile(@Nullable RemoteAccountProfile profile) {
        if (profile == null) {
            return null;
        }
        if (trim(profile.getProfileId()).isEmpty()
                || trim(profile.getLogin()).isEmpty()
                || trim(profile.getServer()).isEmpty()) {
            return null;
        }
        return profile;
    }

    // 过滤掉不完整的账号摘要，避免列表里混入半残缺对象。
    @NonNull
    private static List<RemoteAccountProfile> sanitizeProfiles(@Nullable List<RemoteAccountProfile> profiles) {
        List<RemoteAccountProfile> sanitized = new ArrayList<>();
        if (profiles == null) {
            return sanitized;
        }
        for (RemoteAccountProfile profile : profiles) {
            RemoteAccountProfile safeProfile = sanitizeProfile(profile);
            if (safeProfile != null) {
                sanitized.add(safeProfile);
            }
        }
        return sanitized;
    }

    @NonNull
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String trimmed = trim(value);
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    @NonNull
    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public static final class RestoreRequest {
        private final SessionSummarySnapshot sessionSummary;
        private final boolean persistedLoginEnabled;
        private final boolean accountSessionActive;
        private final String persistedLoginAccount;
        private final String persistedLoginServer;
        private final String defaultLoginAccount;
        private final String defaultLoginServer;

        public RestoreRequest(@Nullable SessionSummarySnapshot sessionSummary,
                              boolean persistedLoginEnabled,
                              boolean accountSessionActive,
                              @Nullable String persistedLoginAccount,
                              @Nullable String persistedLoginServer,
                              @Nullable String defaultLoginAccount,
                              @Nullable String defaultLoginServer) {
            this.sessionSummary = sessionSummary == null ? SessionSummarySnapshot.empty() : sessionSummary;
            this.persistedLoginEnabled = persistedLoginEnabled;
            this.accountSessionActive = accountSessionActive;
            this.persistedLoginAccount = persistedLoginAccount == null ? "" : persistedLoginAccount.trim();
            this.persistedLoginServer = persistedLoginServer == null ? "" : persistedLoginServer.trim();
            this.defaultLoginAccount = defaultLoginAccount == null ? "" : defaultLoginAccount.trim();
            this.defaultLoginServer = defaultLoginServer == null ? "" : defaultLoginServer.trim();
        }

        @NonNull
        public SessionSummarySnapshot getSessionSummary() {
            return sessionSummary;
        }

        public boolean isPersistedLoginEnabled() {
            return persistedLoginEnabled;
        }

        public boolean isAccountSessionActive() {
            return accountSessionActive;
        }

        @NonNull
        public String getPersistedLoginAccount() {
            return persistedLoginAccount;
        }

        @NonNull
        public String getPersistedLoginServer() {
            return persistedLoginServer;
        }

        @NonNull
        public String getDefaultLoginAccount() {
            return defaultLoginAccount;
        }

        @NonNull
        public String getDefaultLoginServer() {
            return defaultLoginServer;
        }
    }

    public static final class RestoreResult {
        private final boolean userLoggedIn;
        private final RemoteAccountProfile activeSessionAccount;
        private final List<RemoteAccountProfile> savedSessionAccounts;
        private final String loginAccountInput;
        private final String loginServerInput;
        private final String storageError;

        public RestoreResult(boolean userLoggedIn,
                             @Nullable RemoteAccountProfile activeSessionAccount,
                             @Nullable List<RemoteAccountProfile> savedSessionAccounts,
                             @Nullable String loginAccountInput,
                             @Nullable String loginServerInput,
                             @Nullable String storageError) {
            this.userLoggedIn = userLoggedIn;
            this.activeSessionAccount = activeSessionAccount;
            this.savedSessionAccounts = savedSessionAccounts == null ? new ArrayList<>() : new ArrayList<>(savedSessionAccounts);
            this.loginAccountInput = loginAccountInput == null ? "" : loginAccountInput.trim();
            this.loginServerInput = loginServerInput == null ? "" : loginServerInput.trim();
            this.storageError = storageError == null ? "" : storageError.trim();
        }

        public boolean isUserLoggedIn() {
            return userLoggedIn;
        }

        @Nullable
        public RemoteAccountProfile getActiveSessionAccount() {
            return activeSessionAccount;
        }

        @NonNull
        public List<RemoteAccountProfile> getSavedSessionAccounts() {
            return new ArrayList<>(savedSessionAccounts);
        }

        @NonNull
        public String getLoginAccountInput() {
            return loginAccountInput;
        }

        @NonNull
        public String getLoginServerInput() {
            return loginServerInput;
        }

        @NonNull
        public String getStorageError() {
            return storageError;
        }
    }
}

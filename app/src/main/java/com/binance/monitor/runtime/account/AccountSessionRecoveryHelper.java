/*
 * 账户远程会话恢复助手，负责把本地安全会话摘要与服务端 session status 对齐。
 * 供 MonitorService 在前台切回或冷启动时复用，避免服务层继续维护账户第二副本职责。
 */
package com.binance.monitor.runtime.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfileDeduplicationHelper;
import com.binance.monitor.data.model.v2.session.SessionStatusPayload;
import com.binance.monitor.data.remote.v2.GatewayV2SessionClient;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionSummarySnapshot;

import java.util.List;

public class AccountSessionRecoveryHelper {

    private final GatewayV2SessionClient sessionClient;
    private final SecureSessionPrefs secureSessionPrefs;
    private final ConfigManager configManager;
    private final AccountStatsPreloadManager accountStatsPreloadManager;
    @Nullable
    private final LogManager logManager;

    public AccountSessionRecoveryHelper(@NonNull GatewayV2SessionClient sessionClient,
                                        @NonNull SecureSessionPrefs secureSessionPrefs,
                                        @NonNull ConfigManager configManager,
                                        @NonNull AccountStatsPreloadManager accountStatsPreloadManager,
                                        @Nullable LogManager logManager) {
        this.sessionClient = sessionClient;
        this.secureSessionPrefs = secureSessionPrefs;
        this.configManager = configManager;
        this.accountStatsPreloadManager = accountStatsPreloadManager;
        this.logManager = logManager;
    }

    // 对齐本地与服务端远程会话状态；只有真正改动当前会话链时才要求 UI 做一次强刷新。
    @NonNull
    public RecoveryResult reconcileRemoteSession() {
        if (!configManager.isAccountSessionActive()) {
            return RecoveryResult.NO_CHANGE;
        }
        SessionSummarySnapshot localSummary = secureSessionPrefs.loadSessionSummary();
        if (localSummary.hasStorageFailure()) {
            warn("远程会话恢复已跳过: " + localSummary.getStorageError());
            return RecoveryResult.NO_CHANGE;
        }
        RemoteAccountProfile localActiveAccount = sanitizeCompleteProfile(localSummary.getActiveAccount());
        if (localActiveAccount == null) {
            return RecoveryResult.NO_CHANGE;
        }
        try {
            sessionClient.resetTransport();
            SessionStatusPayload status = sessionClient.fetchStatus();
            if (status == null || !status.isOk()) {
                return RecoveryResult.NO_CHANGE;
            }
            RemoteAccountProfile currentRemoteActiveAccount = sanitizeCompleteProfile(status.getActiveAccount());
            if (currentRemoteActiveAccount != null
                    && matchesSessionIdentity(currentRemoteActiveAccount, localActiveAccount)) {
                secureSessionPrefs.saveSession(
                        currentRemoteActiveAccount,
                        RemoteAccountProfileDeduplicationHelper.deduplicate(status.getSavedAccounts()),
                        true
                );
                return RecoveryResult.SESSION_SUMMARY_SYNCED;
            }
            settleRemoteLoggedOutLocally(
                    RemoteAccountProfileDeduplicationHelper.deduplicate(status.getSavedAccounts()),
                    localSummary,
                    localActiveAccount
            );
            return RecoveryResult.ACCOUNT_MISMATCH;
        } catch (Exception exception) {
            warn("远程会话恢复失败: " + exception.getMessage());
            return RecoveryResult.NO_CHANGE;
        }
    }

    // 服务端已确认 logged_out 且无法恢复同账号时，本地必须同步退回离线态并清空正式账户运行态。
    private void settleRemoteLoggedOutLocally(@NonNull List<RemoteAccountProfile> savedAccounts,
                                              @NonNull SessionSummarySnapshot localSummary,
                                              @NonNull RemoteAccountProfile localActiveAccount) {
        configManager.setAccountSessionActive(false);
        secureSessionPrefs.saveSession(null, savedAccounts, false);
        secureSessionPrefs.saveDraftIdentity(
                firstNonEmpty(localSummary.getDraftAccount(), localActiveAccount.getLogin()),
                firstNonEmpty(localSummary.getDraftServer(), localActiveAccount.getServer())
        );
        accountStatsPreloadManager.clearAccountRuntimeState(localActiveAccount.getLogin(), localActiveAccount.getServer());
    }

    // profileId 命中时按 profileId 收口；否则回退到 login+server 的稳定身份。
    private boolean matchesSessionIdentity(@NonNull RemoteAccountProfile first,
                                           @NonNull RemoteAccountProfile second) {
        String firstProfileId = trim(first.getProfileId());
        String secondProfileId = trim(second.getProfileId());
        if (!firstProfileId.isEmpty() && !secondProfileId.isEmpty()) {
            return firstProfileId.equalsIgnoreCase(secondProfileId);
        }
        return trim(first.getLogin()).equalsIgnoreCase(trim(second.getLogin()))
                && trim(first.getServer()).equalsIgnoreCase(trim(second.getServer()));
    }

    // 只接受 profileId/login/server 完整的账号摘要，避免脏 session 被再次写回本地。
    @Nullable
    private RemoteAccountProfile sanitizeCompleteProfile(@Nullable RemoteAccountProfile profile) {
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

    // 用于草稿回填时优先保留已有非空值。
    @NonNull
    private String firstNonEmpty(@Nullable String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String safeValue = trim(value);
            if (!safeValue.isEmpty()) {
                return safeValue;
            }
        }
        return "";
    }

    @NonNull
    private String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private void warn(@NonNull String message) {
        if (logManager != null) {
            logManager.warn(message);
        }
    }

    public enum RecoveryResult {
        NO_CHANGE(false),
        SESSION_SUMMARY_SYNCED(false),
        ACCOUNT_MISMATCH(true);

        private final boolean requiresUiRefresh;

        RecoveryResult(boolean requiresUiRefresh) {
            this.requiresUiRefresh = requiresUiRefresh;
        }

        public boolean requiresUiRefresh() {
            return requiresUiRefresh;
        }
    }
}

/*
 * 远程账户会话协调器，负责把公钥拉取、加密登录、账号切换和页面收口串成一条链。
 * 它只负责会话和缓存清理，不直接依赖具体页面控件，方便单测锁住关键状态边界。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.session.SessionPublicKeyPayload;
import com.binance.monitor.data.model.v2.session.SessionReceipt;
import com.binance.monitor.data.model.v2.session.SessionStatusPayload;
import com.binance.monitor.security.SessionCredentialEncryptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AccountRemoteSessionCoordinator {

    public interface SessionGateway {
        SessionPublicKeyPayload fetchPublicKey() throws Exception;

        SessionReceipt login(SessionCredentialEncryptor.LoginEnvelope envelope, boolean saveAccount) throws Exception;

        SessionReceipt switchAccount(String profileId, String requestId) throws Exception;

        SessionReceipt logout(String requestId) throws Exception;

        SessionStatusPayload fetchStatus() throws Exception;
    }

    public interface CredentialEnvelopeFactory {
        SessionCredentialEncryptor.LoginEnvelope encrypt(String publicKeyPem,
                                                        String keyId,
                                                        String login,
                                                        String password,
                                                        String server,
                                                        boolean remember,
                                                        long clientTime) throws Exception;
    }

    public interface CacheResetter {
        void clearAccountSnapshot();

        void clearTradeHistory();

        void clearChartTradeDrafts();

        void clearPendingExpandedState();

        void clearPositionExpandedState();

        void clearTradeExpandedState();
    }

    public interface SessionSummaryStore {
        void saveSession(@Nullable RemoteAccountProfile activeAccount,
                         @NonNull List<RemoteAccountProfile> savedAccounts,
                         boolean active);

        void clearSession();

        @NonNull
        List<RemoteAccountProfile> getSavedAccountsSnapshot();
    }

    public interface RequestIdGenerator {
        String nextRequestId();
    }

    public static final class LoginRequest {
        private final String login;
        private final String password;
        private final String server;
        private final boolean remember;
        private final long clientTime;

        // 构造登录请求参数。
        public LoginRequest(String login,
                            String password,
                            String server,
                            boolean remember,
                            long clientTime) {
            this.login = login == null ? "" : login.trim();
            this.password = password == null ? "" : password;
            this.server = server == null ? "" : server.trim();
            this.remember = remember;
            this.clientTime = clientTime;
        }

        public String getLogin() {
            return login;
        }

        public String getPassword() {
            return password;
        }

        public String getServer() {
            return server;
        }

        public boolean isRemember() {
            return remember;
        }

        public long getClientTime() {
            return clientTime;
        }
    }

    public static final class SessionActionResult {
        private final SessionReceipt receipt;
        private final RemoteAccountProfile activeAccount;
        private final List<RemoteAccountProfile> savedAccounts;

        // 封装一次会话动作的解析结果。
        public SessionActionResult(@Nullable SessionReceipt receipt,
                                   @Nullable RemoteAccountProfile activeAccount,
                                   @Nullable List<RemoteAccountProfile> savedAccounts) {
            this.receipt = receipt;
            this.activeAccount = activeAccount;
            this.savedAccounts = savedAccounts == null ? new ArrayList<>() : new ArrayList<>(savedAccounts);
        }

        public SessionReceipt getReceipt() {
            return receipt;
        }

        public RemoteAccountProfile getActiveAccount() {
            return activeAccount;
        }

        public List<RemoteAccountProfile> getSavedAccounts() {
            return Collections.unmodifiableList(savedAccounts);
        }
    }

    private final AccountSessionStateMachine stateMachine;
    private final SessionGateway sessionGateway;
    private final CredentialEnvelopeFactory credentialFactory;
    private final CacheResetter cacheResetter;
    private final SessionSummaryStore sessionSummaryStore;
    private final RequestIdGenerator requestIdGenerator;

    private String pendingProfileId = "";
    private String pendingLogin = "";
    private String pendingServer = "";
    private boolean awaitingSync;

    // 创建远程会话协调器。
    public AccountRemoteSessionCoordinator(@NonNull AccountSessionStateMachine stateMachine,
                                           @NonNull SessionGateway sessionGateway,
                                           @NonNull CredentialEnvelopeFactory credentialFactory,
                                           @NonNull CacheResetter cacheResetter,
                                           @NonNull SessionSummaryStore sessionSummaryStore,
                                           @NonNull RequestIdGenerator requestIdGenerator) {
        this.stateMachine = stateMachine;
        this.sessionGateway = sessionGateway;
        this.credentialFactory = credentialFactory;
        this.cacheResetter = cacheResetter;
        this.sessionSummaryStore = sessionSummaryStore;
        this.requestIdGenerator = requestIdGenerator;
    }

    // 提交新账号登录，服务器受理成功后进入同步中而不是直接 active。
    public SessionActionResult loginNewAccount(@NonNull LoginRequest request) throws Exception {
        validateLoginRequest(request);
        stateMachine.moveTo(AccountSessionStateMachine.AccountSessionUiState.ENCRYPTING, "正在安全加密");
        SessionPublicKeyPayload publicKey = requirePublicKey(sessionGateway.fetchPublicKey());
        SessionCredentialEncryptor.LoginEnvelope envelope = credentialFactory.encrypt(
                publicKey.getPublicKeyPem(),
                publicKey.getKeyId(),
                request.getLogin(),
                request.getPassword(),
                request.getServer(),
                request.isRemember(),
                request.getClientTime()
        );
        stateMachine.moveTo(AccountSessionStateMachine.AccountSessionUiState.SUBMITTING, "正在提交登录");
        SessionReceipt receipt = sessionGateway.login(envelope, request.isRemember());
        return handleAcceptedReceipt(receipt, "正在同步账户数据", request.getLogin(), request.getServer());
    }

    // 切换到服务器已保存账号，成功后立即清理旧缓存并进入同步中。
    public SessionActionResult switchSavedAccount(@NonNull String profileId) throws Exception {
        String safeProfileId = profileId == null ? "" : profileId.trim();
        if (safeProfileId.isEmpty()) {
            throw new IllegalArgumentException("profileId 不能为空");
        }
        RemoteAccountProfile fallbackProfile = findSavedProfileById(safeProfileId, sessionSummaryStore.getSavedAccountsSnapshot());
        stateMachine.moveTo(AccountSessionStateMachine.AccountSessionUiState.SWITCHING, "正在切换账户");
        SessionReceipt receipt = sessionGateway.switchAccount(safeProfileId, requestIdGenerator.nextRequestId());
        return handleAcceptedReceipt(
                receipt,
                "正在同步账户数据",
                fallbackProfile == null ? "" : fallbackProfile.getLogin(),
                fallbackProfile == null ? "" : fallbackProfile.getServer()
        );
    }

    // 退出当前远程账号，并同步清理本地缓存和状态。
    public SessionActionResult logoutCurrent() throws Exception {
        SessionReceipt receipt = sessionGateway.logout(requestIdGenerator.nextRequestId());
        if (receipt == null || !receipt.isOk()) {
            stateMachine.markFailed(resolveFailureMessage(receipt, "退出登录失败"));
            return new SessionActionResult(receipt, null, Collections.emptyList());
        }
        clearCachesForAccountChange();
        sessionSummaryStore.clearSession();
        awaitingSync = false;
        pendingProfileId = "";
        pendingLogin = "";
        pendingServer = "";
        stateMachine.reset();
        return new SessionActionResult(receipt, null, Collections.emptyList());
    }

    // 在页面拿到新快照后调用，只有匹配当前等待的账号时才转为 active。
    public boolean onSnapshotApplied(@Nullable String account, @Nullable String server) {
        if (!awaitingSync) {
            return false;
        }
        String safeAccount = account == null ? "" : account.trim();
        String safeServer = server == null ? "" : server.trim();
        if (pendingLogin.isEmpty()) {
            // server 往往会被多个账号共用，只有 server 没有 login 时仍然不能安全收口。
            return false;
        }
        if (!pendingLogin.equalsIgnoreCase(safeAccount)) {
            return false;
        }
        if (!pendingServer.isEmpty() && !pendingServer.equalsIgnoreCase(safeServer)) {
            return false;
        }
        awaitingSync = false;
        stateMachine.markActive(pendingProfileId, "账户数据已对齐");
        return true;
    }

    // 当同步阶段明确失败时，统一收口到 failed。
    public void markSyncFailed(@Nullable String message) {
        awaitingSync = false;
        stateMachine.markFailed(message == null ? "账户同步失败" : message);
    }

    // 返回当前是否仍在等待新快照。
    public boolean isAwaitingSync() {
        return awaitingSync;
    }

    // 返回当前等待同步的 profileId。
    public String getPendingProfileId() {
        return pendingProfileId;
    }

    // 返回当前等待同步的 login。
    public String getPendingLogin() {
        return pendingLogin;
    }

    // 返回当前等待同步的 server。
    public String getPendingServer() {
        return pendingServer;
    }

    private SessionActionResult handleAcceptedReceipt(@Nullable SessionReceipt receipt,
                                                      @NonNull String syncingMessage,
                                                      @Nullable String fallbackLogin,
                                                      @Nullable String fallbackServer) throws Exception {
        if (receipt == null || !receipt.isOk()) {
            stateMachine.markFailed(resolveFailureMessage(receipt, "会话请求失败"));
            return new SessionActionResult(receipt, null, Collections.emptyList());
        }
        SessionStatusPayload status = null;
        try {
            status = sessionGateway.fetchStatus();
        } catch (Exception ignored) {
            // 接口已受理成功时，状态补拉失败不应把整次切换判成失败。
            // 这里先用 receipt 里的账号摘要进入 syncing，等待后续快照真值收口。
        }
        List<RemoteAccountProfile> savedAccounts = status == null
                ? new ArrayList<>(sessionSummaryStore.getSavedAccountsSnapshot())
                : new ArrayList<>(status.getSavedAccounts());
        RemoteAccountProfile activeAccount = hydrateActiveAccountFromSavedAccounts(
                resolveActiveAccount(receipt, status),
                savedAccounts
        );
        activeAccount = withIdentityFallback(activeAccount, fallbackLogin, fallbackServer);
        if (activeAccount == null) {
            awaitingSync = false;
            stateMachine.markFailed("会话账号摘要缺失");
            throw new IllegalStateException("会话账号摘要缺失");
        }
        clearCachesForAccountChange();
        sessionSummaryStore.saveSession(activeAccount, savedAccounts, true);
        pendingProfileId = activeAccount == null ? "" : activeAccount.getProfileId();
        pendingLogin = activeAccount == null ? "" : activeAccount.getLogin();
        pendingServer = activeAccount == null ? "" : activeAccount.getServer();
        awaitingSync = true;
        stateMachine.markSyncing(pendingProfileId, syncingMessage);
        return new SessionActionResult(receipt, activeAccount, savedAccounts);
    }

    @Nullable
    private static RemoteAccountProfile withIdentityFallback(@Nullable RemoteAccountProfile profile,
                                                             @Nullable String fallbackLogin,
                                                             @Nullable String fallbackServer) {
        if (profile == null) {
            return null;
        }
        return new RemoteAccountProfile(
                profile.getProfileId(),
                emptyToFallback(profile.getLogin(), fallbackLogin),
                profile.getLoginMasked(),
                emptyToFallback(profile.getServer(), fallbackServer),
                profile.getDisplayName(),
                profile.isActive(),
                profile.getState()
        );
    }

    private void clearCachesForAccountChange() {
        cacheResetter.clearAccountSnapshot();
        cacheResetter.clearTradeHistory();
        cacheResetter.clearChartTradeDrafts();
        cacheResetter.clearPendingExpandedState();
        cacheResetter.clearPositionExpandedState();
        cacheResetter.clearTradeExpandedState();
    }

    @NonNull
    private static SessionPublicKeyPayload requirePublicKey(@Nullable SessionPublicKeyPayload payload) {
        if (payload == null || !payload.isOk()) {
            throw new IllegalStateException("拉取会话公钥失败");
        }
        if (payload.getPublicKeyPem().trim().isEmpty()) {
            throw new IllegalStateException("会话公钥为空");
        }
        return payload;
    }

    private static void validateLoginRequest(@Nullable LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        if (request.getLogin().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (request.getPassword().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (request.getServer().isEmpty()) {
            throw new IllegalArgumentException("服务器不能为空");
        }
    }

    private static String resolveFailureMessage(@Nullable SessionReceipt receipt, @NonNull String fallback) {
        if (receipt == null) {
            return fallback;
        }
        if (!receipt.getMessage().trim().isEmpty()) {
            return receipt.getMessage();
        }
        if (!receipt.getErrorCode().trim().isEmpty()) {
            return receipt.getErrorCode();
        }
        return fallback;
    }

    @Nullable
    private static RemoteAccountProfile resolveActiveAccount(@Nullable SessionReceipt receipt,
                                                             @Nullable SessionStatusPayload status) {
        RemoteAccountProfile statusAccount = status == null ? null : status.getActiveAccount();
        RemoteAccountProfile receiptAccount = receipt == null ? null : receipt.getAccount();
        if (statusAccount != null && receiptAccount != null) {
            // 会话操作已被服务器受理时，receipt 才是本次动作目标账号，不能被短暂旧 status 覆盖。
            if (isAccountIdentityConflict(statusAccount, receiptAccount)) {
                return receiptAccount;
            }
            return mergeProfile(receiptAccount, statusAccount);
        }
        if (receiptAccount != null) {
            return receiptAccount;
        }
        if (statusAccount != null) {
            return statusAccount;
        }
        return null;
    }

    @Nullable
    private static RemoteAccountProfile hydrateActiveAccountFromSavedAccounts(@Nullable RemoteAccountProfile activeAccount,
                                                                              @Nullable List<RemoteAccountProfile> savedAccounts) {
        if (activeAccount == null || savedAccounts == null || savedAccounts.isEmpty()) {
            return activeAccount;
        }
        String activeProfileId = safeTrim(activeAccount.getProfileId());
        if (activeProfileId.isEmpty()) {
            return activeAccount;
        }
        for (RemoteAccountProfile candidate : savedAccounts) {
            if (candidate == null) {
                continue;
            }
            if (equalsIgnoreCase(activeProfileId, candidate.getProfileId())) {
                return mergeProfile(activeAccount, candidate);
            }
        }
        return activeAccount;
    }

    @Nullable
    private static RemoteAccountProfile findSavedProfileById(@Nullable String profileId,
                                                             @Nullable List<RemoteAccountProfile> savedAccounts) {
        String safeProfileId = safeTrim(profileId);
        if (safeProfileId.isEmpty() || savedAccounts == null || savedAccounts.isEmpty()) {
            return null;
        }
        for (RemoteAccountProfile candidate : savedAccounts) {
            if (candidate == null) {
                continue;
            }
            if (equalsIgnoreCase(safeProfileId, candidate.getProfileId())) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private static RemoteAccountProfile mergeProfile(@Nullable RemoteAccountProfile primary,
                                                     @Nullable RemoteAccountProfile fallback) {
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }
        return new RemoteAccountProfile(
                emptyToFallback(primary.getProfileId(), fallback.getProfileId()),
                emptyToFallback(primary.getLogin(), fallback.getLogin()),
                emptyToFallback(primary.getLoginMasked(), fallback.getLoginMasked()),
                emptyToFallback(primary.getServer(), fallback.getServer()),
                emptyToFallback(primary.getDisplayName(), fallback.getDisplayName()),
                primary.isActive() || fallback.isActive(),
                emptyToFallback(primary.getState(), fallback.getState())
        );
    }

    private static boolean isAccountIdentityConflict(@NonNull RemoteAccountProfile first,
                                                     @NonNull RemoteAccountProfile second) {
        return hasComparableConflict(first.getProfileId(), second.getProfileId())
                || hasComparableConflict(first.getLogin(), second.getLogin())
                || hasComparableConflict(first.getServer(), second.getServer());
    }

    private static boolean hasComparableConflict(@Nullable String first, @Nullable String second) {
        String safeFirst = safeTrim(first);
        String safeSecond = safeTrim(second);
        return !safeFirst.isEmpty() && !safeSecond.isEmpty() && !equalsIgnoreCase(safeFirst, safeSecond);
    }

    private static boolean equalsIgnoreCase(@Nullable String first, @Nullable String second) {
        return safeTrim(first).equalsIgnoreCase(safeTrim(second));
    }

    @NonNull
    private static String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @NonNull
    private static String emptyToFallback(@Nullable String primary, @Nullable String fallback) {
        String safePrimary = safeTrim(primary);
        if (!safePrimary.isEmpty()) {
            return safePrimary;
        }
        return safeTrim(fallback);
    }
}

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
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.security.SessionSummaryStore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
                                                        char[] password,
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

    public interface RequestIdGenerator {
        String nextRequestId();
    }

    public static final class LoginRequest {
        private final String login;
        private final char[] password;
        private final String server;
        private final boolean remember;
        private final long clientTime;

        // 构造登录请求参数。
        public LoginRequest(String login,
                            char[] password,
                            String server,
                            boolean remember,
                            long clientTime) {
            this.login = login == null ? "" : login.trim();
            this.password = password == null ? new char[0] : Arrays.copyOf(password, password.length);
            AccountRemoteSessionCoordinator.clearPassword(password);
            this.server = server == null ? "" : server.trim();
            this.remember = remember;
            this.clientTime = clientTime;
        }

        public String getLogin() {
            return login;
        }

        public boolean hasPassword() {
            return password.length > 0;
        }

        public char[] copyPassword() {
            return Arrays.copyOf(password, password.length);
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

        public void clearPassword() {
            Arrays.fill(password, '\0');
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
    private final AtomicReference<AwaitingSyncState> awaitingSyncState = new AtomicReference<>(AwaitingSyncState.empty());

    private static final class AwaitingSyncState {
        private static final AwaitingSyncState EMPTY = new AwaitingSyncState(
                null,
                Collections.emptyList(),
                "",
                "",
                ""
        );

        private final RemoteAccountProfile activeAccount;
        private final List<RemoteAccountProfile> savedAccounts;
        private final String profileId;
        private final String login;
        private final String server;

        private AwaitingSyncState(@Nullable RemoteAccountProfile activeAccount,
                                  @Nullable List<RemoteAccountProfile> savedAccounts,
                                  @Nullable String profileId,
                                  @Nullable String login,
                                  @Nullable String server) {
            this.activeAccount = activeAccount;
            this.savedAccounts = savedAccounts == null ? new ArrayList<>() : new ArrayList<>(savedAccounts);
            this.profileId = profileId == null ? "" : profileId.trim();
            this.login = login == null ? "" : login.trim();
            this.server = server == null ? "" : server.trim();
        }

        @NonNull
        private static AwaitingSyncState empty() {
            return EMPTY;
        }

        private boolean isAwaitingSync() {
            return activeAccount != null;
        }

        @Nullable
        private RemoteAccountProfile getActiveAccount() {
            return activeAccount;
        }

        @NonNull
        private List<RemoteAccountProfile> getSavedAccounts() {
            return new ArrayList<>(savedAccounts);
        }

        @NonNull
        private String getProfileId() {
            return profileId;
        }

        @NonNull
        private String getLogin() {
            return login;
        }

        @NonNull
        private String getServer() {
            return server;
        }
    }

    public static final class SessionActionException extends Exception {
        private final String action;
        private final String requestId;
        @Nullable
        private final SessionReceipt receipt;

        // 封装会话动作失败时的 requestId，方便上层回查服务端诊断。
        public SessionActionException(@NonNull String action,
                                      @Nullable String requestId,
                                      @Nullable String message,
                                      @NonNull Throwable cause) {
            this(action, requestId, message, cause, null);
        }

        // 封装会话动作失败时的结构化回执，便于上层优先展示服务端真实摘要。
        public SessionActionException(@NonNull String action,
                                      @Nullable String requestId,
                                      @Nullable String message,
                                      @NonNull Throwable cause,
                                      @Nullable SessionReceipt receipt) {
            super(message == null ? "" : message, cause);
            this.action = action == null ? "" : action.trim();
            this.requestId = requestId == null ? "" : requestId.trim();
            this.receipt = receipt;
        }

        @NonNull
        public String getAction() {
            return action;
        }

        @NonNull
        public String getRequestId() {
            return requestId;
        }

        @Nullable
        public SessionReceipt getReceipt() {
            return receipt;
        }
    }

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
        char[] password = request.copyPassword();
        try {
            stateMachine.moveTo(AccountSessionStateMachine.AccountSessionUiState.ENCRYPTING, "正在安全加密");
            SessionPublicKeyPayload publicKey = requirePublicKey(sessionGateway.fetchPublicKey());
            SessionCredentialEncryptor.LoginEnvelope envelope = credentialFactory.encrypt(
                    publicKey.getPublicKeyPem(),
                    publicKey.getKeyId(),
                    request.getLogin(),
                    password,
                    request.getServer(),
                    request.isRemember(),
                    request.getClientTime()
            );
            String requestId = envelope.getRequestId();
            stateMachine.moveTo(AccountSessionStateMachine.AccountSessionUiState.SUBMITTING, "正在提交登录");
            SessionReceipt receipt;
            try {
                receipt = sessionGateway.login(envelope, request.isRemember());
            } catch (Exception submitError) {
                throw new SessionActionException(
                        "login",
                        requestId,
                        resolveThrowableMessage(submitError, "登录失败"),
                        submitError
                );
            }
            return handleAcceptedReceipt(
                    receipt,
                    "正在同步账户数据",
                    "",
                    request.getLogin(),
                    request.getServer()
            );
        } finally {
            clearPassword(password);
            request.clearPassword();
        }
    }

    // 切换到服务器已保存账号，成功后立即清理旧缓存并进入同步中。
    public SessionActionResult switchSavedAccount(@NonNull String profileId) throws Exception {
        String safeProfileId = profileId == null ? "" : profileId.trim();
        if (safeProfileId.isEmpty()) {
            throw new IllegalArgumentException("profileId 不能为空");
        }
        stateMachine.moveTo(AccountSessionStateMachine.AccountSessionUiState.SWITCHING, "正在切换账户");
        String requestId = requestIdGenerator.nextRequestId();
        SessionReceipt receipt;
        try {
            receipt = sessionGateway.switchAccount(safeProfileId, requestId);
        } catch (Exception switchError) {
            throw new SessionActionException(
                    "switch",
                    requestId,
                    resolveThrowableMessage(switchError, "切换账户失败"),
                    switchError
            );
        }
        return handleAcceptedReceipt(receipt, "正在同步账户数据", safeProfileId, "", "");
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
        awaitingSyncState.set(AwaitingSyncState.empty());
        stateMachine.reset();
        return new SessionActionResult(receipt, null, Collections.emptyList());
    }

    // 在页面拿到新快照后调用，只有匹配当前等待的账号时才转为 active。
    public boolean onSnapshotApplied(@Nullable String account, @Nullable String server) {
        AwaitingSyncState pendingState = awaitingSyncState.get();
        if (!pendingState.isAwaitingSync()) {
            return false;
        }
        String safeAccount = account == null ? "" : account.trim();
        String safeServer = server == null ? "" : server.trim();
        if (pendingState.getLogin().isEmpty()) {
            // server 往往会被多个账号共用，只有 server 没有 login 时仍然不能安全收口。
            return false;
        }
        if (!pendingState.getLogin().equalsIgnoreCase(safeAccount)) {
            return false;
        }
        if (!pendingState.getServer().isEmpty() && !pendingState.getServer().equalsIgnoreCase(safeServer)) {
            return false;
        }
        if (!awaitingSyncState.compareAndSet(pendingState, AwaitingSyncState.empty())) {
            return false;
        }
        sessionSummaryStore.saveSession(
                pendingState.getActiveAccount(),
                pendingState.getSavedAccounts(),
                true
        );
        stateMachine.markFullSyncing(pendingState.getProfileId(), "账户已登录，正在加载完整数据");
        return true;
    }

    // 当同步阶段明确失败时，统一收口到 failed。
    public void markSyncFailed(@Nullable String message) {
        awaitingSyncState.set(AwaitingSyncState.empty());
        stateMachine.markFailed(message == null ? "账户同步失败" : message);
    }

    // 当网关暂时离线但服务器已经受理时，继续保留等待同步态，只更新提示文案。
    public void markAwaitingGatewaySync(@Nullable String message) {
        AwaitingSyncState pendingState = awaitingSyncState.get();
        if (!pendingState.isAwaitingSync()) {
            return;
        }
        stateMachine.markSyncing(
                pendingState.getProfileId(),
                message == null ? "会话已受理，等待网关上线" : message
        );
    }

    // 返回当前是否仍在等待新快照。
    public boolean isAwaitingSync() {
        return awaitingSyncState.get().isAwaitingSync();
    }

    // 返回当前等待同步的 profileId。
    public String getPendingProfileId() {
        return awaitingSyncState.get().getProfileId();
    }

    // 返回当前等待同步的 login。
    public String getPendingLogin() {
        return awaitingSyncState.get().getLogin();
    }

    // 返回当前等待同步的 server。
    public String getPendingServer() {
        return awaitingSyncState.get().getServer();
    }

    private SessionActionResult handleAcceptedReceipt(@Nullable SessionReceipt receipt,
                                                      @NonNull String syncingMessage,
                                                      @Nullable String profileIdHint,
                                                      @Nullable String loginHint,
                                                      @Nullable String serverHint) throws Exception {
        if (receipt == null || !receipt.isOk()) {
            stateMachine.markFailed(resolveFailureMessage(receipt, "会话请求失败"));
            return new SessionActionResult(receipt, null, Collections.emptyList());
        }
        SessionStatusPayload status;
        try {
            status = sessionGateway.fetchStatus();
        } catch (Exception fetchError) {
            return enterAwaitingSyncWithoutStatus(
                    receipt,
                    "会话已受理，等待网关同步确认",
                    profileIdHint,
                    loginHint,
                    serverHint
            );
        }
        if (status == null || !status.isOk()) {
            return enterAwaitingSyncWithoutStatus(
                    receipt,
                    "会话已受理，等待网关同步确认",
                    profileIdHint,
                    loginHint,
                    serverHint
            );
        }
        List<RemoteAccountProfile> savedAccounts = new ArrayList<>(status.getSavedAccounts());
        RemoteAccountProfile activeAccount;
        try {
            activeAccount = resolveCanonicalActiveAccount(receipt, status);
        } catch (IllegalStateException conflictError) {
            awaitingSyncState.set(AwaitingSyncState.empty());
            stateMachine.markFailed(conflictError.getMessage());
            throw conflictError;
        }
        if (!isCanonicalActiveAccountComplete(activeAccount)) {
            awaitingSyncState.set(AwaitingSyncState.empty());
            stateMachine.markFailed("会话账号摘要缺失");
            throw new IllegalStateException("会话账号摘要缺失");
        }
        return enterAwaitingSync(receipt, activeAccount, savedAccounts, syncingMessage);
    }

    private SessionActionResult enterAwaitingSyncWithoutStatus(@Nullable SessionReceipt receipt,
                                                               @NonNull String syncingMessage,
                                                               @Nullable String profileIdHint,
                                                               @Nullable String loginHint,
                                                               @Nullable String serverHint) {
        SessionSummarySnapshot sessionSummary = sessionSummaryStore.loadSessionSummary();
        RemoteAccountProfile pendingAccount = resolvePendingActiveAccount(
                receipt,
                profileIdHint,
                loginHint,
                serverHint,
                sessionSummary.getSavedAccountsSnapshot()
        );
        if (!isCanonicalActiveAccountComplete(pendingAccount)) {
            awaitingSyncState.set(AwaitingSyncState.empty());
            stateMachine.markFailed("会话账号摘要缺失");
            throw new IllegalStateException("会话账号摘要缺失");
        }
        List<RemoteAccountProfile> savedAccounts = hydrateSavedAccountsSnapshot(
                pendingAccount,
                sessionSummary.getSavedAccountsSnapshot()
        );
        return enterAwaitingSync(receipt, pendingAccount, savedAccounts, syncingMessage);
    }

    private SessionActionResult enterAwaitingSync(@Nullable SessionReceipt receipt,
                                                  @NonNull RemoteAccountProfile activeAccount,
                                                  @NonNull List<RemoteAccountProfile> savedAccounts,
                                                  @NonNull String syncingMessage) {
        clearCachesForAccountChange();
        sessionSummaryStore.saveSession(activeAccount, savedAccounts, false);
        AwaitingSyncState nextState = new AwaitingSyncState(
                activeAccount,
                savedAccounts,
                activeAccount.getProfileId(),
                activeAccount.getLogin(),
                activeAccount.getServer()
        );
        awaitingSyncState.set(nextState);
        stateMachine.markSyncing(nextState.getProfileId(), syncingMessage);
        return new SessionActionResult(receipt, activeAccount, savedAccounts);
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
        if (!request.hasPassword()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (request.getServer().isEmpty()) {
            throw new IllegalArgumentException("服务器不能为空");
        }
    }

    private static void clearPassword(@Nullable char[] password) {
        if (password == null) {
            return;
        }
        Arrays.fill(password, '\0');
    }

    @NonNull
    private static String resolveThrowableMessage(@Nullable Throwable throwable, @NonNull String fallback) {
        String message = throwable == null || throwable.getMessage() == null
                ? ""
                : throwable.getMessage().trim();
        return message.isEmpty() ? fallback : message;
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
    private static RemoteAccountProfile resolvePendingActiveAccount(@Nullable SessionReceipt receipt,
                                                                    @Nullable String profileIdHint,
                                                                    @Nullable String loginHint,
                                                                    @Nullable String serverHint,
                                                                    @Nullable List<RemoteAccountProfile> savedAccounts) {
        RemoteAccountProfile receiptAccount = receipt == null ? null : receipt.getActiveAccount();
        String profileId = preferNonEmpty(
                receiptAccount == null ? "" : receiptAccount.getProfileId(),
                profileIdHint
        );
        String login = preferNonEmpty(
                receiptAccount == null ? "" : receiptAccount.getLogin(),
                resolveLoginFromSavedAccounts(savedAccounts, profileIdHint),
                loginHint
        );
        String server = preferNonEmpty(
                receiptAccount == null ? "" : receiptAccount.getServer(),
                resolveServerFromSavedAccounts(savedAccounts, profileIdHint),
                serverHint
        );
        String loginMasked = receiptAccount == null ? "" : receiptAccount.getLoginMasked();
        String displayName = receiptAccount == null ? "" : receiptAccount.getDisplayName();
        String state = receiptAccount == null ? "" : receiptAccount.getState();
        return new RemoteAccountProfile(profileId, login, loginMasked, server, displayName, true, state);
    }

    @NonNull
    private static List<RemoteAccountProfile> hydrateSavedAccountsSnapshot(@NonNull RemoteAccountProfile activeAccount,
                                                                           @Nullable List<RemoteAccountProfile> savedAccounts) {
        List<RemoteAccountProfile> hydrated = new ArrayList<>();
        boolean replaced = false;
        if (savedAccounts != null) {
            for (RemoteAccountProfile account : savedAccounts) {
                if (account == null) {
                    continue;
                }
                if (equalsIgnoreCase(account.getProfileId(), activeAccount.getProfileId())) {
                    hydrated.add(activeAccount);
                    replaced = true;
                    continue;
                }
                hydrated.add(account);
            }
        }
        if (!replaced) {
            hydrated.add(activeAccount);
        }
        return hydrated;
    }

    @Nullable
    private static RemoteAccountProfile resolveCanonicalActiveAccount(@Nullable SessionReceipt receipt,
                                                                      @Nullable SessionStatusPayload status) {
        RemoteAccountProfile statusAccount = status == null ? null : status.getActiveAccount();
        RemoteAccountProfile receiptAccount = receipt == null ? null : receipt.getActiveAccount();
        if (statusAccount == null) {
            return null;
        }
        if (receiptAccount != null && isAccountIdentityConflict(statusAccount, receiptAccount)) {
            throw new IllegalStateException("会话账号摘要不一致");
        }
        return statusAccount;
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

    private static boolean isCanonicalActiveAccountComplete(@Nullable RemoteAccountProfile account) {
        return account != null
                && !safeTrim(account.getProfileId()).isEmpty()
                && !safeTrim(account.getLogin()).isEmpty()
                && !safeTrim(account.getServer()).isEmpty();
    }

    @NonNull
    private static String preferNonEmpty(@Nullable String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String trimmed = safeTrim(value);
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return "";
    }

    @NonNull
    private static String resolveLoginFromSavedAccounts(@Nullable List<RemoteAccountProfile> savedAccounts,
                                                        @Nullable String profileId) {
        RemoteAccountProfile profile = findSavedAccount(savedAccounts, profileId);
        return profile == null ? "" : safeTrim(profile.getLogin());
    }

    @NonNull
    private static String resolveServerFromSavedAccounts(@Nullable List<RemoteAccountProfile> savedAccounts,
                                                         @Nullable String profileId) {
        RemoteAccountProfile profile = findSavedAccount(savedAccounts, profileId);
        return profile == null ? "" : safeTrim(profile.getServer());
    }

    @Nullable
    private static RemoteAccountProfile findSavedAccount(@Nullable List<RemoteAccountProfile> savedAccounts,
                                                         @Nullable String profileId) {
        String safeProfileId = safeTrim(profileId);
        if (safeProfileId.isEmpty() || savedAccounts == null) {
            return null;
        }
        for (RemoteAccountProfile profile : savedAccounts) {
            if (profile != null && equalsIgnoreCase(profile.getProfileId(), safeProfileId)) {
                return profile;
            }
        }
        return null;
    }

    @NonNull
    private static String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

}

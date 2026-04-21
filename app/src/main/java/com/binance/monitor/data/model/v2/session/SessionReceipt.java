/*
 * 会话动作回执模型，覆盖 login/switch/logout 的统一响应结构。
 * 由 GatewayV2SessionClient 在提交会话动作后返回给上层调用方。
 */
package com.binance.monitor.data.model.v2.session;

public class SessionReceipt {
    private final boolean ok;
    private final String state;
    private final String requestId;
    private final RemoteAccountProfile activeAccount;
    private final String message;
    private final String errorCode;
    private final boolean retryable;
    private final String rawJson;
    private final String stage;
    private final long elapsedMs;
    private final RemoteAccountProfile baselineAccount;
    private final RemoteAccountProfile finalAccount;
    private final String loginError;
    private final RemoteAccountProfile lastObservedAccount;

    // 构造会话动作回执对象。
    public SessionReceipt(boolean ok,
                          String state,
                          String requestId,
                          RemoteAccountProfile activeAccount,
                          String message,
                          String errorCode,
                          boolean retryable,
                          String rawJson) {
        this(ok, state, requestId, activeAccount, message, errorCode, retryable, rawJson,
                "", 0L, null, null, "", null);
    }

    // 构造带切号主链摘要的会话动作回执对象。
    public SessionReceipt(boolean ok,
                          String state,
                          String requestId,
                          RemoteAccountProfile activeAccount,
                          String message,
                          String errorCode,
                          boolean retryable,
                          String rawJson,
                          String stage,
                          long elapsedMs,
                          RemoteAccountProfile baselineAccount,
                          RemoteAccountProfile finalAccount,
                          String loginError,
                          RemoteAccountProfile lastObservedAccount) {
        this.ok = ok;
        this.state = state == null ? "" : state;
        this.requestId = requestId == null ? "" : requestId;
        this.activeAccount = activeAccount;
        this.message = message == null ? "" : message;
        this.errorCode = errorCode == null ? "" : errorCode;
        this.retryable = retryable;
        this.rawJson = rawJson == null ? "" : rawJson;
        this.stage = stage == null ? "" : stage;
        this.elapsedMs = Math.max(0L, elapsedMs);
        this.baselineAccount = baselineAccount;
        this.finalAccount = finalAccount;
        this.loginError = loginError == null ? "" : loginError;
        this.lastObservedAccount = lastObservedAccount;
    }

    // 返回是否成功。
    public boolean isOk() {
        return ok;
    }

    // 返回状态字段。
    public String getState() {
        return state;
    }

    // 返回请求 ID。
    public String getRequestId() {
        return requestId;
    }

    // 返回当前激活账号摘要。
    public RemoteAccountProfile getActiveAccount() {
        return activeAccount;
    }

    // 返回提示消息。
    public String getMessage() {
        return message;
    }

    // 返回错误码。
    public String getErrorCode() {
        return errorCode;
    }

    // 返回是否可重试。
    public boolean isRetryable() {
        return retryable;
    }

    // 返回原始 JSON 字符串。
    public String getRawJson() {
        return rawJson;
    }

    // 返回切号阶段。
    public String getStage() {
        return stage;
    }

    // 返回切号耗时毫秒。
    public long getElapsedMs() {
        return elapsedMs;
    }

    // 返回切号前基线账号。
    public RemoteAccountProfile getBaselineAccount() {
        return baselineAccount;
    }

    // 返回切号后最终账号。
    public RemoteAccountProfile getFinalAccount() {
        return finalAccount;
    }

    // 返回 mt5.login 的原始错误。
    public String getLoginError() {
        return loginError;
    }

    // 返回切号失败前最后一次观察到的账号。
    public RemoteAccountProfile getLastObservedAccount() {
        return lastObservedAccount;
    }

    // 返回是否失败态。
    public boolean isFailed() {
        return !ok || "failed".equalsIgnoreCase(state);
    }
}

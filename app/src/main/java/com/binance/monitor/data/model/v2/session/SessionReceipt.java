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

    // 构造会话动作回执对象。
    public SessionReceipt(boolean ok,
                          String state,
                          String requestId,
                          RemoteAccountProfile activeAccount,
                          String message,
                          String errorCode,
                          boolean retryable,
                          String rawJson) {
        this.ok = ok;
        this.state = state == null ? "" : state;
        this.requestId = requestId == null ? "" : requestId;
        this.activeAccount = activeAccount;
        this.message = message == null ? "" : message;
        this.errorCode = errorCode == null ? "" : errorCode;
        this.retryable = retryable;
        this.rawJson = rawJson == null ? "" : rawJson;
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

    // 返回是否失败态。
    public boolean isFailed() {
        return !ok || "failed".equalsIgnoreCase(state);
    }
}

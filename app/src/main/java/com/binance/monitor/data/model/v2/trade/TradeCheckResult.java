/*
 * 交易检查结果模型，对应 /v2/trade/check 返回结构。
 * 用于区分“可执行检查通过”和“实际提交下单”两个阶段。
 */
package com.binance.monitor.data.model.v2.trade;

import org.json.JSONObject;

public class TradeCheckResult {
    private final String requestId;
    private final String action;
    private final String accountMode;
    private final String status;
    private final ExecutionError error;
    private final JSONObject check;
    private final long serverTime;

    // 构造检查结果对象。
    public TradeCheckResult(String requestId,
                            String action,
                            String accountMode,
                            String status,
                            ExecutionError error,
                            JSONObject check,
                            long serverTime) {
        this.requestId = requestId == null ? "" : requestId;
        this.action = action == null ? "" : action;
        this.accountMode = accountMode == null ? "" : accountMode;
        this.status = status == null ? "" : status;
        this.error = error;
        this.check = check == null ? new JSONObject() : check;
        this.serverTime = serverTime;
    }

    // 判断检查是否可执行。
    public boolean isExecutable() {
        return "EXECUTABLE".equalsIgnoreCase(status) && error == null;
    }

    // 返回请求 ID。
    public String getRequestId() {
        return requestId;
    }

    // 返回动作类型。
    public String getAction() {
        return action;
    }

    // 返回账户模式。
    public String getAccountMode() {
        return accountMode;
    }

    // 返回检查状态。
    public String getStatus() {
        return status;
    }

    // 返回检查错误。
    public ExecutionError getError() {
        return error;
    }

    // 返回检查原始结果。
    public JSONObject getCheck() {
        return check;
    }

    // 返回服务端时间。
    public long getServerTime() {
        return serverTime;
    }
}

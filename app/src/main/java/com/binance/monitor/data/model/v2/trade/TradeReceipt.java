/*
 * 交易提交回执模型，对应 /v2/trade/submit 与 /v2/trade/result 的响应。
 * 用于表示“已受理”“拒单”“超时待确认”等提交结果。
 */
package com.binance.monitor.data.model.v2.trade;

import org.json.JSONObject;

public class TradeReceipt {
    private final String requestId;
    private final String action;
    private final String accountMode;
    private final String status;
    private final ExecutionError error;
    private final JSONObject check;
    private final JSONObject result;
    private final boolean idempotent;
    private final long serverTime;

    // 构造交易回执对象。
    public TradeReceipt(String requestId,
                        String action,
                        String accountMode,
                        String status,
                        ExecutionError error,
                        JSONObject check,
                        JSONObject result,
                        boolean idempotent,
                        long serverTime) {
        this.requestId = requestId == null ? "" : requestId;
        this.action = action == null ? "" : action;
        this.accountMode = accountMode == null ? "" : accountMode;
        this.status = status == null ? "" : status;
        this.error = error;
        this.check = check == null ? new JSONObject() : check;
        this.result = result == null ? new JSONObject() : result;
        this.idempotent = idempotent;
        this.serverTime = serverTime;
    }

    // 判断是否已被网关受理。
    public boolean isAccepted() {
        return "ACCEPTED".equalsIgnoreCase(status);
    }

    // 判断是否是失败态。
    public boolean isRejected() {
        return "FAILED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status);
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

    // 返回提交状态。
    public String getStatus() {
        return status;
    }

    // 返回提交错误。
    public ExecutionError getError() {
        return error;
    }

    // 返回提交前检查结果。
    public JSONObject getCheck() {
        return check;
    }

    // 返回提交执行结果。
    public JSONObject getResult() {
        return result;
    }

    // 返回是否幂等返回。
    public boolean isIdempotent() {
        return idempotent;
    }

    // 返回服务端时间。
    public long getServerTime() {
        return serverTime;
    }
}

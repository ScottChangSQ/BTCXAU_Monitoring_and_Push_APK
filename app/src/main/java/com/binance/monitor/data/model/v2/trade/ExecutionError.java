/*
 * 交易执行错误模型，统一承载错误码、错误信息和可选细节。
 * 供交易检查、提交和状态机在失败分支中共享。
 */
package com.binance.monitor.data.model.v2.trade;

import org.json.JSONObject;

public class ExecutionError {
    private final String code;
    private final String message;
    private final JSONObject details;

    // 构造交易错误对象。
    public ExecutionError(String code, String message, JSONObject details) {
        this.code = code == null ? "" : code;
        this.message = message == null ? "" : message;
        this.details = details == null ? new JSONObject() : details;
    }

    // 快速构造不带细节的错误对象。
    public static ExecutionError of(String code, String message) {
        return new ExecutionError(code, message, null);
    }

    // 返回错误码。
    public String getCode() {
        return code;
    }

    // 返回错误信息。
    public String getMessage() {
        return message;
    }

    // 返回错误细节。
    public JSONObject getDetails() {
        return details;
    }
}

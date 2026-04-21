/*
 * 交易审计条目，统一承载本地与网关两侧的交易阶段事实。
 * 与 TradeAuditStore、GatewayV2TradeClient 和后续交易追踪页协同工作。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

public class TradeAuditEntry {
    private final String traceId;
    private final String traceType;
    private final String action;
    private final String symbol;
    private final String accountMode;
    private final String stage;
    private final String status;
    private final String errorCode;
    private final String message;
    private final String actionSummary;
    private final long serverTime;
    private final long createdAt;

    // 保存一条交易审计事实。
    public TradeAuditEntry(@Nullable String traceId,
                           @Nullable String traceType,
                           @Nullable String action,
                           @Nullable String symbol,
                           @Nullable String accountMode,
                           @Nullable String stage,
                           @Nullable String status,
                           @Nullable String errorCode,
                           @Nullable String message,
                           @Nullable String actionSummary,
                           long serverTime,
                           long createdAt) {
        this.traceId = safe(traceId);
        this.traceType = safe(traceType);
        this.action = safe(action);
        this.symbol = safe(symbol);
        this.accountMode = safe(accountMode);
        this.stage = safe(stage);
        this.status = safe(status);
        this.errorCode = safe(errorCode);
        this.message = safe(message);
        this.actionSummary = safe(actionSummary);
        this.serverTime = Math.max(0L, serverTime);
        this.createdAt = Math.max(0L, createdAt);
    }

    @NonNull
    public String getTraceId() {
        return traceId;
    }

    @NonNull
    public String getTraceType() {
        return traceType;
    }

    @NonNull
    public String getAction() {
        return action;
    }

    @NonNull
    public String getSymbol() {
        return symbol;
    }

    @NonNull
    public String getAccountMode() {
        return accountMode;
    }

    @NonNull
    public String getStage() {
        return stage;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    @NonNull
    public String getErrorCode() {
        return errorCode;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @NonNull
    public String getActionSummary() {
        return actionSummary;
    }

    public long getServerTime() {
        return serverTime;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @NonNull
    public TradeAuditEntry withCreatedAt(long value) {
        return new TradeAuditEntry(
                traceId,
                traceType,
                action,
                symbol,
                accountMode,
                stage,
                status,
                errorCode,
                message,
                actionSummary,
                serverTime,
                value
        );
    }

    @NonNull
    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        putQuietly(object, "traceId", traceId);
        putQuietly(object, "traceType", traceType);
        putQuietly(object, "action", action);
        putQuietly(object, "symbol", symbol);
        putQuietly(object, "accountMode", accountMode);
        putQuietly(object, "stage", stage);
        putQuietly(object, "status", status);
        putQuietly(object, "errorCode", errorCode);
        putQuietly(object, "message", message);
        putQuietly(object, "actionSummary", actionSummary);
        putQuietly(object, "serverTime", serverTime);
        putQuietly(object, "createdAt", createdAt);
        return object;
    }

    @NonNull
    public static TradeAuditEntry fromJson(@Nullable JSONObject object) {
        if (object == null) {
            return new TradeAuditEntry("", "", "", "", "", "", "", "", "", "", 0L, 0L);
        }
        return new TradeAuditEntry(
                object.optString("traceId", ""),
                object.optString("traceType", ""),
                object.optString("action", ""),
                object.optString("symbol", ""),
                object.optString("accountMode", ""),
                object.optString("stage", ""),
                object.optString("status", ""),
                object.optString("errorCode", ""),
                object.optString("message", ""),
                object.optString("actionSummary", ""),
                object.optLong("serverTime", 0L),
                object.optLong("createdAt", 0L)
        );
    }

    private static void putQuietly(@NonNull JSONObject target, @NonNull String key, @Nullable Object value) {
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

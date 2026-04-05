/*
 * v2 交易命令客户端，只负责 check/submit/result 三个交易接口。
 * 与行情、账户、同步读取客户端完全分离，避免职责混杂。
 */
package com.binance.monitor.data.remote.v2;

import android.content.Context;

import androidx.annotation.Nullable;

import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.v2.trade.ExecutionError;
import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeReceipt;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GatewayV2TradeClient {
    private static final long CONNECT_TIMEOUT_SECONDS = 8L;
    private static final long READ_TIMEOUT_SECONDS = 35L;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    @Nullable
    private final ConfigManager configManager;

    // 创建不依赖 Context 的交易客户端实例。
    public GatewayV2TradeClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.configManager = null;
    }

    // 创建依赖配置中心的交易客户端实例。
    public GatewayV2TradeClient(@Nullable Context context) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.configManager = context == null
                ? null
                : ConfigManager.getInstance(context.getApplicationContext());
    }

    // 构建交易命令请求体，固定带 8 个关键字段。
    public static JSONObject buildTradeCommandPayload(TradeCommand command) throws Exception {
        validateTradeCommand(command);
        JSONObject payload = new JSONObject();
        payload.put("requestId", command.getRequestId());
        payload.put("accountId", command.getAccountId());
        payload.put("symbol", command.getSymbol());
        payload.put("action", command.getAction());
        payload.put("volume", command.getVolume());
        payload.put("price", command.getPrice());
        payload.put("sl", command.getSl());
        payload.put("tp", command.getTp());
        JSONObject params = new JSONObject();
        params.put("symbol", command.getSymbol());
        params.put("volume", command.getVolume());
        params.put("price", command.getPrice());
        params.put("sl", command.getSl());
        params.put("tp", command.getTp());
        payload.put("params", params);
        return payload;
    }

    // 解析交易检查响应。
    public static TradeCheckResult parseTradeCheck(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        return new TradeCheckResult(
                json.optString("requestId", ""),
                json.optString("action", ""),
                json.optString("accountMode", ""),
                json.optString("status", ""),
                parseExecutionError(json.optJSONObject("error")),
                json.optJSONObject("check"),
                json.optLong("serverTime", 0L)
        );
    }

    // 解析交易提交或查询回执响应。
    public static TradeReceipt parseTradeReceipt(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        return new TradeReceipt(
                json.optString("requestId", ""),
                json.optString("action", ""),
                json.optString("accountMode", ""),
                json.optString("status", ""),
                parseExecutionError(json.optJSONObject("error")),
                json.optJSONObject("check"),
                json.optJSONObject("result"),
                json.optBoolean("idempotent", false),
                json.optLong("serverTime", 0L)
        );
    }

    // 请求 /v2/trade/check。
    public TradeCheckResult check(TradeCommand command) throws Exception {
        return parseTradeCheck(post("/v2/trade/check", buildTradeCommandPayload(command).toString()));
    }

    // 请求 /v2/trade/submit。
    public TradeReceipt submit(TradeCommand command) throws Exception {
        return parseTradeReceipt(post("/v2/trade/submit", buildTradeCommandPayload(command).toString()));
    }

    // 请求 /v2/trade/result。
    public TradeReceipt result(String requestId) throws Exception {
        String safeId = requestId == null ? "" : requestId;
        String path = "/v2/trade/result?requestId=" + URLEncoder.encode(safeId, StandardCharsets.UTF_8);
        return parseTradeReceipt(get(path));
    }

    // 解析统一错误对象。
    @Nullable
    private static ExecutionError parseExecutionError(@Nullable JSONObject object) {
        if (object == null) {
            return null;
        }
        return new ExecutionError(
                object.optString("code", ""),
                object.optString("message", ""),
                object.optJSONObject("details")
        );
    }

    // 执行 GET 请求。
    private String get(String path) throws Exception {
        String url = resolveBaseUrl() + path;
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + path);
            }
            return response.body() == null ? "" : response.body().string();
        }
    }

    // 执行 POST 请求。
    private String post(String path, String bodyJson) throws Exception {
        String url = resolveBaseUrl() + path;
        RequestBody body = RequestBody.create(bodyJson == null ? "{}" : bodyJson, JSON_MEDIA_TYPE);
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + path);
            }
            return response.body() == null ? "" : response.body().string();
        }
    }

    // 解析网关基础地址。
    private String resolveBaseUrl() {
        if (configManager == null) {
            return "";
        }
        String baseUrl = configManager.getMt5GatewayBaseUrl();
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // 保护空响应体解析。
    private static String safeBody(String body) {
        return body == null ? "{}" : body;
    }

    // 提交前执行客户端最小校验，拒绝明显坏命令。
    private static void validateTradeCommand(@Nullable TradeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command 不能为空");
        }
        if (isBlank(command.getRequestId())) {
            throw new IllegalArgumentException("requestId 不能为空");
        }
        if (isBlank(command.getAccountId())) {
            throw new IllegalArgumentException("accountId 不能为空");
        }
        if (isBlank(command.getSymbol())) {
            throw new IllegalArgumentException("symbol 不能为空");
        }
        if (isBlank(command.getAction())) {
            throw new IllegalArgumentException("action 不能为空");
        }
        if (command.getVolume() <= 0.0d) {
            throw new IllegalArgumentException("volume 必须大于 0");
        }
        if (command.getPrice() <= 0.0d) {
            throw new IllegalArgumentException("price 必须大于 0");
        }
        if (Double.isNaN(command.getSl()) || Double.isInfinite(command.getSl())) {
            throw new IllegalArgumentException("sl 非法");
        }
        if (Double.isNaN(command.getTp()) || Double.isInfinite(command.getTp())) {
            throw new IllegalArgumentException("tp 非法");
        }
    }

    // 判断字符串是否为空白。
    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}

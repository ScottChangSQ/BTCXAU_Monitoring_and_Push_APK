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
import java.util.Locale;
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
    public static JSONObject buildTradeCommandPayload(TradeCommand command) {
        validateTradeCommand(command);
        JSONObject payload = new JSONObject();
        JSONObject params = mergeTradeParams(command);
        putQuietly(payload, "requestId", command.getRequestId());
        putQuietly(payload, "accountId", command.getAccountId());
        putQuietly(payload, "symbol", command.getSymbol());
        putQuietly(payload, "action", command.getAction());
        putQuietly(payload, "volume", command.getVolume());
        putQuietly(payload, "price", command.getPrice());
        putQuietly(payload, "sl", command.getSl());
        putQuietly(payload, "tp", command.getTp());
        putQuietly(payload, "params", params);
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
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(buildHttpFailureMessage(response.code(), path, responseBody));
            }
            return responseBody;
        }
    }

    // 执行 POST 请求。
    private String post(String path, String bodyJson) throws Exception {
        String url = resolveBaseUrl() + path;
        RequestBody body = RequestBody.create(bodyJson == null ? "{}" : bodyJson, JSON_MEDIA_TYPE);
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException(buildHttpFailureMessage(response.code(), path, responseBody));
            }
            return responseBody;
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

    // 尽量保留服务端 detail/error 里的结构化错误信息，避免只剩 HTTP 状态码。
    static String buildHttpFailureMessage(int httpCode, String path, @Nullable String body) {
        String fallback = "HTTP " + httpCode + " for " + path;
        if (httpCode == 404 && path != null && path.startsWith("/v2/trade/")) {
            return fallback + "，当前网关未部署交易接口，请升级 server_v2.py 并重启 8787";
        }
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            JSONObject json = new JSONObject(text);
            JSONObject detail = json.optJSONObject("detail");
            if (detail != null) {
                return buildStructuredFailureMessage(httpCode, path, detail, fallback);
            }
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                return buildStructuredFailureMessage(httpCode, path, error, fallback);
            }
        } catch (Exception ignored) {
        }
        return fallback + " " + text;
    }

    // 统一拼接结构化错误消息。
    private static String buildStructuredFailureMessage(int httpCode,
                                                        String path,
                                                        JSONObject object,
                                                        String fallback) {
        if (object == null) {
            return fallback;
        }
        String code = object.optString("code", "").trim();
        String message = object.optString("message", "").trim();
        if (!code.isEmpty() && !message.isEmpty()) {
            return "HTTP " + httpCode + " for " + path + " [" + code + "] " + message;
        }
        if (!message.isEmpty()) {
            return "HTTP " + httpCode + " for " + path + " " + message;
        }
        if (!code.isEmpty()) {
            return "HTTP " + httpCode + " for " + path + " [" + code + "]";
        }
        return fallback;
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
        if (Double.isNaN(command.getSl()) || Double.isInfinite(command.getSl())) {
            throw new IllegalArgumentException("sl 非法");
        }
        if (Double.isNaN(command.getTp()) || Double.isInfinite(command.getTp())) {
            throw new IllegalArgumentException("tp 非法");
        }
        JSONObject params = mergeTradeParams(command);
        String action = command.getAction().trim().toUpperCase(Locale.ROOT);
        if ("OPEN_MARKET".equals(action)) {
            requirePositive(command.getVolume(), "volume 必须大于 0");
            String side = params.optString("side", "").trim().toLowerCase(Locale.ROOT);
            if (!"buy".equals(side) && !"sell".equals(side)) {
                throw new IllegalArgumentException("side 仅支持 buy/sell");
            }
            return;
        }
        if ("CLOSE_POSITION".equals(action)) {
            requirePositive(command.getVolume(), "volume 必须大于 0");
            return;
        }
        if ("PENDING_ADD".equals(action)) {
            requirePositive(command.getVolume(), "volume 必须大于 0");
            requirePositive(command.getPrice(), "price 必须大于 0");
            if (isBlank(params.optString("orderType", ""))) {
                throw new IllegalArgumentException("orderType 不能为空");
            }
            return;
        }
        if ("PENDING_CANCEL".equals(action)) {
            long orderTicket = params.optLong("orderTicket", params.optLong("orderId", 0L));
            if (orderTicket <= 0L) {
                throw new IllegalArgumentException("orderTicket 不能为空");
            }
            return;
        }
        if ("MODIFY_TPSL".equals(action)) {
            if (command.getSl() <= 0.0d && command.getTp() <= 0.0d) {
                throw new IllegalArgumentException("修改 TP/SL 至少要传一个值");
            }
            return;
        }
        requirePositive(command.getVolume(), "volume 必须大于 0");
        requirePositive(command.getPrice(), "price 必须大于 0");
    }

    // 判断字符串是否为空白。
    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    // 合并基础字段和扩展 params，保证服务端统一从 params 读取也能拿到完整命令。
    private static JSONObject mergeTradeParams(TradeCommand command) {
        JSONObject params = command == null ? new JSONObject() : command.getParams();
        if (params == null) {
            params = new JSONObject();
        }
        if (isBlank(params.optString("symbol", ""))) {
            putQuietly(params, "symbol", command.getSymbol());
        }
        if (!params.has("volume") && command.getVolume() > 0d) {
            putQuietly(params, "volume", command.getVolume());
        }
        if (!params.has("price") && command.getPrice() > 0d) {
            putQuietly(params, "price", command.getPrice());
        }
        if (!params.has("sl") && command.getSl() > 0d) {
            putQuietly(params, "sl", command.getSl());
        }
        if (!params.has("tp") && command.getTp() > 0d) {
            putQuietly(params, "tp", command.getTp());
        }
        return params;
    }

    // 安全写入 JSON，避免把理论上不会发生的格式异常扩散到页面层。
    private static void putQuietly(JSONObject target, String key, Object value) {
        if (target == null || isBlank(key)) {
            return;
        }
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }

    // 统一校验正数。
    private static void requirePositive(double value, String message) {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(message);
        }
    }
}

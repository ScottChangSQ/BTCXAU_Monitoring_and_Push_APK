/*
 * v2 交易命令客户端，负责单笔与批量交易的 check/submit/result 契约访问。
 * 与行情、账户、同步读取客户端完全分离，避免职责混杂。
 */
package com.binance.monitor.data.remote.v2;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.remote.OkHttpTransportResetHelper;
import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradeItemResult;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.BatchTradeReceipt;
import com.binance.monitor.data.model.v2.trade.ExecutionError;
import com.binance.monitor.data.model.v2.trade.TradeAuditEntry;
import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeReceipt;
import com.binance.monitor.util.GatewayAuthRequestHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    private volatile OkHttpClient client;
    @Nullable
    private final ConfigManager configManager;

    // 创建不依赖 Context 的交易客户端实例。
    public GatewayV2TradeClient() {
        this.client = buildClient();
        this.configManager = null;
    }

    // 创建依赖配置中心的交易客户端实例。
    public GatewayV2TradeClient(@Nullable Context context) {
        this.client = buildClient();
        this.configManager = context == null
                ? null
                : ConfigManager.getInstance(context.getApplicationContext());
    }

    // 前后台恢复后重建交易 HTTP 传输层，避免交易命令继续复用失活连接池。
    public synchronized void resetTransport() {
        OkHttpClient previous = client;
        client = buildClient();
        OkHttpTransportResetHelper.closeClientAsync(previous);
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

    // 构建批量交易请求体。
    public static JSONObject buildBatchTradePlanPayload(BatchTradePlan plan) {
        validateBatchTradePlan(plan);
        JSONObject payload = new JSONObject();
        JSONArray items = new JSONArray();
        for (BatchTradeItem item : plan.getItems()) {
            items.put(buildBatchTradeItemPayload(item));
        }
        putQuietly(payload, "batchId", plan.getBatchId());
        putQuietly(payload, "strategy", plan.getStrategy());
        putQuietly(payload, "accountMode", plan.getAccountMode());
        putQuietly(payload, "items", items);
        return payload;
    }

    // 解析批量交易回执。
    public static BatchTradeReceipt parseBatchTradeReceipt(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        JSONArray itemsArray = json.optJSONArray("items");
        List<BatchTradeItemResult> items = new ArrayList<>();
        if (itemsArray != null) {
            for (int index = 0; index < itemsArray.length(); index++) {
                JSONObject item = itemsArray.optJSONObject(index);
                if (item == null) {
                    continue;
                }
                items.add(new BatchTradeItemResult(
                        item.optString("itemId", ""),
                        item.optString("action", ""),
                        "",
                        item.optString("status", ""),
                        parseExecutionError(item.optJSONObject("error")),
                        item.optJSONObject("check"),
                        item.optJSONObject("result"),
                        item.optString("groupKey", "")
                ));
            }
        }
        return new BatchTradeReceipt(
                json.optString("batchId", ""),
                json.optString("strategy", ""),
                json.optString("accountMode", ""),
                json.optString("status", ""),
                parseExecutionError(json.optJSONObject("error")),
                items,
                json.optLong("serverTime", 0L)
        );
    }

    // 解析最近交易审计列表。
    @NonNull
    public static List<TradeAuditEntry> parseTradeAuditRecent(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        return parseTradeAuditItems(json.optJSONArray("items"));
    }

    // 解析单条 trace 的交易审计时间线。
    @NonNull
    public static List<TradeAuditEntry> parseTradeAuditLookup(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        return parseTradeAuditItems(json.optJSONArray("items"));
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

    // 请求 /v2/trade/batch/submit。
    public BatchTradeReceipt submitBatch(BatchTradePlan plan) throws Exception {
        return parseBatchTradeReceipt(post("/v2/trade/batch/submit", buildBatchTradePlanPayload(plan).toString()));
    }

    // 请求 /v2/trade/batch/result。
    public BatchTradeReceipt batchResult(String batchId) throws Exception {
        String safeId = batchId == null ? "" : batchId;
        String path = "/v2/trade/batch/result?batchId=" + URLEncoder.encode(safeId, StandardCharsets.UTF_8);
        return parseBatchTradeReceipt(get(path));
    }

    // 请求最近交易审计。
    @NonNull
    public List<TradeAuditEntry> auditRecent(int limit) throws Exception {
        String path = "/v2/trade/audit/recent?limit=" + Math.max(1, limit);
        return parseTradeAuditRecent(get(path));
    }

    // 按 requestId 或 batchId 查询交易审计。
    @NonNull
    public List<TradeAuditEntry> auditLookup(@Nullable String id) throws Exception {
        String safeId = id == null ? "" : id;
        String path = "/v2/trade/audit/lookup?id=" + URLEncoder.encode(safeId, StandardCharsets.UTF_8);
        return parseTradeAuditLookup(get(path));
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

    @NonNull
    private static List<TradeAuditEntry> parseTradeAuditItems(@Nullable JSONArray array) {
        List<TradeAuditEntry> entries = new ArrayList<>();
        if (array == null) {
            return entries;
        }
        for (int index = 0; index < array.length(); index++) {
            entries.add(TradeAuditEntry.fromJson(array.optJSONObject(index)));
        }
        return entries;
    }

    // 执行 GET 请求。
    private String get(String path) throws Exception {
        String url = resolveBaseUrl() + path;
        Request request = GatewayAuthRequestHelper
                .applyGatewayAuth(new Request.Builder().url(url), configManager)
                .get()
                .build();
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
        Request request = GatewayAuthRequestHelper
                .applyGatewayAuth(new Request.Builder().url(url), configManager)
                .post(body)
                .build();
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

    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
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
            long positionTicket = params.optLong("positionTicket", 0L);
            if (positionTicket <= 0L) {
                throw new IllegalArgumentException("positionTicket 不能为空");
            }
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
        if ("PENDING_MODIFY".equals(action)) {
            long orderTicket = params.optLong("orderTicket", params.optLong("orderId", 0L));
            if (orderTicket <= 0L) {
                throw new IllegalArgumentException("orderTicket 不能为空");
            }
            if (command.getPrice() <= 0.0d && command.getSl() <= 0.0d && command.getTp() <= 0.0d) {
                throw new IllegalArgumentException("修改挂单至少要传一个值");
            }
            return;
        }
        if ("MODIFY_TPSL".equals(action)) {
            long positionTicket = params.optLong("positionTicket", 0L);
            if (positionTicket <= 0L) {
                throw new IllegalArgumentException("positionTicket 不能为空");
            }
            if (command.getSl() <= 0.0d && command.getTp() <= 0.0d) {
                throw new IllegalArgumentException("修改 TP/SL 至少要传一个值");
            }
            return;
        }
        if ("CLOSE_BY".equals(action)) {
            long positionTicket = params.optLong("positionTicket", 0L);
            long oppositePositionTicket = params.optLong("oppositePositionTicket", 0L);
            if (positionTicket <= 0L) {
                throw new IllegalArgumentException("positionTicket 不能为空");
            }
            if (oppositePositionTicket <= 0L) {
                throw new IllegalArgumentException("oppositePositionTicket 不能为空");
            }
            return;
        }
        requirePositive(command.getVolume(), "volume 必须大于 0");
        requirePositive(command.getPrice(), "price 必须大于 0");
    }

    // 构建单项批量请求体。
    private static JSONObject buildBatchTradeItemPayload(@Nullable BatchTradeItem item) {
        if (item == null) {
            throw new IllegalArgumentException("item 不能为空");
        }
        if (isBlank(item.getItemId())) {
            throw new IllegalArgumentException("itemId 不能为空");
        }
        JSONObject itemPayload = new JSONObject();
        JSONObject commandPayload = buildTradeCommandPayload(item.getCommand());
        putQuietly(itemPayload, "itemId", item.getItemId());
        putQuietly(itemPayload, "action", item.getAction());
        putQuietly(itemPayload, "params", commandPayload.optJSONObject("params"));
        if (!isBlank(item.getGroupKey())) {
            putQuietly(itemPayload, "groupKey", item.getGroupKey());
        }
        return itemPayload;
    }

    // 批量提交前执行最小校验。
    private static void validateBatchTradePlan(@Nullable BatchTradePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan 不能为空");
        }
        if (isBlank(plan.getBatchId())) {
            throw new IllegalArgumentException("batchId 不能为空");
        }
        if (isBlank(plan.getStrategy())) {
            throw new IllegalArgumentException("strategy 不能为空");
        }
        if (plan.getItems().isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }
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

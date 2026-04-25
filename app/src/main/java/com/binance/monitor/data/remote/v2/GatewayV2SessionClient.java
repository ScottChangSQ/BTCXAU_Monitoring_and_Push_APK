/*
 * v2 会话客户端，负责请求和解析会话状态、公钥与登录切换接口。
 * 与交易、行情客户端保持分层，避免在同一类里混合职责。
 */
package com.binance.monitor.data.remote.v2;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.remote.OkHttpTransportResetHelper;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.session.SessionPublicKeyPayload;
import com.binance.monitor.data.model.v2.session.SessionReceipt;
import com.binance.monitor.data.model.v2.session.SessionStatusPayload;
import com.binance.monitor.security.SessionCredentialEncryptor;
import com.binance.monitor.util.GatewayAuthRequestHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GatewayV2SessionClient {
    private static final long CONNECT_TIMEOUT_SECONDS = 8L;
    // 服务端 MT5 登录预算允许扩到 120s，客户端读超时必须覆盖探针退出和响应回传余量。
    private static final long READ_TIMEOUT_SECONDS = 135L;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private volatile OkHttpClient client;
    @Nullable
    private final ConfigManager configManager;

    // 保留会话接口结构化失败回执，便于上层继续展示 requestId/stage/loginError。
    public static final class SessionHttpException extends IOException {
        @NonNull
        private final String requestId;
        @Nullable
        private final SessionReceipt receipt;

        public SessionHttpException(@NonNull String message,
                                    @Nullable String requestId,
                                    @Nullable SessionReceipt receipt) {
            super(message == null ? "" : message);
            this.requestId = requestId == null ? "" : requestId.trim();
            this.receipt = receipt;
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

    // 创建不依赖 Context 的会话客户端实例。
    public GatewayV2SessionClient() {
        this.client = buildClient();
        this.configManager = null;
    }

    // 创建依赖配置中心的会话客户端实例。
    public GatewayV2SessionClient(@Nullable Context context) {
        this.client = buildClient();
        this.configManager = context == null
                ? null
                : ConfigManager.getInstance(context.getApplicationContext());
    }

    // 前后台恢复后重建会话 HTTP 传输层，避免登录/状态查询继续占用失活连接池。
    public synchronized void resetTransport() {
        OkHttpClient previous = client;
        client = buildClient();
        OkHttpTransportResetHelper.closeClientAsync(previous);
    }

    // 解析 /v2/session/public-key 响应。
    public static SessionPublicKeyPayload parseSessionPublicKey(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        List<RemoteAccountProfile> savedAccounts = parseProfiles(json.optJSONArray("savedAccounts"));
        int savedCount = json.has("savedAccountCount")
                ? json.optInt("savedAccountCount", savedAccounts.size())
                : savedAccounts.size();
        return new SessionPublicKeyPayload(
                json.optBoolean("ok", false),
                json.optString("keyId", ""),
                json.optString("algorithm", ""),
                json.optString("publicKeyPem", ""),
                json.optLong("expiresAt", 0L),
                parseProfile(json.optJSONObject("activeAccount")),
                savedAccounts,
                savedCount,
                safeBody(body)
        );
    }

    // 解析 /v2/session/status 响应。
    public static SessionStatusPayload parseSessionStatus(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        List<RemoteAccountProfile> savedAccounts = parseProfiles(json.optJSONArray("savedAccounts"));
        int savedCount = json.has("savedAccountCount")
                ? json.optInt("savedAccountCount", savedAccounts.size())
                : savedAccounts.size();
        return new SessionStatusPayload(
                json.optBoolean("ok", false),
                json.optString("state", ""),
                parseProfile(json.optJSONObject("activeAccount")),
                savedAccounts,
                savedCount,
                safeBody(body)
        );
    }

    // 解析 login/switch/logout 的统一回执响应。
    public static SessionReceipt parseSessionReceipt(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        RemoteAccountProfile account = parseProfile(json.optJSONObject("activeAccount"));
        return new SessionReceipt(
                json.optBoolean("ok", false),
                json.optString("state", ""),
                json.optString("requestId", ""),
                account,
                json.optString("message", ""),
                json.optString("errorCode", ""),
                json.optBoolean("retryable", false),
                safeBody(body),
                json.optString("stage", ""),
                json.optLong("elapsedMs", 0L),
                parseProfile(json.optJSONObject("baselineAccount")),
                parseProfile(json.optJSONObject("finalAccount")),
                json.optString("loginError", ""),
                parseProfile(json.optJSONObject("lastObservedAccount"))
        );
    }

    // 拉取会话公钥和当前摘要。
    public SessionPublicKeyPayload fetchPublicKey() throws Exception {
        return parseSessionPublicKey(get("/v2/session/public-key"));
    }

    // 拉取当前会话状态。
    public SessionStatusPayload fetchStatus() throws Exception {
        return parseSessionStatus(get("/v2/session/status"));
    }

    // 拉取指定 requestId 或最近一次的会话诊断时间线，供失败提示直接显示服务端证据。
    public String fetchSessionDiagnosticTimeline(@Nullable String requestId) throws Exception {
        String safeRequestId = requestId == null ? "" : requestId.trim();
        String path = safeRequestId.isEmpty()
                ? "/v2/session/diagnostic/latest"
                : "/v2/session/diagnostic/latest?requestId="
                + URLEncoder.encode(safeRequestId, StandardCharsets.UTF_8.name());
        return buildSessionDiagnosticTimeline(get(path));
    }

    // 提交加密登录信封。
    public SessionReceipt login(SessionCredentialEncryptor.LoginEnvelope envelope, boolean saveAccount) throws Exception {
        if (envelope == null) {
            throw new IllegalArgumentException("envelope 不能为空");
        }
        JSONObject payload = new JSONObject();
        payload.put("requestId", envelope.getRequestId());
        payload.put("keyId", envelope.getKeyId());
        payload.put("algorithm", envelope.getAlgorithm());
        payload.put("encryptedKey", envelope.getEncryptedKey());
        payload.put("encryptedPayload", envelope.getEncryptedPayload());
        payload.put("iv", envelope.getIv());
        payload.put("saveAccount", saveAccount);
        return parseSessionReceipt(post("/v2/session/login", payload.toString()));
    }

    // 切换到已保存账号。
    public SessionReceipt switchAccount(String profileId, String requestId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("requestId", requestId == null ? "" : requestId);
        payload.put("accountProfileId", profileId == null ? "" : profileId);
        return parseSessionReceipt(post("/v2/session/switch", payload.toString()));
    }

    // 退出当前账号会话。
    public SessionReceipt logout(String requestId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("requestId", requestId == null ? "" : requestId);
        return parseSessionReceipt(post("/v2/session/logout", payload.toString()));
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
                throw buildHttpFailureException(response.code(), path, responseBody);
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
                throw buildHttpFailureException(response.code(), path, responseBody);
            }
            return responseBody;
        }
    }

    // 解析网关基础地址。
    private String resolveBaseUrl() {
        String baseUrl = configManager == null
                ? AppConstants.MT5_GATEWAY_BASE_URL
                : configManager.getMt5GatewayBaseUrl();
        if (baseUrl == null) {
            return AppConstants.MT5_GATEWAY_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // 保护空响应体解析。
    private static String safeBody(String body) {
        return body == null ? "{}" : body;
    }

    // 会话失败要优先返回人能看懂的原因，不能把整串 HTTP 5xx 原文直接弹给账户页。
    static String buildHttpFailureMessage(int httpCode, String path, @Nullable String body) {
        String fallback = "HTTP " + httpCode + " for " + path;
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            JSONObject json = new JSONObject(text);
            String detailMessage = extractStructuredErrorMessage(json.opt("detail"));
            if (!detailMessage.isEmpty()) {
                return detailMessage;
            }
            String errorMessage = extractStructuredErrorMessage(json.opt("error"));
            if (!errorMessage.isEmpty()) {
                return errorMessage;
            }
        } catch (Exception ignored) {
        }
        return fallback + " " + text;
    }

    // 优先把结构化会话失败回执透传出去，避免上层只能拿到一段普通字符串。
    @NonNull
    private static IOException buildHttpFailureException(int httpCode, @NonNull String path, @Nullable String body) {
        String fallbackMessage = buildHttpFailureMessage(httpCode, path, body);
        SessionHttpException structured = buildStructuredHttpException(body, fallbackMessage);
        return structured == null ? new IOException(fallbackMessage) : structured;
    }

    // 把服务端诊断时间线格式化成人能直接阅读的多行文本。
    @NonNull
    static String buildSessionDiagnosticTimeline(@Nullable String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        JSONArray items = json.optJSONArray("items");
        if (items == null || items.length() == 0) {
            return "";
        }
        String requestId = json.optString("requestId", "").trim();
        StringBuilder builder = new StringBuilder();
        if (!requestId.isEmpty()) {
            builder.append("requestId=").append(requestId);
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String stage = item.optString("stage", "").trim();
            String message = item.optString("message", "").trim();
            if (stage.isEmpty() && message.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            long serverTime = item.optLong("serverTime", 0L);
            if (serverTime > 0L) {
                builder.append('[').append(serverTime).append("] ");
            }
            if (!stage.isEmpty()) {
                builder.append(stage);
            }
            if (!message.isEmpty()) {
                if (!stage.isEmpty()) {
                    builder.append(" - ");
                }
                builder.append(message);
            }
        }
        return builder.toString().trim();
    }

    // 从 HTTP 错误体里恢复结构化会话回执，供 UI 继续显示 stage/requestId 等字段。
    @Nullable
    private static SessionHttpException buildStructuredHttpException(@Nullable String body,
                                                                     @NonNull String fallbackMessage) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(text);
            SessionReceipt receipt = parseStructuredErrorReceipt(json.opt("detail"), text);
            if (receipt == null) {
                receipt = parseStructuredErrorReceipt(json.opt("error"), text);
            }
            if (receipt == null) {
                return null;
            }
            String summary = buildSessionFailureSummary(receipt);
            String message = summary.isEmpty()
                    ? resolveStructuredReceiptMessage(receipt, fallbackMessage)
                    : summary;
            return new SessionHttpException(message, receipt.getRequestId(), receipt);
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    private static String extractStructuredErrorMessage(@Nullable Object payload) {
        if (payload instanceof JSONObject) {
            JSONObject object = (JSONObject) payload;
            String summary = buildSessionFailureSummary(object);
            if (!summary.isEmpty()) {
                return summary;
            }
            String message = object.optString("message", "").trim();
            if (!message.isEmpty()) {
                return message;
            }
            String code = object.optString("code", "").trim();
            if (!code.isEmpty()) {
                return code;
            }
            return "";
        }
        if (payload instanceof String) {
            return ((String) payload).trim();
        }
        return "";
    }

    // 把服务端切号失败 detail 收口成可直接展示的多行摘要。
    @NonNull
    private static String buildSessionFailureSummary(@NonNull JSONObject object) {
        return buildSessionFailureSummary(
                object.optString("message", ""),
                object.optString("stage", ""),
                object.optString("loginError", ""),
                parseProfile(object.optJSONObject("lastObservedAccount"))
        );
    }

    // 结构化异常已经解析成 SessionReceipt 时，沿用同一套摘要格式。
    @NonNull
    private static String buildSessionFailureSummary(@Nullable SessionReceipt receipt) {
        if (receipt == null) {
            return "";
        }
        return buildSessionFailureSummary(
                receipt.getMessage(),
                receipt.getStage(),
                receipt.getLoginError(),
                receipt.getLastObservedAccount()
        );
    }

    // 统一拼装会话失败摘要，避免 JSON/SessionReceipt 两套逻辑分叉。
    @NonNull
    private static String buildSessionFailureSummary(@Nullable String message,
                                                     @Nullable String stage,
                                                     @Nullable String loginError,
                                                     @Nullable RemoteAccountProfile lastObserved) {
        String safeMessage = message == null ? "" : message.trim();
        String safeStage = stage == null ? "" : stage.trim();
        String safeLoginError = loginError == null ? "" : loginError.trim();
        if (safeMessage.isEmpty() && safeStage.isEmpty() && safeLoginError.isEmpty() && lastObserved == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendLine(builder, safeMessage);
        appendLine(builder, safeStage.isEmpty() ? "" : "stage=" + safeStage);
        appendLine(builder, safeLoginError.isEmpty() ? "" : "loginError=" + safeLoginError);
        if (lastObserved != null && !lastObserved.getLogin().trim().isEmpty()) {
            appendLine(
                    builder,
                    "lastObserved=" + lastObserved.getLogin().trim() + " / " + lastObserved.getServer().trim()
            );
        }
        return builder.toString().trim();
    }

    // 统一按行追加字符串，避免手写重复换行判断。
    private static void appendLine(@NonNull StringBuilder builder, @Nullable String line) {
        String safeLine = line == null ? "" : line.trim();
        if (safeLine.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(safeLine);
    }

    // 从 detail/error 对象里恢复会话失败回执，供上层继续拿结构化字段。
    @Nullable
    private static SessionReceipt parseStructuredErrorReceipt(@Nullable Object payload, @NonNull String rawJson) {
        if (!(payload instanceof JSONObject)) {
            return null;
        }
        JSONObject object = (JSONObject) payload;
        SessionReceipt receipt = new SessionReceipt(
                false,
                object.optString("state", "failed"),
                object.optString("requestId", ""),
                parseProfile(object.optJSONObject("activeAccount")),
                object.optString("message", ""),
                object.optString("errorCode", object.optString("code", "")),
                object.optBoolean("retryable", false),
                rawJson,
                object.optString("stage", ""),
                object.optLong("elapsedMs", 0L),
                parseProfile(object.optJSONObject("baselineAccount")),
                parseProfile(object.optJSONObject("finalAccount")),
                object.optString("loginError", ""),
                parseProfile(object.optJSONObject("lastObservedAccount"))
        );
        return hasStructuredReceiptPayload(receipt) ? receipt : null;
    }

    // 过滤掉空壳对象，避免任意 JSON 都被误判成会话结构化异常。
    private static boolean hasStructuredReceiptPayload(@NonNull SessionReceipt receipt) {
        return !receipt.getRequestId().trim().isEmpty()
                || !receipt.getMessage().trim().isEmpty()
                || !receipt.getErrorCode().trim().isEmpty()
                || !receipt.getStage().trim().isEmpty()
                || !receipt.getLoginError().trim().isEmpty()
                || receipt.getBaselineAccount() != null
                || receipt.getFinalAccount() != null
                || receipt.getLastObservedAccount() != null;
    }

    // 结构化回执若没有可展示摘要，则退回 message/errorCode/fallback。
    @NonNull
    private static String resolveStructuredReceiptMessage(@Nullable SessionReceipt receipt,
                                                          @NonNull String fallbackMessage) {
        if (receipt == null) {
            return fallbackMessage;
        }
        if (!receipt.getMessage().trim().isEmpty()) {
            return receipt.getMessage().trim();
        }
        if (!receipt.getErrorCode().trim().isEmpty()) {
            return receipt.getErrorCode().trim();
        }
        return fallbackMessage;
    }

    // 解析单个账号摘要对象。
    @Nullable
    private static RemoteAccountProfile parseProfile(@Nullable JSONObject object) {
        if (object == null) {
            return null;
        }
        String state = object.optString("state", "");
        boolean active = RemoteAccountProfile.resolveActiveFlag(
                object.optBoolean("active", false),
                state
        );
        return new RemoteAccountProfile(
                object.optString("profileId", ""),
                object.optString("login", ""),
                object.optString("loginMasked", ""),
                object.optString("server", ""),
                object.optString("displayName", ""),
                active,
                state
        );
    }

    // 解析账号摘要数组。
    private static List<RemoteAccountProfile> parseProfiles(@Nullable JSONArray array) {
        List<RemoteAccountProfile> profiles = new ArrayList<>();
        if (array == null) {
            return profiles;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            RemoteAccountProfile profile = parseProfile(item);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

}

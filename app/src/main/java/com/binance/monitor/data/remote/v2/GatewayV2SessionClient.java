/*
 * v2 会话客户端，负责请求和解析会话状态、公钥与登录切换接口。
 * 与交易、行情客户端保持分层，避免在同一类里混合职责。
 */
package com.binance.monitor.data.remote.v2;

import android.content.Context;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.remote.OkHttpTransportResetHelper;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.session.SessionPublicKeyPayload;
import com.binance.monitor.data.model.v2.session.SessionReceipt;
import com.binance.monitor.data.model.v2.session.SessionStatusPayload;
import com.binance.monitor.security.SessionCredentialEncryptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
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
    private static final long READ_TIMEOUT_SECONDS = 60L;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private volatile OkHttpClient client;
    @Nullable
    private final ConfigManager configManager;

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
                safeBody(body)
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
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + path + " " + responseBody);
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
                throw new IOException("HTTP " + response.code() + " for " + path + " " + responseBody);
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

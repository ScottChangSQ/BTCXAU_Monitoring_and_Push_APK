/*
 * 异常网关客户端，负责把服务端异常记录和提醒同步到本地，并把阈值配置推送回网关。
 */
package com.binance.monitor.data.remote;

import android.content.Context;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.AbnormalAlertItem;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.util.GatewayUrlResolver;
import com.binance.monitor.util.ProductSymbolMapper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AbnormalGatewayClient {
    private static final String ABNORMAL_ENDPOINT = "/v1/abnormal";
    private static final String ABNORMAL_CONFIG_ENDPOINT = "/v1/abnormal/config";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    @Nullable
    private final ConfigManager configManager;
    private volatile OkHttpClient client;

    public AbnormalGatewayClient() {
        this.configManager = null;
        this.client = buildClient();
    }

    public AbnormalGatewayClient(@Nullable Context context) {
        this.configManager = context == null ? null : ConfigManager.getInstance(context.getApplicationContext());
        this.client = buildClient();
    }

    // 前后台恢复后重建异常同步 HTTP 传输层，避免继续复用失活连接池。
    public synchronized void resetTransport() {
        OkHttpClient previous = client;
        client = buildClient();
        closeClient(previous);
    }

    // 拉取服务端异常记录，首次返回全量，后续根据 syncSeq 请求增量。
    public SyncResult fetch(long sinceSeq) {
        SyncResult result = new SyncResult();
        List<String> errors = new ArrayList<>();
        for (String baseUrl : buildCandidateBaseUrls()) {
            try {
                String endpoint = GatewayUrlResolver.buildEndpoint(baseUrl, ABNORMAL_ENDPOINT)
                        + "?delta=1"
                        + (sinceSeq > 0L ? "&since=" + sinceSeq : "");
                Request request = new Request.Builder().url(endpoint).get().build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        errors.add(baseUrl + " -> HTTP " + response.code());
                        continue;
                    }
                    String body = response.body() == null ? "" : response.body().string();
                    SyncResult parsed = parseSyncBody(body);
                    parsed.baseUrl = GatewayUrlResolver.normalizeBaseUrl(baseUrl, AppConstants.MT5_GATEWAY_BASE_URL);
                    return parsed;
                }
            } catch (Exception exception) {
                errors.add(baseUrl + " -> " + exception.getMessage());
            }
        }
        result.success = false;
        result.error = String.join(" ; ", errors);
        return result;
    }

    // 把当前阈值配置同步给服务端，确保服务端判断口径和设置页一致。
    public PushResult pushConfig(boolean logicAnd, List<SymbolConfig> configs) {
        PushResult result = new PushResult();
        List<String> errors = new ArrayList<>();
        try {
            JSONObject payload = buildConfigPayload(logicAnd, configs);
            for (String baseUrl : buildCandidateBaseUrls()) {
                try {
                    String endpoint = GatewayUrlResolver.buildEndpoint(baseUrl, ABNORMAL_CONFIG_ENDPOINT);
                    RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
                    Request request = new Request.Builder().url(endpoint).post(body).build();
                    try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                            errors.add(baseUrl + " -> HTTP " + response.code());
                            continue;
                        }
                        result.success = true;
                        result.baseUrl = GatewayUrlResolver.normalizeBaseUrl(baseUrl, AppConstants.MT5_GATEWAY_BASE_URL);
                        return result;
                    }
                } catch (Exception exception) {
                    errors.add(baseUrl + " -> " + exception.getMessage());
                }
            }
        } catch (Exception exception) {
            errors.add(exception.getMessage());
        }
        result.success = false;
        result.error = String.join(" ; ", errors);
        return result;
    }

    static SyncResult parseSyncBody(String body) throws Exception {
        JSONObject root = new JSONObject(body == null ? "{}" : body);
        JSONObject meta = root.optJSONObject("abnormalMeta");
        SyncResult result = new SyncResult();
        result.success = true;
        result.syncSeq = meta == null ? 0L : meta.optLong("syncSeq", 0L);
        result.delta = root.optBoolean("isDelta", false);
        result.unchanged = root.optBoolean("unchanged", false);

        JSONArray recordArray;
        JSONArray alertArray;
        if (result.delta) {
            JSONObject delta = root.optJSONObject("delta");
            recordArray = delta == null ? null : delta.optJSONArray("records");
            alertArray = delta == null ? null : delta.optJSONArray("alerts");
        } else {
            recordArray = root.optJSONArray("records");
            alertArray = root.optJSONArray("alerts");
        }

        result.records = parseRecords(recordArray);
        result.alerts = parseAlerts(alertArray);
        return result;
    }

    static JSONObject buildConfigPayload(boolean logicAnd, List<SymbolConfig> configs) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("logicAnd", logicAnd);
        JSONArray array = new JSONArray();
        if (configs != null) {
            for (SymbolConfig item : configs) {
                if (item == null) {
                    continue;
                }
                JSONObject object = new JSONObject();
                object.put("symbol", toGatewaySymbol(item.getSymbol()));
                object.put("volumeThreshold", item.getVolumeThreshold());
                object.put("amountThreshold", item.getAmountThreshold());
                object.put("priceChangeThreshold", item.getPriceChangeThreshold());
                object.put("volumeEnabled", item.isVolumeEnabled());
                object.put("amountEnabled", item.isAmountEnabled());
                object.put("priceChangeEnabled", item.isPriceChangeEnabled());
                array.put(object);
            }
        }
        payload.put("configs", array);
        return payload;
    }

    // 把服务端返回记录转换为客户端统一使用的本地品种代码。
    private static List<AbnormalRecord> parseRecords(@Nullable JSONArray array) throws Exception {
        List<AbnormalRecord> records = new ArrayList<>();
        if (array == null) {
            return records;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String symbol = toLocalSymbol(item.optString("symbol", ""));
            records.add(new AbnormalRecord(
                    item.optString("id", ""),
                    symbol,
                    item.optLong("timestamp", 0L),
                    item.optLong("closeTime", 0L),
                    item.optDouble("openPrice", 0d),
                    item.optDouble("closePrice", 0d),
                    item.optDouble("volume", 0d),
                    item.optDouble("amount", 0d),
                    item.optDouble("priceChange", 0d),
                    item.optDouble("percentChange", 0d),
                    item.optString("triggerSummary", "")
            ));
        }
        return records;
    }

    // 把服务端提醒列表转换成本地模型，并同步修正品种代码。
    private static List<AbnormalAlertItem> parseAlerts(@Nullable JSONArray array) throws Exception {
        List<AbnormalAlertItem> alerts = new ArrayList<>();
        if (array == null) {
            return alerts;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            AbnormalAlertItem parsed = AbnormalAlertItem.fromJson(item);
            List<String> symbols = new ArrayList<>();
            for (String symbol : parsed.getSymbols()) {
                symbols.add(toLocalSymbol(symbol));
            }
            alerts.add(new AbnormalAlertItem(
                    parsed.getId(),
                    symbols,
                    parsed.getTitle(),
                    parsed.getContent(),
                    parsed.getCloseTime(),
                    parsed.getCreatedAt()
            ));
        }
        return alerts;
    }

    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .writeTimeout(6, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    // 释放旧异常同步 transport 的连接池和调度线程，避免反复重建后残留旧资源。
    private static void closeClient(@Nullable OkHttpClient previous) {
        if (previous == null) {
            return;
        }
        previous.connectionPool().evictAll();
        previous.dispatcher().executorService().shutdown();
    }

    private List<String> buildCandidateBaseUrls() {
        String configuredBaseUrl = configManager != null
                ? configManager.getMt5GatewayBaseUrl()
                : AppConstants.MT5_GATEWAY_BASE_URL;
        return resolveCandidateBaseUrls(configuredBaseUrl);
    }

    // 入口唯一化后，异常链只能请求单一配置入口。
    static List<String> resolveCandidateBaseUrls(@Nullable String configuredBaseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        String primary = GatewayUrlResolver.normalizeBaseUrl(configuredBaseUrl, AppConstants.MT5_GATEWAY_BASE_URL);
        urls.add(primary);
        List<String> result = new ArrayList<>();
        for (String url : urls) {
            result.add(GatewayUrlResolver.normalizeBaseUrl(url, AppConstants.MT5_GATEWAY_BASE_URL));
        }
        return result;
    }

    static String toLocalSymbol(String symbol) {
        return ProductSymbolMapper.toMarketSymbol(symbol);
    }

    static String toGatewaySymbol(String symbol) {
        return ProductSymbolMapper.toMarketSymbol(symbol);
    }

    public static class SyncResult {
        private boolean success;
        private boolean delta;
        private boolean unchanged;
        private long syncSeq;
        private String error = "";
        private String baseUrl = "";
        private List<AbnormalRecord> records = new ArrayList<>();
        private List<AbnormalAlertItem> alerts = new ArrayList<>();

        public boolean isSuccess() {
            return success;
        }

        public boolean isDelta() {
            return delta;
        }

        public boolean isUnchanged() {
            return unchanged;
        }

        public long getSyncSeq() {
            return syncSeq;
        }

        public String getError() {
            return error;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public List<AbnormalRecord> getRecords() {
            return records;
        }

        public List<AbnormalAlertItem> getAlerts() {
            return alerts;
        }
    }

    public static class PushResult {
        private boolean success;
        private String error = "";
        private String baseUrl = "";

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }

        public String getBaseUrl() {
            return baseUrl;
        }
    }
}

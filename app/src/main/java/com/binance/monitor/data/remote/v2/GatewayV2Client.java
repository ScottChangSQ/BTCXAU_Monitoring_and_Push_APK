/*
 * v2 网关客户端，负责请求和解析 `/v2/market/*`、`/v2/account/*` 接口。
 * 图表页和账户页后续会统一通过这里读取服务端真值，而不是各自拼接口。
 */
package com.binance.monitor.data.remote.v2;

import android.content.Context;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.v2.AccountHistoryPayload;
import com.binance.monitor.data.model.v2.AccountFullPayload;
import com.binance.monitor.data.model.v2.AccountSnapshotPayload;
import com.binance.monitor.data.model.v2.MarketSeriesPayload;
import com.binance.monitor.data.model.v2.MarketSnapshotPayload;
import com.binance.monitor.data.remote.OkHttpTransportResetHelper;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.util.GatewayAuthRequestHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GatewayV2Client {
    private static final long CONNECT_TIMEOUT_SECONDS = 8L;
    private static final long READ_TIMEOUT_SECONDS = 35L;

    private volatile OkHttpClient client;
    @Nullable
    private final ConfigManager configManager;

    public GatewayV2Client() {
        this.client = buildClient();
        this.configManager = null;
    }

    public GatewayV2Client(@Nullable Context context) {
        this.client = buildClient();
        this.configManager = context == null
                ? null
                : ConfigManager.getInstance(context.getApplicationContext());
    }

    // 前后台恢复后重建 HTTP 传输层，避免系统网络切换后继续复用失活连接池。
    public synchronized void resetTransport() {
        OkHttpClient previous = client;
        client = buildClient();
        OkHttpTransportResetHelper.closeClientAsync(previous);
    }

    // 解析 v2 market snapshot，供测试和页面预加载复用。
    public static MarketSnapshotPayload parseMarketSnapshot(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        return new MarketSnapshotPayload(
                json.optLong("serverTime"),
                json.optString("syncToken", ""),
                json.optJSONObject("market"),
                json.optJSONObject("account"),
                safeBody(body)
        );
    }

    // 解析 v2 market candles 响应，闭合 K 线和 patch 保持分层。
    public static MarketSeriesPayload parseMarketSeries(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        JSONArray candlesArray = json.optJSONArray("candles");
        List<CandleEntry> candles = new ArrayList<>();
        if (candlesArray != null) {
            for (int i = 0; i < candlesArray.length(); i++) {
                JSONObject item = candlesArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                candles.add(CandleEntry.fromJson(item));
            }
        }
        JSONObject latestPatchObject = json.optJSONObject("latestPatch");
        CandleEntry latestPatch = latestPatchObject == null ? null : CandleEntry.fromJson(latestPatchObject);
        return new MarketSeriesPayload(
                json.optString("symbol", ""),
                json.optString("interval", ""),
                json.optLong("serverTime"),
                candles,
                latestPatch,
                json.optString("nextSyncToken", ""),
                safeBody(body)
        );
    }

    // 解析 v2 account snapshot，保留账户区和当前持仓/挂单原始载荷。
    public static AccountSnapshotPayload parseAccountSnapshot(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        JSONObject accountMeta = requireObject(json, "accountMeta", "v2 account snapshot");
        JSONObject account = requireObject(json, "account", "v2 account snapshot");
        JSONArray positions = requireArray(json, "positions", "v2 account snapshot");
        JSONArray orders = requireArray(json, "orders", "v2 account snapshot");
        return new AccountSnapshotPayload(
                extractServerTime(json, accountMeta),
                extractSyncToken(json, accountMeta),
                accountMeta,
                account,
                json.optJSONArray("overviewMetrics"),
                json.optJSONArray("curveIndicators"),
                json.optJSONArray("statsMetrics"),
                positions,
                orders,
                safeBody(body)
        );
    }

    // 解析 v2 account history，保留交易、历史挂单和净值曲线。
    public static AccountHistoryPayload parseAccountHistory(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        JSONObject accountMeta = requireObject(json, "accountMeta", "v2 account history");
        JSONArray trades = requireArray(json, "trades", "v2 account history");
        JSONArray orders = requireArray(json, "orders", "v2 account history");
        JSONArray curvePoints = requireArray(json, "curvePoints", "v2 account history");
        return new AccountHistoryPayload(
                extractServerTime(json, accountMeta),
                extractSyncToken(json, accountMeta),
                accountMeta,
                json.optJSONArray("overviewMetrics"),
                json.optJSONArray("curveIndicators"),
                json.optJSONArray("statsMetrics"),
                trades,
                orders,
                curvePoints,
                json.optString("nextCursor", ""),
                safeBody(body)
        );
    }

    // 解析 v2 account full，保留当前运行态与历史主体，供强一致刷新复用。
    public static AccountFullPayload parseAccountFull(String body) throws Exception {
        JSONObject json = new JSONObject(safeBody(body));
        JSONObject accountMeta = requireObject(json, "accountMeta", "v2 account full");
        JSONObject account = requireObject(json, "account", "v2 account full");
        JSONArray positions = requireArray(json, "positions", "v2 account full");
        JSONArray orders = requireArray(json, "orders", "v2 account full");
        JSONArray trades = requireArray(json, "trades", "v2 account full");
        JSONArray curvePoints = requireArray(json, "curvePoints", "v2 account full");
        return new AccountFullPayload(
                extractServerTime(json, accountMeta),
                extractSyncToken(json, accountMeta),
                accountMeta,
                account,
                json.optJSONArray("overviewMetrics"),
                json.optJSONArray("curveIndicators"),
                json.optJSONArray("statsMetrics"),
                positions,
                orders,
                trades,
                curvePoints,
                safeBody(body)
        );
    }

    // 请求 market snapshot，供主线程后续接入图表页时直接使用。
    public MarketSnapshotPayload fetchMarketSnapshot() throws Exception {
        return parseMarketSnapshot(get("/v2/market/snapshot"));
    }

    // 请求指定品种与周期的闭合 K 线和 patch。
    public MarketSeriesPayload fetchMarketSeries(String symbol, String interval, int limit) throws Exception {
        String path = "/v2/market/candles?symbol=" + symbol + "&interval=" + interval + "&limit=" + limit;
        return parseMarketSeries(get(path));
    }

    // 请求指定品种与周期在某个时间点之前的窗口，供左滑历史分页复用。
    public MarketSeriesPayload fetchMarketSeriesBefore(String symbol,
                                                       String interval,
                                                       int limit,
                                                       long endTimeInclusive) throws Exception {
        String path = "/v2/market/candles?symbol=" + symbol
                + "&interval=" + interval
                + "&limit=" + limit
                + "&endTime=" + Math.max(0L, endTimeInclusive);
        return parseMarketSeries(get(path));
    }

    // 请求指定品种与周期在某个时间点之后的窗口，供增量补尾复用。
    public MarketSeriesPayload fetchMarketSeriesAfter(String symbol,
                                                      String interval,
                                                      int limit,
                                                      long startTimeInclusive) throws Exception {
        String path = "/v2/market/candles?symbol=" + symbol
                + "&interval=" + interval
                + "&limit=" + limit
                + "&startTime=" + Math.max(0L, startTimeInclusive);
        return parseMarketSeries(get(path));
    }

    // 请求账户历史交易、挂单历史和净值曲线。
    public AccountHistoryPayload fetchAccountHistory(AccountTimeRange range, String cursor) throws Exception {
        return fetchAccountHistory(mapRangeKey(range), cursor);
    }

    // 请求账户当前运行态快照，供页面主动刷新与交易后强一致确认复用。
    public AccountSnapshotPayload fetchAccountSnapshot() throws Exception {
        return parseAccountSnapshot(get("/v2/account/snapshot"));
    }

    // 请求账户单次强一致完整快照，供账户页主动刷新与交易后确认复用。
    public AccountFullPayload fetchAccountFull() throws Exception {
        return parseAccountFull(get("/v2/account/full"));
    }

    // 请求账户历史交易、挂单历史和净值曲线。
    public AccountHistoryPayload fetchAccountHistory(String range, String cursor) throws Exception {
        String rangeKey = (range == null || range.trim().isEmpty()) ? "all" : range.trim().toLowerCase();
        String encodedCursor = encodeQuery(cursor);
        String path = "/v2/account/history?range=" + rangeKey + "&cursor=" + encodedCursor;
        return parseAccountHistory(get(path));
    }

    private String get(String path) throws Exception {
        String baseUrl = resolveBaseUrl();
        String url = baseUrl + path;
        Request request = GatewayAuthRequestHelper
                .applyGatewayAuth(new Request.Builder().url(url), configManager)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " for " + path + " " + responseBody);
            }
            return responseBody;
        }
    }

    private String resolveBaseUrl() {
        String baseUrl = configManager == null
                ? AppConstants.MT5_GATEWAY_BASE_URL
                : configManager.getMt5GatewayBaseUrl();
        if (baseUrl == null) {
            return AppConstants.MT5_GATEWAY_BASE_URL;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String safeBody(String body) {
        return body == null ? "{}" : body;
    }

    private static JSONObject requireObject(JSONObject root, String key, String context) {
        JSONObject value = root == null ? null : root.optJSONObject(key);
        if (value == null) {
            throw new IllegalStateException(context + " missing " + key + " object");
        }
        return value;
    }

    private static JSONArray requireArray(JSONObject root, String key, String context) {
        JSONArray value = root == null ? null : root.optJSONArray(key);
        if (value == null) {
            throw new IllegalStateException(context + " missing " + key + " array");
        }
        return value;
    }

    private static long extractServerTime(JSONObject root, @Nullable JSONObject meta) {
        if (root != null && root.has("serverTime")) {
            return root.optLong("serverTime", 0L);
        }
        return meta == null ? 0L : meta.optLong("serverTime", 0L);
    }

    private static String extractSyncToken(JSONObject root, @Nullable JSONObject meta) {
        if (root != null && root.has("syncToken")) {
            return root.optString("syncToken", "");
        }
        return meta == null ? "" : meta.optString("syncToken", "");
    }

    private static String mapRangeKey(@Nullable AccountTimeRange range) {
        if (range == null) {
            return "all";
        }
        switch (range) {
            case D1:
                return "1d";
            case D7:
                return "7d";
            case M1:
                return "1m";
            case M3:
                return "3m";
            case Y1:
                return "1y";
            case ALL:
            default:
                return "all";
        }
    }

    private static String encodeQuery(@Nullable String value) {
        String safe = value == null ? "" : value;
        return URLEncoder.encode(safe, StandardCharsets.UTF_8);
    }

    private static OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

}

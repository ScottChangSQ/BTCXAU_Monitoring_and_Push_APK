package com.binance.monitor.ui.account;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Mt5BridgeGatewayClient {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build();

    private final List<String> baseUrls;

    public Mt5BridgeGatewayClient() {
        Set<String> urls = new LinkedHashSet<>();
        urls.add(AppConstants.MT5_GATEWAY_BASE_URL);
        urls.add("http://10.0.2.2:8787");
        urls.add("http://127.0.0.1:8787");
        urls.add("http://localhost:8787");
        baseUrls = new ArrayList<>(urls);
    }

    public SnapshotResult fetch(AccountTimeRange range) {
        SnapshotResult result = new SnapshotResult();
        List<String> errors = new ArrayList<>();
        for (String baseUrl : baseUrls) {
            String normalizedBase = normalizeBaseUrl(baseUrl);
            String url = normalizedBase + "/v1/snapshot?range=" + mapRange(range);
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    errors.add(normalizedBase + " -> HTTP " + response.code());
                    continue;
                }
                String body = response.body() == null ? "" : response.body().string();
                if (body.isEmpty()) {
                    errors.add(normalizedBase + " -> Empty response");
                    continue;
                }

                JSONObject root = new JSONObject(body);
                JSONObject meta = root.optJSONObject("accountMeta");
                result.account = meta == null ? "" : meta.optString("login", "");
                result.server = meta == null ? "" : meta.optString("server", "");
                result.source = meta == null ? "MT5网关" : meta.optString("source", "MT5网关");
                result.updatedAt = meta == null ? 0L : meta.optLong("updatedAt", 0L);
                result.connectedBaseUrl = normalizedBase;

                List<AccountMetric> overview = parseMetrics(root.optJSONArray("overviewMetrics"));
                List<CurvePoint> curves = parseCurvePoints(root.optJSONArray("curvePoints"));
                List<AccountMetric> indicators = parseMetrics(root.optJSONArray("curveIndicators"));
                List<PositionItem> positions = parsePositions(root.optJSONArray("positions"));
                List<TradeRecordItem> trades = parseTrades(root.optJSONArray("trades"));
                List<AccountMetric> stats = parseMetrics(root.optJSONArray("statsMetrics"));

                result.snapshot = new AccountSnapshot(overview, curves, indicators, positions, trades, stats);
                result.success = true;
                return result;
            } catch (Exception exception) {
                errors.add(normalizedBase + " -> " + exception.getMessage());
            }
        }
        result.error = String.join(" ; ", errors);
        return result;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "http://10.0.2.2:8787";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String mapRange(AccountTimeRange range) {
        if (range == null) {
            return "7d";
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

    private List<AccountMetric> parseMetrics(JSONArray array) {
        List<AccountMetric> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            list.add(new AccountMetric(
                    MetricNameTranslator.toChinese(item.optString("name", "--")),
                    item.optString("value", "--")));
        }
        return list;
    }

    private List<CurvePoint> parseCurvePoints(JSONArray array) {
        List<CurvePoint> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            list.add(new CurvePoint(
                    item.optLong("timestamp", 0L),
                    item.optDouble("equity", 0d),
                    item.optDouble("balance", 0d)));
        }
        return list;
    }

    private List<PositionItem> parsePositions(JSONArray array) {
        List<PositionItem> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            list.add(new PositionItem(
                    item.optString("productName", "--"),
                    item.optString("code", "--"),
                    item.optString("side", "Buy"),
                    item.optDouble("quantity", 0d),
                    item.optDouble("sellableQuantity", 0d),
                    item.optDouble("costPrice", 0d),
                    item.optDouble("latestPrice", 0d),
                    item.optDouble("marketValue", 0d),
                    item.optDouble("positionRatio", 0d),
                    item.optDouble("dayPnL", 0d),
                    item.optDouble("totalPnL", 0d),
                    item.optDouble("returnRate", 0d),
                    item.optDouble("pendingLots", 0d),
                    item.optInt("pendingCount", 0)));
        }
        return list;
    }

    private List<TradeRecordItem> parseTrades(JSONArray array) {
        List<TradeRecordItem> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            list.add(new TradeRecordItem(
                    item.optLong("timestamp", 0L),
                    item.optString("productName", "--"),
                    item.optString("code", "--"),
                    item.optString("side", "Buy"),
                    item.optDouble("price", 0d),
                    item.optDouble("quantity", 0d),
                    item.optDouble("amount", 0d),
                    item.optDouble("fee", 0d),
                    item.optString("remark", "")));
        }
        return list;
    }

    public static class SnapshotResult {
        private boolean success;
        private String account = "";
        private String server = "";
        private String source = "";
        private long updatedAt;
        private String connectedBaseUrl = "";
        private String error = "";
        private AccountSnapshot snapshot;

        public boolean isSuccess() {
            return success && snapshot != null;
        }

        public String getError() {
            return error == null ? "" : error;
        }

        public AccountSnapshot getSnapshot() {
            return snapshot;
        }

        public String buildMetaLine(String defaultAccount, String defaultServer) {
            String accountText = account.isEmpty() ? defaultAccount : account;
            String serverText = server.isEmpty() ? defaultServer : server;
            String sourceText = localizeSource(source);
            String updateText = updatedAt <= 0L
                    ? "--"
                    : new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(updatedAt);
            String endpoint = connectedBaseUrl.isEmpty() ? "未识别" : connectedBaseUrl;
            return "账号 " + accountText
                    + " | 服务器 " + serverText
                    + " | 数据源 " + sourceText
                    + " | 网关 " + endpoint
                    + " | 更新时间 " + updateText;
        }

        private String localizeSource(String source) {
            if (source == null || source.trim().isEmpty()) {
                return "MT5网关";
            }
            String normalized = source.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("fallback") || normalized.contains("offline")) {
                return "历史数据（网关离线）";
            }
            if ("mt5 gateway".equals(normalized)) {
                return "MT5网关";
            }
            return source;
        }
    }
}

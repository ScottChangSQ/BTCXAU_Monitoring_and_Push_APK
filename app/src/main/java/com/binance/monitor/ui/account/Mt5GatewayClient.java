package com.binance.monitor.ui.account;

import android.content.Context;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.util.GatewayUrlResolver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Mt5GatewayClient {

    private final ConfigManager configManager;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build();

    public Mt5GatewayClient() {
        this.configManager = null;
    }

    public Mt5GatewayClient(Context context) {
        this.configManager = context == null
                ? null
                : ConfigManager.getInstance(context.getApplicationContext());
    }

    public SnapshotResult fetch(AccountStatsRepository.TimeRange range) {
        SnapshotResult result = new SnapshotResult();
        String url = GatewayUrlResolver.buildEndpoint(resolveBaseUrl(), "/v1/snapshot")
                + "?range=" + mapRange(range);
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                result.error = "HTTP " + response.code();
                return result;
            }
            String body = response.body() == null ? "" : response.body().string();
            if (body.isEmpty()) {
                result.error = "empty response";
                return result;
            }
            JSONObject root = new JSONObject(body);
            JSONObject meta = root.optJSONObject("accountMeta");
            result.account = meta == null ? "" : meta.optString("login", "");
            result.server = meta == null ? "" : meta.optString("server", "");
            result.source = meta == null ? "mt5-gateway" : meta.optString("source", "mt5-gateway");
            result.updatedAt = meta == null ? 0L : meta.optLong("updatedAt", 0L);

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
            result.error = exception.getMessage();
            return result;
        }
    }

    private String resolveBaseUrl() {
        if (configManager == null) {
            return GatewayUrlResolver.normalizeBaseUrl(AppConstants.MT5_GATEWAY_BASE_URL, "http://10.0.2.2:8787");
        }
        return configManager.getMt5GatewayBaseUrl();
    }

    private String mapRange(AccountStatsRepository.TimeRange range) {
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
                    item.optString("name", "--"),
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
                    normalizeEpochMs(optLongAny(item, 0L, "timestamp", "time")),
                    item.optDouble("equity", 0d),
                    item.optDouble("balance", 0d)
            ));
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
                    item.optLong("positionTicket", item.optLong("ticket", 0L)),
                    item.optLong("orderId", 0L),
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
                    item.optInt("pendingCount", 0),
                    item.optDouble("pendingPrice", 0d),
                    optDoubleAny(item, 0d, "takeProfit", "tp", "tpPrice", "take_profit"),
                    optDoubleAny(item, 0d, "stopLoss", "sl", "slPrice", "stop_loss"),
                    optDoubleAny(item, 0d, "storageFee", "swap", "storage", "swapFee")
            ));
        }
        return list;
    }

    private double optDoubleAny(JSONObject item, double fallback, String... keys) {
        if (item == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty() || !item.has(key)) {
                continue;
            }
            Object value = item.opt(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            if (value instanceof String) {
                try {
                    return Double.parseDouble(((String) value).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return fallback;
    }

    private long optLongAny(JSONObject item, long fallback, String... keys) {
        if (item == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty() || !item.has(key)) {
                continue;
            }
            Object value = item.opt(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                try {
                    return Long.parseLong(((String) value).trim());
                } catch (Exception ignored) {
                }
            }
        }
        return fallback;
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
            double price = item.optDouble("price", 0d);
            double openPrice = optDoubleAny(item, price,
                    "openPrice", "open_price", "open", "priceOpen", "entryPrice", "entry_price");
            double closePrice = optDoubleAny(item, price,
                    "closePrice", "close_price", "close", "priceClose", "exitPrice", "exit_price");
            long timestampMs = normalizeEpochMs(optLongAny(item, 0L, "timestamp", "time"));
            long openTimeMs = normalizeEpochMs(optLongAny(item, timestampMs,
                    "openTime", "open_time", "timeOpen", "time_open"));
            long closeTimeMs = normalizeEpochMs(optLongAny(item, timestampMs,
                    "closeTime", "close_time", "timeClose", "time_close"));
            list.add(new TradeRecordItem(
                    timestampMs,
                    item.optString("productName", "--"),
                    item.optString("code", "--"),
                    item.optString("side", "买入"),
                    price,
                    item.optDouble("quantity", 0d),
                    item.optDouble("amount", 0d),
                    item.optDouble("fee", 0d),
                    item.optString("remark", ""),
                    item.optDouble("profit", 0d),
                    openTimeMs,
                    closeTimeMs,
                    item.optDouble("storageFee", item.optDouble("fee", 0d)),
                    openPrice,
                    closePrice,
                    item.optLong("dealTicket", 0L),
                    item.optLong("orderId", 0L),
                    item.optLong("positionId", 0L),
                    item.optInt("entryType", 0)
            ));
        }
        return list;
    }

    // 统一把秒级时间戳修正为毫秒，避免不同网关口径混用时历史成交时间漂移。
    private long normalizeEpochMs(long value) {
        if (value <= 0L) {
            return 0L;
        }
        return value < 10_000_000_000L ? value * 1000L : value;
    }

    public static class SnapshotResult {
        private boolean success;
        private String account = "";
        private String server = "";
        private String source = "";
        private long updatedAt;
        private String error = "";
        private AccountSnapshot snapshot;

        public boolean isSuccess() {
            return success && snapshot != null;
        }

        public String getAccount() {
            return account;
        }

        public String getServer() {
            return server;
        }

        public String getSource() {
            return source;
        }

        public long getUpdatedAt() {
            return updatedAt;
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
            String sourceText = source.isEmpty() ? "mt5-gateway" : source;
            String updateText = updatedAt <= 0L ? "--" :
                    new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(updatedAt);
            return "账号 " + accountText + "  |  服务器 " + serverText + "  |  数据源 " + sourceText + "  |  更新 " + updateText;
        }
    }
}

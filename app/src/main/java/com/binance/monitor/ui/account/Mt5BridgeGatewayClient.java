package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Mt5BridgeGatewayClient {
    private static final int GATEWAY_PORT = 8787;
    private static final int LAN_SCAN_THREAD_COUNT = 24;
    private static final long LAN_SCAN_COOLDOWN_MS = 2 * 60 * 1000L;

    private static volatile String discoveredLanBaseUrl = "";
    private static volatile long lastLanScanAtMs = 0L;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build();

    private final OkHttpClient discoveryClient = new OkHttpClient.Builder()
            .connectTimeout(350, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build();

    private final Object syncLock = new Object();
    private long syncSeq;
    private String syncRangeKey = "";
    private String syncBaseUrl = "";
    private List<AccountMetric> cachedOverviewMetrics = new ArrayList<>();
    private List<AccountMetric> cachedCurveIndicators = new ArrayList<>();
    private List<AccountMetric> cachedStatsMetrics = new ArrayList<>();
    private final LinkedHashMap<Long, CurvePoint> cachedCurvePoints = new LinkedHashMap<>();
    private final LinkedHashMap<String, PositionItem> cachedPositions = new LinkedHashMap<>();
    private final LinkedHashMap<String, PositionItem> cachedPendingOrders = new LinkedHashMap<>();
    private final LinkedHashMap<String, TradeRecordItem> cachedTrades = new LinkedHashMap<>();

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
        Set<String> attempted = new HashSet<>();
        String rangeKey = mapRange(range);

        if (!discoveredLanBaseUrl.isEmpty()) {
            if (fetchFromBaseUrl(discoveredLanBaseUrl, rangeKey, result, errors)) {
                return result;
            }
            attempted.add(discoveredLanBaseUrl);
        }

        for (String baseUrl : baseUrls) {
            if (attempted.contains(baseUrl)) {
                continue;
            }
            if (fetchFromBaseUrl(baseUrl, rangeKey, result, errors)) {
                return result;
            }
            attempted.add(baseUrl);
        }

        String discovered = discoverLanGatewayBaseUrl();
        if (!discovered.isEmpty() && !attempted.contains(discovered)) {
            if (fetchFromBaseUrl(discovered, rangeKey, result, errors)) {
                return result;
            }
        } else if (discovered.isEmpty()) {
            errors.add("LAN scan -> no reachable gateway found");
        }

        result.error = String.join(" ; ", errors);
        return result;
    }

    private boolean fetchFromBaseUrl(String baseUrl,
                                     String rangeKey,
                                     SnapshotResult result,
                                     List<String> errors) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        long localSyncSeq;
        boolean canUseDelta;
        synchronized (syncLock) {
            localSyncSeq = syncSeq;
            canUseDelta = syncSeq > 0L
                    && rangeKey.equals(syncRangeKey)
                    && normalizedBase.equals(syncBaseUrl)
                    && !cachedOverviewMetrics.isEmpty();
        }

        String url = normalizedBase + "/v1/snapshot?range=" + rangeKey;
        if (canUseDelta) {
            url = url + "&since=" + localSyncSeq + "&delta=1";
        } else {
            url = url + "&delta=1";
        }
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                errors.add(normalizedBase + " -> HTTP " + response.code());
                return false;
            }
            String body = response.body() == null ? "" : response.body().string();
            if (body.isEmpty()) {
                errors.add(normalizedBase + " -> Empty response");
                return false;
            }

            JSONObject root = new JSONObject(body);
            JSONObject meta = root.optJSONObject("accountMeta");
            result.account = meta == null ? "" : meta.optString("login", "");
            result.server = meta == null ? "" : meta.optString("server", "");
            result.source = meta == null ? "MT5网关" : meta.optString("source", "MT5网关");
            result.updatedAt = meta == null ? 0L : meta.optLong("updatedAt", 0L);
            result.connectedBaseUrl = normalizedBase;
            long remoteSyncSeq = meta == null ? 0L : meta.optLong("syncSeq", 0L);
            boolean isDelta = root.optBoolean("isDelta", false);
            boolean unchanged = root.optBoolean("unchanged", false);

            synchronized (syncLock) {
                if (!rangeKey.equals(syncRangeKey) || !normalizedBase.equals(syncBaseUrl)) {
                    resetSyncStateLocked();
                }

                if (!isDelta) {
                    cachedOverviewMetrics = parseMetrics(root.optJSONArray("overviewMetrics"));
                    cachedCurveIndicators = parseMetrics(root.optJSONArray("curveIndicators"));
                    cachedStatsMetrics = parseMetrics(root.optJSONArray("statsMetrics"));
                    rebuildCurveCache(root.optJSONArray("curvePoints"));
                    rebuildPositionCache(cachedPositions, root.optJSONArray("positions"));
                    rebuildPositionCache(cachedPendingOrders, root.optJSONArray("pendingOrders"));
                    rebuildTradeCache(root.optJSONArray("trades"));
                } else if (!unchanged) {
                    updateMetricsIfPresent(root, "overviewMetrics", true);
                    updateMetricsIfPresent(root, "curveIndicators", false);
                    updateMetricsIfPresent(root, "statsMetrics", false);
                    applyDelta(root.optJSONObject("delta"));
                }

                syncSeq = Math.max(syncSeq, remoteSyncSeq);
                syncRangeKey = rangeKey;
                syncBaseUrl = normalizedBase;

                result.snapshot = buildSnapshotFromCacheLocked();
            }

            result.success = true;
            result.unchanged = unchanged;
            result.deltaResponse = isDelta;
            if (!isLoopbackBaseUrl(normalizedBase)) {
                discoveredLanBaseUrl = normalizedBase;
            }
            return true;
        } catch (Exception exception) {
            synchronized (syncLock) {
                if (canUseDelta) {
                    resetSyncStateLocked();
                }
            }
            errors.add(normalizedBase + " -> " + exception.getMessage());
            return false;
        }
    }

    private boolean isLoopbackBaseUrl(String baseUrl) {
        return baseUrl.contains("10.0.2.2")
                || baseUrl.contains("127.0.0.1")
                || baseUrl.contains("localhost");
    }

    private String discoverLanGatewayBaseUrl() {
        long now = System.currentTimeMillis();
        if (now - lastLanScanAtMs < LAN_SCAN_COOLDOWN_MS) {
            return "";
        }
        synchronized (Mt5BridgeGatewayClient.class) {
            now = System.currentTimeMillis();
            if (now - lastLanScanAtMs < LAN_SCAN_COOLDOWN_MS) {
                return "";
            }
            lastLanScanAtMs = now;
        }

        SubnetInfo subnet = resolveLocalSubnet();
        if (subnet == null) {
            return "";
        }

        List<String> bases = buildLanHostCandidates(subnet);
        if (bases.isEmpty()) {
            return "";
        }

        ExecutorService pool = Executors.newFixedThreadPool(LAN_SCAN_THREAD_COUNT);
        CompletionService<String> completion = new ExecutorCompletionService<>(pool);
        int submitted = 0;
        for (String base : bases) {
            completion.submit(() -> probeGateway(base) ? base : "");
            submitted++;
        }

        String found = "";
        try {
            for (int i = 0; i < submitted; i++) {
                String candidate = completion.take().get();
                if (candidate != null && !candidate.isEmpty()) {
                    found = candidate;
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            pool.shutdownNow();
        }
        if (!found.isEmpty()) {
            discoveredLanBaseUrl = found;
        }
        return found;
    }

    private boolean probeGateway(String baseUrl) {
        Request request = new Request.Builder()
                .url(baseUrl + "/health")
                .get()
                .build();
        try (Response response = discoveryClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }
            String body = response.body() == null ? "" : response.body().string();
            if (body.isEmpty()) {
                return false;
            }
            return body.contains("\"gatewayMode\"")
                    || body.contains("\"mt5PackageAvailable\"")
                    || body.contains("\"ok\"");
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<String> buildLanHostCandidates(SubnetInfo subnet) {
        LinkedHashSet<Integer> hostSet = new LinkedHashSet<>();
        int self = subnet.selfHost;

        for (int delta = 1; delta <= 10; delta++) {
            hostSet.add(self - delta);
            hostSet.add(self + delta);
        }
        int[] commonHosts = {2, 8, 10, 11, 20, 30, 50, 80, 100, 101, 110, 120, 150, 180, 200, 220, 254, 1};
        for (int host : commonHosts) {
            hostSet.add(host);
        }
        for (int host = 1; host <= 254; host++) {
            hostSet.add(host);
        }

        List<String> result = new ArrayList<>();
        for (Integer host : hostSet) {
            if (host == null || host <= 0 || host >= 255 || host == self) {
                continue;
            }
            result.add("http://" + subnet.prefix + host + ":" + GATEWAY_PORT);
        }
        return result;
    }

    @Nullable
    private SubnetInfo resolveLocalSubnet() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (!(address instanceof Inet4Address)) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    if (ip == null || ip.startsWith("127.") || ip.startsWith("169.254.")) {
                        continue;
                    }
                    String[] parts = ip.split("\\.");
                    if (parts.length != 4) {
                        continue;
                    }
                    int self = Integer.parseInt(parts[3]);
                    String prefix = parts[0] + "." + parts[1] + "." + parts[2] + ".";
                    return new SubnetInfo(prefix, self);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
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
            list.add(parsePositionItem(item));
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
            list.add(parseTradeItem(item));
        }
        return list;
    }

    private List<PositionItem> parsePendingOrders(JSONArray array) {
        List<PositionItem> list = new ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            list.add(parsePendingOrderItem(item));
        }
        return list;
    }

    private PositionItem parsePositionItem(JSONObject item) {
        return new PositionItem(
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
                item.optInt("pendingCount", 0),
                item.optDouble("pendingPrice", 0d),
                optDoubleAny(item, 0d, "takeProfit", "tp", "tpPrice", "take_profit"),
                optDoubleAny(item, 0d, "stopLoss", "sl", "slPrice", "stop_loss"),
                optDoubleAny(item, 0d, "storageFee", "swap", "storage", "swapFee"));
    }

    private PositionItem parsePendingOrderItem(JSONObject item) {
        return new PositionItem(
                item.optString("productName", "--"),
                item.optString("code", "--"),
                item.optString("side", "Buy"),
                item.optDouble("quantity", 0d),
                item.optDouble("sellableQuantity", 0d),
                item.optDouble("costPrice", 0d),
                item.optDouble("latestPrice", item.optDouble("pendingPrice", 0d)),
                item.optDouble("marketValue", 0d),
                item.optDouble("positionRatio", 0d),
                item.optDouble("dayPnL", 0d),
                item.optDouble("totalPnL", 0d),
                item.optDouble("returnRate", 0d),
                item.optDouble("pendingLots", item.optDouble("quantity", 0d)),
                item.optInt("pendingCount", 1),
                item.optDouble("pendingPrice", 0d),
                optDoubleAny(item, 0d, "takeProfit", "tp", "tpPrice", "take_profit"),
                optDoubleAny(item, 0d, "stopLoss", "sl", "slPrice", "stop_loss"),
                0d);
    }

    private TradeRecordItem parseTradeItem(JSONObject item) {
        double price = item.optDouble("price", 0d);
        double openPrice = optDoubleAny(item, price,
                "openPrice", "open_price", "open", "priceOpen", "entryPrice", "entry_price");
        double closePrice = optDoubleAny(item, price,
                "closePrice", "close_price", "close", "priceClose", "exitPrice", "exit_price");
        return new TradeRecordItem(
                item.optLong("timestamp", 0L),
                item.optString("productName", "--"),
                item.optString("code", "--"),
                item.optString("side", "Buy"),
                price,
                item.optDouble("quantity", 0d),
                item.optDouble("amount", 0d),
                item.optDouble("fee", 0d),
                item.optString("remark", ""),
                item.optDouble("profit", 0d),
                item.optLong("openTime", item.optLong("timestamp", 0L)),
                item.optLong("closeTime", item.optLong("timestamp", 0L)),
                item.optDouble("storageFee", item.optDouble("fee", 0d)),
                openPrice,
                closePrice,
                item.optLong("dealTicket", 0L),
                item.optLong("orderId", 0L),
                item.optLong("positionId", 0L),
                item.optInt("entryType", 0));
    }

    private void resetSyncStateLocked() {
        syncSeq = 0L;
        syncRangeKey = "";
        syncBaseUrl = "";
        cachedOverviewMetrics = new ArrayList<>();
        cachedCurveIndicators = new ArrayList<>();
        cachedStatsMetrics = new ArrayList<>();
        cachedCurvePoints.clear();
        cachedPositions.clear();
        cachedPendingOrders.clear();
        cachedTrades.clear();
    }

    private void updateMetricsIfPresent(JSONObject root, String key, boolean allowEmptyReplace) {
        if (root == null || !root.has(key)) {
            return;
        }
        JSONArray array = root.optJSONArray(key);
        List<AccountMetric> metrics = parseMetrics(array);
        if (allowEmptyReplace || !metrics.isEmpty()) {
            if ("overviewMetrics".equals(key)) {
                cachedOverviewMetrics = metrics;
            } else if ("curveIndicators".equals(key)) {
                cachedCurveIndicators = metrics;
            } else if ("statsMetrics".equals(key)) {
                cachedStatsMetrics = metrics;
            }
        }
    }

    private void rebuildCurveCache(JSONArray array) {
        cachedCurvePoints.clear();
        List<CurvePoint> list = parseCurvePoints(array);
        for (CurvePoint item : list) {
            if (item == null || item.getTimestamp() <= 0L) {
                continue;
            }
            cachedCurvePoints.put(item.getTimestamp(), item);
        }
    }

    private void rebuildPositionCache(LinkedHashMap<String, PositionItem> target, JSONArray array) {
        target.clear();
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            PositionItem parsed = target == cachedPendingOrders ? parsePendingOrderItem(item) : parsePositionItem(item);
            String key = target == cachedPendingOrders ? pendingKeyFromJson(item, parsed) : positionKeyFromJson(item, parsed);
            target.put(key, parsed);
        }
    }

    private void rebuildTradeCache(JSONArray array) {
        cachedTrades.clear();
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            TradeRecordItem parsed = parseTradeItem(item);
            String key = tradeKeyFromJson(item, parsed);
            cachedTrades.put(key, parsed);
        }
    }

    private void applyDelta(JSONObject delta) {
        if (delta == null) {
            return;
        }
        applyPositionDelta(cachedPositions, delta.optJSONObject("positions"), false);
        applyPositionDelta(cachedPendingOrders, delta.optJSONObject("pendingOrders"), true);
        applyTradeDelta(delta.optJSONObject("trades"));
        applyCurveDelta(delta.optJSONObject("curvePoints"));
    }

    private void applyPositionDelta(LinkedHashMap<String, PositionItem> target,
                                    JSONObject section,
                                    boolean pending) {
        if (section == null) {
            return;
        }
        JSONArray remove = section.optJSONArray("remove");
        if (remove != null) {
            for (int i = 0; i < remove.length(); i++) {
                String key = remove.optString(i, "");
                if (!key.isEmpty()) {
                    target.remove(key);
                }
            }
        }
        JSONArray upsert = section.optJSONArray("upsert");
        if (upsert == null) {
            return;
        }
        for (int i = 0; i < upsert.length(); i++) {
            JSONObject item = upsert.optJSONObject(i);
            if (item == null) {
                continue;
            }
            PositionItem parsed = pending ? parsePendingOrderItem(item) : parsePositionItem(item);
            String key = pending ? pendingKeyFromJson(item, parsed) : positionKeyFromJson(item, parsed);
            target.put(key, parsed);
        }
    }

    private void applyTradeDelta(JSONObject section) {
        if (section == null) {
            return;
        }
        JSONArray remove = section.optJSONArray("remove");
        if (remove != null) {
            for (int i = 0; i < remove.length(); i++) {
                String key = remove.optString(i, "");
                if (!key.isEmpty()) {
                    cachedTrades.remove(key);
                }
            }
        }
        JSONArray upsert = section.optJSONArray("upsert");
        if (upsert == null) {
            return;
        }
        for (int i = 0; i < upsert.length(); i++) {
            JSONObject item = upsert.optJSONObject(i);
            if (item == null) {
                continue;
            }
            TradeRecordItem parsed = parseTradeItem(item);
            String key = tradeKeyFromJson(item, parsed);
            cachedTrades.put(key, parsed);
        }
    }

    private void applyCurveDelta(JSONObject section) {
        if (section == null) {
            return;
        }
        if (section.optBoolean("reset", false)) {
            cachedCurvePoints.clear();
        }
        JSONArray append = section.optJSONArray("append");
        if (append == null) {
            return;
        }
        List<CurvePoint> points = parseCurvePoints(append);
        for (CurvePoint point : points) {
            if (point == null || point.getTimestamp() <= 0L) {
                continue;
            }
            cachedCurvePoints.put(point.getTimestamp(), point);
        }
        while (cachedCurvePoints.size() > 120_000) {
            Long first = cachedCurvePoints.keySet().iterator().next();
            cachedCurvePoints.remove(first);
        }
    }

    private String positionKeyFromJson(JSONObject raw, PositionItem item) {
        String key = raw.optString("_key", "");
        if (!key.isEmpty()) {
            return key;
        }
        return "position:" + item.getCode() + "|" + item.getSide() + "|"
                + Math.round(Math.abs(item.getQuantity()) * 10_000d) + "|"
                + Math.round(item.getCostPrice() * 100d);
    }

    private String pendingKeyFromJson(JSONObject raw, PositionItem item) {
        String key = raw.optString("_key", "");
        if (!key.isEmpty()) {
            return key;
        }
        return "pending:" + item.getCode() + "|" + item.getSide() + "|"
                + Math.round(item.getPendingLots() * 10_000d) + "|"
                + Math.round(item.getPendingPrice() * 10_000d);
    }

    private String tradeKeyFromJson(JSONObject raw, TradeRecordItem item) {
        String key = raw.optString("_key", "");
        if (!key.isEmpty()) {
            return key;
        }
        if (item.getDealTicket() > 0L) {
            return "trade:" + item.getDealTicket();
        }
        return "trade:" + item.getOrderId() + "|" + item.getPositionId() + "|"
                + item.getCloseTime() + "|"
                + Math.round(Math.abs(item.getQuantity()) * 10_000d);
    }

    private AccountSnapshot buildSnapshotFromCacheLocked() {
        List<CurvePoint> curves = new ArrayList<>(cachedCurvePoints.values());
        curves.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        List<PositionItem> positions = new ArrayList<>(cachedPositions.values());
        List<PositionItem> pendingOrders = new ArrayList<>(cachedPendingOrders.values());
        pendingOrders.sort((a, b) -> Double.compare(b.getPendingLots(), a.getPendingLots()));

        List<TradeRecordItem> trades = new ArrayList<>(cachedTrades.values());
        trades.sort((a, b) -> Long.compare(
                b.getCloseTime() > 0L ? b.getCloseTime() : b.getTimestamp(),
                a.getCloseTime() > 0L ? a.getCloseTime() : a.getTimestamp()));

        return new AccountSnapshot(
                new ArrayList<>(cachedOverviewMetrics),
                curves,
                new ArrayList<>(cachedCurveIndicators),
                positions,
                pendingOrders,
                trades,
                new ArrayList<>(cachedStatsMetrics));
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

    private static final class SubnetInfo {
        private final String prefix;
        private final int selfHost;

        private SubnetInfo(String prefix, int selfHost) {
            this.prefix = prefix;
            this.selfHost = selfHost;
        }
    }

    public static class SnapshotResult {
        private boolean success;
        private boolean unchanged;
        private boolean deltaResponse;
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

        public boolean isUnchanged() {
            return unchanged;
        }

        public boolean isDeltaResponse() {
            return deltaResponse;
        }

        public String getError() {
            return error == null ? "" : error;
        }

        public AccountSnapshot getSnapshot() {
            return snapshot;
        }

        public String getAccount(String fallback) {
            if (account == null || account.trim().isEmpty()) {
                return fallback;
            }
            return account.trim();
        }

        public String getServer(String fallback) {
            if (server == null || server.trim().isEmpty()) {
                return fallback;
            }
            return server.trim();
        }

        public String getLocalizedSource() {
            return localizeSource(source);
        }

        public String getGatewayEndpoint() {
            if (connectedBaseUrl == null || connectedBaseUrl.trim().isEmpty()) {
                return "--";
            }
            return connectedBaseUrl.trim();
        }

        public long getUpdatedAt() {
            return updatedAt;
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

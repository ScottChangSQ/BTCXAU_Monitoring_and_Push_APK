package com.binance.monitor.ui.account;

import android.content.Context;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.util.GatewayUrlResolver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
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
    private static final String SNAPSHOT_SCOPE = "snapshot";
    private static final String SUMMARY_SCOPE = "summary";
    private static final String LIVE_SCOPE = "live";
    private static final String PENDING_SCOPE = "pending";
    private static final String TRADES_SCOPE = "trades";
    private static final String CURVE_SCOPE = "curve";
    private static final String SNAPSHOT_ENDPOINT = "/v1/snapshot";
    private static final String SUMMARY_ENDPOINT = "/v1/summary";
    private static final String LIVE_ENDPOINT = "/v1/live";
    private static final String PENDING_ENDPOINT = "/v1/pending";
    private static final String TRADES_ENDPOINT = "/v1/trades";
    private static final String CURVE_ENDPOINT = "/v1/curve";

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
    private final Map<String, Long> syncSeqByRequestKey = new HashMap<>();
    private String activeRangeKey = "";
    private String syncBaseUrl = "";
    private List<AccountMetric> cachedOverviewMetrics = new ArrayList<>();
    private List<AccountMetric> cachedCurveIndicators = new ArrayList<>();
    private List<AccountMetric> cachedStatsMetrics = new ArrayList<>();
    private final LinkedHashMap<Long, CurvePoint> cachedCurvePoints = new LinkedHashMap<>();
    private final LinkedHashMap<String, PositionItem> cachedPositions = new LinkedHashMap<>();
    private final LinkedHashMap<String, PositionItem> cachedPendingOrders = new LinkedHashMap<>();
    private final LinkedHashMap<String, TradeRecordItem> cachedTrades = new LinkedHashMap<>();

    @Nullable
    private final ConfigManager configManager;

    public Mt5BridgeGatewayClient() {
        this.configManager = null;
    }

    public Mt5BridgeGatewayClient(@Nullable Context context) {
        this.configManager = context == null
                ? null
                : ConfigManager.getInstance(context.getApplicationContext());
    }

    public SnapshotResult fetch(AccountTimeRange range) {
        return fetchComposite(range);
    }

    public SnapshotResult fetchSummary(AccountTimeRange range) {
        return fetchScope(range, SUMMARY_SCOPE, SUMMARY_ENDPOINT);
    }

    public SnapshotResult fetchLive(AccountTimeRange range) {
        return fetchScope(range, LIVE_SCOPE, LIVE_ENDPOINT);
    }

    static String buildSyncRequestKey(String scope, String rangeKey) {
        String normalizedScope = scope == null || scope.trim().isEmpty() ? SNAPSHOT_SCOPE : scope.trim();
        String normalizedRange = rangeKey == null || rangeKey.trim().isEmpty() ? "7d" : rangeKey.trim();
        return normalizedScope + ":" + normalizedRange;
    }

    private SnapshotResult fetchComposite(AccountTimeRange range) {
        String rangeKey = mapRange(range);
        synchronized (syncLock) {
            ensureRangeContextLocked(rangeKey);
        }

        if (!hasScopeSyncLocked(buildSyncRequestKey(SNAPSHOT_SCOPE, rangeKey))) {
            return fetchScope(range, SNAPSHOT_SCOPE, SNAPSHOT_ENDPOINT);
        }

        SnapshotResult liveResult = fetchScope(range, LIVE_SCOPE, LIVE_ENDPOINT);
        if (!liveResult.isSuccess()) {
            return fallbackToSnapshot(range, liveResult.getError());
        }

        SnapshotResult pendingResult = fetchScope(range, PENDING_SCOPE, PENDING_ENDPOINT);
        if (!pendingResult.isSuccess()) {
            return fallbackToSnapshot(range, pendingResult.getError());
        }

        SnapshotResult tradesResult = fetchScope(range, TRADES_SCOPE, TRADES_ENDPOINT);
        if (!tradesResult.isSuccess()) {
            return fallbackToSnapshot(range, tradesResult.getError());
        }

        SnapshotResult curveResult = fetchScope(range, CURVE_SCOPE, CURVE_ENDPOINT);
        if (!curveResult.isSuccess()) {
            return fallbackToSnapshot(range, curveResult.getError());
        }

        SnapshotResult result = new SnapshotResult();
        result.success = true;
        result.account = liveResult.account;
        result.server = liveResult.server;
        result.source = liveResult.source;
        result.updatedAt = liveResult.updatedAt;
        result.connectedBaseUrl = liveResult.connectedBaseUrl;
        result.deltaResponse = liveResult.deltaResponse
                || pendingResult.deltaResponse
                || tradesResult.deltaResponse
                || curveResult.deltaResponse;
        result.unchanged = liveResult.unchanged
                && pendingResult.unchanged
                && tradesResult.unchanged
                && curveResult.unchanged;
        synchronized (syncLock) {
            result.snapshot = buildSnapshotFromCacheLocked();
        }
        return result;
    }

    private boolean hasScopeSyncLocked(String requestKey) {
        return syncSeqByRequestKey.containsKey(requestKey) && syncSeqByRequestKey.get(requestKey) != null;
    }

    private SnapshotResult fallbackToSnapshot(AccountTimeRange range, String reason) {
        synchronized (syncLock) {
            resetSyncStateLocked();
        }
        SnapshotResult fallback = fetchScope(range, SNAPSHOT_SCOPE, SNAPSHOT_ENDPOINT);
        if (!fallback.isSuccess() && reason != null && !reason.trim().isEmpty()) {
            fallback.error = reason + " ; " + fallback.getError();
        }
        return fallback;
    }

    private SnapshotResult fetchScope(AccountTimeRange range, String scope, String endpointPath) {
        SnapshotResult result = new SnapshotResult();
        List<String> errors = new ArrayList<>();
        Set<String> attempted = new HashSet<>();
        String rangeKey = mapRange(range);
        String requestKey = buildSyncRequestKey(scope, rangeKey);
        String configuredBaseUrl = configManager != null
                ? configManager.getMt5GatewayBaseUrl()
                : AppConstants.MT5_GATEWAY_BASE_URL;
        boolean shouldTryLocalFallbacks = shouldAppendLocalFallbacks(normalizeBaseUrl(configuredBaseUrl));

        if (!discoveredLanBaseUrl.isEmpty()) {
            if (fetchFromBaseUrl(discoveredLanBaseUrl, endpointPath, rangeKey, scope, result, errors)) {
                return result;
            }
            attempted.add(discoveredLanBaseUrl);
        }

        for (String baseUrl : resolveCandidateBaseUrls(configuredBaseUrl)) {
            if (attempted.contains(baseUrl)) {
                continue;
            }
            if (fetchFromBaseUrl(baseUrl, endpointPath, rangeKey, scope, result, errors)) {
                return result;
            }
            attempted.add(baseUrl);
        }

        if (shouldTryLocalFallbacks) {
            String discovered = discoverLanGatewayBaseUrl();
            if (!discovered.isEmpty() && !attempted.contains(discovered)) {
                if (fetchFromBaseUrl(discovered, endpointPath, rangeKey, scope, result, errors)) {
                    return result;
                }
            } else if (discovered.isEmpty()) {
                errors.add("LAN scan -> no reachable gateway found");
            }
        }

        result.error = String.join(" ; ", errors);
        return result;
    }

    private boolean fetchFromBaseUrl(String baseUrl,
                                     String endpointPath,
                                     String rangeKey,
                                     String scope,
                                     SnapshotResult result,
                                     List<String> errors) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        String requestKey = buildSyncRequestKey(scope, rangeKey);
        long localSyncSeq;
        boolean canUseDelta;
        synchronized (syncLock) {
            ensureRangeContextLocked(rangeKey);
            localSyncSeq = syncSeqByRequestKey.containsKey(requestKey)
                    ? Math.max(0L, syncSeqByRequestKey.get(requestKey))
                    : 0L;
            canUseDelta = localSyncSeq > 0L && normalizedBase.equals(syncBaseUrl);
        }

        String url = normalizedBase + endpointPath + "?range=" + rangeKey;
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
            result.accountName = meta == null ? "" : meta.optString("name", "");
            result.leverage = meta == null ? 0d : meta.optDouble("leverage", 0d);
            result.updatedAt = meta == null ? 0L : meta.optLong("updatedAt", 0L);
            result.connectedBaseUrl = normalizedBase;
            long remoteSyncSeq = meta == null ? 0L : meta.optLong("syncSeq", 0L);
            boolean isDelta = root.optBoolean("isDelta", false);
            boolean unchanged = root.optBoolean("unchanged", false);

            synchronized (syncLock) {
                ensureRangeContextLocked(rangeKey);
                if (!syncBaseUrl.isEmpty() && !normalizedBase.equals(syncBaseUrl)) {
                    resetSyncStateLocked();
                    activeRangeKey = rangeKey;
                }
                applyScopePayloadLocked(scope, root, isDelta, unchanged);
                syncSeqByRequestKey.put(requestKey, Math.max(localSyncSeq, remoteSyncSeq));
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
                    syncSeqByRequestKey.remove(requestKey);
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
        return GatewayUrlResolver.normalizeBaseUrl(baseUrl, "http://10.0.2.2:8787");
    }

    private List<String> buildCandidateBaseUrls() {
        String configuredBaseUrl = configManager != null
                ? configManager.getMt5GatewayBaseUrl()
                : AppConstants.MT5_GATEWAY_BASE_URL;
        return resolveCandidateBaseUrls(configuredBaseUrl);
    }

    // 远端网关只请求自身，只有本地地址才附带模拟器和 localhost 回退。
    static List<String> resolveCandidateBaseUrls(@Nullable String configuredBaseUrl) {
        Set<String> urls = new LinkedHashSet<>();
        String primary = GatewayUrlResolver.normalizeBaseUrl(configuredBaseUrl, AppConstants.MT5_GATEWAY_BASE_URL);
        urls.add(primary);
        if (shouldAppendLocalFallbacks(primary)) {
            urls.add("http://10.0.2.2:8787");
            urls.add("http://127.0.0.1:8787");
            urls.add("http://localhost:8787");
        }
        return new ArrayList<>(urls);
    }

    private static boolean shouldAppendLocalFallbacks(String baseUrl) {
        try {
            URI uri = new URI(GatewayUrlResolver.normalizeBaseUrl(baseUrl, AppConstants.MT5_GATEWAY_BASE_URL));
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
            return "127.0.0.1".equals(normalizedHost)
                    || "localhost".equals(normalizedHost)
                    || "10.0.2.2".equals(normalizedHost);
        } catch (Exception ignored) {
            return false;
        }
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
                    normalizeEpochMs(optLongAny(item, 0L, "timestamp", "time")),
                    item.optDouble("equity", 0d),
                    item.optDouble("balance", 0d),
                    item.optDouble("positionRatio", 0d)));
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
        String productName = optStringAny(item, "--",
                "productName", "name", "symbol", "instrument", "contract", "product");
        String code = optStringAny(item, productName,
                "code", "symbol", "instrument", "productCode", "contract");
        String side = optStringAny(item, "Buy", "side", "direction", "type", "positionSide");
        double quantity = optDoubleAny(item, 0d, "quantity", "lots", "volume", "qty");
        double sellableQuantity = optDoubleAny(item, quantity, "sellableQuantity", "availableQuantity", "freeQuantity");
        double costPrice = optDoubleAny(item, 0d, "costPrice", "openPrice", "open_price", "priceOpen", "avgPrice");
        double latestPrice = optDoubleAny(item, 0d, "latestPrice", "lastPrice", "markPrice", "priceCurrent", "pendingPrice");
        double marketValue = optDoubleAny(item, 0d, "marketValue", "notional", "positionValue");
        double positionRatio = optDoubleAny(item, 0d, "positionRatio", "ratio", "weight");
        double dayPnl = optDoubleAny(item, 0d, "dayPnL", "dayPnlAmount", "dailyPnl");
        double totalPnl = optDoubleAny(item, 0d, "totalPnL", "pnl", "profit");
        double storageFee = optDoubleAny(item, 0d, "storageFee", "swap", "storage", "swapFee");
        double returnRate = normalizeReturnRate(
                side,
                quantity,
                costPrice,
                totalPnl + storageFee,
                optDoubleAny(item, 0d, "returnRate", "pnlRate", "yieldRate"));
        double pendingLots = optDoubleAny(item, 0d, "pendingLots", "pendingQuantity", "pendingVolume");
        int pendingCount = item.optInt("pendingCount", item.optInt("pendingOrderCount", 0));
        double pendingPrice = optDoubleAny(item, 0d, "pendingPrice", "pendingAvgPrice");
        return new PositionItem(
                productName,
                code,
                side,
                item.optLong("positionTicket", item.optLong("positionId", item.optLong("ticket", 0L))),
                item.optLong("orderId", item.optLong("order", item.optLong("ticket", 0L))),
                quantity,
                sellableQuantity,
                costPrice,
                latestPrice,
                marketValue,
                positionRatio,
                dayPnl,
                totalPnl,
                returnRate,
                pendingLots,
                pendingCount,
                pendingPrice,
                optDoubleAny(item, 0d, "takeProfit", "tp", "tpPrice", "take_profit"),
                optDoubleAny(item, 0d, "stopLoss", "sl", "slPrice", "stop_loss"),
                storageFee);
    }

    private PositionItem parsePendingOrderItem(JSONObject item) {
        String productName = optStringAny(item, "--",
                "productName", "name", "symbol", "instrument", "contract", "product");
        String code = optStringAny(item, productName,
                "code", "symbol", "instrument", "productCode", "contract");
        String side = optStringAny(item, "Buy", "side", "direction", "type", "positionSide");
        double quantity = optDoubleAny(item, 0d, "quantity", "lots", "volume", "qty");
        double pendingLots = optDoubleAny(item, quantity, "pendingLots", "pendingQuantity", "pendingVolume");
        double pendingPrice = optDoubleAny(item, 0d, "pendingPrice", "price", "orderPrice");
        double latestPrice = optDoubleAny(item, pendingPrice, "latestPrice", "lastPrice", "markPrice");
        return new PositionItem(
                productName,
                code,
                side,
                0L,
                item.optLong("orderId", item.optLong("ticket", 0L)),
                quantity,
                optDoubleAny(item, quantity, "sellableQuantity", "availableQuantity"),
                optDoubleAny(item, 0d, "costPrice", "openPrice", "open_price", "priceOpen"),
                latestPrice,
                item.optDouble("marketValue", 0d),
                item.optDouble("positionRatio", 0d),
                item.optDouble("dayPnL", 0d),
                item.optDouble("totalPnL", 0d),
                item.optDouble("returnRate", 0d),
                pendingLots,
                item.optInt("pendingCount", item.optInt("pendingOrderCount", 1)),
                pendingPrice,
                optDoubleAny(item, 0d, "takeProfit", "tp", "tpPrice", "take_profit"),
                optDoubleAny(item, 0d, "stopLoss", "sl", "slPrice", "stop_loss"),
                0d);
    }

    private TradeRecordItem parseTradeItem(JSONObject item) {
        String productName = optStringAny(item, "--",
                "productName", "name", "symbol", "instrument", "contract", "product");
        String code = optStringAny(item, productName,
                "code", "symbol", "instrument", "productCode", "contract");
        String side = optStringAny(item, "Buy", "side", "direction", "type", "positionSide");
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
        return new TradeRecordItem(
                timestampMs,
                productName,
                code,
                side,
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
                item.optInt("entryType", 0));
    }

    // 统一把秒级时间戳修正为毫秒，避免不同网关口径混用时历史成交时间漂移。
    private long normalizeEpochMs(long value) {
        if (value <= 0L) {
            return 0L;
        }
        return value < 10_000_000_000L ? value * 1000L : value;
    }

    private void ensureRangeContextLocked(String rangeKey) {
        String normalizedRange = rangeKey == null ? "" : rangeKey.trim();
        if (normalizedRange.equals(activeRangeKey)) {
            return;
        }
        resetSyncStateLocked();
        activeRangeKey = normalizedRange;
    }

    private void resetSyncStateLocked() {
        syncSeqByRequestKey.clear();
        activeRangeKey = "";
        syncBaseUrl = "";
        cachedOverviewMetrics = new ArrayList<>();
        cachedCurveIndicators = new ArrayList<>();
        cachedStatsMetrics = new ArrayList<>();
        cachedCurvePoints.clear();
        cachedPositions.clear();
        cachedPendingOrders.clear();
        cachedTrades.clear();
    }

    private void applyScopePayloadLocked(String scope,
                                         JSONObject root,
                                         boolean isDelta,
                                         boolean unchanged) {
        if (root == null) {
            return;
        }
        if (!isDelta) {
            applyScopeFullPayloadLocked(scope, root);
            return;
        }
        if (unchanged) {
            return;
        }
        applyScopeDeltaPayloadLocked(scope, root);
    }

    private void applyScopeFullPayloadLocked(String scope, JSONObject root) {
        if (SNAPSHOT_SCOPE.equals(scope)) {
            cachedOverviewMetrics = parseMetrics(root.optJSONArray("overviewMetrics"));
            cachedCurveIndicators = parseMetrics(root.optJSONArray("curveIndicators"));
            cachedStatsMetrics = parseMetrics(root.optJSONArray("statsMetrics"));
            rebuildCurveCache(root.optJSONArray("curvePoints"));
            rebuildPositionCache(cachedPositions, root.optJSONArray("positions"));
            rebuildPositionCache(cachedPendingOrders, root.optJSONArray("pendingOrders"));
            rebuildTradeCache(root.optJSONArray("trades"));
            return;
        }
        if (SUMMARY_SCOPE.equals(scope)) {
            updateMetricsIfPresent(root, "overviewMetrics", true);
            updateMetricsIfPresent(root, "statsMetrics", true);
            return;
        }
        if (LIVE_SCOPE.equals(scope)) {
            updateMetricsIfPresent(root, "overviewMetrics", true);
            updateMetricsIfPresent(root, "statsMetrics", true);
            rebuildPositionCache(cachedPositions, root.optJSONArray("positions"));
            return;
        }
        if (PENDING_SCOPE.equals(scope)) {
            rebuildPositionCache(cachedPendingOrders, root.optJSONArray("pendingOrders"));
            return;
        }
        if (TRADES_SCOPE.equals(scope)) {
            rebuildTradeCache(root.optJSONArray("trades"));
            return;
        }
        if (CURVE_SCOPE.equals(scope)) {
            updateMetricsIfPresent(root, "curveIndicators", true);
            rebuildCurveCache(root.optJSONArray("curvePoints"));
        }
    }

    private void applyScopeDeltaPayloadLocked(String scope, JSONObject root) {
        if (SNAPSHOT_SCOPE.equals(scope)) {
            updateMetricsIfPresent(root, "overviewMetrics", true);
            updateMetricsIfPresent(root, "curveIndicators", false);
            updateMetricsIfPresent(root, "statsMetrics", false);
            applyDelta(root.optJSONObject("delta"));
            return;
        }
        if (SUMMARY_SCOPE.equals(scope)) {
            updateMetricsIfPresent(root, "overviewMetrics", true);
            updateMetricsIfPresent(root, "statsMetrics", true);
            return;
        }
        if (LIVE_SCOPE.equals(scope)) {
            updateMetricsIfPresent(root, "overviewMetrics", true);
            updateMetricsIfPresent(root, "statsMetrics", true);
            applyPositionDelta(cachedPositions, root.optJSONObject("delta") == null ? null : root.optJSONObject("delta").optJSONObject("positions"), false);
            return;
        }
        if (PENDING_SCOPE.equals(scope)) {
            applyPositionDelta(cachedPendingOrders, root.optJSONObject("delta") == null ? null : root.optJSONObject("delta").optJSONObject("pendingOrders"), true);
            return;
        }
        if (TRADES_SCOPE.equals(scope)) {
            applyTradeDelta(root.optJSONObject("delta") == null ? null : root.optJSONObject("delta").optJSONObject("trades"));
            return;
        }
        if (CURVE_SCOPE.equals(scope)) {
            updateMetricsIfPresent(root, "curveIndicators", false);
            applyCurveDelta(root.optJSONObject("delta") == null ? null : root.optJSONObject("delta").optJSONObject("curvePoints"));
        }
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
        long ticket = raw.optLong("positionTicket", raw.optLong("ticket", 0L));
        if (ticket > 0L) {
            return "position:" + ticket;
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
        long orderId = raw.optLong("orderId", raw.optLong("ticket", 0L));
        if (orderId > 0L) {
            return "pending:" + orderId;
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

    private String optStringAny(JSONObject item, String fallback, String... keys) {
        if (item == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            if (key == null || key.trim().isEmpty() || !item.has(key)) {
                continue;
            }
            String value = item.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return fallback;
    }

    private double normalizeReturnRate(String side,
                                       double quantity,
                                       double costPrice,
                                       double pnlWithStorage,
                                       double sourceRate) {
        double absQty = Math.abs(quantity);
        double absCost = Math.abs(costPrice);
        if (absQty > 1e-9 && absCost > 1e-9) {
            double calculated = pnlWithStorage / (absQty * absCost);
            if (Math.abs(sourceRate) <= 1e-9) {
                return calculated;
            }
            if (Math.abs(pnlWithStorage) > 1e-9 && Math.signum(sourceRate) != Math.signum(pnlWithStorage)) {
                return calculated;
            }
            return sourceRate;
        }
        if (Math.abs(pnlWithStorage) > 1e-9 && Math.abs(sourceRate) > 1e-9
                && Math.signum(sourceRate) != Math.signum(pnlWithStorage)) {
            return Math.copySign(Math.abs(sourceRate), pnlWithStorage);
        }
        return sourceRate;
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
        private String accountName = "";
        private String server = "";
        private String source = "";
        private double leverage;
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

        public String getAccountName(String fallback) {
            if (accountName == null || accountName.trim().isEmpty()) {
                return fallback == null ? "" : fallback.trim();
            }
            return accountName.trim();
        }

        public double getLeverage() {
            return leverage;
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

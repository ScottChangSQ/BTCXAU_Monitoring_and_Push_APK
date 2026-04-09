/*
 * 负责 Binance 行情 REST / 历史文件请求。
 * 图表链路只消费 Binance REST 主链，不再混入历史文件或客户端补算结果。
 */
package com.binance.monitor.data.remote;

import android.content.Context;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.KlineData;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BinanceApiClient {

    private static final long LATEST_CLOSED_MAX_STALE_MS = 45L * 60L * 1000L;
    private static final long SHARED_RESPONSE_CACHE_TTL_MS = 8_000L;
    private static final int SHARED_RESPONSE_CACHE_MAX_SIZE = 120;
    private static final Map<String, CachedArray> SHARED_RESPONSE_CACHE = new ConcurrentHashMap<>();
    private final ConfigManager configManager;

    private static final class CachedArray {
        private final String body;
        private final long cachedAtMs;

        private CachedArray(String body, long cachedAtMs) {
            this.body = body == null ? "" : body;
            this.cachedAtMs = cachedAtMs;
        }
    }

    private final OkHttpClient client;

    public BinanceApiClient() {
        this(null);
    }

    public BinanceApiClient(Context context) {
        configManager = context == null ? null : ConfigManager.getInstance(context.getApplicationContext());
        client = new OkHttpClient.Builder()
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .build();
    }

    public KlineData fetchLatestClosedKline(String symbol) throws Exception {
        long now = System.currentTimeMillis();
        List<CandleEntry> history = fetchRecentRealtimeKlines(symbol, 30);
        if (history.isEmpty()) {
            history = fetchKlineHistory(symbol, "1m", 30);
        }
        CandleEntry selected = selectLatestClosed(history, now);
        if (selected == null) {
            throw new IOException("K线数据为空");
        }
        long staleMs = now - selected.getCloseTime();
        if (staleMs > LATEST_CLOSED_MAX_STALE_MS) {
            List<CandleEntry> retry = fetchRecentRealtimeKlines(symbol, 200);
            CandleEntry refreshed = selectLatestClosed(retry, now);
            if (refreshed != null && refreshed.getCloseTime() > selected.getCloseTime()) {
                selected = refreshed;
            }
        }
        return new KlineData(
                selected.getSymbol(),
                selected.getOpen(),
                selected.getHigh(),
                selected.getLow(),
                selected.getClose(),
                selected.getVolume(),
                selected.getQuoteVolume(),
                selected.getOpenTime(),
                selected.getCloseTime(),
                true
        );
    }

    public List<CandleEntry> fetchKlineHistory(String symbol, String interval, int limit) throws Exception {
        String normalizedInterval = normalizeInterval(interval);
        if (isWeeklyOrMonthly(normalizedInterval)) {
            int dailyLimit = Math.min(1500, "1w".equals(normalizedInterval) ? Math.max(200, limit * 9) : Math.max(300, limit * 35));
            List<CandleEntry> daily = fetchKlineHistory(symbol, "1d", dailyLimit);
            return aggregateDailyToInterval(daily, symbol, normalizedInterval, limit, Long.MAX_VALUE);
        }
        Set<String> candidates = buildCandidateUrls(symbol, normalizedInterval, limit);
        Exception lastError = null;
        for (String url : candidates) {
            try {
                JSONArray array = requestJsonArray(url);
                if (array.length() == 0) {
                    lastError = new IOException("返回空数据: " + url);
                    continue;
                }
                List<CandleEntry> result = new ArrayList<>(array.length());
                for (int i = 0; i < array.length(); i++) {
                    result.add(CandleEntry.fromRest(symbol, array.getJSONArray(i)));
                }
                if (!result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                lastError = new IOException("请求失败(" + url + "): " + e.getMessage(), e);
            }
        }
        if (shouldUseDirectBinanceFallback()) {
            try {
                List<CandleEntry> fallback = fetchFromBinanceVision(symbol, normalizedInterval, limit);
                if (!fallback.isEmpty()) {
                    return fallback;
                }
            } catch (Exception e) {
                lastError = new IOException("Binance Vision 回退失败: " + e.getMessage(), e);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("K线历史请求失败");
    }

    public List<CandleEntry> fetchKlineHistoryBefore(String symbol,
                                                     String interval,
                                                     int limit,
                                                     long endTimeInclusive) throws Exception {
        String normalizedInterval = normalizeInterval(interval);
        if (isWeeklyOrMonthly(normalizedInterval)) {
            int dailyLimit = Math.min(1500, "1w".equals(normalizedInterval) ? Math.max(260, limit * 10) : Math.max(360, limit * 38));
            List<CandleEntry> daily = fetchKlineHistoryBefore(symbol, "1d", dailyLimit, endTimeInclusive);
            return aggregateDailyToInterval(daily, symbol, normalizedInterval, limit, endTimeInclusive);
        }
        Set<String> candidates = buildCandidateUrlsWithEndTime(symbol, normalizedInterval, limit, endTimeInclusive);
        Exception lastError = null;
        for (String url : candidates) {
            try {
                JSONArray array = requestJsonArray(url);
                if (array.length() == 0) {
                    continue;
                }
                List<CandleEntry> result = new ArrayList<>(array.length());
                for (int i = 0; i < array.length(); i++) {
                    CandleEntry item = CandleEntry.fromRest(symbol, array.getJSONArray(i));
                    if (item.getOpenTime() <= endTimeInclusive) {
                        result.add(item);
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (shouldUseDirectBinanceFallback()) {
            try {
                List<CandleEntry> fallback = fetchFromBinanceVisionBefore(symbol, normalizedInterval, limit, endTimeInclusive);
                if (!fallback.isEmpty()) {
                    return fallback;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw new IOException("历史分页请求失败: " + lastError.getMessage(), lastError);
        }
        return new ArrayList<>();
    }

    public List<CandleEntry> fetchKlineHistoryAfter(String symbol,
                                                    String interval,
                                                    int limit,
                                                    long startTimeInclusive) throws Exception {
        String normalizedInterval = normalizeInterval(interval);
        if (startTimeInclusive <= 0L) {
            return fetchKlineHistory(symbol, normalizedInterval, limit);
        }
        if (isWeeklyOrMonthly(normalizedInterval)) {
            List<CandleEntry> fallbackWindow = fetchKlineHistory(symbol, normalizedInterval, Math.max(limit, 64));
            return filterAfter(fallbackWindow, startTimeInclusive);
        }
        Set<String> candidates = buildCandidateUrlsWithStartTime(symbol, normalizedInterval, limit, startTimeInclusive);
        Exception lastError = null;
        for (String url : candidates) {
            try {
                JSONArray array = requestJsonArray(url);
                if (array.length() == 0) {
                    continue;
                }
                List<CandleEntry> result = new ArrayList<>(array.length());
                for (int i = 0; i < array.length(); i++) {
                    CandleEntry item = CandleEntry.fromRest(symbol, array.getJSONArray(i));
                    if (item.getOpenTime() >= startTimeInclusive) {
                        result.add(item);
                    }
                }
                if (!result.isEmpty()) {
                    Collections.sort(result, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
                    return result;
                }
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (shouldUseDirectBinanceFallback()) {
            List<CandleEntry> fallback = fetchKlineHistory(symbol, normalizedInterval, Math.max(limit, 50));
            List<CandleEntry> filtered = filterAfter(fallback, startTimeInclusive);
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }
        if (lastError != null) {
            throw new IOException("增量K线请求失败: " + lastError.getMessage(), lastError);
        }
        return new ArrayList<>();
    }

    // 图表专用：所有周期都只使用 Binance REST K 线接口拉取完整窗口。
    public List<CandleEntry> fetchChartKlineFullWindow(String symbol, String interval, int limit) throws Exception {
        String normalizedInterval = normalizeInterval(interval);
        int safeLimit = clampLimit(limit);
        return requestChartRestKlines(symbol, normalizedInterval, safeLimit, null);
    }

    // 图表专用：左滑历史分页同样仅走 Binance REST，并按 endTime 截断。
    public List<CandleEntry> fetchChartKlineHistoryBefore(String symbol,
                                                          String interval,
                                                          int limit,
                                                          long endTimeInclusive) throws Exception {
        String normalizedInterval = normalizeInterval(interval);
        int safeLimit = clampLimit(limit);
        return requestChartRestKlines(symbol, normalizedInterval, safeLimit, endTimeInclusive);
    }

    private List<CandleEntry> fetchRecentRealtimeKlines(String symbol, int limit) {
        Set<String> candidates = buildCandidateUrls(symbol, "1m", clampLimit(limit));
        for (String url : candidates) {
            try {
                JSONArray array = requestJsonArray(url);
                if (array.length() == 0) {
                    continue;
                }
                List<CandleEntry> result = new ArrayList<>(array.length());
                for (int i = 0; i < array.length(); i++) {
                    result.add(CandleEntry.fromRest(symbol, array.getJSONArray(i)));
                }
                if (!result.isEmpty()) {
                    return result;
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
    }

    // 图表专用 REST 请求：读取完整字段并按开盘时间去重排序，不接历史回退链。
    private List<CandleEntry> requestChartRestKlines(String symbol,
                                                     String interval,
                                                     int limit,
                                                     Long endTimeInclusive) throws Exception {
        Set<String> candidates = buildChartRestCandidates(symbol, interval, limit, endTimeInclusive);
        Exception lastError = null;
        for (String url : candidates) {
            try {
                JSONArray array = requestJsonArrayNoCache(url);
                if (array == null || array.length() == 0) {
                    lastError = new IOException("返回空数据: " + url);
                    continue;
                }
                List<CandleEntry> parsed = new ArrayList<>(array.length());
                for (int i = 0; i < array.length(); i++) {
                    CandleEntry item = CandleEntry.fromRest(symbol, array.getJSONArray(i));
                    if (endTimeInclusive == null || item.getOpenTime() <= endTimeInclusive) {
                        parsed.add(item);
                    }
                }
                List<CandleEntry> normalized = normalizeCandles(parsed);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
                lastError = new IOException("解析后为空: " + url);
            } catch (Exception e) {
                lastError = new IOException("请求失败(" + url + "): " + e.getMessage(), e);
            }
        }
        if (lastError != null) {
            throw new IOException("图表K线REST请求失败: " + lastError.getMessage(), lastError);
        }
        throw new IOException("图表K线REST请求失败");
    }

    private CandleEntry selectLatestClosed(List<CandleEntry> history, long now) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        CandleEntry fallback = history.get(history.size() - 1);
        CandleEntry selected = fallback;
        for (int i = history.size() - 1; i >= 0; i--) {
            CandleEntry item = history.get(i);
            if (item.getCloseTime() < now - 1000L) {
                selected = item;
                break;
            }
        }
        return selected;
    }

    private JSONArray requestJsonArray(String url) throws Exception {
        JSONArray cached = getCachedJsonArray(url);
        if (cached != null) {
            return cached;
        }
        String content = executeJsonArrayRequest(url);
        cacheJsonArray(url, content);
        return new JSONArray(content);
    }

    // 图表页主动请求优先量真实网络耗时，不复用本地 8 秒 JSON 缓存。
    private JSONArray requestJsonArrayNoCache(String url) throws Exception {
        return new JSONArray(executeJsonArrayRequest(url));
    }

    // 执行一次真实的 JSON 数组请求，并返回原始响应体。
    private String executeJsonArrayRequest(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = safeBody(response.body());
                throw new IOException("HTTP " + response.code() + " " + body);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("响应体为空");
            }
            String content = body.string();
            if (content == null || content.trim().isEmpty()) {
                throw new IOException("空响应体");
            }
            return content;
        }
    }

    private JSONArray getCachedJsonArray(String url) {
        CachedArray cached = SHARED_RESPONSE_CACHE.get(url);
        if (cached == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - cached.cachedAtMs > SHARED_RESPONSE_CACHE_TTL_MS) {
            SHARED_RESPONSE_CACHE.remove(url);
            return null;
        }
        try {
            return new JSONArray(cached.body);
        } catch (Exception ignored) {
            SHARED_RESPONSE_CACHE.remove(url);
            return null;
        }
    }

    private void cacheJsonArray(String url, String body) {
        if (url == null || url.trim().isEmpty() || body == null || body.trim().isEmpty()) {
            return;
        }
        SHARED_RESPONSE_CACHE.put(url, new CachedArray(body, System.currentTimeMillis()));
        if (SHARED_RESPONSE_CACHE.size() <= SHARED_RESPONSE_CACHE_MAX_SIZE) {
            return;
        }
        int removeCount = SHARED_RESPONSE_CACHE.size() - SHARED_RESPONSE_CACHE_MAX_SIZE;
        if (removeCount <= 0) {
            return;
        }
        Iterator<String> iterator = SHARED_RESPONSE_CACHE.keySet().iterator();
        while (iterator.hasNext() && removeCount > 0) {
            String key = iterator.next();
            SHARED_RESPONSE_CACHE.remove(key);
            removeCount--;
        }
    }

    private Set<String> buildCandidateUrls(String symbol, String interval, int limit) {
        String primary = AppConstants.buildRestUrl(getConfiguredRestBaseUrl(), symbol, interval, limit);
        Set<String> urls = new LinkedHashSet<>();
        urls.add(primary);
        return urls;
    }

    // 图表链路固定使用 Binance 官方 REST 主域及其同源镜像，避免接入其他口径数据。
    private Set<String> buildChartRestCandidates(String symbol, String interval, int limit, Long endTimeInclusive) {
        int safeLimit = clampLimit(limit);
        String primary = AppConstants.buildRestUrl(getConfiguredRestBaseUrl(), symbol, interval, safeLimit);
        if (endTimeInclusive != null && endTimeInclusive > 0L) {
            primary = appendQuery(primary, "endTime", String.valueOf(endTimeInclusive));
        }
        Set<String> urls = new LinkedHashSet<>();
        urls.add(primary);
        return urls;
    }

    private String getConfiguredRestBaseUrl() {
        if (configManager == null) {
            return AppConstants.BASE_REST_URL;
        }
        return configManager.getBinanceRestBaseUrl();
    }

    private boolean shouldUseDirectBinanceFallback() {
        String baseUrl = getConfiguredRestBaseUrl();
        return baseUrl != null && baseUrl.contains("binance.com");
    }

    private Set<String> buildCandidateUrlsWithEndTime(String symbol, String interval, int limit, long endTime) {
        Set<String> base = buildCandidateUrls(symbol, interval, limit);
        Set<String> out = new LinkedHashSet<>();
        for (String url : base) {
            out.add(appendQuery(url, "endTime", String.valueOf(endTime)));
        }
        return out;
    }

    private Set<String> buildCandidateUrlsWithStartTime(String symbol, String interval, int limit, long startTime) {
        Set<String> base = buildCandidateUrls(symbol, interval, limit);
        Set<String> out = new LinkedHashSet<>();
        for (String url : base) {
            String withStart = appendQuery(url, "startTime", String.valueOf(startTime));
            out.add(appendQuery(withStart, "endTime", String.valueOf(System.currentTimeMillis())));
        }
        return out;
    }

    private String appendQuery(String url, String key, String value) {
        if (url.contains(key + "=")) {
            return url;
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + key + "=" + value;
    }

    private void addHostFallback(Set<String> urls,
                                 String base,
                                 String sourceHost,
                                 String[] targetHosts) {
        try {
            URI uri = URI.create(base);
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase(sourceHost)) {
                return;
            }
            for (String target : targetHosts) {
                String fallback = new URI(
                        uri.getScheme(),
                        uri.getUserInfo(),
                        target,
                        uri.getPort(),
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment()
                ).toString();
                urls.add(fallback);
            }
        } catch (Exception ignored) {
        }
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(1500, limit));
    }

    private String normalizeInterval(String interval) {
        if (interval == null) {
            return "1m";
        }
        String value = interval.trim();
        if (value.isEmpty()) {
            return "1m";
        }
        if ("1M".equals(value)) {
            return "1M";
        }
        String lower = value.toLowerCase(Locale.US);
        if ("1m".equals(lower) || "5m".equals(lower) || "15m".equals(lower) || "30m".equals(lower)
                || "1h".equals(lower) || "4h".equals(lower) || "1d".equals(lower) || "1w".equals(lower)) {
            return lower;
        }
        return value;
    }

    private boolean isWeeklyOrMonthly(String interval) {
        return "1w".equals(interval) || "1M".equals(interval);
    }

    private List<CandleEntry> filterAfter(List<CandleEntry> source, long startTimeInclusive) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<CandleEntry> filtered = new ArrayList<>();
        for (CandleEntry item : source) {
            if (item.getOpenTime() >= startTimeInclusive) {
                filtered.add(item);
            }
        }
        Collections.sort(filtered, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        return filtered;
    }

    // 图表链统一按开盘时间排序并去重，确保消费侧只看到单一有序序列。
    private List<CandleEntry> normalizeCandles(List<CandleEntry> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<CandleEntry> filtered = new ArrayList<>();
        for (CandleEntry item : source) {
            if (item != null) {
                filtered.add(item);
            }
        }
        return deduplicateByOpenTime(filtered);
    }

    private int resolveVisionMonthlyAttempts(String interval, int target) {
        String normalizedInterval = normalizeInterval(interval);
        int barsPerMonth;
        if ("1m".equals(normalizedInterval)) {
            barsPerMonth = 43_200;
        } else if ("5m".equals(normalizedInterval)) {
            barsPerMonth = 8_640;
        } else if ("15m".equals(normalizedInterval)) {
            barsPerMonth = 2_880;
        } else if ("30m".equals(normalizedInterval)) {
            barsPerMonth = 1_440;
        } else if ("1h".equals(normalizedInterval)) {
            barsPerMonth = 720;
        } else if ("4h".equals(normalizedInterval)) {
            barsPerMonth = 180;
        } else if ("1d".equals(normalizedInterval)) {
            barsPerMonth = 30;
        } else if ("1w".equals(normalizedInterval)) {
            barsPerMonth = 5;
        } else {
            barsPerMonth = 1;
        }
        int months = (int) Math.ceil(target / Math.max(1d, barsPerMonth)) + 1;
        return Math.max(1, Math.min(24, months));
    }

    private int resolveVisionRecentDailyWindow(String interval, int target) {
        String normalizedInterval = normalizeInterval(interval);
        if ("1m".equals(normalizedInterval) || "5m".equals(normalizedInterval)
                || "15m".equals(normalizedInterval) || "30m".equals(normalizedInterval)) {
            return target <= 240 ? 3 : 7;
        }
        if ("1h".equals(normalizedInterval) || "4h".equals(normalizedInterval)) {
            return 10;
        }
        if ("1d".equals(normalizedInterval)) {
            return 20;
        }
        return 35;
    }

    private List<CandleEntry> fetchFromBinanceVision(String symbol, String interval, int limit) throws Exception {
        int target = clampLimit(limit);
        List<CandleEntry> merged = new ArrayList<>();
        Calendar cursor = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        int monthlyAttempts = resolveVisionMonthlyAttempts(interval, target);
        for (int i = 0; i < monthlyAttempts; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("请求已取消");
            }
            String monthUrl = buildVisionMonthlyUrl(symbol, interval, cursor);
            try {
                List<CandleEntry> oneMonth = fetchZipCsvKlines(monthUrl, symbol);
                if (!oneMonth.isEmpty()) {
                    merged.addAll(oneMonth);
                    if (merged.size() >= target) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            cursor.add(Calendar.MONTH, -1);
        }
        int recentWindow = resolveVisionRecentDailyWindow(interval, target);
        List<CandleEntry> recentDaily = fetchFromBinanceVisionRecentDaily(symbol, interval, recentWindow);
        if (!recentDaily.isEmpty()) {
            merged.addAll(recentDaily);
        }
        if (merged.isEmpty()) {
            return merged;
        }
        List<CandleEntry> deduped = deduplicateByOpenTime(merged);
        if (deduped.size() <= target) {
            return deduped;
        }
        return new ArrayList<>(deduped.subList(deduped.size() - target, deduped.size()));
    }

    private List<CandleEntry> fetchFromBinanceVisionBefore(String symbol,
                                                           String interval,
                                                           int limit,
                                                           long endTimeInclusive) throws Exception {
        int target = clampLimit(limit);
        List<CandleEntry> merged = new ArrayList<>();
        Calendar cursor = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cursor.setTimeInMillis(endTimeInclusive);
        for (int i = 0; i < 12; i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("请求已取消");
            }
            String monthUrl = buildVisionMonthlyUrl(symbol, interval, cursor);
            try {
                List<CandleEntry> oneMonth = fetchZipCsvKlines(monthUrl, symbol);
                for (CandleEntry item : oneMonth) {
                    if (item.getOpenTime() <= endTimeInclusive) {
                        merged.add(item);
                    }
                }
                if (merged.size() >= target) {
                    break;
                }
            } catch (Exception ignored) {
            }
            cursor.add(Calendar.MONTH, -1);
        }
        if (merged.isEmpty()) {
            return merged;
        }
        List<CandleEntry> deduped = deduplicateByOpenTime(merged);
        if (deduped.size() <= target) {
            return deduped;
        }
        return new ArrayList<>(deduped.subList(deduped.size() - target, deduped.size()));
    }

    private List<CandleEntry> fetchFromBinanceVisionRecentDaily(String symbol, String interval, int maxDays) {
        List<CandleEntry> out = new ArrayList<>();
        Calendar cursor = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < maxDays; i++) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            String dailyUrl = buildVisionDailyUrl(symbol, interval, cursor);
            try {
                List<CandleEntry> oneDay = fetchZipCsvKlines(dailyUrl, symbol);
                if (!oneDay.isEmpty()) {
                    out.addAll(oneDay);
                }
            } catch (Exception ignored) {
            }
            cursor.add(Calendar.DAY_OF_MONTH, -1);
        }
        return out;
    }

    private List<CandleEntry> aggregateDailyToInterval(List<CandleEntry> daily,
                                                       String symbol,
                                                       String interval,
                                                       int limit,
                                                       long endTimeInclusive) {
        if (daily == null || daily.isEmpty()) {
            return new ArrayList<>();
        }
        List<CandleEntry> sorted = new ArrayList<>(daily);
        Collections.sort(sorted, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Calendar cal = Calendar.getInstance(tz);
        List<CandleEntry> out = new ArrayList<>();
        CandleEntry agg = null;
        long currentBucket = Long.MIN_VALUE;
        for (CandleEntry day : sorted) {
            if (day.getOpenTime() > endTimeInclusive) {
                continue;
            }
            long bucket = resolveBucketStart(day.getOpenTime(), interval, cal);
            if (bucket != currentBucket || agg == null) {
                if (agg != null) {
                    out.add(agg);
                }
                currentBucket = bucket;
                agg = new CandleEntry(
                        symbol,
                        bucket,
                        day.getCloseTime(),
                        day.getOpen(),
                        day.getHigh(),
                        day.getLow(),
                        day.getClose(),
                        day.getVolume(),
                        day.getQuoteVolume()
                );
            } else {
                agg = new CandleEntry(
                        symbol,
                        agg.getOpenTime(),
                        Math.max(agg.getCloseTime(), day.getCloseTime()),
                        agg.getOpen(),
                        Math.max(agg.getHigh(), day.getHigh()),
                        Math.min(agg.getLow(), day.getLow()),
                        day.getClose(),
                        agg.getVolume() + day.getVolume(),
                        agg.getQuoteVolume() + day.getQuoteVolume()
                );
            }
        }
        if (agg != null) {
            out.add(agg);
        }
        if (out.size() <= limit) {
            return out;
        }
        return new ArrayList<>(out.subList(out.size() - limit, out.size()));
    }

    private long resolveBucketStart(long openTimeMs, String interval, Calendar cal) {
        String normalizedInterval = normalizeInterval(interval);
        cal.setTimeInMillis(openTimeMs);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if ("1w".equals(normalizedInterval)) {
            int day = cal.get(Calendar.DAY_OF_WEEK);
            int delta;
            if (day == Calendar.SUNDAY) {
                delta = -6;
            } else {
                delta = Calendar.MONDAY - day;
            }
            cal.add(Calendar.DAY_OF_MONTH, delta);
            return cal.getTimeInMillis();
        }
        if ("1M".equals(normalizedInterval)) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
            return cal.getTimeInMillis();
        }
        return cal.getTimeInMillis();
    }

    private String buildVisionMonthlyUrl(String symbol, String interval, Calendar calendar) {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        return String.format(Locale.US,
                "https://data.binance.vision/data/futures/um/monthly/klines/%s/%s/%s-%s-%04d-%02d.zip",
                symbol, interval, symbol, interval, year, month);
    }

    private String buildVisionDailyUrl(String symbol, String interval, Calendar calendar) {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        return String.format(Locale.US,
                "https://data.binance.vision/data/futures/um/daily/klines/%s/%s/%s-%s-%04d-%02d-%02d.zip",
                symbol, interval, symbol, interval, year, month, day);
    }

    private List<CandleEntry> fetchZipCsvKlines(String url, String symbol) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Zip 响应为空");
            }
            List<CandleEntry> out = new ArrayList<>();
            try (ZipInputStream zipInput = new ZipInputStream(body.byteStream())) {
                ZipEntry entry;
                while ((entry = zipInput.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        zipInput.closeEntry();
                        continue;
                    }
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zipInput));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        CandleEntry parsed = parseVisionCsvLine(symbol, line);
                        if (parsed != null) {
                            out.add(parsed);
                        }
                    }
                    zipInput.closeEntry();
                }
            }
            return out;
        }
    }

    private CandleEntry parseVisionCsvLine(String symbol, String line) {
        if (line == null) {
            return null;
        }
        String value = line.trim();
        if (value.isEmpty()) {
            return null;
        }
        String[] parts = value.split(",");
        if (parts.length < 8) {
            return null;
        }
        try {
            long openTime = Long.parseLong(parts[0].trim());
            double open = Double.parseDouble(parts[1].trim());
            double high = Double.parseDouble(parts[2].trim());
            double low = Double.parseDouble(parts[3].trim());
            double close = Double.parseDouble(parts[4].trim());
            double volume = Double.parseDouble(parts[5].trim());
            long closeTime = Long.parseLong(parts[6].trim());
            double quoteVolume = Double.parseDouble(parts[7].trim());
            return new CandleEntry(
                    symbol,
                    openTime,
                    closeTime,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    quoteVolume
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<CandleEntry> deduplicateByOpenTime(List<CandleEntry> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        Collections.sort(source, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        List<CandleEntry> out = new ArrayList<>();
        Long last = null;
        for (CandleEntry item : source) {
            if (last != null && item.getOpenTime() == last) {
                if (!out.isEmpty()) {
                    out.set(out.size() - 1, item);
                }
            } else {
                out.add(item);
                last = item.getOpenTime();
            }
        }
        return out;
    }

    private String safeBody(ResponseBody body) {
        if (body == null) {
            return "";
        }
        try {
            String content = body.string();
            if (content.length() > 120) {
                return content.substring(0, 120);
            }
            return content;
        } catch (Exception ignored) {
            return "";
        }
    }
}

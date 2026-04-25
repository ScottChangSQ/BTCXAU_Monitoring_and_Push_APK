package com.binance.monitor.constants;

import com.binance.monitor.BuildConfig;
import com.binance.monitor.util.ProductSymbolMapper;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class AppConstants {

    public static final String SYMBOL_BTC = "BTCUSDT";
    public static final String SYMBOL_XAU = "XAUUSDT";
    public static final List<String> MONITOR_SYMBOLS = Arrays.asList(SYMBOL_BTC, SYMBOL_XAU);

    public static final double BTC_DEFAULT_VOLUME = 1000d;
    public static final double BTC_DEFAULT_AMOUNT = 70000000d;
    public static final double BTC_DEFAULT_PRICE_CHANGE = 200d;

    public static final double XAU_DEFAULT_VOLUME = 3000d;
    public static final double XAU_DEFAULT_AMOUNT = 15000000d;
    public static final double XAU_DEFAULT_PRICE_CHANGE = 10d;

    public static final String BASE_REST_URL = sanitizeBaseUrl(
            BuildConfig.BINANCE_REST_BASE_URL,
            "https://tradeapp.ltd/binance-rest/fapi/v1/klines");
    public static final String BASE_WS_URL = sanitizeBaseUrl(
            BuildConfig.BINANCE_WS_BASE_URL,
            "wss://tradeapp.ltd/binance-ws/ws/");
    public static final int MAX_RECONNECT_ATTEMPTS = 30;
    public static final long WS_PING_INTERVAL_SECONDS = 45L;
    public static final long PRICE_UPDATE_THROTTLE_MS = 1_000L;
    public static final long FLOATING_UPDATE_THROTTLE_MS = 1_000L;
    public static final long FLOATING_UPDATE_IDLE_THROTTLE_MS = 1_000L;
    public static final long FLOATING_UPDATE_BACKGROUND_THROTTLE_MS = 500L;
    public static final long FLOATING_UPDATE_BACKGROUND_IDLE_THROTTLE_MS = 1_500L;
    public static final long FLOATING_UPDATE_MINIMIZED_THROTTLE_MS = 2_000L;
    public static final long CONNECTION_HEARTBEAT_INTERVAL_MS = 30_000L;
    public static final long CONNECTION_HEARTBEAT_BACKGROUND_INTERVAL_MS = 60_000L;
    public static final long CONNECTION_HEARTBEAT_SCREEN_OFF_INTERVAL_MS = 90_000L;
    public static final long SOCKET_STALE_TIMEOUT_MS = 8_000L;
    public static final long STALE_RECONNECT_COOLDOWN_MS = 60_000L;
    public static final long NOTIFICATION_COOLDOWN_MS = 5 * 60 * 1000L;
    public static final long MERGE_WINDOW_MS = 4000L;
    public static final long ABNORMAL_SYNC_INTERVAL_MS = 8000L;
    public static final long ABNORMAL_SYNC_BACKGROUND_INTERVAL_MS = 20_000L;
    public static final long CHART_AUTO_REFRESH_INTERVAL_MS = 1_000L;
    public static final long CHART_AUTO_REFRESH_HEALTHY_INTERVAL_MS = 10_000L;
    public static final long CHART_REALTIME_TAIL_UI_WINDOW_MS = 1_000L;
    public static final int CHART_BASE_MINUTE_HISTORY_LIMIT = 1500;

    public static final String SERVICE_CHANNEL_ID = "monitor_service_channel";
    public static final String ALERT_CHANNEL_ID = "monitor_alert_channel";
    public static final int SERVICE_NOTIFICATION_ID = 9001;
    public static final int BTC_ALERT_NOTIFICATION_ID = 1001;
    public static final int XAU_ALERT_NOTIFICATION_ID = 1002;
    public static final int COMBINED_ALERT_NOTIFICATION_ID = 1003;

    public static final String ACTION_BOOTSTRAP = "com.binance.monitor.action.BOOTSTRAP";
    public static final String ACTION_START_MONITORING = "com.binance.monitor.action.START_MONITORING";
    public static final String ACTION_STOP_MONITORING = "com.binance.monitor.action.STOP_MONITORING";
    public static final String ACTION_REFRESH_CONFIG = "com.binance.monitor.action.REFRESH_CONFIG";
    public static final String ACTION_CLEAR_ACCOUNT_RUNTIME = "com.binance.monitor.action.CLEAR_ACCOUNT_RUNTIME";

    public static final String LOG_INFO = "INFO";
    public static final String LOG_WARN = "WARN";
    public static final String LOG_ERROR = "ERROR";

    public static final List<String> BINANCE_PACKAGES = Arrays.asList(
            "com.binance.dev",
            "com.binance",
            "com.binance.app",
            "com.binance.client",
            "com.binance.mobile",
            "com.binance.mgs",
            "com.binance.us"
    );
    public static final List<String> MT5_PACKAGES = Arrays.asList(
            "net.metaquotes.metatrader5",
            "com.metatrader5.mobile"
    );
    public static final String BINANCE_URL = "https://www.binance.com/zh-CN/futures/";
    public static final String MT5_WEB_URL = "https://www.metatrader5.com/zh";
    public static final String MT5_PACKAGE = "net.metaquotes.metatrader5";
    public static final String MT5_GATEWAY_BASE_URL = BuildConfig.MT5_GATEWAY_BASE_URL;
    public static final long ACCOUNT_REFRESH_INTERVAL_MS = 5000L;
    public static final long ACCOUNT_REFRESH_MAX_INTERVAL_MS = 30000L;
    public static final long MARKET_RECENT_RECORDS_REFRESH_INTERVAL_MS = 60_000L;

    private AppConstants() {
    }

    public static String buildRestUrl(String symbol) {
        return buildRestUrl(symbol, "1m", 3);
    }

    public static String buildRestUrl(String symbol, String interval, int limit) {
        return buildRestUrl(BASE_REST_URL, symbol, interval, limit);
    }

    public static String buildRestUrl(String baseRestUrl, String symbol, String interval, int limit) {
        String safeSymbol = symbol == null || symbol.trim().isEmpty() ? SYMBOL_BTC : symbol.trim();
        String safeInterval = interval == null || interval.trim().isEmpty() ? "1m" : interval.trim();
        int safeLimit = Math.max(1, Math.min(1500, limit));
        String base = sanitizeBaseUrl(baseRestUrl, BASE_REST_URL);
        if (base.contains("{symbol}")) {
            String url = base.replace("{symbol}", safeSymbol);
            if (url.contains("{interval}")) {
                url = url.replace("{interval}", safeInterval);
            } else if (!url.contains("interval=")) {
                String separator = url.contains("?") ? "&" : "?";
                url = url + separator + "interval=" + safeInterval;
            }
            if (url.contains("{limit}")) {
                url = url.replace("{limit}", String.valueOf(safeLimit));
            } else if (!url.contains("limit=")) {
                String separator = url.contains("?") ? "&" : "?";
                url = url + separator + "limit=" + safeLimit;
            }
            return url;
        }
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "symbol=" + safeSymbol + "&interval=" + safeInterval + "&limit=" + safeLimit;
    }

    public static String buildWebSocketUrl(String symbol) {
        return buildWebSocketUrl(BASE_WS_URL, symbol);
    }

    public static String buildWebSocketUrl(String baseWsUrl, String symbol) {
        String stream = symbol.toLowerCase() + "@kline_1m";
        return buildStreamWebSocketUrl(baseWsUrl, stream);
    }

    public static String buildAggTradeWebSocketUrl(String symbol) {
        return buildAggTradeWebSocketUrl(BASE_WS_URL, symbol);
    }

    public static String buildAggTradeWebSocketUrl(String baseWsUrl, String symbol) {
        String stream = symbol.toLowerCase() + "@aggTrade";
        return buildStreamWebSocketUrl(baseWsUrl, stream);
    }

    private static String buildStreamWebSocketUrl(String baseWsUrl, String stream) {
        String baseUrl = sanitizeBaseUrl(baseWsUrl, BASE_WS_URL);
        if (baseUrl.contains("{stream}")) {
            return baseUrl.replace("{stream}", stream);
        }
        String base = baseUrl.endsWith("/") ? baseUrl : (baseUrl + "/");
        return base + stream;
    }

    public static String buildCombinedWebSocketUrl(Collection<String> symbols) {
        return buildCombinedWebSocketUrl(BASE_WS_URL, symbols);
    }

    public static String buildCombinedWebSocketUrl(String baseWsUrl, Collection<String> symbols) {
        return buildCombinedStreamWebSocketUrl(baseWsUrl, symbols, "@kline_1m");
    }

    public static String buildCombinedAggTradeWebSocketUrl(Collection<String> symbols) {
        return buildCombinedAggTradeWebSocketUrl(BASE_WS_URL, symbols);
    }

    public static String buildCombinedAggTradeWebSocketUrl(String baseWsUrl, Collection<String> symbols) {
        return buildCombinedStreamWebSocketUrl(baseWsUrl, symbols, "@aggTrade");
    }

    private static String buildCombinedStreamWebSocketUrl(String baseWsUrl,
                                                          Collection<String> symbols,
                                                          String streamSuffix) {
        if (symbols == null || symbols.isEmpty()) {
            String safeSuffix = streamSuffix == null || streamSuffix.trim().isEmpty()
                    ? "@kline_1m"
                    : streamSuffix.trim();
            if ("@aggTrade".equalsIgnoreCase(safeSuffix)) {
                return buildAggTradeWebSocketUrl(baseWsUrl, SYMBOL_BTC);
            }
            return buildWebSocketUrl(baseWsUrl, SYMBOL_BTC);
        }
        StringBuilder streams = new StringBuilder();
        String safeSuffix = streamSuffix == null || streamSuffix.trim().isEmpty()
                ? "@kline_1m"
                : streamSuffix.trim();
        for (String symbol : symbols) {
            if (symbol == null || symbol.trim().isEmpty()) {
                continue;
            }
            if (streams.length() > 0) {
                streams.append("/");
            }
            streams.append(symbol.trim().toLowerCase(Locale.ROOT)).append(safeSuffix);
        }
        if (streams.length() == 0) {
            if ("@aggTrade".equalsIgnoreCase(safeSuffix)) {
                return buildAggTradeWebSocketUrl(baseWsUrl, SYMBOL_BTC);
            }
            return buildWebSocketUrl(baseWsUrl, SYMBOL_BTC);
        }
        String baseUrl = sanitizeBaseUrl(baseWsUrl, BASE_WS_URL);
        if (baseUrl.contains("{stream}")) {
            return baseUrl.replace("{stream}", streams.toString());
        }
        String base = baseUrl.trim();
        if (base.endsWith("/ws") || base.endsWith("/ws/")) {
            base = base.replaceAll("/ws/?$", "");
        } else if (base.endsWith("/stream") || base.endsWith("/stream/")) {
            base = base.replaceAll("/stream/?$", "");
        } else if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/stream?streams=" + streams;
    }

    public static String symbolToAsset(String symbol) {
        String marketSymbol = ProductSymbolMapper.toMarketSymbol(symbol);
        if (SYMBOL_XAU.equals(marketSymbol)) {
            return "XAU";
        }
        if (SYMBOL_BTC.equals(marketSymbol)) {
            return "BTC";
        }
        return marketSymbol;
    }

    private static String sanitizeBaseUrl(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}

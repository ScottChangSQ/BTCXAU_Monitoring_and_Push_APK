package com.binance.monitor.constants;

import com.binance.monitor.BuildConfig;

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
            "https://fapi.binance.com/fapi/v1/klines");
    public static final String BASE_WS_URL = sanitizeBaseUrl(
            BuildConfig.BINANCE_WS_BASE_URL,
            "wss://fstream.binance.com/ws/");
    public static final int MAX_RECONNECT_ATTEMPTS = 30;
    public static final long WS_PING_INTERVAL_SECONDS = 45L;
    public static final long PRICE_UPDATE_THROTTLE_MS = 2_000L;
    public static final long FLOATING_UPDATE_THROTTLE_MS = 1_500L;
    public static final long CONNECTION_HEARTBEAT_INTERVAL_MS = 30_000L;
    public static final long SOCKET_STALE_TIMEOUT_MS = 70_000L;
    public static final long STALE_RECONNECT_COOLDOWN_MS = 60_000L;
    public static final long NOTIFICATION_COOLDOWN_MS = 5 * 60 * 1000L;
    public static final long MERGE_WINDOW_MS = 4000L;

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
    public static final long ACCOUNT_REFRESH_INTERVAL_MS = 8000L;
    public static final long ACCOUNT_REFRESH_MAX_INTERVAL_MS = 30000L;

    private AppConstants() {
    }

    public static String buildRestUrl(String symbol) {
        return buildRestUrl(symbol, "1m", 3);
    }

    public static String buildRestUrl(String symbol, String interval, int limit) {
        String safeSymbol = symbol == null || symbol.trim().isEmpty() ? SYMBOL_BTC : symbol.trim();
        String safeInterval = interval == null || interval.trim().isEmpty() ? "1m" : interval.trim();
        int safeLimit = Math.max(1, Math.min(1500, limit));
        if (BASE_REST_URL.contains("{symbol}")) {
            String url = BASE_REST_URL.replace("{symbol}", safeSymbol);
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
        String separator = BASE_REST_URL.contains("?") ? "&" : "?";
        return BASE_REST_URL + separator + "symbol=" + safeSymbol + "&interval=" + safeInterval + "&limit=" + safeLimit;
    }

    public static String buildWebSocketUrl(String symbol) {
        String stream = symbol.toLowerCase() + "@kline_1m";
        if (BASE_WS_URL.contains("{stream}")) {
            return BASE_WS_URL.replace("{stream}", stream);
        }
        String base = BASE_WS_URL.endsWith("/") ? BASE_WS_URL : (BASE_WS_URL + "/");
        return base + stream;
    }

    public static String buildCombinedWebSocketUrl(Collection<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return buildWebSocketUrl(SYMBOL_BTC);
        }
        StringBuilder streams = new StringBuilder();
        for (String symbol : symbols) {
            if (symbol == null || symbol.trim().isEmpty()) {
                continue;
            }
            if (streams.length() > 0) {
                streams.append("/");
            }
            streams.append(symbol.trim().toLowerCase(Locale.ROOT)).append("@kline_1m");
        }
        if (streams.length() == 0) {
            return buildWebSocketUrl(SYMBOL_BTC);
        }
        if (BASE_WS_URL.contains("{stream}")) {
            return BASE_WS_URL.replace("{stream}", streams.toString());
        }
        String base = BASE_WS_URL.trim();
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
        if (SYMBOL_XAU.equals(symbol)) {
            return "XAU";
        }
        return "BTC";
    }

    private static String sanitizeBaseUrl(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}

package com.binance.monitor.constants;

import com.binance.monitor.BuildConfig;

import java.util.Arrays;
import java.util.List;

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
    public static final long ACCOUNT_REFRESH_INTERVAL_MS = 5000L;

    private AppConstants() {
    }

    public static String buildRestUrl(String symbol) {
        if (BASE_REST_URL.contains("{symbol}")) {
            return BASE_REST_URL.replace("{symbol}", symbol);
        }
        String separator = BASE_REST_URL.contains("?") ? "&" : "?";
        return BASE_REST_URL + separator + "symbol=" + symbol + "&interval=1m&limit=3";
    }

    public static String buildWebSocketUrl(String symbol) {
        String stream = symbol.toLowerCase() + "@kline_1m";
        if (BASE_WS_URL.contains("{stream}")) {
            return BASE_WS_URL.replace("{stream}", stream);
        }
        String base = BASE_WS_URL.endsWith("/") ? BASE_WS_URL : (BASE_WS_URL + "/");
        return base + stream;
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
